package com.ghatana.products.yappc.domain.task;

/**
 * SDLC Phase enumeration.
 *
 * @doc.type enum
 * @doc.purpose Categorize tasks by SDLC phase
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum SDLCPhase {
    DISCOVERY("discovery", "Discovery & Exploration"),
    DESIGN("design", "Planning & Design"),
    IMPLEMENTATION("implementation", "Code Generation & Implementation"),
    TESTING("testing", "Quality & Testing"),
    DEPLOYMENT("deployment", "Deployment & Release"),
    OPERATIONS("operations", "Monitoring & Maintenance");

    private final String id;
    private final String displayName;

    SDLCPhase(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SDLCPhase fromId(String id) {
        for (SDLCPhase phase : values()) {
            if (phase.id.equals(id)) {
                return phase;
            }
        }
        throw new IllegalArgumentException("Unknown phase ID: " + id);
    }
}
