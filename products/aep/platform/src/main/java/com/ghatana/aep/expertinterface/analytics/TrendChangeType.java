package com.ghatana.aep.expertinterface.analytics;

/**
 * Defines types of trend changes in analytics data.
 *
 * <p>This class provides constants representing different trend change
 * directions (INCREASE, DECREASE) for categorizing trend movements.
 *
 * @doc.type class
 * @doc.purpose Defines trend change type constants for analytics classification
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class TrendChangeType {
    public static final TrendChangeType INCREASE = new TrendChangeType();
    public static final TrendChangeType DECREASE = new TrendChangeType();
}
