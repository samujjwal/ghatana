package com.ghatana.platform.testing.chaos;

/**
 * Types of chaos that can be injected during testing.
 *
 * @doc.type enum
 * @doc.purpose Defines categories of failure injection for chaos testing
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum ChaosType {

    /**
     * Random selection of chaos types.
     */
    RANDOM("Random chaos injection"),

    /**
     * Network-related failures (latency, timeouts, connection refused).
     */
    NETWORK("Network failures - latency, timeouts, connection errors"),

    /**
     * Service unavailability (HTTP 503, connection refused).
     */
    SERVICE_UNAVAILABLE("Service unavailability simulation"),

    /**
     * Resource exhaustion (memory, CPU, file handles).
     */
    RESOURCE_EXHAUSTION("Resource exhaustion - memory, CPU, connections"),

    /**
     * Data corruption or invalid responses.
     */
    DATA_CORRUPTION("Data corruption - malformed responses, invalid data"),

    /**
     * Concurrent access violations (race conditions, deadlocks).
     */
    CONCURRENCY("Concurrency issues - race conditions, deadlocks"),

    /**
     * Slow responses (high latency without failure).
     */
    LATENCY("High latency responses"),

    /**
     * Partial failures (some operations succeed, some fail).
     */
    PARTIAL_FAILURE("Partial failures - intermittent errors"),

    /**
     * Clock drift or time-related issues.
     */
    CLOCK_DRIFT("Clock drift - time synchronization issues"),

    /**
     * Disk I/O failures.
     */
    DISK_FAILURE("Disk I/O failures - read/write errors");

    private final String description;

    ChaosType(String description) {
        this.description = description;
    }

    /**
     * Returns a human-readable description of the chaos type.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this chaos type affects network operations.
     *
     * @return true if network-related
     */
    public boolean isNetworkRelated() {
        return this == NETWORK || this == SERVICE_UNAVAILABLE || this == LATENCY;
    }

    /**
     * Checks if this chaos type affects data integrity.
     *
     * @return true if data-related
     */
    public boolean isDataRelated() {
        return this == DATA_CORRUPTION || this == PARTIAL_FAILURE;
    }

    /**
     * Checks if this chaos type affects system resources.
     *
     * @return true if resource-related
     */
    public boolean isResourceRelated() {
        return this == RESOURCE_EXHAUSTION || this == DISK_FAILURE;
    }
}
