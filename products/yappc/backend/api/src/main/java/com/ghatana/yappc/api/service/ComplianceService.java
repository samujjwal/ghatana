/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.products.yappc.domain.model.ComplianceAssessment;
import com.ghatana.yappc.api.repository.ComplianceRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing compliance assessments.
 *
 * @doc.type class
 * @doc.purpose Business logic for compliance assessment operations
 * @doc.layer service
 * @doc.pattern Service
 */
public class ComplianceService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceService.class);

    private final ComplianceRepository repository;

    @Inject
    public ComplianceService(ComplianceRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new compliance assessment.
     */
    public Promise<ComplianceAssessment> createAssessment(UUID workspaceId, CreateAssessmentInput input) {
        logger.info("Creating assessment for framework: {} in workspace: {}", input.frameworkId(), workspaceId);

        ComplianceAssessment assessment = ComplianceAssessment.of(workspaceId, input.frameworkId());
        if (input.projectId() != null) {
            assessment.setProjectId(input.projectId());
        }
        if (input.assessorName() != null) {
            assessment.setAssessorName(input.assessorName());
        }
        if (input.assessmentType() != null) {
            assessment.setAssessmentType(input.assessmentType());
        }
        if (input.dueDate() != null) {
            assessment.setDueDate(input.dueDate());
        }
        if (input.notes() != null) {
            assessment.setNotes(input.notes());
        }

        return repository.save(assessment);
    }

    /**
     * Gets a compliance assessment by ID.
     */
    public Promise<Optional<ComplianceAssessment>> getAssessment(UUID workspaceId, UUID assessmentId) {
        return repository.findById(workspaceId, assessmentId)
            .map(Optional::ofNullable);
    }

    /**
     * Lists assessments for a project.
     */
    public Promise<List<ComplianceAssessment>> listProjectAssessments(UUID workspaceId, UUID projectId) {
        return repository.findByProject(workspaceId, projectId);
    }

    /**
     * Lists assessments by framework.
     */
    public Promise<List<ComplianceAssessment>> listByFramework(UUID workspaceId, UUID frameworkId) {
        return repository.findByFramework(workspaceId, frameworkId);
    }

    /**
     * Lists assessments for a project and framework.
     */
    public Promise<List<ComplianceAssessment>> listProjectFrameworkAssessments(
            UUID workspaceId, UUID projectId, UUID frameworkId) {
        return repository.findByProjectAndFramework(workspaceId, projectId, frameworkId);
    }

    /**
     * Starts an assessment.
     */
    public Promise<ComplianceAssessment> startAssessment(UUID workspaceId, UUID assessmentId, String assessorName) {
        logger.info("Starting assessment: {} by {}", assessmentId, assessorName);

        return repository.findById(workspaceId, assessmentId)
            .then(assessment -> {
                if (assessment == null) {
                    return Promise.ofException(new IllegalArgumentException("Assessment not found"));
                }
                assessment.setStatus("IN_PROGRESS");
                assessment.setStartedAt(java.time.Instant.now());
                assessment.setAssessorName(assessorName);
                assessment.setAssessmentDate(LocalDate.now());
                return repository.save(assessment);
            });
    }

    /**
     * Completes an assessment with results.
     */
    public Promise<ComplianceAssessment> completeAssessment(UUID workspaceId, UUID assessmentId,
                                                             CompleteAssessmentInput input) {
        logger.info("Completing assessment: {}", assessmentId);

        return repository.findById(workspaceId, assessmentId)
            .then(assessment -> {
                if (assessment == null) {
                    return Promise.ofException(new IllegalArgumentException("Assessment not found"));
                }
                assessment.setPassedControls(input.passedControls());
                assessment.setFailedControls(input.failedControls());
                assessment.setNaControls(input.naControls());
                assessment.setTotalControls(input.passedControls() + input.failedControls() + input.naControls());
                assessment.complete();
                if (input.notes() != null) {
                    assessment.setNotes(input.notes());
                }
                return repository.save(assessment);
            });
    }

    /**
     * Gets compliance statistics for a project.
     */
    public Promise<ComplianceStats> getComplianceStatistics(UUID workspaceId, UUID projectId) {
        return repository.findByProject(workspaceId, projectId)
            .map(assessments -> {
                ComplianceStats stats = new ComplianceStats();
                stats.totalAssessments = assessments.size();
                stats.completedCount = assessments.stream()
                    .filter(a -> "COMPLETED".equalsIgnoreCase(a.getStatus())).count();
                stats.inProgressCount = assessments.stream()
                    .filter(a -> "IN_PROGRESS".equalsIgnoreCase(a.getStatus())).count();
                stats.averageScore = assessments.stream()
                    .filter(a -> "COMPLETED".equalsIgnoreCase(a.getStatus()))
                    .mapToInt(ComplianceAssessment::getScore)
                    .average()
                    .orElse(0.0);
                stats.totalPassed = assessments.stream()
                    .mapToInt(ComplianceAssessment::getPassedControls).sum();
                stats.totalFailed = assessments.stream()
                    .mapToInt(ComplianceAssessment::getFailedControls).sum();
                return stats;
            });
    }

    /**
     * Deletes an assessment.
     */
    public Promise<Void> deleteAssessment(UUID workspaceId, UUID assessmentId) {
        logger.info("Deleting assessment: {} for workspace: {}", assessmentId, workspaceId);
        return repository.delete(workspaceId, assessmentId);
    }

    // ========== Input Records ==========

    public record CreateAssessmentInput(
        UUID frameworkId,
        UUID projectId,
        String assessorName,
        String assessmentType,
        LocalDate dueDate,
        String notes
    ) {}

    public record CompleteAssessmentInput(
        int score,
        int passedControls,
        int failedControls,
        int naControls,
        String notes
    ) {}

    // ========== Stats ==========

    public static class ComplianceStats {
        public int totalAssessments;
        public long completedCount;
        public long inProgressCount;
        public double averageScore;
        public int totalPassed;
        public int totalFailed;
    }
}
