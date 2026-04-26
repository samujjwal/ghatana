/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.consent;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import io.activej.promise.Promise;
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
 * T-23: DataCloud-backed {@link ConsentDecisionStore} for production environments.
 *
 * <p>Persists consent decisions as entities in the {@value #COLLECTION} DataCloud
 * collection. Each entity represents one consent decision. Tenant isolation is
 * enforced via the {@code tenantId} field in every entity.
 *
 * @doc.type class
 * @doc.purpose DataCloud-backed server-side consent decision store
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class DataCloudConsentDecisionStore implements ConsentDecisionStore {

    private static final Logger log = LoggerFactory.getLogger(DataCloudConsentDecisionStore.class);
    static final String COLLECTION = "aep_consent_decisions";

    private final DataCloudClient dataCloud;

    /**
     * @param dataCloud the DataCloud client (required)
     */
    public DataCloudConsentDecisionStore(DataCloudClient dataCloud) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud required");
    }

    @Override
    public Promise<ConsentRecord> recordDecision(
            String tenantId,
            String userId,
            String status,
            List<String> purposes,
            Instant decidedAt) {
        String consentId = UUID.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("consentId", consentId);
        data.put("tenantId", tenantId);
        data.put("userId", userId);
        data.put("status", status);
        data.put("purposes", purposes != null ? purposes : List.of());
        data.put("decidedAt", decidedAt.toString());

        return dataCloud.save(tenantId, COLLECTION, data)
                .map(ignored -> new ConsentRecord(consentId, tenantId, userId, status,
                        purposes != null ? purposes : List.of(), decidedAt))
                .then(Promise::of, e -> {
                    log.error("[consent-store] recordDecision failed tenantId={} userId={}: {}",
                            tenantId, userId, e.getMessage(), e);
                    return Promise.ofException(e);
                });
    }

    @Override
    public Promise<Optional<ConsentRecord>> getDecision(String tenantId, String userId) {
        Query query = Query.builder()
                .filter(Filter.eq("userId", userId))
                .limit(1)
                .build();
        return dataCloud.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .findFirst()
                        .map(this::toRecord))
                .then(Promise::of, e -> {
                    log.warn("[consent-store] getDecision failed tenantId={} userId={}: {}",
                            tenantId, userId, e.getMessage());
                    return Promise.of(Optional.empty());
                });
    }

    @Override
    public Promise<List<ConsentRecord>> listDecisions(String tenantId, int limit, int offset) {
        Query query = Query.limit(limit);
        return dataCloud.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(this::toRecord)
                        .skip(offset)
                        .toList())
                .then(Promise::of, e -> {
                    log.warn("[consent-store] listDecisions failed tenantId={}: {}", tenantId, e.getMessage());
                    return Promise.of(List.of());
                });
    }

    @SuppressWarnings("unchecked")
    private ConsentRecord toRecord(Entity entity) {
        Map<String, Object> d = entity.data();
        String consentId = (String) d.getOrDefault("consentId", entity.id());
        String tenantId  = (String) d.getOrDefault("tenantId", "");
        String userId    = (String) d.getOrDefault("userId", "");
        String status    = (String) d.getOrDefault("status", "unknown");
        List<String> purposes = d.get("purposes") instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : List.of();
        Instant decidedAt;
        try {
            decidedAt = Instant.parse((String) d.get("decidedAt"));
        } catch (Exception e) {
            decidedAt = Instant.EPOCH;
        }
        return new ConsentRecord(consentId, tenantId, userId, status, purposes, decidedAt);
    }
}
