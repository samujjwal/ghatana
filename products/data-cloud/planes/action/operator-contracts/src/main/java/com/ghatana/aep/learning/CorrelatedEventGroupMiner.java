package com.ghatana.aep.learning;

import com.ghatana.aep.model.CanonicalEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic correlated event group miner for exploration and extraction.
 *
 * @doc.type class
 * @doc.purpose Mines correlation-id event groups as candidates for PatternSpec synthesis
 * @doc.layer product
 * @doc.pattern Service
 */
public final class CorrelatedEventGroupMiner {

    public List<CorrelatedEventGroup> mine(
            List<CanonicalEvent> events,
            Duration maxWindow,
            int minimumGroupSize) {
        if (maxWindow == null || maxWindow.isNegative() || maxWindow.isZero()) {
            throw new IllegalArgumentException("maxWindow must be positive");
        }
        if (minimumGroupSize < 2) {
            throw new IllegalArgumentException("minimumGroupSize must be at least 2");
        }
        List<CanonicalEvent> safeEvents = List.copyOf(events != null ? events : List.of());
        Map<String, List<CanonicalEvent>> byCorrelation = new LinkedHashMap<>();
        for (CanonicalEvent event : safeEvents) {
            byCorrelation.computeIfAbsent(event.correlationId(), ignored -> new ArrayList<>()).add(event);
        }

        List<CorrelatedEventGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<CanonicalEvent>> entry : byCorrelation.entrySet()) {
            List<CanonicalEvent> sorted = entry.getValue().stream()
                .sorted(Comparator.comparing(CanonicalEvent::eventTime))
                .toList();
            if (sorted.size() < minimumGroupSize) {
                continue;
            }
            Instant start = sorted.get(0).eventTime();
            Instant end = sorted.get(sorted.size() - 1).eventTime();
            if (Duration.between(start, end).compareTo(maxWindow) > 0) {
                continue;
            }
            double support = (double) sorted.size() / Math.max(1, safeEvents.size());
            double correlation = distinctEventTypeRatio(sorted);
            double reduction = 1.0 - ((double) byCorrelation.size() / Math.max(1, safeEvents.size()));
            groups.add(new CorrelatedEventGroup(
                sorted.get(0).tenantId() + ":" + entry.getKey(),
                sorted.get(0).tenantId(),
                entry.getKey(),
                start,
                end,
                sorted,
                support,
                correlation,
                Math.max(0.0, Math.min(1.0, reduction)),
                Map.of(
                    "eventTypes", sorted.stream().map(CanonicalEvent::eventType).distinct().toList(),
                    "windowMillis", Duration.between(start, end).toMillis())));
        }
        return groups;
    }

    private static double distinctEventTypeRatio(List<CanonicalEvent> events) {
        long distinctTypes = events.stream().map(CanonicalEvent::eventType).distinct().count();
        return (double) distinctTypes / Math.max(1, events.size());
    }
}
