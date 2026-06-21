package com.ameen.healthcare.service;

import com.ameen.healthcare.dto.request.BookAppointmentRequest;
import com.ameen.healthcare.dto.response.AppointmentResponse;
import com.ameen.healthcare.entity.*;
import com.ameen.healthcare.enums.AppointmentStatus;
import com.ameen.healthcare.enums.Role;
import com.ameen.healthcare.enums.SlotStatus;
import com.ameen.healthcare.exception.ResourceNotFoundException;
import com.ameen.healthcare.exception.SlotUnavailableException;
import com.ameen.healthcare.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private SlotRepository slotRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private AppointmentEventPublisher eventPublisher;

    private AppointmentService service;
    private ObjectMapper objectMapper;

    private User patientUser;
    private User doctorUser;
    private Patient patient;
    private Doctor doctor;
    private Slot slot;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new AppointmentService(appointmentRepository, slotRepository, patientRepository,
                doctorRepository, idempotencyKeyRepository, eventPublisher, objectMapper);

        patientUser = User.builder().id(1L).email("patient@test.com").role(Role.PATIENT).build();
        doctorUser  = User.builder().id(2L).email("doctor@test.com").role(Role.DOCTOR).build();

        doctor = new Doctor();
        doctor.setId(1L);
        doctor.setUser(doctorUser);
        doctor.setFirstName("John");
        doctor.setLastName("Smith");
        doctor.setSpecialization("Cardiology");

        patient = new Patient();
        patient.setId(1L);
        patient.setUser(patientUser);
        patient.setFirstName("Jane");
        patient.setLastName("Doe");
        patient.setPhone("555-1234");

        slot = new Slot();
        slot.setId(10L);
        slot.setDoctor(doctor);
        slot.setSlotDate(LocalDate.now().plusDays(1));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));
        slot.setStatus(SlotStatus.AVAILABLE);

        appointment = new Appointment();
        appointment.setId(100L);
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setSlot(slot);
        appointment.setAppointmentDate(slot.getSlotDate());
        appointment.setStartTime(slot.getStartTime());
        appointment.setEndTime(slot.getEndTime());
        appointment.setStatus(AppointmentStatus.CONFIRMED);
    }

    // ─── bookAppointment ──────────────────────────────────────────────────────

    @Test
    void bookAppointment_happyPath_returnsResponse() {
        BookAppointmentRequest request = new BookAppointmentRequest(10L, "routine check");

        when(idempotencyKeyRepository.findByIdemKey("key-1")).thenReturn(Optional.empty());
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(patient));
        when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));
        when(slotRepository.save(slot)).thenReturn(slot);
        when(appointmentRepository.save(any())).thenReturn(appointment);

        AppointmentResponse response = service.bookAppointment(1L, "key-1", request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);
        verify(eventPublisher).publishAppointmentCreated(any());
        verify(idempotencyKeyRepository).save(any());
    }

    @Test
    void bookAppointment_idempotencyHit_returnsCachedResponse() throws Exception {
        BookAppointmentRequest request = new BookAppointmentRequest(10L, "routine check");
        String requestJson = objectMapper.writeValueAsString(request);
        String hash = sha256(requestJson);

        String cachedJson = objectMapper.writeValueAsString(
                new AppointmentResponse(100L, 10L, 1L, 1L, "John", "Smith",
                        "Cardiology", slot.getSlotDate(), slot.getStartTime(),
                        slot.getEndTime(), AppointmentStatus.CONFIRMED, "routine check"));

        IdempotencyKey record = new IdempotencyKey();
        record.setIdemKey("key-1");
        record.setRequestHash(hash);
        record.setResponseBody(cachedJson);
        record.setStatusCode(201);

        when(idempotencyKeyRepository.findByIdemKey("key-1")).thenReturn(Optional.of(record));

        AppointmentResponse response = service.bookAppointment(1L, "key-1", request);

        assertThat(response.id()).isEqualTo(100L);
        verify(appointmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishAppointmentCreated(any());
    }

    @Test
    void bookAppointment_idempotencyKeyReusedWithDifferentPayload_throwsIllegalArgument() {
        IdempotencyKey record = new IdempotencyKey();
        record.setIdemKey("key-1");
        record.setRequestHash("completely-different-hash");
        record.setResponseBody("{}");

        when(idempotencyKeyRepository.findByIdemKey("key-1")).thenReturn(Optional.of(record));

        BookAppointmentRequest request = new BookAppointmentRequest(10L, "routine check");

        assertThatThrownBy(() -> service.bookAppointment(1L, "key-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key reused");
    }

    @Test
    void bookAppointment_patientNotFound_throwsResourceNotFound() {
        when(idempotencyKeyRepository.findByIdemKey(any())).thenReturn(Optional.empty());
        when(patientRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bookAppointment(99L, "key-1", new BookAppointmentRequest(10L, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void bookAppointment_slotNotFound_throwsResourceNotFound() {
        when(idempotencyKeyRepository.findByIdemKey(any())).thenReturn(Optional.empty());
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(patient));
        when(slotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bookAppointment(1L, "key-1", new BookAppointmentRequest(99L, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void bookAppointment_slotAlreadyBooked_throwsSlotUnavailable() {
        slot.setStatus(SlotStatus.BOOKED);

        when(idempotencyKeyRepository.findByIdemKey(any())).thenReturn(Optional.empty());
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(patient));
        when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.bookAppointment(1L, "key-1", new BookAppointmentRequest(10L, null)))
                .isInstanceOf(SlotUnavailableException.class);
    }

    // ─── cancelAppointment ────────────────────────────────────────────────────

    @Test
    void cancelAppointment_byPatientOwner_cancelsAndFreesSlot() {
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(slotRepository.save(slot)).thenReturn(slot);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        AppointmentResponse response = service.cancelAppointment(100L, patientUser.getId());

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        verify(eventPublisher).publishAppointmentCancelled(any());
    }

    @Test
    void cancelAppointment_byDoctorOwner_cancels() {
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(slotRepository.save(slot)).thenReturn(slot);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        AppointmentResponse response = service.cancelAppointment(100L, doctorUser.getId());

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancelAppointment_byNonOwner_throwsAccessDenied() {
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.cancelAppointment(100L, 99L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelAppointment_alreadyCancelled_throwsIllegalState() {
        appointment.setStatus(AppointmentStatus.CANCELLED);
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.cancelAppointment(100L, patientUser.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelAppointment_notFound_throwsResourceNotFound() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelAppointment(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── getMyAppointmentsAsPatient ───────────────────────────────────────────

    @Test
    void getMyAppointmentsAsPatient_returnsPageForPatient() {
        Pageable pageable = PageRequest.of(0, 20);
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findByPatientIdOrderByAppointmentDateDescStartTimeDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(appointment)));

        var result = service.getMyAppointmentsAsPatient(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).patientId()).isEqualTo(1L);
    }

    // ─── getAppointmentById ───────────────────────────────────────────────────

    @Test
    void getAppointmentById_nonOwner_throwsAccessDenied() {
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.getAppointmentById(100L, 99L))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
