/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts.schema;

import com.ghatana.kernel.contracts.SchemaContract;

import java.util.List;
import java.util.Optional;

/**
 * Bridge interface for subsystems that manage schemas to connect to the
 * canonical {@link SchemaContract} model.
 *
 * <p>Implementors include:</p>
 * <ul>
 *   <li><b>Event-store</b> — JSON Schema v7 event payload validation</li>
 *   <li><b>API gateway</b> — OpenAPI request/response schema validation</li>
 *   <li><b>Data governance</b> — Multi-format (Avro/JSON/Protobuf) schema evolution</li>
 * </ul>
 *
 * <p>Each subsystem publishes its schemas as {@code SchemaContract.SchemaSubject}
 * entries and reports compatibility check results through this interface.</p>
 *
 * @doc.type interface
 * @doc.purpose Bridge between schema subsystems and canonical contract model
 * @doc.layer core
 * @doc.pattern Adapter, Bridge
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public interface SchemaContractBridge {

    /**
     * Compatibility check result.
     */
    record CompatibilityCheckResult(boolean compatible, List<String> violations) {
        /** All clear. */
        public static final CompatibilityCheckResult COMPATIBLE =
            new CompatibilityCheckResult(true, List.of());

        public static CompatibilityCheckResult incompatible(List<String> violations) {
            return new CompatibilityCheckResult(false, List.copyOf(violations));
        }
    }

    /**
     * Breaking change detected event payload.
     */
    record BreakingChangeEvent(String subjectId, String fromVersion, String toVersion,
                               List<String> breakingFields, String requiredAction) {}

    /**
     * Returns the schema subjects managed by this subsystem as canonical contract entries.
     */
    List<SchemaContract.SchemaSubject> exportSubjects();

    /**
     * Checks compatibility of a proposed schema change against the subsystem's rules.
     *
     * @param subjectId the schema subject being evolved
     * @param newSchemaContent the proposed new schema content
     * @return compatibility check result
     */
    CompatibilityCheckResult checkCompatibility(String subjectId, String newSchemaContent);

    /**
     * Returns breaking change events detected since the given version.
     * Empty if no breaking changes detected.
     */
    List<BreakingChangeEvent> getBreakingChangesSince(String subjectId, String sinceVersion);

    /**
     * Returns the active schema content for a subject.
     */
    Optional<String> getActiveSchema(String subjectId);
}
