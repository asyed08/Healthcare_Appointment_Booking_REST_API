package com.ameen.healthcare.service;

import com.ameen.healthcare.dto.request.LoginRequest;
import com.ameen.healthcare.dto.request.RegisterRequest;
import com.ameen.healthcare.dto.response.AuthResponse;
import com.ameen.healthcare.entity.User;
import com.ameen.healthcare.enums.Role;
import com.ameen.healthcare.exception.DuplicateResourceException;
import com.ameen.healthcare.repository.UserRepository;
import com.ameen.healthcare.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserDetailsService userDetailsService;
    @Mock private UserDetails userDetails;

    @InjectMocks
    private AuthService service;

    @Test
    void register_newEmail_returnsToken() {
        RegisterRequest request = new RegisterRequest("new@test.com", "password123", Role.PATIENT);
        User saved = User.builder().id(1L).email("new@test.com").role(Role.PATIENT).build();

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(saved);
        when(userDetailsService.loadUserByUsername("new@test.com")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("jwt-token");

        AuthResponse response = service.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.email()).isEqualTo("new@test.com");
        assertThat(response.role()).isEqualTo(Role.PATIENT);
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void register_duplicateEmail_throwsDuplicateResource() {
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("taken@test.com", "pass", Role.PATIENT)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("taken@test.com");
    }

    @Test
    void login_validCredentials_returnsToken() {
        LoginRequest request = new LoginRequest("user@test.com", "password123");
        User user = User.builder().id(1L).email("user@test.com").role(Role.DOCTOR).build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("jwt-token");

        AuthResponse response = service.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.role()).isEqualTo(Role.DOCTOR);
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }
}
