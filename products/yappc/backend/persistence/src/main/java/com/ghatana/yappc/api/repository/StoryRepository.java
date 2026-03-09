/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.Story;
import com.ghatana.yappc.api.domain.Story.StoryStatus;
import com.ghatana.yappc.api.domain.Story.StoryType;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Story entities.
 *
 * @doc.type interface
 * @doc.purpose Repository for story persistence
 * @doc.layer domain
 * @doc.pattern Repository
 */
public interface StoryRepository {

    /**
     * Save a story.
     *
     * @param story the story to save
     * @return Promise with saved story
     */
    Promise<Story> save(Story story);

    /**
     * Find a story by ID.
     *
     * @param tenantId the tenant ID
     * @param id       the story ID
     * @return Promise with optional story
     */
    Promise<Optional<Story>> findById(String tenantId, UUID id);

    /**
     * Find all stories for a project.
     *
     * @param tenantId  the tenant ID
     * @param projectId the project ID
     * @return Promise with list of stories
     */
    Promise<List<Story>> findByProject(String tenantId, String projectId);

    /**
     * Find stories in a sprint.
     *
     * @param tenantId the tenant ID
     * @param sprintId the sprint ID
     * @return Promise with list of stories
     */
    Promise<List<Story>> findBySprint(String tenantId, String sprintId);

    /**
     * Find backlog stories (not in any sprint).
     *
     * @param tenantId  the tenant ID
     * @param projectId the project ID
     * @return Promise with list of backlog stories
     */
    Promise<List<Story>> findBacklog(String tenantId, String projectId);

    /**
     * Find stories by status.
     *
     * @param tenantId  the tenant ID
     * @param projectId the project ID
     * @param status    the story status
     * @return Promise with list of stories
     */
    Promise<List<Story>> findByStatus(String tenantId, String projectId, StoryStatus status);

    /**
     * Find stories by type.
     *
     * @param tenantId  the tenant ID
     * @param projectId the project ID
     * @param type      the story type
     * @return Promise with list of stories
     */
    Promise<List<Story>> findByType(String tenantId, String projectId, StoryType type);

    /**
     * Find stories assigned to a user.
     *
     * @param tenantId the tenant ID
     * @param userId   the user ID
     * @return Promise with list of stories
     */
    Promise<List<Story>> findByAssignee(String tenantId, String userId);

    /**
     * Find a story by its key (e.g., "PROJ-123").
     *
     * @param tenantId the tenant ID
     * @param storyKey the story key
     * @return Promise with optional story
     */
    Promise<Optional<Story>> findByKey(String tenantId, String storyKey);

    /**
     * Find blocked stories.
     *
     * @param tenantId  the tenant ID
     * @param projectId the project ID
     * @return Promise with list of blocked stories
     */
    Promise<List<Story>> findBlocked(String tenantId, String projectId);

    /**
     * Delete a story.
     *
     * @param tenantId the tenant ID
     * @param id       the story ID
     * @return Promise with success status
     */
    Promise<Boolean> delete(String tenantId, UUID id);

    /**
     * Check if a story exists.
     *
     * @param tenantId the tenant ID
     * @param id       the story ID
     * @return Promise with existence status
     */
    Promise<Boolean> exists(String tenantId, UUID id);

    /**
     * Count stories by status for a project.
     *
     * @param tenantId  the tenant ID
     * @param projectId the project ID
     * @param status    the story status
     * @return Promise with count
     */
    Promise<Long> countByStatus(String tenantId, String projectId, StoryStatus status);

    /**
     * Sum story points for a sprint.
     *
     * @param tenantId the tenant ID
     * @param sprintId the sprint ID
     * @return Promise with total story points
     */
    Promise<Integer> sumStoryPoints(String tenantId, String sprintId);

    /**
     * Sum completed story points for a sprint.
     *
     * @param tenantId the tenant ID
     * @param sprintId the sprint ID
     * @return Promise with completed story points
     */
    Promise<Integer> sumCompletedStoryPoints(String tenantId, String sprintId);
}
