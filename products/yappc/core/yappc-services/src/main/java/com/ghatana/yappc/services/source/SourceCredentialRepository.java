package com.ghatana.yappc.services.source;

import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Repository abstraction for governed source credential reference ownership
 * @doc.layer service
 * @doc.pattern Repository
 */
public interface SourceCredentialRepository {

    Optional<CredentialBinding> findBinding(
        String tenantId,
        String workspaceId,
        String projectId,
        String provider,
        String credentialRef
    );

    record CredentialBinding(
        String tenantId,
        String workspaceId,
        String projectId,
        String provider,
        String credentialRef,
        String secretKey
    ) {
    }
}
