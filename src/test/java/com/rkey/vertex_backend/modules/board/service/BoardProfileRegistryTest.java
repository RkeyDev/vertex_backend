package com.rkey.vertex_backend.modules.board.service;

import com.rkey.vertex_backend.modules.board.models.dto.CursorProfileDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BoardProfileRegistry covering in-memory cursor profile management,
 * profile registration, retrieval, removal, and concurrent access patterns.
 */
class BoardProfileRegistryTest {

    @BeforeEach
    void setUp() {
        // Clear all profiles before each test
        BoardProfileRegistry.clearAll();
    }

    @Test
    void registerProfile_withNewUser() {
        // Arrange
        String boardToken = "board_123";
        String preferredId = "1";
        String username = "john_doe";
        String avatar = "https://example.com/avatar.jpg";

        // Act
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, preferredId, username, avatar);

        // Assert
        assertNotNull(profile);
        assertEquals("1", profile.id());
        assertEquals("john_doe", profile.username());
        assertEquals("https://example.com/avatar.jpg", profile.avatar());
    }

    @Test
    void registerProfile_withDuplicateUsername() {
        // Arrange
        String boardToken = "board_123";
        String username = "duplicate_user";

        // Act
        CursorProfileDTO profile1 = BoardProfileRegistry.registerProfile(boardToken, "1", username, "avatar1.jpg");
        CursorProfileDTO profile2 = BoardProfileRegistry.registerProfile(boardToken, "2", username, "avatar2.jpg");

        // Assert - should return the same profile for duplicate username
        assertEquals(profile1.id(), profile2.id());
        assertEquals(profile1.username(), profile2.username());
    }

    @Test
    void registerProfile_withNullUsername() {
        // Arrange
        String boardToken = "board_123";

        // Act
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, "1", null, "avatar.jpg");

        // Assert
        assertNotNull(profile);
        assertEquals("", profile.username());
    }

    @Test
    void registerProfile_withNullAvatar() {
        // Arrange
        String boardToken = "board_123";

        // Act
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, "1", "user", null);

        // Assert
        assertNotNull(profile);
        assertEquals("", profile.avatar());
    }

    @Test
    void registerProfile_withPreferredIdAlreadyTaken() {
        // Arrange
        String boardToken = "board_123";
        BoardProfileRegistry.registerProfile(boardToken, "1", "user1", "avatar1.jpg");

        // Act
        CursorProfileDTO profile2 = BoardProfileRegistry.registerProfile(boardToken, "1", "user2", "avatar2.jpg");

        // Assert - should auto-assign next available ID
        assertNotNull(profile2);
        assertEquals("2", profile2.id());
    }

    @Test
    void registerProfile_withInvalidPreferredId() {
        // Arrange
        String boardToken = "board_123";

        // Act
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, "invalid", "user", "avatar.jpg");

        // Assert - should auto-assign
        assertNotNull(profile);
        assertEquals("1", profile.id());
    }

    @Test
    void registerProfile_withNegativePreferredId() {
        // Arrange
        String boardToken = "board_123";

        // Act
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, "-1", "user", "avatar.jpg");

        // Assert - should auto-assign (negative is invalid)
        assertNotNull(profile);
        assertEquals("1", profile.id());
    }

    @Test
    void registerProfile_withZeroPreferredId() {
        // Arrange
        String boardToken = "board_123";

        // Act
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, "0", "user", "avatar.jpg");

        // Assert - should auto-assign (zero is invalid)
        assertNotNull(profile);
        assertEquals("1", profile.id());
    }

    @Test
    void registerProfile_multipleUsersOnSameBoard() {
        // Arrange
        String boardToken = "board_123";

        // Act
        CursorProfileDTO profile1 = BoardProfileRegistry.registerProfile(boardToken, "1", "user1", "avatar1.jpg");
        CursorProfileDTO profile2 = BoardProfileRegistry.registerProfile(boardToken, "2", "user2", "avatar2.jpg");
        CursorProfileDTO profile3 = BoardProfileRegistry.registerProfile(boardToken, "3", "user3", "avatar3.jpg");

        // Assert
        assertEquals("1", profile1.id());
        assertEquals("2", profile2.id());
        assertEquals("3", profile3.id());
    }

    @Test
    void getProfilesForBoard_withExistingProfiles() {
        // Arrange
        String boardToken = "board_123";
        BoardProfileRegistry.registerProfile(boardToken, "1", "user1", "avatar1.jpg");
        BoardProfileRegistry.registerProfile(boardToken, "2", "user2", "avatar2.jpg");

        // Act
        List<CursorProfileDTO> profiles = BoardProfileRegistry.getProfilesForBoard(boardToken);

        // Assert
        assertEquals(2, profiles.size());
        assertTrue(profiles.stream().anyMatch(p -> p.username().equals("user1")));
        assertTrue(profiles.stream().anyMatch(p -> p.username().equals("user2")));
    }

    @Test
    void getProfilesForBoard_withNonExistentBoard() {
        // Arrange
        String boardToken = "nonexistent_board";

        // Act
        List<CursorProfileDTO> profiles = BoardProfileRegistry.getProfilesForBoard(boardToken);

        // Assert
        assertNotNull(profiles);
        assertTrue(profiles.isEmpty());
    }

    @Test
    void getProfilesForBoard_afterRemovingAllProfiles() {
        // Arrange
        String boardToken = "board_123";
        CursorProfileDTO profile1 = BoardProfileRegistry.registerProfile(boardToken, "1", "user1", "avatar1.jpg");
        CursorProfileDTO profile2 = BoardProfileRegistry.registerProfile(boardToken, "2", "user2", "avatar2.jpg");

        // Act
        BoardProfileRegistry.removeProfile(boardToken, profile1.id());
        BoardProfileRegistry.removeProfile(boardToken, profile2.id());
        List<CursorProfileDTO> profiles = BoardProfileRegistry.getProfilesForBoard(boardToken);

        // Assert
        assertTrue(profiles.isEmpty());
    }

    @Test
    void removeProfile_withValidProfile() {
        // Arrange
        String boardToken = "board_123";
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, "1", "user1", "avatar1.jpg");

        // Act
        BoardProfileRegistry.removeProfile(boardToken, profile.id());
        List<CursorProfileDTO> profiles = BoardProfileRegistry.getProfilesForBoard(boardToken);

        // Assert
        assertTrue(profiles.isEmpty());
    }

    @Test
    void removeProfile_withNonExistentProfile() {
        // Arrange
        String boardToken = "board_123";
        BoardProfileRegistry.registerProfile(boardToken, "1", "user1", "avatar1.jpg");

        // Act - should not throw
        BoardProfileRegistry.removeProfile(boardToken, "999");
        List<CursorProfileDTO> profiles = BoardProfileRegistry.getProfilesForBoard(boardToken);

        // Assert - original profile should still exist
        assertEquals(1, profiles.size());
    }

    @Test
    void removeProfile_withNullProfileId() {
        // Arrange
        String boardToken = "board_123";
        BoardProfileRegistry.registerProfile(boardToken, "1", "user1", "avatar1.jpg");

        // Act - should not throw
        BoardProfileRegistry.removeProfile(boardToken, null);
        List<CursorProfileDTO> profiles = BoardProfileRegistry.getProfilesForBoard(boardToken);

        // Assert
        assertEquals(1, profiles.size());
    }

    @Test
    void removeProfile_withNullBoardToken() {
        // Act - should not throw
        BoardProfileRegistry.removeProfile(null, "1");

        // Assert - no error
        assertTrue(true);
    }

    @Test
    void clearAll_removesAllProfiles() {
        // Arrange
        BoardProfileRegistry.registerProfile("board_1", "1", "user1", "avatar1.jpg");
        BoardProfileRegistry.registerProfile("board_2", "1", "user2", "avatar2.jpg");

        // Act
        BoardProfileRegistry.clearAll();

        // Assert
        assertTrue(BoardProfileRegistry.getProfilesForBoard("board_1").isEmpty());
        assertTrue(BoardProfileRegistry.getProfilesForBoard("board_2").isEmpty());
    }

    @Test
    void registerProfile_autoIncrementProfileId() {
        // Arrange
        String boardToken = "board_123";

        // Act
        CursorProfileDTO profile1 = BoardProfileRegistry.registerProfile(boardToken, null, "user1", "avatar1.jpg");
        CursorProfileDTO profile2 = BoardProfileRegistry.registerProfile(boardToken, null, "user2", "avatar2.jpg");
        CursorProfileDTO profile3 = BoardProfileRegistry.registerProfile(boardToken, null, "user3", "avatar3.jpg");

        // Assert
        assertEquals("1", profile1.id());
        assertEquals("2", profile2.id());
        assertEquals("3", profile3.id());
    }

    @Test
    void registerProfile_withSpecialCharactersInUsername() {
        // Arrange
        String boardToken = "board_123";
        String username = "user@example.com_#123";

        // Act
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, "1", username, "avatar.jpg");

        // Assert
        assertEquals(username, profile.username());
    }

    @Test
    void registerProfile_withLongUsername() {
        // Arrange
        String boardToken = "board_123";
        String longUsername = "a".repeat(255);

        // Act
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, "1", longUsername, "avatar.jpg");

        // Assert
        assertEquals(longUsername, profile.username());
    }

    @Test
    void registerProfile_withDataUrlAvatar() {
        // Arrange
        String boardToken = "board_123";
        String dataUrlAvatar = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

        // Act
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, "1", "user", dataUrlAvatar);

        // Assert
        assertEquals(dataUrlAvatar, profile.avatar());
    }

    @Test
    void getProfilesForBoard_returnsCopyNotReference() {
        // Arrange
        String boardToken = "board_123";
        BoardProfileRegistry.registerProfile(boardToken, "1", "user1", "avatar1.jpg");

        // Act
        List<CursorProfileDTO> profiles = BoardProfileRegistry.getProfilesForBoard(boardToken);

        // Assert - modifying returned list should not affect registry
        assertDoesNotThrow(() -> profiles.clear());
        assertEquals(1, BoardProfileRegistry.getProfilesForBoard(boardToken).size());
    }

    @Test
    void registerProfile_differentBoardsSameUsername() {
        // Arrange
        String board1 = "board_1";
        String board2 = "board_2";
        String username = "shared_username";

        // Act
        CursorProfileDTO profile1 = BoardProfileRegistry.registerProfile(board1, "1", username, "avatar1.jpg");
        CursorProfileDTO profile2 = BoardProfileRegistry.registerProfile(board2, "1", username, "avatar2.jpg");

        // Assert - same username on different boards should be separate
        assertEquals(1, BoardProfileRegistry.getProfilesForBoard(board1).size());
        assertEquals(1, BoardProfileRegistry.getProfilesForBoard(board2).size());
    }

    @Test
    void registerProfile_concurrentRegistration() throws InterruptedException {
        // Arrange
        String boardToken = "board_123";
        Thread[] threads = new Thread[5];

        // Act
        for (int i = 0; i < 5; i++) {
            final int index = i;
            threads[i] = new Thread(() -> 
                BoardProfileRegistry.registerProfile(boardToken, String.valueOf(index + 1), 
                    "user" + index, "avatar" + index + ".jpg")
            );
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        List<CursorProfileDTO> profiles = BoardProfileRegistry.getProfilesForBoard(boardToken);
        assertEquals(5, profiles.size());
    }

    @Test
    void registerProfile_withNullPreferredId() {
        // Arrange
        String boardToken = "board_123";

        // Act
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, null, "user", "avatar.jpg");

        // Assert - should auto-assign
        assertNotNull(profile);
        assertEquals("1", profile.id());
    }

    @Test
    void registerProfile_withEmptyUsername() {
        // Arrange
        String boardToken = "board_123";

        // Act
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, "1", "", "avatar.jpg");

        // Assert
        assertEquals("", profile.username());
    }

    @Test
    void registerProfile_withEmptyAvatar() {
        // Arrange
        String boardToken = "board_123";

        // Act
        CursorProfileDTO profile = BoardProfileRegistry.registerProfile(boardToken, "1", "user", "");

        // Assert
        assertEquals("", profile.avatar());
    }
}
