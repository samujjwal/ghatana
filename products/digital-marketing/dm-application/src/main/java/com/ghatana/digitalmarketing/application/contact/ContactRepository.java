package com.ghatana.digitalmarketing.application.contact;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.contact.Contact;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DMOS contact persistence.
 *
 * <p>All methods are workspace-scoped; implementations must enforce isolation
 * so that cross-workspace data access is structurally impossible.</p>
 *
 * <p>PII-safe implementation (DMOS-P0-001): Email lookups use hash instead of raw
 * email to protect PII. Use findByEmailHash for privacy-compliant lookups.</p>
 *
 * @doc.type interface
 * @doc.purpose Contact persistence contract enforcing workspace-scoped isolation with PII protection
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ContactRepository {

    /**
     * Saves a contact (insert or update by ID within the workspace).
     *
     * @param contact the contact to save; must not be null
     * @return promise resolving to the saved contact
     */
    Promise<Contact> save(Contact contact);

    /**
     * Finds a contact by ID within the given workspace.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param contactId   the contact ID; must not be null
     * @return promise resolving to an optional contact
     */
    Promise<Optional<Contact>> findById(DmWorkspaceId workspaceId, String contactId);

    /**
     * Finds a contact by email hash within the given workspace (DMOS-P0-001).
     * This is the preferred method for privacy-compliant email lookups.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param emailHash   the HMAC-SHA256 hash of the email; must not be blank
     * @return promise resolving to an optional contact
     */
    Promise<Optional<Contact>> findByEmailHash(DmWorkspaceId workspaceId, String emailHash);

    /**
     * Finds a contact by email address within the given workspace.
     * @deprecated Use findByEmailHash instead for privacy compliance (DMOS-P0-001).
     * This method is provided for backward compatibility during migration.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param email       the email address; must not be blank
     * @return promise resolving to an optional contact
     */
    @Deprecated
    Promise<Optional<Contact>> findByEmail(DmWorkspaceId workspaceId, String email);

    /**
     * Lists all contacts in the workspace that are eligible for marketing
     * (consent granted, not suppressed).
     *
     * @param workspaceId the owning workspace; must not be null
     * @return promise resolving to a list of eligible contacts; never null
     */
    Promise<List<Contact>> listMarketingEligible(DmWorkspaceId workspaceId);

    /**
     * Returns the count of contacts with granted consent in the workspace.
     *
     * @param workspaceId the owning workspace; must not be null
     * @return promise resolving to the count
     */
    Promise<Integer> countMarketingEligible(DmWorkspaceId workspaceId);
    
    /**
     * Deletes a contact by ID within the given workspace for DSAR compliance.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param contactId   the contact ID; must not be null
     * @return promise resolving to true if deleted, false if not found
     */
    Promise<Boolean> deleteById(DmWorkspaceId workspaceId, String contactId);
}
