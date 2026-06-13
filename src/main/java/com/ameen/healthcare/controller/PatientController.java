package com.ameen.healthcare.controller;

import com.ameen.healthcare.dto.request.PatientProfileRequest;
import com.ameen.healthcare.dto.response.PatientProfileResponse;
import com.ameen.healthcare.exception.ResourceNotFoundException;
import com.ameen.healthcare.repository.UserRepository;
import com.ameen.healthcare.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for patient profile management.
 *
 * <pre>
 *   POST /api/v1/patients          – create own profile  (PATIENT)
 *   GET  /api/v1/patients/me       – get own profile     (PATIENT)
 *   PUT  /api/v1/patients/me       – update own profile  (PATIENT)
 *   GET  /api/v1/patients/{id}     – get by ID           (DOCTOR or owner)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Patients", description = "Patient profile management")
public class PatientController {

    private final PatientService patientService;
    private final UserRepository userRepository;

    public PatientController(PatientService patientService, UserRepository userRepository) {
        this.patientService = patientService;
        this.userRepository = userRepository;
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Create a patient profile for the authenticated user")
    public ResponseEntity<PatientProfileResponse> createProfile(
            @Valid @RequestBody PatientProfileRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(patientService.createProfile(resolveUserId(principal), request));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Get the authenticated patient's own profile")
    public ResponseEntity<PatientProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(patientService.getMyProfile(resolveUserId(principal)));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Update the authenticated patient's own profile")
    public ResponseEntity<PatientProfileResponse> updateProfile(
            @Valid @RequestBody PatientProfileRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(patientService.updateProfile(resolveUserId(principal), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a patient profile by ID (doctor or profile owner only)")
    public ResponseEntity<PatientProfileResponse> getProfileById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = resolveUserId(principal);
        boolean isDoctor = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
        return ResponseEntity.ok(patientService.getProfileById(id, userId, isDoctor));
    }

    private Long resolveUserId(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"))
                .getId();
    }
}
