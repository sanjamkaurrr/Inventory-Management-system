package com.inventory.model.enums;

/**
 * Represents the lifecycle of an order.
 *
 * An enum in Java is a special type with a fixed set of constants.
 * Think of it like a dropdown menu — these are the ONLY valid values.
 *
 * Lifecycle:
 *   PENDING → RESERVED → CONFIRMED (happy path)
 *   PENDING → FAILED (no stock available)
 *   RESERVED → CANCELLED (customer cancelled before payment)
 */
public enum OrderStatus {

    /** Order just created, not yet processed */
    PENDING,

    /** Stock has been reserved (held for this order, not available to others) */
    RESERVED,

    /** Payment confirmed, stock permanently deducted */
    CONFIRMED,

    /** Order failed — could not reserve stock at any warehouse */
    FAILED,

    /** Order cancelled after reservation — stock returned to available */
    CANCELLED
}
