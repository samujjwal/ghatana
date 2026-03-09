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
package com.ghatana.yappc.core.telemetry.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.time.Instant;
import java.util.Map;

/**
 * Day 19: Telemetry configuration model for opt-in consent management. Ensures
 * privacy compliance per Doc1 §6 Non-Negotiables.
 */
@JsonDeserialize(builder = LocalTelemetryConfiguration.Builder.class)
/**
 * LocalTelemetryConfiguration component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose LocalTelemetryConfiguration component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Configuration
 */
public class LocalTelemetryConfiguration
        implements com.ghatana.yappc.framework.api.services.TelemetryConfiguration {

    private final boolean optedIn;
    private final String version;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String consentVersion;

    @JsonCreator
    LocalTelemetryConfiguration(
            @JsonProperty("optedIn") boolean optedIn,
            @JsonProperty("version") String version,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt,
            @JsonProperty("consentVersion") String consentVersion) {
        this.optedIn = optedIn;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        this.consentVersion = consentVersion;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new configuration with updated opt-in status.
     */
    public LocalTelemetryConfiguration withOptedIn(boolean optedIn) {
        return builder()
                .optedIn(optedIn)
                .version(this.version)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now())
                .consentVersion(this.consentVersion)
                .build();
    }

    public boolean isOptedIn() {
        return optedIn;
    }

    public String getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    @Override
    public boolean isEnabled() {
        return optedIn;
    }

    @Override
    public String getProtocol() {
        return "local-file";
    }

    @Override
    public String getEndpoint() {
        String userHome = System.getProperty("user.home", "");
        return userHome.isEmpty() ? "file:///.yappc/telemetry" : "file://" + userHome + "/.yappc/telemetry";
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of(
                "version", version,
                "createdAt", createdAt,
                "updatedAt", updatedAt,
                "consentVersion", consentVersion);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private boolean optedIn;
        private String version;
        private Instant createdAt;
        private Instant updatedAt;
        private String consentVersion;

        public Builder optedIn(boolean optedIn) {
            this.optedIn = optedIn;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder consentVersion(String consentVersion) {
            this.consentVersion = consentVersion;
            return this;
        }

        public LocalTelemetryConfiguration build() {
            return new LocalTelemetryConfiguration(
                    optedIn, version, createdAt, updatedAt, consentVersion);
        }
    }
}
