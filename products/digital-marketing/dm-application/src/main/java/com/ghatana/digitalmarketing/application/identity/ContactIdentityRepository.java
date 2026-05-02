package com.ghatana.digitalmarketing.application.identity;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.identity.ContactIdentityProfile;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository for contact identity profile persistence.
 *
 * @doc.type interface
 * @doc.purpose DMOS contact identity repository contract
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ContactIdentityRepository {

    Promise<ContactIdentityProfile> save(ContactIdentityProfile profile);

    Promise<Optional<ContactIdentityProfile>> findByContactId(DmWorkspaceId workspaceId, String contactId);
}
