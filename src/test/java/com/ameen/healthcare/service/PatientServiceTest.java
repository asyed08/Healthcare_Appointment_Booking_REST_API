package com.ameen.healthcare.service;

import com.ameen.healthcare.dto.request.PatientProfileRequest;
import com.ameen.healthcare.dto.response.PatientProfileResponse;
import com.ameen.healthcare.entity.Patient;
import com.ameen.healthcare.entity.User;
import com.ameen.healthcare.enums.Role;
import com.ameen.healthcare.exception.DuplicateResourceException;
import com.ameen.healthcare.exception.ResourceNotFoundException;
import com.ameen.healthcare.repository.PatientRepository;
import com.ameen.healthcare.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock private PatientRepository patientRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private PatientService service;

    private User user;
    private Patient patient;
    private PatientProfileRequest request;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("patient@test.com").role(Role.PATIENT).build();

        patient = new Patient();
        patient.setId(1L);
        patient.setUser(user);
        patient.setFirstName("Jane");
        patient.setLastName("Doe");
        patient.setPhone("555-1234");
        patient.setDateOfBirth(LocalDate.of(1990, 5, 15));

        request = new PatientProfileRequest("Jane", "Doe", LocalDate.of(1990, 5, 15), "555-1234", "123 Main St");
    }

    @Test
    void createProfile_newUser_returnsProfile() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(patientRepository.save(any())).thenReturn(patient);

        PatientProfileResponse response = service.createProfile(1L, request);

        assertThat(response.firstName()).isEqualTo("Jane");
        assertThat(response.lastName()).isEqualTo("Doe");
        verify(patientRepository).save(any());
    }

    @Test
    void createProfile_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createProfile(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createProfile_profileAlreadyExists_throwsDuplicateResource() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.createProfile(1L, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void getMyProfile_exists_returnsProfile() {
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(patient));

        PatientProfileResponse response = service.getMyProfile(1L);

        assertThat(response.firstName()).isEqualTo("Jane");
    }

    @Test
    void getMyProfile_notFound_throwsResourceNotFound() {
        when(patientRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyProfile(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProfileById_asOwner_returnsProfile() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));

        PatientProfileResponse response = service.getProfileById(1L, 1L, false);

        assertThat(response.firstName()).isEqualTo("Jane");
    }

    @Test
    void getProfileById_asDoctor_returnsProfile() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));

        PatientProfileResponse response = service.getProfileById(1L, 99L, true);

        assertThat(response.firstName()).isEqualTo("Jane");
    }

    @Test
    void getProfileById_nonOwnerNonDoctor_throwsAccessDenied() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.getProfileById(1L, 99L, false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateProfile_asOwner_updatesAndReturns() {
        PatientProfileRequest updateRequest = new PatientProfileRequest(
                "Janet", "Doe", LocalDate.of(1990, 5, 15), "555-9999", "456 New St");
        patient.setFirstName("Janet");

        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(patient));
        when(patientRepository.save(any())).thenReturn(patient);

        PatientProfileResponse response = service.updateProfile(1L, updateRequest);

        assertThat(response.firstName()).isEqualTo("Janet");
        verify(patientRepository).save(patient);
    }

    @Test
    void updateProfile_notFound_throwsResourceNotFound() {
        when(patientRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
