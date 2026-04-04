package com.picmeup.photo;

import com.picmeup.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock
    private PhotoRepository photoRepository;
    @Mock
    private PhotographerRepository photographerRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private ImageProcessingService imageProcessingService;
    @Mock
    private FaceRecognitionService faceRecognitionService;
    @Mock
    private com.picmeup.payment.OrderItemRepository orderItemRepository;

    private PhotoService photoService;

    @BeforeEach
    void setUp() {
        photoService = new PhotoService(photoRepository, photographerRepository,
                eventRepository, s3StorageService, imageProcessingService, faceRecognitionService,
                orderItemRepository);
    }

    @Test
    void uploadPhotos_shouldCreatePhotosAndTriggerProcessing() {
        var event = new Event("Marathon", LocalDate.of(2026, 5, 10), "Sydney");
        var photographer = new Photographer("John", "john@example.com");

        when(eventRepository.findBySlug(event.getSlug())).thenReturn(Optional.of(event));
        when(photographerRepository.findByEmail("john@example.com")).thenReturn(Optional.of(photographer));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

        var file = mock(MultipartFile.class);
        var photos = photoService.uploadPhotos(event.getSlug(), "john@example.com", "John", List.of(file));

        assertThat(photos).hasSize(1);
        assertThat(photos.get(0).getStatus()).isEqualTo(Photo.Status.PROCESSING);
        verify(photoRepository).save(any(Photo.class));
    }

    @Test
    void uploadPhotos_shouldCreateNewPhotographerIfNotExists() {
        var event = new Event("Marathon", LocalDate.of(2026, 5, 10), "Sydney");

        when(eventRepository.findBySlug(event.getSlug())).thenReturn(Optional.of(event));
        when(photographerRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(photographerRepository.save(any(Photographer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

        var file = mock(MultipartFile.class);
        photoService.uploadPhotos(event.getSlug(), "new@example.com", "New Guy", List.of(file));

        verify(photographerRepository).save(any(Photographer.class));
    }

    @Test
    void uploadPhotos_shouldThrowWhenEventNotFound() {
        when(eventRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        var file = mock(MultipartFile.class);

        assertThatThrownBy(() -> photoService.uploadPhotos("unknown", "a@b.com", "A", List.of(file)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getActivePhotos_shouldReturnOnlyActivePhotos() {
        var event = new Event("Marathon", LocalDate.of(2026, 5, 10), "Sydney");
        when(eventRepository.findBySlug(event.getSlug())).thenReturn(Optional.of(event));
        when(photoRepository.findByEventIdAndStatus(event.getId(), Photo.Status.ACTIVE))
                .thenReturn(List.of());

        var result = photoService.getActivePhotos(event.getSlug());

        assertThat(result).isEmpty();
        verify(photoRepository).findByEventIdAndStatus(event.getId(), Photo.Status.ACTIVE);
    }

    @Test
    void getActivePhotos_shouldThrowWhenEventNotFound() {
        when(eventRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> photoService.getActivePhotos("unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchByFace_shouldReturnMatchedPhotos() {
        var event = new Event("Marathon", LocalDate.of(2026, 5, 10), "Sydney");
        var photo = new Photo(event, new Photographer("John", "john@example.com"));

        when(eventRepository.findBySlug(event.getSlug())).thenReturn(Optional.of(event));
        when(faceRecognitionService.searchByFace(event.getId(), new byte[]{1, 2, 3}))
                .thenReturn(List.of(photo.getId()));
        when(photoRepository.findByIdInAndStatus(List.of(photo.getId()), Photo.Status.ACTIVE))
                .thenReturn(List.of(photo));

        var result = photoService.searchByFace(event.getSlug(), new byte[]{1, 2, 3});

        assertThat(result).hasSize(1);
    }

    @Test
    void searchByFace_shouldReturnEmptyWhenNoMatches() {
        var event = new Event("Marathon", LocalDate.of(2026, 5, 10), "Sydney");

        when(eventRepository.findBySlug(event.getSlug())).thenReturn(Optional.of(event));
        when(faceRecognitionService.searchByFace(event.getId(), new byte[]{1, 2, 3}))
                .thenReturn(List.of());

        var result = photoService.searchByFace(event.getSlug(), new byte[]{1, 2, 3});

        assertThat(result).isEmpty();
    }

    @Test
    void searchByFace_shouldThrowWhenEventNotFound() {
        when(eventRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> photoService.searchByFace("unknown", new byte[]{1}))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
