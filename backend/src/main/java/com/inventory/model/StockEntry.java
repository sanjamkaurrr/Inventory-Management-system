package com.inventory.model;

/**
 * Tracks inventory levels for ONE SKU at ONE warehouse.
 *
 * Think of it as a row in a spreadsheet:
 *   Warehouse: EAST_COAST | SKU: SKU-001 | Available: 45 | Reserved: 3 | Defective: 2
 *
 * KEY CONCEPT: The three states of inventory
 * ───────────────────────────────────────────
 *   AVAILABLE  → Can be sold right now
 *   RESERVED   → Held for a pending order (waiting for payment)
 *   DEFECTIVE  → Damaged/returned, cannot be sold
 *
 * When an order comes in:
 *   1. available -= quantity  (take from shelf)
 *   2. reserved  += quantity  (put on hold)
 *
 * When payment confirms:
 *   3. reserved  -= quantity  (item ships — gone from warehouse)
 *
 * When order is cancelled:
 *   3. reserved  -= quantity  (return to shelf)
 *   4. available += quantity
 */
public class StockEntry {

    private int available;       // Ready to sell
    private int reserved;        // Held for pending orders
    private int defective;       // Damaged, write-off inventory
    private long lastUpdated;    // Timestamp of last change (epoch millis)

    // ---- Default constructor ----
    public StockEntry() {
        this.lastUpdated = System.currentTimeMillis();
    }

    // ---- Constructor with initial quantities ----
    public StockEntry(int available, int reserved, int defective) {
        this.available = available;
        this.reserved = reserved;
        this.defective = defective;
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Total physical inventory = available + reserved + defective
     * This is how many items are physically in the warehouse.
     */
    public int getTotalPhysical() {
        return available + reserved + defective;
    }

    // ---- Getters and Setters ----
    public int getAvailable() { return available; }
    public void setAvailable(int available) {
        this.available = available;
        this.lastUpdated = System.currentTimeMillis();
    }

    public int getReserved() { return reserved; }
    public void setReserved(int reserved) {
        this.reserved = reserved;
        this.lastUpdated = System.currentTimeMillis();
    }

    public int getDefective() { return defective; }
    public void setDefective(int defective) {
        this.defective = defective;
        this.lastUpdated = System.currentTimeMillis();
    }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    @Override
    public String toString() {
        return "StockEntry{available=" + available + ", reserved=" + reserved +
               ", defective=" + defective + "}";
    }
}
