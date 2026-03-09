package com.ghatana.yappc.knowledge;

import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates YAPPC-specific graph operations.
 */
@Slf4j
/**
 * @doc.type class
 * @doc.purpose Handles yappc graph validator operations
 * @doc.layer core
 * @doc.pattern Validator
 */
public class YAPPCGraphValidator {
    
    public void validateNode(YAPPCGraphNode node) {
        if (node.id() == null || node.id().isBlank()) {
            throw new IllegalArgumentException("Node id cannot be null or blank");
        }
        if (node.type() == null) {
            throw new IllegalArgumentException("Node type cannot be null");
        }
        if (node.name() == null || node.name().isBlank()) {
            throw new IllegalArgumentException("Node name cannot be null or blank");
        }
        if (node.metadata() == null || node.metadata().tenantId() == null) {
            throw new IllegalArgumentException("Node metadata and tenantId are required");
        }
        log.debug("Node validation passed: {}", node.id());
    }
}
