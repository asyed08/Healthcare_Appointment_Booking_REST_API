package com.ameen.healthcare.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Login payload exchanging credentials for a JWT. */
public record LoginRequest(

        @NotBlank @Email
        String email,

        @NotBlank
        String password
) {
}
