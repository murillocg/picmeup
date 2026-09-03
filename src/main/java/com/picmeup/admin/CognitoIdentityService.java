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
 * <p>Only for people who will sign in with an emailed code. Google users are created by
 * federation on first sign-in, and pre-creating a native user owning the same address
 * risks colliding with that.
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
            log.info("Cognito not configured — skipping identity creation for {}", email);
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
