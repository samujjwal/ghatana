package com.ghatana.digitalmarketing.application.identity;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.identity.ContactIdentityProfile;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Application service for contact identity profile depth.
 *
 * @doc.type interface
 * @doc.purpose DMOS contact identity-depth service
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface ContactIdentityService {

    Promise<ContactIdentityProfile> upsertIdentity(
        DmOperationContext ctx,
        String contactId,
        UpsertIdentityCommand command
    );

    Promise<ContactIdentityProfile> getIdentity(DmOperationContext ctx, String contactId);

    record UpsertIdentityCommand(
        String phoneNumber,
        String preferredLocale,
        String externalIdentityId,
        Map<String, String> attributes
    ) {
        public UpsertIdentityCommand {
            attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
        }
    }
}
