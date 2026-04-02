package com.heritage.marketplace.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> attemptsByAccount = new ConcurrentHashMap<>();

    public int recordFailure(String accountKey) {
        Deque<Instant> attempts = attemptsByAccount.computeIfAbsent(accountKey, key -> new ArrayDeque<>());
        synchronized (attempts) {
            Instant now = Instant.now();
            pruneExpired(attempts, now);
            attempts.addLast(now);
            return attempts.size();
        }
    }

    public void reset(String accountKey) {
        attemptsByAccount.remove(accountKey);
    }

    private void pruneExpired(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }
}
