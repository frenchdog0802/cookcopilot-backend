package com.lardermind.service;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Ensures only one chat turn runs per user at a time. Concurrent turns against
 * the same chat memory corrupt tool-call / tool-result sequencing.
 *
 * <p>Uses a {@link Semaphore} (not a reentrant lock) so stream callbacks can
 * release from a different thread than the one that acquired.
 */
@Component
public class ChatSessionGuard {

    private final ConcurrentHashMap<UUID, Semaphore> locks = new ConcurrentHashMap<>();

    public boolean tryAcquire(UUID userId) {
        Semaphore semaphore = locks.computeIfAbsent(userId, id -> new Semaphore(1));
        return semaphore.tryAcquire();
    }

    public void release(UUID userId) {
        Semaphore semaphore = locks.get(userId);
        if (semaphore == null) {
            return;
        }
        semaphore.release();
        // Guard against double-release from stream complete + timeout/error paths.
        while (semaphore.availablePermits() > 1) {
            if (!semaphore.tryAcquire()) {
                break;
            }
        }
    }
}
