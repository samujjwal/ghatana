package com.ghatana.platform.kernel.dependency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose Tracks and reports critical dependency availability
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class DependencyAvailability {
    private static final Logger LOG = LoggerFactory.getLogger(DependencyAvailability.class);

    private final String dependencyName;
    private final DependencyMode mode;
    private volatile boolean available;
    private volatile long lastCheckTime;
    private String lastErrorMessage;

    public DependencyAvailability(String dependencyName, DependencyMode mode, boolean initiallyAvailable) {
        this.dependencyName = dependencyName;
        this.mode = mode;
        this.available = initiallyAvailable;
        this.lastCheckTime = System.currentTimeMillis();
    }

    /**
     * Check if dependency is available and handle based on mode.
     * In STRICT mode, throws exception if unavailable for write operations.
     * In DEGRADED mode, logs warning but allows operation to proceed.
     */
    public void verifyAvailable(String operationType, boolean isWriteOperation) {
        if (available) {
            return;
        }

        String message = String.format(
            "Dependency %s is not available for %s operation (mode: %s)",
            dependencyName, operationType, mode.name()
        );

        if (mode.isStrict() && isWriteOperation) {
            LOG.error("STRICT MODE: {}", message);
            throw new IllegalStateException("Critical dependency unavailable: " + dependencyName + 
                                          " (write operation not allowed in strict mode)");
        }

        if (mode.isDegraded()) {
            LOG.warn("DEGRADED MODE: {} - Operation may complete with fallback behavior", message);
            this.lastErrorMessage = message;
        }
    }

    /**
     * Mark dependency as available/unavailable.
     */
    public void setAvailable(boolean available) {
        if (this.available != available) {
            this.available = available;
            this.lastCheckTime = System.currentTimeMillis();
            
            String signal = available ? "RESTORED" : "DEGRADED";
            LOG.warn("{} MODE SIGNAL: Dependency {} is now {}", 
                mode.name(), dependencyName, signal);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String getDependencyName() {
        return dependencyName;
    }

    public DependencyMode getMode() {
        return mode;
    }

    public long getLastCheckTime() {
        return lastCheckTime;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    @Override
    public String toString() {
        return String.format(
            "DependencyAvailability{name=%s, available=%s, mode=%s}",
            dependencyName, available, mode.name()
        );
    }
}
