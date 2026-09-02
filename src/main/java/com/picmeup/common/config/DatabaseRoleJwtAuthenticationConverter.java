package com.picmeup.common.config;

import com.picmeup.common.config.AccessNotGrantedException.Reason;
import com.picmeup.common.user.AppUser;
import com.picmeup.common.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Turns a validated Cognito token into authorities from our own database.
 *
 * <p>Cognito proves identity; it does not grant permission. Google federation cannot be
 * restricted at the user pool — any Google account can complete sign-in and Cognito will
 * create a pool user for it — so invite-only is enforced here: an email with no
 * {@code users} row is refused outright.
 *
 * <p>Refusals are logged at WARN. There are only a handful of authorised people, so the
 * volume is negligible, and a rejected sign-in is precisely what an operator wants to see
 * — INFO is routinely filtered out in production.
 */
@Component
public class DatabaseRoleJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Logger log = LoggerFactory.getLogger(DatabaseRoleJwtAuthenticationConverter.class);

    private final AppUserRepository users;
    private final Set<String> bootstrapAdminEmails;

    public DatabaseRoleJwtAuthenticationConverter(
            AppUserRepository users,
            @Value("${app.admin.bootstrap-emails:}") String bootstrapEmails) {
        this.users = users;
        this.bootstrapAdminEmails = bootstrapEmails.isBlank()
                ? Set.of()
                : Set.of(bootstrapEmails.toLowerCase().split("\\s*,\\s*"));
    }

    @Override
    @Transactional
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String email = jwt.getClaimAsString("email");

        // Without a verified email there is nothing to match a user against. Google always
        // supplies one; a token that does not is not something to trust with authority.
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            log.warn("Refused sign-in: token carried no verified email (sub={})", jwt.getSubject());
            throw new AccessNotGrantedException(Reason.UNVERIFIED_EMAIL, email,
                    "This sign-in did not provide a verified email address.");
        }

        String normalised = email.toLowerCase();

        var user = users.findByEmailIgnoreCase(normalised)
                .orElseGet(() -> bootstrapAdminEmails.contains(normalised)
                        ? createBootstrapAdmin(normalised, jwt)
                        : null);

        // Usually harmless — they signed in with a different Google account than the
        // invited one — but it is also what probing looks like, so it is worth seeing.
        if (user == null) {
            log.warn("Refused sign-in: {} has no user record", normalised);
            throw new AccessNotGrantedException(Reason.NOT_INVITED, normalised,
                    "%s has not been given access. Ask the team to invite this address.".formatted(normalised));
        }

        // Distinct from NOT_INVITED on purpose. This person was invited and had access
        // removed, so telling them to ask for an invite would send them the wrong way —
        // and someone whose access was revoked trying to return is worth noticing.
        if (!user.canSignIn()) {
            log.warn("Refused sign-in: access for {} is disabled", normalised);
            throw new AccessNotGrantedException(Reason.DISABLED, normalised,
                    "Access for %s has been turned off.".formatted(normalised));
        }

        user.recordLogin(jwt.getSubject());
        users.save(user);

        var authorities = List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new JwtAuthenticationToken(jwt, authorities, normalised);
    }

    /**
     * Bootstraps the very first administrators. Without this nobody could issue the first
     * invite, because issuing invites requires already being an admin.
     */
    private AppUser createBootstrapAdmin(String email, Jwt jwt) {
        log.info("Creating bootstrap admin for {} from app.admin.bootstrap-emails", email);
        var user = new AppUser(email, jwt.getClaimAsString("name"), AppUser.Role.ADMIN, null);
        user.recordLogin(jwt.getSubject());
        return users.save(user);
    }
}
