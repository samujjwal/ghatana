package com.ghatana.aep.event.spi;

import io.activej.promise.Promise;

import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Canonical AEP EventCloud SPI combining append, tail, replay, checkpoint, and watermark access
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface EventCloudStore extends EventCloudAppender, EventCloudTail, EventCloudReplay {

    Promise<Optional<EventCloudRecord>> read(EventCloudOffset offset);

    Promise<EventCloudWatermark> watermark(String tenantId, String partition);

    EventCloudCheckpointStore checkpoints();

    EventCloudPartialMatchStore partialMatches();
}
