package com.ghatana.aep.domain.agent;

/**
 * Canonical Task priority levels used across the platform.
 */
public enum TaskPriority {
    CRITICAL(4, "Critical - immediate attention required"),
    HIGH(3, "High - complete within 24h"),
    MEDIUM(2, "Medium - complete within week"),
    LOW(1, "Low - complete when capacity available");

    private final int level;
    private final String description;

    TaskPriority(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }
}
