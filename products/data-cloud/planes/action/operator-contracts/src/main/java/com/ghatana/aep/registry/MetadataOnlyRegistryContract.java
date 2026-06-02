/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.registry;

import com.ghatana.aep.pattern.spec.PatternSpec;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * WS2: Metadata-only registry contract for pattern/agent/action definitions.
 *
 * <p>This contract enforces that the registry stores only metadata (definitions,
 * specifications, configurations) and never executable code. The separation ensures:
 * <ul>
 *   <li><b>Registry</b>: Stores metadata only (PatternSpec, AgentEntry, ActionDefinition)</li>
 *   <li><b>Execution</b>: Handled by separate execution engines (PatternEngine, AgentDispatcher)</li>
 *   <li><b>Validation</b>: Registry validates metadata structure, not execution logic</li>
 * </ul>
 *
 * <p>This separation enables:
 * <ul>
 *   <li>Safe metadata updates without affecting running executions</li>
 *   <li>Versioned metadata with rollback capability</li>
 *   <li>Multi-tenant metadata isolation</li>
 *   <li>Metadata governance and audit trails</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Metadata-only registry contract for pattern/agent/action definitions
 * @doc.layer product
 * @doc.pattern Contract
 */
public interface MetadataOnlyRegistryContract {

    /**
     * Pattern metadata operations.
     */
    interface PatternMetadataStore {
        /**
         * Store pattern metadata (PatternSpec) in the registry.
         *
         * <p>WS2: Stores only the pattern specification metadata, not executable code.
         * The pattern execution is handled by PatternEngine separately.
         *
         * @param tenantId tenant identifier
         * @param patternSpec pattern specification metadata
         * @return promise completing when metadata is stored
         */
        Promise<Void> storePattern(String tenantId, PatternSpec patternSpec);

        /**
         * Retrieve pattern metadata by ID.
         *
         * @param tenantId tenant identifier
         * @param patternId pattern identifier
         * @return pattern specification metadata
         */
        Promise<Optional<PatternSpec>> getPattern(String tenantId, String patternId);

        /**
         * List all pattern metadata for a tenant.
         *
         * @param tenantId tenant identifier
         * @return list of pattern specifications
         */
        Promise<Set<PatternSpec>> listPatterns(String tenantId);

        /**
         * Delete pattern metadata.
         *
         * @param tenantId tenant identifier
         * @param patternId pattern identifier
         * @return promise completing when metadata is deleted
         */
        Promise<Void> deletePattern(String tenantId, String patternId);
    }

    /**
     * Agent metadata operations.
     */
    interface AgentMetadataStore {
        /**
         * Store agent metadata (CatalogAgentEntry) in the registry.
         *
         * <p>WS2: Stores only the agent definition metadata, not executable code.
         * The agent execution is handled by AgentDispatcher separately.
         *
         * @param tenantId tenant identifier
         * @param agentEntry agent definition metadata
         * @return promise completing when metadata is stored
         */
        Promise<Void> storeAgent(String tenantId, CatalogAgentEntry agentEntry);

        /**
         * Retrieve agent metadata by ID.
         *
         * @param tenantId tenant identifier
         * @param agentId agent identifier
         * @return agent definition metadata
         */
        Promise<Optional<CatalogAgentEntry>> getAgent(String tenantId, String agentId);

        /**
         * List all agent metadata for a tenant.
         *
         * @param tenantId tenant identifier
         * @return list of agent definitions
         */
        Promise<Set<CatalogAgentEntry>> listAgents(String tenantId);

        /**
         * Delete agent metadata.
         *
         * @param tenantId tenant identifier
         * @param agentId agent identifier
         * @return promise completing when metadata is deleted
         */
        Promise<Void> deleteAgent(String tenantId, String agentId);
    }

    /**
     * Action metadata operations.
     */
    interface ActionMetadataStore {
        /**
         * Store action metadata in the registry.
         *
         * <p>WS2: Stores only the action definition metadata, not executable code.
         * The action execution is handled by ActionExecutor separately.
         *
         * @param tenantId tenant identifier
         * @param actionId action identifier
         * @param actionMetadata action definition metadata
         * @return promise completing when metadata is stored
         */
        Promise<Void> storeAction(String tenantId, String actionId, Map<String, Object> actionMetadata);

        /**
         * Retrieve action metadata by ID.
         *
         * @param tenantId tenant identifier
         * @param actionId action identifier
         * @return action definition metadata
         */
        Promise<Optional<Map<String, Object>>> getAction(String tenantId, String actionId);

        /**
         * List all action metadata for a tenant.
         *
         * @param tenantId tenant identifier
         * @return list of action definitions
         */
        Promise<Set<Map<String, Object>>> listActions(String tenantId);

        /**
         * Delete action metadata.
         *
         * @param tenantId tenant identifier
         * @param actionId action identifier
         * @return promise completing when metadata is deleted
         */
        Promise<Void> deleteAction(String tenantId, String actionId);
    }

    /**
     * Metadata validation operations.
     */
    interface MetadataValidator {
        /**
         * Validate that metadata contains no executable code.
         *
         * <p>WS2: Ensures the registry stores only metadata, not executable code.
         * This validation prevents security risks and maintains the metadata-only contract.
         *
         * @param metadata the metadata to validate
         * @return validation result
         */
        ValidationResult validateNoExecutableCode(Map<String, Object> metadata);

        /**
         * Validate pattern metadata structure.
         *
         * @param patternSpec pattern specification to validate
         * @return validation result
         */
        ValidationResult validatePatternStructure(PatternSpec patternSpec);

        /**
         * Validate agent metadata structure.
         *
         * @param agentEntry agent definition to validate
         * @return validation result
         */
        ValidationResult validateAgentStructure(CatalogAgentEntry agentEntry);

        /**
         * Validate action metadata structure.
         *
         * @param actionMetadata action definition to validate
         * @return validation result
         */
        ValidationResult validateActionStructure(Map<String, Object> actionMetadata);
    }

    // ==================== Supporting Types ====================

    /**
     * Validation result.
     */
    record ValidationResult(
        boolean valid,
        String error,
        java.util.List<String> warnings
    ) {
        public ValidationResult {
            warnings = java.util.List.copyOf(warnings != null ? warnings : java.util.List.of());
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null, java.util.List.of());
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error, java.util.List.of());
        }

        public static ValidationResult invalid(String error, java.util.List<String> warnings) {
            return new ValidationResult(false, error, warnings);
        }

        public static ValidationResult withWarnings(java.util.List<String> warnings) {
            return new ValidationResult(true, null, warnings);
        }
    }
}
