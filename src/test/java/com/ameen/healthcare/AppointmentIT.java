package com.ameen.healthcare;

import com.ameen.healthcare.dto.request.AvailabilityRequest;
import com.ameen.healthcare.dto.request.BookAppointmentRequest;
import com.ameen.healthcare.dto.request.DoctorProfileRequest;
import com.ameen.healthcare.dto.request.PatientProfileRequest;
import com.ameen.healthcare.dto.request.RegisterRequest;
import com.ameen.healthcare.dto.response.ApiError;
import com.ameen.healthcare.dto.response.AppointmentResponse;
import com.ameen.healthcare.dto.response.AuthResponse;
import com.ameen.healthcare.dto.response.DoctorProfileResponse;
import com.ameen.healthcare.dto.response.SlotResponse;
import com.ameen.healthcare.enums.AppointmentStatus;
import com.ameen.healthcare.enums.Role;
import com.ameen.healthcare.service.AppointmentEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration tests for the appointment booking flow.
 *
 * Each test spins up a real HTTP server backed by a Testcontainers PostgreSQL
 * database (via BaseIntegrationTest). Kafka is replaced by a @MockBean so no
 * broker is needed. Every test creates its own uniquely-named users so tests
 * can run in any order without sharing state.
 */
@SuppressWarnings("null") // HttpHeaders → @NonNull MultiValueMap is a Spring API annotation mismatch; safe at runtime
class AppointmentIT extends BaseIntegrationTest {

    @MockBean
    private AppointmentEventPublisher eventPublisher;

    @Autowired
    private TestRestTemplate rest;

    // ─── Tests ───────────────────────────────────────────────────────────────────

    @Test
    void bookAppointment_happyPath_returnsConfirmedAppointment() {
        String uid = uid();
        String doctorToken = register("dr." + uid + "@test.com", Role.DOCTOR);
        long doctorId = createDoctorProfile(doctorToken, uid);
        addAvailability(doctorToken, doctorId);
        Long slotId = getFirstAvailableSlotId(doctorToken, doctorId);

        String patientToken = register("patient." + uid + "@test.com", Role.PATIENT);
        createPatientProfile(patientToken, uid);

        ResponseEntity<AppointmentResponse> resp = book(patientToken, slotId, UUID.randomUUID().toString());
        AppointmentResponse body = Objects.requireNonNull(resp.getBody());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(body.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(body.slotId()).isEqualTo(slotId);
    }

    @Test
    void bookAppointment_sameIdempotencyKey_returnsSameResponse() {
        String uid = uid();
        String doctorToken = register("dr." + uid + "@test.com", Role.DOCTOR);
        long doctorId = createDoctorProfile(doctorToken, uid);
        addAvailability(doctorToken, doctorId);
        Long slotId = getFirstAvailableSlotId(doctorToken, doctorId);

        String patientToken = register("patient." + uid + "@test.com", Role.PATIENT);
        createPatientProfile(patientToken, uid);

        String idempotencyKey = UUID.randomUUID().toString();
        ResponseEntity<AppointmentResponse> first  = book(patientToken, slotId, idempotencyKey);
        ResponseEntity<AppointmentResponse> second = book(patientToken, slotId, idempotencyKey);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(Objects.requireNonNull(second.getBody()).id())
                .isEqualTo(Objects.requireNonNull(first.getBody()).id());
    }

    @Test
    void bookAppointment_slotAlreadyTaken_returnsConflict() {
        String uid = uid();
        String doctorToken = register("dr." + uid + "@test.com", Role.DOCTOR);
        long doctorId = createDoctorProfile(doctorToken, uid);
        addAvailability(doctorToken, doctorId);
        Long slotId = getFirstAvailableSlotId(doctorToken, doctorId);

        // First patient books the slot
        String uid1 = uid();
        String patient1Token = register("p1." + uid1 + "@test.com", Role.PATIENT);
        createPatientProfile(patient1Token, uid1);
        assertThat(book(patient1Token, slotId, UUID.randomUUID().toString()).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // Second patient tries the same slot — should get 409
        String uid2 = uid();
        String patient2Token = register("p2." + uid2 + "@test.com", Role.PATIENT);
        createPatientProfile(patient2Token, uid2);
        ResponseEntity<ApiError> resp = bookExpectError(patient2Token, slotId, UUID.randomUUID().toString());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(Objects.requireNonNull(resp.getBody()).message()).contains("no longer available");
    }

    @Test
    void cancelAppointment_byPatient_returnsCancel() {
        String uid = uid();
        String doctorToken = register("dr." + uid + "@test.com", Role.DOCTOR);
        long doctorId = createDoctorProfile(doctorToken, uid);
        addAvailability(doctorToken, doctorId);
        Long slotId = getFirstAvailableSlotId(doctorToken, doctorId);

        String patientToken = register("patient." + uid + "@test.com", Role.PATIENT);
        createPatientProfile(patientToken, uid);
        Long appointmentId = Objects.requireNonNull(
                book(patientToken, slotId, UUID.randomUUID().toString()).getBody()).id();

        ResponseEntity<AppointmentResponse> cancelResp = rest.exchange(
                "/api/v1/appointments/" + appointmentId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(patientToken)),
                AppointmentResponse.class
        );

        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(cancelResp.getBody()).status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancelAppointment_byDoctor_returnsCancel() {
        String uid = uid();
        String doctorToken = register("dr." + uid + "@test.com", Role.DOCTOR);
        long doctorId = createDoctorProfile(doctorToken, uid);
        addAvailability(doctorToken, doctorId);
        Long slotId = getFirstAvailableSlotId(doctorToken, doctorId);

        String patientToken = register("patient." + uid + "@test.com", Role.PATIENT);
        createPatientProfile(patientToken, uid);
        Long appointmentId = Objects.requireNonNull(
                book(patientToken, slotId, UUID.randomUUID().toString()).getBody()).id();

        ResponseEntity<AppointmentResponse> cancelResp = rest.exchange(
                "/api/v1/appointments/" + appointmentId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(doctorToken)),
                AppointmentResponse.class
        );

        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(cancelResp.getBody()).status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void bookAppointment_withoutToken_returns4xx() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(new BookAppointmentRequest(1L, null), headers),
                String.class
        );

        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void bookAppointment_withDoctorRole_returnsForbidden() {
        String uid = uid();
        String doctorToken = register("dr." + uid + "@test.com", Role.DOCTOR);
        createDoctorProfile(doctorToken, uid);

        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(new BookAppointmentRequest(1L, null), authHeaders(doctorToken)),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String register(String email, Role role) {
        var req = new RegisterRequest(email, "password123", role);
        ResponseEntity<AuthResponse> resp = rest.postForEntity("/api/v1/auth/register", req, AuthResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(resp.getBody()).token();
    }

    private long createDoctorProfile(String token, String uid) {
        var req = new DoctorProfileRequest("Dr", uid, "Cardiology", "LIC-" + uid, "555-0000", null);
        ResponseEntity<DoctorProfileResponse> resp = rest.exchange(
                "/api/v1/doctors",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeaders(token)),
                DoctorProfileResponse.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(resp.getBody()).id();
    }

    /** Adds a 9:00–12:00 availability window for today's day of week, generating slots immediately. */
    private void addAvailability(String token, long doctorId) {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        var req = new AvailabilityRequest(today, LocalTime.of(9, 0), LocalTime.of(12, 0));
        ResponseEntity<Void> resp = rest.exchange(
                "/api/v1/doctors/" + doctorId + "/availability",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeaders(token)),
                Void.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private Long getFirstAvailableSlotId(String token, long doctorId) {
        ResponseEntity<List<SlotResponse>> resp = rest.exchange(
                "/api/v1/doctors/" + doctorId + "/slots",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<SlotResponse> slots = Objects.requireNonNull(resp.getBody());
        assertThat(slots).isNotEmpty();
        return slots.get(0).id();
    }

    private void createPatientProfile(String token, String uid) {
        var req = new PatientProfileRequest("Patient", uid, LocalDate.of(1990, 1, 1), "555-1111", "123 Main St");
        ResponseEntity<Void> resp = rest.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeaders(token)),
                Void.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<AppointmentResponse> book(String token, Long slotId, String idempotencyKey) {
        HttpHeaders headers = authHeaders(token);
        headers.set("Idempotency-Key", idempotencyKey);
        return rest.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(new BookAppointmentRequest(slotId, "routine check"), headers),
                AppointmentResponse.class
        );
    }

    private ResponseEntity<ApiError> bookExpectError(String token, Long slotId, String idempotencyKey) {
        HttpHeaders headers = authHeaders(token);
        headers.set("Idempotency-Key", idempotencyKey);
        return rest.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(new BookAppointmentRequest(slotId, "routine check"), headers),
                ApiError.class
        );
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
