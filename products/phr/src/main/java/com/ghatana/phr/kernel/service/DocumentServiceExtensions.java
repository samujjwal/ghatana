package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;

/**
 * Extension methods for DocumentService for dashboard and route family completion.
 *
 * <p>These methods provide convenience methods for the dashboard and other route families
 * that need aggregated document data.</p>
 *
 * @doc.type class
 * @doc.purpose Extension methods for DocumentService
 * @doc.layer product
 * @doc.pattern Service Extension
 */
public final class DocumentServiceExtensions {

    private final DocumentService documentService;

    public DocumentServiceExtensions(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Gets the total document count for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing the document count
     */
    public Promise<Integer> getDocumentCount(String patientId) {
        return documentService.getPatientDocuments(patientId, patientId)
            .map(documents -> documents.size());
    }
}
