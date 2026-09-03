package com.picmeup.admin;

import com.picmeup.admin.dto.InviteRequest;
import com.picmeup.admin.dto.UserResponse;
import com.picmeup.common.user.AppUser;
import com.picmeup.common.user.AppUserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only user management. Access is enforced by the {@code /api/admin/**} rule in
 * SecurityConfig, which requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserManagementService userManagement;
    private final AppUserRepository users;

    public AdminUserController(UserManagementService userManagement, AppUserRepository users) {
        this.userManagement = userManagement;
        this.users = users;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        var response = userManagement.listUsers().stream()
                .map(user -> UserResponse.from(user, userManagement.assignmentCount(user.getId())))
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<UserResponse> invite(@Valid @RequestBody InviteRequest request,
                                               Authentication authentication) {
        var invitedBy = currentUserId(authentication);
        var user = userManagement.invite(
                request.email(), request.name(), request.role(), request.signInMethod(), invitedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user, 0));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> update(@PathVariable UUID userId,
                                               @RequestParam(required = false) AppUser.Role role,
                                               @RequestParam(required = false) Boolean enabled) {
        AppUser user = null;
        if (role != null) {
            user = userManagement.setRole(userId, role);
        }
        if (enabled != null) {
            user = userManagement.setEnabled(userId, enabled);
        }
        if (user == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(UserResponse.from(user, userManagement.assignmentCount(userId)));
    }

    @GetMapping("/events/{slug}")
    public ResponseEntity<List<UserResponse>> assignedTo(@PathVariable String slug) {
        var response = userManagement.assignedTo(slug).stream()
                .map(user -> UserResponse.from(user, userManagement.assignmentCount(user.getId())))
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/events/{slug}")
    public ResponseEntity<Void> assign(@PathVariable UUID userId, @PathVariable String slug) {
        userManagement.assign(slug, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/events/{slug}")
    public ResponseEntity<Void> unassign(@PathVariable UUID userId, @PathVariable String slug) {
        userManagement.unassign(slug, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Records who issued an invite. Null for the session admin, which has no users row —
     * that mechanism disappears in Phase 6 anyway.
     */
    private UUID currentUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .map(AppUser::getId)
                .orElse(null);
    }
}
