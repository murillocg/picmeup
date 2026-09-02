package com.picmeup.common.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * An application user. The JWT proves who someone is; this decides what they may do.
 * Named AppUser to avoid colliding with Spring Security's own User.
 */
@Entity
@Table(name = "users")
public class AppUser {

    public enum Role {
        ADMIN, PHOTOGRAPHER
    }

    public enum Status {
        INVITED, ACTIVE, DISABLED
    }

    @Id
    private UUID id;

    /** The verified email from the identity provider. This is the join key, not cognitoSub. */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Recorded for reference only. One person can end up with two Cognito subs — a Google
     * federation and a native account at the same address — so it is never the lookup key.
     */
    @Column(unique = true)
    private String cognitoSub;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    private UUID invitedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    protected AppUser() {
    }

    public AppUser(String email, String name, Role role, UUID invitedBy) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.name = name;
        this.role = role;
        this.status = Status.INVITED;
        this.invitedBy = invitedBy;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * First successful sign-in for an invited person: they become active, and the Cognito
     * subject is recorded so the two identities can be correlated later if needed.
     */
    public void recordLogin(String cognitoSub) {
        if (this.status == Status.INVITED) {
            this.status = Status.ACTIVE;
        }
        if (cognitoSub != null) {
            this.cognitoSub = cognitoSub;
        }
        this.lastLoginAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Revocation. The row is kept so photos they uploaded keep their attribution. */
    public void disable() {
        this.status = Status.DISABLED;
    }

    public void enable() {
        this.status = Status.ACTIVE;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** INVITED counts: someone who has been invited but not yet signed in may sign in. */
    public boolean canSignIn() {
        return status == Status.INVITED || status == Status.ACTIVE;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getCognitoSub() { return cognitoSub; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public Status getStatus() { return status; }
    public UUID getInvitedBy() { return invitedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
}
