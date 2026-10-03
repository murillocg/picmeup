package com.picmeup.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/**
 * Creates the Cognito identity an invited person signs in with.
 *
 * <p>An invite writes two things in different places: a {@code users} row, which grants
 * authority, and a Cognito user, which is what a one-time code is actually sent to.
 * Without the second there is nothing to send to — and because the app client sets
 * {@code prevent_user_existence_errors}, the login page cannot say so. It shows
 * "check your email" either way and the code never arrives.
 *
 * <p>Created for everyone invited, so each person can use whichever button they prefer.
 * Google federation does not need this profile, and is not harmed by it: Cognito creates
 * its own federated profile on first sign-in, and because this application resolves people
 * by verified email rather than by Cognito subject, both profiles lead to the same user.
 */
@Service
public class CognitoIdentityService {

    private static final Logger log = LoggerFactory.getLogger(CognitoIdentityService.class);

    private final ObjectProvider<CognitoIdentityProviderClient> clients;
    private final String userPoolId;

    public CognitoIdentityService(ObjectProvider<CognitoIdentityProviderClient> clients,
                                  @Value("${app.cognito.user-pool-id:}") String userPoolId) {
        this.clients = clients;
        this.userPoolId = userPoolId;
    }

    /**
     * Idempotent: re-inviting an address that already exists in the pool is a no-op
     * rather than an error.
     *
     * @return true if an identity now exists for the address
     */
    public boolean createPasswordlessUser(String email) {
        var client = clients.getIfAvailable();
        if (client == null || userPoolId.isBlank()) {
            // Expected with no Cognito configured, as in dev and test. In production it
            // means invitations are being issued that nobody can sign in to, and the
            // sign-in page cannot report a missing identity — so it is logged loudly
            // rather than passed over in silence.
            log.warn("Cognito is not configured (app.cognito.user-pool-id is unset) — "
                    + "no sign-in identity created for {}. If this is production, that "
                    + "invitation cannot be used until one exists.", email);
            return false;
        }

        try {
            client.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            // The admin is vouching for the address, and the sign-in code
                            // itself proves control of the inbox before any token is issued.
                            AttributeType.builder().name("email_verified").value("true").build())
                    // No temporary password: the pool allows passwordless sign-in, so the
                    // user is created CONFIRMED and can request a code straight away.
                    //
                    // SUPPRESS stops Cognito emailing its own invitation. Invitations are
                    // delivered out of band, and its default message talks about a
                    // temporary password that does not exist.
                    .messageAction(MessageActionType.SUPPRESS)
                    .build());

            log.info("Created Cognito identity for {}", email);
            return true;
        } catch (UsernameExistsException e) {
            log.info("Cognito identity already exists for {}", email);
            return true;
        }
    }
}
