package com.inventory.controller;

import com.inventory.model.Order;
import com.inventory.model.enums.OrderStatus;
import com.inventory.service.OrderService;
import com.inventory.service.QuotaManager;
import com.inventory.service.StockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API for order management.
 *
 * @RestController — Tells Spring: "This class handles HTTP requests and returns JSON"
 * @RequestMapping — Sets the base URL path for all endpoints in this class
 * @CrossOrigin   — Allows the frontend (on a different port) to call these endpoints
 */
@RestController
@RequestMapping("/api/v1/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final StockManager stockManager;
    private final OrderService orderService;
    private final QuotaManager quotaManager;

    public OrderController(StockManager stockManager, OrderService orderService, QuotaManager quotaManager) {
        this.stockManager = stockManager;
        this.orderService = orderService;
        this.quotaManager = quotaManager;
    }

    /**
     * POST /api/v1/orders — Create a new order
     * Body: { "items": {"SKU-001": 2, "SKU-002": 5}, "preferredWarehouse": "EAST_COAST" }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader(value = "X-API-Key", defaultValue = "demo-free-key") String apiKey,
            @RequestBody Order order) {

        log.info("══════════════════════════════════════════════");
        log.info("📨 POST /api/v1/orders — New order request");
        log.info("   API Key: {}, Tier: {}", apiKey, quotaManager.getTierForApiKey(apiKey));
        log.info("   Items: {}, Preferred WH: {}", order.getItems(), order.getPreferredWarehouse());

        // Step 1: Quota check
        log.info("   [1/4] Checking quota & rate limit...");
        quotaManager.validateRequest(apiKey);
        log.info("   [1/4] ✅ Quota check passed");

        // Step 2: Validate input
        log.info("   [2/4] Validating order input...");
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        for (Map.Entry<String, Integer> item : order.getItems().entrySet()) {
            if (item.getValue() <= 0) {
                throw new IllegalArgumentException("Quantity must be > 0 for SKU: " + item.getKey());
            }
        }
        log.info("   [2/4] ✅ Input valid — {} items, {} total quantity",
                order.getItems().size(),
                order.getItems().values().stream().mapToInt(Integer::intValue).sum());

        order.setApiKey(apiKey);
        order.setTier(quotaManager.getTierForApiKey(apiKey));

        // Step 3: Process the order (thread-safe)
        log.info("   [3/4] Sending to StockManager.processOrder()...");
        Order processed = stockManager.processOrder(order);

        // Step 4: Save to ledger
        log.info("   [4/4] Saving to order ledger...");
        orderService.save(processed);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", processed.getStatus() == OrderStatus.FAILED ? "error" : "success");
        response.put("orderId", processed.getOrderId());
        response.put("orderStatus", processed.getStatus().name());
        response.put("fulfilledBy", processed.getFulfilledBy());
        response.put("timestamp", processed.getCreatedAt().toString());
        response.put("items", processed.getItems());
        if (processed.getFailureReason() != null) {
            response.put("failureReason", processed.getFailureReason());
        }
        response.put("triedWarehouses", processed.getTriedWarehouses());

        HttpStatus status = processed.getStatus() == OrderStatus.FAILED
                ? HttpStatus.CONFLICT : HttpStatus.CREATED;

        log.info("📤 Response: {} {} — order {} at {}",
                status.value(), status.name(), processed.getOrderId(), processed.getFulfilledBy());
        log.info("══════════════════════════════════════════════");
        return ResponseEntity.status(status).body(response);
    }

    /** GET /api/v1/orders — List all orders (recent first) */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listOrders(
            @RequestHeader(value = "X-API-Key", defaultValue = "demo-free-key") String apiKey,
            @RequestParam(defaultValue = "50") int limit) {
        quotaManager.validateRequest(apiKey);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("orders", orderService.findRecent(limit));
        response.put("total", orderService.getTotalCount());
        return ResponseEntity.ok(response);
    }

    /** GET /api/v1/orders/{id} — Get order details */
    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(
            @RequestHeader(value = "X-API-Key", defaultValue = "demo-free-key") String apiKey,
            @PathVariable String orderId) {
        quotaManager.validateRequest(apiKey);

        return orderService.findById(orderId)
                .map(order -> {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("status", "success");
                    res.put("order", order);
                    return ResponseEntity.ok(res);
                })
                .orElseGet(() -> {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("status", "error");
                    res.put("message", "Order not found: " + orderId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(res);
                });
    }

    /** POST /api/v1/orders/{id}/confirm — Confirm an order (simulate payment) */
    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<Map<String, Object>> confirmOrder(
            @RequestHeader(value = "X-API-Key", defaultValue = "demo-free-key") String apiKey,
            @PathVariable String orderId) {
        log.info("📨 POST /api/v1/orders/{}/confirm — Confirming order", orderId);
        quotaManager.validateRequest(apiKey);

        return orderService.findById(orderId)
                .map(order -> {
                    if (order.getStatus() != OrderStatus.RESERVED) {
                        log.warn("   ❌ Cannot confirm — order is {} (must be RESERVED)", order.getStatus());
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("status", "error");
                        res.put("message", "Order is not in RESERVED state. Current: " + order.getStatus());
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
                    }
                    log.info("   Calling StockManager.confirmStock() — moving reserved → shipped");
                    stockManager.confirmStock(order.getFulfilledBy(), order.getItems());
                    order.setStatus(OrderStatus.CONFIRMED);
                    orderService.save(order);
                    log.info("   ✅ Order {} CONFIRMED — stock permanently deducted", orderId);

                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("status", "success");
                    res.put("orderId", orderId);
                    res.put("orderStatus", "CONFIRMED");
                    return ResponseEntity.ok(res);
                })
                .orElseGet(() -> {
                    log.warn("   ❌ Order {} not found in ledger", orderId);
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("status", "error");
                    res.put("message", "Order not found: " + orderId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(res);
                });
    }

    /** POST /api/v1/orders/{id}/cancel — Cancel an order */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @RequestHeader(value = "X-API-Key", defaultValue = "demo-free-key") String apiKey,
            @PathVariable String orderId) {
        log.info("📨 POST /api/v1/orders/{}/cancel — Cancelling order", orderId);
        quotaManager.validateRequest(apiKey);

        return orderService.findById(orderId)
                .map(order -> {
                    if (order.getStatus() != OrderStatus.RESERVED) {
                        log.warn("   ❌ Cannot cancel — order is {} (must be RESERVED)", order.getStatus());
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("status", "error");
                        res.put("message", "Only RESERVED orders can be cancelled. Current: " + order.getStatus());
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
                    }
                    log.info("   Calling StockManager.releaseReservedStock() — returning stock");
                    stockManager.releaseReservedStock(order.getFulfilledBy(), order.getItems());
                    order.setStatus(OrderStatus.CANCELLED);
                    orderService.save(order);
                    log.info("   ✅ Order {} CANCELLED — stock returned to available pool", orderId);

                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("status", "success");
                    res.put("orderId", orderId);
                    res.put("orderStatus", "CANCELLED");
                    return ResponseEntity.ok(res);
                })
                .orElseGet(() -> {
                    log.warn("   ❌ Order {} not found in ledger", orderId);
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("status", "error");
                    res.put("message", "Order not found: " + orderId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(res);
                });
    }

    /** GET /api/v1/orders/stats — Order statistics */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestHeader(value = "X-API-Key", defaultValue = "demo-free-key") String apiKey) {
        quotaManager.validateRequest(apiKey);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.putAll(orderService.getStatistics());
        return ResponseEntity.ok(response);
    }
}
