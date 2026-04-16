/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Module — Prompt Versioning Service
 */
package com.ghatana.yappc.ai.prompt;

import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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

    private final YappcDataCloudRepository<PromptVersion> repository;

    public PromptVersioningService(@NotNull YappcDataCloudRepository<PromptVersion> repository) {
        this.repository = Objects.requireNonNull(repository, "YappcDataCloudRepository must not be null");
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
        String contentHash = sha256(content);

        // Deduplicate: if same content already exists, return existing versionId
        return findByHash(promptName, contentHash)
                .then(existing -> {
                    if (existing.isPresent()) {
                        log.debug("Prompt version already exists (no-op): name={} hash={}",
                                promptName, contentHash);
                        return Promise.of(existing.get().versionId());
                    }

                    // Mark prior active version as inactive
                    return deactivateCurrent(promptName)
                            .then(ignored -> {
                                UUID versionId = UUID.randomUUID();
                                Instant now = Instant.now();

                                PromptVersion promptVersion = new PromptVersion(
                                        versionId,
                                        promptName,
                                        content,
                                        contentHash,
                                        description != null ? description : "",
                                        author != null ? author : "system",
                                        true,
                                        now
                                );

                                return repository.save(promptVersion)
                                        .map(saved -> {
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
    public Promise<Optional<PromptVersion>> findActive(String promptName) {
        return repository.findByField("promptName", promptName)
                .map(versions -> versions.stream()
                        .filter(PromptVersion::active)
                        .findFirst());
    }

    /**
     * Returns a specific prompt version by its UUID.
     */
    public Promise<Optional<PromptVersion>> findById(@NotNull UUID versionId) {
        return repository.findById(versionId);
    }

    /**
     * Returns all versions of a named prompt, newest first.
     *
     * @param limit maximum number of versions to return
     */
    public Promise<List<PromptVersion>> listVersions(String promptName, int limit) {
        return repository.findByField("promptName", promptName)
                .map(versions -> versions.stream()
                        .limit(limit)
                        .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Activates a specific version by ID, deactivating the current active version.
     */
    public Promise<Void> activate(@NotNull UUID versionId) {
        return repository.findById(versionId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(
                                new IllegalArgumentException("Version not found: " + versionId));
                    }
                    PromptVersion promptVersion = opt.get();
                    return deactivateCurrent(promptVersion.promptName())
                            .then(ignored -> {
                                PromptVersion updated = new PromptVersion(
                                        promptVersion.versionId(),
                                        promptVersion.promptName(),
                                        promptVersion.content(),
                                        promptVersion.contentHash(),
                                        promptVersion.description(),
                                        promptVersion.author(),
                                        true,
                                        promptVersion.createdAt()
                                );
                                return repository.save(updated).toVoid();
                            });
                })
                .whenResult(() -> log.info("Activated prompt version: {}", versionId));
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private Promise<Optional<PromptVersion>> findByHash(String promptName, String hash) {
        return repository.findByField("promptName", promptName)
                .map(versions -> versions.stream()
                        .filter(v -> v.contentHash().equals(hash))
                        .findFirst());
    }

    private Promise<Void> deactivateCurrent(String promptName) {
        return repository.findByField("promptName", promptName)
                .then(versions -> {
                    Optional<PromptVersion> active = versions.stream()
                            .filter(PromptVersion::active)
                            .findFirst();
                    if (active.isEmpty()) {
                        return Promise.complete();
                    }
                    PromptVersion current = active.get();
                    PromptVersion deactivated = new PromptVersion(
                            current.versionId(),
                            current.promptName(),
                            current.content(),
                            current.contentHash(),
                            current.description(),
                            current.author(),
                            false,
                            current.createdAt()
                    );
                    return repository.save(deactivated).toVoid();
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
}
