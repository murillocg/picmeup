package com.picmeup.common.user;

import com.picmeup.common.config.AccessNotGrantedException;
import com.picmeup.common.config.AccessNotGrantedException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccessServiceTest {

    @Mock
    private AppUserRepository users;

    private static final String SUBJECT = "cognito-sub-123";

    private UserAccessService serviceWithBootstrap(String bootstrapEmails) {
        return new UserAccessService(users, bootstrapEmails);
    }

    private AppUser signIn(UserAccessService service, String email) {
        return service.resolveOnSignIn(email, true, SUBJECT, "Test Person");
    }

    @Test
    void shouldGrantRoleFromTheDatabaseNotTheIdentityProvider() {
        var photographer = new AppUser("shooter@example.com", "Shooter", AppUser.Role.PHOTOGRAPHER, null);
        photographer.recordLogin("old-sub");
        when(users.findByEmailIgnoreCase("shooter@example.com")).thenReturn(Optional.of(photographer));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var user = signIn(serviceWithBootstrap(""), "shooter@example.com");

        assertThat(user.getRole()).isEqualTo(AppUser.Role.PHOTOGRAPHER);
        assertThat(user.getEmail()).isEqualTo("shooter@example.com");
    }

    @Test
    void shouldRefuseAnAddressWithNoUserRecord() {
        when(users.findByEmailIgnoreCase("stranger@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> signIn(serviceWithBootstrap(""), "stranger@example.com"))
                .isInstanceOf(AccessNotGrantedException.class)
                .extracting(e -> ((AccessNotGrantedException) e).getReason())
                .isEqualTo(Reason.NOT_INVITED);

        verify(users, never()).save(any());
    }

    @Test
    void shouldRefuseADisabledUserAndSayItWasDisabledNotUninvited() {
        var disabled = new AppUser("gone@example.com", "Gone", AppUser.Role.PHOTOGRAPHER, null);
        disabled.disable();
        when(users.findByEmailIgnoreCase("gone@example.com")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> signIn(serviceWithBootstrap(""), "gone@example.com"))
                .isInstanceOf(AccessNotGrantedException.class)
                .extracting(e -> ((AccessNotGrantedException) e).getReason())
                .isEqualTo(Reason.DISABLED);
    }

    @Test
    void shouldRefuseAnIdentityWhoseEmailIsNotVerified() {
        var service = serviceWithBootstrap("");

        assertThatThrownBy(() ->
                service.resolveOnSignIn("unverified@example.com", false, SUBJECT, "Nobody"))
                .isInstanceOf(AccessNotGrantedException.class)
                .extracting(e -> ((AccessNotGrantedException) e).getReason())
                .isEqualTo(Reason.UNVERIFIED_EMAIL);
    }

    @Test
    void shouldRefuseAnIdentityWithNoEmailAtAll() {
        var service = serviceWithBootstrap("");

        assertThatThrownBy(() -> service.resolveOnSignIn(null, true, SUBJECT, "Nobody"))
                .isInstanceOf(AccessNotGrantedException.class)
                .extracting(e -> ((AccessNotGrantedException) e).getReason())
                .isEqualTo(Reason.UNVERIFIED_EMAIL);
    }

    @Test
    void shouldCreateTheFirstAdminFromBootstrapEmails() {
        when(users.findByEmailIgnoreCase("boss@example.com")).thenReturn(Optional.empty());
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var user = signIn(serviceWithBootstrap("boss@example.com, other@example.com"), "boss@example.com");

        assertThat(user.getRole()).isEqualTo(AppUser.Role.ADMIN);
    }

    @Test
    void shouldMatchBootstrapAndLookupCaseInsensitively() {
        when(users.findByEmailIgnoreCase("boss@example.com")).thenReturn(Optional.empty());
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var user = signIn(serviceWithBootstrap("Boss@Example.com"), "BOSS@example.com");

        assertThat(user.getEmail()).isEqualTo("boss@example.com");
        assertThat(user.getRole()).isEqualTo(AppUser.Role.ADMIN);
    }

    @Test
    void firstSignInShouldActivateAnInvitedUserAndRecordTheSubject() {
        var invited = new AppUser("new@example.com", "New", AppUser.Role.PHOTOGRAPHER, null);
        assertThat(invited.getStatus()).isEqualTo(AppUser.Status.INVITED);

        when(users.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.of(invited));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        signIn(serviceWithBootstrap(""), "new@example.com");

        assertThat(invited.getStatus()).isEqualTo(AppUser.Status.ACTIVE);
        assertThat(invited.getCognitoSub()).isEqualTo(SUBJECT);
        assertThat(invited.getLastLoginAt()).isNotNull();
    }

    // --- revalidation, the path RevalidateUserFilter runs on every request ---

    @Test
    void revalidationShouldPassForAnActiveUser() {
        var active = new AppUser("shooter@example.com", "Shooter", AppUser.Role.PHOTOGRAPHER, null);
        active.enable();
        when(users.findByEmailIgnoreCase("shooter@example.com")).thenReturn(Optional.of(active));

        assertThat(serviceWithBootstrap("").requireStillActive("shooter@example.com")).isSameAs(active);
    }

    @Test
    void revalidationShouldRejectSomeoneDisabledMidSession() {
        var disabled = new AppUser("gone@example.com", "Gone", AppUser.Role.PHOTOGRAPHER, null);
        disabled.disable();
        when(users.findByEmailIgnoreCase("gone@example.com")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> serviceWithBootstrap("").requireStillActive("gone@example.com"))
                .isInstanceOf(AccessNotGrantedException.class)
                .extracting(e -> ((AccessNotGrantedException) e).getReason())
                .isEqualTo(Reason.DISABLED);
    }

    /**
     * Bootstrap only applies at sign-in. A bootstrap admin whose row was later deleted
     * must not be silently recreated by a mid-session revalidation.
     */
    @Test
    void revalidationShouldNotRecreateABootstrapAdmin() {
        when(users.findByEmailIgnoreCase("boss@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                serviceWithBootstrap("boss@example.com").requireStillActive("boss@example.com"))
                .isInstanceOf(AccessNotGrantedException.class)
                .extracting(e -> ((AccessNotGrantedException) e).getReason())
                .isEqualTo(Reason.NOT_INVITED);

        verify(users, never()).save(any());
    }
}
