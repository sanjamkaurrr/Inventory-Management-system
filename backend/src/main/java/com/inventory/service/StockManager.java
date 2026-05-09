package com.inventory.service;

import com.inventory.exception.InsufficientStockException;
import com.inventory.model.Order;
import com.inventory.model.StockEntry;
import com.inventory.model.Warehouse;
import com.inventory.model.enums.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║               THE HEART OF THE SYSTEM                       ║
 * ║     Thread-Safe Stock Management — ZERO Overselling          ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * This class is the most critical piece of the entire application.
 * It ensures that even if 1000 orders arrive at the SAME instant,
 * no product is ever sold more times than it exists in inventory.
 *
 * HOW IT WORKS:
 * ─────────────
 * The keyword "synchronized" on a method means:
 *   "Only ONE thread can execute this method at a time."
 *
 * Imagine a bathroom with a lock:
 *   Thread A enters → locks door → does its work → unlocks → leaves
 *   Thread B was waiting → enters → locks door → does its work → unlocks
 *   Thread C was waiting → enters → ...
 *
 * This prevents the classic race condition:
 *   Thread A: "I see 5 items" → Thread B: "I also see 5 items"
 *   Thread A: reserves 5 → Thread B: reserves 5 → OVERSOLD!
 *
 * With synchronized:
 *   Thread A: LOCKS → sees 5 → reserves 5 → available=0 → UNLOCKS
 *   Thread B: LOCKS → sees 0 → REJECTS → UNLOCKS ✅
 *
 * @Service — Tells Spring: "Create ONE instance of this class and share it everywhere"
 */
@Service
public class StockManager {

    private static final Logger log = LoggerFactory.getLogger(StockManager.class);

    /**
     * The main inventory data structure.
     *
     * Structure: warehouseId → (sku → StockEntry)
     * Example:   "EAST_COAST" → { "SKU-001" → StockEntry{available=45, reserved=3} }
     *
     * ConcurrentHashMap is like a regular HashMap but thread-safe for reads.
     * We still use synchronized for writes to guarantee atomicity across multiple SKUs.
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, StockEntry>> inventory
            = new ConcurrentHashMap<>();

    /** All registered warehouses */
    private final ConcurrentHashMap<String, Warehouse> warehouses = new ConcurrentHashMap<>();

    /** Warehouse fallback priority (ordered list) */
    private final List<String> warehousePriority = new ArrayList<>();

    /** Counters for monitoring */
    private final AtomicLong totalOrdersProcessed = new AtomicLong(0);
    private final AtomicLong totalOrdersSucceeded = new AtomicLong(0);
    private final AtomicLong totalOrdersFailed = new AtomicLong(0);
    private final AtomicLong totalItemsReserved = new AtomicLong(0);
    private final AtomicLong totalItemsConfirmed = new AtomicLong(0);

    // ═══════════════════════════════════════════════════════════
    //  SETUP METHODS — Called during application startup
    // ═══════════════════════════════════════════════════════════

    /** Register a warehouse in the system */
    public void addWarehouse(Warehouse warehouse) {
        warehouses.put(warehouse.getId(), warehouse);
        inventory.putIfAbsent(warehouse.getId(), new ConcurrentHashMap<>());
        if (!warehousePriority.contains(warehouse.getId())) {
            warehousePriority.add(warehouse.getId());
        }
        log.info("Registered warehouse: {}", warehouse.getId());
    }

    /** Set initial inventory for a specific SKU at a specific warehouse */
    public void setInventory(String warehouseId, String sku,
                              int available, int reserved, int defective) {
        inventory.computeIfAbsent(warehouseId, k -> new ConcurrentHashMap<>())
                 .put(sku, new StockEntry(available, reserved, defective));
    }

    // ═══════════════════════════════════════════════════════════
    //  CORE OPERATIONS — All synchronized for thread safety
    // ═══════════════════════════════════════════════════════════

    /**
     * PROCESS ORDER — The main entry point for order fulfillment.
     *
     * This method:
     *  1. Determines which warehouses to try (preferred first, then fallbacks)
     *  2. Attempts to atomically reserve ALL items in the order
     *  3. Returns the updated order with status RESERVED or FAILED
     *
     * @param order The incoming order with items and preferred warehouse
     * @return The order updated with status and fulfillment details
     */
    public Order processOrder(Order order) {
        long orderNum = totalOrdersProcessed.incrementAndGet();

        log.info("┌─── ORDER #{} ─── {} ───────────────────────", orderNum, order.getOrderId());
        log.info("│ [STEP 1/5] New order received");
        log.info("│   Items: {}", order.getItems());
        log.info("│   Preferred warehouse: {}", order.getPreferredWarehouse() != null ? order.getPreferredWarehouse() : "AUTO");

        // Build the warehouse priority list: preferred first, then others
        List<String> warehouseChain = buildWarehouseChain(order.getPreferredWarehouse());
        order.setTriedWarehouses(new ArrayList<>());

        log.info("│ [STEP 2/5] Warehouse chain built: {}", warehouseChain);

        // Try each warehouse in order
        int attempt = 0;
        for (String warehouseId : warehouseChain) {
            attempt++;
            order.getTriedWarehouses().add(warehouseId);

            log.info("│ [STEP 3/5] Attempt {}/{} — trying warehouse: {}", attempt, warehouseChain.size(), warehouseId);

            try {
                reserveStock(warehouseId, order.getItems());

                // SUCCESS! Stock reserved at this warehouse
                order.setStatus(OrderStatus.RESERVED);
                order.setFulfilledBy(warehouseId);
                totalOrdersSucceeded.incrementAndGet();

                int totalItems = order.getItems().values().stream()
                        .mapToInt(Integer::intValue).sum();
                totalItemsReserved.addAndGet(totalItems);

                log.info("│ [STEP 4/5] ✅ RESERVATION SUCCESSFUL at {}", warehouseId);
                log.info("│   {} items reserved, status → RESERVED", totalItems);
                log.info("│ [STEP 5/5] Order ready for payment confirmation");
                log.info("└─── ORDER {} COMPLETE ─── fulfilled by {} ───", order.getOrderId(), warehouseId);
                return order;

            } catch (InsufficientStockException e) {
                log.info("│   ❌ Failed at {}: {}", warehouseId, e.getMessage());
                log.info("│   Trying next warehouse in chain...");
            }
        }

        // ALL warehouses failed
        order.setStatus(OrderStatus.FAILED);
        order.setFailureReason("Insufficient stock at all warehouses: " + warehouseChain);
        totalOrdersFailed.incrementAndGet();
        log.warn("│ [STEP 4/5] ❌ ALL {} warehouses exhausted — order FAILED", warehouseChain.size());
        log.warn("└─── ORDER {} FAILED ─── tried {} ───", order.getOrderId(), warehouseChain);
        return order;
    }

    /**
     * RESERVE STOCK — Atomic check-and-reserve for ALL items.
     *
     * ⚠️ THIS IS THE CRITICAL SYNCHRONIZED METHOD ⚠️
     *
     * The "synchronized" keyword ensures:
     *  - Only ONE thread can execute this method at a time
     *  - If any SKU fails, ALL previously reserved SKUs are rolled back
     *  - The inventory is NEVER left in an inconsistent state
     *
     * This is called "atomicity" — it either ALL succeeds or ALL fails.
     *
     * @param warehouseId The warehouse to reserve from
     * @param items       Map of SKU → quantity to reserve
     * @throws InsufficientStockException if any SKU has insufficient stock
     */
    public synchronized void reserveStock(String warehouseId, Map<String, Integer> items) {
        log.debug("│   🔒 LOCK acquired — entering synchronized reserveStock()");
        log.debug("│   Thread '{}' has exclusive access now", Thread.currentThread().getName());

        ConcurrentHashMap<String, StockEntry> warehouseStock = inventory.get(warehouseId);
        if (warehouseStock == null) {
            log.debug("│   Warehouse {} not found in inventory map", warehouseId);
            throw new InsufficientStockException("N/A", warehouseId, 0, 0);
        }

        // Track what we've reserved so far (for rollback if something fails)
        Map<String, Integer> reservedSoFar = new HashMap<>();

        try {
            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                String sku = entry.getKey();
                int quantity = entry.getValue();

                StockEntry stock = warehouseStock.get(sku);
                int avail = (stock != null) ? stock.getAvailable() : 0;

                log.debug("│   Checking {} at {}: need={}, available={}", sku, warehouseId, quantity, avail);

                if (stock == null || avail < quantity) {
                    log.debug("│   ⚠️ INSUFFICIENT — {} has only {} but need {} → triggering ROLLBACK", sku, avail, quantity);
                    throw new InsufficientStockException(sku, warehouseId, quantity, avail);
                }

                // Reserve: move from available to reserved
                stock.setAvailable(stock.getAvailable() - quantity);
                stock.setReserved(stock.getReserved() + quantity);
                reservedSoFar.put(sku, quantity);
                log.debug("│   ✓ Reserved {} × {} → available: {} → {}, reserved: {} → {}",
                        quantity, sku, avail, stock.getAvailable(),
                        stock.getReserved() - quantity, stock.getReserved());
            }
            log.debug("│   🔓 LOCK released — all {} SKUs reserved successfully", items.size());
        } catch (InsufficientStockException e) {
            // ROLLBACK: undo everything we've reserved so far
            if (!reservedSoFar.isEmpty()) {
                log.debug("│   🔄 ROLLBACK — undoing {} SKUs that were already reserved", reservedSoFar.size());
            }
            rollbackReservations(warehouseId, reservedSoFar);
            log.debug("│   🔓 LOCK released — reservation failed, state fully restored");
            throw e;  // Re-throw so the caller knows it failed
        }
    }

    /**
     * CONFIRM ORDER — Move reserved stock to "sold" (permanently deducted).
     * Called after payment is confirmed.
     *
     * @param warehouseId The warehouse that holds the reservation
     * @param items       Map of SKU → quantity to confirm
     */
    public synchronized void confirmStock(String warehouseId, Map<String, Integer> items) {
        log.info("┌─── CONFIRM STOCK ───────────────────────────");
        log.info("│ Warehouse: {}, Items: {}", warehouseId, items);

        ConcurrentHashMap<String, StockEntry> warehouseStock = inventory.get(warehouseId);
        if (warehouseStock == null) {
            log.warn("│ Warehouse {} not found — skipping confirmation", warehouseId);
            return;
        }

        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            StockEntry stock = warehouseStock.get(entry.getKey());
            if (stock != null) {
                int oldReserved = stock.getReserved();
                stock.setReserved(oldReserved - entry.getValue());
                totalItemsConfirmed.addAndGet(entry.getValue());
                log.info("│ ✅ {} confirmed: reserved {} → {} (shipped out)",
                        entry.getKey(), oldReserved, stock.getReserved());
            }
        }
        log.info("└─── CONFIRMATION COMPLETE ──────────────────");
    }

    /**
     * CANCEL RESERVATION — Return reserved stock back to available.
     * Called when an order is cancelled.
     *
     * @param warehouseId The warehouse that holds the reservation
     * @param items       Map of SKU → quantity to release
     */
    public synchronized void releaseReservedStock(String warehouseId, Map<String, Integer> items) {
        log.info("┌─── CANCEL / RELEASE STOCK ─────────────────");
        log.info("│ Warehouse: {}, Items: {}", warehouseId, items);
        rollbackReservations(warehouseId, items);
        log.info("└─── STOCK RELEASED BACK TO AVAILABLE ───────");
    }

    // ═══════════════════════════════════════════════════════════
    //  READ OPERATIONS — Non-blocking queries
    // ═══════════════════════════════════════════════════════════

    /** Get available stock for a specific SKU at a specific warehouse */
    public int getAvailableStock(String warehouseId, String sku) {
        ConcurrentHashMap<String, StockEntry> warehouseStock = inventory.get(warehouseId);
        if (warehouseStock == null) return 0;
        StockEntry entry = warehouseStock.get(sku);
        return (entry != null) ? entry.getAvailable() : 0;
    }

    /** Get the full StockEntry for a SKU at a warehouse */
    public StockEntry getStockEntry(String warehouseId, String sku) {
        ConcurrentHashMap<String, StockEntry> warehouseStock = inventory.get(warehouseId);
        if (warehouseStock == null) return null;
        return warehouseStock.get(sku);
    }

    /** Get all inventory for a specific warehouse */
    public Map<String, StockEntry> getWarehouseInventory(String warehouseId) {
        ConcurrentHashMap<String, StockEntry> warehouseStock = inventory.get(warehouseId);
        return warehouseStock != null ? new HashMap<>(warehouseStock) : new HashMap<>();
    }

    /** Get complete inventory snapshot across all warehouses */
    public Map<String, Map<String, StockEntry>> getFullInventory() {
        Map<String, Map<String, StockEntry>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, ConcurrentHashMap<String, StockEntry>> entry : inventory.entrySet()) {
            snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return snapshot;
    }

    /** Get all registered warehouses */
    public List<Warehouse> getWarehouses() {
        return new ArrayList<>(warehouses.values());
    }

    /** Get a specific warehouse */
    public Warehouse getWarehouse(String warehouseId) {
        return warehouses.get(warehouseId);
    }

    /** Get list of all warehouse IDs */
    public List<String> getWarehouseIds() {
        return new ArrayList<>(warehousePriority);
    }

    /** Get all SKU IDs across all warehouses */
    public Set<String> getAllSkus() {
        Set<String> allSkus = new TreeSet<>();
        for (ConcurrentHashMap<String, StockEntry> warehouseStock : inventory.values()) {
            allSkus.addAll(warehouseStock.keySet());
        }
        return allSkus;
    }

    /** Get count of SKUs in a specific warehouse */
    public int getSkuCountInWarehouse(String warehouseId) {
        ConcurrentHashMap<String, StockEntry> warehouseStock = inventory.get(warehouseId);
        return warehouseStock != null ? warehouseStock.size() : 0;
    }

    /** Get total available stock across ALL warehouses for a SKU */
    public int getGlobalAvailableStock(String sku) {
        int total = 0;
        for (ConcurrentHashMap<String, StockEntry> warehouseStock : inventory.values()) {
            StockEntry entry = warehouseStock.get(sku);
            if (entry != null) {
                total += entry.getAvailable();
            }
        }
        return total;
    }

    // ═══════════════════════════════════════════════════════════
    //  MONITORING & STATS
    // ═══════════════════════════════════════════════════════════

    public long getTotalOrdersProcessed() { return totalOrdersProcessed.get(); }
    public long getTotalOrdersSucceeded() { return totalOrdersSucceeded.get(); }
    public long getTotalOrdersFailed() { return totalOrdersFailed.get(); }
    public long getTotalItemsReserved() { return totalItemsReserved.get(); }
    public long getTotalItemsConfirmed() { return totalItemsConfirmed.get(); }

    /** Get a summary of all warehouse utilization */
    public Map<String, Map<String, Object>> getWarehouseUtilization() {
        Map<String, Map<String, Object>> utilization = new LinkedHashMap<>();
        for (Map.Entry<String, Warehouse> entry : warehouses.entrySet()) {
            String whId = entry.getKey();
            Warehouse wh = entry.getValue();
            ConcurrentHashMap<String, StockEntry> stock = inventory.get(whId);

            int totalAvailable = 0;
            int totalReserved = 0;
            int totalDefective = 0;
            int skuCount = 0;

            if (stock != null) {
                for (StockEntry se : stock.values()) {
                    totalAvailable += se.getAvailable();
                    totalReserved += se.getReserved();
                    totalDefective += se.getDefective();
                    skuCount++;
                }
            }

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("name", wh.getName());
            stats.put("maxCapacity", wh.getMaxCapacity());
            stats.put("totalAvailable", totalAvailable);
            stats.put("totalReserved", totalReserved);
            stats.put("totalDefective", totalDefective);
            stats.put("totalPhysical", totalAvailable + totalReserved + totalDefective);
            stats.put("skuCount", skuCount);
            stats.put("utilizationPercent",
                    wh.getMaxCapacity() > 0
                            ? Math.round(((double)(totalAvailable + totalReserved + totalDefective)
                            / wh.getMaxCapacity()) * 10000.0) / 100.0
                            : 0);
            utilization.put(whId, stats);
        }
        return utilization;
    }

    // ═══════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Build an ordered list of warehouses to try.
     * The preferred warehouse goes first, then the rest in priority order.
     */
    private List<String> buildWarehouseChain(String preferredWarehouse) {
        List<String> chain = new ArrayList<>();

        // Add preferred warehouse first (if it exists)
        if (preferredWarehouse != null && warehouses.containsKey(preferredWarehouse)) {
            chain.add(preferredWarehouse);
        }

        // Add remaining warehouses in priority order
        for (String wh : warehousePriority) {
            if (!chain.contains(wh)) {
                chain.add(wh);
            }
        }

        return chain;
    }

    /**
     * Rollback (undo) reservations — moves stock from reserved back to available.
     * This is called when a multi-SKU reservation partially fails.
     */
    private void rollbackReservations(String warehouseId, Map<String, Integer> reservedItems) {
        ConcurrentHashMap<String, StockEntry> warehouseStock = inventory.get(warehouseId);
        if (warehouseStock == null) return;

        for (Map.Entry<String, Integer> entry : reservedItems.entrySet()) {
            StockEntry stock = warehouseStock.get(entry.getKey());
            if (stock != null) {
                int oldAvail = stock.getAvailable();
                int oldRes = stock.getReserved();
                stock.setAvailable(oldAvail + entry.getValue());
                stock.setReserved(oldRes - entry.getValue());
                log.info("│ 🔄 {} rolled back: available {} → {}, reserved {} → {}",
                        entry.getKey(), oldAvail, stock.getAvailable(), oldRes, stock.getReserved());
            }
        }
    }
}
