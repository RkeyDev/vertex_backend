package com.rkey.vertex_backend.modules.auth.service;

import io.mailtrap.client.MailtrapClient;
import io.mailtrap.config.MailtrapConfig;
import io.mailtrap.factory.MailtrapClientFactory;
import io.mailtrap.model.request.emails.Address;
import io.mailtrap.model.request.emails.MailtrapMail;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for handling email dispatches via Mailtrap API SDK.
 *
 * Supports two modes controlled by environment variables:
 *  - Sandbox mode (MAILTRAP_SANDBOX=true): emails are captured in your Mailtrap inbox.
 *    Requires MAILTRAP_INBOX_ID to be set. Ideal for development/testing.
 *  - Live mode (MAILTRAP_SANDBOX=false): emails are sent to real recipients.
 *    Requires a verified sender domain in your Mailtrap account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    @Value("${MAILTRAP_TOKEN}")
    private String mailtrapToken;

    @Value("${MAILTRAP_SENDER_EMAIL:hello@demomailtrap.co}")
    private String senderEmail;

    @Value("${MAILTRAP_SENDER_NAME:Vertex System}")
    private String senderName;

    @Value("${MAILTRAP_SANDBOX:true}")
    private boolean sandboxMode;

    @Value("${MAILTRAP_INBOX_ID:}")
    private String inboxIdStr;

    @Value("${application.frontend.url:http://localhost:5174}")
    private String frontendUrl;

    private MailtrapClient mailtrapClient;

    @PostConstruct
    private void init() {
        if (mailtrapToken == null || mailtrapToken.isBlank()) {
            log.error("MAILTRAP_TOKEN is missing. Email service will not function.");
            return;
        }

        MailtrapConfig.Builder configBuilder = new MailtrapConfig.Builder()
                .token(mailtrapToken);

        if (sandboxMode) {
            long inboxId = 0;
            try {
                inboxId = (inboxIdStr != null && !inboxIdStr.isBlank()) ? Long.parseLong(inboxIdStr.trim()) : 0;
            } catch (NumberFormatException e) {
                log.error("MAILTRAP_INBOX_ID='{}' is not a valid number. Email service will not function.", inboxIdStr);
                return;
            }
            if (inboxId == 0) {
                log.error("MAILTRAP_SANDBOX=true but MAILTRAP_INBOX_ID is not set. " +
                        "Get your inbox ID from mailtrap.io -> Email Testing -> your inbox URL.");
                return;
            }
            configBuilder.sandbox(true).inboxId(inboxId);
            log.info("Mailtrap client initialized in SANDBOX mode (inbox: {}).", inboxId);
        } else {
            log.info("Mailtrap client initialized in LIVE sending mode.");
        }

        this.mailtrapClient = MailtrapClientFactory.createMailtrapClient(configBuilder.build());
    }

    public void sendVerificationEmail(String to, String token) {
        if (mailtrapClient == null) {
            log.error("Cannot send verification email: Mailtrap client is not initialized.");
            return;
        }

        try {
            String verificationLink = String.format("%s/email-verification?token=%s&email=%s",
                    frontendUrl, token, to);

            final MailtrapMail mail = MailtrapMail.builder()
                    .from(new Address(senderEmail, senderName))
                    .to(List.of(new Address(to)))
                    .subject("Account Verification - Vertex")
                    .text("Welcome to Vertex!\n\n" +
                            "Please click the link below to verify your account:\n" +
                            verificationLink + "\n\n" +
                            "If you didn't request this, you can safely ignore this email.")
                    .category("Account Verification")
                    .build();

            var response = mailtrapClient.send(mail);
            log.info("Verification email dispatched to {} (sandbox={}) | API response: {}", to, sandboxMode, response);

        } catch (Exception e) {
            log.error("Failed to dispatch verification email to {} via Mailtrap SDK", to, e);
        }
    }
}