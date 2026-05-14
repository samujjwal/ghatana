/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.safety;

import com.ghatana.agent.safety.SafetyPolicy;
import com.ghatana.agent.safety.SafetyPolicyRepository;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Cloud-backed repository for safety policies.
 *
 * <p>This implementation uses EntityRepository for durable persistence with tenant isolation.
 *
 * @doc.type class
 * @doc.purpose Data Cloud repository for safety policies
 * @doc.layer data-cloud
 * @doc.pattern Repository Implementation
 */
public final class DataCloudSafetyPolicyRepository implements SafetyPolicyRepository {

    private static final String COLLECTION_SAFETY_POLICIES = "agent-safety-policies";

    private final EntityRepository entityRepository;

    /**
     * Creates a new DataCloudSafetyPolicyRepository.
     *
     * @param entityRepository Data Cloud entity repository
     */
    public DataCloudSafetyPolicyRepository(@NotNull EntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    @Override
    @NotNull
    public Promise<Void> save(@NotNull SafetyPolicy policy) {
        String tenantId = policy.tenantId();
        Map<String, Object> dataMap = SafetyPolicyMapper.toDataMap(policy);

        // Check if policy already exists
        return entityRepository.findAll(tenantId, COLLECTION_SAFETY_POLICIES,
                Map.of("policyId", policy.policyId()), null, 0, 1)
                .then(entities -> {
                    UUID entityId = entities.isEmpty() ? UUID.randomUUID() : entities.get(0).getId();

                    Entity entity = Entity.builder()
                            .id(entityId)
                            .tenantId(tenantId)
                            .collectionName(COLLECTION_SAFETY_POLICIES)
                            .data(dataMap)
                            .createdBy("system")
                            .build();

                    return entityRepository.save(tenantId, entity).map(saved -> null);
                });
    }

    @Override
    @NotNull
    public Promise<Optional<SafetyPolicy>> findById(
            @NotNull String tenantId,
            @NotNull String policyId) {
        return entityRepository.findAll(tenantId, COLLECTION_SAFETY_POLICIES,
                Map.of("policyId", policyId), null, 0, 1)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.of(Optional.empty());
                    }
                    SafetyPolicy policy = SafetyPolicyMapper.fromDataMap(entities.get(0).getData());
                    return Promise.of(Optional.of(policy));
                });
    }

    @Override
    @NotNull
    public Promise<Optional<SafetyPolicy>> findActive(@NotNull String tenantId) {
        return entityRepository.findAll(tenantId, COLLECTION_SAFETY_POLICIES,
                Map.of("active", true), null, 0, 1)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.of(Optional.empty());
                    }
                    SafetyPolicy policy = SafetyPolicyMapper.fromDataMap(entities.get(0).getData());
                    return Promise.of(Optional.of(policy));
                });
    }

    @Override
    @NotNull
    public Promise<Void> delete(
            @NotNull String tenantId,
            @NotNull String policyId) {
        return entityRepository.findAll(tenantId, COLLECTION_SAFETY_POLICIES,
                Map.of("policyId", policyId), null, 0, 1)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.of(null);
                    }
                    return entityRepository.delete(tenantId, COLLECTION_SAFETY_POLICIES, entities.get(0).getId());
                });
    }
}
