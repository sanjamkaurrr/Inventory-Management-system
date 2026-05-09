package com.inventory.service;

import com.inventory.exception.QuotaExceededException;
import com.inventory.exception.RateLimitException;
import com.inventory.model.enums.Tier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enforces tier-based quotas and rate limits on every API request.
 * Uses a simple token-bucket approach for rate limiting.
 */
@Service
public class QuotaManager {

    private static final Logger log = LoggerFactory.getLogger(QuotaManager.class);

    // Per-API-key tracking
    private final ConcurrentHashMap<String, QuotaTracker> trackers = new ConcurrentHashMap<>();

    // Tier limits
    private static final Map<Tier, Integer> DAILY_LIMITS = Map.of(
            Tier.FREE, 1000, Tier.PROFESSIONAL, 50000, Tier.ENTERPRISE, Integer.MAX_VALUE);
    private static final Map<Tier, Integer> MAX_CONNECTIONS = Map.of(
            Tier.FREE, 5, Tier.PROFESSIONAL, 50, Tier.ENTERPRISE, Integer.MAX_VALUE);
    private static final Map<Tier, Integer> RATE_LIMITS = Map.of(
            Tier.FREE, 10, Tier.PROFESSIONAL, 100, Tier.ENTERPRISE, Integer.MAX_VALUE);
    private static final Map<Tier, Integer> MAX_WAREHOUSES = Map.of(
            Tier.FREE, 2, Tier.PROFESSIONAL, 10, Tier.ENTERPRISE, Integer.MAX_VALUE);
    private static final Map<Tier, List<String>> EXPORT_FORMATS = Map.of(
            Tier.FREE, List.of("CSV"),
            Tier.PROFESSIONAL, Arrays.asList("CSV", "JSON", "Parquet"),
            Tier.ENTERPRISE, Arrays.asList("CSV", "JSON", "Parquet", "Avro", "Protobuf"));

    // Demo API keys
    private static final Map<String, Tier> DEMO_KEYS = Map.of(
            "demo-free-key", Tier.FREE,
            "demo-pro-key", Tier.PROFESSIONAL,
            "demo-enterprise-key", Tier.ENTERPRISE);

    public Tier getTierForApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return Tier.FREE;
        return DEMO_KEYS.getOrDefault(apiKey, Tier.FREE);
    }

    public void validateRequest(String apiKey) {
        Tier tier = getTierForApiKey(apiKey);
        QuotaTracker tracker = trackers.computeIfAbsent(apiKey, k -> new QuotaTracker());

        log.debug("   \ud83d\udee1\ufe0f Quota check: key={}, tier={}", apiKey, tier);

        // Check daily limit
        tracker.resetIfNewDay();
        int dailyLimit = DAILY_LIMITS.get(tier);
        long currentUsage = tracker.dailyCount.get();
        log.debug("   Daily usage: {}/{}", currentUsage, dailyLimit == Integer.MAX_VALUE ? "unlimited" : dailyLimit);

        if (dailyLimit != Integer.MAX_VALUE && currentUsage >= dailyLimit) {
            log.warn("   \u274c QUOTA EXCEEDED \u2014 {} tier daily limit of {} reached", tier, dailyLimit);
            throw new QuotaExceededException(
                    "Daily API limit exceeded for tier " + tier + ". Limit: " + dailyLimit);
        }

        // Simple rate limiting: check requests in last second
        long now = System.currentTimeMillis();
        int rateLimit = RATE_LIMITS.get(tier);
        if (rateLimit != Integer.MAX_VALUE) {
            long recentRequests = tracker.requestTimestamps.values().stream()
                    .filter(ts -> (now - ts) < 1000).count();
            log.debug("   Rate check: {} req in last 1s (limit: {}/s)", recentRequests, rateLimit);
            if (recentRequests >= rateLimit) {
                log.warn("   \u274c RATE LIMITED \u2014 {} tier max {} req/sec exceeded", tier, rateLimit);
                throw new RateLimitException(
                        "Rate limit exceeded for tier " + tier + ". Max " + rateLimit + " req/sec");
            }
        }

        // Record this request
        tracker.dailyCount.incrementAndGet();
        tracker.requestTimestamps.put(Thread.currentThread().getId(), now);
        tracker.totalRequests.incrementAndGet();
        log.debug("   \u2705 Quota passed \u2014 request #{} for today", tracker.dailyCount.get());
    }

    public void validateExportFormat(String apiKey, String format) {
        Tier tier = getTierForApiKey(apiKey);
        List<String> allowed = EXPORT_FORMATS.get(tier);
        if (!allowed.contains(format.toUpperCase())) {
            throw new QuotaExceededException(
                    "Export format '" + format + "' not available in tier " + tier);
        }
    }

    public int getMaxWarehouses(String apiKey) {
        return MAX_WAREHOUSES.get(getTierForApiKey(apiKey));
    }

    public Map<String, Object> getUsageStats(String apiKey) {
        Tier tier = getTierForApiKey(apiKey);
        QuotaTracker tracker = trackers.computeIfAbsent(apiKey, k -> new QuotaTracker());
        tracker.resetIfNewDay();

        int dailyLimit = DAILY_LIMITS.get(tier);
        long used = tracker.dailyCount.get();

        return Map.of(
                "tier", tier.name(),
                "dailyLimit", dailyLimit == Integer.MAX_VALUE ? "unlimited" : dailyLimit,
                "dailyUsed", used,
                "dailyRemaining", dailyLimit == Integer.MAX_VALUE ? "unlimited" : Math.max(0, dailyLimit - used),
                "rateLimit", RATE_LIMITS.get(tier) == Integer.MAX_VALUE ? "unlimited" : RATE_LIMITS.get(tier) + " req/sec",
                "maxConnections", MAX_CONNECTIONS.get(tier) == Integer.MAX_VALUE ? "unlimited" : MAX_CONNECTIONS.get(tier),
                "totalRequests", tracker.totalRequests.get()
        );
    }

    /** Inner class tracking per-API-key usage */
    private static class QuotaTracker {
        final AtomicLong dailyCount = new AtomicLong(0);
        final AtomicLong totalRequests = new AtomicLong(0);
        final ConcurrentHashMap<Long, Long> requestTimestamps = new ConcurrentHashMap<>();
        volatile LocalDate resetDate = LocalDate.now();

        void resetIfNewDay() {
            LocalDate today = LocalDate.now();
            if (!today.equals(resetDate)) {
                dailyCount.set(0);
                resetDate = today;
                requestTimestamps.clear();
            }
        }
    }
}
