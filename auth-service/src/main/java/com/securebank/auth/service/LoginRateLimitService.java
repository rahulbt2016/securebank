package com.securebank.auth.service;

import com.securebank.common.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Tracks failed login attempts per email address in Redis using a fixed-window counter.
 *
 * How it works:
 *   - Each failed attempt increments a Redis key: "auth:login:attempts:{email}"
 *   - The key expires after {@code windowSeconds} (default 15 min), resetting the window.
 *   - Once the counter reaches {@code maxAttempts} (default 5), subsequent attempts
 *     are rejected with 429 Too Many Requests until the window expires.
 *   - A successful login clears the counter immediately.
 *
 * Why per email and not per IP?
 *   Per-email protects the specific account being targeted. IP-based limiting
 *   is a complementary layer better handled at the API gateway/load balancer level
 *   (Phase 6) where X-Forwarded-For is more reliably available.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    private static final String KEY_PREFIX = "auth:login:attempts:";

    private final StringRedisTemplate redis;

    @Value("${rate-limit.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${rate-limit.login.window-seconds:900}")
    private long windowSeconds;

    /**
     * Check whether this email has exceeded the attempt limit.
     * Call this BEFORE validating credentials.
     * Throws {@link TooManyRequestsException} (429) if the limit is exceeded.
     */
    public void checkLimit(String email) {
        String raw = redis.opsForValue().get(KEY_PREFIX + email);
        if (raw != null && Integer.parseInt(raw) >= maxAttempts) {
            long ttl = Objects.requireNonNullElse(
                    redis.getExpire(KEY_PREFIX + email, TimeUnit.SECONDS), windowSeconds);
            log.warn("Rate limit exceeded for email: {}, retry after {}s", email, ttl);
            throw new TooManyRequestsException(ttl);
        }
    }

    /**
     * Increment the failure counter for this email.
     * Call this AFTER a failed credential check.
     */
    public void recordFailure(String email) {
        String key = KEY_PREFIX + email;
        Long count = redis.opsForValue().increment(key);
        // Set TTL only on the first increment so the window starts from the first failure.
        if (count != null && count == 1) {
            redis.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        log.debug("Login failure recorded for {}: attempt {}/{}", email, count, maxAttempts);
    }

    /**
     * Clear the failure counter after a successful login.
     */
    public void clearLimit(String email) {
        redis.delete(KEY_PREFIX + email);
    }
}
