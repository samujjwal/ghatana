/**
 * Canvas Service
 * 
 * Service for managing canvas document persistence with operation metadata.
 * All canvas changes are persisted with operation metadata for audit logging.
 * 
 * @doc.type interface
 * @doc.purpose Canvas persistence with operation metadata
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.canvas;

import com.ghatana.yappc.api.CanvasDocument;

import java.util.Map;

/**
 * Service interface for managing canvas document persistence with operation metadata.
 */
public interface CanvasService {

    /**
     * Saves a canvas document with operation metadata.
     * 
     * @param document The canvas document to save
     * @param operationMetadata Metadata about the operation being performed
     * @return The saved canvas document with updated metadata
     */
    CanvasDocument save(CanvasDocument document, CanvasOperationMetadata operationMetadata);

    /**
     * Loads a canvas document by ID.
     * 
     * @param id The canvas document ID
     * @return The canvas document, or null if not found
     */
    CanvasDocument load(String id);

    /**
     * Loads a canvas document by project ID.
     * 
     * @param projectId The project ID
     * @return The canvas document, or null if not found
     */
    CanvasDocument loadByProjectId(String projectId);

    /**
     * Deletes a canvas document with operation metadata.
     * 
     * @param id The canvas document ID
     * @param operationMetadata Metadata about the operation being performed
     */
    void delete(String id, CanvasOperationMetadata operationMetadata);

    /**
     * Metadata about a canvas operation for audit logging.
     */
    record CanvasOperationMetadata(
            String operationType,
            String actorId,
            String actorName,
            String correlationId,
            String source,
            Map<String, String> additionalMetadata
    ) {
        public enum OperationType {
            CREATE,
            UPDATE,
            DELETE,
            RESTORE,
            IMPORT,
            EXPORT
        }
    }
}
