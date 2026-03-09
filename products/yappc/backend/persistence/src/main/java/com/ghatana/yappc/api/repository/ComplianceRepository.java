/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.products.yappc.domain.model.ComplianceAssessment;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for ComplianceAssessment persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Compliance assessment repository for CRUD operations
 * @doc.layer repository
 * @doc.pattern Repository
 */
public interface ComplianceRepository {

    /** Save a compliance assessment. */
    Promise<ComplianceAssessment> save(ComplianceAssessment assessment);

    /** Find a compliance assessment by ID. */
    Promise<ComplianceAssessment> findById(UUID workspaceId, UUID id);

    /** Find assessments by project. */
    Promise<List<ComplianceAssessment>> findByProject(UUID workspaceId, UUID projectId);

    /** Find assessments by framework. */
    Promise<List<ComplianceAssessment>> findByFramework(UUID workspaceId, UUID frameworkId);

    /** Find assessments by project and framework. */
    Promise<List<ComplianceAssessment>> findByProjectAndFramework(UUID workspaceId, UUID projectId, UUID frameworkId);

    /** Find assessments by status. */
    Promise<List<ComplianceAssessment>> findByStatus(UUID workspaceId, String status);

    /** Find assessments by assessment type. */
    Promise<List<ComplianceAssessment>> findByAssessmentType(UUID workspaceId, String assessmentType);

    /** Count assessments by status within a project. */
    Promise<Long> countByStatus(UUID workspaceId, UUID projectId, String status);

    /** Delete an assessment. */
    Promise<Void> delete(UUID workspaceId, UUID id);

    /** Check if an assessment exists. */
    Promise<Boolean> exists(UUID workspaceId, UUID id);
}
