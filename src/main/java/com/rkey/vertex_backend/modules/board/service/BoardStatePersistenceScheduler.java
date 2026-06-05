package com.rkey.vertex_backend.modules.board.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.rkey.vertex_backend.modules.board.models.dto.BoardStateDTO;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Debounces PostgreSQL persistence for high-frequency board updates (dragging).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardStatePersistenceScheduler {

    private static final long DEBOUNCE_MS = 2_000;

    private final BoardService boardService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "board-db-persist");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, ScheduledFuture<?>> pendingByBoard = new ConcurrentHashMap<>();

    public void scheduleDbPersist(String boardToken, BoardStateDTO stateDto) {
        if (boardToken == null || stateDto == null || stateDto.boardStateJson() == null
                || stateDto.boardStateJson().isEmpty()) {
            return;
        }

        ScheduledFuture<?> previous = pendingByBoard.get(boardToken);
        if (previous != null) {
            previous.cancel(false);
        }

        ScheduledFuture<?> scheduled = scheduler.schedule(() -> {
            pendingByBoard.remove(boardToken);
            boardService.saveBoardInDb(boardToken, stateDto);
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);

        pendingByBoard.put(boardToken, scheduled);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
