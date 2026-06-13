package com.ameen.healthcare.service;

import com.ameen.healthcare.dto.request.AvailabilityRequest;
import com.ameen.healthcare.dto.request.DoctorProfileRequest;
import com.ameen.healthcare.dto.response.AvailabilityResponse;
import com.ameen.healthcare.dto.response.DoctorProfileResponse;
import com.ameen.healthcare.dto.response.SlotResponse;
import com.ameen.healthcare.entity.Availability;
import com.ameen.healthcare.entity.Doctor;
import com.ameen.healthcare.entity.User;
import com.ameen.healthcare.enums.SlotStatus;
import com.ameen.healthcare.exception.DuplicateResourceException;
import com.ameen.healthcare.exception.ResourceNotFoundException;
import com.ameen.healthcare.repository.AvailabilityRepository;
import com.ameen.healthcare.repository.DoctorRepository;
import com.ameen.healthcare.repository.SlotRepository;
import com.ameen.healthcare.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Business logic for doctor profile management and availability scheduling.
 */
@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final AvailabilityRepository availabilityRepository;
    private final SlotRepository slotRepository;
    private final SlotGenerationService slotGenerationService;

    public DoctorService(DoctorRepository doctorRepository,
                         UserRepository userRepository,
                         AvailabilityRepository availabilityRepository,
                         SlotRepository slotRepository,
                         SlotGenerationService slotGenerationService) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.availabilityRepository = availabilityRepository;
        this.slotRepository = slotRepository;
        this.slotGenerationService = slotGenerationService;
    }

    // ─── Profile ──────────────────────────────────────────────────────────────

    /**
     * Creates a doctor profile for the given authenticated user.
     * A user may only have one profile.
     */
    @Transactional
    public DoctorProfileResponse createProfile(Long userId, DoctorProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (doctorRepository.findByUserId(userId).isPresent()) {
            throw new DuplicateResourceException("Doctor profile already exists for user: " + userId);
        }
        if (doctorRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new DuplicateResourceException("License number already registered: " + request.licenseNumber());
        }

        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setFirstName(request.firstName());
        doctor.setLastName(request.lastName());
        doctor.setSpecialization(request.specialization());
        doctor.setLicenseNumber(request.licenseNumber());
        doctor.setPhone(request.phone());
        doctor.setBio(request.bio());

        return DoctorProfileResponse.from(doctorRepository.save(doctor));
    }

    /**
     * Returns a doctor profile by internal doctor ID.
     */
    @Transactional(readOnly = true)
    public DoctorProfileResponse getProfile(Long doctorId) {
        Doctor doctor = findDoctor(doctorId);
        return DoctorProfileResponse.from(doctor);
    }

    /**
     * Returns the doctor profile owned by the given user.
     */
    @Transactional(readOnly = true)
    public DoctorProfileResponse getProfileByUserId(Long userId) {
        Doctor doctor = doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile for user: " + userId));
        return DoctorProfileResponse.from(doctor);
    }

    /**
     * Updates mutable profile fields. Only the owning user may call this.
     */
    @Transactional
    public DoctorProfileResponse updateProfile(Long doctorId, Long callerUserId, DoctorProfileRequest request) {
        Doctor doctor = findDoctor(doctorId);
        assertOwner(doctor, callerUserId);

        // Reject license-number change to a number already taken by someone else
        if (!doctor.getLicenseNumber().equals(request.licenseNumber())
                && doctorRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new DuplicateResourceException("License number already registered: " + request.licenseNumber());
        }

        doctor.setFirstName(request.firstName());
        doctor.setLastName(request.lastName());
        doctor.setSpecialization(request.specialization());
        doctor.setLicenseNumber(request.licenseNumber());
        doctor.setPhone(request.phone());
        doctor.setBio(request.bio());

        return DoctorProfileResponse.from(doctorRepository.save(doctor));
    }

    /**
     * Lists all doctors, optionally filtered by specialization.
     */
    @Transactional(readOnly = true)
    public List<DoctorProfileResponse> listDoctors(String specialization) {
        return doctorRepository.findBySpecializationOptional(specialization)
                .stream()
                .map(DoctorProfileResponse::from)
                .toList();
    }

    // ─── Availability ──────────────────────────────────────────────────────────

    /**
     * Adds a weekly availability window and immediately generates slots
     * for the next 30 days so patients can book right away.
     */
    @Transactional
    public AvailabilityResponse addAvailability(Long doctorId, Long callerUserId, AvailabilityRequest request) {
        Doctor doctor = findDoctor(doctorId);
        assertOwner(doctor, callerUserId);

        Availability availability = new Availability();
        availability.setDoctor(doctor);
        availability.setDayOfWeek(request.dayOfWeek());
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());

        Availability saved = availabilityRepository.save(availability);

        // Eagerly generate slots for the next 30 days
        slotGenerationService.generateSlotsForDoctor(doctorId, 30);

        return AvailabilityResponse.from(saved);
    }

    /**
     * Removes a weekly availability window. Only the owning doctor may do this.
     */
    @Transactional
    public void removeAvailability(Long doctorId, Long availabilityId, Long callerUserId) {
        Doctor doctor = findDoctor(doctorId);
        assertOwner(doctor, callerUserId);

        Availability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Availability not found: " + availabilityId));

        if (!availability.getDoctor().getId().equals(doctorId)) {
            throw new AccessDeniedException("Availability does not belong to this doctor");
        }

        availabilityRepository.delete(availability);
    }

    // ─── Slots ────────────────────────────────────────────────────────────────

    /**
     * Returns available slots for a doctor from today onwards.
     */
    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailableSlots(Long doctorId) {
        findDoctor(doctorId);  // 404 guard
        return slotRepository
                .findByDoctorIdAndSlotDateGreaterThanEqualAndStatusOrderBySlotDateAscStartTimeAsc(
                        doctorId, LocalDate.now(), SlotStatus.AVAILABLE)
                .stream()
                .map(SlotResponse::from)
                .toList();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Doctor findDoctor(Long doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));
    }

    private void assertOwner(Doctor doctor, Long callerUserId) {
        if (!doctor.getUser().getId().equals(callerUserId)) {
            throw new AccessDeniedException("You do not own this doctor profile");
        }
    }
}
