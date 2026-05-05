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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

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

        private static final Set<String> ALLOWED_ACTION_BINDING_TYPES = Set.of(
            "NAVIGATE",
            "SUBMIT",
            "OPEN_MODAL",
            "CUSTOM_EVENT"
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

    /**
     * Data classification severity order (least to most restrictive).
     * A node must not claim a classification lower than its containing document.
     */
    private static final Map<String, Integer> DATA_CLASSIFICATION_LEVEL;

    static {
        DATA_CLASSIFICATION_LEVEL = new HashMap<>();
        DATA_CLASSIFICATION_LEVEL.put("UNCLASSIFIED", 0);
        DATA_CLASSIFICATION_LEVEL.put("PUBLIC", 1);
        DATA_CLASSIFICATION_LEVEL.put("INTERNAL", 2);
        DATA_CLASSIFICATION_LEVEL.put("CONFIDENTIAL", 3);
        DATA_CLASSIFICATION_LEVEL.put("RESTRICTED", 4);
    }

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
        validateBuilderDocument(document.builderDocument(), document.dataClassification(), errors, warnings);

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
        validateGovernanceRecords(
            document.aiChangeRecords(),
            document.source(),
            document.artifactId(),
            document.documentId(),
            errors,
            warnings
        );

        LOG.debug("Validation complete: errors={}, warnings={}", errors.size(), warnings.size());

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private static void validateBuilderDocument(
            @NotNull Map<String, Object> builderDocument,
            String documentDataClassification,
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
            validateTreeShape(rootNodeList, nodesMap, errors, warnings);
            validateNodeDataClassificationPropagation(nodesMap, documentDataClassification, errors);
        }

        validateBuilderMetadata(builderDocument, documentDataClassification, errors);

        // Reject executable payloads (security concern, fail-closed)
        checkForExecutablePayloads(builderDocument, errors);
    }

    private static void validateBuilderMetadata(
            @NotNull Map<String, Object> builderDocument,
            String documentDataClassification,
            @NotNull List<String> errors
    ) {
        Object metadata = builderDocument.get("metadata");
        if (!(metadata instanceof Map<?, ?> metadataMap)) {
            return;
        }

        Object builderClassification = metadataMap.get("dataClassification");
        if (builderClassification instanceof String classification && !classification.isBlank()) {
            if (documentDataClassification == null || documentDataClassification.isBlank()) {
                errors.add("Document data_classification must be present when BuilderDocument.metadata.dataClassification is set");
                return;
            }

            if (!classification.equals(documentDataClassification)) {
                errors.add("BuilderDocument.metadata.dataClassification does not match document data_classification");
            }
        }
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
                continue;
            }

            validateContractRules(entry.getKey(), contract, nodeMap, errors);

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
        Set<String> referencedChildren = new HashSet<>();
        for (Map.Entry<String, Object> entry : nodesMap.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> nodeMap)) {
                continue;
            }

            Object slots = nodeMap.get("slots");
            if (!(slots instanceof Map<?, ?> slotsMap)) {
                continue;
            }

            for (Map.Entry<?, ?> slotEntry : slotsMap.entrySet()) {
                if (!(slotEntry.getKey() instanceof String slotName) || slotName.isBlank()) {
                    errors.add("BuilderDocument node '" + entry.getKey() + "' has invalid slot name");
                    continue;
                }
                if (!(slotEntry.getValue() instanceof List<?> childIds)) {
                    errors.add("BuilderDocument node '" + entry.getKey() + "' slot '" + slotName + "' must be a list");
                    continue;
                }

                for (Object childId : childIds) {
                    if (!(childId instanceof String childNodeId) || childNodeId.isBlank()) {
                        errors.add("BuilderDocument node '" + entry.getKey() + "' has non-string child reference");
                        continue;
                    }
                    if (!nodesMap.containsKey(childNodeId)) {
                        errors.add("BuilderDocument node '" + entry.getKey() + "' references missing child node '" + childNodeId + "'");
                        continue;
                    }
                    if (!referencedChildren.add(childNodeId)) {
                        errors.add("BuilderDocument child node '" + childNodeId + "' is referenced by multiple parents or slots");
                    }
                }
            }
        }
    }

    private static void validateContractRules(
            @NotNull String nodeId,
            @NotNull String contractName,
            @NotNull Map<?, ?> nodeMap,
            @NotNull List<String> errors
    ) {
        PageArtifactDesignSystemRegistry.ContractRule contractRule = PageArtifactDesignSystemRegistry.findContract(contractName)
                .orElse(null);
        if (contractRule == null) {
            errors.add("BuilderDocument node '" + nodeId + "' references unknown contract '" + contractName + "'");
            return;
        }

        Object props = nodeMap.get("props");
        if (props instanceof Map<?, ?> propsMap) {
            for (String requiredProp : contractRule.requiredProps()) {
                Object value = propsMap.get(requiredProp);
                if (value == null || (value instanceof String stringValue && stringValue.isBlank())) {
                    errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                            + "' missing required prop '" + requiredProp + "'");
                }
            }

            for (Map.Entry<String, PageArtifactDesignSystemRegistry.PropType> propRule : contractRule.propTypes().entrySet()) {
                Object value = propsMap.get(propRule.getKey());
                if (value == null) {
                    continue;
                }
                if (!matchesPropType(value, propRule.getValue())) {
                    errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                            + "' prop '" + propRule.getKey() + "' must be of type "
                            + propRule.getValue().name().toLowerCase());
                }
            }

            validateContractPolicy(nodeId, contractName, propsMap, errors);
        }

        Object slots = nodeMap.get("slots");
        if (slots instanceof Map<?, ?> slotsMap) {
            for (Object slotNameObj : slotsMap.keySet()) {
                if (!(slotNameObj instanceof String slotName) || slotName.isBlank()) {
                    continue;
                }
                if (!contractRule.allowedSlots().contains(slotName)) {
                    errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                            + "' does not allow slot '" + slotName + "'");
                }
            }
        }
    }

    private static boolean matchesPropType(
            @NotNull Object value,
            @NotNull PageArtifactDesignSystemRegistry.PropType expectedType
    ) {
        return switch (expectedType) {
            case STRING -> value instanceof String;
            case BOOLEAN -> value instanceof Boolean;
            case NUMBER -> value instanceof Number;
        };
    }

    private static void validateContractPolicy(
            @NotNull String nodeId,
            @NotNull String contractName,
            @NotNull Map<?, ?> propsMap,
            @NotNull List<String> errors
    ) {
        for (Map.Entry<?, ?> entry : propsMap.entrySet()) {
            if (!(entry.getKey() instanceof String propName)) {
                continue;
            }

            Object propValue = entry.getValue();
            if (propValue instanceof String stringValue) {
                String normalizedPropName = propName.toLowerCase();
                if (normalizedPropName.startsWith("on") && !normalizedPropName.equals("onevent")) {
                    errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                            + "' prop '" + propName + "' must not contain inline executable handlers");
                    continue;
                }

                if (containsExecutablePattern(stringValue)) {
                    errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                            + "' prop '" + propName + "' contains unsafe executable payload");
                }
            }

            if ("actionBinding".equals(propName) || "actionBindings".equals(propName)) {
                validateActionBindingProp(nodeId, contractName, propName, propValue, errors);
            }

            if ("dataBinding".equals(propName) || "dataBindings".equals(propName)) {
                validateDataBindingProp(nodeId, contractName, propName, propValue, errors);
            }
        }

        if ("Image".equals(contractName)) {
            Object src = propsMap.get("src");
            if (src instanceof String srcValue) {
                validateSrcScheme(nodeId, contractName, srcValue, errors);
            }
        } else {
            // Generalized src scheme check for any node that exposes an `src` prop —
            // even non-Image contracts must not accept javascript: or unsafe data URIs.
            Object src = propsMap.get("src");
            if (src instanceof String srcValue) {
                validateSrcScheme(nodeId, contractName, srcValue, errors);
            }
        }
    }

    private static void validateSrcScheme(
            @NotNull String nodeId,
            @NotNull String contractName,
            @NotNull String srcValue,
            @NotNull List<String> errors
    ) {
        String normalizedSrc = srcValue.trim().toLowerCase(Locale.ROOT);
        boolean safeDataImage = normalizedSrc.startsWith("data:image/");
        boolean safeRelative = normalizedSrc.startsWith("/");
        boolean safeHttp = normalizedSrc.startsWith("http://") || normalizedSrc.startsWith("https://");

        if (!safeDataImage && !safeRelative && !safeHttp) {
            errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                    + "' has unsupported or unsafe src scheme: '" + normalizedSrc.split(":")[0] + ":'");
        }
    }

    private static void validateActionBindingProp(
            @NotNull String nodeId,
            @NotNull String contractName,
            @NotNull String propName,
            Object propValue,
            @NotNull List<String> errors
    ) {
        if (propValue instanceof List<?> listValue) {
            for (Object value : listValue) {
                validateActionBindingObject(nodeId, contractName, propName, value, errors);
            }
            return;
        }

        validateActionBindingObject(nodeId, contractName, propName, propValue, errors);
    }

    private static void validateActionBindingObject(
            @NotNull String nodeId,
            @NotNull String contractName,
            @NotNull String propName,
            Object value,
            @NotNull List<String> errors
    ) {
        if (!(value instanceof Map<?, ?> bindingMap)) {
            errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                    + "' prop '" + propName + "' must be an object or list of objects");
            return;
        }

        Object type = bindingMap.get("type");
        if (!(type instanceof String typeValue) || typeValue.isBlank()) {
            errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                    + "' prop '" + propName + "' missing required field 'type'");
            return;
        }

        String normalizedType = typeValue.toUpperCase(Locale.ROOT);
        if (!ALLOWED_ACTION_BINDING_TYPES.contains(normalizedType)) {
            errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                    + "' prop '" + propName + "' has unsupported action binding type '" + typeValue + "'");
        }

        Object target = bindingMap.get("target");
        if (!(target instanceof String targetValue) || targetValue.isBlank()) {
            errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                    + "' prop '" + propName + "' missing required field 'target'");
        }
    }

    private static void validateDataBindingProp(
            @NotNull String nodeId,
            @NotNull String contractName,
            @NotNull String propName,
            Object propValue,
            @NotNull List<String> errors
    ) {
        if (propValue instanceof List<?> listValue) {
            for (Object value : listValue) {
                validateDataBindingObject(nodeId, contractName, propName, value, errors);
            }
            return;
        }

        validateDataBindingObject(nodeId, contractName, propName, propValue, errors);
    }

    private static void validateDataBindingObject(
            @NotNull String nodeId,
            @NotNull String contractName,
            @NotNull String propName,
            Object value,
            @NotNull List<String> errors
    ) {
        if (!(value instanceof Map<?, ?> bindingMap)) {
            errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                    + "' prop '" + propName + "' must be an object or list of objects");
            return;
        }

        Object source = bindingMap.get("source");
        if (!(source instanceof String sourceValue) || sourceValue.isBlank()) {
            errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                    + "' prop '" + propName + "' missing required field 'source'");
        }

        Object path = bindingMap.get("path");
        if (!(path instanceof String pathValue) || pathValue.isBlank()) {
            errors.add("BuilderDocument node '" + nodeId + "' contract '" + contractName
                    + "' prop '" + propName + "' missing required field 'path'");
        }
    }

    private static void validateTreeShape(
            @NotNull List<?> rootNodes,
            @NotNull Map<String, Object> nodesMap,
            @NotNull List<String> errors,
            @NotNull List<String> warnings
    ) {
        Set<String> reachable = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (Object rootNode : rootNodes) {
            if (rootNode instanceof String rootId && nodesMap.containsKey(rootId)) {
                traverseNode(rootId, nodesMap, reachable, visiting, errors);
            }
        }

        for (String nodeId : nodesMap.keySet()) {
            if (!reachable.contains(nodeId)) {
                errors.add("BuilderDocument contains orphan node not reachable from rootNodes: " + nodeId);
            }
        }

        if (reachable.isEmpty() && !nodesMap.isEmpty()) {
            warnings.add("BuilderDocument contains nodes but no reachable root graph");
        }
    }

    private static void traverseNode(
            @NotNull String nodeId,
            @NotNull Map<String, Object> nodesMap,
            @NotNull Set<String> reachable,
            @NotNull Set<String> visiting,
            @NotNull List<String> errors
    ) {
        if (reachable.contains(nodeId)) {
            return;
        }
        if (!visiting.add(nodeId)) {
            errors.add("BuilderDocument contains a component cycle involving node: " + nodeId);
            return;
        }

        Object rawNode = nodesMap.get(nodeId);
        if (rawNode instanceof Map<?, ?> nodeMap) {
            Object slots = nodeMap.get("slots");
            if (slots instanceof Map<?, ?> slotsMap) {
                for (Object childList : slotsMap.values()) {
                    if (childList instanceof List<?> childIds) {
                        for (Object childId : childIds) {
                            if (childId instanceof String childNodeId && nodesMap.containsKey(childNodeId)) {
                                traverseNode(childNodeId, nodesMap, reachable, visiting, errors);
                            }
                        }
                    }
                }
            }
        }

        visiting.remove(nodeId);
        reachable.add(nodeId);
    }

    private static void checkForExecutablePayloads(
            @NotNull Object currentValue,
            @NotNull List<String> errors
    ) {
        if (currentValue instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                checkForExecutablePayloads(value, errors);
            }
            return;
        }

        if (currentValue instanceof List<?> list) {
            for (Object value : list) {
                checkForExecutablePayloads(value, errors);
            }
            return;
        }

        if (currentValue instanceof String stringValue) {
            if (containsExecutablePattern(stringValue)) {
                errors.add("BuilderDocument contains potentially executable content and is rejected");
            }
        }
    }

    private static boolean containsExecutablePattern(@NotNull String value) {
        String lowered = value.toLowerCase();
        return lowered.contains("eval(")
                || lowered.contains("new function(")
                || lowered.contains("<script")
                || lowered.contains("javascript:")
                || lowered.contains("expression(");
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
            @NotNull String documentArtifactId,
            @NotNull String documentId,
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
            } else if (!documentArtifactId.equals(record.artifactId())) {
                errors.add("Governance record artifactId does not match document artifactId");
            }
            if (record.documentId() == null || record.documentId().isBlank()) {
                errors.add("Governance record missing documentId");
            } else if (!documentId.equals(record.documentId())) {
                errors.add("Governance record documentId does not match document documentId");
            }
        }
    }

    /**
     * Validates that no individual node's {@code props.dataClassification} under-declares
     * the sensitivity of the containing document. A node that processes or renders data
     * must claim at least the same classification level as the document-level declaration.
     * If a node's classification is lower than the document's, the node would present
     * higher-sensitivity data under a weaker constraint — which is an information-leakage risk.
     */
    private static void validateNodeDataClassificationPropagation(
            @NotNull Map<String, Object> nodesMap,
            String documentDataClassification,
            @NotNull List<String> errors
    ) {
        if (documentDataClassification == null || documentDataClassification.isBlank()) {
            return;
        }

        int documentLevel = DATA_CLASSIFICATION_LEVEL.getOrDefault(documentDataClassification, -1);
        if (documentLevel < 0) {
            return; // Unknown classification — structural validation will already flag this
        }

        for (Map.Entry<String, Object> entry : nodesMap.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> nodeMap)) {
                continue;
            }

            Object props = nodeMap.get("props");
            if (!(props instanceof Map<?, ?> propsMap)) {
                continue;
            }

            Object nodeClassificationObj = propsMap.get("dataClassification");
            if (!(nodeClassificationObj instanceof String nodeClassification) || nodeClassification.isBlank()) {
                continue;
            }

            int nodeLevel = DATA_CLASSIFICATION_LEVEL.getOrDefault(nodeClassification, -1);
            if (nodeLevel < 0) {
                // Unknown node-level classification — skip; contract prop type checks will surface this
                continue;
            }

            if (nodeLevel < documentLevel) {
                errors.add("BuilderDocument node '" + entry.getKey()
                        + "' declares dataClassification '" + nodeClassification
                        + "' which is less restrictive than the document-level classification '"
                        + documentDataClassification + "' — node classification must not under-declare sensitivity");
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
