package com.ghatana.core.event.query;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of an aggregation query.
 * Contains aggregated values grouped by the specified field.
 *
 * @param <T> The type of the aggregation result
 */
@Builder(setterPrefix = "with")
@Getter
public class AggregationResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String groupByField;
    private final AggregationFunction aggregationFunction;
    private final String aggregationField;
    private final List<Map<String, Object>> groups;
    private final double value;
    private final long totalCount;
    @Builder.Default long windowStart = 0L;
    @Builder.Default long windowEnd = 0L;
    private final Map<String, Object> metadata;

    public static <T> AggregationResult<T> withWindow(AggregationFunction aggType, double aggregatedValue, long windowStart, long windowEnd, String key) {
        return AggregationResult.<T>builder()
                .withAggregationFunction(aggType)
                .withAggregationField("value")
                .withGroups(Collections.singletonList(Collections.singletonMap("key", key)))
                .withValue(aggregatedValue)
                .withWindowStart(windowStart)
                .withWindowEnd(windowEnd)
                .build();
    }
}
