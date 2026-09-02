package com.picmeup.photo;

import com.picmeup.common.user.AppUser;
import com.picmeup.common.user.AppUserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Works out who is uploading, and whether they are allowed to upload here.
 *
 * <p>Kept out of PhotoService so the upload path does not have to reach into the security
 * context in two places, and so the session-admin special case lives in exactly one spot
 * — it disappears when the session login is removed in Phase 6.
 */
@Component
public class UploaderResolver {

    private final AppUserRepository users;
    private final EventPhotographerRepository assignments;

    public UploaderResolver(AppUserRepository users, EventPhotographerRepository assignments) {
        this.users = users;
        this.assignments = assignments;
    }

    /**
     * Refuses a photographer uploading to an event they are not assigned to. Admins are
     * unrestricted, and so is an unauthenticated caller — the security rules already
     * require a role before any upload endpoint is reached, so reaching here without one
     * is only possible in tests.
     */
    public void requireCanUploadTo(UUID eventId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || hasRole(authentication, "ROLE_ADMIN")) {
            return;
        }

        if (!hasRole(authentication, "ROLE_PHOTOGRAPHER")) {
            return;
        }

        var user = currentUser(authentication);
        boolean assigned = user
                .map(appUser -> assignments.existsByIdEventIdAndIdUserId(eventId, appUser.getId()))
                .orElse(false);

        if (!assigned) {
            throw new AccessDeniedException("You are not assigned to this event");
        }
    }

    /**
     * The photographer record to credit a photo to, resolved from whoever is signed in.
     *
     * <p>Replaces a fallback that took whichever row the database happened to return
     * first, which was right only while a single row existed. Falls back to a placeholder
     * for the session admin, which has no users row of its own.
     */
    public Photographer resolvePhotographer(PhotographerRepository photographers) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var user = authentication == null ? Optional.<AppUser>empty() : currentUser(authentication);

        String email = user.map(AppUser::getEmail)
                .orElseGet(() -> authentication == null ? "admin@elitesportphotos.com" : authentication.getName());
        String name = user.map(AppUser::getName).orElse("Admin");

        final String resolvedName = name == null || name.isBlank() ? email : name;
        return photographers.findByEmail(email)
                .orElseGet(() -> photographers.save(new Photographer(resolvedName, email)));
    }

    private Optional<AppUser> currentUser(Authentication authentication) {
        return users.findByEmailIgnoreCase(authentication.getName());
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> role.equals(granted.getAuthority()));
    }
}
