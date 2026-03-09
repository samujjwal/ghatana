package com.ghatana.yappc.refactorer.event;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;

/**
 * Core event bus port for publish-subscribe event distribution.
 
 * @doc.type interface
 * @doc.purpose Defines the contract for event bus
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface EventBus {

    Promise<Void> publish(Event event);

    Promise<Void> subscribe(EventHandler handler);

    Promise<Void> unsubscribe(EventHandler handler);

    int getHandlerCount();

    long getEventCount();

    Promise<Void> clear();
}
