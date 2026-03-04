package com.example.springai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple fixed-window rate limiter applied only to AI inference endpoints
 * (/api/agent/chat, /task, /stream) to prevent cost runaway.
 *
 * Window resets every 60 seconds per API key (falls back to remote IP).
 * Configure limit:  agent.rate-limit.requests-per-minute  (default 20)
 */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final int maxPerMinute;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${agent.rate-limit.requests-per-minute:20}") int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Only rate-limit expensive AI inference paths
        if (!path.startsWith("/api/agent/chat")
                && !path.startsWith("/api/agent/task")
                && !path.startsWith("/api/agent/stream")) {
            chain.doFilter(request, response);
            return;
        }

        // Identify caller: prefer the API key (already validated), fall back to IP
        String key = request.getHeader("X-API-Key");
        if (key == null) key = request.getParameter("apiKey");
        if (key == null) key = request.getRemoteAddr();

        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter());
        if (!counter.tryAcquire(maxPerMinute)) {
            log.warn("Rate limit exceeded for caller: {}", key);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Rate limit exceeded. Max " + maxPerMinute + " requests/minute.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    // --- Fixed-window counter (thread-safe) ---

    private static class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        boolean tryAcquire(int limit) {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60_000L) {
                synchronized (this) {
                    if (now - windowStart > 60_000L) {   // double-checked locking
                        windowStart = now;
                        count.set(0);
                    }
                }
            }
            return count.incrementAndGet() <= limit;
        }
    }
}
