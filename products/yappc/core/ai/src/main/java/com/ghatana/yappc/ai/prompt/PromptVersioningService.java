/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Module — Prompt Versioning Service
 */
package com.ghatana.yappc.ai.prompt;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Version-controlled prompt store for YAPPC AI interactions.
 *
 * <p>Stores named prompt templates with full version history so that:
 * <ul>
 *   <li>Prompt changes are tracked and auditable</li>
 *   <li>A/B tests can reference specific prompt versions by hash</li>
 *   <li>Regressions can be reproduced by replaying a prior version</li>
 *   <li>Teams can roll back to a previous version instantly</li>
 * </ul>
 *
 * <p><b>Version Identity</b></p>
 * <p>Each version is identified by the SHA-256 hash of its content. Storing the same
 * content a second time is a no-op — the existing version is returned. Versions are
 * immutable once stored.
 *
 * <p><b>Collection Schema</b></p>
 * <pre>
 * prompt-versions/{tenantId}/{versionId}:
 *   versionId   : UUID (surrogate key)
 *   promptName  : String  — logical prompt identifier (e.g. "requirement-generation")
 *   content     : String  — full prompt text with {{varName}} placeholders
 *   contentHash : String  — SHA-256 hex of content (deduplication key)
 *   description : String  — human-readable change description
 *   author      : String  — who created this version
 *   active      : boolean — whether this version is the current default
 *   createdAt   : ISO-8601 instant
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Version-controlled prompt store for YAPPC AI prompt management
 * @doc.layer product
 * @doc.pattern Repository, Service
 */
public final class PromptVersioningService {

    private static final Logger log = LoggerFactory.getLogger(PromptVersioningService.class);
    private static final String COLLECTION = "prompt-versions";

    private final DataCloudClient client;

    public PromptVersioningService(@NotNull DataCloudClient client) {
        this.client = Objects.requireNonNull(client, "DataCloudClient must not be null");
    }

    // ── Write operations ─────────────────────────────────────────────────────

    /**
     * Stores a new version of a named prompt.
     *
     * <p>If a version with identical content already exists, the existing version ID
     * is returned without creating a duplicate.
     *
     * @param promptName  logical name for the prompt (e.g. {@code "requirement-generation"})
     * @param content     full prompt text
     * @param description human-readable change description
     * @param author      identifier of the person/process making the change
     * @return promise resolving to the version UUID (new or existing deduplicated)
     */
    public Promise<UUID> saveVersion(String promptName, String content,
                                     String description, String author) {
        String tenantId    = resolveTenantId();
        String contentHash = sha256(content);

        // Deduplicate: if same content already exists, return existing versionId
        return findByHash(tenantId, promptName, contentHash)
                .then(existing -> {
                    if (existing.isPresent()) {
                        log.debug("Prompt version already exists (no-op): name={} hash={}",
                                promptName, contentHash);
                        return Promise.of(UUID.fromString(
                                existing.get().get("versionId").toString()));
                    }

                    // Mark prior active version as inactive
                    return deactivateCurrent(tenantId, promptName)
                            .then(ignored -> {
                                UUID versionId = UUID.randomUUID();
                                String now     = Instant.now().toString();

                                Map<String, Object> doc = new HashMap<>();
                                doc.put("versionId",   versionId.toString());
                                doc.put("promptName",  promptName);
                                doc.put("content",     content);
                                doc.put("contentHash", contentHash);
                                doc.put("description", description != null ? description : "");
                                doc.put("author",      author != null ? author : "system");
                                doc.put("active",      true);
                                doc.put("createdAt",   now);

                                return client.save(tenantId, COLLECTION, doc)
                                        .map(ignored2 -> {
                                            log.info("Saved prompt version: name={} versionId={} hash={}",
                                                    promptName, versionId, contentHash);
                                            return versionId;
                                        });
                            });
                });
    }

    // ── Read operations ──────────────────────────────────────────────────────

    /**
     * Returns the currently active prompt version for the given prompt name.
     */
    public Promise<Optional<Map<String, Object>>> findActive(String promptName) {
        String tenantId = resolveTenantId();
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("promptName", promptName))
                .filter(DataCloudClient.Filter.eq("active", true))
                .limit(1)
                .build();
        return client.query(tenantId, COLLECTION, query)
                .map(entities -> entities.isEmpty()
                        ? Optional.empty()
                        : Optional.of(entities.get(0).data()));
    }

    /**
     * Returns a specific prompt version by its UUID.
     */
    public Promise<Optional<Map<String, Object>>> findById(@NotNull UUID versionId) {
        String tenantId = resolveTenantId();
        return client.findById(tenantId, COLLECTION, versionId.toString())
                .map(opt -> opt.map(DataCloudClient.Entity::data));
    }

    /**
     * Returns all versions of a named prompt, newest first.
     *
     * @param limit maximum number of versions to return
     */
    public Promise<List<Map<String, Object>>> listVersions(String promptName, int limit) {
        String tenantId = resolveTenantId();
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("promptName", promptName))
                .limit(limit)
                .build();
        return client.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(DataCloudClient.Entity::data)
                        .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Activates a specific version by ID, deactivating the current active version.
     */
    public Promise<Void> activate(@NotNull UUID versionId) {
        String tenantId = resolveTenantId();
        return client.findById(tenantId, COLLECTION, versionId.toString())
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(
                                new IllegalArgumentException("Version not found: " + versionId));
                    }
                    String promptName = opt.get().data().get("promptName").toString();
                    return deactivateCurrent(tenantId, promptName)
                            .then(ignored -> {
                                Map<String, Object> updated = new HashMap<>(opt.get().data());
                                updated.put("active", true);
                                return client.save(tenantId, COLLECTION, updated).toVoid();
                            });
                })
                .whenResult(() -> log.info("Activated prompt version: {}", versionId));
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private Promise<Optional<Map<String, Object>>> findByHash(String tenantId,
                                                               String promptName,
                                                               String hash) {
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("promptName", promptName))
                .filter(DataCloudClient.Filter.eq("contentHash", hash))
                .limit(1)
                .build();
        return client.query(tenantId, COLLECTION, query)
                .map(entities -> entities.isEmpty()
                        ? Optional.empty()
                        : Optional.of(entities.get(0).data()));
    }

    private Promise<Void> deactivateCurrent(String tenantId, String promptName) {
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("promptName", promptName))
                .filter(DataCloudClient.Filter.eq("active", true))
                .limit(1)
                .build();
        return client.query(tenantId, COLLECTION, query)
                .then(entities -> {
                    if (entities.isEmpty()) return Promise.complete();
                    DataCloudClient.Entity current = entities.get(0);
                    Map<String, Object> updated = new HashMap<>(current.data());
                    updated.put("active", false);
                    return client.save(tenantId, COLLECTION, updated).toVoid();
                });
    }

    private static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String resolveTenantId() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank() || "default-tenant".equals(tenantId)) {
            throw new SecurityException(
                    "PromptVersioningService requires an active tenant context.");
        }
        return tenantId;
    }
}
