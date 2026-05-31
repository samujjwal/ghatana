/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Emits events for media artifact lifecycle operations.
 *
 * <p>Publishes events to the Data Cloud event log for:
 * <ul>
 *   <li>Media artifact created</li>
 *   <li>Media artifact deleted</li>
 *   <li>Transcription requested</li>
 *   <li>Transcription completed</li>
 *   <li>Vision analysis completed</li>
 *   <li>Processing failed</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Emits events for media artifact operations
 * @doc.layer product
 * @doc.pattern Event Emitter
 */
public class MediaArtifactEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(MediaArtifactEventEmitter.class);
    private static final String EVENT_TYPE_PREFIX = "media.artifact.";

    private final EventLogStore eventLogStore;

    public MediaArtifactEventEmitter(EventLogStore eventLogStore) {
        this.eventLogStore = eventLogStore;
    }

    /**
     * Emit an event when a media artifact is created.
     */
    public Promise<Offset> emitCreated(MediaArtifactRecord record) {
        String eventType = EVENT_TYPE_PREFIX + "created";
        
        Map<String, String> headers = Map.of(
            "artifactId", record.artifactId(),
            "agentId", record.agentId(),
            "mediaType", record.mediaType(),
            "originToolId", record.originToolId() != null ? record.originToolId() : "",
            "correlationId", record.correlationId() != null ? record.correlationId() : "",
            "source", "media-artifact-service"
        );

        EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
            UUID.randomUUID(),
            eventType,
            "1.0",
            record.createdAt(),
            ByteBuffer.wrap(serializeRecord(record)),
            "application/json",
            headers,
            java.util.Optional.of(record.artifactId()) // idempotency key
        );

        TenantContext tenantContext = TenantContext.of(record.tenantId());
        
        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted created event artifactId={} tenantId={} offset={}", 
                    record.artifactId(), record.tenantId(), offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit created event artifactId={}", record.artifactId(), e);
            });
    }

    /**
     * Emit an event when a media artifact is deleted.
     */
    public Promise<Offset> emitDeleted(String artifactId, String tenantId, String agentId) {
        String eventType = EVENT_TYPE_PREFIX + "deleted";
        
        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "agentId", agentId != null ? agentId : "",
            "source", "media-artifact-service"
        );

        EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
            UUID.randomUUID(),
            eventType,
            "1.0",
            Instant.now(),
            ByteBuffer.wrap(serializeDeletePayload(artifactId, agentId)),
            "application/json",
            headers,
            java.util.Optional.of(artifactId) // idempotency key
        );

        TenantContext tenantContext = TenantContext.of(tenantId);
        
        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted deleted event artifactId={} tenantId={} offset={}", 
                    artifactId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit deleted event artifactId={}", artifactId, e);
            });
    }

    /**
     * Emit an event when transcription is requested for a media artifact.
     */
    public Promise<Offset> emitTranscriptionRequested(String artifactId, String tenantId, String agentId, String languageCode) {
        String eventType = EVENT_TYPE_PREFIX + "transcription.requested";
        
        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "agentId", agentId != null ? agentId : "",
            "languageCode", languageCode != null ? languageCode : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"agentId\":\"%s\",\"languageCode\":\"%s\"}",
            artifactId, tenantId, agentId != null ? agentId : "", languageCode != null ? languageCode : ""
        );

        EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
            UUID.randomUUID(),
            eventType,
            "1.0",
            Instant.now(),
            ByteBuffer.wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "application/json",
            headers,
            java.util.Optional.of(artifactId + ":transcription") // idempotency key
        );

        TenantContext tenantContext = TenantContext.of(tenantId);
        
        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted transcription requested event artifactId={} tenantId={} offset={}", 
                    artifactId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit transcription requested event artifactId={}", artifactId, e);
            });
    }

    /**
     * Emit an event when transcription is completed for a media artifact.
     */
    public Promise<Offset> emitTranscriptionCompleted(String artifactId, String tenantId, String agentId, String transcriptId, String transcript, int durationMs) {
        String eventType = EVENT_TYPE_PREFIX + "transcription.completed";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "transcriptId", transcriptId != null ? transcriptId : "",
            "agentId", agentId != null ? agentId : "",
            "durationMs", String.valueOf(durationMs),
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"agentId\":\"%s\",\"transcriptId\":\"%s\",\"transcript\":\"%s\",\"durationMs\":%d}",
            artifactId, tenantId, agentId != null ? agentId : "",
            transcriptId != null ? transcriptId : "",
            transcript != null ? transcript.replace("\"", "\\\"") : "", durationMs
        );

        EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
            UUID.randomUUID(),
            eventType,
            "1.0",
            Instant.now(),
            ByteBuffer.wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "application/json",
            headers,
            java.util.Optional.of(artifactId + ":transcription:" + (transcriptId != null ? transcriptId : "completed")) // idempotency key
        );

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted transcription completed event artifactId={} transcriptId={} tenantId={} offset={}",
                    artifactId, transcriptId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit transcription completed event artifactId={}", artifactId, e);
            });
    }

    /**
     * Emit an event when vision analysis is completed for a media artifact.
     */
    public Promise<Offset> emitVisionAnalysisCompleted(String artifactId, String tenantId, String agentId, String analysisType, String result) {
        String eventType = EVENT_TYPE_PREFIX + "vision.completed";
        
        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "agentId", agentId != null ? agentId : "",
            "analysisType", analysisType != null ? analysisType : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"agentId\":\"%s\",\"analysisType\":\"%s\",\"result\":\"%s\"}",
            artifactId, tenantId, agentId != null ? agentId : "", 
            analysisType != null ? analysisType : "", result != null ? result.replace("\"", "\\\"") : ""
        );

        EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
            UUID.randomUUID(),
            eventType,
            "1.0",
            Instant.now(),
            ByteBuffer.wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "application/json",
            headers,
            java.util.Optional.of(artifactId + ":vision:" + (analysisType != null ? analysisType : "analysis")) // idempotency key
        );

        TenantContext tenantContext = TenantContext.of(tenantId);
        
        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted vision analysis completed event artifactId={} tenantId={} offset={}", 
                    artifactId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit vision analysis completed event artifactId={}", artifactId, e);
            });
    }

    /**
     * Emit an event when a processing operation is requested for a media artifact.
     */
    public Promise<Offset> emitProcessingRequested(String artifactId, String tenantId, String agentId, String operation, String operationType) {
        String eventType = EVENT_TYPE_PREFIX + "processing.requested";
        
        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "agentId", agentId != null ? agentId : "",
            "operation", operation != null ? operation : "",
            "operationType", operationType != null ? operationType : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"agentId\":\"%s\",\"operation\":\"%s\",\"operationType\":\"%s\"}",
            artifactId, tenantId, agentId != null ? agentId : "", 
            operation != null ? operation : "", operationType != null ? operationType : ""
        );

        EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
            UUID.randomUUID(),
            eventType,
            "1.0",
            Instant.now(),
            ByteBuffer.wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "application/json",
            headers,
            java.util.Optional.of(artifactId + ":" + (operation != null ? operation : "processing")) // idempotency key
        );

        TenantContext tenantContext = TenantContext.of(tenantId);
        
        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted processing requested event artifactId={} tenantId={} operation={} offset={}", 
                    artifactId, tenantId, operation, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit processing requested event artifactId={}", artifactId, e);
            });
    }

    /**
     * Emit an event when media processing fails.
     */
    public Promise<Offset> emitProcessingFailed(String artifactId, String tenantId, String agentId, String operation, String error) {
        String eventType = EVENT_TYPE_PREFIX + "processing.failed";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "agentId", agentId != null ? agentId : "",
            "operation", operation != null ? operation : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"agentId\":\"%s\",\"operation\":\"%s\",\"error\":\"%s\"}",
            artifactId, tenantId, agentId != null ? agentId : "",
            operation != null ? operation : "", error != null ? error.replace("\"", "\\\"") : ""
        );

        EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
            UUID.randomUUID(),
            eventType,
            "1.0",
            Instant.now(),
            ByteBuffer.wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "application/json",
            headers,
            java.util.Optional.empty() // no idempotency key for failures
        );

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted processing failed event artifactId={} tenantId={} operation={} offset={}",
                    artifactId, tenantId, operation, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit processing failed event artifactId={}", artifactId, e);
            });
    }

    /**
     * Pass 6: Emit an event when a media artifact is updated.
     */
    public Promise<Offset> emitUpdated(String artifactId, String tenantId, String agentId, String updateType, String newValue) {
        String eventType = EVENT_TYPE_PREFIX + "updated";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "agentId", agentId != null ? agentId : "",
            "updateType", updateType != null ? updateType : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"agentId\":\"%s\",\"updateType\":\"%s\",\"newValue\":\"%s\"}",
            artifactId, tenantId, agentId != null ? agentId : "",
            updateType != null ? updateType : "", newValue != null ? newValue.replace("\"", "\\\"") : ""
        );

        EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
            UUID.randomUUID(),
            eventType,
            "1.0",
            Instant.now(),
            ByteBuffer.wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "application/json",
            headers,
            java.util.Optional.of(artifactId + ":" + (updateType != null ? updateType : "update")) // idempotency key
        );

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted updated event artifactId={} tenantId={} updateType={} offset={}",
                    artifactId, tenantId, updateType, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit updated event artifactId={}", artifactId, e);
            });
    }

    /**
     * Pass 6: Emit an event when multimodal indexing is requested for a media artifact.
     */
    public Promise<Offset> emitMultimodalIndexRequested(String artifactId, String tenantId, String agentId, String indexType) {
        String eventType = EVENT_TYPE_PREFIX + "multimodal.index.requested";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "agentId", agentId != null ? agentId : "",
            "indexType", indexType != null ? indexType : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"agentId\":\"%s\",\"indexType\":\"%s\"}",
            artifactId, tenantId, agentId != null ? agentId : "", indexType != null ? indexType : ""
        );

        EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
            UUID.randomUUID(),
            eventType,
            "1.0",
            Instant.now(),
            ByteBuffer.wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "application/json",
            headers,
            java.util.Optional.of(artifactId + ":multimodal:" + (indexType != null ? indexType : "index")) // idempotency key
        );

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted multimodal index requested event artifactId={} tenantId={} indexType={} offset={}",
                    artifactId, tenantId, indexType, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit multimodal index requested event artifactId={}", artifactId, e);
            });
    }

    /**
     * Pass 6: Emit an event when multimodal indexing is completed for a media artifact.
     */
    public Promise<Offset> emitMultimodalIndexCompleted(String artifactId, String tenantId, String agentId, String indexId, String indexType, int indexedFields) {
        String eventType = EVENT_TYPE_PREFIX + "multimodal.index.completed";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "indexId", indexId != null ? indexId : "",
            "agentId", agentId != null ? agentId : "",
            "indexType", indexType != null ? indexType : "",
            "indexedFields", String.valueOf(indexedFields),
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"agentId\":\"%s\",\"indexId\":\"%s\",\"indexType\":\"%s\",\"indexedFields\":%d}",
            artifactId, tenantId, agentId != null ? agentId : "",
            indexId != null ? indexId : "", indexType != null ? indexType : "", indexedFields
        );

        EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
            UUID.randomUUID(),
            eventType,
            "1.0",
            Instant.now(),
            ByteBuffer.wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "application/json",
            headers,
            java.util.Optional.of(artifactId + ":multimodal:" + (indexId != null ? indexId : "completed")) // idempotency key
        );

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted multimodal index completed event artifactId={} indexId={} tenantId={} offset={}",
                    artifactId, indexId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit multimodal index completed event artifactId={}", artifactId, e);
            });
    }

    /**
     * Pass 6: Emit an event when multimodal indexing fails for a media artifact.
     */
    public Promise<Offset> emitMultimodalIndexFailed(String artifactId, String tenantId, String agentId, String indexType, String error) {
        String eventType = EVENT_TYPE_PREFIX + "multimodal.index.failed";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "agentId", agentId != null ? agentId : "",
            "indexType", indexType != null ? indexType : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"agentId\":\"%s\",\"indexType\":\"%s\",\"error\":\"%s\"}",
            artifactId, tenantId, agentId != null ? agentId : "",
            indexType != null ? indexType : "", error != null ? error.replace("\"", "\\\"") : ""
        );

        EventLogStore.EventEntry entry = new EventLogStore.EventEntry(
            UUID.randomUUID(),
            eventType,
            "1.0",
            Instant.now(),
            ByteBuffer.wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "application/json",
            headers,
            java.util.Optional.empty() // no idempotency key for failures
        );

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted multimodal index failed event artifactId={} tenantId={} indexType={} offset={}",
                    artifactId, tenantId, indexType, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit multimodal index failed event artifactId={}", artifactId, e);
            });
    }

    private byte[] serializeRecord(MediaArtifactRecord record) {
        String json = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"agentId\":\"%s\",\"mediaType\":\"%s\",\"sizeBytes\":%d,\"durationMs\":%d}",
            record.artifactId(), record.tenantId(), record.agentId(), 
            record.mediaType(), record.sizeBytes(), record.durationMs()
        );
        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] serializeDeletePayload(String artifactId, String agentId) {
        String json = String.format(
            "{\"artifactId\":\"%s\",\"agentId\":\"%s\"}",
            artifactId, agentId != null ? agentId : ""
        );
        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
