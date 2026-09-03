package com.picmeup.admin;

import com.picmeup.admin.dto.InviteRequest;
import com.picmeup.common.exception.ResourceNotFoundException;
import com.picmeup.common.user.AppUser;
import com.picmeup.common.user.AppUserRepository;
import com.picmeup.photo.Event;
import com.picmeup.photo.EventPhotographer;
import com.picmeup.photo.EventPhotographerRepository;
import com.picmeup.photo.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Invites, roles and event assignments.
 *
 * <p>An "invite" sends nothing. The address itself is the invitation: an admin records it
 * here, tells the person out of band to sign in, and their first successful sign-in flips
 * the row from INVITED to ACTIVE. There is no token to expire, leak or re-send.
 *
 * <p>Inviting someone who will use an emailed code also creates their Cognito identity —
 * the one AWS call here — because a code has nowhere to go without it.
 */
@Service
@Transactional(readOnly = true)
public class UserManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private final AppUserRepository users;
    private final EventRepository events;
    private final EventPhotographerRepository assignments;
    private final CognitoIdentityService cognitoIdentities;

    public UserManagementService(AppUserRepository users,
                                 EventRepository events,
                                 EventPhotographerRepository assignments,
                                 CognitoIdentityService cognitoIdentities) {
        this.users = users;
        this.events = events;
        this.assignments = assignments;
        this.cognitoIdentities = cognitoIdentities;
    }

    public List<AppUser> listUsers() {
        return users.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public AppUser invite(String email, String name, AppUser.Role role,
                          InviteRequest.SignInMethod signInMethod, UUID invitedBy) {
        String normalised = email.trim().toLowerCase();

        if (users.existsByEmailIgnoreCase(normalised)) {
            throw new IllegalArgumentException("%s has already been invited".formatted(normalised));
        }

        // Identity first. If Cognito refuses, no users row is written — better than an
        // invitation that looks issued but can never be signed in to, since the login
        // page cannot report a missing identity.
        if (signInMethod == InviteRequest.SignInMethod.EMAIL_CODE) {
            cognitoIdentities.createPasswordlessUser(normalised);
        }

        var user = users.save(new AppUser(normalised, name, role, invitedBy));
        log.info("Invited {} as {} signing in with {}", normalised, role, signInMethod);
        return user;
    }

    @Transactional
    public AppUser setRole(UUID userId, AppUser.Role role) {
        var user = get(userId);
        user.setRole(role);
        log.info("Changed role of {} to {}", user.getEmail(), role);
        return users.save(user);
    }

    /**
     * Revocation sets DISABLED rather than deleting: photos keep their attribution, and
     * the refusal takes effect on the next request with no call to Cognito. Any token the
     * person still holds stays cryptographically valid and completely powerless.
     */
    @Transactional
    public AppUser setEnabled(UUID userId, boolean enabled) {
        var user = get(userId);
        if (enabled) {
            user.enable();
        } else {
            user.disable();
        }
        log.info("{} access for {}", enabled ? "Enabled" : "Disabled", user.getEmail());
        return users.save(user);
    }

    /**
     * Both entities are resolved first so assigning to a deleted event or an unknown user
     * fails as a clean 404 rather than a foreign-key violation at flush time.
     */
    @Transactional
    public void assign(String eventSlug, UUID userId) {
        var event = requireEvent(eventSlug);
        var user = get(userId);

        if (assignments.existsByIdEventIdAndIdUserId(event.getId(), user.getId())) {
            return;
        }

        assignments.save(new EventPhotographer(event.getId(), user.getId()));
        log.info("Assigned {} to event {}", user.getEmail(), eventSlug);
    }

    @Transactional
    public void unassign(String eventSlug, UUID userId) {
        var event = requireEvent(eventSlug);
        assignments.deleteByIdEventIdAndIdUserId(event.getId(), userId);
        log.info("Unassigned user {} from event {}", userId, eventSlug);
    }

    public List<AppUser> assignedTo(String eventSlug) {
        var event = requireEvent(eventSlug);
        var userIds = assignments.findByIdEventId(event.getId()).stream()
                .map(EventPhotographer::getUserId)
                .toList();
        return userIds.isEmpty() ? List.of() : users.findAllById(userIds);
    }

    public long assignmentCount(UUID userId) {
        return assignments.countByIdUserId(userId);
    }

    private AppUser get(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    private Event requireEvent(String slug) {
        return events.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", slug));
    }
}
