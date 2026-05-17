package com.ghatana.yappc.services.artifact;

import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.artifact.EdgeResolutionRecordDto;
import com.ghatana.yappc.domain.artifact.ResidualIslandDto;
import com.ghatana.yappc.domain.artifact.UnresolvedGraphEdgeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Centralized validator for artifact graph integrity, scope consistency, and residual fidelity
 * @doc.layer service
 * @doc.pattern Validator
 *
 * P0: Centralizes graph validation logic previously scattered in controllers.
 * Validates node/edge integrity, scope consistency, residual payload completeness,
 * provenance, confidence ranges, and snapshot consistency.
 */
public final class ArtifactGraphValidator {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGraphValidator.class);

    private static final double MIN_CONFIDENCE = 0.0;
    private static final double MAX_CONFIDENCE = 1.0;
    private static final int MAX_ID_LENGTH = 256;
    private static final int MAX_TYPE_LENGTH = 128;
    private static final Set<String> VALID_PROVENANCE = Set.of("exact", "inferred", "synthesized", "manual", "assumed");

    private ArtifactGraphValidator() {
        // Utility class - prevent instantiation
    }

    /**
     * Comprehensive validation result containing all validation errors and warnings.
     */
    public record ValidationResult(
        boolean valid,
        List<ValidationError> errors,
        List<ValidationWarning> warnings
    ) {
        public ValidationResult {
            errors = errors != null ? List.copyOf(errors) : List.of();
            warnings = warnings != null ? List.copyOf(warnings) : List.of();
        }
    }

    public record ValidationError(
        String code,
        String message,
        String field,
        String entityId
    ) {}

    public record ValidationWarning(
        String code,
        String message,
        String field,
        String entityId
    ) {}

    /**
     * Validates a complete graph ingest request including nodes, edges, and residuals.
     *
     * @param request the ingest request to validate
     * @param scope the expected scope (tenant/workspace/project)
     * @return validation result with errors and warnings
     */
    public static ValidationResult validateIngestRequest(
            ArtifactGraphIngestRequest request,
            ArtifactRequestScope scope) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(scope, "scope must not be null");

        List<ValidationError> errors = new ArrayList<>();
        List<ValidationWarning> warnings = new ArrayList<>();

        // Validate scope consistency
        validateScopeConsistency(request, scope, errors);

        // Build node ID set for edge target validation
        Set<String> nodeIds = new HashSet<>();
        Set<String> duplicateNodeIds = new HashSet<>();

        // Validate nodes
        if (request.nodes() == null || request.nodes().isEmpty()) {
            errors.add(new ValidationError(
                "EMPTY_NODE_LIST",
                "Graph ingest request must contain at least one node",
                "nodes",
                null
            ));
        } else {
            for (ArtifactNodeDto node : request.nodes()) {
                validateNode(node, errors, warnings);
                if (node != null && node.id() != null) {
                    if (!nodeIds.add(node.id())) {
                        duplicateNodeIds.add(node.id());
                    }
                }
            }
        }

        if (!duplicateNodeIds.isEmpty()) {
            for (String duplicateId : duplicateNodeIds) {
                errors.add(new ValidationError(
                    "DUPLICATE_NODE_ID",
                    "Duplicate node ID found in graph: " + duplicateId,
                    "nodes",
                    duplicateId
                ));
            }
        }

        // Validate edges
        if (request.edges() != null) {
            for (ArtifactEdgeDto edge : request.edges()) {
                validateEdge(edge, nodeIds, errors, warnings);
            }
        }

        // Validate unresolved edges structure
        if (request.unresolvedEdges() != null) {
            validateUnresolvedEdges(request.unresolvedEdges(), nodeIds, errors, warnings);
        }

        // Validate edge resolution records
        if (request.edgeResolutionRecords() != null) {
            validateEdgeResolutionRecords(request.edgeResolutionRecords(), errors, warnings);
        }

        // Validate residual islands
        if (request.residualIslands() != null) {
            for (ResidualIslandDto residual : request.residualIslands()) {
                validateResidualIsland(residual, scope, errors, warnings);
            }
        }

        // Validate snapshot metadata
        validateSnapshotMetadata(request, errors, warnings);

        boolean valid = errors.isEmpty();
        if (!valid) {
            log.warn("Graph validation failed with {} errors, {} warnings", errors.size(), warnings.size());
        } else if (!warnings.isEmpty()) {
            log.info("Graph validation passed with {} warnings", warnings.size());
        }

        return new ValidationResult(valid, errors, warnings);
    }

    /**
     * Validates that request scope matches the expected scope.
     */
    private static void validateScopeConsistency(
            ArtifactGraphIngestRequest request,
            ArtifactRequestScope scope,
            List<ValidationError> errors) {

        if (request.tenantId() != null && !request.tenantId().equals(scope.tenantId())) {
            errors.add(new ValidationError(
                "SCOPE_MISMATCH_TENANT",
                "Request tenantId " + request.tenantId() + " does not match scope " + scope.tenantId(),
                "tenantId",
                null
            ));
        }

        if (request.projectId() != null && !request.projectId().equals(scope.projectId())) {
            errors.add(new ValidationError(
                "SCOPE_MISMATCH_PROJECT",
                "Request projectId " + request.projectId() + " does not match scope " + scope.projectId(),
                "projectId",
                null
            ));
        }
    }

    /**
     * Validates a single artifact node.
     */
    private static void validateNode(
            ArtifactNodeDto node,
            List<ValidationError> errors,
            List<ValidationWarning> warnings) {

        if (node == null) {
            errors.add(new ValidationError(
                "NULL_NODE",
                "Null node found in nodes list",
                "nodes",
                null
            ));
            return;
        }

        // Validate ID
        if (node.id() == null || node.id().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_NODE_ID",
                "Node is missing required 'id' field",
                "id",
                null
            ));
        } else if (node.id().length() > MAX_ID_LENGTH) {
            errors.add(new ValidationError(
                "NODE_ID_TOO_LONG",
                "Node ID exceeds maximum length of " + MAX_ID_LENGTH,
                "id",
                node.id()
            ));
        }

        // Validate type
        if (node.type() == null || node.type().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_NODE_TYPE",
                "Node is missing required 'type' field",
                "type",
                node.id()
            ));
        } else if (node.type().length() > MAX_TYPE_LENGTH) {
            warnings.add(new ValidationWarning(
                "NODE_TYPE_LONG",
                "Node type exceeds " + MAX_TYPE_LENGTH + " characters",
                "type",
                node.id()
            ));
        }

        // Validate name
        if (node.name() == null || node.name().isBlank()) {
            warnings.add(new ValidationWarning(
                "MISSING_NODE_NAME",
                "Node is missing 'name' field",
                "name",
                node.id()
            ));
        }

        // Validate confidence range
        if (node.confidence() != null) {
            if (node.confidence() < MIN_CONFIDENCE || node.confidence() > MAX_CONFIDENCE) {
                errors.add(new ValidationError(
                    "INVALID_CONFIDENCE_RANGE",
                    "Node confidence " + node.confidence() + " is outside valid range [0.0, 1.0]",
                    "confidence",
                    node.id()
                ));
            }
            if (node.confidence() < 0.5) {
                warnings.add(new ValidationWarning(
                    "LOW_CONFIDENCE",
                    "Node has low confidence: " + node.confidence(),
                    "confidence",
                    node.id()
                ));
            }
        }

        // Validate source location if present
        if (node.sourceLocation() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> location = node.sourceLocation() instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : null;
            if (location != null) {
                Object startLine = location.get("startLine");
                Object endLine = location.get("endLine");
                if (startLine instanceof Number && endLine instanceof Number) {
                    int start = ((Number) startLine).intValue();
                    int end = ((Number) endLine).intValue();
                    if (start > end) {
                        errors.add(new ValidationError(
                            "INVALID_SOURCE_SPAN",
                            "Source location startLine " + start + " is greater than endLine " + end,
                            "sourceLocation",
                            node.id()
                        ));
                    }
                }
            }
        }

        // P0: Validate provenance field
        if (node.provenance() == null || node.provenance().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_PROVENANCE",
                "Node is missing required 'provenance' field - extraction source must be tracked",
                "provenance",
                node.id()
            ));
        } else {
            String normalizedProvenance = node.provenance().toLowerCase();
            if (!VALID_PROVENANCE.contains(normalizedProvenance)) {
                warnings.add(new ValidationWarning(
                    "UNKNOWN_PROVENANCE",
                    "Node provenance '" + node.provenance() + "' is not in canonical set",
                    "provenance",
                    node.id()
                ));
            }

            if (isSyntheticProvenance(normalizedProvenance)
                && (node.properties() == null || !node.properties().containsKey("syntheticReason"))) {
                errors.add(new ValidationError(
                    "MISSING_SYNTHETIC_REASON",
                    "Synthetic/inferred node must include properties.syntheticReason",
                    "properties.syntheticReason",
                    node.id()
                ));
            }
        }

        if (node.extractorId() == null || node.extractorId().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_EXTRACTOR_ID",
                "Node is missing required extractorId metadata",
                "extractorId",
                node.id()
            ));
        }

        if (node.extractorVersion() == null || node.extractorVersion().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_EXTRACTOR_VERSION",
                "Node is missing required extractorVersion metadata",
                "extractorVersion",
                node.id()
            ));
        }

        if (node.sourceRef() == null || node.sourceRef().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_SOURCE_REF",
                "Node is missing required sourceRef",
                "sourceRef",
                node.id()
            ));
        } else if (!node.sourceRef().contains(":")) {
            warnings.add(new ValidationWarning(
                "NON_DETERMINISTIC_SOURCE_REF",
                "sourceRef should be deterministic and URI-like",
                "sourceRef",
                node.id()
            ));
        }

        if (node.symbolRef() == null || node.symbolRef().isBlank()) {
            warnings.add(new ValidationWarning(
                "MISSING_SYMBOL_REF",
                "Node is missing symbolRef; symbol resolution quality may degrade",
                "symbolRef",
                node.id()
            ));
        } else if (!node.symbolRef().contains("#") || !node.symbolRef().contains(":")) {
            warnings.add(new ValidationWarning(
                "INVALID_SYMBOL_REF_FORMAT",
                "symbolRef should follow 'path#kind:name' format",
                "symbolRef",
                node.id()
            ));
        }

        if (node.privacySecurityFlags() != null) {
            for (String flag : node.privacySecurityFlags()) {
                if (flag == null || flag.isBlank()) {
                    errors.add(new ValidationError(
                        "INVALID_PRIVACY_FLAG",
                        "privacySecurityFlags must not contain null/blank entries",
                        "privacySecurityFlags",
                        node.id()
                    ));
                } else if (!flag.matches("[A-Z0-9_\\-]+")) {
                    warnings.add(new ValidationWarning(
                        "NON_CANONICAL_PRIVACY_FLAG",
                        "privacy/security flag should be uppercase canonical token",
                        "privacySecurityFlags",
                        node.id()
                    ));
                }
            }
        }
    }

    /**
     * Validates a single artifact edge.
     */
    private static void validateEdge(
            ArtifactEdgeDto edge,
            Set<String> nodeIds,
            List<ValidationError> errors,
            List<ValidationWarning> warnings) {

        if (edge == null) {
            errors.add(new ValidationError(
                "NULL_EDGE",
                "Null edge found in edges list",
                "edges",
                null
            ));
            return;
        }

        // Validate relationship type
        if (edge.relationshipType() == null || edge.relationshipType().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_RELATIONSHIP_TYPE",
                "Edge is missing required 'relationshipType' field",
                "relationshipType",
                edge.edgeId()
            ));
        } else if (!edge.relationshipType().matches("[A-Z][A-Z0-9_\\-]*")) {
            warnings.add(new ValidationWarning(
                "NON_CANONICAL_RELATIONSHIP_TYPE",
                "relationshipType should be uppercase canonical token",
                "relationshipType",
                edge.edgeId()
            ));
        }

        // Validate source node exists
        if (edge.sourceNodeId() == null || edge.sourceNodeId().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_EDGE_SOURCE",
                "Edge is missing required 'sourceNodeId' field",
                "sourceNodeId",
                edge.edgeId()
            ));
        } else if (!nodeIds.contains(edge.sourceNodeId())) {
            errors.add(new ValidationError(
                "EDGE_SOURCE_NOT_FOUND",
                "Edge source node '" + edge.sourceNodeId() + "' not found in nodes. " +
                    "Unresolved references belong in 'unresolvedEdges', not 'edges'.",
                "sourceNodeId",
                edge.edgeId()
            ));
        }

        // Validate target node exists
        if (edge.targetNodeId() == null || edge.targetNodeId().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_EDGE_TARGET",
                "Edge is missing required 'targetNodeId' field",
                "targetNodeId",
                edge.edgeId()
            ));
        } else if (!nodeIds.contains(edge.targetNodeId())) {
            errors.add(new ValidationError(
                "EDGE_TARGET_NOT_FOUND",
                "Edge target node '" + edge.targetNodeId() + "' not found in nodes. " +
                    "Unresolved references belong in 'unresolvedEdges', not 'edges'.",
                "targetNodeId",
                edge.edgeId()
            ));
        }

        // Validate confidence range
        if (edge.confidence() != null) {
            if (edge.confidence() < MIN_CONFIDENCE || edge.confidence() > MAX_CONFIDENCE) {
                errors.add(new ValidationError(
                    "INVALID_EDGE_CONFIDENCE",
                    "Edge confidence " + edge.confidence() + " is outside valid range [0.0, 1.0]",
                    "confidence",
                    edge.edgeId()
                ));
            }
        }
    }

    /**
     * Validates unresolved edge records.
     */
        private static void validateUnresolvedEdges(
            List<UnresolvedGraphEdgeDto> unresolvedEdges,
            Set<String> nodeIds,
            List<ValidationError> errors,
            List<ValidationWarning> warnings) {

        Set<String> unresolvedEdgeIds = new HashSet<>();

        for (UnresolvedGraphEdgeDto edge : unresolvedEdges) {
            if (edge == null) {
                continue;
            }

            String id = edge.id();
            String sourceNodeId = edge.sourceNodeId();
            String targetRef = edge.targetRef();
            String relationshipType = edge.relationshipType();

            if (id == null || id.isBlank()) {
                warnings.add(new ValidationWarning(
                    "MISSING_UNRESOLVED_EDGE_ID",
                    "Unresolved edge is missing 'id' field - will be auto-generated",
                    "id",
                    null
                ));
            } else if (!unresolvedEdgeIds.add(id)) {
                errors.add(new ValidationError(
                    "DUPLICATE_UNRESOLVED_EDGE_ID",
                    "Duplicate unresolved edge ID: " + id,
                    "id",
                    id
                ));
            }

            if (sourceNodeId == null || sourceNodeId.isBlank()) {
                errors.add(new ValidationError(
                    "MISSING_UNRESOLVED_SOURCE",
                    "Unresolved edge is missing 'sourceNodeId' field",
                    "sourceNodeId",
                    id
                ));
            } else if (!nodeIds.contains(sourceNodeId)) {
                errors.add(new ValidationError(
                    "UNRESOLVED_SOURCE_NOT_FOUND",
                    "Unresolved edge source node '" + sourceNodeId + "' not found in nodes",
                    "sourceNodeId",
                    id
                ));
            }

            if (targetRef == null || targetRef.isBlank()) {
                errors.add(new ValidationError(
                    "MISSING_UNRESOLVED_TARGET_REF",
                    "Unresolved edge is missing 'targetRef' field",
                    "targetRef",
                    id
                ));
            }

            if (relationshipType == null || relationshipType.isBlank()) {
                errors.add(new ValidationError(
                    "MISSING_UNRESOLVED_RELATIONSHIP_TYPE",
                    "Unresolved edge is missing required 'relationshipType' field.",
                    "relationshipType",
                    id
                ));
            }
        }
    }

    /**
     * Validates edge resolution records.
     */
    private static void validateEdgeResolutionRecords(
            List<EdgeResolutionRecordDto> resolutionRecords,
            List<ValidationError> errors,
            List<ValidationWarning> warnings) {

        for (EdgeResolutionRecordDto record : resolutionRecords) {
            if (record == null) {
                continue;
            }

            String id = record.id();
            String unresolvedEdgeId = record.unresolvedEdgeId();
            String status = record.status();

            if (unresolvedEdgeId == null || unresolvedEdgeId.isBlank()) {
                errors.add(new ValidationError(
                    "MISSING_RESOLUTION_EDGE_REF",
                    "Edge resolution record is missing 'unresolvedEdgeId' field",
                    "unresolvedEdgeId",
                    id
                ));
            }

            if (status == null || status.isBlank()) {
                errors.add(new ValidationError(
                    "MISSING_RESOLUTION_STATUS",
                    "Edge resolution record is missing 'status' field",
                    "status",
                    id
                ));
            } else {
                Set<String> validStatuses = Set.of("PENDING", "RESOLVED", "AMBIGUOUS", "FAILED", "IGNORED");
                if (!validStatuses.contains(status.toUpperCase())) {
                    warnings.add(new ValidationWarning(
                        "UNKNOWN_RESOLUTION_STATUS",
                        "Unknown resolution status: " + status,
                        "status",
                        id
                    ));
                }
            }
        }
    }

    /**
     * Validates a residual island for complete payload fidelity.
     */
    private static void validateResidualIsland(
            ResidualIslandDto residual,
            ArtifactRequestScope scope,
            List<ValidationError> errors,
            List<ValidationWarning> warnings) {

        if (residual == null) {
            errors.add(new ValidationError(
                "NULL_RESIDUAL",
                "Null residual island found in residuals list",
                "residualIslands",
                null
            ));
            return;
        }

        // Validate ID
        if (residual.id() == null || residual.id().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_RESIDUAL_ID",
                "Residual island is missing required 'id' field",
                "id",
                null
            ));
        }

        // Validate island type
        if (residual.islandType() == null || residual.islandType().isBlank()) {
            warnings.add(new ValidationWarning(
                "MISSING_ISLAND_TYPE",
                "Residual island is missing 'islandType' field - will default to 'unknown'",
                "islandType",
                residual.id()
            ));
        }

        // P0: Validate originalSource is present (critical for round-trip fidelity)
        if (residual.originalSource() == null || residual.originalSource().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_ORIGINAL_SOURCE",
                "Residual island is missing required 'originalSource' field - original source code is required for round-trip fidelity",
                "originalSource",
                residual.id()
            ));
        }

        // P0: Validate sourceSpan is present (critical for round-trip fidelity)
        if (residual.sourceSpan() == null || residual.sourceSpan().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_SOURCE_SPAN",
                "Residual island is missing required 'sourceSpan' field - original source location is required for round-trip fidelity",
                "sourceSpan",
                residual.id()
            ));
        }

        // P0: Validate structured sourceLocation as canonical location payload
        if (residual.sourceLocation() == null || residual.sourceLocation().filePath() == null || residual.sourceLocation().filePath().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_SOURCE_LOCATION",
                "Residual island is missing required structured 'sourceLocation' with filePath",
                "sourceLocation",
                residual.id()
            ));
        } else {
            int startLine = residual.sourceLocation().startLine();
            int startColumn = residual.sourceLocation().startColumn();
            int endLine = residual.sourceLocation().endLine();
            int endColumn = residual.sourceLocation().endColumn();
            if (startLine < 0 || startColumn < 0 || endLine < 0 || endColumn < 0) {
                errors.add(new ValidationError(
                    "INVALID_SOURCE_LOCATION_NEGATIVE",
                    "Residual sourceLocation line/column values must be non-negative",
                    "sourceLocation",
                    residual.id()
                ));
            }
            if (endLine < startLine || (endLine == startLine && endColumn < startColumn)) {
                errors.add(new ValidationError(
                    "INVALID_SOURCE_LOCATION_RANGE",
                    "Residual sourceLocation end position must be >= start position",
                    "sourceLocation",
                    residual.id()
                ));
            }
        }

        // P0: Require checksum (affects integrity verification)
        if (residual.checksum() == null || residual.checksum().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_RESIDUAL_CHECKSUM",
                "Residual island is missing required 'checksum' - content integrity verification is required",
                "checksum",
                residual.id()
            ));
        }

        // P0: Require rawFragmentRef (affects content retrieval)
        if (residual.rawFragmentRef() == null || residual.rawFragmentRef().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_RAW_FRAGMENT_REF",
                "Residual island is missing required 'rawFragmentRef' - content reference is required for retrieval",
                "rawFragmentRef",
                residual.id()
            ));
        }

        // Validate confidence range
        if (residual.confidence() != null) {
            if (residual.confidence() < MIN_CONFIDENCE || residual.confidence() > MAX_CONFIDENCE) {
                errors.add(new ValidationError(
                    "INVALID_RESIDUAL_CONFIDENCE",
                    "Residual confidence " + residual.confidence() + " is outside valid range [0.0, 1.0]",
                    "confidence",
                    residual.id()
                ));
            }
        }

        // Validate risk score range
        if (residual.riskScore() != null) {
            if (residual.riskScore() < MIN_CONFIDENCE || residual.riskScore() > MAX_CONFIDENCE) {
                errors.add(new ValidationError(
                    "INVALID_RISK_SCORE",
                    "Residual risk score " + residual.riskScore() + " is outside valid range [0.0, 1.0]",
                    "riskScore",
                    residual.id()
                ));
            }
        }

        // Flag if review is required but no reason given
        if (residual.reviewRequired() != null && residual.reviewRequired()) {
            if (residual.reason() == null || residual.reason().isBlank()) {
                warnings.add(new ValidationWarning(
                    "REVIEW_REQUIRED_NO_REASON",
                    "Residual is marked for review but no reason is provided",
                    "reason",
                    residual.id()
                ));
            }
        }

        if (residual.tenantId() != null && !residual.tenantId().isBlank() && !residual.tenantId().equals(scope.tenantId())) {
            errors.add(new ValidationError(
                "RESIDUAL_SCOPE_MISMATCH_TENANT",
                "Residual tenantId does not match ingest scope tenantId",
                "tenantId",
                residual.id()
            ));
        }
        if (residual.projectId() != null && !residual.projectId().isBlank() && !residual.projectId().equals(scope.projectId())) {
            errors.add(new ValidationError(
                "RESIDUAL_SCOPE_MISMATCH_PROJECT",
                "Residual projectId does not match ingest scope projectId",
                "projectId",
                residual.id()
            ));
        }
        if (scope.workspaceId() != null && residual.workspaceId() != null
            && !residual.workspaceId().isBlank() && !residual.workspaceId().equals(scope.workspaceId())) {
            errors.add(new ValidationError(
                "RESIDUAL_SCOPE_MISMATCH_WORKSPACE",
                "Residual workspaceId does not match ingest scope workspaceId",
                "workspaceId",
                residual.id()
            ));
        }
    }

    private static boolean isSyntheticProvenance(String normalizedProvenance) {
        return "synthesized".equals(normalizedProvenance) || "inferred".equals(normalizedProvenance) || "assumed".equals(normalizedProvenance);
    }

    /**
     * Validates snapshot metadata consistency.
     */
    private static void validateSnapshotMetadata(
            ArtifactGraphIngestRequest request,
            List<ValidationError> errors,
            List<ValidationWarning> warnings) {

        // Check for snapshot ID consistency
        if (request.snapshotId() != null && request.snapshotRef() != null) {
            if (!request.snapshotId().equals(request.snapshotRef())) {
                warnings.add(new ValidationWarning(
                    "SNAPSHOT_ID_REF_MISMATCH",
                    "snapshotId and snapshotRef have different values",
                    "snapshotId/snapshotRef",
                    null
                ));
            }
        }

        // Warn if no snapshot reference is provided
        if ((request.snapshotId() == null || request.snapshotId().isBlank()) &&
            (request.snapshotRef() == null || request.snapshotRef().isBlank())) {
            warnings.add(new ValidationWarning(
                "MISSING_SNAPSHOT_REF",
                "No snapshotId or snapshotRef provided - graph nodes may not be deterministic",
                "snapshotId",
                null
            ));
        }

        // Validate content checksum if present
        if (request.contentChecksum() != null && request.contentChecksum().isBlank()) {
            warnings.add(new ValidationWarning(
                "EMPTY_CONTENT_CHECKSUM",
                "contentChecksum is provided but empty",
                "contentChecksum",
                null
            ));
        }
    }

    /**
     * Validates that all residual references in nodes have corresponding full residual records.
     *
     * @param nodes the artifact nodes
     * @param residuals the residual islands
     * @return validation result
     */
    public static ValidationResult validateResidualReferences(
            List<ArtifactNodeDto> nodes,
            List<ResidualIslandDto> residuals) {

        List<ValidationError> errors = new ArrayList<>();
        List<ValidationWarning> warnings = new ArrayList<>();

        Set<String> residualIds = residuals != null
            ? residuals.stream().filter(r -> r != null).map(ResidualIslandDto::id).filter(Objects::nonNull).collect(HashSet::new, Set::add, Set::addAll)
            : new HashSet<>();

        if (nodes != null) {
            for (ArtifactNodeDto node : nodes) {
                if (node == null || node.residualFragmentIds() == null) {
                    continue;
                }

                for (String residualRef : node.residualFragmentIds()) {
                    if (residualRef != null && !residualRef.isBlank() && !residualIds.contains(residualRef)) {
                        errors.add(new ValidationError(
                            "ORPHANED_RESIDUAL_REF",
                            "Node references residual '" + residualRef + "' but no full residual record is present",
                            "residualFragmentIds",
                            node.id()
                        ));
                    }
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
}
