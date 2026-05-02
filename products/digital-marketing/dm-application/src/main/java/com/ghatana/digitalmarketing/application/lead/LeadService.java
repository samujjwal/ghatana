package com.ghatana.digitalmarketing.application.lead;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.lead.Lead;
import io.activej.promise.Promise;

/**
 * Application service contract for CRM-lite lead capture and qualification.
 *
 * @doc.type interface
 * @doc.purpose Lead capture and qualification lifecycle service for DMOS CRM-lite
 * @doc.layer product
 * @doc.pattern Service
 */
public interface LeadService {

    /**
     * Captures a new lead for a campaign.
     *
     * @param ctx     operation context; must not be null
     * @param command lead capture command; must not be null
     * @return promise resolving to the captured lead
     * @throws SecurityException if the actor is not authorized
     * @throws IllegalArgumentException if a duplicate lead exists for this email+campaign
     */
    Promise<Lead> captureLead(DmOperationContext ctx, CaptureLeadCommand command);

    /**
     * Qualifies a NEW lead for further action.
     *
     * @param ctx    operation context; must not be null
     * @param leadId the lead ID; must not be null
     * @return promise resolving to the qualified lead
     * @throws SecurityException if the actor is not authorized
     * @throws java.util.NoSuchElementException if the lead does not exist
     */
    Promise<Lead> qualifyLead(DmOperationContext ctx, String leadId);

    /**
     * Converts a QUALIFIED lead to a converted customer.
     *
     * @param ctx    operation context; must not be null
     * @param leadId the lead ID; must not be null
     * @return promise resolving to the converted lead
     * @throws SecurityException if the actor is not authorized
     * @throws java.util.NoSuchElementException if the lead does not exist
     */
    Promise<Lead> convertLead(DmOperationContext ctx, String leadId);

    /**
     * Disqualifies a lead (spam, duplicate, or unresponsive).
     *
     * @param ctx    operation context; must not be null
     * @param leadId the lead ID; must not be null
     * @return promise resolving to the disqualified lead
     * @throws SecurityException if the actor is not authorized
     * @throws java.util.NoSuchElementException if the lead does not exist
     */
    Promise<Lead> disqualifyLead(DmOperationContext ctx, String leadId);

    /**
     * Gets a lead by ID.
     *
     * @param ctx    operation context; must not be null
     * @param leadId the lead ID; must not be null
     * @return promise resolving to the lead
     * @throws SecurityException if the actor is not authorized
     * @throws java.util.NoSuchElementException if the lead does not exist
     */
    Promise<Lead> getLead(DmOperationContext ctx, String leadId);

    /**
     * Command to capture a new lead.
     *
     * @param campaignId the campaign ID this lead was captured from; must not be blank
     * @param email      the lead's email; must not be blank
     * @param firstName  optional first name; may be null
     * @param lastName   optional last name; may be null
     * @param phone      optional phone; may be null
     * @param source     capture source label; may be null (defaults to "unknown")
     */
    record CaptureLeadCommand(
        String campaignId,
        String email,
        String firstName,
        String lastName,
        String phone,
        String source
    ) {
        /**
         * Validates required capture fields.
         *
         * @throws IllegalArgumentException if campaignId or email is blank
         */
        public CaptureLeadCommand {
            if (campaignId == null || campaignId.isBlank()) {
                throw new IllegalArgumentException("campaignId must not be blank");
            }
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("email must not be blank");
            }
        }
    }
}
