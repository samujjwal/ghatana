package com.ghatana.yappc.services.source;

import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.SourceLocator;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * @doc.type interface
 * @doc.purpose Canonical Java source provider contract for durable import orchestration
 * @doc.layer service
 * @doc.pattern Strategy
 */
public interface SourceProvider {

    String providerId();

    boolean canHandle(SourceLocator locator);

    Promise<RepositorySnapshot> resolve(SourceLocator locator, ScopeContext scope);

    default Map<String, Object> capabilities() {
        return Map.of();
    }

    /**
     * @doc.type record
     * @doc.purpose Scope context for tenant/workspace/project governed source resolution
     * @doc.layer service
     * @doc.pattern DataTransferObject
     */
    record ScopeContext(
        String tenantId,
        String workspaceId,
        String projectId,
        String principalId
    ) {}
}
