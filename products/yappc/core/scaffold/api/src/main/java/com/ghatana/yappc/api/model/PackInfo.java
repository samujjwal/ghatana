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
import java.util.Map;
import java.util.Objects;

/**
 * Information about a pack.
 *
 * @doc.type record
 * @doc.purpose Pack information model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class PackInfo {

    private final String name;
    private final String version;
    private final String description;
    private final String language;
    private final String category;
    private final String platform;
    private final String buildSystem;
    private final String archetype;
    private final List<String> templates;
    private final List<String> requiredVariables;
    private final List<String> optionalVariables;
    private final List<String> supportedPacks;
    private final Map<String, String> defaults;
    private final boolean isComposition;
    private final List<String> composedPacks;

    private PackInfo(Builder builder) {
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.language = builder.language;
        this.category = builder.category;
        this.platform = builder.platform;
        this.buildSystem = builder.buildSystem;
        this.archetype = builder.archetype;
        this.templates = builder.templates != null ? List.copyOf(builder.templates) : List.of();
        this.requiredVariables = builder.requiredVariables != null ? List.copyOf(builder.requiredVariables) : List.of();
        this.optionalVariables = builder.optionalVariables != null ? List.copyOf(builder.optionalVariables) : List.of();
        this.supportedPacks = builder.supportedPacks != null ? List.copyOf(builder.supportedPacks) : List.of();
        this.defaults = builder.defaults != null ? Map.copyOf(builder.defaults) : Map.of();
        this.isComposition = builder.isComposition;
        this.composedPacks = builder.composedPacks != null ? List.copyOf(builder.composedPacks) : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
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

    public String getArchetype() {
        return archetype;
    }

    public List<String> getTemplates() {
        return templates;
    }

    public List<String> getRequiredVariables() {
        return requiredVariables;
    }

    public List<String> getOptionalVariables() {
        return optionalVariables;
    }

    public List<String> getSupportedPacks() {
        return supportedPacks;
    }

    public Map<String, String> getDefaults() {
        return defaults;
    }

    public boolean isComposition() {
        return isComposition;
    }

    public List<String> getComposedPacks() {
        return composedPacks;
    }

    public int getTemplateCount() {
        return templates.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackInfo packInfo = (PackInfo) o;
        return Objects.equals(name, packInfo.name) &&
                Objects.equals(version, packInfo.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version);
    }

    @Override
    public String toString() {
        return "PackInfo{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", language='" + language + '\'' +
                ", category='" + category + '\'' +
                ", templates=" + templates.size() +
                '}';
    }

    public static final class Builder {
        private String name;
        private String version;
        private String description;
        private String language;
        private String category;
        private String platform;
        private String buildSystem;
        private String archetype;
        private List<String> templates;
        private List<String> requiredVariables;
        private List<String> optionalVariables;
        private List<String> supportedPacks;
        private Map<String, String> defaults;
        private boolean isComposition;
        private List<String> composedPacks;

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

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

        public Builder archetype(String archetype) {
            this.archetype = archetype;
            return this;
        }

        public Builder templates(List<String> templates) {
            this.templates = templates;
            return this;
        }

        public Builder requiredVariables(List<String> requiredVariables) {
            this.requiredVariables = requiredVariables;
            return this;
        }

        public Builder optionalVariables(List<String> optionalVariables) {
            this.optionalVariables = optionalVariables;
            return this;
        }

        public Builder supportedPacks(List<String> supportedPacks) {
            this.supportedPacks = supportedPacks;
            return this;
        }

        public Builder defaults(Map<String, String> defaults) {
            this.defaults = defaults;
            return this;
        }

        public Builder isComposition(boolean isComposition) {
            this.isComposition = isComposition;
            return this;
        }

        public Builder composedPacks(List<String> composedPacks) {
            this.composedPacks = composedPacks;
            return this;
        }

        public PackInfo build() {
            return new PackInfo(this);
        }
    }
}
