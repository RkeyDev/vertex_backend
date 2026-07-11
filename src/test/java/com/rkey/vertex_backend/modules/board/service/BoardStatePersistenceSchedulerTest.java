package com.rkey.vertex_backend.modules.board.service;

import com.rkey.vertex_backend.modules.board.models.dto.BoardStateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BoardStatePersistenceSchedulerTest {

    @Mock
    private BoardService boardService;

    @Mock
    private ScheduledExecutorService scheduler;

    @InjectMocks
    private BoardStatePersistenceScheduler boardStatePersistenceScheduler;

    private Map<String, ScheduledFuture<?>> pendingByBoard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        // Inject the mocked scheduler using reflection since it is instantiated in field declaration
        Field schedulerField = BoardStatePersistenceScheduler.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(boardStatePersistenceScheduler, scheduler);

        Field pendingField = BoardStatePersistenceScheduler.class.getDeclaredField("pendingByBoard");
        pendingField.setAccessible(true);
        pendingByBoard = (Map<String, ScheduledFuture<?>>) pendingField.get(boardStatePersistenceScheduler);
    }

    @Test
    void testScheduleDbPersist_nullOrEmptyInput() {
        boardStatePersistenceScheduler.scheduleDbPersist(null, new BoardStateDTO("{}", "C"));
        boardStatePersistenceScheduler.scheduleDbPersist("t", null);
        boardStatePersistenceScheduler.scheduleDbPersist("t", new BoardStateDTO(null, "C"));
        boardStatePersistenceScheduler.scheduleDbPersist("t", new BoardStateDTO("", "C"));

        verifyNoInteractions(scheduler);
    }

    @Test
    void testScheduleDbPersist_newSchedule() {
        BoardStateDTO dto = new BoardStateDTO("{}", "C");
        ScheduledFuture mockFuture = mock(ScheduledFuture.class);
        when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(mockFuture);

        boardStatePersistenceScheduler.scheduleDbPersist("token-123", dto);

        assertTrue(pendingByBoard.containsKey("token-123"));
        assertEquals(mockFuture, pendingByBoard.get("token-123"));
    }

    @Test
    void testScheduleDbPersist_existingScheduleCancelled() {
        BoardStateDTO dto = new BoardStateDTO("{}", "C");
        ScheduledFuture mockFuture1 = mock(ScheduledFuture.class);
        ScheduledFuture mockFuture2 = mock(ScheduledFuture.class);
        
        when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
            .thenReturn(mockFuture1)
            .thenReturn(mockFuture2);

        boardStatePersistenceScheduler.scheduleDbPersist("token-123", dto);
        boardStatePersistenceScheduler.scheduleDbPersist("token-123", dto);

        verify(mockFuture1).cancel(false);
        assertTrue(pendingByBoard.containsKey("token-123"));
        assertEquals(mockFuture2, pendingByBoard.get("token-123"));
    }

    @Test
    void testShutdown() {
        boardStatePersistenceScheduler.shutdown();
        verify(scheduler).shutdownNow();
    }
}
