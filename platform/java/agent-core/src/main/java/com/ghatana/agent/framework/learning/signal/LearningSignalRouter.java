/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.learning.signal;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * SPI for routing learning signals to the appropriate downstream handler.
 *
 * <p>Implementations may fan-out to multiple handlers based on {@link LearningSignal#signalType()},
 * queue signals asynchronously, or forward them to a remote learning plane.
 *
 * @doc.type interface
 * @doc.purpose Routing SPI for learning signals produced during agent execution
 * @doc.layer platform
 * @doc.pattern Router
 */
public interface LearningSignalRouter {

    /**
     * Routes a {@link LearningSignal} to the appropriate handler(s).
     *
     * @param signal the signal to route
     * @return a {@link Promise} that completes when routing is complete (fire-and-forget is acceptable)
     */
    @NotNull
    Promise<Void> route(@NotNull LearningSignal signal);

    /**
     * A no-op router that silently discards all signals.
     * Useful in tests or contexts where learning is not yet wired.
     */
    @NotNull
    static LearningSignalRouter noOp() {
        return signal -> Promise.complete();
    }
}
