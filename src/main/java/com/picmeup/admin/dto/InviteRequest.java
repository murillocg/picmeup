package com.picmeup.admin.dto;

import com.picmeup.common.user.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteRequest(
        @NotBlank @Email String email,
        String name,
        @NotNull AppUser.Role role
) {
}
