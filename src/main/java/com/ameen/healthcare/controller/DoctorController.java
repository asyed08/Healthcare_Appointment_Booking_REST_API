package com.ameen.healthcare.controller;

import com.ameen.healthcare.dto.request.AvailabilityRequest;
import com.ameen.healthcare.dto.request.DoctorProfileRequest;
import com.ameen.healthcare.dto.response.AvailabilityResponse;
import com.ameen.healthcare.dto.response.DoctorProfileResponse;
import com.ameen.healthcare.dto.response.SlotResponse;
import com.ameen.healthcare.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import com.ameen.healthcare.repository.UserRepository;
import com.ameen.healthcare.exception.ResourceNotFoundException;

import java.util.List;

/**
 * REST endpoints for doctor profile and availability management.
 *
 * <p>URL structure:
 * <pre>
 *   POST   /api/v1/doctors                        – create own profile (DOCTOR)
 *   GET    /api/v1/doctors                        – list all doctors (public)
 *   GET    /api/v1/doctors/{id}                   – get doctor by ID (public)
 *   PUT    /api/v1/doctors/{id}                   – update own profile (DOCTOR)
 *   POST   /api/v1/doctors/{id}/availability      – add availability window (DOCTOR)
 *   DELETE /api/v1/doctors/{id}/availability/{avid} – remove availability (DOCTOR)
 *   GET    /api/v1/doctors/{id}/slots             – get available slots (authenticated)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/doctors")
@Tag(name = "Doctors", description = "Doctor profile and availability management")
public class DoctorController {

    private final DoctorService doctorService;
    private final UserRepository userRepository;

    public DoctorController(DoctorService doctorService, UserRepository userRepository) {
        this.doctorService = doctorService;
        this.userRepository = userRepository;
    }

    // ─── Profile endpoints ────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Create a doctor profile for the authenticated user")
    public ResponseEntity<DoctorProfileResponse> createProfile(
            @Valid @RequestBody DoctorProfileRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = resolveUserId(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(doctorService.createProfile(userId, request));
    }

    @GetMapping
    @Operation(summary = "List all doctors, optionally filtered by specialization")
    public ResponseEntity<List<DoctorProfileResponse>> listDoctors(
            @RequestParam(required = false) String specialization) {
        return ResponseEntity.ok(doctorService.listDoctors(specialization));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a doctor profile by ID")
    public ResponseEntity<DoctorProfileResponse> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(doctorService.getProfile(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Update the authenticated doctor's profile")
    public ResponseEntity<DoctorProfileResponse> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody DoctorProfileRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = resolveUserId(principal);
        return ResponseEntity.ok(doctorService.updateProfile(id, userId, request));
    }

    // ─── Availability endpoints ───────────────────────────────────────────────

    @PostMapping("/{id}/availability")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Add a weekly availability window and generate slots for next 30 days")
    public ResponseEntity<AvailabilityResponse> addAvailability(
            @PathVariable Long id,
            @Valid @RequestBody AvailabilityRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = resolveUserId(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(doctorService.addAvailability(id, userId, request));
    }

    @DeleteMapping("/{id}/availability/{availabilityId}")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Remove a weekly availability window")
    public ResponseEntity<Void> removeAvailability(
            @PathVariable Long id,
            @PathVariable Long availabilityId,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = resolveUserId(principal);
        doctorService.removeAvailability(id, availabilityId, userId);
        return ResponseEntity.noContent().build();
    }

    // ─── Slots endpoint ───────────────────────────────────────────────────────

    @GetMapping("/{id}/slots")
    @Operation(summary = "Get available bookable slots for a doctor")
    public ResponseEntity<List<SlotResponse>> getAvailableSlots(@PathVariable Long id) {
        return ResponseEntity.ok(doctorService.getAvailableSlots(id));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    /** Resolves the authenticated user's DB primary key from their email (JWT subject). */
    private Long resolveUserId(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"))
                .getId();
    }
}
