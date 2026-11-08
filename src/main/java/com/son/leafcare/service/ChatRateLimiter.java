package com.son.leafcare.service;

import com.son.leafcare.config.GeminiProperties;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

@Component
public class ChatRateLimiter {

    private static final long WINDOW_MS = 60_000L;

    private final GeminiProperties props;
    private final Map<String, Deque<Long>> sessionBuckets = new ConcurrentHashMap<>();
    private final Deque<Long> globalBucket = new ConcurrentLinkedDeque<>();

    public ChatRateLimiter(GeminiProperties props) {
        this.props = props;
    }

    public synchronized boolean tryAcquire(String sessionKey) {
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;

        prune(globalBucket, cutoff);
        if (globalBucket.size() >= props.getMaxRequestsPerMinuteGlobal()) {
            return false;
        }

        String key = sessionKey == null || sessionKey.isBlank() ? "anonymous" : sessionKey;
        Deque<Long> bucket = sessionBuckets.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        prune(bucket, cutoff);
        if (bucket.size() >= props.getMaxRequestsPerMinutePerSession()) {
            return false;
        }

        globalBucket.addLast(now);
        bucket.addLast(now);
        return true;
    }

    private void prune(Deque<Long> deque, long cutoff) {
        while (!deque.isEmpty()) {
            Long head = deque.peekFirst();
            if (head == null || head < cutoff) {
                deque.pollFirst();
            } else {
                break;
            }
        }
    }
}

