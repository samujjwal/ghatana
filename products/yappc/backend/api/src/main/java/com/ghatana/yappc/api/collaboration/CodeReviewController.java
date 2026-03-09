/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.CodeReview.*;
import com.ghatana.yappc.api.service.CodeReviewService;
import com.ghatana.yappc.api.service.CodeReviewService.*;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

/**
 * HTTP controller for code review endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP endpoints for code reviews
 * @doc.layer api
 * @doc.pattern Controller
 */
public class CodeReviewController {

    private static final Logger logger = LoggerFactory.getLogger(CodeReviewController.class);

    private final CodeReviewService service;
    private final ObjectMapper mapper;

    @Inject
    public CodeReviewController(CodeReviewService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** POST /api/reviews */
    public Promise<HttpResponse> createReview(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                CreateReviewRequest req = mapper.readValue(body.getArray(), CreateReviewRequest.class);
                                CreateReviewInput input = new CreateReviewInput(
                                        req.projectId(),
                                        req.storyId(),
                                        req.pullRequestUrl(),
                                        req.pullRequestNumber(),
                                        req.title(),
                                        req.description(),
                                        ctx.userId(),
                                        req.isDraft() != null && req.isDraft(),
                                        req.reviewerIds(),
                                        req.fileChanges()
                                );
                                return service.createReview(ctx.tenantId(), input)
                                        .map(ApiResponse::created);
                            } catch (Exception e) {
                                logger.error("Failed to create review", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/reviews/:id */
    public Promise<HttpResponse> getReview(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getReview(ctx.tenantId(), UUID.fromString(id))
                        .map(opt -> opt.map(ApiResponse::ok)
                                .orElse(ApiResponse.notFound("Review not found"))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/projects/:projectId/reviews */
    public Promise<HttpResponse> listProjectReviews(HttpRequest request, String projectId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listProjectReviews(ctx.tenantId(), projectId)
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/stories/:storyId/reviews */
    public Promise<HttpResponse> listStoryReviews(HttpRequest request, String storyId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listStoryReviews(ctx.tenantId(), storyId)
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/me/reviews/pending */
    public Promise<HttpResponse> listMyPendingReviews(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listPendingReviews(ctx.tenantId(), ctx.userId())
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/me/reviews/authored */
    public Promise<HttpResponse> listMyAuthoredReviews(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listAuthoredReviews(ctx.tenantId(), ctx.userId())
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/reviews/:id/submit */
    public Promise<HttpResponse> submitReview(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                SubmitReviewRequest req = mapper.readValue(body.getArray(), SubmitReviewRequest.class);
                                SubmitReviewInput input = new SubmitReviewInput(
                                        ctx.userId(),
                                        req.decision(),
                                        req.comment()
                                );
                                return service.submitReview(ctx.tenantId(), UUID.fromString(id), input)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to submit review", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/reviews/:id/approve */
    public Promise<HttpResponse> approveReview(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                CommentRequest req = mapper.readValue(body.getArray(), CommentRequest.class);
                                return service.approveReview(ctx.tenantId(), UUID.fromString(id), 
                                        ctx.userId(), req.comment())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to approve review", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/reviews/:id/request-changes */
    public Promise<HttpResponse> requestChanges(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                CommentRequest req = mapper.readValue(body.getArray(), CommentRequest.class);
                                return service.requestChanges(ctx.tenantId(), UUID.fromString(id), 
                                        ctx.userId(), req.comment())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to request changes", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/reviews/:id/comments */
    public Promise<HttpResponse> addComment(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                AddCommentRequest req = mapper.readValue(body.getArray(), AddCommentRequest.class);
                                AddCommentInput input = new AddCommentInput(
                                        ctx.userId(),
                                        req.authorName(),
                                        req.type(),
                                        req.content(),
                                        req.filePath(),
                                        req.lineNumber(),
                                        req.suggestionCode()
                                );
                                return service.addComment(ctx.tenantId(), UUID.fromString(id), input)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to add comment", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/reviews/:id/comments/:commentId/resolve */
    public Promise<HttpResponse> resolveComment(HttpRequest request, String id, String commentId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.resolveComment(ctx.tenantId(), UUID.fromString(id), commentId, ctx.userId())
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/reviews/:id/merge */
    public Promise<HttpResponse> mergeReview(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.mergeReview(ctx.tenantId(), UUID.fromString(id))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/reviews/:id/close */
    public Promise<HttpResponse> closeReview(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.closeReview(ctx.tenantId(), UUID.fromString(id))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/projects/:projectId/reviews/statistics */
    public Promise<HttpResponse> getProjectStatistics(HttpRequest request, String projectId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getProjectStatistics(ctx.tenantId(), projectId)
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    // ========== Request DTOs ==========

    public record CreateReviewRequest(
            String projectId,
            String storyId,
            String pullRequestUrl,
            int pullRequestNumber,
            String title,
            String description,
            Boolean isDraft,
            List<String> reviewerIds,
            List<FileChange> fileChanges
    ) {}

    public record SubmitReviewRequest(
            ReviewDecision decision,
            String comment
    ) {}

    public record CommentRequest(String comment) {}

    public record AddCommentRequest(
            String authorName,
            CommentType type,
            String content,
            String filePath,
            Integer lineNumber,
            String suggestionCode
    ) {}
}
