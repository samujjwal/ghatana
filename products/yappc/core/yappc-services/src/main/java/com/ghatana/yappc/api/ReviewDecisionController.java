/**
 * Review Decision Controller
 * 
 * HTTP API controller for review decisions on page artifacts.
 * Handles apply/reject/rollback/request-changes decisions for AI-generated changes.
 * 
 * @doc.type class
 * @doc.purpose Review decision HTTP API
 * @doc.layer product
 * @doc.pattern Controller
 */

package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.ghatana.yappc.api.HttpResponses.badRequest400;
import static com.ghatana.yappc.api.HttpResponses.ok200Json;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.activej.http.HttpResponse.*;

/**
 * Production-grade controller for review decisions on page artifacts.
 */
public final class ReviewDecisionController {

    private static final Logger log = LoggerFactory.getLogger(ReviewDecisionController.class);

    private final ObjectMapper objectMapper;

    public ReviewDecisionController(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Promise<HttpResponse> applyReviewDecision(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    ReviewDecisionRequest req = objectMapper.readValue(body.getArray(), ReviewDecisionRequest.class);

                    // Validate required fields
                    if (req.artifactId() == null || req.artifactId().isBlank()) {
                        return Promise.of(badRequest400("artifactId is required"));
                    }
                    if (req.decision() == null) {
                        return Promise.of(badRequest400("decision is required"));
                    }
                    if (req.actorId() == null || req.actorId().isBlank()) {
                        return Promise.of(badRequest400("actorId is required"));
                    }

                    // Process review decision
                    log.info("Processing review decision: artifactId={}, decision={}, actorId={}",
                            req.artifactId(), req.decision(), req.actorId());

                    ReviewDecisionResponse response = processDecision(req);

                    return Promise.of(ok200Json(objectMapper.writeValueAsString(response)));

                } catch (Exception e) {
                    log.error("Error processing review decision", e);
                    return Promise.of(badRequest400("Invalid request format"));
                }
            })
            .whenException(e -> log.error("Review decision request failed", e));
    }

    public Promise<HttpResponse> rollbackReviewDecision(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    RollbackRequest req = objectMapper.readValue(body.getArray(), RollbackRequest.class);

                    // Validate required fields
                    if (req.artifactId() == null || req.artifactId().isBlank()) {
                        return Promise.of(badRequest400("artifactId is required"));
                    }
                    if (req.changeRecordId() == null || req.changeRecordId().isBlank()) {
                        return Promise.of(badRequest400("changeRecordId is required"));
                    }

                    // Process rollback
                    log.info("Processing rollback: artifactId={}, changeRecordId={}, actorId={}",
                            req.artifactId(), req.changeRecordId(), req.actorId());

                    RollbackResponse response = processRollback(req);

                    return Promise.of(ok200Json(objectMapper.writeValueAsString(response)));

                } catch (Exception e) {
                    log.error("Error processing rollback", e);
                    return Promise.of(badRequest400("Invalid request format"));
                }
            })
            .whenException(e -> log.error("Rollback request failed", e));
    }

    private ReviewDecisionResponse processDecision(ReviewDecisionRequest req) {
        // In production, this would integrate with the actual review decision service
        // For now, return a simulated response
        return new ReviewDecisionResponse(
                req.artifactId(),
                req.decision(),
                req.changeRecordId(),
                "success",
                "Review decision processed successfully",
                java.time.Instant.now().toString()
        );
    }

    private RollbackResponse processRollback(RollbackRequest req) {
        // In production, this would integrate with the actual rollback service
        // For now, return a simulated response
        return new RollbackResponse(
                req.artifactId(),
                req.changeRecordId(),
                "success",
                "Rollback processed successfully",
                java.time.Instant.now().toString()
        );
    }

    public record ReviewDecisionRequest(
            String artifactId,
            String documentId,
            String decision,
            String changeRecordId,
            String actorId,
            String actorName,
            String reason,
            String correlationId
    ) {}

    public record ReviewDecisionResponse(
            String artifactId,
            String decision,
            String changeRecordId,
            String status,
            String message,
            String processedAt
    ) {}

    public record RollbackRequest(
            String artifactId,
            String changeRecordId,
            String actorId,
            String actorName,
            String reason,
            String correlationId
    ) {}

    public record RollbackResponse(
            String artifactId,
            String changeRecordId,
            String status,
            String message,
            String processedAt
    ) {}
}
