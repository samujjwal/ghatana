/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.obsolescence;

import com.ghatana.agent.obsolescence.ObsolescenceEvent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Cloud-backed repository for obsolescence events.
 *
 * <p>TODO: Replace in-memory storage with actual Data Cloud persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud repository for obsolescence events
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudObsolescenceEventRepository {

    private final ConcurrentHashMap<String, ObsolescenceEvent> events = new ConcurrentHashMap<>();

    /**
     * Saves an obsolescence event.
     *
     * @param event event to save
     * @return promise of saved event
     */
    @NotNull
    public Promise<ObsolescenceEvent> save(@NotNull ObsolescenceEvent event) {
        events.put(event.eventId(), event);
        return Promise.of(event);
    }

    /**
     * Finds an obsolescence event by ID.
     *
     * @param eventId event identifier
     * @return promise of optional event
     */
    @NotNull
    public Promise<Optional<ObsolescenceEvent>> findById(@NotNull String eventId) {
        return Promise.of(Optional.ofNullable(events.get(eventId)));
    }

    /**
     * Finds obsolescence events for a specific mastery item.
     *
     * @param masteryId mastery item identifier
     * @return promise of list of events
     */
    @NotNull
    public Promise<List<ObsolescenceEvent>> findByMasteryId(@NotNull String masteryId) {
        return Promise.of(events.values().stream()
                .filter(e -> e.masteryId().equals(masteryId))
                .toList());
    }

    /**
     * Finds recent obsolescence events.
     *
     * @param since time threshold
     * @return promise of list of events
     */
    @NotNull
    public Promise<List<ObsolescenceEvent>> findRecent(@NotNull java.time.Instant since) {
        return Promise.of(events.values().stream()
                .filter(e -> e.detectedAt().isAfter(since))
                .toList());
    }
}
