package com.picmeup.photo;

import com.picmeup.common.user.AppUser;
import com.picmeup.common.user.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploaderResolverTest {

    @Mock
    private AppUserRepository users;
    @Mock
    private EventPhotographerRepository assignments;
    @Mock
    private PhotographerRepository photographers;

    private final UUID eventId = UUID.randomUUID();

    private UploaderResolver resolver() {
        return new UploaderResolver(users, assignments);
    }

    private void signedInAs(String email, String role) {
        var authentication = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void photographerAssignedToTheEventMayUpload() {
        var user = new AppUser("shooter@example.com", "Shooter", AppUser.Role.PHOTOGRAPHER, null);
        signedInAs("shooter@example.com", "ROLE_PHOTOGRAPHER");
        when(users.findByEmailIgnoreCase("shooter@example.com")).thenReturn(Optional.of(user));
        when(assignments.existsByIdEventIdAndIdUserId(eventId, user.getId())).thenReturn(true);

        assertThatCode(() -> resolver().requireCanUploadTo(eventId)).doesNotThrowAnyException();
    }

    @Test
    void photographerNotAssignedToTheEventIsRefused() {
        var user = new AppUser("shooter@example.com", "Shooter", AppUser.Role.PHOTOGRAPHER, null);
        signedInAs("shooter@example.com", "ROLE_PHOTOGRAPHER");
        when(users.findByEmailIgnoreCase("shooter@example.com")).thenReturn(Optional.of(user));
        when(assignments.existsByIdEventIdAndIdUserId(eventId, user.getId())).thenReturn(false);

        assertThatThrownBy(() -> resolver().requireCanUploadTo(eventId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not assigned");
    }

    @Test
    void adminMayUploadAnywhereWithoutAnAssignment() {
        signedInAs("boss@example.com", "ROLE_ADMIN");

        assertThatCode(() -> resolver().requireCanUploadTo(eventId)).doesNotThrowAnyException();
        verify(assignments, never()).existsByIdEventIdAndIdUserId(any(), any());
    }

    @Test
    void photographerWithNoUserRecordIsRefused() {
        signedInAs("ghost@example.com", "ROLE_PHOTOGRAPHER");
        when(users.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver().requireCanUploadTo(eventId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void photosAreCreditedToWhoeverIsSignedIn() {
        var user = new AppUser("shooter@example.com", "Shooter", AppUser.Role.PHOTOGRAPHER, null);
        signedInAs("shooter@example.com", "ROLE_PHOTOGRAPHER");
        when(users.findByEmailIgnoreCase("shooter@example.com")).thenReturn(Optional.of(user));
        when(photographers.findByEmail("shooter@example.com")).thenReturn(Optional.empty());
        when(photographers.save(any(Photographer.class))).thenAnswer(inv -> inv.getArgument(0));

        var photographer = resolver().resolvePhotographer(photographers);

        assertThat(photographer.getEmail()).isEqualTo("shooter@example.com");
        assertThat(photographer.getName()).isEqualTo("Shooter");
    }

    @Test
    void anExistingPhotographerRecordIsReusedRatherThanDuplicated() {
        var user = new AppUser("shooter@example.com", "Shooter", AppUser.Role.PHOTOGRAPHER, null);
        var existing = new Photographer("Shooter", "shooter@example.com");
        signedInAs("shooter@example.com", "ROLE_PHOTOGRAPHER");
        when(users.findByEmailIgnoreCase("shooter@example.com")).thenReturn(Optional.of(user));
        when(photographers.findByEmail("shooter@example.com")).thenReturn(Optional.of(existing));

        assertThat(resolver().resolvePhotographer(photographers)).isSameAs(existing);
        verify(photographers, never()).save(any());
    }

    @Test
    void twoPhotographersAreCreditedSeparately() {
        var first = new AppUser("a@example.com", "Ann", AppUser.Role.PHOTOGRAPHER, null);
        var second = new AppUser("b@example.com", "Ben", AppUser.Role.PHOTOGRAPHER, null);
        when(users.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(first));
        when(users.findByEmailIgnoreCase("b@example.com")).thenReturn(Optional.of(second));
        when(photographers.findByEmail(any())).thenReturn(Optional.empty());
        when(photographers.save(any(Photographer.class))).thenAnswer(inv -> inv.getArgument(0));

        signedInAs("a@example.com", "ROLE_PHOTOGRAPHER");
        var one = resolver().resolvePhotographer(photographers);
        signedInAs("b@example.com", "ROLE_PHOTOGRAPHER");
        var two = resolver().resolvePhotographer(photographers);

        // The old fallback returned whichever row came back first, so both uploads would
        // have been credited to the same person.
        assertThat(one.getEmail()).isEqualTo("a@example.com");
        assertThat(two.getEmail()).isEqualTo("b@example.com");
    }
}
