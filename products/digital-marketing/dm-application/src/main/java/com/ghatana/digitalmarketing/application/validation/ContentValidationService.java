package com.ghatana.digitalmarketing.application.validation;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.validation.ContentValidationResult;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;

/**
 * Application service for brand voice, claim, and disclosure validation of content versions.
 *
 * <p>Validation is deterministic: it checks a given content version against the workspace
 * brand profile, forbidden-term lists, approved-claim registries, and required disclosures.
 * The result is pass/warn/fail with per-finding reasons and required actions.</p>
 *
 * @doc.type interface
 * @doc.purpose DMOS F1-021 brand and claim validation application service
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface ContentValidationService {

    /**
     * Command to trigger validation of a content version.
     *
     * @param versionId        the content version to validate; must not be blank
     * @param forbiddenTerms   list of forbidden terms to check against content blocks; must not be null
     * @param requiredClaimIds list of claim IDs that must appear in the version; must not be null
     */
    record ValidateContentVersionCommand(
            String versionId,
            List<String> forbiddenTerms,
            List<String> requiredClaimIds) {

        public ValidateContentVersionCommand {
            Objects.requireNonNull(versionId,        "versionId must not be null");
            Objects.requireNonNull(forbiddenTerms,   "forbiddenTerms must not be null");
            Objects.requireNonNull(requiredClaimIds, "requiredClaimIds must not be null");
            if (versionId.isBlank()) throw new IllegalArgumentException("versionId must not be blank");
            forbiddenTerms   = List.copyOf(forbiddenTerms);
            requiredClaimIds = List.copyOf(requiredClaimIds);
        }
    }

    /**
     * Validates the given content version and returns a {@link ContentValidationResult}.
     *
     * <p>The caller must hold {@code content-version/<versionId>:read} permission.
     * The validation result is recorded as an audit event.</p>
     *
     * @param ctx     the operation context; must not be null
     * @param command the validation command; must not be null
     * @return the full {@link ContentValidationResult} for this version
     */
    Promise<ContentValidationResult> validateVersion(DmOperationContext ctx, ValidateContentVersionCommand command);

    /**
     * Returns all previously stored validation results for a content version, ordered by
     * validation timestamp descending (most recent first).
     *
     * @param ctx       the operation context; must not be null
     * @param versionId the content version ID; must not be blank
     * @return all {@link ContentValidationResult} records for this version
     */
    Promise<List<ContentValidationResult>> listResults(DmOperationContext ctx, String versionId);
}
