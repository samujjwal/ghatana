/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning.review;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent {@link HumanReviewQueue} backed by Data Cloud entities.
 *
 * <p>Review records are stored under a shared registry tenant so the queue can
 * answer cross-tenant admin queries without requiring a separate multi-tenant
 * query API from Data Cloud.
 *
 * @doc.type class
 * @doc.purpose Durable human review queue backed by Data Cloud
 * @doc.layer agent-learning
 * @doc.pattern Repository, Adapter
 */
public final class DataCloudHumanReviewQueue implements HumanReviewQueue {

    static final String COLLECTION = "aep_review_queue";
    static final String STORAGE_TENANT = "platform";
    private static final int QUERY_LIMIT = 10_000;
    private static final Set<ReviewStatus> ACTIVE_STATUSES = EnumSet.of(
        ReviewStatus.PENDING,
        ReviewStatus.IN_REVIEW
    );

    private final DataCloudClient dataCloudClient;
    private final ReviewNotificationSpi notificationSpi;

    public DataCloudHumanReviewQueue(@NotNull DataCloudClient dataCloudClient,
                                     @NotNull ReviewNotificationSpi notificationSpi) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient");
        this.notificationSpi = Objects.requireNonNull(notificationSpi, "notificationSpi");
    }

    public DataCloudHumanReviewQueue(@NotNull DataCloudClient dataCloudClient) {
        this(dataCloudClient, ReviewNotificationSpi.NOOP);
    }

    @Override
    public @NotNull Promise<ReviewItem> enqueue(@NotNull ReviewItem item) {
        Objects.requireNonNull(item, "item");
        return dataCloudClient.save(STORAGE_TENANT, COLLECTION, toRecord(item))
            .map(this::fromEntity)
            .whenResult(notificationSpi::onItemEnqueued);
    }

    @Override
    public @NotNull Promise<List<ReviewItem>> getPending(@Nullable ReviewFilter filter) {
        return queryAll()
            .then(items -> {
                int limit = filter != null && filter.limit() > 0 ? filter.limit() : QUERY_LIMIT;
                List<ReviewItem> pending = new ArrayList<>(Math.min(items.size(), limit));
                List<ReviewItem> expirable = new ArrayList<>();

                for (ReviewItem item : items) {
                    if (item.isExpired(Instant.now())) {
                        expirable.add(item);
                        continue;
                    }
                    if (isActive(item) && matchesFilter(item, filter)) {
                        pending.add(item);
                        if (pending.size() >= limit) {
                            break;
                        }
                    }
                }

                if (expirable.isEmpty()) {
                    return Promise.of(pending);
                }

                return normalizeExpiredItems(expirable).map(ignored -> pending);
            });
    }

    @Override
    public @NotNull Promise<List<ReviewItem>> listRecent(@Nullable ReviewFilter filter) {
        return queryAll()
            .then(this::normalizeExpiredItems)
            .map(items -> items.stream()
                .filter(item -> matchesFilter(item, filter))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .limit(filter != null && filter.limit() > 0 ? filter.limit() : QUERY_LIMIT)
                .toList());
    }

    @Override
    public @NotNull Promise<@Nullable ReviewItem> getById(@NotNull String reviewId) {
        Objects.requireNonNull(reviewId, "reviewId");
        return dataCloudClient.findById(STORAGE_TENANT, COLLECTION, reviewId)
            .then(entity -> entity.isPresent()
                ? expireIfNeeded(fromEntity(entity.get())).map(item -> (ReviewItem) item)
                : Promise.of(null));
    }

    @Override
    public @NotNull Promise<ReviewItem> approve(@NotNull String reviewId, @NotNull ReviewDecision decision) {
        Objects.requireNonNull(decision, "decision");
        return loadActiveItem(reviewId)
            .map(item -> {
                item.markApproved(decision);
                return item;
            })
            .then(this::saveUpdated)
            .whenResult(notificationSpi::onItemApproved);
    }

    @Override
    public @NotNull Promise<ReviewItem> reject(@NotNull String reviewId, @NotNull ReviewDecision decision) {
        Objects.requireNonNull(decision, "decision");
        return loadActiveItem(reviewId)
            .map(item -> {
                item.markRejected(decision);
                return item;
            })
            .then(this::saveUpdated)
            .whenResult(notificationSpi::onItemRejected);
    }

    @Override
    public @NotNull Promise<ReviewItem> escalate(@NotNull String reviewId) {
        return loadActiveItem(reviewId)
            .map(item -> {
                item.markEscalated();
                return item;
            })
            .then(this::saveUpdated)
            .whenResult(notificationSpi::onItemEscalated);
    }

    @Override
    public @NotNull Promise<List<ReviewItem>> findOverdue(long thresholdSeconds, @Nullable String tenantId) {
        Instant cutoff = Instant.now().minusSeconds(thresholdSeconds);
        return queryAll()
            .then(this::normalizeExpiredItems)
            .map(items -> items.stream()
                .filter(this::isActive)
                .filter(item -> item.getCreatedAt().isBefore(cutoff))
                .filter(item -> tenantId == null || tenantId.equals(item.getTenantId()))
                .toList());
    }

    @Override
    public @NotNull Promise<Long> pendingCount() {
        return queryAll()
            .then(this::normalizeExpiredItems)
            .map(items -> items.stream().filter(this::isActive).count());
    }

    private Promise<ReviewItem> loadActiveItem(String reviewId) {
        return getById(reviewId).then(item -> {
            if (item == null) {
                return Promise.ofException(new IllegalArgumentException("Review item not found: " + reviewId));
            }
            if (!isActive(item)) {
                return Promise.ofException(new IllegalStateException(
                    "Review item " + reviewId + " is not pending: " + item.getStatus()));
            }
            return Promise.of(item);
        });
    }

    private Promise<ReviewItem> saveUpdated(ReviewItem item) {
        return dataCloudClient.save(STORAGE_TENANT, COLLECTION, toRecord(item))
            .map(this::fromEntity);
    }

    private Promise<List<ReviewItem>> normalizeExpiredItems(List<ReviewItem> items) {
        List<ReviewItem> normalized = new ArrayList<>(items.size());
        return Promises.all(items.stream()
                .map(item -> expireIfNeeded(item).map(expiredItem -> {
                    normalized.add(expiredItem);
                    return expiredItem;
                }))
                .toList())
            .map(ignored -> normalized);
    }

    private Promise<ReviewItem> expireIfNeeded(ReviewItem item) {
        if (isActive(item) && item.isExpired(Instant.now())) {
            item.markExpired();
            return saveUpdated(item);
        }
        return Promise.of(item);
    }

    private Promise<List<ReviewItem>> queryAll() {
        return dataCloudClient.query(STORAGE_TENANT, COLLECTION,
                DataCloudClient.Query.builder().limit(QUERY_LIMIT).build())
            .map(entities -> entities.stream().map(this::fromEntity).toList());
    }

    private boolean isActive(ReviewItem item) {
        return ACTIVE_STATUSES.contains(item.getStatus());
    }

    private boolean matchesFilter(ReviewItem item, @Nullable ReviewFilter filter) {
        if (filter == null) {
            return true;
        }
        if (filter.tenantId() != null && !filter.tenantId().equals(item.getTenantId())) {
            return false;
        }
        if (filter.itemType() != null && filter.itemType() != item.getItemType()) {
            return false;
        }
        if (filter.maxConfidence() != null && item.getConfidenceScore() > filter.maxConfidence()) {
            return false;
        }
        if (filter.assignedTo() != null && !filter.assignedTo().equals(item.getAssignedTo())) {
            return false;
        }
        return true;
    }

    private Map<String, Object> toRecord(ReviewItem item) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", item.getReviewId());
        record.put("reviewId", item.getReviewId());
        record.put("tenantId", item.getTenantId());
        record.put("skillId", item.getSkillId());
        record.put("proposedVersion", item.getProposedVersion());
        record.put("itemType", item.getItemType().name());
        record.put("confidenceScore", item.getConfidenceScore());
        record.put("context", item.getContext());
        record.put("createdAt", item.getCreatedAt().toString());
        putIfNotNull(record, "expiresAt", item.getExpiresAt() != null ? item.getExpiresAt().toString() : null);
        record.put("status", item.getStatus().name());
        putIfNotNull(record, "evaluationSummary", item.getEvaluationSummary());
        putIfNotNull(record, "assignedTo", item.getAssignedTo());
        if (item.getDecidedAt() != null) {
            record.put("decidedAt", item.getDecidedAt().toString());
        }
        if (item.getDecision() != null) {
            record.put("decision", Map.of(
                "reviewer", item.getDecision().reviewer(),
                "rationale", item.getDecision().rationale(),
                "decidedAt", item.getDecision().decidedAt().toString(),
                "notes", item.getDecision().notes() != null ? item.getDecision().notes() : ""
            ));
        }
        return record;
    }

    private void putIfNotNull(Map<String, Object> record, String key, @Nullable Object value) {
        if (value != null) {
            record.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private ReviewItem fromEntity(DataCloudClient.Entity entity) {
        Map<String, Object> data = entity.data();
        Map<String, Object> context = data.get("context") instanceof Map<?, ?> rawContext
            ? (Map<String, Object>) rawContext
            : Map.of();
        ReviewDecision decision = null;
        if (data.get("decision") instanceof Map<?, ?> rawDecision) {
            decision = new ReviewDecision(
                requiredString(rawDecision.get("reviewer")),
                requiredString(rawDecision.get("rationale")),
                parseInstant(rawDecision.get("decidedAt"), entity.updatedAt()),
                nullableString(rawDecision.get("notes"))
            );
        }

        return ReviewItem.builder()
            .reviewId(requiredString(data.getOrDefault("reviewId", entity.id())))
            .tenantId(requiredString(data.get("tenantId")))
            .skillId(requiredString(data.get("skillId")))
            .proposedVersion(requiredString(data.get("proposedVersion")))
            .itemType(parseItemType(data.get("itemType")))
            .confidenceScore(parseDouble(data.get("confidenceScore")))
            .evaluationSummary(nullableString(data.get("evaluationSummary")))
            .context(context)
            .createdAt(parseInstant(data.get("createdAt"), entity.createdAt()))
            .expiresAt(data.get("expiresAt") != null ? parseInstant(data.get("expiresAt"), entity.updatedAt()) : null)
            .status(parseStatus(data.get("status")))
            .decision(decision)
            .decidedAt(data.get("decidedAt") != null ? parseInstant(data.get("decidedAt"), entity.updatedAt()) : null)
            .assignedTo(nullableString(data.get("assignedTo")))
            .build();
    }

    private ReviewItemType parseItemType(Object value) {
        return value != null ? ReviewItemType.valueOf(value.toString()) : ReviewItemType.POLICY;
    }

    private ReviewStatus parseStatus(Object value) {
        return value != null ? ReviewStatus.valueOf(value.toString()) : ReviewStatus.PENDING;
    }

    private Instant parseInstant(Object value, Instant fallback) {
        return value != null ? Instant.parse(value.toString()) : fallback;
    }

    private String requiredString(Object value) {
        return Objects.requireNonNull(value, "value").toString();
    }

    private String nullableString(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = value.toString();
        return stringValue.isBlank() ? null : stringValue;
    }

    private double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return value != null ? Double.parseDouble(value.toString()) : 0.0d;
    }
}
