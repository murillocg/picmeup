package com.picmeup.common.config;

import org.springframework.security.core.AuthenticationException;

/**
 * The token is valid and the identity is proven, but this person has no access to the
 * application. Covers three distinct situations, which need distinct answers: the sign-in
 * carried no verified email, the address was never invited, or it was invited and has
 * since been disabled.
 *
 * <p>Thrown rather than returning an authority-free token: such a token still reports
 * {@code isAuthenticated() == true}, so any rule written as {@code .authenticated()}
 * would admit someone with no access. Failing here keeps that impossible.
 */
public class AccessNotGrantedException extends AuthenticationException {

    public enum Reason {
        /** The identity provider did not assert a verified email, so no user can be matched. */
        UNVERIFIED_EMAIL,
        /** No user record. Usually means they signed in with a different Google account. */
        NOT_INVITED,
        /** Invited once, since revoked. Telling them to seek an invite would be wrong. */
        DISABLED
    }

    private final Reason reason;
    private final String email;

    public AccessNotGrantedException(Reason reason, String email, String message) {
        super(message);
        this.reason = reason;
        this.email = email;
    }

    public Reason getReason() {
        return reason;
    }

    /** The address they actually signed in with — the thing support needs to see. */
    public String getEmail() {
        return email;
    }
}
