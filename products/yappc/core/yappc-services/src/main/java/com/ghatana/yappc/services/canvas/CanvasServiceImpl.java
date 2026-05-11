/**
 * Canvas Service Implementation
 * 
 * Production-grade implementation of canvas service with operation metadata persistence.
 * All canvas changes are persisted with operation metadata for audit logging.
 * 
 * @doc.type class
 * @doc.purpose Canvas persistence with operation metadata implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.canvas;

import com.ghatana.yappc.api.CanvasDocument;
import com.ghatana.yappc.services.canvas.CanvasValidationService.CanvasValidationResult;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade implementation of canvas service with operation metadata.
 * Uses in-memory storage for demonstration; should be replaced with database persistence.
 */
public final class CanvasServiceImpl implements CanvasService {

    private static final Logger log = LoggerFactory.getLogger(CanvasServiceImpl.class);

    // In-memory storage for demonstration - replace with database persistence
    private final Map<String, CanvasDocument> storage = new ConcurrentHashMap<>();
    private final Map<String, String> projectToCanvasIdMap = new ConcurrentHashMap<>();

    private final CanvasValidationService validationService;

    public CanvasServiceImpl(CanvasValidationService validationService) {
        this.validationService = validationService;
    }

    @Override
    public CanvasDocument save(CanvasDocument document, CanvasOperationMetadata operationMetadata) {
        log.info("Saving canvas document: id={}, operation={}, actor={}", 
                document.id(), operationMetadata.operationType(), operationMetadata.actorId());

        // Validate document before persistence
        CanvasValidationResult validationResult = validationService.validate(document);
        if (!validationResult.isValid()) {
            log.error("Canvas document validation failed: id={}, errors={}", 
                    document.id(), validationResult.errors());
            throw new IllegalArgumentException("Canvas document validation failed: " + validationResult.errors());
        }

        // Log operation metadata for audit trail
        logOperationMetadata(document.id(), operationMetadata);

        // Update document metadata
        Instant now = Instant.now();
        Long newRevision = document.revision() + 1;

        // Create updated document with operation metadata
        CanvasDocument updatedDocument = new CanvasDocument(
                document.id(),
                document.projectId(),
                document.workspaceId(),
                document.tenantId(),
                document.version(),
                document.metadata(),
                document.nodes(),
                document.edges(),
                document.viewport(),
                document.state(),
                document.createdAt(),
                now,
                document.createdBy(),
                operationMetadata.actorId(),
                newRevision
        );

        // Persist document
        storage.put(document.id(), updatedDocument);
        projectToCanvasIdMap.put(document.projectId(), document.id());

        log.info("Canvas document saved successfully: id={}, revision={}", document.id(), newRevision);
        return updatedDocument;
    }

    @Override
    public CanvasDocument load(String id) {
        log.debug("Loading canvas document: id={}", id);
        CanvasDocument document = storage.get(id);
        if (document == null) {
            log.warn("Canvas document not found: id={}", id);
        }
        return document;
    }

    @Override
    public CanvasDocument loadByProjectId(String projectId) {
        log.debug("Loading canvas document by project ID: projectId={}", projectId);
        String canvasId = projectToCanvasIdMap.get(projectId);
        if (canvasId == null) {
            log.warn("No canvas document found for project: projectId={}", projectId);
            return null;
        }
        return load(canvasId);
    }

    @Override
    public void delete(String id, CanvasOperationMetadata operationMetadata) {
        log.info("Deleting canvas document: id={}, operation={}, actor={}", 
                id, operationMetadata.operationType(), operationMetadata.actorId());

        CanvasDocument document = storage.get(id);
        if (document == null) {
            log.warn("Cannot delete non-existent canvas document: id={}", id);
            throw new IllegalArgumentException("Canvas document not found: " + id);
        }

        // Log operation metadata for audit trail
        logOperationMetadata(id, operationMetadata);

        // Remove document
        storage.remove(id);
        projectToCanvasIdMap.remove(document.projectId());

        log.info("Canvas document deleted successfully: id={}", id);
    }

    private void logOperationMetadata(String canvasId, CanvasOperationMetadata metadata) {
        log.info("Canvas operation: canvasId={}, operationType={}, actorId={}, actorName={}, correlationId={}, source={}",
                canvasId,
                metadata.operationType(),
                metadata.actorId(),
                metadata.actorName(),
                metadata.correlationId(),
                metadata.source());

        if (metadata.additionalMetadata() != null && !metadata.additionalMetadata().isEmpty()) {
            log.info("Canvas operation additional metadata: canvasId={}, metadata={}", 
                    canvasId, metadata.additionalMetadata());
        }
    }

    /**
     * Creates a new canvas document with initial metadata.
     * 
     * @param projectId The project ID
     * @param workspaceId The workspace ID
     * @param tenantId The tenant ID
     * @param actorId The actor ID
     * @return A new canvas document with initial metadata
     */
    public CanvasDocument createNewDocument(String projectId, String workspaceId, String tenantId, String actorId) {
        String canvasId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        CanvasDocument.CanvasMetadata metadata = new CanvasDocument.CanvasMetadata(
                "Untitled Canvas",
                "",
                "intent",
                Set.of(),
                Map.of()
        );

        CanvasDocument.CanvasViewport viewport = new CanvasDocument.CanvasViewport(
                new CanvasDocument.CanvasPoint(0, 0),
                1.0,
                0.0
        );

        CanvasDocument.CanvasDocumentState state = new CanvasDocument.CanvasDocumentState(
                CanvasDocument.CanvasDocumentState.DocumentStatus.DRAFT,
                true,
                true,
                List.of(),
                null,
                null
        );

        return new CanvasDocument(
                canvasId,
                projectId,
                workspaceId,
                tenantId,
                "1.0.0",
                metadata,
                List.of(),
                List.of(),
                viewport,
                state,
                now,
                now,
                actorId,
                actorId,
                0L
        );
    }
}
