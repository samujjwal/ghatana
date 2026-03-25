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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Plugin lifecycle state.
 *
 * @doc.type enum
 * @doc.purpose Plugin state tracking
 * @doc.layer platform
 * @doc.pattern State Machine
 */
public enum PluginState {
    @JsonProperty("unloaded")
    UNLOADED,

    @JsonProperty("loaded")
    LOADED,

    @JsonProperty("initialized")
    INITIALIZED,

    @JsonProperty("active")
    ACTIVE,

    @JsonProperty("error")
    ERROR,

    @JsonProperty("shutdown")
    SHUTDOWN
}
