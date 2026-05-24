package com.ghatana.aep.learning;

import com.ghatana.aep.model.CanonicalEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Candidate event group discovered during pattern exploration.
 *
 * @doc.type record
 * @doc.purpose Captures correlated event groups for PatternSpec extraction and synthesis
 * @doc.layer product
 * @doc.pattern Contract
 */
public record CorrelatedEventGroup(
        String groupId,
        String tenantId,
        String correlationId,
        Instant windowStart,
        Instant windowEnd,
        List<CanonicalEvent> events,
        double support,
        double correlation,
        double searchSpaceReductionRatio,
        Map<String, Object> metadata) {

    public CorrelatedEventGroup {
        groupId = requireText(groupId, "groupId");
        tenantId = requireText(tenantId, "tenantId");
        correlationId = requireText(correlationId, "correlationId");
        if (windowStart == null) {
            throw new IllegalArgumentException("windowStart must not be null");
        }
        if (windowEnd == null) {
            throw new IllegalArgumentException("windowEnd must not be null");
        }
        if (windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException("windowEnd must not be before windowStart");
        }
        events = List.copyOf(events != null ? events : List.of());
        requireProbability(support, "support");
        requireProbability(correlation, "correlation");
        requireProbability(searchSpaceReductionRatio, "searchSpaceReductionRatio");
        metadata = Map.copyOf(metadata != null ? metadata : Map.of());
    }

    private static void requireProbability(double value, String fieldName) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
