package com.inventory.scheduler;

import com.inventory.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background job that cleans up old orders based on tier retention policy.
 * Runs daily at 2 AM UTC by default.
 */
@Component
public class OrderCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderCleanupScheduler.class);

    private final OrderService orderService;

    @Value("${app.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    /** FREE tier: 7-day retention */
    private static final int FREE_TIER_RETENTION_DAYS = 7;

    public OrderCleanupScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(cron = "${app.cleanup.cron:0 0 2 * * *}")
    public void cleanupExpiredOrders() {
        if (!cleanupEnabled) return;

        log.info("Running order cleanup job...");
        int deleted = orderService.deleteOlderThan(FREE_TIER_RETENTION_DAYS);
        log.info("Order cleanup complete. Removed {} expired orders.", deleted);
    }
}
