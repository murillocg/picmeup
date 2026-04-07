package com.picmeup.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RedeemPassRequest(
        @NotBlank @Email String email
) {
}
