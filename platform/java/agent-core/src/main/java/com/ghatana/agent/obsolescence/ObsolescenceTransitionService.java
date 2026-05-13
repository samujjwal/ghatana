/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.mastery.MasteryTransitionResult;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Service for handling obsolescence events and transitioning mastery items.
 *
 * <p>Accepts obsolescence events, validates them, creates mastery transitions,
 * and routes them through the mastery registry.
 *
 * @doc.type interface
 * @doc.purpose Service for handling obsolescence events and transitions
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public interface ObsolescenceTransitionService {

    /**
     * Processes an obsolescence event by validating it and creating the appropriate mastery transition.
     *
     * @param event obsolescence event to process
     * @return promise of transition result
     */
    @NotNull
    Promise<MasteryTransitionResult> processObsolescenceEvent(@NotNull ObsolescenceEvent event);

    /**
     * Validates an obsolescence event before processing.
     *
     * @param event obsolescence event to validate
     * @return true if valid, false otherwise
     */
    boolean validateObsolescenceEvent(@NotNull ObsolescenceEvent event);

    /**
     * Creates a mastery transition from an obsolescence event.
     *
     * @param event obsolescence event to convert
     * @return mastery transition
     */
    @NotNull
    com.ghatana.agent.mastery.MasteryTransition createTransitionFromEvent(@NotNull ObsolescenceEvent event);
}
