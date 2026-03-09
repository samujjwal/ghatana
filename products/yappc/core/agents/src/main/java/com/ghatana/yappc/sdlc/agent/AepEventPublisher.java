package com.ghatana.yappc.sdlc.agent;

import io.activej.promise.Promise;
import java.util.Map;

/** Publishes YAPPC workflow events to AEP runtime endpoints. 
 * @doc.type interface
 * @doc.purpose Defines the contract for aep event publisher
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface AepEventPublisher {
  Promise<Void> publish(String eventType, String tenantId, Map<String, Object> payload);
}
