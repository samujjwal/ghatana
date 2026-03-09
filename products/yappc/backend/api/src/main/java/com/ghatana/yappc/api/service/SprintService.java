/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.domain.Sprint;
import com.ghatana.yappc.api.domain.Sprint.*;
import com.ghatana.yappc.api.repository.SprintRepository;
import com.ghatana.yappc.api.repository.StoryRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Service for managing sprints.
 *
 * <p><b>Purpose</b><br>
 * Implements business logic for sprint management, including creation, lifecycle
 * transitions, and velocity tracking.
 *
 * @doc.type class
 * @doc.purpose Sprint management business logic
 * @doc.layer service
 * @doc.pattern Service
 */
public class SprintService {

    private static final Logger logger = LoggerFactory.getLogger(SprintService.class);

    private final SprintRepository sprintRepository;
    private final StoryRepository storyRepository;
    private final AuditService auditService;

    @Inject
    public SprintService(SprintRepository sprintRepository, 
                        StoryRepository storyRepository, 
                        AuditService auditService) {
        this.sprintRepository = sprintRepository;
        this.storyRepository = storyRepository;
        this.auditService = auditService;
    }

    /**
     * Creates a new sprint.
     */
    public Promise<Sprint> createSprint(String tenantId, CreateSprintInput input) {
        logger.info("Creating sprint '{}' for project {}", input.name(), input.projectId());

        return sprintRepository.findByProject(tenantId, input.projectId())
                .then(existingSprints -> {
                    int nextNumber = existingSprints.size() + 1;

                    Sprint sprint = new Sprint();
                    sprint.setId(UUID.randomUUID());
                    sprint.setTenantId(tenantId);
                    sprint.setProjectId(input.projectId());
                    sprint.setSprintNumber(nextNumber);
                    sprint.setName(input.name() != null ? input.name() : "Sprint " + nextNumber);
                    sprint.setGoals(input.goals() != null ? input.goals() : new ArrayList<>());
                    sprint.setStatus(SprintStatus.PLANNING);
                    sprint.setStartDate(toInstant(input.startDate()));
                    sprint.setEndDate(toInstant(input.endDate()));
                    sprint.setTeamCapacity(input.teamCapacity() != null ? input.teamCapacity() : 0);
                    sprint.setPlannedVelocity(input.teamCapacity() != null ? input.teamCapacity() : 0);
                    sprint.setCreatedBy(input.createdBy());
                    sprint.setCreatedAt(Instant.now());
                    sprint.setUpdatedAt(Instant.now());

                    return sprintRepository.save(sprint);
                });
    }

    /**
     * Gets a sprint by ID.
     */
    public Promise<Optional<Sprint>> getSprint(String tenantId, UUID sprintId) {
        return sprintRepository.findById(tenantId, sprintId);
    }

    /**
     * Gets the current active sprint for a project.
     */
    public Promise<Optional<Sprint>> getCurrentSprint(String tenantId, UUID projectId) {
        return sprintRepository.findCurrentSprint(tenantId, projectId.toString());
    }

    /**
     * Lists all sprints for a project.
     */
    public Promise<List<Sprint>> listProjectSprints(String tenantId, UUID projectId) {
        return sprintRepository.findByProject(tenantId, projectId.toString());
    }

    /**
     * Lists completed sprints for a project.
     */
    public Promise<List<Sprint>> listCompletedSprints(String tenantId, UUID projectId, int limit) {
        return sprintRepository.findCompletedSprints(tenantId, projectId.toString(), limit);
    }

    /**
     * Starts a sprint.
     */
    public Promise<Sprint> startSprint(String tenantId, UUID sprintId) {
        return sprintRepository.findById(tenantId, sprintId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Sprint not found: " + sprintId));
                    }
                    Sprint sprint = opt.get();

                    if (sprint.getStatus() != SprintStatus.PLANNING) {
                        return Promise.ofException(new IllegalStateException(
                                "Can only start sprints in PLANNING status"));
                    }

                    // Calculate committed points from stories
                    return storyRepository.sumStoryPoints(tenantId, sprintId.toString())
                            .then(committedPoints -> {
                                sprint.setCommittedPoints(committedPoints);
                                sprint.setActualVelocity(0);
                                sprint.start();
                                return sprintRepository.save(sprint);
                            });
                });
    }

    /**
     * Completes a sprint.
     */
    public Promise<Sprint> completeSprint(String tenantId, UUID sprintId, SprintRetrospective retrospective) {
        return sprintRepository.findById(tenantId, sprintId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Sprint not found: " + sprintId));
                    }
                    Sprint sprint = opt.get();

                    if (sprint.getStatus() != SprintStatus.ACTIVE) {
                        return Promise.ofException(new IllegalStateException(
                                "Can only complete ACTIVE sprints"));
                    }

                    return storyRepository.sumCompletedStoryPoints(tenantId, sprintId.toString())
                            .then(completedPoints -> {
                                sprint.setCompletedPoints(completedPoints);
                                sprint.setActualVelocity(completedPoints);
                                if (retrospective != null) {
                                    sprint.setRetrospective(retrospective);
                                }
                                sprint.complete();
                                return sprintRepository.save(sprint);
                            });
                });
    }

    /**
     * Cancels a sprint.
     */
    public Promise<Sprint> cancelSprint(String tenantId, UUID sprintId, String reason) {
        return sprintRepository.findById(tenantId, sprintId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Sprint not found: " + sprintId));
                    }
                    Sprint sprint = opt.get();

                    sprint.cancel();
                    sprint.getMetadata().put("cancelReason", reason);
                    
                    logger.info("Cancelled sprint: {}, reason: {}", sprintId, reason);
                    return sprintRepository.save(sprint);
                });
    }

    /**
     * Updates sprint goals.
     */
    public Promise<Sprint> updateSprintGoals(String tenantId, UUID sprintId, List<String> goals) {
        return sprintRepository.findById(tenantId, sprintId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Sprint not found: " + sprintId));
                    }
                    Sprint sprint = opt.get();
                    sprint.setGoals(goals);
                    sprint.setUpdatedAt(Instant.now());
                    return sprintRepository.save(sprint);
                });
    }

    /**
     * Updates sprint dates.
     */
    public Promise<Sprint> updateSprintDates(String tenantId, UUID sprintId, 
                                             LocalDate startDate, LocalDate endDate) {
        return sprintRepository.findById(tenantId, sprintId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Sprint not found: " + sprintId));
                    }
                    Sprint sprint = opt.get();

                    if (sprint.getStatus() != SprintStatus.PLANNING) {
                        return Promise.ofException(new IllegalStateException(
                                "Can only update dates for sprints in PLANNING status"));
                    }

                    if (startDate != null) {
                        sprint.setStartDate(toInstant(startDate));
                    }
                    if (endDate != null) {
                        sprint.setEndDate(toInstant(endDate));
                    }
                    sprint.setUpdatedAt(Instant.now());
                    return sprintRepository.save(sprint);
                });
    }

    /**
     * Deletes a sprint.
     */
    public Promise<Boolean> deleteSprint(String tenantId, UUID sprintId) {
        return sprintRepository.findById(tenantId, sprintId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.of(false);
                    }
                    Sprint sprint = opt.get();
                    if (sprint.getStatus() != SprintStatus.PLANNING) {
                        return Promise.ofException(new IllegalStateException(
                                "Can only delete sprints in PLANNING status"));
                    }
                    return sprintRepository.delete(tenantId, sprintId);
                });
    }

    /**
     * Gets the average velocity for a project.
     */
    public Promise<Double> getAverageVelocity(String tenantId, UUID projectId, int sprintCount) {
        return sprintRepository.calculateAverageVelocity(tenantId, projectId.toString(), sprintCount);
    }

    // Helper method
    private Instant toInstant(LocalDate date) {
        return date != null ? date.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
    }

    // ========== Input Records ==========

    public record CreateSprintInput(
            String projectId,
            String name,
            List<String> goals,
            LocalDate startDate,
            LocalDate endDate,
            Integer teamCapacity,
            String createdBy
    ) {}
}
