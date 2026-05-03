package com.ghatana.digitalmarketing.application.validation;

import com.ghatana.digitalmarketing.application.content.ContentItemVersionRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import com.ghatana.digitalmarketing.domain.validation.ContentValidationFinding;
import com.ghatana.digitalmarketing.domain.validation.ContentValidationResult;
import com.ghatana.digitalmarketing.domain.validation.ContentValidationResult.ValidationOutcome;
import com.ghatana.digitalmarketing.domain.validation.ValidationSeverity;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic brand and claim validation service for DMOS content versions (F1-021).
 *
 * <p>Validation checks performed, in order:
 * <ol>
 *   <li><strong>Forbidden terms</strong> — each content block body is scanned case-insensitively.
 *       Any hit produces a FAIL finding.</li>
 *   <li><strong>Required claims</strong> — every required claim ID must appear in the version's
 *       claim references. A missing required claim produces a FAIL finding.</li>
 *   <li><strong>Claim evidence</strong> — claims whose {@code claimSource} is {@code "UNVERIFIED"}
 *       (case-insensitive) produce a WARN finding requiring evidence attachment.</li>
 *   <li><strong>Disclosure check</strong> — if the version has no disclosure references attached,
 *       a WARN finding is produced.</li>
 * </ol>
 * </p>
 *
 * @doc.type class
 * @doc.purpose DMOS F1-021 brand and claim validation service implementation
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ContentValidationServiceImpl implements ContentValidationService {

    private static final Logger LOG = LoggerFactory.getLogger(ContentValidationServiceImpl.class);

    static final String RULE_FORBIDDEN_TERM          = "FORBIDDEN_TERM";
    static final String RULE_REQUIRED_CLAIM_MISSING  = "REQUIRED_CLAIM_MISSING";
    static final String RULE_UNVERIFIED_CLAIM        = "UNVERIFIED_CLAIM";
    static final String RULE_NO_DISCLOSURES          = "NO_DISCLOSURES";
    static final String UNVERIFIED_SOURCE_MARKER     = "UNVERIFIED";

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final ContentItemVersionRepository  versionRepository;
    private final ContentValidationResultRepository resultRepository;

    public ContentValidationServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            ContentItemVersionRepository versionRepository,
            ContentValidationResultRepository resultRepository) {
        this.kernelAdapter    = Objects.requireNonNull(kernelAdapter,    "kernelAdapter must not be null");
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository must not be null");
        this.resultRepository  = Objects.requireNonNull(resultRepository,  "resultRepository must not be null");
    }

    @Override
    public Promise<ContentValidationResult> validateVersion(
            DmOperationContext ctx, ValidateContentVersionCommand command) {
        Objects.requireNonNull(ctx,     "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "content-version/" + command.versionId(), "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor is not authorised to validate content version"));
                }
                return versionRepository.findById(ctx.getWorkspaceId(), command.versionId())
                    .then(opt -> {
                        if (opt.isEmpty()) {
                            return Promise.ofException(
                                new NoSuchElementException("Content version not found: " + command.versionId()));
                        }
                        ContentVersion version = opt.get();
                        List<ContentValidationFinding> findings = runChecks(version, command);
                        ValidationOutcome outcome = deriveOutcome(findings);
                        ContentValidationResult result = new ContentValidationResult(
                            command.versionId(),
                            outcome,
                            findings,
                            Instant.now(),
                            ctx.getActor().getPrincipalId());

                        return resultRepository.save(ctx.getWorkspaceId(), result)
                            .then(saved -> {
                                Map<String, Object> details = Map.of(
                                    "versionId", command.versionId(),
                                    "outcome", outcome.name(),
                                    "findingCount", findings.size());
                                return kernelAdapter
                                    .recordAudit(ctx, command.versionId(), "content-version-validated", details)
                                    .map(ignored -> saved);
                            });
                    });
            });
    }

    @Override
    public Promise<List<ContentValidationResult>> listResults(DmOperationContext ctx, String versionId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (versionId == null || versionId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("versionId must not be blank"));
        }
        return kernelAdapter.isAuthorized(ctx, "content-version/" + versionId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor is not authorised to read validation results"));
                }
                return resultRepository.findByVersionId(ctx.getWorkspaceId(), versionId);
            });
    }

    // -------------------------------------------------------------------------
    // Validation logic
    // -------------------------------------------------------------------------

    private List<ContentValidationFinding> runChecks(
            ContentVersion version, ValidateContentVersionCommand command) {
        List<ContentValidationFinding> findings = new ArrayList<>();
        checkForbiddenTerms(version, command.forbiddenTerms(), findings);
        checkRequiredClaims(version, command.requiredClaimIds(), findings);
        checkUnverifiedClaims(version, findings);
        checkDisclosures(version, findings);
        return findings;
    }

    private void checkForbiddenTerms(ContentVersion version, List<String> forbiddenTerms,
            List<ContentValidationFinding> findings) {
        if (forbiddenTerms.isEmpty()) return;
        version.getContentBlocks().forEach(block -> {
            String body = block.bodyText() != null ? block.bodyText().toLowerCase() : "";
            forbiddenTerms.forEach(term -> {
                if (!term.isBlank() && body.contains(term.toLowerCase())) {
                    findings.add(new ContentValidationFinding(
                        ValidationSeverity.FAIL,
                        RULE_FORBIDDEN_TERM,
                        block.blockId(),
                        "Forbidden term '" + term + "' found in block '" + block.blockId() + "'",
                        "Remove or replace the forbidden term",
                        "COMPLIANCE_REVIEWER"));
                }
            });
        });
    }

    private void checkRequiredClaims(ContentVersion version, List<String> requiredClaimIds,
            List<ContentValidationFinding> findings) {
        if (requiredClaimIds.isEmpty()) return;
        Set<String> presentClaimIds = version.getClaimReferences().stream()
            .map(c -> c.claimId())
            .collect(Collectors.toSet());
        requiredClaimIds.forEach(required -> {
            if (!presentClaimIds.contains(required)) {
                findings.add(new ContentValidationFinding(
                    ValidationSeverity.FAIL,
                    RULE_REQUIRED_CLAIM_MISSING,
                    null,
                    "Required claim '" + required + "' is missing from this version",
                    "Attach the required claim reference before requesting approval",
                    "COMPLIANCE_REVIEWER"));
            }
        });
    }

    private void checkUnverifiedClaims(ContentVersion version, List<ContentValidationFinding> findings) {
        version.getClaimReferences().forEach(claim -> {
            if (UNVERIFIED_SOURCE_MARKER.equalsIgnoreCase(claim.claimSource())) {
                findings.add(new ContentValidationFinding(
                    ValidationSeverity.WARN,
                    RULE_UNVERIFIED_CLAIM,
                    null,
                    "Claim '" + claim.claimId() + "' has source marked as UNVERIFIED",
                    "Attach evidence or replace with an approved claim reference",
                    "COMPLIANCE_REVIEWER"));
            }
        });
    }

    private void checkDisclosures(ContentVersion version, List<ContentValidationFinding> findings) {
        if (version.getDisclosureReferences().isEmpty()) {
            findings.add(new ContentValidationFinding(
                ValidationSeverity.WARN,
                RULE_NO_DISCLOSURES,
                null,
                "No disclosure references are attached to this content version",
                "Attach the required regulatory or brand disclosures",
                "LEGAL_REVIEWER"));
        }
    }

    private static ValidationOutcome deriveOutcome(List<ContentValidationFinding> findings) {
        boolean hasFail = findings.stream().anyMatch(f -> f.severity() == ValidationSeverity.FAIL);
        if (hasFail) return ValidationOutcome.FAIL;
        boolean hasWarn = findings.stream().anyMatch(f -> f.severity() == ValidationSeverity.WARN);
        if (hasWarn) return ValidationOutcome.WARN;
        return ValidationOutcome.PASS;
    }
}
