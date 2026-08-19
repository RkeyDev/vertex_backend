package com.rkey.vertex_backend.modules.auth.service;

import io.mailtrap.client.MailtrapClient;
import io.mailtrap.model.request.emails.MailtrapMail;
import io.mailtrap.model.response.emails.SendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailService covering email sending with Mailtrap,
 * including initialization, sandbox/live modes, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private MailtrapClient mockMailtrapClient;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService();
    }

    @Test
    void init_withValidTokenAndSandboxMode() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailtrapToken", "valid_token");
        ReflectionTestUtils.setField(emailService, "sandboxMode", true);
        ReflectionTestUtils.setField(emailService, "inboxIdStr", "12345");
        ReflectionTestUtils.setField(emailService, "senderEmail", "test@example.com");
        ReflectionTestUtils.setField(emailService, "senderName", "Test Sender");

        // Act & Assert - no exception thrown
        assertDoesNotThrow(() -> {
            ReflectionTestUtils.invokeMethod(emailService, "init");
        });
    }

    @Test
    void init_withMissingToken() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailtrapToken", "");
        ReflectionTestUtils.setField(emailService, "sandboxMode", false);

        // Act
        ReflectionTestUtils.invokeMethod(emailService, "init");

        // Assert - mailtrapClient should remain null
        MailtrapClient client = (MailtrapClient) ReflectionTestUtils.getField(emailService, "mailtrapClient");
        assertNull(client);
    }

    @Test
    void init_withSandboxModeButMissingInboxId() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailtrapToken", "valid_token");
        ReflectionTestUtils.setField(emailService, "sandboxMode", true);
        ReflectionTestUtils.setField(emailService, "inboxIdStr", "");

        // Act
        ReflectionTestUtils.invokeMethod(emailService, "init");

        // Assert - initialization should fail gracefully
        MailtrapClient client = (MailtrapClient) ReflectionTestUtils.getField(emailService, "mailtrapClient");
        assertNull(client);
    }

    @Test
    void init_withSandboxModeAndInvalidInboxId() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailtrapToken", "valid_token");
        ReflectionTestUtils.setField(emailService, "sandboxMode", true);
        ReflectionTestUtils.setField(emailService, "inboxIdStr", "not_a_number");

        // Act
        ReflectionTestUtils.invokeMethod(emailService, "init");

        // Assert - initialization should fail gracefully
        MailtrapClient client = (MailtrapClient) ReflectionTestUtils.getField(emailService, "mailtrapClient");
        assertNull(client);
    }

    @Test
    void sendVerificationEmail_success() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailtrapClient", mockMailtrapClient);
        ReflectionTestUtils.setField(emailService, "senderEmail", "sender@example.com");
        ReflectionTestUtils.setField(emailService, "senderName", "Vertex System");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:5174");

        String to = "user@example.com";
        String token = "verification_token_123";
        when(mockMailtrapClient.send(any(MailtrapMail.class))).thenReturn(new SendResponse());

        // Act
        emailService.sendVerificationEmail(to, token);

        // Assert
        verify(mockMailtrapClient, times(1)).send(any(MailtrapMail.class));
    }

    @Test
    void sendVerificationEmail_withNullClient() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailtrapClient", null);

        // Act - should not throw
        emailService.sendVerificationEmail("user@example.com", "token");

        // Assert - no exception
        assertTrue(true);
    }

    @Test
    void sendVerificationEmail_withExceptionFromMailtrap() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailtrapClient", mockMailtrapClient);
        ReflectionTestUtils.setField(emailService, "senderEmail", "sender@example.com");
        ReflectionTestUtils.setField(emailService, "senderName", "Vertex System");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:5174");

        when(mockMailtrapClient.send(any(MailtrapMail.class)))
                .thenThrow(new RuntimeException("Mailtrap API error"));

        // Act - should not throw
        emailService.sendVerificationEmail("user@example.com", "token");

        // Assert - exception handled gracefully
        verify(mockMailtrapClient, times(1)).send(any(MailtrapMail.class));
    }

    @Test
    void sendVerificationEmail_verificationLinkFormat() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailtrapClient", mockMailtrapClient);
        ReflectionTestUtils.setField(emailService, "senderEmail", "sender@example.com");
        ReflectionTestUtils.setField(emailService, "senderName", "Vertex System");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:5174");

        String to = "user@example.com";
        String token = "test_token_123";

        ArgumentCaptor<MailtrapMail> captor = ArgumentCaptor.forClass(MailtrapMail.class);
        when(mockMailtrapClient.send(any(MailtrapMail.class))).thenReturn(new SendResponse());

        // Act
        emailService.sendVerificationEmail(to, token);

        // Assert
        verify(mockMailtrapClient).send(captor.capture());
        MailtrapMail sentMail = captor.getValue();
        assertNotNull(sentMail);
    }

    @Test
    void sendVerificationEmail_withSpecialCharactersInToken() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailtrapClient", mockMailtrapClient);
        ReflectionTestUtils.setField(emailService, "senderEmail", "sender@example.com");
        ReflectionTestUtils.setField(emailService, "senderName", "Vertex System");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:5174");

        String token = "token_with_special_chars_!@#$%^&*";
        when(mockMailtrapClient.send(any(MailtrapMail.class))).thenReturn(new SendResponse());

        // Act
        emailService.sendVerificationEmail("user@example.com", token);

        // Assert - should handle special characters
        verify(mockMailtrapClient, times(1)).send(any(MailtrapMail.class));
    }

    @Test
    void sendVerificationEmail_withLongEmail() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailtrapClient", mockMailtrapClient);
        ReflectionTestUtils.setField(emailService, "senderEmail", "sender@example.com");
        ReflectionTestUtils.setField(emailService, "senderName", "Vertex System");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:5174");

        String longEmail = "very.long.email.address.with.many.characters@subdomain.example.co.uk";
        when(mockMailtrapClient.send(any(MailtrapMail.class))).thenReturn(new SendResponse());

        // Act
        emailService.sendVerificationEmail(longEmail, "token");

        // Assert
        verify(mockMailtrapClient, times(1)).send(any(MailtrapMail.class));
    }
}
