package com.ameen.healthcare.service;

import com.ameen.healthcare.dto.request.LoginRequest;
import com.ameen.healthcare.dto.request.RegisterRequest;
import com.ameen.healthcare.dto.response.AuthResponse;
import com.ameen.healthcare.entity.User;
import com.ameen.healthcare.exception.DuplicateResourceException;
import com.ameen.healthcare.repository.UserRepository;
import com.ameen.healthcare.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication use-cases: registration (with BCrypt hashing) and login
 * (delegating credential verification to the {@link AuthenticationManager}).
 * All business logic lives here, keeping the controller thin.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthenticationManager authenticationManager,
                       UserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();
        user = userRepository.save(user);

        String token = jwtService.generateToken(userDetailsService.loadUserByUsername(user.getEmail()));
        return AuthResponse.bearer(token, user.getId(), user.getEmail(), user.getRole());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow();   // authentication succeeded, so the user exists

        String token = jwtService.generateToken(userDetailsService.loadUserByUsername(user.getEmail()));
        return AuthResponse.bearer(token, user.getId(), user.getEmail(), user.getRole());
    }
}
