package com.picmeup.common.user;

import com.picmeup.common.config.AccessNotGrantedException;
import com.picmeup.common.config.AccessNotGrantedException.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Decides whether a proven identity may use the application, and as what.
 *
 * <p>Cognito proves who someone is; it does not grant permission. Federation cannot be
 * restricted at the user pool — any Google account, or anyone who can receive a one-time
 * code, completes sign-in and Cognito creates a pool user for them — so invite-only is
 * enforced here: an email with no {@code users} row is refused outright.
 *
 * <p>Called at sign-in to establish the session, and again on every subsequent request to
 * re-check status. Both paths share this method so revocation cannot take effect in one
 * and not the other.
 *
 * <p>Refusals are logged at WARN. There are only a handful of authorised people, so the
 * volume is negligible, and a rejected sign-in is precisely what an operator wants to see
 * — INFO is routinely filtered out in production.
 */
@Service
public class UserAccessService {

    private static final Logger log = LoggerFactory.getLogger(UserAccessService.class);

    private final AppUserRepository users;
    private final Set<String> bootstrapAdminEmails;

    public UserAccessService(AppUserRepository users,
                             @Value("${app.admin.bootstrap-emails:}") String bootstrapEmails) {
        this.users = users;
        this.bootstrapAdminEmails = bootstrapEmails.isBlank()
                ? Set.of()
                : Set.of(bootstrapEmails.toLowerCase().split("\\s*,\\s*"));
    }

    /**
     * Resolves the user for a freshly proven identity, recording the sign-in. Throws
     * rather than returning an empty result: returning a principal with no authorities
     * would still report {@code isAuthenticated() == true}, so any rule written as
     * {@code .authenticated()} would admit someone with no access.
     */
    @Transactional
    public AppUser resolveOnSignIn(String email, boolean emailVerified, String subject, String name) {
        // Without a verified email there is nothing to match a user against. Both Google
        // and an emailed one-time code establish this; a sign-in that does not is not
        // something to trust with authority.
        if (email == null || email.isBlank() || !emailVerified) {
            log.warn("Refused sign-in: no verified email on the identity (sub={})", subject);
            throw new AccessNotGrantedException(Reason.UNVERIFIED_EMAIL, email,
                    "This sign-in did not provide a verified email address.");
        }

        String normalised = email.toLowerCase();
        var user = requireActive(normalised, () -> bootstrapAdminEmails.contains(normalised)
                ? createBootstrapAdmin(normalised, name, subject)
                : null);

        user.recordLogin(subject);
        return users.save(user);
    }

    /**
     * Re-checks an established session. Authority lives in the database, not in the
     * session, so disabling someone takes effect on their next request rather than
     * whenever their session happens to expire.
     */
    @Transactional(readOnly = true)
    public AppUser requireStillActive(String email) {
        return requireActive(email.toLowerCase(), () -> null);
    }

    private AppUser requireActive(String normalised, java.util.function.Supplier<AppUser> fallback) {
        var user = users.findByEmailIgnoreCase(normalised).orElseGet(fallback);

        // Usually harmless — they signed in with a different account than the invited one
        // — but it is also what probing looks like, so it is worth seeing.
        if (user == null) {
            log.warn("Refused access: {} has no user record", normalised);
            throw new AccessNotGrantedException(Reason.NOT_INVITED, normalised,
                    "%s has not been given access. Ask the team to invite this address."
                            .formatted(normalised));
        }

        // Distinct from NOT_INVITED on purpose. This person was invited and had access
        // removed, so telling them to ask for an invite would send them the wrong way —
        // and someone whose access was revoked trying to return is worth noticing.
        if (!user.canSignIn()) {
            log.warn("Refused access: {} is disabled", normalised);
            throw new AccessNotGrantedException(Reason.DISABLED, normalised,
                    "Access for %s has been turned off.".formatted(normalised));
        }

        return user;
    }

    /**
     * Bootstraps the very first administrators. Without this nobody could issue the first
     * invite, because issuing invites requires already being an admin.
     */
    private AppUser createBootstrapAdmin(String email, String name, String subject) {
        log.info("Creating bootstrap admin for {} from app.admin.bootstrap-emails", email);
        var user = new AppUser(email, name, AppUser.Role.ADMIN, null);
        user.recordLogin(subject);
        return users.save(user);
    }
}
