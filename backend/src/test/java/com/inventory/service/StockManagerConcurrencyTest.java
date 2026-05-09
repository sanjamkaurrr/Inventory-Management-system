package com.inventory.service;

import com.inventory.model.Order;
import com.inventory.model.StockEntry;
import com.inventory.model.Warehouse;
import com.inventory.model.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * THE BIG TEST — Proves ZERO overselling under extreme concurrency.
 *
 * This test launches 200 threads simultaneously, all trying to buy
 * from a pool of 100 items. Exactly 100 should succeed, 100 should fail,
 * and available stock must NEVER go negative.
 */
class StockManagerConcurrencyTest {

    private StockManager stockManager;

    @BeforeEach
    void setUp() {
        stockManager = new StockManager();
        stockManager.addWarehouse(new Warehouse("TEST_WH", "Test Warehouse", 10000));
    }

    @Test
    @DisplayName("200 threads competing for 100 units → exactly 100 succeed, 0 overselling")
    void testZeroOverselling() throws Exception {
        // Setup: 100 units of SKU-001 available
        stockManager.setInventory("TEST_WH", "SKU-001", 100, 0, 0);

        int threadCount = 200;
        CountDownLatch startGun = new CountDownLatch(1);  // All threads wait for this
        CountDownLatch finish = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Launch all threads
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGun.await();  // Wait for the starting gun
                    Order order = new Order(Map.of("SKU-001", 1), "TEST_WH");
                    Order result = stockManager.processOrder(order);
                    if (result.getStatus() == OrderStatus.RESERVED) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    finish.countDown();
                }
            });
        }

        // FIRE! All 200 threads start at the same instant
        startGun.countDown();
        finish.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify results
        StockEntry stock = stockManager.getStockEntry("TEST_WH", "SKU-001");

        System.out.println("═══════════════════════════════════════");
        System.out.println("  CONCURRENCY TEST RESULTS");
        System.out.println("═══════════════════════════════════════");
        System.out.println("  Threads launched:  " + threadCount);
        System.out.println("  Successes:         " + successes.get());
        System.out.println("  Failures:          " + failures.get());
        System.out.println("  Available stock:   " + stock.getAvailable());
        System.out.println("  Reserved stock:    " + stock.getReserved());
        System.out.println("═══════════════════════════════════════");

        // ASSERTIONS — these must ALL pass
        assertEquals(100, successes.get(), "Exactly 100 orders should succeed");
        assertEquals(100, failures.get(), "Exactly 100 orders should fail");
        assertEquals(0, stock.getAvailable(), "Available stock must be exactly 0");
        assertEquals(100, stock.getReserved(), "Reserved stock must be exactly 100");
        assertTrue(stock.getAvailable() >= 0, "Available stock must NEVER be negative");
    }

    @Test
    @DisplayName("Multi-SKU atomic reservation: partial failure rolls back everything")
    void testAtomicMultiSkuReservation() throws Exception {
        stockManager.setInventory("TEST_WH", "SKU-001", 10, 0, 0);
        stockManager.setInventory("TEST_WH", "SKU-002", 2, 0, 0);  // Only 2 available

        // Try to order 5 of each — should fail because SKU-002 only has 2
        Order order = new Order(Map.of("SKU-001", 5, "SKU-002", 5), "TEST_WH");
        Order result = stockManager.processOrder(order);

        assertEquals(OrderStatus.FAILED, result.getStatus());

        // Verify NO stock was deducted (atomic rollback)
        assertEquals(10, stockManager.getAvailableStock("TEST_WH", "SKU-001"));
        assertEquals(2, stockManager.getAvailableStock("TEST_WH", "SKU-002"));
    }

    @Test
    @DisplayName("Warehouse fallback: if preferred is empty, tries secondary")
    void testWarehouseFallback() {
        stockManager.addWarehouse(new Warehouse("WH_EMPTY", "Empty Warehouse", 1000));
        stockManager.setInventory("TEST_WH", "SKU-001", 50, 0, 0);
        stockManager.setInventory("WH_EMPTY", "SKU-001", 0, 0, 0);

        Order order = new Order(Map.of("SKU-001", 5), "WH_EMPTY");
        Order result = stockManager.processOrder(order);

        assertEquals(OrderStatus.RESERVED, result.getStatus());
        assertEquals("TEST_WH", result.getFulfilledBy()); // Fell back to TEST_WH
    }

    @Test
    @DisplayName("Basic reserve and confirm lifecycle")
    void testReserveAndConfirm() {
        stockManager.setInventory("TEST_WH", "SKU-001", 50, 0, 0);

        Order order = new Order(Map.of("SKU-001", 10), "TEST_WH");
        Order result = stockManager.processOrder(order);

        assertEquals(OrderStatus.RESERVED, result.getStatus());
        assertEquals(40, stockManager.getAvailableStock("TEST_WH", "SKU-001"));

        StockEntry entry = stockManager.getStockEntry("TEST_WH", "SKU-001");
        assertEquals(10, entry.getReserved());

        // Confirm the order
        stockManager.confirmStock("TEST_WH", order.getItems());
        entry = stockManager.getStockEntry("TEST_WH", "SKU-001");
        assertEquals(40, entry.getAvailable());
        assertEquals(0, entry.getReserved());
    }

    @Test
    @DisplayName("Cancel reservation returns stock to available")
    void testCancelReservation() {
        stockManager.setInventory("TEST_WH", "SKU-001", 50, 0, 0);

        Order order = new Order(Map.of("SKU-001", 10), "TEST_WH");
        stockManager.processOrder(order);

        assertEquals(40, stockManager.getAvailableStock("TEST_WH", "SKU-001"));

        // Cancel — stock should return
        stockManager.releaseReservedStock("TEST_WH", order.getItems());
        assertEquals(50, stockManager.getAvailableStock("TEST_WH", "SKU-001"));
    }
}
