package com.inventory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a physical warehouse / distribution center.
 *
 * In Java, a class is like a blueprint. Each warehouse in our system
 * is an "instance" (copy) of this blueprint with its own values.
 *
 * The fields below define what data every warehouse has.
 */
@JsonIgnoreProperties(ignoreUnknown = true)  // Ignore extra JSON fields we don't need
public class Warehouse {

    private String id;             // Unique identifier, e.g. "EAST_COAST"
    private String name;           // Human-readable name
    private String region;         // Geographic region
    private String address;        // Physical address
    private double latitude;       // GPS coordinates
    private double longitude;
    private int maxCapacity;       // Max number of items this warehouse can hold
    private String operatingHours; // e.g. "24/7"
    private String timezone;       // e.g. "America/New_York"
    private String contactEmail;   // Operations team email

    // ---- Default constructor (required by Jackson for JSON deserialization) ----
    public Warehouse() {}

    // ---- Constructor with essential fields ----
    public Warehouse(String id, String name, int maxCapacity) {
        this.id = id;
        this.name = name;
        this.maxCapacity = maxCapacity;
    }

    // ---- Full constructor ----
    public Warehouse(String id, String name, String region, String address,
                     double latitude, double longitude, int maxCapacity,
                     String timezone) {
        this.id = id;
        this.name = name;
        this.region = region;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.maxCapacity = maxCapacity;
        this.timezone = timezone;
    }

    // ---- Getters and Setters ----
    // In Java, fields are usually "private" (hidden), and we use
    // public getter/setter methods to access them. This is called "encapsulation."

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(int maxCapacity) { this.maxCapacity = maxCapacity; }

    public String getOperatingHours() { return operatingHours; }
    public void setOperatingHours(String operatingHours) { this.operatingHours = operatingHours; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    @Override
    public String toString() {
        return "Warehouse{id='" + id + "', name='" + name + "', capacity=" + maxCapacity + "}";
    }
}
