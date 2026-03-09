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

/**
 * Result of validating a project.
 *
 * @doc.type record
 * @doc.purpose Project validation result model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ProjectValidationResult(
        boolean isValid,
        boolean isYappcProject,
        List<String> errors,
        List<String> warnings,
        List<String> missingFiles,
        List<String> extraFiles
) {
    public static ProjectValidationResult validResult() {
        return new ProjectValidationResult(true, true, List.of(), List.of(), List.of(), List.of());
    }

    public static ProjectValidationResult notYappcProject() {
        return new ProjectValidationResult(false, false, List.of("Not a YAPPC-managed project"), List.of(), List.of(), List.of());
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
