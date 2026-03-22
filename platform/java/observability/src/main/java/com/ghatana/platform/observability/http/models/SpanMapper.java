package com.ghatana.platform.observability.http.models;

import com.ghatana.platform.observability.trace.SpanData;
import com.ghatana.platform.observability.trace.SpanDataBuilder;

/**
 * Utility for mapping between HTTP models and domain models.
 * <p>
 * Converts {@link SpanRequest} (HTTP) to {@link SpanData} (domain)
 * and vice versa.
 * </p>
 *
 * @author Ghatana Platform Team
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Mapper utility for converting between HTTP span models and domain SpanData
 * @doc.layer observability
 * @doc.pattern Mapper, Utility, Adapter
 */
public final class SpanMapper {

    private SpanMapper() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts a SpanRequest to SpanData domain model.
     * <p>
     * Maps all fields from HTTP request to domain object using SpanDataBuilder.
     * Duration is auto-calculated if not provided.
     * Status defaults to "UNSET" if not provided.
     * </p>
     *
     * @param request  HTTP span request (not null)
     * @return SpanData domain model
     * @throws IllegalArgumentException if required fields are missing
     */
    public static SpanData toDomain(SpanRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("SpanRequest cannot be null");
        }

        SpanDataBuilder builder = SpanData.builder()
                .withSpanId(request.getSpanId())
                .withTraceId(request.getTraceId())
                .withOperationName(request.getOperationName())
                .withServiceName(request.getServiceName())
                .withStartTime(request.getStartTime());

        // Optional fields
        if (request.getParentSpanId() != null) {
            builder.withParentSpanId(request.getParentSpanId());
        }

        if (request.getEndTime() != null) {
            builder.withEndTime(request.getEndTime());
        }

        if (request.getDuration() != null) {
            builder.withDurationMs(request.getDuration());
        }

        if (request.getStatus() != null) {
            builder.withStatus(request.getStatus());
        }

        // Tags and logs (safe if empty)
        builder.withTags(request.getTags())
               .withLogs(request.getLogs());

        return builder.build();
    }

    /**
     * Converts a SpanData domain model to SpanRequest.
     * <p>
     * Maps all fields from domain object to HTTP request model.
     * Useful for testing or API responses that return span data.
     * </p>
     *
     * @param spanData  SpanData domain model (not null)
     * @return SpanRequest HTTP model
     * @throws IllegalArgumentException if spanData is null
     */
    public static SpanRequest fromDomain(SpanData spanData) {
        if (spanData == null) {
            throw new IllegalArgumentException("SpanData cannot be null");
        }

        return new SpanRequest(
                spanData.spanId(),
                spanData.traceId(),
                spanData.parentSpanId(),
                spanData.operationName(),
                spanData.serviceName(),
                spanData.startTime(),
                spanData.endTime(),
                spanData.durationMs(),
                spanData.status(),
                spanData.tags(),
                spanData.logs()
        );
    }
}
