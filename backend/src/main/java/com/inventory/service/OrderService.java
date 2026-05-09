package com.inventory.service;

import com.inventory.model.Order;
import com.inventory.model.enums.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the global order ledger — creating, tracking, and querying orders.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();

    public void save(Order order) {
        orders.put(order.getOrderId(), order);
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public List<Order> findAll() {
        return orders.values().stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Order> findByStatus(OrderStatus status) {
        return orders.values().stream()
                .filter(o -> o.getStatus() == status)
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Order> findRecent(int limit) {
        return orders.values().stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            long count = orders.values().stream().filter(o -> o.getStatus() == status).count();
            counts.put(status.name(), count);
        }
        return counts;
    }

    public Map<String, Long> countByWarehouse() {
        return orders.values().stream()
                .filter(o -> o.getFulfilledBy() != null)
                .collect(Collectors.groupingBy(Order::getFulfilledBy, Collectors.counting()));
    }

    public int getTotalCount() { return orders.size(); }

    public int deleteOlderThan(int retentionDays) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<String> toDelete = orders.entrySet().stream()
                .filter(e -> e.getValue().getCreatedAt().isBefore(cutoff))
                .map(Map.Entry::getKey).collect(Collectors.toList());
        toDelete.forEach(orders::remove);
        if (!toDelete.isEmpty()) {
            log.info("Cleaned up {} orders older than {} days", toDelete.size(), retentionDays);
        }
        return toDelete.size();
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalOrders", orders.size());
        stats.put("statusCounts", countByStatus());
        stats.put("warehouseCounts", countByWarehouse());
        long succeeded = orders.values().stream()
                .filter(o -> o.getStatus() == OrderStatus.CONFIRMED || o.getStatus() == OrderStatus.RESERVED)
                .count();
        double successRate = orders.isEmpty() ? 100.0 : Math.round((double) succeeded / orders.size() * 10000.0) / 100.0;
        stats.put("successRate", successRate);
        return stats;
    }
}
