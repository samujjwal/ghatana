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

/**
 * A single validation issue found during token/contract validation or audit.
 *
 * @doc.type class
 * @doc.purpose Represents a validation or audit finding for reporting.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ValidationIssue(
        Severity severity,
        String code,
        String message,
        String path) {

    /** Severity levels matching standard toolchain conventions. */
    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public static ValidationIssue error(final String code, final String message, final String path) {
        return new ValidationIssue(Severity.ERROR, code, message, path);
    }

    public static ValidationIssue warning(final String code, final String message, final String path) {
        return new ValidationIssue(Severity.WARNING, code, message, path);
    }

    public static ValidationIssue info(final String code, final String message, final String path) {
        return new ValidationIssue(Severity.INFO, code, message, path);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
