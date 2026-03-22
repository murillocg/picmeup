package com.picmeup.photo;

import com.picmeup.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private FaceRecognitionService faceRecognitionService;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, photoRepository, s3StorageService, faceRecognitionService);
    }

    @Test
    void createEvent_shouldCreateWithGeneratedSlugAndExpiry() {
        when(eventRepository.existsBySlug(anyString())).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        var event = eventService.createEvent("City Marathon 2026", LocalDate.of(2026, 5, 10), "Sydney");

        assertThat(event.getName()).isEqualTo("City Marathon 2026");
        assertThat(event.getSlug()).isEqualTo("city-marathon-2026-2026-05-10");
        assertThat(event.getExpiresAt()).isEqualTo(event.getCreatedAt().plusMonths(2));
        assertThat(event.getId()).isNotNull();
    }

    @Test
    void createEvent_shouldRejectDuplicateSlug() {
        when(eventRepository.existsBySlug(anyString())).thenReturn(true);

        assertThatThrownBy(() -> eventService.createEvent("Marathon", LocalDate.of(2026, 5, 10), "Sydney"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getBySlug_shouldThrowWhenNotFound() {
        when(eventRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getBySlug("unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void getBySlug_shouldReturnEvent() {
        var event = new Event("Test", LocalDate.of(2026, 3, 1), "Melbourne");
        when(eventRepository.findBySlug(event.getSlug())).thenReturn(Optional.of(event));

        var result = eventService.getBySlug(event.getSlug());

        assertThat(result.getName()).isEqualTo("Test");
    }
}
