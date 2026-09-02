package com.picmeup.common.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Lookup is by lower-cased email. Providers are inconsistent about case and an admin
     * may type an invite in any casing, so both sides are normalised before comparison.
     */
    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findAllByOrderByCreatedAtDesc();
}
