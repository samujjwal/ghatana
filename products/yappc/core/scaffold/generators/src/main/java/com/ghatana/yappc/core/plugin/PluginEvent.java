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
import java.time.Instant;
import java.util.Map;

/**
 * Plugin lifecycle and operation event.
 *
 * @doc.type record
 * @doc.purpose Plugin event data
 * @doc.layer platform
 * @doc.pattern Event
 */
public record PluginEvent(
        @JsonProperty("type") PluginEventType type,
        @JsonProperty("pluginId") String pluginId,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("data") Map<String, Object> data) {

    @JsonCreator
    public PluginEvent {
    }

    public static PluginEvent of(PluginEventType type, String pluginId) {
        return new PluginEvent(type, pluginId, Instant.now(), Map.of());
    }

    public static PluginEvent of(PluginEventType type, String pluginId, Map<String, Object> data) {
        return new PluginEvent(type, pluginId, Instant.now(), data);
    }

    /**
     * Plugin event types.
     */
    public enum PluginEventType {
        @JsonProperty("before_render")
        BEFORE_RENDER,

        @JsonProperty("after_render")
        AFTER_RENDER,

        @JsonProperty("before_apply")
        BEFORE_APPLY,

        @JsonProperty("after_apply")
        AFTER_APPLY,

        @JsonProperty("validation")
        VALIDATION,

        @JsonProperty("analysis")
        ANALYSIS,

        @JsonProperty("post_process")
        POST_PROCESS,

        @JsonProperty("plugin_loaded")
        PLUGIN_LOADED,

        @JsonProperty("plugin_initialized")
        PLUGIN_INITIALIZED,

        @JsonProperty("plugin_error")
        PLUGIN_ERROR,

        @JsonProperty("plugin_shutdown")
        PLUGIN_SHUTDOWN
    }
}
