/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
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
package com.ghatana.datacloud.plugins.trino;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuration for EventCloud Trino connector.
 *
 * @doc.type class
 * @doc.purpose Connector configuration
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class EventCloudConnectorConfig {

    // Configuration keys
    public static final String URL_KEY = "eventcloud.url";
    public static final String AUTH_TOKEN_KEY = "eventcloud.auth.token";
    public static final String TENANT_ID_KEY = "eventcloud.tenant.id";
    public static final String CACHE_ENABLED_KEY = "eventcloud.cache.enabled";
    public static final String CACHE_TTL_KEY = "eventcloud.cache.ttl.seconds";
    public static final String MAX_SPLITS_KEY = "eventcloud.max.splits";
    public static final String FETCH_SIZE_KEY = "eventcloud.fetch.size";

    // Default values
    private static final String DEFAULT_URL = "http://localhost:8080";
    private static final boolean DEFAULT_CACHE_ENABLED = true;
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_SPLITS = 16;
    private static final int DEFAULT_FETCH_SIZE = 10000;

    private final String url;
    private final String authToken;
    private final String defaultTenantId;
    private final boolean cacheEnabled;
    private final Duration cacheTtl;
    private final int maxSplits;
    private final int fetchSize;

    private EventCloudConnectorConfig(Builder builder) {
        this.url = Objects.requireNonNull(builder.url, "url is required");
        this.authToken = builder.authToken;
        this.defaultTenantId = builder.defaultTenantId;
        this.cacheEnabled = builder.cacheEnabled;
        this.cacheTtl = builder.cacheTtl;
        this.maxSplits = builder.maxSplits;
        this.fetchSize = builder.fetchSize;
    }

    /**
     * Creates configuration from a properties map.
     *
     * @param config Map of configuration properties
     * @return Configured instance
     */
    public static EventCloudConnectorConfig fromMap(Map<String, String> config) {
        Builder builder = builder()
                .url(config.getOrDefault(URL_KEY, DEFAULT_URL))
                .cacheEnabled(Boolean.parseBoolean(
                        config.getOrDefault(CACHE_ENABLED_KEY, String.valueOf(DEFAULT_CACHE_ENABLED))))
                .cacheTtl(Duration.ofSeconds(Long.parseLong(
                        config.getOrDefault(CACHE_TTL_KEY, String.valueOf(DEFAULT_CACHE_TTL.toSeconds())))))
                .maxSplits(Integer.parseInt(
                        config.getOrDefault(MAX_SPLITS_KEY, String.valueOf(DEFAULT_MAX_SPLITS))))
                .fetchSize(Integer.parseInt(
                        config.getOrDefault(FETCH_SIZE_KEY, String.valueOf(DEFAULT_FETCH_SIZE))));

        if (config.containsKey(AUTH_TOKEN_KEY)) {
            builder.authToken(config.get(AUTH_TOKEN_KEY));
        }

        if (config.containsKey(TENANT_ID_KEY)) {
            builder.defaultTenantId(config.get(TENANT_ID_KEY));
        }

        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getUrl() {
        return url;
    }

    public Optional<String> getAuthToken() {
        return Optional.ofNullable(authToken);
    }

    public Optional<String> getDefaultTenantId() {
        return Optional.ofNullable(defaultTenantId);
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public int getMaxSplits() {
        return maxSplits;
    }

    public int getFetchSize() {
        return fetchSize;
    }

    /**
     * Builder for EventCloudConnectorConfig.
     */
    public static final class Builder {

        private String url = DEFAULT_URL;
        private String authToken;
        private String defaultTenantId;
        private boolean cacheEnabled = DEFAULT_CACHE_ENABLED;
        private Duration cacheTtl = DEFAULT_CACHE_TTL;
        private int maxSplits = DEFAULT_MAX_SPLITS;
        private int fetchSize = DEFAULT_FETCH_SIZE;

        private Builder() {
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public Builder defaultTenantId(String defaultTenantId) {
            this.defaultTenantId = defaultTenantId;
            return this;
        }

        public Builder cacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
            return this;
        }

        public Builder cacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
            return this;
        }

        public Builder maxSplits(int maxSplits) {
            this.maxSplits = maxSplits;
            return this;
        }

        public Builder fetchSize(int fetchSize) {
            this.fetchSize = fetchSize;
            return this;
        }

        public EventCloudConnectorConfig build() {
            return new EventCloudConnectorConfig(this);
        }
    }
}
