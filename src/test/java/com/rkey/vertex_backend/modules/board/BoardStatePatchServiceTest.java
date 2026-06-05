package com.rkey.vertex_backend.modules.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkey.vertex_backend.modules.board.models.dto.ComponentTransformDTO;
import com.rkey.vertex_backend.modules.board.service.BoardRoomCacheService;
import com.rkey.vertex_backend.modules.board.service.BoardStatePatchService;
import com.rkey.vertex_backend.modules.board.service.BoardStatePersistenceScheduler;

@ExtendWith(MockitoExtension.class)
class BoardStatePatchServiceTest {

    private static final String BOARD_JSON = """
            {"components":[{"id":"comp-1","xPos":10,"yPos":20,"width":100,"height":80}],"arrows":[]}
            """;

    @Mock
    private BoardRoomCacheService boardRoomCacheService;

    @Mock
    private BoardStatePersistenceScheduler boardStatePersistenceScheduler;

    private BoardStatePatchService patchService;

    @BeforeEach
    void setUp() {
        patchService = new BoardStatePatchService(
                new ObjectMapper(),
                boardRoomCacheService,
                boardStatePersistenceScheduler);
    }

    @Test
    void patchComponentPosition_updatesMatchingComponent() {
        ComponentTransformDTO transform = new ComponentTransformDTO("comp-1", 401, 526, null, null, "user-1");

        String patched = patchService.patchComponentPosition(BOARD_JSON, transform);

        assertNotNull(patched);
        assertTrue(patched.contains("\"xPos\":401"));
        assertTrue(patched.contains("\"yPos\":526"));
    }

    @Test
    void patchComponentPosition_updatesSizeWhenProvided() {
        ComponentTransformDTO transform = new ComponentTransformDTO("comp-1", 401, 526, 200.0, 150.0, "user-1");

        String patched = patchService.patchComponentPosition(BOARD_JSON, transform);

        assertNotNull(patched);
        assertTrue(patched.contains("\"width\":200"));
        assertTrue(patched.contains("\"height\":150"));
    }

    @Test
    void patchComponentPosition_returnsNullWhenComponentMissing() {
        ComponentTransformDTO transform = new ComponentTransformDTO("missing", 1, 2, null, null, "user-1");

        assertNull(patchService.patchComponentPosition(BOARD_JSON, transform));
    }

    @Test
    void applyTransformAsync_skipsWhenCacheEmpty() throws Exception {
        ComponentTransformDTO transform = new ComponentTransformDTO("comp-1", 1, 2, null, null, "user-1");

        patchService.applyTransformAsync("token", transform);
        Thread.sleep(100);

        verifyNoInteractions(boardStatePersistenceScheduler);
        verify(boardRoomCacheService, never()).saveBoardData(any(), any());
    }

    @Test
    void applyTransformAsync_persistsPatchedState() throws Exception {
        ComponentTransformDTO transform = new ComponentTransformDTO("comp-1", 99, 88, null, null, "user-1");
        org.mockito.Mockito.when(boardRoomCacheService.getBoardData("token")).thenReturn(BOARD_JSON);

        patchService.applyTransformAsync("token", transform);
        Thread.sleep(200);

        verify(boardRoomCacheService).saveBoardData(eq("token"), org.mockito.ArgumentMatchers.contains("\"xPos\":99"));
        verify(boardStatePersistenceScheduler).scheduleDbPersist(eq("token"), any());
    }
}
