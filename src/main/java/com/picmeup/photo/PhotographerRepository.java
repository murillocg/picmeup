package com.picmeup.photo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PhotographerRepository extends JpaRepository<Photographer, UUID> {

    Optional<Photographer> findByEmail(String email);
}
