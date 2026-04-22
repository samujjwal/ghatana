/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Module — Conversation History Repository
 */
package com.ghatana.yappc.ai.history;

import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
 * <p><b>Collection Schema</b></p>
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
@SuppressWarnings("deprecation") // Uses deprecated YappcDataCloudRepository.findByFilter; migrate to typed query methods
public final class ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(ConversationRepository.class);

    private final YappcDataCloudRepository<Conversation> repository;

    public ConversationRepository(@NotNull YappcDataCloudRepository<Conversation> repository) {
        this.repository = Objects.requireNonNull(repository, "YappcDataCloudRepository must not be null");
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
        UUID conversationId = UUID.randomUUID();
        Instant now = Instant.now();

        Conversation conversation = new Conversation(
                conversationId,
                userId,
                projectId,
                feature,
                new ArrayList<>(),
                now,
                now
        );

        return repository.save(conversation)
                .map(saved -> {
                    log.debug("Created conversation: conversationId={} userId={} feature={}",
                            conversationId, userId, feature);
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
    public Promise<Void> appendTurn(UUID conversationId, String role, String content,
                                     String model, int inputTokens, int outputTokens, long latencyMs) {
        Instant now = Instant.now();

        Conversation.Turn turn = new Conversation.Turn(
                role,
                content,
                model != null ? model : "",
                inputTokens,
                outputTokens,
                latencyMs,
                now
        );

        return repository.findById(conversationId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Conversation not found: " + conversationId));
                    }
                    Conversation existing = opt.get();
                    List<Conversation.Turn> updatedTurns = new ArrayList<>(existing.turns());
                    updatedTurns.add(turn);
                    
                    Conversation updated = new Conversation(
                            existing.conversationId(),
                            existing.userId(),
                            existing.projectId(),
                            existing.feature(),
                            updatedTurns,
                            existing.createdAt(),
                            now
                    );
                    return repository.save(updated).toVoid();
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
    public Promise<Optional<Conversation>> findById(@NotNull UUID conversationId) {
        return repository.findById(conversationId);
    }

    /**
     * Lists conversations for a user, most-recently-updated first.
     *
     * @param userId user to query
     * @param limit  maximum number of records to return
     */
    public Promise<List<Conversation>> findByUser(String userId, int limit) {
        return repository.findByField("userId", userId)
                .map(conversations -> conversations.stream()
                        .limit(limit)
                        .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Lists conversations for a project, most-recently-updated first.
     */
    public Promise<List<Conversation>> findByProject(String projectId, int limit) {
        return repository.findByField("projectId", projectId)
                .map(conversations -> conversations.stream()
                        .limit(limit)
                        .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Deletes conversations not updated since the given cutoff (data retention).
     *
     * @param before ISO-8601 instant — records with {@code updatedAt} before this are deleted
     * @return count of deleted records
     */
    public Promise<Integer> deleteOlderThan(@NotNull Instant before) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Purging AI conversation records before {} for tenant {}", before, tenantId);
        
        // Memory-safe implementation: process records in batches to avoid loading all into memory
        final int BATCH_SIZE = 100;
        return deleteOlderThanBatched(before, 0, BATCH_SIZE, 0);
    }
    
    /**
     * Recursive batched deletion to avoid memory issues with large datasets.
     * Processes records in batches and recursively continues until no more records match.
     */
    private Promise<Integer> deleteOlderThanBatched(@NotNull Instant before, int offset, int batchSize, int deletedSoFar) {
        // Use findByFilter with pagination to get a batch of records
        return repository.findByFilter(java.util.Map.of(), "updatedAt", batchSize, offset)
                .then(conversations -> {
                    if (conversations.isEmpty()) {
                        // No more records to process
                        return Promise.of(deletedSoFar);
                    }
                    
                    // Filter current batch for records to delete
                    List<Conversation> toDelete = conversations.stream()
                            .filter(c -> c.updatedAt().isBefore(before))
                            .collect(java.util.stream.Collectors.toList());
                    
                    if (toDelete.isEmpty()) {
                        // No records in this batch need deletion, try next batch
                        return deleteOlderThanBatched(before, offset + batchSize, batchSize, deletedSoFar);
                    }
                    
                    // Delete matching records in this batch
                    List<Promise<Void>> deletes = toDelete.stream()
                            .map(c -> repository.deleteById(c.conversationId()))
                            .collect(java.util.stream.Collectors.toList());
                    
                    return io.activej.promise.Promises.all(deletes)
                            .then(ignored -> {
                                int newDeletedCount = deletedSoFar + toDelete.size();
                                log.debug("Deleted batch of {} conversation records, total deleted: {}", 
                                         toDelete.size(), newDeletedCount);
                                
                                // Continue with next batch
                                return deleteOlderThanBatched(before, offset + batchSize, batchSize, newDeletedCount);
                            });
                });
    }
}
