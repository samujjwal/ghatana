package com.ghatana.digitalmarketing.application.consent;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.consent.ConsentProofSnapshot;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Repository for durable consent proof snapshots.
 *
 * @doc.type interface
 * @doc.purpose DMOS consent proof snapshot repository contract
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ConsentProofRepository {

    Promise<ConsentProofSnapshot> save(ConsentProofSnapshot snapshot);

    Promise<List<ConsentProofSnapshot>> listByContactId(DmWorkspaceId workspaceId, String contactId);
}
