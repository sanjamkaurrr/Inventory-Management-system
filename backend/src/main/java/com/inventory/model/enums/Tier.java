package com.inventory.model.enums;

/**
 * Subscription tiers that control what a user can do.
 *
 * Each tier has different limits for:
 *  - How many warehouses they can use
 *  - How many API requests per day
 *  - How many orders per second (rate limiting)
 *  - How long order history is retained
 */
public enum Tier {

    FREE,
    PROFESSIONAL,
    ENTERPRISE
}
