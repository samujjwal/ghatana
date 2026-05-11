/**
 * Canvas Validation Service Implementation
 * 
 * Production-grade implementation of canvas document validation.
 * Validates canvas documents, nodes, and edges before persistence to ensure
 * data integrity and consistency.
 * 
 * @doc.type class
 * @doc.purpose Canvas validation implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.canvas;

import com.ghatana.yappc.api.CanvasDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Production-grade implementation of canvas validation service.
 */
public final class CanvasValidationServiceImpl implements CanvasValidationService {

    private static final Logger log = LoggerFactory.getLogger(CanvasValidationServiceImpl.class);

    @Override
    public CanvasValidationResult validate(CanvasDocument document) {
        log.info("Validating canvas document: id={}, projectId={}", document.id(), document.projectId());

        List<String> errors = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();

        // Validate document-level fields
        if (document.id() == null || document.id().isBlank()) {
            errors.add("Canvas document ID is required");
        }
        if (document.projectId() == null || document.projectId().isBlank()) {
            errors.add("Project ID is required");
        }
        if (document.workspaceId() == null || document.workspaceId().isBlank()) {
            errors.add("Workspace ID is required");
        }
        if (document.tenantId() == null || document.tenantId().isBlank()) {
            errors.add("Tenant ID is required");
        }
        if (document.version() == null || document.version().isBlank()) {
            errors.add("Document version is required");
        }

        // Validate nodes
        if (document.nodes() == null) {
            errors.add("Nodes list cannot be null");
        } else {
            for (CanvasDocument.CanvasNode node : document.nodes()) {
                CanvasValidationResult nodeResult = validateNode(node);
                if (!nodeResult.isValid()) {
                    errors.addAll(nodeResult.errors());
                }
                // Check for duplicate node IDs
                if (nodeIds.contains(node.id())) {
                    errors.add(String.format("Duplicate node ID: %s", node.id()));
                }
                nodeIds.add(node.id());
            }
        }

        // Validate edges
        if (document.edges() == null) {
            errors.add("Edges list cannot be null");
        } else {
            for (CanvasDocument.CanvasEdge edge : document.edges()) {
                CanvasValidationResult edgeResult = validateEdge(edge);
                if (!edgeResult.isValid()) {
                    errors.addAll(edgeResult.errors());
                }
                // Validate edge references existing nodes
                if (!nodeIds.contains(edge.sourceNodeId())) {
                    errors.add(String.format("Edge references non-existent source node: %s", edge.sourceNodeId()));
                }
                if (!nodeIds.contains(edge.targetNodeId())) {
                    errors.add(String.format("Edge references non-existent target node: %s", edge.targetNodeId()));
                }
            }
        }

        boolean isValid = errors.isEmpty();
        if (isValid) {
            log.info("Canvas document validation passed: id={}", document.id());
        } else {
            log.warn("Canvas document validation failed: id={}, errors={}", document.id(), errors);
        }

        return new CanvasValidationResult(isValid, errors);
    }

    @Override
    public CanvasValidationResult validateNode(CanvasDocument.CanvasNode node) {
        log.debug("Validating canvas node: id={}", node.id());

        List<String> errors = new ArrayList<>();

        if (node.id() == null || node.id().isBlank()) {
            errors.add("Node ID is required");
        }
        if (node.nodeType() == null || node.nodeType().isBlank()) {
            errors.add("Node type is required");
        }
        if (node.position() == null) {
            errors.add("Node position is required");
        } else {
            if (node.position().x() < 0 || node.position().y() < 0) {
                errors.add("Node position cannot be negative");
            }
        }
        if (node.bounds() == null) {
            errors.add("Node bounds are required");
        } else {
            if (node.bounds().width() <= 0 || node.bounds().height() <= 0) {
                errors.add("Node bounds must be positive");
            }
        }
        if (node.transform() == null) {
            errors.add("Node transform is required");
        } else {
            if (node.transform().scale() <= 0) {
                errors.add("Node transform scale must be positive");
            }
        }
        if (node.data() == null) {
            errors.add("Node data is required");
        }
        if (node.style() == null) {
            errors.add("Node style is required");
        }
        if (node.state() == null) {
            errors.add("Node state is required");
        }

        boolean isValid = errors.isEmpty();
        if (!isValid) {
            log.debug("Canvas node validation failed: id={}, errors={}", node.id(), errors);
        }

        return new CanvasValidationResult(isValid, errors);
    }

    @Override
    public CanvasValidationResult validateEdge(CanvasDocument.CanvasEdge edge) {
        log.debug("Validating canvas edge: id={}", edge.id());

        List<String> errors = new ArrayList<>();

        if (edge.id() == null || edge.id().isBlank()) {
            errors.add("Edge ID is required");
        }
        if (edge.sourceNodeId() == null || edge.sourceNodeId().isBlank()) {
            errors.add("Edge source node ID is required");
        }
        if (edge.targetNodeId() == null || edge.targetNodeId().isBlank()) {
            errors.add("Edge target node ID is required");
        }
        if (edge.sourceNodeId().equals(edge.targetNodeId())) {
            errors.add("Edge cannot connect a node to itself");
        }
        if (edge.style() == null) {
            errors.add("Edge style is required");
        }
        if (edge.data() == null) {
            errors.add("Edge data is required");
        }
        if (edge.state() == null) {
            errors.add("Edge state is required");
        }

        boolean isValid = errors.isEmpty();
        if (!isValid) {
            log.debug("Canvas edge validation failed: id={}, errors={}", edge.id(), errors);
        }

        return new CanvasValidationResult(isValid, errors);
    }
}
