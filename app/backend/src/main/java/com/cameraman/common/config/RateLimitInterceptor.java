package com.cameraman.common.config;

import com.cameraman.common.exception.RateLimitException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-IP rate limiting for the endpoints reachable without a login: /auth/**, /public/**,
 * and /webhooks/**. These were the one gap in the security model — every other endpoint
 * requires a JWT, but these can't, since a client booking form, a login page, or an
 * attendance device obviously has no session yet. Without a limiter, /auth/login is a
 * standing invitation to brute-force, and the public booking form / webhook endpoints can
 * be hammered without consequence.
 *
 * Deliberately in-memory (Bucket4j core, no Redis) rather than distributed: this app runs
 * as a single backend container (see docker-compose.yml), so a per-instance limiter is
 * exactly as effective as a distributed one here, without betting on a Bucket4j-Redis
 * integration API this environment has no way to compile-check. If the app is later scaled
 * to multiple backend instances behind a load balancer, each instance would enforce its own
 * limit independently (e.g. 3 instances effectively triple the ceiling) — swapping the
 * bucket store below for bucket4j-redis (already a pom.xml dependency) closes that gap when
 * that day comes; not needed for a single-instance deployment today.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep = new AtomicLong(System.currentTimeMillis());

    @Override
    public boolean preHandle(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, Object handler) {
        String path = request.getServletPath();
        RateLimitRule rule = ruleFor(path);
        if (rule == null) {
            return true; // not a rate-limited path
        }

        String key = rule.name() + ":" + clientIp(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(rule));
        sweepOccasionally();

        if (bucket.tryConsume(1)) {
            return true;
        }
        throw new RateLimitException("Too many requests — please wait a few minutes and try again.");
    }

    private Bucket newBucket(RateLimitRule rule) {
        Bandwidth limit = Bandwidth.classic(rule.capacity, Refill.intervally(rule.capacity, rule.window));
        return Bucket.builder().addLimit(limit).build();
    }

    /** Most specific match wins (login gets its own, tighter limit than /auth/** generally).
     *  Matched against the servlet path (context-path already stripped by the container) —
     *  the same path space addPathPatterns() in WebMvcConfig uses, so this stays correct
     *  regardless of what server.servlet.context-path is set to. */
    private RateLimitRule ruleFor(String path) {
        if (path.startsWith("/auth/login")) { return RateLimitRule.LOGIN; }
        if (path.startsWith("/auth/")) { return RateLimitRule.AUTH_OTHER; }
        if (path.startsWith("/public/")) { return RateLimitRule.PUBLIC_FORM; }
        if (path.startsWith("/webhooks/")) { return RateLimitRule.WEBHOOK; }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Buckets for IPs that stopped requesting would otherwise sit in memory forever.
     *  Cheap opportunistic cleanup — piggybacks on real traffic rather than a scheduled
     *  job, since this only needs to bound memory, not run on a strict schedule. */
    private void sweepOccasionally() {
        long now = System.currentTimeMillis();
        long last = lastSweep.get();
        if (now - last > TimeUnit.MINUTES.toMillis(30) && lastSweep.compareAndSet(last, now)) {
            buckets.clear();
        }
    }

    private enum RateLimitRule {
        LOGIN(10, Duration.ofMinutes(5)),
        AUTH_OTHER(20, Duration.ofMinutes(15)),
        PUBLIC_FORM(30, Duration.ofMinutes(10)),
        WEBHOOK(120, Duration.ofMinutes(1));

        final int capacity;
        final Duration window;
        RateLimitRule(int capacity, Duration window) { this.capacity = capacity; this.window = window; }
    }
}
