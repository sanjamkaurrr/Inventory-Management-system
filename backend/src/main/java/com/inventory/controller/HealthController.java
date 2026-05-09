package com.inventory.controller;

import com.inventory.bootstrap.DataBootstrapper;
import com.inventory.service.QuotaManager;
import com.inventory.service.StockManager;
import com.inventory.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Health check and monitoring endpoints.
 */
@RestController
@CrossOrigin(origins = "*")
public class HealthController {

    private final DataBootstrapper bootstrapper;
    private final StockManager stockManager;
    private final OrderService orderService;
    private final QuotaManager quotaManager;

    public HealthController(DataBootstrapper bootstrapper, StockManager stockManager,
                            OrderService orderService, QuotaManager quotaManager) {
        this.bootstrapper = bootstrapper;
        this.stockManager = stockManager;
        this.orderService = orderService;
        this.quotaManager = quotaManager;
    }

    /** GET /health/live — Is the service running? */
    @GetMapping("/health/live")
    public ResponseEntity<Map<String, Object>> liveness() {
        return ResponseEntity.ok(Map.of("status", "UP", "timestamp", Instant.now().toString()));
    }

    /** GET /health/ready — Is all data loaded and valid? */
    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        boolean ready = bootstrapper.isWarehousesLoaded()
                && bootstrapper.isProductsLoaded()
                && bootstrapper.isInventoryLoaded();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", ready ? "READY" : "NOT_READY");
        response.put("warehouses", bootstrapper.isWarehousesLoaded());
        response.put("products", bootstrapper.isProductsLoaded());
        response.put("inventory", bootstrapper.isInventoryLoaded());
        response.put("timestamp", Instant.now().toString());

        return ready ? ResponseEntity.ok(response)
                     : ResponseEntity.status(503).body(response);
    }

    /** GET /health/data — Data freshness and load status */
    @GetMapping("/health/data")
    public ResponseEntity<Map<String, Object>> dataHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "healthy");
        response.put("source", bootstrapper.getLoadSource());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("warehouses", Map.of(
                "loaded", bootstrapper.isWarehousesLoaded(),
                "count", bootstrapper.getWarehouseCount()));
        data.put("products", Map.of(
                "loaded", bootstrapper.isProductsLoaded(),
                "count", bootstrapper.getProductCount()));
        data.put("inventory", Map.of(
                "loaded", bootstrapper.isInventoryLoaded(),
                "entries", bootstrapper.getInventoryEntryCount()));
        response.put("data", data);

        return ResponseEntity.ok(response);
    }

    /** GET /api/v1/dashboard — Combined dashboard data for the frontend */
    @GetMapping("/api/v1/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(
            @RequestHeader(value = "X-API-Key", defaultValue = "demo-free-key") String apiKey) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");

        // Warehouse utilization
        response.put("warehouses", stockManager.getWarehouseUtilization());

        // Order stats
        response.put("orderStats", orderService.getStatistics());

        // Stock manager stats
        Map<String, Object> systemStats = new LinkedHashMap<>();
        systemStats.put("totalOrdersProcessed", stockManager.getTotalOrdersProcessed());
        systemStats.put("totalOrdersSucceeded", stockManager.getTotalOrdersSucceeded());
        systemStats.put("totalOrdersFailed", stockManager.getTotalOrdersFailed());
        systemStats.put("totalItemsReserved", stockManager.getTotalItemsReserved());
        systemStats.put("totalItemsConfirmed", stockManager.getTotalItemsConfirmed());
        response.put("systemStats", systemStats);

        // Tier usage
        response.put("tierUsage", quotaManager.getUsageStats(apiKey));

        // Recent orders
        response.put("recentOrders", orderService.findRecent(20));

        // Inventory summary
        Map<String, Map<String, Integer>> skuTotals = new LinkedHashMap<>();
        for (String sku : stockManager.getAllSkus()) {
            int totalAvail = 0, totalRes = 0, totalDef = 0;
            for (String whId : stockManager.getWarehouseIds()) {
                var se = stockManager.getStockEntry(whId, sku);
                if (se != null) {
                    totalAvail += se.getAvailable();
                    totalRes += se.getReserved();
                    totalDef += se.getDefective();
                }
            }
            skuTotals.put(sku, Map.of("available", totalAvail, "reserved", totalRes, "defective", totalDef));
        }
        response.put("inventorySummary", skuTotals);

        // Products catalog
        response.put("products", bootstrapper.getProductCatalog());

        return ResponseEntity.ok(response);
    }
}
