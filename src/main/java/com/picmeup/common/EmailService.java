package com.picmeup.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final SesClient sesClient;
    private final String senderEmail;
    private final String[] adminRecipients;

    public EmailService(SesClient sesClient,
                        @Value("${app.email.sender}") String senderEmail,
                        @Value("${app.email.admin-recipients}") String adminRecipients) {
        this.sesClient = sesClient;
        this.senderEmail = senderEmail;
        this.adminRecipients = adminRecipients.split(",");
    }

    @Async
    public void sendAdminNotification(String subject, String htmlBody) {
        try {
            var request = SendEmailRequest.builder()
                    .source(senderEmail)
                    .destination(Destination.builder()
                            .toAddresses(adminRecipients)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build();

            sesClient.sendEmail(request);
            log.info("Admin notification sent: {}", subject);
        } catch (Exception e) {
            log.error("Failed to send admin notification: {}", subject, e);
        }
    }
}
