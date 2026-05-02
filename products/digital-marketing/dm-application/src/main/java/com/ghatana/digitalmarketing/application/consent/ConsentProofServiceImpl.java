package com.ghatana.digitalmarketing.application.consent;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.consent.ConsentProofSnapshot;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link ConsentProofService}.
 *
 * @doc.type class
 * @doc.purpose DMOS consent proof snapshot application service implementation
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class ConsentProofServiceImpl implements ConsentProofService {

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final ConsentProofRepository repository;

    public ConsentProofServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            ConsentProofRepository repository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<ConsentProofSnapshot> recordSnapshot(
            DmOperationContext ctx,
            String contactId,
            String consentStatus,
            String consentPurpose,
            String evidenceType,
            String evidenceReference) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(contactId, "contactId must not be null");
        Objects.requireNonNull(consentStatus, "consentStatus must not be null");
        Objects.requireNonNull(evidenceType, "evidenceType must not be null");
        Objects.requireNonNull(evidenceReference, "evidenceReference must not be null");

        return kernelAdapter.isAuthorized(ctx, "contacts/" + contactId, "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to record consent proof"));
                }

                ConsentProofSnapshot snapshot = ConsentProofSnapshot.builder()
                    .snapshotId(UUID.randomUUID().toString())
                    .contactId(contactId)
                    .workspaceId(ctx.getWorkspaceId())
                    .consentStatus(consentStatus)
                    .consentPurpose(consentPurpose)
                    .evidenceType(evidenceType)
                    .evidenceReference(evidenceReference)
                    .recordedAt(Instant.now())
                    .recordedBy(ctx.getActor().getPrincipalId())
                    .correlationId(ctx.getCorrelationId().getValue())
                    .build();

                return repository.save(snapshot)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx,
                        "contacts/" + contactId,
                        "consent-proof-snapshot",
                        Map.of("snapshotId", saved.getSnapshotId())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<List<ConsentProofSnapshot>> listSnapshots(DmOperationContext ctx, String contactId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(contactId, "contactId must not be null");

        return kernelAdapter.isAuthorized(ctx, "contacts/" + contactId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read consent proof"));
                }
                return repository.listByContactId(ctx.getWorkspaceId(), contactId);
            });
    }
}
