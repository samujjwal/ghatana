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
 * @doc.type interface
 * @doc.purpose Contact persistence contract enforcing workspace-scoped isolation
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
     * Finds a contact by email address within the given workspace.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param email       the email address; must not be blank
     * @return promise resolving to an optional contact
     */
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
}
