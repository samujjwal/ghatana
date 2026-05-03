package com.ghatana.digitalmarketing.application.privacy;

import com.ghatana.digitalmarketing.application.contact.ContactRepository;
import com.ghatana.digitalmarketing.application.suppression.SuppressionRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Production implementation of DataSubjectRequestService.
 *
 * <p>DSAR compliance (DMOS-P0-001): Handles deletion requests in accordance with
 * GDPR/CCPA requirements. Deletes data from contacts and suppression lists,
 * and records all DSAR actions for audit purposes.</p>
 *
 * @doc.type class
 * @doc.purpose Production DSAR service for privacy compliance
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DataSubjectRequestServiceImpl implements DataSubjectRequestService {

    private static final Logger LOG = LoggerFactory.getLogger(DataSubjectRequestServiceImpl.class);

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final ContactRepository contactRepository;
    private final SuppressionRepository suppressionRepository;

    /**
     * Creates a new DataSubjectRequestServiceImpl.
     *
     * @param kernelAdapter the kernel adapter for auth and audit
     * @param contactRepository the contact repository
     * @param suppressionRepository the suppression repository
     */
    public DataSubjectRequestServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            ContactRepository contactRepository,
            SuppressionRepository suppressionRepository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.contactRepository = Objects.requireNonNull(contactRepository, "contactRepository must not be null");
        this.suppressionRepository = Objects.requireNonNull(suppressionRepository, "suppressionRepository must not be null");
    }

    @Override
    public Promise<Boolean> deleteContactData(DmOperationContext ctx, String contactId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(contactId, "contactId must not be null");

        return kernelAdapter.isAuthorized(ctx, "privacy/dsar", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to perform DSAR deletion"));
                }

                return contactRepository.deleteById(ctx.getWorkspaceId(), contactId)
                    .then(deleted -> {
                        LOG.info("[DMOS] DSAR deletion: contactId={} workspace={} deleted={} correlationId={}",
                            contactId, ctx.getWorkspaceId().getValue(), deleted, ctx.getCorrelationId().getValue());
                        
                        // Also delete from suppression list if present
                        return suppressionRepository.deleteByContactId(ctx.getWorkspaceId(), contactId)
                            .then(__ -> {
                                // Record audit
                                return kernelAdapter.recordAudit(
                                    ctx,
                                    "privacy/dsar",
                                    "contact-deletion",
                                    Map.of("contactId", contactId, "deleted", String.valueOf(deleted))
                                ).map(___ -> deleted);
                            });
                    });
            });
    }

    @Override
    public Promise<Integer> deleteContactDataByEmailHash(DmOperationContext ctx, String emailHash) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(emailHash, "emailHash must not be null");

        return kernelAdapter.isAuthorized(ctx, "privacy/dsar", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to perform DSAR deletion"));
                }

                // Find contact by email hash first
                return contactRepository.findByEmailHash(ctx.getWorkspaceId(), emailHash)
                    .then(optContact -> {
                        if (optContact.isEmpty()) {
                            LOG.info("[DMOS] DSAR deletion by email hash: no contact found workspace={} emailHash={}",
                                ctx.getWorkspaceId().getValue(), emailHash);
                            return Promise.of(0);
                        }

                        String contactId = optContact.get().getId();
                        return deleteContactData(ctx, contactId)
                            .then(deleted -> deleted ? 1 : 0);
                    });
            });
    }

    @Override
    public Promise<Void> recordDsarRequest(DmOperationContext ctx, String requestType, String subjectId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(requestType, "requestType must not be null");
        Objects.requireNonNull(subjectId, "subjectId must not be null");

        return kernelAdapter.recordAudit(
            ctx,
            "privacy/dsar",
            "dsar-request",
            Map.of("requestType", requestType, "subjectId", subjectId)
        ).then(__ -> {
            LOG.info("[DMOS] DSAR request recorded: type={} subjectId={} correlationId={}",
                requestType, subjectId, ctx.getCorrelationId().getValue());
            return Promise.of((Void) null);
        });
    }
}
