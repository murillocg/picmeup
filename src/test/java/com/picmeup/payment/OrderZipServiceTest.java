package com.picmeup.payment;

import com.picmeup.photo.Event;
import com.picmeup.photo.Photo;
import com.picmeup.photo.PhotoRepository;
import com.picmeup.photo.Photographer;
import com.picmeup.photo.S3MultipartOutputStream;
import com.picmeup.photo.S3StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderZipServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PhotoRepository photoRepository;
    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private S3MultipartOutputStream stream;

    private final UUID orderId = UUID.randomUUID();

    private OrderZipService service() {
        return new OrderZipService(orderRepository, orderItemRepository, photoRepository, s3StorageService);
    }

    private Photo photoWithKey(String key, String filename) {
        var event = new Event("Marathon", LocalDate.of(2026, 5, 10), "Sydney");
        var photo = new Photo(UUID.randomUUID(), event, new Photographer("A", "a@b.com"), filename);
        photo.markActive(key, "thumbnails/x.jpg", new String[]{"face-1"});
        return photo;
    }

    @Test
    void theArchiveKeyIsDerivedFromTheOrder() {
        assertThat(OrderZipService.zipKey(orderId)).isEqualTo("zips/" + orderId + ".zip");
    }

    @Test
    void aFinishedArchiveIsHandedOutAsAPresignedLink() {
        when(s3StorageService.exists("zips/" + orderId + ".zip")).thenReturn(true);
        when(s3StorageService.generatePresignedUrl(anyString(), any(), anyString()))
                .thenReturn("https://s3.example.com/signed");

        assertThat(service().downloadUrl(orderId)).contains("https://s3.example.com/signed");
    }

    @Test
    void anArchiveThatDoesNotExistYetHasNoLink() {
        when(s3StorageService.exists(anyString())).thenReturn(false);

        assertThat(service().downloadUrl(orderId)).isEmpty();
    }

    /** Rebuilding something that is already in S3 would be pure waste. */
    @Test
    void anExistingArchiveIsNotRebuilt() {
        when(s3StorageService.exists(anyString())).thenReturn(true);

        service().requestBuild(orderId);

        verify(s3StorageService, never()).multipartOutputStream(anyString(), anyString());
    }

    @Test
    void aBuildWritesEveryPhotoAndCompletesTheUpload() throws Exception {
        var order = new Order("buyer@example.com", new BigDecimal("60.00"));
        var photo = photoWithKey("originals/e/1.jpg", "IMG_1.jpg");
        var item = new OrderItem(order, photo.getId(), new BigDecimal("20.00"));

        when(s3StorageService.exists(anyString())).thenReturn(false);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));
        when(s3StorageService.getObject("originals/e/1.jpg"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        when(s3StorageService.multipartOutputStream(anyString(), eq("application/zip")))
                .thenReturn(stream);

        service().requestBuild(orderId);

        verify(stream).close();
        verify(stream, never()).abort();
    }

    /**
     * Parts already uploaded are billed but absent from a bucket listing, so a failed
     * build must discard them rather than leave them behind.
     */
    @Test
    void aFailedBuildAbortsTheUploadRatherThanLeavingPartsBehind() {
        var order = new Order("buyer@example.com", new BigDecimal("60.00"));
        var photo = photoWithKey("originals/e/1.jpg", "IMG_1.jpg");
        var item = new OrderItem(order, photo.getId(), new BigDecimal("20.00"));

        when(s3StorageService.exists(anyString())).thenReturn(false);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));
        when(s3StorageService.multipartOutputStream(anyString(), anyString())).thenReturn(stream);
        when(s3StorageService.getObject(anyString()))
                .thenThrow(new RuntimeException("S3 is unavailable"));

        service().requestBuild(orderId);

        verify(stream).abort();
    }

    /** An order whose photos have all been deleted must not produce an empty archive. */
    @Test
    void anOrderWithNoUsablePhotosAbortsInsteadOfWritingAnEmptyArchive() {
        var order = new Order("buyer@example.com", BigDecimal.ZERO);
        var item = new OrderItem(order, UUID.randomUUID(), BigDecimal.ZERO);

        when(s3StorageService.exists(anyString())).thenReturn(false);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(photoRepository.findById(any())).thenReturn(Optional.empty());
        when(s3StorageService.multipartOutputStream(anyString(), anyString())).thenReturn(stream);

        service().requestBuild(orderId);

        verify(stream).abort();
    }

    /**
     * Virtual threads give @Async an unbounded thread per call, so without a permit limit
     * every simultaneously-requested order would buffer a part at once. This pins the
     * ceiling that keeps that within the heap.
     */
    @Test
    void concurrentBuildsAreCappedSoTheHeapCannotBeSwamped() throws Exception {
        var order = new Order("buyer@example.com", new BigDecimal("60.00"));
        var photo = photoWithKey("originals/e/1.jpg", "IMG_1.jpg");

        when(s3StorageService.exists(anyString())).thenReturn(false);
        when(photoRepository.findById(any())).thenReturn(Optional.of(photo));
        when(s3StorageService.multipartOutputStream(anyString(), anyString())).thenReturn(stream);

        var inFlight = new java.util.concurrent.atomic.AtomicInteger();
        var peak = new java.util.concurrent.atomic.AtomicInteger();
        var release = new java.util.concurrent.CountDownLatch(1);

        when(s3StorageService.getObject(anyString())).thenAnswer(invocation -> {
            int now = inFlight.incrementAndGet();
            peak.updateAndGet(previous -> Math.max(previous, now));
            release.await();
            inFlight.decrementAndGet();
            return new ByteArrayInputStream(new byte[]{1, 2, 3, 4});
        });

        var service = service();
        var threads = new java.util.ArrayList<Thread>();
        for (int i = 0; i < 10; i++) {
            var id = UUID.randomUUID();
            when(orderItemRepository.findByOrderId(id))
                    .thenReturn(List.of(new OrderItem(order, photo.getId(), BigDecimal.ONE)));
            var t = new Thread(() -> service.requestBuild(id));
            t.start();
            threads.add(t);
        }

        // Give the threads time to pile up against the permit limit before releasing.
        Thread.sleep(400);
        assertThat(peak.get())
                .describedAs("builds running at once")
                .isLessThanOrEqualTo(3);

        release.countDown();
        for (var t : threads) t.join(5000);
    }

    @Test
    void onlyPaidOrdersMayBeDownloaded() {
        var paid = new Order("buyer@example.com", new BigDecimal("60.00"));
        paid.setStatus(Order.Status.PAID);
        var pending = new Order("other@example.com", new BigDecimal("60.00"));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(paid));
        assertThat(service().isPaid(orderId)).isTrue();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pending));
        assertThat(service().isPaid(orderId)).isFalse();

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        assertThat(service().isPaid(orderId)).isFalse();
    }
}
