package com.ghatana.datacloud.analytics;

/**
 * Enumeration of supported analytics query types.
 *
 * @doc.type enum
 * @doc.purpose Classifies analytics queries for routing and plan generation
 * @doc.layer core
 */
public enum QueryType {
    SELECT,
    AGGREGATE,
    TIMESERIES,
    JOIN
}
