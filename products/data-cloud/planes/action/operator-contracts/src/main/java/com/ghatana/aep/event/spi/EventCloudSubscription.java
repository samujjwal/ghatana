package com.ghatana.aep.event.spi;

/**
 * @doc.type interface
 * @doc.purpose Represents an active EventCloud tail or subscription handle
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface EventCloudSubscription {

    void cancel();

    boolean isCancelled();
}
