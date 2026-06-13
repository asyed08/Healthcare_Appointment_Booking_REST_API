package com.ameen.healthcare.service;

import com.ameen.healthcare.dto.request.PatientProfileRequest;
import com.ameen.healthcare.dto.response.PatientProfileResponse;
import com.ameen.healthcare.entity.Patient;
import com.ameen.healthcare.entity.User;
import com.ameen.healthcare.exception.DuplicateResourceException;
import com.ameen.healthcare.exception.ResourceNotFoundException;
import com.ameen.healthcare.repository.PatientRepository;
import com.ameen.healthcare.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for patient profile management.
 */
@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    public PatientService(PatientRepository patientRepository, UserRepository userRepository) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a patient profile for the given authenticated user.
     * A user may only have one profile.
     */
    @Transactional
    public PatientProfileResponse createProfile(Long userId, PatientProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (patientRepository.findByUserId(userId).isPresent()) {
            throw new DuplicateResourceException("Patient profile already exists for user: " + userId);
        }

        Patient patient = new Patient();
        patient.setUser(user);
        patient.setFirstName(request.firstName());
        patient.setLastName(request.lastName());
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setPhone(request.phone());
        patient.setAddress(request.address());

        return PatientProfileResponse.from(patientRepository.save(patient));
    }

    /**
     * Returns the patient profile owned by the given user.
     */
    @Transactional(readOnly = true)
    public PatientProfileResponse getMyProfile(Long userId) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No patient profile for user: " + userId));
        return PatientProfileResponse.from(patient);
    }

    /**
     * Returns a patient profile by internal patient ID.
     * Only the owning patient or a doctor may call this.
     */
    @Transactional(readOnly = true)
    public PatientProfileResponse getProfileById(Long patientId, Long callerUserId, boolean isDoctor) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientId));

        boolean isOwner = patient.getUser().getId().equals(callerUserId);
        if (!isOwner && !isDoctor) {
            throw new AccessDeniedException("You are not authorised to view this profile");
        }
        return PatientProfileResponse.from(patient);
    }

    /**
     * Updates mutable profile fields. Only the owning patient may call this.
     */
    @Transactional
    public PatientProfileResponse updateProfile(Long userId, PatientProfileRequest request) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No patient profile for user: " + userId));

        patient.setFirstName(request.firstName());
        patient.setLastName(request.lastName());
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setPhone(request.phone());
        patient.setAddress(request.address());

        return PatientProfileResponse.from(patientRepository.save(patient));
    }
}
