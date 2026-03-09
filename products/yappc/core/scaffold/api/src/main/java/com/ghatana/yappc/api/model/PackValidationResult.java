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
 * Result of validating a pack.
 *
 * @doc.type record
 * @doc.purpose Pack validation result model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class PackValidationResult {

    private final boolean valid;
    private final String packName;
    private final List<ValidationError> errors;
    private final List<ValidationWarning> warnings;
    private final int templateCount;
    private final int variableCount;

    private PackValidationResult(Builder builder) {
        this.valid = builder.valid;
        this.packName = builder.packName;
        this.errors = builder.errors != null ? List.copyOf(builder.errors) : List.of();
        this.warnings = builder.warnings != null ? List.copyOf(builder.warnings) : List.of();
        this.templateCount = builder.templateCount;
        this.variableCount = builder.variableCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PackValidationResult valid(String packName, int templateCount, int variableCount) {
        return builder()
                .valid(true)
                .packName(packName)
                .templateCount(templateCount)
                .variableCount(variableCount)
                .build();
    }

    public boolean isValid() {
        return valid;
    }

    public String getPackName() {
        return packName;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public List<ValidationWarning> getWarnings() {
        return warnings;
    }

    public int getTemplateCount() {
        return templateCount;
    }

    public int getVariableCount() {
        return variableCount;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackValidationResult that = (PackValidationResult) o;
        return valid == that.valid &&
                Objects.equals(packName, that.packName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valid, packName);
    }

    @Override
    public String toString() {
        return "PackValidationResult{" +
                "valid=" + valid +
                ", packName='" + packName + '\'' +
                ", errors=" + errors.size() +
                ", warnings=" + warnings.size() +
                '}';
    }

    /**
     * Represents a validation error.
     */
    public record ValidationError(
            String code,
            String message,
            String file,
            int line
    ) {}

    /**
     * Represents a validation warning.
     */
    public record ValidationWarning(
            String code,
            String message,
            String file
    ) {}

    public static final class Builder {
        private boolean valid;
        private String packName;
        private List<ValidationError> errors;
        private List<ValidationWarning> warnings;
        private int templateCount;
        private int variableCount;

        private Builder() {}

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder packName(String packName) {
            this.packName = packName;
            return this;
        }

        public Builder errors(List<ValidationError> errors) {
            this.errors = errors;
            return this;
        }

        public Builder warnings(List<ValidationWarning> warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder templateCount(int templateCount) {
            this.templateCount = templateCount;
            return this;
        }

        public Builder variableCount(int variableCount) {
            this.variableCount = variableCount;
            return this;
        }

        public PackValidationResult build() {
            return new PackValidationResult(this);
        }
    }
}
