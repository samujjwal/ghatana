/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.development;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.Story;
import com.ghatana.yappc.api.domain.Story.*;
import com.ghatana.yappc.api.service.StoryService;
import com.ghatana.yappc.api.service.StoryService.*;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

/**
 * HTTP controller for story management endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP endpoints for story management
 * @doc.layer api
 * @doc.pattern Controller
 */
public class StoryController {

    private static final Logger logger = LoggerFactory.getLogger(StoryController.class);

    private final StoryService service;
    private final ObjectMapper mapper;

    @Inject
    public StoryController(StoryService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** POST /api/stories */
    public Promise<HttpResponse> createStory(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                CreateStoryRequest req = mapper.readValue(body.getArray(), CreateStoryRequest.class);
                                CreateStoryInput input = new CreateStoryInput(
                                        UUID.fromString(req.projectId()),
                                        req.sprintId() != null ? UUID.fromString(req.sprintId()) : null,
                                        req.title(),
                                        req.description(),
                                        req.type(),
                                        req.priority(),
                                        req.storyPoints(),
                                        ctx.userId(),
                                        req.generateTasks() != null ? req.generateTasks() : true,
                                        req.generateAcceptanceCriteria() != null ? req.generateAcceptanceCriteria() : true
                                );
                                return service.createStory(ctx.tenantId(), input)
                                        .map(ApiResponse::created);
                            } catch (Exception e) {
                                logger.error("Failed to create story", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/stories/:id */
    public Promise<HttpResponse> getStory(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getStory(ctx.tenantId(), UUID.fromString(id))
                        .map(opt -> opt.map(ApiResponse::ok)
                                .orElse(ApiResponse.notFound("Story not found"))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/stories/key/:key */
    public Promise<HttpResponse> getStoryByKey(HttpRequest request, String key) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getStoryByKey(ctx.tenantId(), key)
                        .map(opt -> opt.map(ApiResponse::ok)
                                .orElse(ApiResponse.notFound("Story not found"))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/sprints/:sprintId/stories */
    public Promise<HttpResponse> listSprintStories(HttpRequest request, String sprintId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listSprintStories(ctx.tenantId(), UUID.fromString(sprintId))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/projects/:projectId/backlog */
    public Promise<HttpResponse> listBacklog(HttpRequest request, String projectId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listBacklog(ctx.tenantId(), UUID.fromString(projectId))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/users/:userId/stories */
    public Promise<HttpResponse> listAssignedStories(HttpRequest request, String userId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listAssignedStories(ctx.tenantId(), userId)
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/projects/:projectId/stories/blocked */
    public Promise<HttpResponse> listBlockedStories(HttpRequest request, String projectId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listBlockedStories(ctx.tenantId(), UUID.fromString(projectId))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** PATCH /api/stories/:id */
    public Promise<HttpResponse> updateStory(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                UpdateStoryRequest req = mapper.readValue(body.getArray(), UpdateStoryRequest.class);
                                UpdateStoryInput input = new UpdateStoryInput(
                                        req.title(),
                                        req.description(),
                                        req.type(),
                                        req.priority(),
                                        req.storyPoints()
                                );
                                return service.updateStory(ctx.tenantId(), UUID.fromString(id), input)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to update story", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/stories/:id/move */
    public Promise<HttpResponse> moveStory(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                MoveStoryRequest req = mapper.readValue(body.getArray(), MoveStoryRequest.class);
                                return service.moveStory(ctx.tenantId(), UUID.fromString(id), req.status())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to move story", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/stories/:id/assign */
    public Promise<HttpResponse> assignStory(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                AssignStoryRequest req = mapper.readValue(body.getArray(), AssignStoryRequest.class);
                                return service.assignStory(ctx.tenantId(), UUID.fromString(id), req.userId())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to assign story", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** DELETE /api/stories/:id/assign/:userId */
    public Promise<HttpResponse> unassignStory(HttpRequest request, String id, String userId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.unassignStory(ctx.tenantId(), UUID.fromString(id), userId)
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/stories/:id/sprint */
    public Promise<HttpResponse> moveToSprint(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                MoveToSprintRequest req = mapper.readValue(body.getArray(), MoveToSprintRequest.class);
                                UUID sprintId = req.sprintId() != null ? UUID.fromString(req.sprintId()) : null;
                                return service.moveToSprint(ctx.tenantId(), UUID.fromString(id), sprintId)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to move story to sprint", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/stories/:id/tasks */
    public Promise<HttpResponse> addTask(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                Task task = mapper.readValue(body.getArray(), Task.class);
                                return service.addTask(ctx.tenantId(), UUID.fromString(id), task)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to add task", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** PATCH /api/stories/:id/tasks/:taskId */
    public Promise<HttpResponse> updateTask(HttpRequest request, String id, String taskId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                UpdateTaskRequest req = mapper.readValue(body.getArray(), UpdateTaskRequest.class);
                                return service.updateTaskStatus(ctx.tenantId(), UUID.fromString(id), taskId, req.status())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to update task", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/stories/:id/criteria */
    public Promise<HttpResponse> addAcceptanceCriterion(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                AcceptanceCriterion criterion = mapper.readValue(body.getArray(), AcceptanceCriterion.class);
                                return service.addAcceptanceCriterion(ctx.tenantId(), UUID.fromString(id), criterion)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to add acceptance criterion", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** PATCH /api/stories/:id/criteria/:criterionId */
    public Promise<HttpResponse> updateAcceptanceCriterion(HttpRequest request, String id, String criterionId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                UpdateCriterionRequest req = mapper.readValue(body.getArray(), UpdateCriterionRequest.class);
                                return service.updateAcceptanceCriterion(ctx.tenantId(), UUID.fromString(id), 
                                        criterionId, req.completed())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to update acceptance criterion", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/stories/:id/pull-request */
    public Promise<HttpResponse> linkPullRequest(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                PullRequest pr = mapper.readValue(body.getArray(), PullRequest.class);
                                return service.linkPullRequest(ctx.tenantId(), UUID.fromString(id), pr)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to link pull request", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/stories/:id/blockers */
    public Promise<HttpResponse> addBlocker(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                AddBlockerRequest req = mapper.readValue(body.getArray(), AddBlockerRequest.class);
                                return service.addBlocker(ctx.tenantId(), UUID.fromString(id), req.blockingStoryId())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to add blocker", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** DELETE /api/stories/:id/blockers/:blockerId */
    public Promise<HttpResponse> removeBlocker(HttpRequest request, String id, String blockerId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.removeBlocker(ctx.tenantId(), UUID.fromString(id), blockerId)
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** DELETE /api/stories/:id */
    public Promise<HttpResponse> deleteStory(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.deleteStory(ctx.tenantId(), UUID.fromString(id))
                        .map(deleted -> deleted ? ApiResponse.noContent() : ApiResponse.notFound("Story not found")))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    // ========== Request DTOs ==========

    public record CreateStoryRequest(
            String projectId,
            String sprintId,
            String title,
            String description,
            StoryType type,
            Priority priority,
            Integer storyPoints,
            Boolean generateTasks,
            Boolean generateAcceptanceCriteria
    ) {}

    public record UpdateStoryRequest(
            String title,
            String description,
            StoryType type,
            Priority priority,
            Integer storyPoints
    ) {}

    public record MoveStoryRequest(StoryStatus status) {}

    public record AssignStoryRequest(String userId) {}

    public record MoveToSprintRequest(String sprintId) {}

    public record UpdateTaskRequest(String status) {}

    public record UpdateCriterionRequest(boolean completed) {}

    public record AddBlockerRequest(String blockingStoryId) {}
}
