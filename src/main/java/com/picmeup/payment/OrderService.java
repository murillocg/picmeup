package com.picmeup.payment;

import com.picmeup.common.EmailService;
import com.picmeup.common.exception.ResourceNotFoundException;
import com.picmeup.payment.dto.OrderItemResponse;
import com.picmeup.photo.Photo;
import com.picmeup.photo.PhotoRepository;
import com.picmeup.photo.S3StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final BigDecimal PHOTO_PRICE = new BigDecimal("10.00");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PhotoRepository photoRepository;
    private final S3StorageService s3StorageService;
    private final EmailService emailService;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        PhotoRepository photoRepository,
                        S3StorageService s3StorageService,
                        EmailService emailService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.photoRepository = photoRepository;
        this.s3StorageService = s3StorageService;
        this.emailService = emailService;
    }

    @Transactional
    public Order createOrder(String buyerEmail, List<UUID> photoIds) {
        var photos = photoRepository.findAllById(photoIds);

        if (photos.size() != photoIds.size()) {
            throw new IllegalArgumentException("One or more photos not found");
        }

        for (Photo photo : photos) {
            if (photo.getStatus() != Photo.Status.ACTIVE) {
                throw new IllegalArgumentException("Photo " + photo.getId() + " is not available for purchase");
            }
        }

        BigDecimal totalAmount = PHOTO_PRICE.multiply(new BigDecimal(photos.size()));
        var order = new Order(buyerEmail, totalAmount);
        orderRepository.save(order);

        for (Photo photo : photos) {
            var item = new OrderItem(order, photo.getId(), PHOTO_PRICE);
            orderItemRepository.save(item);
        }

        // TODO: When Stripe is integrated, keep as PENDING and create checkout session.
        // For now, mark as PAID immediately so downloads are available.
        order.setStatus(Order.Status.PAID);
        orderRepository.save(order);

        log.info("Order {} created for {} ({} photos, ${} AUD)", order.getId(), buyerEmail, photos.size(), totalAmount);

        emailService.sendAdminNotification(
                "New order — $" + totalAmount + " AUD",
                "<h2>New Order Received</h2>"
                        + "<p><strong>Order ID:</strong> " + order.getId() + "</p>"
                        + "<p><strong>Buyer:</strong> " + buyerEmail + "</p>"
                        + "<p><strong>Photos:</strong> " + photos.size() + "</p>"
                        + "<p><strong>Total:</strong> $" + totalAmount + " AUD</p>"
        );

        return order;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId.toString()));
    }

    public List<OrderItemResponse> getOrderItems(UUID orderId) {
        var order = getOrder(orderId);
        var items = orderItemRepository.findByOrderId(orderId);

        return items.stream()
                .map(item -> {
                    String downloadUrl = null;
                    if (order.getStatus() == Order.Status.PAID) {
                        var photo = photoRepository.findById(item.getPhotoId()).orElse(null);
                        if (photo != null && photo.getOriginalS3Key() != null) {
                            String filename = "photo-" + photo.getId() + ".jpg";
                            downloadUrl = s3StorageService.generatePresignedUrl(
                                    photo.getOriginalS3Key(), Duration.ofHours(24), filename);
                        }
                    }
                    return OrderItemResponse.from(item, downloadUrl);
                })
                .toList();
    }

    public void streamOrderAsZip(UUID orderId, OutputStream outputStream) throws IOException {
        var order = getOrder(orderId);
        if (order.getStatus() != Order.Status.PAID) {
            throw new IllegalStateException("Order is not paid");
        }

        var items = orderItemRepository.findByOrderId(orderId);

        try (var zipOut = new ZipOutputStream(outputStream)) {
            int index = 1;
            for (OrderItem item : items) {
                var photo = photoRepository.findById(item.getPhotoId()).orElse(null);
                if (photo == null || photo.getOriginalS3Key() == null) {
                    continue;
                }

                String filename = "photo-" + index + ".jpg";
                zipOut.putNextEntry(new ZipEntry(filename));

                try (var s3Stream = s3StorageService.getObject(photo.getOriginalS3Key())) {
                    s3Stream.transferTo(zipOut);
                }

                zipOut.closeEntry();
                index++;
            }
        }
    }
}
