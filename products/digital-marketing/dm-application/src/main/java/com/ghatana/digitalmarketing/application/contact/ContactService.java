package com.ghatana.digitalmarketing.application.contact;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.contact.Contact;
import io.activej.promise.Promise;

/**
 * Application service contract for DMOS contact and consent lifecycle management.
 *
 * <p>All operations enforce consent-first requirements per the {@code DM_CONSENT_LIFECYCLE}
 * rule set and are governed by boundary policy rules {@code DM-BP-002} and {@code DM-BP-003}.
 * Sensitive contact operations are always audited.</p>
 *
 * @doc.type interface
 * @doc.purpose Contact and consent lifecycle application service contract
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface ContactService {

    /**
     * Registers a new contact in the workspace.
     *
     * <p>New contacts start with {@code UNKNOWN} consent status. Marketing targeting
     * is only allowed after explicit consent is granted.</p>
     *
     * @param ctx     operation context
     * @param command contact creation parameters
     * @return promise resolving to the created contact
     * @throws SecurityException if the actor is not authorized
     */
    Promise<Contact> registerContact(DmOperationContext ctx, RegisterContactCommand command);

    /**
     * Records explicit consent from a contact for a specific marketing purpose.
     *
     * <p>This method must be called before any marketing targeting of the contact.
     * It records consent in the domain and delegates to the kernel consent plugin
     * via the bridge adapter for durable consent storage.</p>
     *
     * @param ctx       operation context
     * @param contactId the contact identifier
     * @param purpose   the consent purpose (e.g. {@code "marketing-email"})
     * @return promise resolving to the updated contact with {@code GRANTED} consent
     * @throws SecurityException         if the actor is not authorized
     * @throws java.util.NoSuchElementException if the contact does not exist
     */
    Promise<Contact> grantConsent(DmOperationContext ctx, String contactId, String purpose);

    /**
     * Records consent withdrawal for a contact, adding them to the suppression list.
     *
     * <p>After withdrawal, the contact must not receive any marketing communications
     * unless a new explicit consent is obtained.</p>
     *
     * @param ctx       operation context
     * @param contactId the contact identifier
     * @return promise resolving to the updated contact with withdrawn consent
     * @throws SecurityException         if the actor is not authorized
     * @throws java.util.NoSuchElementException if the contact does not exist
     */
    Promise<Contact> withdrawConsent(DmOperationContext ctx, String contactId);

    /**
     * Retrieves a contact by ID, enforcing workspace isolation and authorization.
     *
     * @param ctx       operation context
     * @param contactId the contact identifier
     * @return promise resolving to the contact
     * @throws SecurityException         if the actor is not authorized
     * @throws java.util.NoSuchElementException if the contact does not exist
     */
    Promise<Contact> getContact(DmOperationContext ctx, String contactId);

    /**
     * Verifies whether a contact has valid, current consent for the given purpose.
     *
     * <p>Returns {@code true} only when consent is {@code GRANTED} and the contact
     * is not suppressed. Suppressed contacts always return {@code false}.</p>
     *
     * @param ctx       operation context
     * @param contactId the contact identifier
     * @param purpose   the consent purpose to check
     * @return promise resolving to {@code true} when marketing-eligible
     */
    Promise<Boolean> hasConsent(DmOperationContext ctx, String contactId, String purpose);

    // -----------------------------------------------------------------------
    // Command records
    // -----------------------------------------------------------------------

    /**
     * Command for registering a new contact.
     *
     * @param email       email address; must not be blank
     * @param displayName optional display name; may be null or empty
     */
    record RegisterContactCommand(String email, String displayName) {
        /**
         * Validates command fields.
         *
         * @throws IllegalArgumentException if email is blank
         */
        public RegisterContactCommand {
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Contact email must not be blank");
            }
        }
    }
}
