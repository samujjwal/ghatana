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
package com.ghatana.platform.dscli.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DTCG-aligned token file model.
 *
 * <p>Represents a W3C Design Tokens Community Group (DTCG) format token file,
 * with optional $schema, $version, and a map of token groups/values.
 *
 * @doc.type class
 * @doc.purpose DTCG token file structure for CLI parsing and validation.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class TokenFile {

    @JsonProperty("$schema")
    private String schema;

    @JsonProperty("$version")
    private String version = "1.0.0";

    private final Map<String, Object> tokens = new LinkedHashMap<>();

    public String getSchema() {
        return schema;
    }

    public void setSchema(final String schema) {
        this.schema = schema;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    @JsonAnyGetter
    public Map<String, Object> getTokens() {
        return tokens;
    }

    @JsonAnySetter
    public void setToken(final String name, final Object value) {
        tokens.put(name, value);
    }
}
