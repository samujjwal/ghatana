/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Module — Conversation History Repository
 */
package com.ghatana.yappc.ai.history;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-Cloud-backed repository for YAPPC AI conversation history.
 *
 * <p>Persists conversation turns so that:
 * <ul>
 *   <li>Users can resume conversations across sessions</li>
 *   <li>Context can be reconstructed for follow-up prompts</li>
 *   <li>Quality and cost metrics can be computed over time</li>
 * </ul>
 *
 * <h2>Collection Schema</h2>
 * <pre>
 * ai-conversations/{tenantId}/{conversationId}:
 *   conversationId : UUID
 *   userId         : String
 *   projectId      : String (optional)
 *   feature        : String (e.g. "suggestion", "explanation", "generation")
 *   turns          : List of turn records
 *     - role        : "user" | "assistant" | "system"
 *     - content     : String
 *     - model       : String (only for assistant turns)
 *     - inputTokens : int
 *     - outputTokens: int
 *     - latencyMs   : long
 *     - timestamp   : ISO-8601
 *   createdAt      : ISO-8601 instant
 *   updatedAt      : ISO-8601 instant
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Durable conversation history persistence for YAPPC AI interactions
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(ConversationRepository.class);
    private static final String COLLECTION = "ai-conversations";

    private final DataCloudClient client;

    public ConversationRepository(@NotNull DataCloudClient client) {
        this.client = Objects.requireNonNull(client, "DataCloudClient must not be null");
    }

    // ── Write operations ─────────────────────────────────────────────────────

    /**
     * Creates a new conversation and returns its ID.
     *
     * @param userId    owner of the conversation
     * @param projectId optional project scope ({@code null} for user-level conversations)
     * @param feature   product feature that owns this conversation
     * @return promise resolving to the new conversation UUID
     */
    public Promise<UUID> createConversation(String userId, String projectId, String feature) {
        String tenantId        = resolveTenantId();
        UUID   conversationId  = UUID.randomUUID();
        String now             = Instant.now().toString();

        Map<String, Object> doc = new HashMap<>();
        doc.put("conversationId", conversationId.toString());
        doc.put("userId",         userId);
        doc.put("projectId",      projectId != null ? projectId : "");
        doc.put("feature",        feature);
        doc.put("turns",          List.of());
        doc.put("createdAt",      now);
        doc.put("updatedAt",      now);

        return client.save(tenantId, COLLECTION, doc)
                .map(ignored -> {
                    log.debug("Created conversation: tenantId={} conversationId={} userId={} feature={}",
                            tenantId, conversationId, userId, feature);
                    return conversationId;
                });
    }

    /**
     * Appends a turn to an existing conversation.
     *
     * @param conversationId the conversation to extend
     * @param role           {@code "user"}, {@code "assistant"}, or {@code "system"}
     * @param content        the text content of the turn
     * @param model          model used (null for user/system turns)
     * @param inputTokens    tokens consumed (0 for user turns)
     * @param outputTokens   tokens produced (0 for user turns)
     * @param latencyMs      time taken to generate the response (0 for user turns)
     */
    @SuppressWarnings("unchecked")
    public Promise<Void> appendTurn(UUID conversationId, String role, String content,
                                     String model, int inputTokens, int outputTokens, long latencyMs) {
        String tenantId = resolveTenantId();
        String now      = Instant.now().toString();

        Map<String, Object> turn = new HashMap<>();
        turn.put("role",         role);
        turn.put("content",      content);
        turn.put("model",        model != null ? model : "");
        turn.put("inputTokens",  inputTokens);
        turn.put("outputTokens", outputTokens);
        turn.put("latencyMs",    latencyMs);
        turn.put("timestamp",    now);

        return client.findById(tenantId, COLLECTION, conversationId.toString())
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Conversation not found: " + conversationId));
                    }
                    Map<String, Object> updated = new HashMap<>(opt.get().data());
                    List<Object> turns = new java.util.ArrayList<>(
                            (List<Object>) updated.getOrDefault("turns", List.of()));
                    turns.add(turn);
                    updated.put("conversationId", conversationId.toString());
                    updated.put("turns",      turns);
                    updated.put("updatedAt",  now);
                    return client.save(tenantId, COLLECTION, updated).toVoid();
                })
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        log.error("Failed to append turn to conversation {}: {}",
                                conversationId, ex.getMessage(), ex);
                    }
                });
    }

    // ── Read operations ──────────────────────────────────────────────────────

    /**
     * Returns a conversation record by ID (within the current tenant scope).
     */
    public Promise<Optional<Map<String, Object>>> findById(@NotNull UUID conversationId) {
        String tenantId = resolveTenantId();
        return client.findById(tenantId, COLLECTION, conversationId.toString())
                .map(opt -> opt.map(DataCloudClient.Entity::data));
    }

    /**
     * Lists conversations for a user, most-recently-updated first.
     *
     * @param userId user to query
     * @param limit  maximum number of records to return
     */
    public Promise<List<Map<String, Object>>> findByUser(String userId, int limit) {
        String tenantId = resolveTenantId();
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("userId", userId))
                .limit(limit)
                .build();
        return client.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(DataCloudClient.Entity::data)
                        .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Lists conversations for a project, most-recently-updated first.
     */
    public Promise<List<Map<String, Object>>> findByProject(String projectId, int limit) {
        String tenantId = resolveTenantId();
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("projectId", projectId))
                .limit(limit)
                .build();
        return client.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(DataCloudClient.Entity::data)
                        .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Deletes conversations not updated since the given cutoff (data retention).
     *
     * @param before ISO-8601 instant — records with {@code updatedAt} before this are deleted
     * @return count of deleted records
     */
    public Promise<Integer> deleteOlderThan(@NotNull Instant before) {
        String tenantId = resolveTenantId();
        log.info("Purging AI conversation records before {} for tenant {}", before, tenantId);
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.lt("updatedAt", before.toString()))
                .limit(1000)
                .build();
        return client.query(tenantId, COLLECTION, query)
                .then(entities -> {
                    List<Promise<Void>> deletes = entities.stream()
                            .map(entity -> client.delete(tenantId, COLLECTION, entity.id()))
                            .collect(java.util.stream.Collectors.toList());
                    return io.activej.promise.Promises.all(deletes)
                            .map(ignored -> entities.size());
                });
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static String resolveTenantId() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank() || "default-tenant".equals(tenantId)) {
            throw new SecurityException(
                    "ConversationRepository requires an active tenant context.");
        }
        return tenantId;
    }
}
