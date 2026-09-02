package com.picmeup.common.config;

import com.picmeup.common.config.AccessNotGrantedException.Reason;
import com.picmeup.common.user.AppUser;
import com.picmeup.common.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseRoleJwtAuthenticationConverterTest {

    @Mock
    private AppUserRepository users;

    private DatabaseRoleJwtAuthenticationConverter converterWithBootstrap(String bootstrapEmails) {
        return new DatabaseRoleJwtAuthenticationConverter(users, bootstrapEmails);
    }

    private Jwt token(String email, Boolean emailVerified) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("cognito-sub-123")
                .claim("name", "Test Person");
        if (email != null) {
            builder.claim("email", email);
        }
        if (emailVerified != null) {
            builder.claim("email_verified", emailVerified);
        }
        return builder.build();
    }

    @Test
    void shouldGrantRoleFromTheDatabaseNotTheToken() {
        var photographer = new AppUser("shooter@example.com", "Shooter", AppUser.Role.PHOTOGRAPHER, null);
        photographer.recordLogin("old-sub");
        when(users.findByEmailIgnoreCase("shooter@example.com")).thenReturn(Optional.of(photographer));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var authentication = converterWithBootstrap("").convert(token("shooter@example.com", true));

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_PHOTOGRAPHER");
        assertThat(authentication.getName()).isEqualTo("shooter@example.com");
    }

    @Test
    void shouldRefuseAnAddressWithNoUserRecord() {
        when(users.findByEmailIgnoreCase("stranger@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converterWithBootstrap("").convert(token("stranger@example.com", true)))
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

        assertThatThrownBy(() -> converterWithBootstrap("").convert(token("gone@example.com", true)))
                .isInstanceOf(AccessNotGrantedException.class)
                .extracting(e -> ((AccessNotGrantedException) e).getReason())
                .isEqualTo(Reason.DISABLED);
    }

    @Test
    void shouldRefuseATokenWhoseEmailIsNotVerified() {
        assertThatThrownBy(() -> converterWithBootstrap("").convert(token("unverified@example.com", false)))
                .isInstanceOf(AccessNotGrantedException.class)
                .extracting(e -> ((AccessNotGrantedException) e).getReason())
                .isEqualTo(Reason.UNVERIFIED_EMAIL);
    }

    @Test
    void shouldRefuseATokenWithNoEmailClaim() {
        assertThatThrownBy(() -> converterWithBootstrap("").convert(token(null, true)))
                .isInstanceOf(AccessNotGrantedException.class)
                .extracting(e -> ((AccessNotGrantedException) e).getReason())
                .isEqualTo(Reason.UNVERIFIED_EMAIL);
    }

    @Test
    void shouldCreateTheFirstAdminFromBootstrapEmails() {
        when(users.findByEmailIgnoreCase("boss@example.com")).thenReturn(Optional.empty());
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var converter = converterWithBootstrap("boss@example.com, other@example.com");
        var authentication = converter.convert(token("boss@example.com", true));

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void shouldMatchBootstrapAndLookupCaseInsensitively() {
        when(users.findByEmailIgnoreCase("boss@example.com")).thenReturn(Optional.empty());
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var converter = converterWithBootstrap("Boss@Example.com");
        var authentication = converter.convert(token("BOSS@example.com", true));

        assertThat(authentication.getName()).isEqualTo("boss@example.com");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void firstSignInShouldActivateAnInvitedUserAndRecordTheCognitoSubject() {
        var invited = new AppUser("new@example.com", "New", AppUser.Role.PHOTOGRAPHER, null);
        assertThat(invited.getStatus()).isEqualTo(AppUser.Status.INVITED);

        when(users.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.of(invited));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        converterWithBootstrap("").convert(token("new@example.com", true));

        assertThat(invited.getStatus()).isEqualTo(AppUser.Status.ACTIVE);
        assertThat(invited.getCognitoSub()).isEqualTo("cognito-sub-123");
        assertThat(invited.getLastLoginAt()).isNotNull();
    }
}
