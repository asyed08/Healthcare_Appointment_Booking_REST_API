package com.ameen.healthcare.service;

import com.ameen.healthcare.dto.event.AppointmentCancelledEvent;
import com.ameen.healthcare.dto.event.AppointmentCreatedEvent;
import com.ameen.healthcare.dto.request.BookAppointmentRequest;
import com.ameen.healthcare.dto.response.AppointmentResponse;
import com.ameen.healthcare.entity.*;
import com.ameen.healthcare.enums.AppointmentStatus;
import com.ameen.healthcare.enums.SlotStatus;
import com.ameen.healthcare.exception.ResourceNotFoundException;
import com.ameen.healthcare.exception.SlotUnavailableException;
import com.ameen.healthcare.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Core appointment booking logic:
 * <ul>
 *   <li>Idempotency — duplicate requests with the same key return the cached response</li>
 *   <li>Optimistic locking — concurrent slot grabs collide on {@code @Version}; loser gets 409</li>
 *   <li>Kafka events — publishes {@code AppointmentCreatedEvent} / {@code AppointmentCancelledEvent}</li>
 * </ul>
 */
@Service
public class AppointmentService {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentService.class);
    private static final int IDEMPOTENCY_TTL_HOURS = 24;

    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AppointmentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              SlotRepository slotRepository,
                              PatientRepository patientRepository,
                              DoctorRepository doctorRepository,
                              IdempotencyKeyRepository idempotencyKeyRepository,
                              AppointmentEventPublisher eventPublisher,
                              ObjectMapper objectMapper) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    // ─── Book ─────────────────────────────────────────────────────────────────

    /**
     * Books an available slot for the authenticated patient.
     *
     * <p>Flow:
     * <ol>
     *   <li>Check idempotency key — return cached response if seen before</li>
     *   <li>Load slot with a pessimistic read, verify it is AVAILABLE</li>
     *   <li>Mark slot BOOKED and create Appointment — optimistic lock guards concurrent grabs</li>
     *   <li>Persist idempotency record + publish Kafka event</li>
     * </ol>
     *
     * @param userId        the authenticated user's DB PK
     * @param idempotencyKey client-supplied dedup key (from {@code Idempotency-Key} header)
     * @param request       booking payload
     */
    @Transactional
    public AppointmentResponse bookAppointment(Long userId, String idempotencyKey,
                                               BookAppointmentRequest request) {
        String requestHash = sha256(serializeRequest(request));

        // 1. Idempotency check
        if (idempotencyKey != null) {
            var existing = idempotencyKeyRepository.findByIdemKey(idempotencyKey);
            if (existing.isPresent()) {
                IdempotencyKey record = existing.get();
                // Guard: same key, different payload → 422
                if (!record.getRequestHash().equals(requestHash)) {
                    throw new IllegalArgumentException(
                            "Idempotency-Key reused with a different request body");
                }
                // Return cached response
                logger.info("Idempotency hit for key={}", idempotencyKey);
                return deserializeResponse(record.getResponseBody());
            }
        }

        // 2. Load patient profile
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient profile not found for user: " + userId));

        // 3. Load + lock slot
        Slot slot = slotRepository.findById(request.slotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + request.slotId()));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new SlotUnavailableException("Slot " + request.slotId() + " is no longer available");
        }

        // 4. Mark slot BOOKED (optimistic lock will fire if concurrent transaction wins)
        try {
            slot.setStatus(SlotStatus.BOOKED);
            slotRepository.save(slot);
        } catch (OptimisticLockingFailureException e) {
            throw new SlotUnavailableException("Slot " + request.slotId() +
                    " was taken by a concurrent request — please choose another slot");
        }

        // 5. Create Appointment
        Doctor doctor = slot.getDoctor();
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setSlot(slot);
        appointment.setAppointmentDate(slot.getSlotDate());
        appointment.setStartTime(slot.getStartTime());
        appointment.setEndTime(slot.getEndTime());
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setNotes(request.notes());
        appointment = appointmentRepository.save(appointment);

        AppointmentResponse response = AppointmentResponse.from(appointment);

        // 6. Persist idempotency record
        if (idempotencyKey != null) {
            saveIdempotencyRecord(idempotencyKey, requestHash, response, 201);
        }

        // 7. Publish Kafka event
        eventPublisher.publishAppointmentCreated(new AppointmentCreatedEvent(
                appointment.getId(),
                patient.getId(),
                doctor.getId(),
                appointment.getAppointmentDate().atTime(appointment.getStartTime()),
                doctor.getFirstName() + " " + doctor.getLastName(),
                patient.getUser().getEmail(),
                patient.getPhone(),
                appointment.getNotes()
        ));

        logger.info("Appointment {} booked: patient={}, doctor={}, slot={}",
                appointment.getId(), patient.getId(), doctor.getId(), slot.getId());
        return response;
    }

    // ─── Cancel ───────────────────────────────────────────────────────────────

    /**
     * Cancels an appointment. Only the owning patient or the assigned doctor may cancel.
     */
    @Transactional
    public AppointmentResponse cancelAppointment(Long appointmentId, Long userId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

        // Only the patient who booked or the assigned doctor may cancel
        boolean isPatientOwner = appointment.getPatient().getUser().getId().equals(userId);
        boolean isDoctorOwner  = appointment.getDoctor().getUser().getId().equals(userId);
        if (!isPatientOwner && !isDoctorOwner) {
            throw new AccessDeniedException("You are not authorised to cancel this appointment");
        }

        if (appointment.getStatus() == AppointmentStatus.COMPLETED ||
            appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot cancel an appointment with status: " + appointment.getStatus());
        }

        // Free the slot
        Slot slot = appointment.getSlot();
        if (slot != null) {
            slot.setStatus(SlotStatus.AVAILABLE);
            slotRepository.save(slot);
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment = appointmentRepository.save(appointment);

        // Publish Kafka event
        eventPublisher.publishAppointmentCancelled(new AppointmentCancelledEvent(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getDoctor().getId(),
                appointment.getAppointmentDate().atTime(appointment.getStartTime()),
                appointment.getDoctor().getFirstName() + " " + appointment.getDoctor().getLastName(),
                appointment.getPatient().getUser().getEmail(),
                "Cancelled by user",
                LocalDateTime.now()
        ));

        logger.info("Appointment {} cancelled by user {}", appointmentId, userId);
        return AppointmentResponse.from(appointment);
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    /**
     * Returns all appointments for the authenticated patient, newest first.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getMyAppointmentsAsPatient(Long userId) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient profile not found for user: " + userId));
        return appointmentRepository
                .findByPatientIdOrderByAppointmentDateDescStartTimeDesc(patient.getId())
                .stream().map(AppointmentResponse::from).toList();
    }

    /**
     * Returns all appointments for the authenticated doctor, newest first.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getMyAppointmentsAsDoctor(Long userId) {
        Doctor doctor = doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor profile not found for user: " + userId));
        return appointmentRepository
                .findByDoctorIdOrderByAppointmentDateDescStartTimeDesc(doctor.getId())
                .stream().map(AppointmentResponse::from).toList();
    }

    /**
     * Returns a single appointment by ID. Caller must own the appointment.
     */
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long appointmentId, Long userId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

        boolean isPatientOwner = appointment.getPatient().getUser().getId().equals(userId);
        boolean isDoctorOwner  = appointment.getDoctor().getUser().getId().equals(userId);
        if (!isPatientOwner && !isDoctorOwner) {
            throw new AccessDeniedException("You are not authorised to view this appointment");
        }
        return AppointmentResponse.from(appointment);
    }

    // ─── Idempotency helpers ───────────────────────────────────────────────────

    private void saveIdempotencyRecord(String key, String hash,
                                       AppointmentResponse response, int statusCode) {
        IdempotencyKey record = new IdempotencyKey();
        record.setIdemKey(key);
        record.setRequestHash(hash);
        record.setResponseBody(serializeResponse(response));
        record.setStatusCode(statusCode);
        record.setCreatedAt(LocalDateTime.now());
        record.setExpiresAt(LocalDateTime.now().plusHours(IDEMPOTENCY_TTL_HOURS));
        idempotencyKeyRepository.save(record);
    }

    private String serializeRequest(BookAppointmentRequest req) {
        try {
            return objectMapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            return req.slotId().toString();
        }
    }

    private String serializeResponse(AppointmentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private AppointmentResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, AppointmentResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached response", e);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
