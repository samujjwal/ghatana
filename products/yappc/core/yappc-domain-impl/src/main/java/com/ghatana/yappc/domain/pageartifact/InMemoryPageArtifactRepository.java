/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.domain.pageartifact;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of PageArtifactRepository.
 * <p>
 * Uses a ConcurrentHashMap for thread-safe storage. This is a simple implementation
 * suitable for development and testing. In production, this should be replaced with
 * a database-backed implementation using platform:java:database.
 *
 * @doc.type class
 * @doc.purpose In-memory implementation of page artifact repository
 * @doc.layer product
 * @doc.pattern Repository Implementation
 */
public final class InMemoryPageArtifactRepository implements PageArtifactRepository {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryPageArtifactRepository.class);

    private final Map<String, PageArtifactDocument> storage = new ConcurrentHashMap<>();

    @Override
    public Promise<PageArtifactDocument> save(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull PageArtifactDocument document
    ) {
        String key = buildKey(tenantId, workspaceId, projectId, document.artifactId());
        
        LOG.debug("Saving page artifact: tenant={}, workspace={}, project={}, artifactId={}, documentId={}",
                tenantId, workspaceId, projectId, document.artifactId(), document.documentId());

        PageArtifactDocument existing = storage.get(key);
        if (existing != null && !existing.documentId().equals(document.documentId())) {
            // Version conflict
            LOG.warn("Conflict detected for artifact {}: expected documentId={}, got documentId={}",
                    document.artifactId(), existing.documentId(), document.documentId());
            return Promise.ofException(new PageArtifactConflictException(
                    document.artifactId(),
                    existing.documentId()
            ));
        }

        PageArtifactDocument persisted = existing == null
                ? document
                : new PageArtifactDocument(
                        document.artifactId(),
                        nextDocumentId(existing.documentId()),
                        document.name(),
                        existing.createdBy(),
                        existing.createdAt(),
                        Instant.now(),
                        document.syncStatus(),
                        document.trustLevel(),
                        document.dataClassification(),
                        document.builderDocument(),
                        document.validationSummary(),
                        document.aiChangeRecords(),
                        document.source(),
                        document.residualIslandCount(),
                        document.roundTripFidelity()
                );

        storage.put(key, persisted);
        LOG.debug("Successfully saved page artifact: {}", key);
        return Promise.of(persisted);
    }

    @Override
    public Promise<PageArtifactDocument> load(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String artifactId
    ) {
        String key = buildKey(tenantId, workspaceId, projectId, artifactId);
        
        LOG.debug("Loading page artifact: {}", key);
        
        PageArtifactDocument document = storage.get(key);
        if (document == null) {
            LOG.debug("Page artifact not found: {}", key);
            return Promise.of(null);
        }
        
        LOG.debug("Successfully loaded page artifact: {}", key);
        return Promise.of(document);
    }

    @Override
    public Promise<Void> delete(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String artifactId
    ) {
        String key = buildKey(tenantId, workspaceId, projectId, artifactId);
        
        LOG.debug("Deleting page artifact: {}", key);
        
        storage.remove(key);
        LOG.debug("Successfully deleted page artifact: {}", key);
        return Promise.of(null);
    }

    private String buildKey(String tenantId, String workspaceId, String projectId, String artifactId) {
        return String.format("%s:%s:%s:%s", tenantId, workspaceId, projectId, artifactId);
    }

    /**
     * Clears all stored documents. Useful for testing.
     */
    public void clear() {
        storage.clear();
    }

    /**
     * Gets the current size of the storage. Useful for testing.
     */
    public int size() {
        return storage.size();
    }

    private String nextDocumentId(String currentDocumentId) {
        return currentDocumentId + "@rev-" + UUID.randomUUID();
    }
}
