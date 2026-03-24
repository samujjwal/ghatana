package com.ghatana.yappc.framework.api.services;

import java.util.Map;

/** Telemetry sink contract used by framework and plugins. 
 * @doc.type interface
 * @doc.purpose Defines the contract for telemetry collector
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface TelemetryCollector extends AutoCloseable {
  void recordEvent(String eventName, Map<String, Object> properties);

  void recordMetric(String metricName, double value, Map<String, String> tags);

  void startTrace(String operationName);

  void endTrace(String operationName);

  @Override
  void close();
}
