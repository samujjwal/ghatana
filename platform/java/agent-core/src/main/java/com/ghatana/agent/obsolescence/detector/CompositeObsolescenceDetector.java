/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence.detector;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.obsolescence.ObsolescenceDetector;
import com.ghatana.agent.obsolescence.ObsolescenceEvent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite obsolescence detector that combines multiple concrete detectors.
 * Phase 7 FIX: Orchestrates all concrete detectors for comprehensive obsolescence detection.
 *
 * @doc.type class
 * @doc.purpose Composite detector combining multiple obsolescence detectors
 * @doc.layer agent-core
 * @doc.pattern Composite
 */
public final class CompositeObsolescenceDetector implements ObsolescenceDetector {

    private final List<ObsolescenceDetector> detectors;

    /**
     * Creates a composite detector with the provided concrete detectors.
     *
     * @param detectors list of concrete detectors
     */
    public CompositeObsolescenceDetector(@NotNull List<ObsolescenceDetector> detectors) {
        this.detectors = List.copyOf(detectors);
    }

    @Override
    @NotNull
    public Promise<List<ObsolescenceEvent>> detect(
            @NotNull MasteryItem item,
            @NotNull EnvironmentFingerprint env
    ) {
        List<ObsolescenceEvent> allEvents = new ArrayList<>();

        // Run all detectors and collect events
        Promise<List<ObsolescenceEvent>> chain = Promise.of(allEvents);
        for (ObsolescenceDetector detector : detectors) {
            chain = chain.then(events -> {
                return detector.detect(item, env).then(detectorEvents -> {
                    allEvents.addAll(detectorEvents);
                    return Promise.of(allEvents);
                });
            });
        }

        return chain;
    }

    @Override
    @NotNull
    public Promise<List<ObsolescenceEvent>> scanAll(@NotNull String tenantId, @NotNull EnvironmentFingerprint env) {
        List<ObsolescenceEvent> allEvents = new ArrayList<>();

        // Run all detectors' scanAll methods
        Promise<List<ObsolescenceEvent>> chain = Promise.of(allEvents);
        for (ObsolescenceDetector detector : detectors) {
            chain = chain.then(events -> {
                return detector.scanAll(tenantId, env).then(detectorEvents -> {
                    allEvents.addAll(detectorEvents);
                    return Promise.of(allEvents);
                });
            });
        }

        return chain;
    }
}
