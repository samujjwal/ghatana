package com.ghatana.aep.event.spi;

import io.activej.promise.Promise;

import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Persists and reads EventCloud consumer checkpoints
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface EventCloudCheckpointStore {

    Promise<Void> save(EventCloudCheckpoint checkpoint);

    Promise<Optional<EventCloudCheckpoint>> load(String tenantId, String consumerId);
}
