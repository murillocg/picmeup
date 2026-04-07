package com.picmeup.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreatePassRequest(
        @NotBlank @Email String email
) {
}
