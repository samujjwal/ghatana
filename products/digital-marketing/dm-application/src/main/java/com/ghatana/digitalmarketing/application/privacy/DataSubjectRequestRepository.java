package com.ghatana.digitalmarketing.application.privacy;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.privacy.DataSubjectRequest;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Repository port for {@link DataSubjectRequest} (DMOS-P1-017).
 *
 * @doc.type interface
 * @doc.purpose Repository for data subject request storage (DMOS-P1-017)
 * @doc.layer application
 * @doc.pattern Repository
 */
public interface DataSubjectRequestRepository {

    Promise<DataSubjectRequest> save(DataSubjectRequest request);

    Promise<DataSubjectRequest> findById(String id);

    Promise<List<DataSubjectRequest>> findByTenantAndWorkspace(DmTenantId tenantId, DmWorkspaceId workspaceId);

    Promise<List<DataSubjectRequest>> findByContactPointHash(String contactPointHash);

    Promise<List<DataSubjectRequest>> findByStatus(DataSubjectRequest.RequestStatus status);

    Promise<DataSubjectRequest> update(DataSubjectRequest request);

    Promise<Void> delete(String id);
}
