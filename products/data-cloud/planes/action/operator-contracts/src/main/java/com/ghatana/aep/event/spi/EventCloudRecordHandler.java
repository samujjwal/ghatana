package com.ghatana.aep.event.spi;

/**
 * @doc.type interface
 * @doc.purpose Handles EventCloud records delivered by tail or replay operations
 * @doc.layer product
 * @doc.pattern Callback
 */
@FunctionalInterface
public interface EventCloudRecordHandler {

    void onRecord(EventCloudRecord record);
}
