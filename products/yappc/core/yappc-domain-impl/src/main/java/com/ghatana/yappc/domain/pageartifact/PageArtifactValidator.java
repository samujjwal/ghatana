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

package com.ghatana.yappc.domain.pageartifact;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side validator for PageArtifactDocument.
 * <p>
 * Validates:
 * <ul>
 *   <li>BuilderDocument structure and required fields</li>
 *   <li>Design-system contracts compliance</li>
 *   <li>Trust policy enforcement</li>
 *   <li>Data classification validity</li>
 *   <li>Residual island thresholds</li>
 *   <li>Executable payload safety</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Server-side validation for page artifact documents
 * @doc.layer product
 * @doc.pattern Validator
 */
public final class PageArtifactValidator {

    private static final Logger LOG = LoggerFactory.getLogger(PageArtifactValidator.class);

    private static final int MAX_RESIDUAL_ISLANDS = 10;
    private static final double MIN_ROUND_TRIP_FIDELITY = 0.7;
    private static final int MAX_NODE_COUNT = 10000;
    private static final Set<String> GOVERNANCE_REQUIRED_SOURCES = Set.of(
            "generated",
            "decompiled",
            "import",
            "imported"
    );

    // Valid sync status values
    private static final String[] VALID_SYNC_STATUS = {
            "SYNCED", "PENDING", "CONFLICT", "ERROR"
    };

    // Valid trust level values
    private static final String[] VALID_TRUST_LEVEL = {
            "TRUSTED", "UNTRUSTED", "UNKNOWN", "REVIEW_REQUIRED"
    };

    // Valid data classification values
    private static final String[] VALID_DATA_CLASSIFICATION = {
            "UNCLASSIFIED", "PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"
    };

    private PageArtifactValidator() {
        // Utility class
    }

    /**
     * Validates a PageArtifactDocument and returns validation result.
     *
     * @param document The document to validate
     * @return ValidationResult with errors and warnings
     */
    @NotNull
    public static ValidationResult validate(@NotNull PageArtifactDocument document) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        LOG.debug("Validating PageArtifactDocument: artifactId={}", document.artifactId());

        // Validate BuilderDocument structure
        validateBuilderDocument(document.builderDocument(), errors, warnings);

        // Validate sync status
        validateSyncStatus(document.syncStatus(), errors);

        // Validate trust level
        validateTrustLevel(document.trustLevel(), errors);

        // Validate data classification
        validateDataClassification(document.dataClassification(), errors);

        // Validate residual islands
        validateResidualIslands(document.residualIslandCount(), errors, warnings);

        // Validate round-trip fidelity
        validateRoundTripFidelity(document.roundTripFidelity(), errors, warnings);

        // Validate governance records
        validateGovernanceRecords(document.aiChangeRecords(), document.source(), errors, warnings);

        LOG.debug("Validation complete: errors={}, warnings={}", errors.size(), warnings.size());

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private static void validateBuilderDocument(
            @NotNull Map<String, Object> builderDocument,
            @NotNull List<String> errors,
            @NotNull List<String> warnings
    ) {
        // Check for required top-level fields
        if (!builderDocument.containsKey("rootNodes")) {
            errors.add("BuilderDocument missing required field: rootNodes");
        }
        if (!builderDocument.containsKey("nodes")) {
            errors.add("BuilderDocument missing required field: nodes");
        }

        Object rootNodes = builderDocument.get("rootNodes");
        if (!(rootNodes instanceof List<?> rootNodeList)) {
            errors.add("BuilderDocument.rootNodes must be a list");
        }

        // Validate nodes structure if present
        Map<String, Object> nodesMap = Map.of();
        if (builderDocument.containsKey("nodes")) {
            Object nodes = builderDocument.get("nodes");
            if (!(nodes instanceof Map)) {
                errors.add("BuilderDocument.nodes must be a map/object");
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedNodesMap = (Map<String, Object>) nodes;
                nodesMap = typedNodesMap;
                if (nodesMap.size() > MAX_NODE_COUNT) {
                    warnings.add("BuilderDocument contains " + nodesMap.size() + " nodes, exceeds recommended limit of " + MAX_NODE_COUNT);
                }
                validateNodeEntries(nodesMap, errors);
            }
        }

        if (rootNodes instanceof List<?> rootNodeList && !nodesMap.isEmpty()) {
            validateRootNodeReferences(rootNodeList, nodesMap, errors);
            validateSlotReferences(nodesMap, errors);
        }

        // Check for executable payloads (security concern)
        checkForExecutablePayloads(builderDocument, warnings);
    }

    private static void validateNodeEntries(
            @NotNull Map<String, Object> nodesMap,
            @NotNull List<String> errors
    ) {
        for (Map.Entry<String, Object> entry : nodesMap.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> nodeMap)) {
                errors.add("BuilderDocument node '" + entry.getKey() + "' must be an object");
                continue;
            }

            Object contractName = nodeMap.get("contractName");
            if (!(contractName instanceof String contract) || contract.isBlank()) {
                errors.add("BuilderDocument node '" + entry.getKey() + "' missing contractName");
            }

            Object props = nodeMap.get("props");
            if (props != null && !(props instanceof Map<?, ?>)) {
                errors.add("BuilderDocument node '" + entry.getKey() + "'.props must be an object");
            }

            Object slots = nodeMap.get("slots");
            if (slots != null && !(slots instanceof Map<?, ?>)) {
                errors.add("BuilderDocument node '" + entry.getKey() + "'.slots must be an object");
            }
        }
    }

    private static void validateRootNodeReferences(
            @NotNull List<?> rootNodes,
            @NotNull Map<String, Object> nodesMap,
            @NotNull List<String> errors
    ) {
        Set<String> seenRoots = new HashSet<>();
        for (Object rootNode : rootNodes) {
            if (!(rootNode instanceof String rootId) || rootId.isBlank()) {
                errors.add("BuilderDocument.rootNodes must contain non-empty string node IDs");
                continue;
            }
            if (!nodesMap.containsKey(rootId)) {
                errors.add("BuilderDocument.rootNodes references unknown node: " + rootId);
            }
            if (!seenRoots.add(rootId)) {
                errors.add("BuilderDocument.rootNodes contains duplicate node: " + rootId);
            }
        }
    }

    private static void validateSlotReferences(
            @NotNull Map<String, Object> nodesMap,
            @NotNull List<String> errors
    ) {
        for (Map.Entry<String, Object> entry : nodesMap.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> nodeMap)) {
                continue;
            }

            Object slots = nodeMap.get("slots");
            if (!(slots instanceof Map<?, ?> slotsMap)) {
                continue;
            }

            for (Map.Entry<?, ?> slotEntry : slotsMap.entrySet()) {
                if (!(slotEntry.getValue() instanceof List<?> childIds)) {
                    errors.add("BuilderDocument node '" + entry.getKey() + "' slot '" + slotEntry.getKey() + "' must be a list");
                    continue;
                }

                for (Object childId : childIds) {
                    if (!(childId instanceof String childNodeId) || childNodeId.isBlank()) {
                        errors.add("BuilderDocument node '" + entry.getKey() + "' has non-string child reference");
                        continue;
                    }
                    if (!nodesMap.containsKey(childNodeId)) {
                        errors.add("BuilderDocument node '" + entry.getKey() + "' references missing child node '" + childNodeId + "'");
                    }
                }
            }
        }
    }

    private static void checkForExecutablePayloads(
            @NotNull Object currentValue,
            @NotNull List<String> warnings
    ) {
        if (currentValue instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                checkForExecutablePayloads(value, warnings);
            }
            return;
        }

        if (currentValue instanceof List<?> list) {
            for (Object value : list) {
                checkForExecutablePayloads(value, warnings);
            }
            return;
        }

        if (currentValue instanceof String stringValue) {
            String lowered = stringValue.toLowerCase();
            if (lowered.contains("eval(") || lowered.contains("<script") || lowered.contains("javascript:")) {
                warnings.add("BuilderDocument contains potentially executable content - review required");
            }
        }
    }

    private static void validateSyncStatus(String syncStatus, @NotNull List<String> errors) {
        boolean valid = false;
        for (String status : VALID_SYNC_STATUS) {
            if (status.equals(syncStatus)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            errors.add("Invalid sync_status: " + syncStatus + ". Valid values: " + String.join(", ", VALID_SYNC_STATUS));
        }
    }

    private static void validateTrustLevel(String trustLevel, @NotNull List<String> errors) {
        boolean valid = false;
        for (String level : VALID_TRUST_LEVEL) {
            if (level.equals(trustLevel)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            errors.add("Invalid trust_level: " + trustLevel + ". Valid values: " + String.join(", ", VALID_TRUST_LEVEL));
        }
    }

    private static void validateDataClassification(String dataClassification, @NotNull List<String> errors) {
        boolean valid = false;
        for (String classification : VALID_DATA_CLASSIFICATION) {
            if (classification.equals(dataClassification)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            errors.add("Invalid data_classification: " + dataClassification + ". Valid values: " + String.join(", ", VALID_DATA_CLASSIFICATION));
        }
    }

    private static void validateResidualIslands(
            int residualIslandCount,
            @NotNull List<String> errors,
            @NotNull List<String> warnings
    ) {
        if (residualIslandCount < 0) {
            errors.add("residual_island_count cannot be negative: " + residualIslandCount);
        }
        if (residualIslandCount > MAX_RESIDUAL_ISLANDS) {
            errors.add("residual_island_count exceeds maximum allowed: " + residualIslandCount + " > " + MAX_RESIDUAL_ISLANDS);
        } else if (residualIslandCount > 0) {
            warnings.add("BuilderDocument contains " + residualIslandCount + " residual islands - review recommended");
        }
    }

    private static void validateRoundTripFidelity(
            double roundTripFidelity,
            @NotNull List<String> errors,
            @NotNull List<String> warnings
    ) {
        if (roundTripFidelity < 0.0 || roundTripFidelity > 1.0) {
            errors.add("round_trip_fidelity must be between 0.0 and 1.0: " + roundTripFidelity);
        }
        if (roundTripFidelity < MIN_ROUND_TRIP_FIDELITY) {
            warnings.add("round_trip_fidelity below recommended threshold: " + roundTripFidelity + " < " + MIN_ROUND_TRIP_FIDELITY);
        }
    }

    private static void validateGovernanceRecords(
            @NotNull List<PageArtifactDocument.GovernanceRecord> governanceRecords,
            String source,
            @NotNull List<String> errors,
            @NotNull List<String> warnings
    ) {
        boolean governanceRequired = source != null && GOVERNANCE_REQUIRED_SOURCES.contains(source.toLowerCase());

        if (governanceRecords.isEmpty() && governanceRequired) {
            errors.add("Governance records are required for source: " + source);
            return;
        }

        if (governanceRecords.isEmpty()) {
            warnings.add("No governance records present - changes may lack provenance tracking");
        }

        for (PageArtifactDocument.GovernanceRecord record : governanceRecords) {
            if (record.lineage() == null) {
                errors.add("Governance record missing lineage information for artifact: " + record.artifactId());
            }
            if (record.artifactId() == null || record.artifactId().isBlank()) {
                errors.add("Governance record missing artifactId");
            }
        }
    }

    /**
     * Validation result containing errors and warnings.
     */
    public record ValidationResult(
            boolean valid,
            List<String> errors,
            List<String> warnings
    ) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public String getSummary() {
            return "ValidationResult{valid=" + valid + ", errors=" + errors.size() + ", warnings=" + warnings.size() + "}";
        }
    }
}
