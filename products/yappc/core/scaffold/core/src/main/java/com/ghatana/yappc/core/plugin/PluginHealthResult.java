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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Result of a plugin health check.
 *
 * @doc.type record
 * @doc.purpose Plugin health check result
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record PluginHealthResult(
        @JsonProperty("healthy") boolean healthy,
        @JsonProperty("message") String message,
        @JsonProperty("details") List<String> details) {

    @JsonCreator
    public PluginHealthResult {
    }

    public static PluginHealthResult createHealthy() {
        return new PluginHealthResult(true, "Plugin is healthy", List.of());
    }

    public static PluginHealthResult createHealthy(String message) {
        return new PluginHealthResult(true, message, List.of());
    }

    public static PluginHealthResult createUnhealthy(String message) {
        return new PluginHealthResult(false, message, List.of());
    }

    public static PluginHealthResult createUnhealthy(String message, List<String> details) {
        return new PluginHealthResult(false, message, details);
    }
}
