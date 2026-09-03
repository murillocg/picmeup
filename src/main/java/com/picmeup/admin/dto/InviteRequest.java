package com.picmeup.admin.dto;

import com.picmeup.common.user.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteRequest(
        @NotBlank @Email String email,
        String name,
        @NotNull AppUser.Role role,
        @NotNull SignInMethod signInMethod
) {
    /**
     * How this person will sign in. It decides whether we pre-create a Cognito identity:
     * an emailed code needs one to exist, whereas Google federation creates its own on
     * first sign-in — and pre-creating a native user for the same address risks
     * colliding with that.
     */
    public enum SignInMethod {
        GOOGLE, EMAIL_CODE
    }
}
