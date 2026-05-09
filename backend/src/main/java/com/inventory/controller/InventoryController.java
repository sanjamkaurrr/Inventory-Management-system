package com.inventory.controller;

import com.inventory.model.StockEntry;
import com.inventory.service.QuotaManager;
import com.inventory.service.StockManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API for querying inventory across warehouses.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@CrossOrigin(origins = "*")
public class InventoryController {

    private final StockManager stockManager;
    private final QuotaManager quotaManager;

    public InventoryController(StockManager stockManager, QuotaManager quotaManager) {
        this.stockManager = stockManager;
        this.quotaManager = quotaManager;
    }

    /** GET /api/v1/inventory — Full inventory snapshot across all warehouses */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getFullInventory(
            @RequestHeader(value = "X-API-Key", defaultValue = "demo-free-key") String apiKey) {
        quotaManager.validateRequest(apiKey);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("inventory", stockManager.getFullInventory());
        response.put("warehouses", stockManager.getWarehouseUtilization());
        return ResponseEntity.ok(response);
    }

    /** GET /api/v1/inventory/{warehouse} — All SKUs in a specific warehouse */
    @GetMapping("/{warehouseId}")
    public ResponseEntity<Map<String, Object>> getWarehouseInventory(
            @RequestHeader(value = "X-API-Key", defaultValue = "demo-free-key") String apiKey,
            @PathVariable String warehouseId) {
        quotaManager.validateRequest(apiKey);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("warehouseId", warehouseId);
        response.put("inventory", stockManager.getWarehouseInventory(warehouseId));
        return ResponseEntity.ok(response);
    }

    /** GET /api/v1/inventory/{warehouse}/{sku} — Specific SKU at a warehouse */
    @GetMapping("/{warehouseId}/{sku}")
    public ResponseEntity<Map<String, Object>> getSkuInventory(
            @RequestHeader(value = "X-API-Key", defaultValue = "demo-free-key") String apiKey,
            @PathVariable String warehouseId,
            @PathVariable String sku) {
        quotaManager.validateRequest(apiKey);

        StockEntry entry = stockManager.getStockEntry(warehouseId, sku);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("warehouseId", warehouseId);
        response.put("sku", sku);
        if (entry != null) {
            response.put("available", entry.getAvailable());
            response.put("reserved", entry.getReserved());
            response.put("defective", entry.getDefective());
            response.put("totalPhysical", entry.getTotalPhysical());
        } else {
            response.put("available", 0);
            response.put("message", "SKU not found at this warehouse");
        }
        return ResponseEntity.ok(response);
    }

    /** GET /api/v1/inventory/summary — Aggregated summary */
    @GetMapping("/summary/overview")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestHeader(value = "X-API-Key", defaultValue = "demo-free-key") String apiKey) {
        quotaManager.validateRequest(apiKey);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("warehouseUtilization", stockManager.getWarehouseUtilization());
        response.put("totalSkus", stockManager.getAllSkus().size());
        response.put("warehouseCount", stockManager.getWarehouses().size());

        // Per-SKU global totals
        Map<String, Map<String, Integer>> skuTotals = new LinkedHashMap<>();
        for (String sku : stockManager.getAllSkus()) {
            Map<String, Integer> totals = new LinkedHashMap<>();
            int totalAvail = 0, totalRes = 0, totalDef = 0;
            for (String whId : stockManager.getWarehouseIds()) {
                StockEntry se = stockManager.getStockEntry(whId, sku);
                if (se != null) {
                    totalAvail += se.getAvailable();
                    totalRes += se.getReserved();
                    totalDef += se.getDefective();
                }
            }
            totals.put("available", totalAvail);
            totals.put("reserved", totalRes);
            totals.put("defective", totalDef);
            skuTotals.put(sku, totals);
        }
        response.put("skuGlobalTotals", skuTotals);
        return ResponseEntity.ok(response);
    }
}
