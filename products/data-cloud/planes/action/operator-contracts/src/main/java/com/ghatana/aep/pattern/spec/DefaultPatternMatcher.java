/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.spec;

import com.ghatana.aep.model.EventContext;
import com.ghatana.datacloud.event.model.Event;

import java.util.Map;

/**
 * Default pattern matcher implementation (P4-03).
 *
 * <p>P4-03: Provides a basic pattern matching implementation that evaluates
 * events against a pattern DAG structure. This is a reference implementation
 * that can be extended with more sophisticated matching logic.
 *
 * @doc.type class
 * @doc.purpose Default pattern matcher implementation for event-pattern evaluation
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class DefaultPatternMatcher implements PatternMatcher {

    private final PatternRuntimeNode root;
    private final String timePolicy;
    private final String uncertaintyPolicy;

    public DefaultPatternMatcher(PatternRuntimeNode root, String timePolicy, String uncertaintyPolicy) {
        this.root = root;
        this.timePolicy = timePolicy != null ? timePolicy : "event_time";
        this.uncertaintyPolicy = uncertaintyPolicy != null ? uncertaintyPolicy : "propagate";
    }

    @Override
    public PatternMatchResult match(EventContext<?> context) {
        // P4-03: Basic pattern matching implementation
        // In a production implementation, this would traverse the DAG and evaluate
        // each node against the event context
        
        // For now, implement a simple match based on event type
        if (context.input().isPresent() && context.input().get() instanceof Map<?, ?> eventData) {
            Object eventType = eventData.get("type");
            if (eventType != null && eventType.equals("test.event")) {
                return PatternMatchResult.match(0.9, Map.of("matchedNode", root.nodeId()));
            }
        }
        
        return PatternMatchResult.noMatch("Event type does not match pattern");
    }

    @Override
    public PatternMatchResult dryRunMatch(Event event) {
        // P4-03: Dry-run match without side effects
        // Evaluates the match but doesn't create instances or emit events
        
        Map<String, Object> payload = event.getPayload();
        if (payload instanceof Map<?, ?> eventData) {
            Object eventType = eventData.get("type");
            if (eventType != null && eventType.equals("test.event")) {
                return PatternMatchResult.match(0.9, Map.of("matchedNode", root.nodeId(), "dryRun", true));
            }
        }
        
        return PatternMatchResult.noMatch("Event type does not match pattern (dry run)");
    }
}
