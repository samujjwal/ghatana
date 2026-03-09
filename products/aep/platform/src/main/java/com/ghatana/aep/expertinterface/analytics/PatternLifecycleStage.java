package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents the lifecycle stage of a detected pattern.
 *
 * <p>This class tracks where a pattern is in its lifecycle, enabling
 * analytics to understand pattern maturity and evolution.
 *
 * @doc.type class
 * @doc.purpose Tracks pattern lifecycle stage for analytics maturity assessment
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class PatternLifecycleStage {
    private String stage;
    public String getStage() { return stage; }
}
