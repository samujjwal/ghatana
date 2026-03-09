/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.plugin;

import java.util.Map;

/**
 * Plugin interface for telemetry collection.
 *
 * @doc.type interface
 * @doc.purpose Telemetry plugin
 * @doc.layer platform
 * @doc.pattern Plugin SPI
 */
public interface TelemetryPlugin extends YappcPlugin {

    /**
     * Records a telemetry event.
     *
     * @param eventName  event name
     * @param properties event properties
     * @throws PluginException if recording fails
     */
    void recordEvent(String eventName, Map<String, Object> properties) throws PluginException;

    /**
     * Records a metric.
     *
     * @param metricName metric name
     * @param value      metric value
     * @param tags       metric tags
     * @throws PluginException if recording fails
     */
    void recordMetric(String metricName, double value, Map<String, String> tags)
            throws PluginException;

    /**
     * Flushes pending telemetry data.
     *
     * @throws PluginException if flush fails
     */
    void flush() throws PluginException;

    /**
     * Checks if user has consented to telemetry.
     *
     * @return true if consent given
     */
    boolean hasConsent();
}
