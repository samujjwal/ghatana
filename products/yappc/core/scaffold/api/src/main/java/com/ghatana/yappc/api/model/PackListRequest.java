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

package com.ghatana.yappc.api.model;

import java.util.List;
import java.util.Objects;

/**
 * Request to list packs with filtering options.
 *
 * @doc.type record
 * @doc.purpose Pack listing filter model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class PackListRequest {

    private final String language;
    private final String category;
    private final String platform;
    private final String buildSystem;
    private final String searchQuery;
    private final List<String> tags;
    private final boolean includeCompositions;
    private final boolean includeFeaturePacks;

    private PackListRequest(Builder builder) {
        this.language = builder.language;
        this.category = builder.category;
        this.platform = builder.platform;
        this.buildSystem = builder.buildSystem;
        this.searchQuery = builder.searchQuery;
        this.tags = builder.tags != null ? List.copyOf(builder.tags) : List.of();
        this.includeCompositions = builder.includeCompositions;
        this.includeFeaturePacks = builder.includeFeaturePacks;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PackListRequest all() {
        return builder().build();
    }

    public static PackListRequest byLanguage(String language) {
        return builder().language(language).build();
    }

    public static PackListRequest byCategory(String category) {
        return builder().category(category).build();
    }

    public String getLanguage() {
        return language;
    }

    public String getCategory() {
        return category;
    }

    public String getPlatform() {
        return platform;
    }

    public String getBuildSystem() {
        return buildSystem;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean isIncludeCompositions() {
        return includeCompositions;
    }

    public boolean isIncludeFeaturePacks() {
        return includeFeaturePacks;
    }

    public boolean hasFilters() {
        return language != null || category != null || platform != null ||
                buildSystem != null || searchQuery != null || !tags.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackListRequest that = (PackListRequest) o;
        return includeCompositions == that.includeCompositions &&
                includeFeaturePacks == that.includeFeaturePacks &&
                Objects.equals(language, that.language) &&
                Objects.equals(category, that.category) &&
                Objects.equals(platform, that.platform) &&
                Objects.equals(buildSystem, that.buildSystem) &&
                Objects.equals(searchQuery, that.searchQuery) &&
                Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(language, category, platform, buildSystem, searchQuery, tags,
                includeCompositions, includeFeaturePacks);
    }

    @Override
    public String toString() {
        return "PackListRequest{" +
                "language='" + language + '\'' +
                ", category='" + category + '\'' +
                ", platform='" + platform + '\'' +
                ", searchQuery='" + searchQuery + '\'' +
                '}';
    }

    public static final class Builder {
        private String language;
        private String category;
        private String platform;
        private String buildSystem;
        private String searchQuery;
        private List<String> tags;
        private boolean includeCompositions = true;
        private boolean includeFeaturePacks = true;

        private Builder() {}

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder platform(String platform) {
            this.platform = platform;
            return this;
        }

        public Builder buildSystem(String buildSystem) {
            this.buildSystem = buildSystem;
            return this;
        }

        public Builder searchQuery(String searchQuery) {
            this.searchQuery = searchQuery;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder includeCompositions(boolean includeCompositions) {
            this.includeCompositions = includeCompositions;
            return this;
        }

        public Builder includeFeaturePacks(boolean includeFeaturePacks) {
            this.includeFeaturePacks = includeFeaturePacks;
            return this;
        }

        public PackListRequest build() {
            return new PackListRequest(this);
        }
    }
}
