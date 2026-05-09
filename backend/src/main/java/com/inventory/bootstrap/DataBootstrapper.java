package com.inventory.bootstrap;

import com.inventory.exception.DataValidationException;
import com.inventory.model.Product;
import com.inventory.model.StockEntry;
import com.inventory.model.Warehouse;
import com.inventory.service.StockManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads all seed data when the application starts up.
 *
 * @PostConstruct means "run this method automatically after Spring creates this object."
 * It reads warehouse JSON, product CSV, and inventory CSV to populate the StockManager.
 */
@Component
public class DataBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(DataBootstrapper.class);

    private final StockManager stockManager;
    private final ObjectMapper objectMapper;

    @Value("${app.demo-mode:true}")
    private boolean demoMode;

    // Track loaded data for health checks
    private boolean warehousesLoaded = false;
    private boolean productsLoaded = false;
    private boolean inventoryLoaded = false;
    private int warehouseCount = 0;
    private int productCount = 0;
    private int inventoryEntryCount = 0;
    private String loadSource = "DEMO";

    private final Map<String, Product> productCatalog = new ConcurrentHashMap<>();

    public DataBootstrapper(StockManager stockManager, ObjectMapper objectMapper) {
        this.stockManager = stockManager;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        log.info("════════════════════════════════════════════════════");
        log.info("  Inventory Management System — Data Bootstrap");
        log.info("  Demo Mode: {}", demoMode);
        log.info("════════════════════════════════════════════════════");

        try {
            loadWarehouses();
            loadProducts();
            loadInventory();
            validate();
            log.info("✅ Bootstrap complete: {} warehouses, {} products, {} inventory entries",
                    warehouseCount, productCount, inventoryEntryCount);
        } catch (Exception e) {
            log.error("❌ Bootstrap failed: {}", e.getMessage(), e);
            if (demoMode) {
                log.warn("Falling back to embedded demo data...");
                loadEmbeddedDemoData();
            } else {
                throw new DataValidationException("Bootstrap failed: " + e.getMessage());
            }
        }
    }

    private void loadWarehouses() throws Exception {
        // Try loading from config file first
        Path configPath = Paths.get("config/warehouses.json");
        if (Files.exists(configPath)) {
            String json = Files.readString(configPath);
            JsonNode root = objectMapper.readTree(json);
            JsonNode warehousesNode = root.get("warehouses");

            for (JsonNode wn : warehousesNode) {
                Warehouse wh = objectMapper.treeToValue(wn, Warehouse.class);
                stockManager.addWarehouse(wh);
                warehouseCount++;
            }
            loadSource = "FILE";
            warehousesLoaded = true;
            log.info("Loaded {} warehouses from {}", warehouseCount, configPath);
        } else {
            log.warn("Warehouse config not found at {}, using embedded data", configPath);
            loadEmbeddedWarehouses();
        }
    }

    private void loadProducts() throws Exception {
        Path csvPath = Paths.get("data/products.csv");
        if (Files.exists(csvPath)) {
            List<String> lines = Files.readAllLines(csvPath);
            // Skip header
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length >= 8) {
                    Product p = new Product(
                            parts[0].trim(),       // SKU
                            parts[1].trim(),       // ProductName
                            parts[2].trim(),       // Category
                            parseDouble(parts[3]), // WeightKg
                            parseDouble(parts[4]), // BasePrice
                            parseInt(parts[5]),    // ReorderPoint
                            parseInt(parts[6]),    // SafetyStock
                            Boolean.parseBoolean(parts[7].trim()) // Active
                    );
                    productCatalog.put(p.getSku(), p);
                    productCount++;
                }
            }
            productsLoaded = true;
            log.info("Loaded {} products from {}", productCount, csvPath);
        } else {
            log.warn("Products CSV not found at {}, using embedded data", csvPath);
            loadEmbeddedProducts();
        }
    }

    private void loadInventory() throws Exception {
        Path csvPath = Paths.get("data/inventory_snapshot.csv");
        if (Files.exists(csvPath)) {
            List<String> lines = Files.readAllLines(csvPath);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    stockManager.setInventory(
                            parts[0].trim(),      // Warehouse
                            parts[1].trim(),      // SKU
                            parseInt(parts[2]),   // Available
                            parseInt(parts[3]),   // Reserved
                            parseInt(parts[4])    // Defective
                    );
                    inventoryEntryCount++;
                }
            }
            inventoryLoaded = true;
            log.info("Loaded {} inventory entries from {}", inventoryEntryCount, csvPath);
        } else {
            log.warn("Inventory CSV not found at {}, using embedded data", csvPath);
            loadEmbeddedInventory();
        }
    }

    private void validate() {
        // Check no negative stock
        for (String whId : stockManager.getWarehouseIds()) {
            Map<String, StockEntry> inv = stockManager.getWarehouseInventory(whId);
            for (Map.Entry<String, StockEntry> entry : inv.entrySet()) {
                StockEntry se = entry.getValue();
                if (se.getAvailable() < 0 || se.getReserved() < 0 || se.getDefective() < 0) {
                    throw new DataValidationException(
                            whId + "/" + entry.getKey() + " has negative inventory: " + se);
                }
            }
        }
        log.info("Data validation passed ✓");
    }

    // ── Embedded Demo Data (fallback) ──

    private void loadEmbeddedDemoData() {
        loadEmbeddedWarehouses();
        loadEmbeddedProducts();
        loadEmbeddedInventory();
        log.info("✅ Loaded embedded demo data");
    }

    private void loadEmbeddedWarehouses() {
        stockManager.addWarehouse(new Warehouse("EAST_COAST", "New York Distribution Center",
                "Northeast USA", "123 Industrial Blvd, New York, NY", 40.7128, -74.006, 100000, "America/New_York"));
        stockManager.addWarehouse(new Warehouse("WEST_COAST", "Los Angeles Distribution Center",
                "West USA", "456 Commerce Way, Los Angeles, CA", 34.0522, -118.2437, 150000, "America/Los_Angeles"));
        stockManager.addWarehouse(new Warehouse("CENTRAL", "Chicago Central Hub",
                "Central USA", "789 Logistics Dr, Chicago, IL", 41.8781, -87.6298, 200000, "America/Chicago"));
        warehouseCount = 3;
        warehousesLoaded = true;
    }

    private void loadEmbeddedProducts() {
        addDemoProduct("SKU-001", "Laptop Pro 15", "Electronics", 1299.99);
        addDemoProduct("SKU-002", "USB-C Cable 1m", "Accessories", 19.99);
        addDemoProduct("SKU-003", "Wireless Mouse", "Accessories", 49.99);
        addDemoProduct("SKU-004", "Monitor 27 4K", "Electronics", 599.99);
        addDemoProduct("SKU-005", "Mechanical Keyboard", "Accessories", 149.99);
        productsLoaded = true;
    }

    private void addDemoProduct(String sku, String name, String category, double price) {
        productCatalog.put(sku, new Product(sku, name, category, price));
        productCount++;
    }

    private void loadEmbeddedInventory() {
        stockManager.setInventory("EAST_COAST", "SKU-001", 45, 3, 2);
        stockManager.setInventory("EAST_COAST", "SKU-002", 500, 20, 5);
        stockManager.setInventory("EAST_COAST", "SKU-003", 200, 15, 3);
        stockManager.setInventory("EAST_COAST", "SKU-004", 12, 1, 0);
        stockManager.setInventory("EAST_COAST", "SKU-005", 80, 5, 2);
        stockManager.setInventory("WEST_COAST", "SKU-001", 30, 0, 1);
        stockManager.setInventory("WEST_COAST", "SKU-002", 800, 50, 10);
        stockManager.setInventory("WEST_COAST", "SKU-003", 150, 10, 2);
        stockManager.setInventory("WEST_COAST", "SKU-004", 20, 2, 1);
        stockManager.setInventory("WEST_COAST", "SKU-005", 100, 8, 3);
        inventoryEntryCount = 10;
        inventoryLoaded = true;
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // ── Health check accessors ──
    public boolean isWarehousesLoaded() { return warehousesLoaded; }
    public boolean isProductsLoaded() { return productsLoaded; }
    public boolean isInventoryLoaded() { return inventoryLoaded; }
    public int getWarehouseCount() { return warehouseCount; }
    public int getProductCount() { return productCount; }
    public int getInventoryEntryCount() { return inventoryEntryCount; }
    public String getLoadSource() { return loadSource; }
    public Map<String, Product> getProductCatalog() { return productCatalog; }
}
