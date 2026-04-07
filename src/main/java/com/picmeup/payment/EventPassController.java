package com.picmeup.payment;

import com.picmeup.payment.dto.CreatePassRequest;
import com.picmeup.payment.dto.EventPassResponse;
import com.picmeup.payment.dto.RedeemPassRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class EventPassController {

    private final EventPassService eventPassService;
    private final PayPalService payPalService;

    public EventPassController(EventPassService eventPassService, PayPalService payPalService) {
        this.eventPassService = eventPassService;
        this.payPalService = payPalService;
    }

    @GetMapping("/api/events/{slug}/passes/price")
    public ResponseEntity<?> getPassPrice(@PathVariable String slug) {
        return ResponseEntity.ok(Map.of("price", eventPassService.getPassPrice()));
    }

    @PostMapping("/api/events/{slug}/passes")
    public ResponseEntity<EventPassResponse> createPass(
            @PathVariable String slug,
            @Valid @RequestBody CreatePassRequest request) {
        var pass = eventPassService.createPass(slug, request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(EventPassResponse.from(pass));
    }

    @PostMapping("/api/events/{slug}/passes/{id}/capture")
    public ResponseEntity<EventPassResponse> capturePayment(
            @PathVariable String slug,
            @PathVariable UUID id) {
        var pass = eventPassService.capturePayment(id);
        return ResponseEntity.ok(EventPassResponse.from(pass));
    }

    @PostMapping("/api/events/{slug}/passes/redeem")
    public ResponseEntity<?> redeemPass(
            @PathVariable String slug,
            @RequestPart("email") String email,
            @RequestPart("selfie") MultipartFile selfie) throws IOException {
        var downloadUrls = eventPassService.redeemPass(slug, email.trim(), selfie.getBytes());
        return ResponseEntity.ok(Map.of("downloadUrls", downloadUrls));
    }

    @GetMapping("/api/passes")
    public ResponseEntity<List<EventPassResponse>> listAllPasses() {
        var passes = eventPassService.getAllPasses().stream()
                .map(EventPassResponse::from)
                .toList();
        return ResponseEntity.ok(passes);
    }
}
