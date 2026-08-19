package com.rkey.vertex_backend.modules.auth.mapper;

import com.rkey.vertex_backend.modules.auth.entity.UserEntity;
import com.rkey.vertex_backend.modules.auth.model.dto.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserMapper covering entity to DTO conversion.
 * Tests various user configurations and edge cases.
 */
class UserMapperTest {

    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        userMapper = new UserMapper();
    }

    @Test
    void getUserSummary_withCompleteUser() {
        // Arrange
        UserEntity user = UserEntity.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .username("johndoe")
                .avatarUrl("https://example.com/avatar.jpg")
                .build();

        // Act
        UserSummary summary = userMapper.getUserSummary(user);

        // Assert
        assertNotNull(summary);
        assertEquals("John", summary.firstName());
        assertEquals("Doe", summary.lastName());
        assertEquals("john.doe@example.com", summary.email());
        assertEquals("johndoe", summary.username());
        assertEquals("https://example.com/avatar.jpg", summary.avatarUrl());
    }

    @Test
    void getUserSummary_withoutAvatar() {
        // Arrange
        UserEntity user = UserEntity.builder()
                .id(1L)
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@example.com")
                .username("janesmith")
                .avatarUrl(null)
                .build();

        // Act
        UserSummary summary = userMapper.getUserSummary(user);

        // Assert
        assertNotNull(summary);
        assertEquals("Jane", summary.firstName());
        assertEquals("Smith", summary.lastName());
        assertEquals("jane.smith@example.com", summary.email());
        assertEquals("janesmith", summary.username());
        assertNull(summary.avatarUrl());
    }

    @Test
    void getUserSummary_withEmptyAvatar() {
        // Arrange
        UserEntity user = UserEntity.builder()
                .id(2L)
                .firstName("Bob")
                .lastName("Johnson")
                .email("bob@example.com")
                .username("bobjohnson")
                .avatarUrl("")
                .build();

        // Act
        UserSummary summary = userMapper.getUserSummary(user);

        // Assert
        assertNotNull(summary);
        assertEquals("Bob", summary.firstName());
        assertEquals("Johnson", summary.lastName());
        assertEquals("bob@example.com", summary.email());
        assertEquals("bobjohnson", summary.username());
        assertEquals("", summary.avatarUrl());
    }

    @Test
    void getUserSummary_withSpecialCharactersInName() {
        // Arrange
        UserEntity user = UserEntity.builder()
                .id(3L)
                .firstName("François")
                .lastName("Müller")
                .email("francois.muller@example.fr")
                .username("francois_muller")
                .avatarUrl("https://example.com/avatar3.jpg")
                .build();

        // Act
        UserSummary summary = userMapper.getUserSummary(user);

        // Assert
        assertNotNull(summary);
        assertEquals("François", summary.firstName());
        assertEquals("Müller", summary.lastName());
        assertEquals("francois.muller@example.fr", summary.email());
    }

    @Test
    void getUserSummary_withLongValues() {
        // Arrange
        String longFirstName = "A".repeat(100);
        String longLastName = "B".repeat(100);
        String longEmail = "test".repeat(20) + "@example.com";
        String longUsername = "username".repeat(10);
        String longAvatarUrl = "https://example.com/" + "x".repeat(200) + ".jpg";

        UserEntity user = UserEntity.builder()
                .id(4L)
                .firstName(longFirstName)
                .lastName(longLastName)
                .email(longEmail)
                .username(longUsername)
                .avatarUrl(longAvatarUrl)
                .build();

        // Act
        UserSummary summary = userMapper.getUserSummary(user);

        // Assert
        assertNotNull(summary);
        assertEquals(longFirstName, summary.firstName());
        assertEquals(longLastName, summary.lastName());
        assertEquals(longEmail, summary.email());
        assertEquals(longUsername, summary.username());
        assertEquals(longAvatarUrl, summary.avatarUrl());
    }

    @Test
    void getUserSummary_withSingleCharacterNames() {
        // Arrange
        UserEntity user = UserEntity.builder()
                .id(5L)
                .firstName("A")
                .lastName("B")
                .email("a.b@example.com")
                .username("ab")
                .avatarUrl("https://example.com/a.jpg")
                .build();

        // Act
        UserSummary summary = userMapper.getUserSummary(user);

        // Assert
        assertNotNull(summary);
        assertEquals("A", summary.firstName());
        assertEquals("B", summary.lastName());
    }

    @Test
    void getUserSummary_withNumbersInUsername() {
        // Arrange
        UserEntity user = UserEntity.builder()
                .id(6L)
                .firstName("Test")
                .lastName("User")
                .email("test.user@example.com")
                .username("testuser123456789")
                .avatarUrl(null)
                .build();

        // Act
        UserSummary summary = userMapper.getUserSummary(user);

        // Assert
        assertNotNull(summary);
        assertEquals("testuser123456789", summary.username());
    }

    @Test
    void getUserSummary_withUnderscoresAndDashesInUsername() {
        // Arrange
        UserEntity user = UserEntity.builder()
                .id(7L)
                .firstName("Test")
                .lastName("User")
                .email("test.user@example.com")
                .username("test_user-123")
                .avatarUrl(null)
                .build();

        // Act
        UserSummary summary = userMapper.getUserSummary(user);

        // Assert
        assertNotNull(summary);
        assertEquals("test_user-123", summary.username());
    }

    @Test
    void getUserSummary_multipleCallsWithSameUser() {
        // Arrange
        UserEntity user = UserEntity.builder()
                .id(8L)
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .username("testuser")
                .avatarUrl("https://example.com/avatar.jpg")
                .build();

        // Act
        UserSummary summary1 = userMapper.getUserSummary(user);
        UserSummary summary2 = userMapper.getUserSummary(user);

        // Assert - both summaries should be equal
        assertEquals(summary1, summary2);
        assertEquals(summary1.firstName(), summary2.firstName());
        assertEquals(summary1.email(), summary2.email());
    }

    @Test
    void getUserSummary_withUrlSafeAvatarPath() {
        // Arrange
        String avatarUrl = "https://cdn.example.com/avatars/user-12345/profile.png?size=200&format=webp";
        UserEntity user = UserEntity.builder()
                .id(9L)
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .username("testuser")
                .avatarUrl(avatarUrl)
                .build();

        // Act
        UserSummary summary = userMapper.getUserSummary(user);

        // Assert
        assertNotNull(summary);
        assertEquals(avatarUrl, summary.avatarUrl());
    }

    @Test
    void getUserSummary_withDataUrlAvatar() {
        // Arrange
        String dataUrlAvatar = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        UserEntity user = UserEntity.builder()
                .id(10L)
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .username("testuser")
                .avatarUrl(dataUrlAvatar)
                .build();

        // Act
        UserSummary summary = userMapper.getUserSummary(user);

        // Assert
        assertNotNull(summary);
        assertEquals(dataUrlAvatar, summary.avatarUrl());
    }

    @Test
    void getUserSummary_withCaseSensitiveValues() {
        // Arrange
        UserEntity user = UserEntity.builder()
                .id(11L)
                .firstName("UPPERCASE")
                .lastName("lowercase")
                .email("MixedCase@Example.COM")
                .username("MixedCaseUsername")
                .avatarUrl("HTTPS://EXAMPLE.COM/AVATAR.JPG")
                .build();

        // Act
        UserSummary summary = userMapper.getUserSummary(user);

        // Assert - mapper should preserve case
        assertNotNull(summary);
        assertEquals("UPPERCASE", summary.firstName());
        assertEquals("lowercase", summary.lastName());
        assertEquals("MixedCase@Example.COM", summary.email());
        assertEquals("MixedCaseUsername", summary.username());
        assertEquals("HTTPS://EXAMPLE.COM/AVATAR.JPG", summary.avatarUrl());
    }
}
