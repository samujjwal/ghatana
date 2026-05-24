package com.ghatana.aep.event.spi;

import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Replays EventCloud records across an inclusive offset range
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface EventCloudReplay {

    Promise<Void> replay(String tenantId, EventCloudOffset from, EventCloudOffset to, EventCloudRecordHandler handler);
}
