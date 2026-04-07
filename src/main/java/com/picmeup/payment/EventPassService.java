package com.picmeup.payment;

import com.picmeup.common.EmailService;
import com.picmeup.common.exception.ResourceNotFoundException;
import com.picmeup.photo.EventRepository;
import com.picmeup.photo.FaceRecognitionService;
import com.picmeup.photo.Photo;
import com.picmeup.photo.PhotoRepository;
import com.picmeup.photo.S3StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EventPassService {

    private static final Logger log = LoggerFactory.getLogger(EventPassService.class);

    private final EventPassRepository eventPassRepository;
    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final FaceRecognitionService faceRecognitionService;
    private final S3StorageService s3StorageService;
    private final PayPalService payPalService;
    private final EmailService emailService;
    private final BigDecimal passPrice;

    public EventPassService(EventPassRepository eventPassRepository,
                            EventRepository eventRepository,
                            PhotoRepository photoRepository,
                            FaceRecognitionService faceRecognitionService,
                            S3StorageService s3StorageService,
                            PayPalService payPalService,
                            EmailService emailService,
                            @Value("${app.pass.price:30.00}") BigDecimal passPrice) {
        this.eventPassRepository = eventPassRepository;
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
        this.faceRecognitionService = faceRecognitionService;
        this.s3StorageService = s3StorageService;
        this.payPalService = payPalService;
        this.emailService = emailService;
        this.passPrice = passPrice;
    }

    @Transactional
    public EventPass createPass(String eventSlug, String buyerEmail) {
        var event = eventRepository.findBySlug(eventSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventSlug));

        var pass = new EventPass(event.getId(), buyerEmail, passPrice);
        eventPassRepository.save(pass);

        String paypalOrderId = payPalService.createOrder(passPrice, pass.getCurrency());
        pass.setPaypalOrderId(paypalOrderId);
        eventPassRepository.save(pass);

        log.info("Event pass {} created for {} on event {} (PayPal: {})",
                pass.getId(), buyerEmail, eventSlug, paypalOrderId);
        return pass;
    }

    @Transactional
    public EventPass capturePayment(UUID passId) {
        var pass = eventPassRepository.findById(passId)
                .orElseThrow(() -> new ResourceNotFoundException("EventPass", passId.toString()));

        if (pass.getStatus() != EventPass.Status.PENDING) {
            throw new IllegalStateException("Pass is not in PENDING state");
        }

        if (pass.getPaypalOrderId() == null) {
            throw new IllegalStateException("Pass has no PayPal order ID");
        }

        boolean captured = payPalService.captureOrder(pass.getPaypalOrderId());

        if (captured) {
            pass.setStatus(EventPass.Status.PAID);
            log.info("Event pass {} payment captured successfully", passId);

            emailService.sendAdminNotification(
                    "New photo pass — $" + pass.getPrice() + " AUD",
                    "<h2>New Photo Pass Purchased</h2>"
                            + "<p><strong>Pass ID:</strong> " + pass.getId() + "</p>"
                            + "<p><strong>Buyer:</strong> " + pass.getBuyerEmail() + "</p>"
                            + "<p><strong>Price:</strong> $" + pass.getPrice() + " AUD</p>"
            );
        } else {
            pass.setStatus(EventPass.Status.PENDING);
            log.warn("Event pass {} payment capture failed", passId);
            throw new IllegalStateException("Payment capture failed");
        }

        eventPassRepository.save(pass);
        return pass;
    }

    @Transactional
    public List<String> redeemPass(String eventSlug, String buyerEmail, byte[] selfieBytes) {
        var event = eventRepository.findBySlug(eventSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventSlug));

        var pass = eventPassRepository.findByEventIdAndBuyerEmailAndStatus(
                        event.getId(), buyerEmail, EventPass.Status.PAID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No valid photo pass found for this email"));

        List<UUID> matchedPhotoIds = faceRecognitionService.searchByFace(event.getId(), selfieBytes);

        if (matchedPhotoIds.isEmpty()) {
            throw new IllegalArgumentException("No photos matched your selfie");
        }

        var photos = photoRepository.findByIdInAndStatus(matchedPhotoIds, Photo.Status.ACTIVE);

        pass.markRedeemed();
        eventPassRepository.save(pass);

        log.info("Event pass {} redeemed by {} — {} photos matched", pass.getId(), buyerEmail, photos.size());

        return photos.stream()
                .filter(photo -> photo.getOriginalS3Key() != null)
                .map(photo -> s3StorageService.generatePresignedUrl(
                        photo.getOriginalS3Key(), Duration.ofHours(24),
                        "photo-" + photo.getId() + ".jpg"))
                .toList();
    }

    public BigDecimal getPassPrice() {
        return passPrice;
    }

    public List<EventPass> getAllPasses() {
        return eventPassRepository.findAllByOrderByCreatedAtDesc();
    }
}
