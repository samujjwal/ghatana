package com.ghatana.aep.event.spi;

import com.ghatana.aep.model.CanonicalEvent;
import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Appends canonical events to EventCloud and returns assigned offsets
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface EventCloudAppender {

    Promise<EventCloudOffset> append(CanonicalEvent event);
}
