/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.spec;

import com.ghatana.aep.model.EventContext;
import com.ghatana.datacloud.event.model.Event;

/**
 * Pattern matcher interface for evaluating events against compiled patterns (P4-03).
 *
 * <p>P4-03: Defines the contract for matching events against a compiled pattern,
 * supporting both live evaluation and dry-run mode for replay scenarios.
 *
 * @doc.type interface
 * @doc.purpose Pattern matcher interface for evaluating events against compiled patterns
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface PatternMatcher {

    /**
     * Match an event context against the pattern.
     *
     * @param context the event context to match
     * @return match result with confidence and uncertainty
     */
    PatternMatchResult match(EventContext<?> context);

    /**
     * Perform a dry-run match without creating side effects.
     *
     * <p>P4-03: Used during replay to evaluate matches without creating
     * pattern instances or emitting events.
     *
     * @param event the event to match
     * @return match result without side effects
     */
    PatternMatchResult dryRunMatch(Event event);
}
