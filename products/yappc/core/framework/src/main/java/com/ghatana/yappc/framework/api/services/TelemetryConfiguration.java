package com.ghatana.yappc.framework.api.services;

import java.util.Map;

/** Telemetry provider configuration contract. 
 * @doc.type interface
 * @doc.purpose Defines the contract for telemetry configuration
 * @doc.layer core
 * @doc.pattern Configuration
*/
public interface TelemetryConfiguration {
  boolean isEnabled();

  String getProtocol();

  String getEndpoint();

  Map<String, Object> getProperties();
}
