package com.ghatana.digitalmarketing.application.validation;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.validation.ContentValidationResult;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Repository contract for persisting and querying content validation results.
 *
 * @doc.type interface
 * @doc.purpose DMOS content validation result repository SPI
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ContentValidationResultRepository {

    /**
     * Persists a validation result.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param result      the result to persist; must not be null
     * @return the saved result
     */
    Promise<ContentValidationResult> save(DmWorkspaceId workspaceId, ContentValidationResult result);

    /**
     * Returns all validation results for a given version, ordered by validatedAt descending.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param versionId   the content version ID; must not be blank
     * @return all persisted results (may be empty)
     */
    Promise<List<ContentValidationResult>> findByVersionId(DmWorkspaceId workspaceId, String versionId);
}
