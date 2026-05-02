package com.ghatana.digitalmarketing.application.identity;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.identity.ContactIdentityProfile;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Production implementation of {@link ContactIdentityService}.
 *
 * @doc.type class
 * @doc.purpose DMOS contact identity-depth application service implementation
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class ContactIdentityServiceImpl implements ContactIdentityService {

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final ContactIdentityRepository repository;

    public ContactIdentityServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            ContactIdentityRepository repository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<ContactIdentityProfile> upsertIdentity(
            DmOperationContext ctx,
            String contactId,
            UpsertIdentityCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(contactId, "contactId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "contacts/" + contactId, "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to update contact identity"));
                }

                ContactIdentityProfile profile = ContactIdentityProfile.builder()
                    .contactId(contactId)
                    .workspaceId(ctx.getWorkspaceId())
                    .phoneNumber(command.phoneNumber())
                    .preferredLocale(command.preferredLocale())
                    .externalIdentityId(command.externalIdentityId())
                    .attributes(command.attributes())
                    .updatedAt(Instant.now())
                    .updatedBy(ctx.getActor().getPrincipalId())
                    .build();

                return repository.save(profile)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx,
                        "contacts/" + contactId,
                        "identity-upsert",
                        Map.of("hasExternalIdentity", Boolean.toString(!saved.getExternalIdentityId().isBlank()))
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<ContactIdentityProfile> getIdentity(DmOperationContext ctx, String contactId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(contactId, "contactId must not be null");

        return kernelAdapter.isAuthorized(ctx, "contacts/" + contactId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read contact identity"));
                }
                return repository.findByContactId(ctx.getWorkspaceId(), contactId)
                    .then(opt -> opt
                        .map(Promise::of)
                        .orElseGet(() -> Promise.ofException(new NoSuchElementException("Identity profile not found: " + contactId))));
            });
    }
}
