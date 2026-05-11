/**
 * Canvas Validation Service
 * 
 * Validates canvas documents before persistence to ensure data integrity and consistency.
 * All canvas documents must pass validation before being persisted to the database.
 * 
 * @doc.type interface
 * @doc.purpose Canvas validation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.canvas;

import com.ghatana.yappc.api.CanvasDocument;

import java.util.List;

/**
 * Service interface for validating canvas documents before persistence.
 */
public interface CanvasValidationService {

    /**
     * Validates a canvas document before persistence.
     * 
     * @param document The canvas document to validate
     * @return CanvasValidationResult containing validation status and any errors
     */
    CanvasValidationResult validate(CanvasDocument document);

    /**
     * Validates a canvas node before persistence.
     * 
     * @param node The canvas node to validate
     * @return CanvasValidationResult containing validation status and any errors
     */
    CanvasValidationResult validateNode(CanvasDocument.CanvasNode node);

    /**
     * Validates a canvas edge before persistence.
     * 
     * @param edge The canvas edge to validate
     * @return CanvasValidationResult containing validation status and any errors
     */
    CanvasValidationResult validateEdge(CanvasDocument.CanvasEdge edge);

    /**
     * Result of canvas validation.
     */
    record CanvasValidationResult(
            boolean isValid,
            List<String> errors
    ) {
        public CanvasValidationResult {
            if (errors == null) {
                errors = List.of();
            }
        }
    }
}
