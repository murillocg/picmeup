package com.picmeup.payment;

import com.picmeup.common.EmailService;
import com.picmeup.photo.Event;
import com.picmeup.photo.EventRepository;
import com.picmeup.photo.Photo;
import com.picmeup.photo.PhotoRepository;
import com.picmeup.photo.Photographer;
import com.picmeup.photo.S3StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceEventScopeTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PhotoRepository photoRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private EmailService emailService;
    @Mock
    private PayPalService payPalService;

    @InjectMocks
    private OrderService orderService;

    private Photo photoIn(Event event) {
        var photo = new Photo(UUID.randomUUID(), event, new Photographer("A", "a@b.com"), "shot.jpg");
        photo.markActive("originals/x.jpg", "thumbnails/x.jpg", new String[0]);
        return photo;
    }

    private Event freeEvent(String name) {
        var event = new Event(name, LocalDate.of(2026, 5, 10), "Sydney");
        event.setFree(true);
        return event;
    }

    @Test
    void recordsTheEventTheOrderBelongsTo() {
        var event = freeEvent("Marathon");
        var photos = List.of(photoIn(event), photoIn(event));
        when(photoRepository.findAllById(any())).thenReturn(photos);

        var order = orderService.createOrder(
                "buyer@example.com", photos.stream().map(Photo::getId).toList());

        assertThat(order.getEventId()).isEqualTo(event.getId());
    }

    @Test
    void rejectsACartSpanningTwoEvents() {
        // Pricing is taken from the first photo's event, so a mixed cart used to be
        // charged at one event's rates while containing another's photos.
        var photos = List.of(photoIn(freeEvent("Marathon")), photoIn(freeEvent("Pickleball")));
        when(photoRepository.findAllById(any())).thenReturn(photos);

        assertThatThrownBy(() -> orderService.createOrder(
                "buyer@example.com", photos.stream().map(Photo::getId).toList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same event");

        verify(orderRepository, never()).save(any());
        verify(payPalService, never()).createOrder(any(), anyString());
    }
}
