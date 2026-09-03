package com.picmeup.admin;

import com.picmeup.admin.dto.InviteRequest.SignInMethod;
import com.picmeup.common.user.AppUser;
import com.picmeup.common.user.AppUserRepository;
import com.picmeup.photo.EventPhotographerRepository;
import com.picmeup.photo.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private AppUserRepository users;
    @Mock
    private EventRepository events;
    @Mock
    private EventPhotographerRepository assignments;
    @Mock
    private CognitoIdentityService cognitoIdentities;

    private UserManagementService service() {
        return new UserManagementService(users, events, assignments, cognitoIdentities);
    }

    /**
     * The whole reason the sign-in method is asked for: an emailed code has nowhere to go
     * unless a Cognito identity exists first.
     */
    @Test
    void invitingSomeoneWhoUsesAnEmailedCodeCreatesTheirCognitoIdentity() {
        when(users.existsByEmailIgnoreCase("shooter@example.com")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service().invite("shooter@example.com", "Shooter", AppUser.Role.PHOTOGRAPHER,
                SignInMethod.EMAIL_CODE, null);

        verify(cognitoIdentities).createPasswordlessUser("shooter@example.com");
    }

    /**
     * Google federation creates its own identity on first sign-in. Pre-creating a native
     * user owning the same address risks colliding with that.
     */
    @Test
    void invitingAGoogleUserDoesNotCreateACognitoIdentity() {
        when(users.existsByEmailIgnoreCase("boss@example.com")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service().invite("boss@example.com", "Boss", AppUser.Role.ADMIN, SignInMethod.GOOGLE, null);

        verify(cognitoIdentities, never()).createPasswordlessUser(anyString());
    }

    @Test
    void invitesAreNormalisedToLowercase() {
        // The lookup happens after normalising, so the stub must use the lowercase form.
        when(users.existsByEmailIgnoreCase("shooter@example.com")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var user = service().invite("  Shooter@Example.com  ", "Shooter",
                AppUser.Role.PHOTOGRAPHER, SignInMethod.EMAIL_CODE, null);

        assertThat(user.getEmail()).isEqualTo("shooter@example.com");
        verify(cognitoIdentities).createPasswordlessUser("shooter@example.com");
    }

    @Test
    void invitingAnAddressTwiceIsRejected() {
        when(users.existsByEmailIgnoreCase("shooter@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service().invite("shooter@example.com", "Shooter",
                AppUser.Role.PHOTOGRAPHER, SignInMethod.EMAIL_CODE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been invited");

        // No identity should be created for a duplicate invite either.
        verify(cognitoIdentities, never()).createPasswordlessUser(anyString());
        verify(users, never()).save(any());
    }

    @Test
    void anInviteStartsAsInvitedNotActive() {
        when(users.existsByEmailIgnoreCase("shooter@example.com")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var user = service().invite("shooter@example.com", "Shooter",
                AppUser.Role.PHOTOGRAPHER, SignInMethod.EMAIL_CODE, null);

        assertThat(user.getStatus()).isEqualTo(AppUser.Status.INVITED);
        assertThat(user.getRole()).isEqualTo(AppUser.Role.PHOTOGRAPHER);
    }
}
