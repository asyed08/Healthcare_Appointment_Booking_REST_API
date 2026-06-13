package com.ameen.healthcare.dto.request;

import com.ameen.healthcare.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Creates a {@link com.ameen.healthcare.entity.User}
 * with the chosen {@link Role}; the matching domain profile (Doctor/Patient)
 * is created in a later step of the respective feature.
 */
public record RegisterRequest(

        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8, max = 100)
        String password,

        @NotNull
        Role role
) {
}
