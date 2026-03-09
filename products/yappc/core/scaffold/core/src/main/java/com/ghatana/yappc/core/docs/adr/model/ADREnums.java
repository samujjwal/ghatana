/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
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

package com.ghatana.yappc.core.docs.adr.model;

/**
 * Enumerations for Architecture Decision Records (ADR).
 * Contains status, type, complexity, impact, template types, and severity definitions.
 *
 * @doc.type class
 * @doc.purpose Enumerations for Architecture Decision Records (ADR).
 * @doc.layer platform
 * @doc.pattern Component
 */
public final class ADREnums {

    private ADREnums() {
        // Utility class
    }

    /**
 * Status of an architecture decision. */
    public enum DecisionStatus {
        PROPOSED,
        ACCEPTED,
        DEPRECATED,
        SUPERSEDED
    }

    /**
 * Type of architectural decision being made. */
    public enum DecisionType {
        ARCHITECTURAL,
        TECHNOLOGICAL,
        PROCESS,
        QUALITY,
        SECURITY,
        PERFORMANCE
    }

    /**
 * Complexity level of a decision. */
    public enum ComplexityLevel {
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH
    }

    /**
 * Impact level of a decision on the system. */
    public enum ImpactLevel {
        LOCAL,
        MODULE,
        SYSTEM,
        ORGANIZATION
    }

    /**
 * Available ADR template types. */
    public enum ADRTemplateType {
        STANDARD,
        BRIEF,
        DETAILED,
        Y_STATEMENT,
        MADR
    }

    /**
 * Severity level for validation issues. */
    public enum IssueSeverity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
}
