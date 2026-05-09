package com.inventory.model;

import com.inventory.model.enums.OrderStatus;
import com.inventory.model.enums.Tier;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a customer order.
 *
 * An order contains:
 *  - A map of items (SKU → quantity). Example: {"SKU-001": 2, "SKU-002": 5}
 *  - A preferred warehouse (where we try first)
 *  - A status that tracks the order lifecycle
 *  - Which warehouse ultimately fulfilled the order
 *
 * The orderId is auto-generated as a UUID (universally unique identifier).
 */
public class Order {

    private String orderId;                    // Auto-generated unique ID
    private Map<String, Integer> items;        // SKU → quantity
    private String preferredWarehouse;         // Which warehouse to try first
    private OrderStatus status;                // Current lifecycle state
    private String fulfilledBy;                // Which warehouse fulfilled this order
    private Instant createdAt;                 // When the order was created
    private Instant updatedAt;                 // When the order was last updated
    private String apiKey;                     // API key that submitted this order
    private Tier tier;                         // Tier of the submitting user
    private String failureReason;              // Why the order failed (if applicable)
    private List<String> triedWarehouses;      // Which warehouses we attempted

    // ---- Default constructor ----
    public Order() {
        this.orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.items = new HashMap<>();
    }

    // ---- Constructor for creating a new order ----
    public Order(Map<String, Integer> items, String preferredWarehouse) {
        this();  // Calls the default constructor above
        this.items = items != null ? items : new HashMap<>();
        this.preferredWarehouse = preferredWarehouse;
    }

    // ---- Getters and Setters ----
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Map<String, Integer> getItems() { return items; }
    public void setItems(Map<String, Integer> items) { this.items = items; }

    public String getPreferredWarehouse() { return preferredWarehouse; }
    public void setPreferredWarehouse(String preferredWarehouse) {
        this.preferredWarehouse = preferredWarehouse;
    }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getFulfilledBy() { return fulfilledBy; }
    public void setFulfilledBy(String fulfilledBy) { this.fulfilledBy = fulfilledBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Tier getTier() { return tier; }
    public void setTier(Tier tier) { this.tier = tier; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public List<String> getTriedWarehouses() { return triedWarehouses; }
    public void setTriedWarehouses(List<String> triedWarehouses) {
        this.triedWarehouses = triedWarehouses;
    }

    @Override
    public String toString() {
        return "Order{id='" + orderId + "', status=" + status +
               ", items=" + items.size() + ", fulfilledBy='" + fulfilledBy + "'}";
    }
}
