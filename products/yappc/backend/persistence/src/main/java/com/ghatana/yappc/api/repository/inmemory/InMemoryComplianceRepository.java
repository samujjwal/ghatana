/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.inmemory;

import com.ghatana.products.yappc.domain.model.ComplianceAssessment;
import com.ghatana.yappc.api.repository.ComplianceRepository;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ComplianceRepository for development and testing.
 *
 * @doc.type class
 * @doc.purpose In-memory compliance assessment storage
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class InMemoryComplianceRepository implements ComplianceRepository {

    private final Map<UUID, Map<UUID, ComplianceAssessment>> workspaceAssessments = new ConcurrentHashMap<>();

    @Override
    public Promise<ComplianceAssessment> save(ComplianceAssessment assessment) {
        if (assessment.getId() == null) {
            assessment.setId(UUID.randomUUID());
        }
        if (assessment.getCreatedAt() == null) {
            assessment.setCreatedAt(Instant.now());
        }
        assessment.setUpdatedAt(Instant.now());

        workspaceAssessments
            .computeIfAbsent(assessment.getWorkspaceId(), k -> new ConcurrentHashMap<>())
            .put(assessment.getId(), assessment);
        return Promise.of(assessment);
    }

    @Override
    public Promise<ComplianceAssessment> findById(UUID workspaceId, UUID id) {
        return Promise.of(workspaceAssessments.getOrDefault(workspaceId, Map.of()).get(id));
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByProject(UUID workspaceId, UUID projectId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> Objects.equals(a.getProjectId(), projectId))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByFramework(UUID workspaceId, UUID frameworkId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> Objects.equals(a.getFrameworkId(), frameworkId))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByProjectAndFramework(UUID workspaceId, UUID projectId, UUID frameworkId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> Objects.equals(a.getProjectId(), projectId) && Objects.equals(a.getFrameworkId(), frameworkId))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByStatus(UUID workspaceId, String status) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> status.equalsIgnoreCase(a.getStatus()))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByAssessmentType(UUID workspaceId, String assessmentType) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> assessmentType.equalsIgnoreCase(a.getAssessmentType()))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<Long> countByStatus(UUID workspaceId, UUID projectId, String status) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> Objects.equals(a.getProjectId(), projectId))
            .filter(a -> status.equalsIgnoreCase(a.getStatus()))
            .count());
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        Map<UUID, ComplianceAssessment> assessments = workspaceAssessments.get(workspaceId);
        if (assessments != null) {
            assessments.remove(id);
        }
        return Promise.of(null);
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.of(workspaceAssessments.getOrDefault(workspaceId, Map.of()).containsKey(id));
    }

    private List<ComplianceAssessment> getAll(UUID workspaceId) {
        return new ArrayList<>(workspaceAssessments.getOrDefault(workspaceId, Map.of()).values());
    }
}
