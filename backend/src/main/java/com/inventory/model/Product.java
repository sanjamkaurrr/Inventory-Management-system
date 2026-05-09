package com.inventory.model;

/**
 * Represents a product in our catalog, identified by a unique SKU.
 *
 * SKU = "Stock Keeping Unit" — a unique code for each product.
 * Example: "SKU-001" = "Laptop Pro 15 inch"
 */
public class Product {

    private String sku;           // Unique product identifier
    private String productName;   // Human-readable name
    private String category;      // e.g. "Electronics", "Accessories"
    private double weightKg;      // Weight in kilograms
    private double basePrice;     // Selling price in USD
    private int reorderPoint;     // When stock drops below this → alert to restock
    private int safetyStock;      // Minimum stock to always keep on hand
    private boolean active;       // Is this product currently being sold?

    // ---- Default constructor ----
    public Product() {}

    // ---- Constructor with essential fields ----
    public Product(String sku, String productName, String category, double basePrice) {
        this.sku = sku;
        this.productName = productName;
        this.category = category;
        this.basePrice = basePrice;
        this.active = true;
    }

    // ---- Full constructor ----
    public Product(String sku, String productName, String category, double weightKg,
                   double basePrice, int reorderPoint, int safetyStock, boolean active) {
        this.sku = sku;
        this.productName = productName;
        this.category = category;
        this.weightKg = weightKg;
        this.basePrice = basePrice;
        this.reorderPoint = reorderPoint;
        this.safetyStock = safetyStock;
        this.active = active;
    }

    // ---- Getters and Setters ----
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }

    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }

    public int getReorderPoint() { return reorderPoint; }
    public void setReorderPoint(int reorderPoint) { this.reorderPoint = reorderPoint; }

    public int getSafetyStock() { return safetyStock; }
    public void setSafetyStock(int safetyStock) { this.safetyStock = safetyStock; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return "Product{sku='" + sku + "', name='" + productName + "', price=" + basePrice + "}";
    }
}
