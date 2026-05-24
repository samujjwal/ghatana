package com.ghatana.aep.event.spi;

import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Tails EventCloud records from a tenant partition and offset
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface EventCloudTail {

    Promise<EventCloudSubscription> tail(String tenantId, String partition, EventCloudOffset from, EventCloudRecordHandler handler);
}
