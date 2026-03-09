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
 * Information about an available feature.
 *
 * @doc.type record
 * @doc.purpose Feature information model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record FeatureInfo(
        String name,
        String description,
        List<String> availableTypes,
        String defaultType,
        List<String> requiredVariables,
        List<String> compatibleLanguages,
        boolean addsDependencies
) {
    public FeatureInfo {
        Objects.requireNonNull(name, "name is required");
        availableTypes = availableTypes != null ? List.copyOf(availableTypes) : List.of();
        requiredVariables = requiredVariables != null ? List.copyOf(requiredVariables) : List.of();
        compatibleLanguages = compatibleLanguages != null ? List.copyOf(compatibleLanguages) : List.of();
    }

    public static FeatureInfo of(String name, String description, List<String> types) {
        return new FeatureInfo(
                name,
                description,
                types,
                types.isEmpty() ? null : types.get(0),
                List.of(),
                List.of("java", "typescript", "rust", "go"),
                true
        );
    }
}
