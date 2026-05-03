package com.ghatana.digitalmarketing.application.privacy;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Service for handling Data Subject Access Requests (DSAR) including deletion requests.
 *
 * <p>DSAR compliance (DMOS-P0-001): Provides endpoints for data subjects to request
 * deletion of their personal data in accordance with GDPR/CCPA requirements.</p>
 *
 * @doc.type interface
 * @doc.purpose DSAR service for privacy compliance
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DataSubjectRequestService {

    /**
     * Deletes all personal data for a contact by ID.
     *
     * @param ctx the operation context
     * @param contactId the contact ID to delete
     * @return promise resolving to true if deleted, false if not found
     */
    Promise<Boolean> deleteContactData(DmOperationContext ctx, String contactId);

    /**
     * Deletes all personal data for a contact by email hash.
     *
     * @param ctx the operation context
     * @param emailHash the email hash to delete
     * @return promise resolving to the number of records deleted
     */
    Promise<Integer> deleteContactDataByEmailHash(DmOperationContext ctx, String emailHash);

    /**
     * Records a DSAR request for audit purposes.
     *
     * @param ctx the operation context
     * @param requestType the type of DSAR request (e.g., "deletion", "access")
     * @param subjectId the subject identifier (contact ID or email hash)
     * @return promise resolving when audit is recorded
     */
    Promise<Void> recordDsarRequest(DmOperationContext ctx, String requestType, String subjectId);
}
