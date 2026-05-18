package com.ghatana.yappc.services.artifact;

import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.artifact.SemanticModelDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Centralized validator for semantic model integrity and graph node reference enforcement
 * @doc.layer service
 * @doc.pattern Validator
 *
 * P0: Validates semantic model elements for governance compliance, graph node ID references,
 * provenance normalization, and confidence ranges. Ensures semantic models maintain referential
 * integrity with the artifact graph.
 */
public final class SemanticModelValidator {

    private static final Logger log = LoggerFactory.getLogger(SemanticModelValidator.class);

    private static final double MIN_CONFIDENCE = 0.0;
    private static final double MAX_CONFIDENCE = 1.0;
    private static final Set<String> VALID_PROVENANCE = Set.of("exact", "inferred", "synthesized", "manual", "assumed");

    private SemanticModelValidator() {
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
     * Validates a list of semantic models against graph nodes for referential integrity.
     *
     * @param models the semantic models to validate
     * @param graphNodes the artifact graph nodes for reference validation
     * @return validation result with errors and warnings
     */
    public static ValidationResult validateSemanticModels(
            List<SemanticModelDto> models,
            List<ArtifactNodeDto> graphNodes) {

        List<ValidationError> errors = new ArrayList<>();
        List<ValidationWarning> warnings = new ArrayList<>();

        // Build graph node ID set for reference validation
        Set<String> graphNodeIds = new HashSet<>();
        if (graphNodes != null) {
            for (ArtifactNodeDto node : graphNodes) {
                if (node != null && node.id() != null) {
                    graphNodeIds.add(node.id());
                }
            }
        }

        // Validate each semantic model
        if (models == null || models.isEmpty()) {
            warnings.add(new ValidationWarning(
                "EMPTY_SEMANTIC_MODELS",
                "No semantic models provided for validation",
                "semanticModels",
                null
            ));
        } else {
            Set<String> modelIds = new HashSet<>();
            Set<String> duplicateModelIds = new HashSet<>();

            for (SemanticModelDto model : models) {
                validateModel(model, graphNodeIds, errors, warnings);
                
                if (model != null && model.id() != null) {
                    if (!modelIds.add(model.id())) {
                        duplicateModelIds.add(model.id());
                    }
                }
            }

            if (!duplicateModelIds.isEmpty()) {
                for (String duplicateId : duplicateModelIds) {
                    errors.add(new ValidationError(
                        "DUPLICATE_SEMANTIC_MODEL_ID",
                        "Duplicate semantic model ID found: " + duplicateId,
                        "id",
                        duplicateId
                    ));
                }
            }
        }

        boolean valid = errors.isEmpty();
        if (!valid) {
            log.warn("Semantic model validation failed with {} errors, {} warnings", errors.size(), warnings.size());
        } else if (!warnings.isEmpty()) {
            log.info("Semantic model validation passed with {} warnings", warnings.size());
        }

        return new ValidationResult(valid, errors, warnings);
    }

    /**
     * Validates a single semantic model.
     */
    private static void validateModel(
            SemanticModelDto model,
            Set<String> graphNodeIds,
            List<ValidationError> errors,
            List<ValidationWarning> warnings) {

        if (model == null) {
            errors.add(new ValidationError(
                "NULL_SEMANTIC_MODEL",
                "Null semantic model found in models list",
                "semanticModels",
                null
            ));
            return;
        }

        // Validate ID
        if (model.id() == null || model.id().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_MODEL_ID",
                "Semantic model is missing required 'id' field",
                "id",
                null
            ));
        }

        // Validate element ID
        if (model.elementId() == null || model.elementId().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_ELEMENT_ID",
                "Semantic model is missing required 'elementId' field",
                "elementId",
                model.id()
            ));
        }

        // Validate element type
        if (model.elementType() == null || model.elementType().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_ELEMENT_TYPE",
                "Semantic model is missing required 'elementType' field",
                "elementType",
                model.id()
            ));
        }

        // Validate name
        if (model.name() == null || model.name().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_MODEL_NAME",
                "Semantic model is missing required 'name' field",
                "name",
                model.id()
            ));
        }

        // Validate file path
        if (model.filePath() == null || model.filePath().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_FILE_PATH",
                "Semantic model is missing required 'filePath' field",
                "filePath",
                model.id()
            ));
        }

        // Validate snapshot ID
        if (model.snapshotId() == null || model.snapshotId().isBlank()) {
            errors.add(new ValidationError(
                "MISSING_SNAPSHOT_ID",
                "Semantic model is missing required 'snapshotId' field",
                "snapshotId",
                model.id()
            ));
        }

        // Validate provenance - it's an enum, not a string
        if (model.provenance() == null) {
            errors.add(new ValidationError(
                "MISSING_PROVENANCE",
                "Semantic model is missing required 'provenance' field",
                "provenance",
                model.id()
            ));
        } else {
            // Provenance is an enum, no need to normalize
            if (!VALID_PROVENANCE.contains(model.provenance().name().toLowerCase())) {
                warnings.add(new ValidationWarning(
                    "UNKNOWN_PROVENANCE",
                    "Semantic model provenance '" + model.provenance() + "' is not in canonical set",
                    "provenance",
                    model.id()
                ));
            }

            if (isSyntheticProvenance(model.provenance().name().toLowerCase())
                && (model.syntheticReason() == null || model.syntheticReason().isBlank())) {
                errors.add(new ValidationError(
                    "MISSING_SYNTHETIC_REASON",
                    "Synthetic/inferred semantic model must include syntheticReason",
                    "syntheticReason",
                    model.id()
                ));
            }
        }

        // Validate confidence range
        if (model.confidence() != null) {
            if (model.confidence() < MIN_CONFIDENCE || model.confidence() > MAX_CONFIDENCE) {
                errors.add(new ValidationError(
                    "INVALID_CONFIDENCE_RANGE",
                    "Semantic model confidence " + model.confidence() + " is outside valid range [0.0, 1.0]",
                    "confidence",
                    model.id()
                ));
            }
            if (model.confidence() < 0.5) {
                warnings.add(new ValidationWarning(
                    "LOW_CONFIDENCE",
                    "Semantic model has low confidence: " + model.confidence(),
                    "confidence",
                    model.id()
                ));
            }
        }

        // P0: Validate graph node ID references
        if (model.graphNodeIds() != null && !model.graphNodeIds().isEmpty()) {
            Set<String> missingNodeIds = new HashSet<>();
            for (String nodeId : model.graphNodeIds()) {
                if (nodeId != null && !nodeId.isBlank() && !graphNodeIds.contains(nodeId)) {
                    missingNodeIds.add(nodeId);
                }
            }
            if (!missingNodeIds.isEmpty()) {
                errors.add(new ValidationError(
                    "ORPHANED_GRAPH_NODE_REFERENCES",
                    "Semantic model references graph node IDs that do not exist: " + missingNodeIds,
                    "graphNodeIds",
                    model.id()
                ));
            }
        }

        // Validate residual island ID references (if any)
        if (model.residualIslandIds() != null && !model.residualIslandIds().isEmpty()) {
            for (String islandId : model.residualIslandIds()) {
                if (islandId == null || islandId.isBlank()) {
                    errors.add(new ValidationError(
                        "INVALID_RESIDUAL_ISLAND_ID",
                        "Semantic model residualIslandIds contains null/blank entry",
                        "residualIslandIds",
                        model.id()
                    ));
                }
            }
        }

        // Validate extractor metadata
        if (model.extractorId() == null || model.extractorId().isBlank()) {
            warnings.add(new ValidationWarning(
                "MISSING_EXTRACTOR_ID",
                "Semantic model is missing extractorId metadata",
                "extractorId",
                model.id()
            ));
        }

        if (model.extractorVersion() == null || model.extractorVersion().isBlank()) {
            warnings.add(new ValidationWarning(
                "MISSING_EXTRACTOR_VERSION",
                "Semantic model is missing extractorVersion metadata",
                "extractorVersion",
                model.id()
            ));
        }

        // Validate source and symbol references
        if (model.sourceRef() == null || model.sourceRef().isBlank()) {
            warnings.add(new ValidationWarning(
                "MISSING_SOURCE_REF",
                "Semantic model is missing sourceRef",
                "sourceRef",
                model.id()
            ));
        }

        if (model.symbolRef() == null || model.symbolRef().isBlank()) {
            warnings.add(new ValidationWarning(
                "MISSING_SYMBOL_REF",
                "Semantic model is missing symbolRef",
                "symbolRef",
                model.id()
            ));
        }

        // Flag if review is required but no reason given
        if (model.reviewRequired() != null && model.reviewRequired()) {
            if (model.reviewReason() == null || model.reviewReason().isBlank()) {
                warnings.add(new ValidationWarning(
                    "REVIEW_REQUIRED_NO_REASON",
                    "Semantic model is marked for review but no reason is provided",
                    "reviewReason",
                    model.id()
                ));
            }
        }

        // Validate security and privacy flags format
        if (model.securityFlags() != null) {
            for (String flag : model.securityFlags()) {
                if (flag == null || flag.isBlank()) {
                    errors.add(new ValidationError(
                        "INVALID_SECURITY_FLAG",
                        "securityFlags must not contain null/blank entries",
                        "securityFlags",
                        model.id()
                    ));
                } else if (!flag.matches("[A-Z0-9_\\-]+")) {
                    warnings.add(new ValidationWarning(
                        "NON_CANONICAL_SECURITY_FLAG",
                        "Security flag should be uppercase canonical token",
                        "securityFlags",
                        model.id()
                    ));
                }
            }
        }

        if (model.privacyFlags() != null) {
            for (String flag : model.privacyFlags()) {
                if (flag == null || flag.isBlank()) {
                    errors.add(new ValidationError(
                        "INVALID_PRIVACY_FLAG",
                        "privacyFlags must not contain null/blank entries",
                        "privacyFlags",
                        model.id()
                    ));
                } else if (!flag.matches("[A-Z0-9_\\-]+")) {
                    warnings.add(new ValidationWarning(
                        "NON_CANONICAL_PRIVACY_FLAG",
                        "Privacy flag should be uppercase canonical token",
                        "privacyFlags",
                        model.id()
                    ));
                }
            }
        }
    }

    private static boolean isSyntheticProvenance(String normalizedProvenance) {
        return "synthesized".equals(normalizedProvenance) || "inferred".equals(normalizedProvenance) || "assumed".equals(normalizedProvenance);
    }
}
