package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents a stage in the pattern lifecycle for analytics tracking.
 *
 * <p>LifecycleStage captures the current stage of a pattern within its
 * lifecycle (e.g., draft, review, active, deprecated) for analytics purposes.
 *
 * @doc.type class
 * @doc.purpose Represents a pattern lifecycle stage for analytics tracking
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class LifecycleStage {
    private String stage;
    public String getStage() { return stage; }
}
