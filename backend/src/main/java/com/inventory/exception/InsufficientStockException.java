package com.inventory.exception;

/**
 * Thrown when there is not enough stock to fulfill an order.
 *
 * This is a "checked exception" — the compiler forces you to handle it
 * (either catch it or declare it in the method signature with "throws").
 *
 * We extend RuntimeException instead so it's "unchecked" — cleaner code,
 * and our GlobalExceptionHandler catches it automatically.
 */
public class InsufficientStockException extends RuntimeException {

    private final String sku;
    private final String warehouseId;
    private final int requested;
    private final int available;

    public InsufficientStockException(String sku, String warehouseId,
                                       int requested, int available) {
        super(String.format(
            "Insufficient stock for %s at %s (requested: %d, available: %d)",
            sku, warehouseId, requested, available
        ));
        this.sku = sku;
        this.warehouseId = warehouseId;
        this.requested = requested;
        this.available = available;
    }

    public String getSku() { return sku; }
    public String getWarehouseId() { return warehouseId; }
    public int getRequested() { return requested; }
    public int getAvailable() { return available; }
}
