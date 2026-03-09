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

package com.ghatana.yappc.core.multirepo;

import java.util.List;

/**
 * Day 21: Multi-repository workspace validation result. Contains validation results for workspace
 * consistency and configuration.
 *
 * @doc.type class
 * @doc.purpose Day 21: Multi-repository workspace validation result. Contains validation results for workspace
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public class MultiRepoValidationResult {

    private final boolean isValid;
    private final List<String> errors;
    private final List<String> warnings;

    public MultiRepoValidationResult(boolean isValid, List<String> errors, List<String> warnings) {
        this.isValid = isValid;
        this.errors = errors;
        this.warnings = warnings;
    }

    public boolean isValid() {
        return isValid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public int getErrorCount() {
        return errors.size();
    }

    public int getWarningCount() {
        return warnings.size();
    }

    @Override
    public String toString() {
        return String.format(
                "MultiRepoValidationResult{valid=%s, errors=%d, warnings=%d}",
                isValid, getErrorCount(), getWarningCount());
    }
}
