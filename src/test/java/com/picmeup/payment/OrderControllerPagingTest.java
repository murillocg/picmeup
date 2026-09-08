package com.picmeup.payment;

import com.picmeup.payment.dto.OrderSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerPagingTest {

    @Mock
    private OrderService orderService;
    @Mock
    private PayPalService payPalService;

    private OrderController controller() {
        return new OrderController(orderService, payPalService);
    }

    private Order paidOrder() {
        var order = new Order("buyer@example.com", new BigDecimal("65.00"));
        order.setPaypalOrderId("PAYPAL-123");
        return order;
    }

    private Order freeOrder() {
        return new Order("runner@example.com", BigDecimal.ZERO);
    }

    @Test
    void shouldReturnAPageWithTotalsSoTheUiCanShowProgress() {
        var page = new PageImpl<>(List.of(paidOrder()), PageRequest.of(0, 25), 140);
        when(orderService.getOrders(any())).thenReturn(page);

        var body = controller().listOrders(0, 25).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getContent()).hasSize(1);
        assertThat(body.getMetadata().totalElements()).isEqualTo(140);
        assertThat(body.getMetadata().totalPages()).isEqualTo(6);
    }

    /** A hand-edited size must not be able to pull the whole table in one query. */
    @Test
    void shouldCapThePageSize() {
        when(orderService.getOrders(any())).thenAnswer(inv -> {
            var pageable = inv.getArgument(0, org.springframework.data.domain.Pageable.class);
            assertThat(pageable.getPageSize()).isEqualTo(100);
            return new PageImpl<>(List.<Order>of(), pageable, 0);
        });

        controller().listOrders(0, 100_000);
    }

    @Test
    void shouldTreatANegativePageAsTheFirstOne() {
        when(orderService.getOrders(any())).thenAnswer(inv -> {
            var pageable = inv.getArgument(0, org.springframework.data.domain.Pageable.class);
            assertThat(pageable.getPageNumber()).isZero();
            return new PageImpl<>(List.<Order>of(), pageable, 0);
        });

        controller().listOrders(-5, 25);
    }

    /**
     * A free-event order shows $0.00, which otherwise reads like a failed charge. The flag
     * is what lets the list say why.
     */
    @Test
    void anOrderThatNeverWentToPayPalIsMarkedFree() {
        var response = OrderSummaryResponse.from(freeOrder());

        assertThat(response.free()).isTrue();
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aPaidOrderIsNotMarkedFree() {
        assertThat(OrderSummaryResponse.from(paidOrder()).free()).isFalse();
    }

    /**
     * The stored timestamp is a naive LocalDateTime that is UTC by convention. Exposing it
     * as an Instant carries that marker, so the browser converts to the reader's own zone
     * instead of guessing.
     */
    @Test
    void createdAtIsExposedAsAUtcInstant() {
        var order = freeOrder();
        var response = OrderSummaryResponse.from(order);

        assertThat(response.createdAt())
                .isEqualTo(order.getCreatedAt().toInstant(ZoneOffset.UTC));
        assertThat(LocalDateTime.ofInstant(response.createdAt(), ZoneOffset.UTC))
                .isEqualTo(order.getCreatedAt());
    }
}
