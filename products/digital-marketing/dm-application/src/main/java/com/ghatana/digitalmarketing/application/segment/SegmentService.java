package com.ghatana.digitalmarketing.application.segment;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.segment.AudienceSegment;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for audience/segment management.
 *
 * @doc.type class
 * @doc.purpose Defines operations for managing audience segments (DMOS-F2-005)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface SegmentService {

    /**
     * Create a new audience segment.
     *
     * @param ctx     operation context
     * @param request segment creation request
     * @return the newly created segment
     */
    Promise<AudienceSegment> createSegment(DmOperationContext ctx, CreateSegmentRequest request);

    /**
     * Fetch a segment by ID.
     *
     * @param ctx        operation context
     * @param segmentId segment ID
     * @return optional segment
     */
    Promise<Optional<AudienceSegment>> getSegment(DmOperationContext ctx, String segmentId);

    /**
     * Update segment criteria.
     *
     * @param ctx        operation context
     * @param segmentId segment ID
     * @param criteria   new criteria
     * @return updated segment
     */
    Promise<AudienceSegment> updateSegment(DmOperationContext ctx, String segmentId, SegmentCriteria criteria);

    /**
     * Refresh segment population.
     *
     * @param ctx        operation context
     * @param segmentId segment ID
     * @return updated segment with new population
     */
    Promise<AudienceSegment> refreshSegment(DmOperationContext ctx, String segmentId);

    /**
     * Get segment population.
     *
     * @param ctx        operation context
     * @param segmentId segment ID
     * @return segment population
     */
    Promise<SegmentPopulation> getSegmentPopulation(DmOperationContext ctx, String segmentId);

    /**
     * List segments for the tenant.
     *
     * @param ctx   operation context
     * @param limit max results
     * @return list of segments
     */
    Promise<List<AudienceSegment>> listSegments(DmOperationContext ctx, int limit);

    // ── Request types ─────────────────────────────────────────────────────────

    record CreateSegmentRequest(
        String name,
        String description,
        SegmentCriteria criteria
    ) {
        public CreateSegmentRequest {
            // Validation logic
        }
    }

    record SegmentCriteria(
        List<String> includedTags,
        List<String> excludedTags,
        List<String> includedSegments,
        String customExpression
    ) {}

    record SegmentPopulation(
        String segmentId,
        int totalMembers,
        int newMembers,
        int removedMembers,
        String lastRefreshedAt
    ) {}
}
