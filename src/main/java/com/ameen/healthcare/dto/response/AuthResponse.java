package com.ameen.healthcare.dto.response;

import com.ameen.healthcare.enums.Role;

/** Returned on successful register/login. */
public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String email,
        Role role
) {
    public static AuthResponse bearer(String token, Long userId, String email, Role role) {
        return new AuthResponse(token, "Bearer", userId, email, role);
    }
}
