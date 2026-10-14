package com.picmeup.payment;

import com.picmeup.payment.dto.CreateOrderRequest;
import com.picmeup.payment.dto.OrderItemResponse;
import com.picmeup.payment.dto.OrderResponse;
import com.picmeup.payment.dto.OrderSummaryResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PayPalService payPalService;
    private final OrderZipService orderZipService;

    public OrderController(OrderService orderService,
                           PayPalService payPalService,
                           OrderZipService orderZipService) {
        this.orderService = orderService;
        this.payPalService = payPalService;
        this.orderZipService = orderZipService;
    }

    @GetMapping
    public ResponseEntity<PagedModel<OrderSummaryResponse>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        // Capped so a hand-edited URL cannot ask for the whole table in one query.
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
        var orders = orderService.getOrders(pageable).map(OrderSummaryResponse::from);
        return ResponseEntity.ok(new PagedModel<>(orders));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        var photoIds = request.photoIds().stream().map(UUID::fromString).toList();
        var order = orderService.createOrder(request.email(), photoIds);
        var items = orderService.getOrderItems(order.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order, items));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        var order = orderService.getOrder(id);
        var items = orderService.getOrderItems(id);
        return ResponseEntity.ok(OrderResponse.from(order, items));
    }

    @PostMapping("/{id}/capture")
    public ResponseEntity<OrderResponse> capturePayment(@PathVariable UUID id) {
        var order = orderService.capturePayment(id);
        var items = orderService.getOrderItems(id);
        return ResponseEntity.ok(OrderResponse.from(order, items));
    }

    @GetMapping("/paypal-client-id")
    public ResponseEntity<?> getPayPalClientId() {
        return ResponseEntity.ok(java.util.Map.of("clientId", payPalService.getClientId()));
    }

    @GetMapping("/{id}/downloads")
    public ResponseEntity<?> getDownloads(@PathVariable UUID id) {
        var order = orderService.getOrder(id);
        if (order.getStatus() != Order.Status.PAID) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("message", "Order is not paid"));
        }
        var items = orderService.getOrderItems(id);
        return ResponseEntity.ok(items);
    }

    /**
     * Returns a link to the order's archive, building it first if necessary.
     *
     * <p>The archive is served from S3 rather than streamed through here, so the browser
     * receives a content length and range support: a truncated download is detected
     * instead of being saved as a corrupt file, and an interrupted one resumes.
     */
    @GetMapping("/{id}/downloads/zip")
    public ResponseEntity<Map<String, String>> downloadZip(@PathVariable UUID id) {
        if (!orderZipService.isPaid(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "FORBIDDEN", "message", "Order is not paid"));
        }

        var url = orderZipService.downloadUrl(id);
        if (url.isPresent()) {
            return ResponseEntity.ok(Map.of("status", "READY", "url", url.get()));
        }

        orderZipService.requestBuild(id);
        return ResponseEntity.accepted().body(Map.of("status", "PREPARING"));
    }
}
