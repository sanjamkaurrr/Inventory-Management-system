package com.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Inventory Management System.
 *
 * @SpringBootApplication — This single annotation does three things:
 *   1. @Configuration    — Marks this class as a source of bean definitions
 *   2. @EnableAutoConfiguration — Tells Spring Boot to auto-configure based on dependencies
 *   3. @ComponentScan    — Scans for @Service, @Controller, @Component in this package
 *
 * @EnableScheduling — Allows us to use @Scheduled for background jobs (like order cleanup)
 */
@SpringBootApplication
@EnableScheduling
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
