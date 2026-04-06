package com.picmeup.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Map;

@Service
public class PayPalService {

    private static final Logger log = LoggerFactory.getLogger(PayPalService.class);

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final ObjectMapper objectMapper;

    public PayPalService(@Value("${paypal.client-id}") String clientId,
                         @Value("${paypal.client-secret}") String clientSecret,
                         @Value("${paypal.base-url}") String baseUrl,
                         ObjectMapper objectMapper) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        log.info("PayPal configured: clientId={}..., baseUrl={}", clientId.substring(0, Math.min(8, clientId.length())), baseUrl);
    }

    private String getAccessToken() {
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());

        String response = restClient.post()
                .uri("/v1/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(String.class);

        try {
            return objectMapper.readTree(response).get("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse PayPal access token", e);
        }
    }

    public String createOrder(BigDecimal amount, String currency) {
        String token = getAccessToken();

        var orderPayload = Map.of(
                "intent", "CAPTURE",
                "purchase_units", new Object[]{
                        Map.of(
                                "amount", Map.of(
                                        "currency_code", currency,
                                        "value", amount.toPlainString()
                                )
                        )
                }
        );

        try {
            String body = objectMapper.writeValueAsString(orderPayload);

            String response = restClient.post()
                    .uri("/v2/checkout/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(response);
            String paypalOrderId = node.get("id").asText();
            log.info("PayPal order created: {}", paypalOrderId);
            return paypalOrderId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PayPal order", e);
        }
    }

    public boolean captureOrder(String paypalOrderId) {
        String token = getAccessToken();

        try {
            String response = restClient.post()
                    .uri("/v2/checkout/orders/{id}/capture", paypalOrderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(response);
            String status = node.get("status").asText();
            log.info("PayPal order {} capture status: {}", paypalOrderId, status);
            return "COMPLETED".equals(status);
        } catch (Exception e) {
            log.error("Failed to capture PayPal order {}", paypalOrderId, e);
            return false;
        }
    }

    public String getClientId() {
        return clientId;
    }
}
