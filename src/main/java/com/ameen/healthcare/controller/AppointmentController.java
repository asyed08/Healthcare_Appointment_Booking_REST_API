package com.ameen.healthcare.controller;

import com.ameen.healthcare.dto.request.BookAppointmentRequest;
import com.ameen.healthcare.dto.response.AppointmentResponse;
import com.ameen.healthcare.exception.ResourceNotFoundException;
import com.ameen.healthcare.repository.UserRepository;
import com.ameen.healthcare.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for appointment booking and management.
 *
 * <pre>
 *   POST   /api/v1/appointments                  – book a slot (PATIENT)
 *   DELETE /api/v1/appointments/{id}             – cancel (PATIENT or DOCTOR)
 *   GET    /api/v1/appointments/my/patient        – my bookings as patient
 *   GET    /api/v1/appointments/my/doctor         – my bookings as doctor
 *   GET    /api/v1/appointments/{id}             – get single appointment
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Appointments", description = "Book, cancel and query appointments")
@SecurityRequirement(name = "bearerAuth")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final UserRepository userRepository;

    public AppointmentController(AppointmentService appointmentService, UserRepository userRepository) {
        this.appointmentService = appointmentService;
        this.userRepository = userRepository;
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Book an available slot (idempotent via Idempotency-Key header)")
    public ResponseEntity<AppointmentResponse> bookAppointment(
            @Valid @RequestBody BookAppointmentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserDetails principal) {

        Long userId = resolveUserId(principal);
        AppointmentResponse response = appointmentService.bookAppointment(userId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel an appointment (patient or assigned doctor)")
    public ResponseEntity<AppointmentResponse> cancelAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {

        Long userId = resolveUserId(principal);
        return ResponseEntity.ok(appointmentService.cancelAppointment(id, userId));
    }

    @GetMapping("/my/patient")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Get my appointments as a patient, newest first")
    public ResponseEntity<Page<AppointmentResponse>> getMyPatientAppointments(
            @AuthenticationPrincipal UserDetails principal,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        Long userId = resolveUserId(principal);
        return ResponseEntity.ok(appointmentService.getMyAppointmentsAsPatient(userId, pageable));
    }

    @GetMapping("/my/doctor")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Get my appointments as a doctor, newest first")
    public ResponseEntity<Page<AppointmentResponse>> getMyDoctorAppointments(
            @AuthenticationPrincipal UserDetails principal,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        Long userId = resolveUserId(principal);
        return ResponseEntity.ok(appointmentService.getMyAppointmentsAsDoctor(userId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single appointment by ID (must be owner)")
    public ResponseEntity<AppointmentResponse> getAppointmentById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {

        Long userId = resolveUserId(principal);
        return ResponseEntity.ok(appointmentService.getAppointmentById(id, userId));
    }

    private Long resolveUserId(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"))
                .getId();
    }
}
