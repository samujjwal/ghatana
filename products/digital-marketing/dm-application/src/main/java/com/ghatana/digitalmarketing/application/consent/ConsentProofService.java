package com.ghatana.digitalmarketing.application.consent;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.consent.ConsentProofSnapshot;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Application service for durable consent proof lifecycle.
 *
 * @doc.type interface
 * @doc.purpose DMOS consent proof snapshot service
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface ConsentProofService {

    Promise<ConsentProofSnapshot> recordSnapshot(
        DmOperationContext ctx,
        String contactId,
        String consentStatus,
        String consentPurpose,
        String evidenceType,
        String evidenceReference
    );

    Promise<List<ConsentProofSnapshot>> listSnapshots(DmOperationContext ctx, String contactId);
}
