package com.ghatana.digitalmarketing.application.contact;

import com.ghatana.digitalmarketing.application.consent.ConsentProofService;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.contact.Contact;
import com.ghatana.digitalmarketing.domain.contact.ConsentStatus;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link ContactService}.
 *
 * <p>Enforces consent-first behavior: all marketing actions verify consent before
 * proceeding. All contact writes and reads of sensitive data are audited.</p>
 *
 * @doc.type class
 * @doc.purpose Production DMOS contact and consent lifecycle application service
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class ContactServiceImpl implements ContactService {

    private static final Logger LOG = LoggerFactory.getLogger(ContactServiceImpl.class);

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final ContactRepository repository;
    private final ConsentProofService consentProofService;

    /**
     * Constructs the contact service.
     *
     * @param kernelAdapter DMOS kernel adapter for auth, consent verification, and audit
     * @param repository    contact persistence
     */
    public ContactServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            ContactRepository repository) {
        this(kernelAdapter, repository, null);
    }

    /**
     * Constructs the contact service with optional consent proof persistence.
     *
     * @param kernelAdapter       DMOS kernel adapter for auth, consent verification, and audit
     * @param repository          contact persistence
     * @param consentProofService consent proof storage service; nullable for compatibility
     */
    public ContactServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            ContactRepository repository,
            ConsentProofService consentProofService) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository    = Objects.requireNonNull(repository,    "repository must not be null");
        this.consentProofService = consentProofService;
    }

    // -----------------------------------------------------------------------
    // Register
    // -----------------------------------------------------------------------

    @Override
    public Promise<Contact> registerContact(DmOperationContext ctx, RegisterContactCommand command) {
        Objects.requireNonNull(ctx,     "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "contacts/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to register contacts"));
                }
                Instant now = Instant.now();
                Contact contact = Contact.builder()
                    .id(UUID.randomUUID().toString())
                    .workspaceId(ctx.getWorkspaceId())
                    .email(command.email())
                    .displayName(command.displayName() != null ? command.displayName() : "")
                    .consentStatus(ConsentStatus.UNKNOWN)
                    .suppressed(false)
                    .createdAt(now)
                    .updatedAt(now)
                    .createdBy(ctx.getActor().getPrincipalId())
                    .build();

                return repository.save(contact)
                    .then(saved -> {
                        LOG.info("[DMOS] Contact registered: id={} workspace={} correlationId={}",
                            saved.getId(),
                            ctx.getWorkspaceId().getValue(),
                            ctx.getCorrelationId().getValue());
                        return kernelAdapter.recordAudit(
                            ctx,
                            "contacts/" + saved.getId(),
                            "register",
                            Map.of("email", saved.getEmail())
                        ).map(__ -> saved);
                    });
            });
    }

    // -----------------------------------------------------------------------
    // Consent management
    // -----------------------------------------------------------------------

    @Override
    public Promise<Contact> grantConsent(DmOperationContext ctx, String contactId, String purpose) {
        Objects.requireNonNull(ctx,       "ctx must not be null");
        Objects.requireNonNull(contactId, "contactId must not be null");
        Objects.requireNonNull(purpose,   "purpose must not be null");

        return kernelAdapter.isAuthorized(ctx, "contacts/" + contactId, "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to update contact " + contactId));
                }
                return repository.findById(ctx.getWorkspaceId(), contactId)
                    .then(opt -> {
                        if (opt.isEmpty()) {
                            return Promise.ofException(
                                new NoSuchElementException("Contact not found: " + contactId));
                        }
                        Contact updated = opt.get().grantConsent(purpose, Instant.now());
                        return repository.save(updated)
                            .then(saved -> {
                                LOG.info("[DMOS] Consent granted: contact={} purpose={} correlationId={}",
                                    contactId, purpose, ctx.getCorrelationId().getValue());
                                Promise<Contact> persisted = recordConsentProof(
                                    ctx,
                                    saved,
                                    "GRANTED",
                                    purpose,
                                    "contact-consent-grant",
                                    "contact:" + contactId + ":grant"
                                );
                                return persisted.then(savedWithProof -> kernelAdapter.recordAudit(
                                    ctx,
                                    "contacts/" + contactId,
                                    "consent-granted",
                                    Map.of("purpose", purpose)
                                ).map(__ -> savedWithProof));
                            });
                    });
            });
    }

    @Override
    public Promise<Contact> withdrawConsent(DmOperationContext ctx, String contactId) {
        Objects.requireNonNull(ctx,       "ctx must not be null");
        Objects.requireNonNull(contactId, "contactId must not be null");

        return kernelAdapter.isAuthorized(ctx, "contacts/" + contactId, "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to update contact " + contactId));
                }
                return repository.findById(ctx.getWorkspaceId(), contactId)
                    .then(opt -> {
                        if (opt.isEmpty()) {
                            return Promise.ofException(
                                new NoSuchElementException("Contact not found: " + contactId));
                        }
                        Contact updated = opt.get().withdrawConsent();
                        return repository.save(updated)
                            .then(saved -> {
                                LOG.info("[DMOS] Consent withdrawn: contact={} correlationId={}",
                                    contactId, ctx.getCorrelationId().getValue());
                                Promise<Contact> persisted = recordConsentProof(
                                    ctx,
                                    saved,
                                    "WITHDRAWN",
                                    saved.getConsentPurpose(),
                                    "contact-consent-withdraw",
                                    "contact:" + contactId + ":withdraw"
                                );
                                return persisted.then(savedWithProof -> kernelAdapter.recordAudit(
                                    ctx,
                                    "contacts/" + contactId,
                                    "consent-withdrawn",
                                    Map.of("suppressed", Boolean.TRUE.toString())
                                ).map(__ -> savedWithProof));
                            });
                    });
            });
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    @Override
    public Promise<Contact> getContact(DmOperationContext ctx, String contactId) {
        Objects.requireNonNull(ctx,       "ctx must not be null");
        Objects.requireNonNull(contactId, "contactId must not be null");

        return kernelAdapter.isAuthorized(ctx, "contacts/" + contactId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to read contact " + contactId));
                }
                return repository.findById(ctx.getWorkspaceId(), contactId)
                    .then(opt -> opt.isPresent()
                        ? Promise.of(opt.get())
                        : Promise.ofException(new NoSuchElementException("Contact not found: " + contactId)));
            });
    }

    @Override
    public Promise<Boolean> hasConsent(DmOperationContext ctx, String contactId, String purpose) {
        Objects.requireNonNull(ctx,       "ctx must not be null");
        Objects.requireNonNull(contactId, "contactId must not be null");
        Objects.requireNonNull(purpose,   "purpose must not be null");

        return repository.findById(ctx.getWorkspaceId(), contactId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(false);
                }
                Contact contact = opt.get();
                boolean eligible = contact.isMarketingEligible()
                    && purpose.equals(contact.getConsentPurpose());
                return Promise.of(eligible);
            });
    }

    private Promise<Contact> recordConsentProof(
            DmOperationContext ctx,
            Contact contact,
            String status,
            String purpose,
            String evidenceType,
            String evidenceReference) {
        if (consentProofService == null) {
            return Promise.of(contact);
        }

        return consentProofService
            .recordSnapshot(ctx, contact.getId(), status, purpose, evidenceType, evidenceReference)
            .map(__ -> contact);
    }
}
