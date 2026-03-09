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
 * Plugin capability enumeration defining what a plugin can do.
 *
 * @doc.type enum
 * @doc.purpose Plugin capability classification
 * @doc.layer platform
 * @doc.pattern Enumeration
 */
public enum PluginCapability {
    @JsonProperty("pack_discovery")
    PACK_DISCOVERY,

    @JsonProperty("template_helper")
    TEMPLATE_HELPER,

    @JsonProperty("feature_pack")
    FEATURE_PACK,

    @JsonProperty("build_system")
    BUILD_SYSTEM,

    @JsonProperty("post_processor")
    POST_PROCESSOR,

    @JsonProperty("analyzer")
    ANALYZER,

    @JsonProperty("telemetry")
    TELEMETRY,

    @JsonProperty("validator")
    VALIDATOR,

    @JsonProperty("code_generator")
    CODE_GENERATOR,

    @JsonProperty("dependency_resolver")
    DEPENDENCY_RESOLVER
}
