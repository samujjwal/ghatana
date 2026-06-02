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
 * <p>WS3-8: Publishes canonical events to the Data Cloud event log for:
 * <ul>
 *   <li>Media artifact registered</li>
 *   <li>Media artifact consent-updated</li>
 *   <li>Media artifact processing-requested</li>
 *   <li>Media artifact processing-started</li>
 *   <li>Media artifact transcript-created</li>
 *   <li>Media artifact frame-index-created</li>
 *   <li>Media artifact processing-failed</li>
 *   <li>Media artifact retry-requested</li>
 *   <li>Media artifact retention-expired</li>
 *   <li>Media artifact deleted</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Emits canonical events for media artifact operations
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
            "contentClass", record.contentClass() != null ? record.contentClass() : "",
            "privacyClass", record.privacyClass() != null ? record.privacyClass() : "",
            "consentStatus", record.consentStatus() != null ? record.consentStatus() : "",
            "storageProvider", record.storageProvider() != null ? record.storageProvider() : "",
            "originToolId", record.originToolId() != null ? record.originToolId() : "",
            "correlationId", record.correlationId() != null ? record.correlationId() : "",
            "source", "media-artifact-service"
        );

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(record.createdAt())
            .payload(serializeRecord(record))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(record.artifactId())
            .source("media-artifact-service")
            .build();

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

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(serializeDeletePayload(artifactId, agentId))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId)
            .source("media-artifact-service")
            .build();

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

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":transcription")
            .source("media-artifact-service")
            .build();

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
     * WS3-8: Emit an event when a media artifact is registered.
     */
    public Promise<Offset> emitRegistered(MediaArtifactRecord record) {
        String eventType = EVENT_TYPE_PREFIX + "registered";
        return emitCreated(record).map(offset -> {
            log.info("[media-artifact] Emitted registered event artifactId={} tenantId={}", record.artifactId(), record.tenantId());
            return offset;
        });
    }

    /**
     * WS3-8: Emit an event when consent is updated for a media artifact.
     */
    public Promise<Offset> emitConsentUpdated(String artifactId, String tenantId, String oldConsentStatus, String newConsentStatus, String updatedBy) {
        String eventType = EVENT_TYPE_PREFIX + "consent-updated";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "oldConsentStatus", oldConsentStatus != null ? oldConsentStatus : "",
            "newConsentStatus", newConsentStatus != null ? newConsentStatus : "",
            "updatedBy", updatedBy != null ? updatedBy : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"oldConsentStatus\":\"%s\",\"newConsentStatus\":\"%s\",\"updatedBy\":\"%s\"}",
            artifactId, tenantId, oldConsentStatus != null ? oldConsentStatus : "",
            newConsentStatus != null ? newConsentStatus : "", updatedBy != null ? updatedBy : ""
        );

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":consent:" + Instant.now().toEpochMilli())
            .source("media-artifact-service")
            .build();

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted consent-updated event artifactId={} tenantId={} offset={}", artifactId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit consent-updated event artifactId={}", artifactId, e);
            });
    }

    /**
     * WS3-8: Emit an event when processing is requested for a media artifact.
     */
    public Promise<Offset> emitProcessingRequested(String artifactId, String tenantId, String jobType, String jobId) {
        String eventType = EVENT_TYPE_PREFIX + "processing-requested";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "jobType", jobType != null ? jobType : "",
            "jobId", jobId != null ? jobId : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"jobType\":\"%s\",\"jobId\":\"%s\"}",
            artifactId, tenantId, jobType != null ? jobType : "", jobId != null ? jobId : ""
        );

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":processing:" + jobId)
            .source("media-artifact-service")
            .build();

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted processing-requested event artifactId={} tenantId={} offset={}", artifactId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit processing-requested event artifactId={}", artifactId, e);
            });
    }

    /**
     * WS3-8: Emit an event when processing is started for a media artifact.
     */
    public Promise<Offset> emitProcessingStarted(String artifactId, String tenantId, String jobId, String processorId) {
        String eventType = EVENT_TYPE_PREFIX + "processing-started";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "jobId", jobId != null ? jobId : "",
            "processorId", processorId != null ? processorId : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"jobId\":\"%s\",\"processorId\":\"%s\"}",
            artifactId, tenantId, jobId != null ? jobId : "", processorId != null ? processorId : ""
        );

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":processing-started:" + jobId)
            .source("media-artifact-service")
            .build();

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted processing-started event artifactId={} tenantId={} offset={}", artifactId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit processing-started event artifactId={}", artifactId, e);
            });
    }

    /**
     * WS3-8: Emit an event when a transcript is created for a media artifact.
     */
    public Promise<Offset> emitTranscriptCreated(String artifactId, String tenantId, String transcriptId, String jobId) {
        String eventType = EVENT_TYPE_PREFIX + "transcript-created";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "transcriptId", transcriptId != null ? transcriptId : "",
            "jobId", jobId != null ? jobId : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"transcriptId\":\"%s\",\"jobId\":\"%s\"}",
            artifactId, tenantId, transcriptId != null ? transcriptId : "", jobId != null ? jobId : ""
        );

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":transcript:" + transcriptId)
            .source("media-artifact-service")
            .build();

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted transcript-created event artifactId={} tenantId={} offset={}", artifactId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit transcript-created event artifactId={}", artifactId, e);
            });
    }

    /**
     * WS3-8: Emit an event when a frame index is created for a media artifact.
     */
    public Promise<Offset> emitFrameIndexCreated(String artifactId, String tenantId, String frameIndexId, String jobId) {
        String eventType = EVENT_TYPE_PREFIX + "frame-index-created";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "frameIndexId", frameIndexId != null ? frameIndexId : "",
            "jobId", jobId != null ? jobId : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"frameIndexId\":\"%s\",\"jobId\":\"%s\"}",
            artifactId, tenantId, frameIndexId != null ? frameIndexId : "", jobId != null ? jobId : ""
        );

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":frame-index:" + frameIndexId)
            .source("media-artifact-service")
            .build();

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted frame-index-created event artifactId={} tenantId={} offset={}", artifactId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit frame-index-created event artifactId={}", artifactId, e);
            });
    }

    /**
     * WS3-8: Emit an event when processing fails for a media artifact.
     */
    public Promise<Offset> emitProcessingFailed(String artifactId, String tenantId, String jobId, String failureCode, String failureReason) {
        String eventType = EVENT_TYPE_PREFIX + "processing-failed";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "jobId", jobId != null ? jobId : "",
            "failureCode", failureCode != null ? failureCode : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"jobId\":\"%s\",\"failureCode\":\"%s\",\"failureReason\":\"%s\"}",
            artifactId, tenantId, jobId != null ? jobId : "",
            failureCode != null ? failureCode : "",
            failureReason != null ? failureReason.replace("\"", "\\\"") : ""
        );

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":processing-failed:" + jobId)
            .source("media-artifact-service")
            .build();

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted processing-failed event artifactId={} tenantId={} offset={}", artifactId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit processing-failed event artifactId={}", artifactId, e);
            });
    }

    /**
     * WS3-8: Emit an event when a retry is requested for a failed media processing job.
     */
    public Promise<Offset> emitRetryRequested(String artifactId, String tenantId, String jobId, int attempt, int maxAttempts) {
        String eventType = EVENT_TYPE_PREFIX + "retry-requested";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "jobId", jobId != null ? jobId : "",
            "attempt", String.valueOf(attempt),
            "maxAttempts", String.valueOf(maxAttempts),
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"jobId\":\"%s\",\"attempt\":%d,\"maxAttempts\":%d}",
            artifactId, tenantId, jobId != null ? jobId : "", attempt, maxAttempts
        );

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":retry:" + jobId + ":" + attempt)
            .source("media-artifact-service")
            .build();

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted retry-requested event artifactId={} tenantId={} offset={}", artifactId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit retry-requested event artifactId={}", artifactId, e);
            });
    }

    /**
     * WS3-8: Emit an event when retention expires for a media artifact.
     */
    public Promise<Offset> emitRetentionExpired(String artifactId, String tenantId, String retentionPolicy, Instant retentionUntil) {
        String eventType = EVENT_TYPE_PREFIX + "retention-expired";

        Map<String, String> headers = Map.of(
            "artifactId", artifactId,
            "retentionPolicy", retentionPolicy != null ? retentionPolicy : "",
            "retentionUntil", retentionUntil != null ? retentionUntil.toString() : "",
            "source", "media-artifact-service"
        );

        String payload = String.format(
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"retentionPolicy\":\"%s\",\"retentionUntil\":\"%s\"}",
            artifactId, tenantId, retentionPolicy != null ? retentionPolicy : "",
            retentionUntil != null ? retentionUntil.toString() : ""
        );

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":retention-expired")
            .source("media-artifact-service")
            .build();

        TenantContext tenantContext = TenantContext.of(tenantId);

        return eventLogStore.append(tenantContext, entry)
            .map(offset -> {
                log.info("[media-artifact] Emitted retention-expired event artifactId={} tenantId={} offset={}", artifactId, tenantId, offset);
                return offset;
            })
            .whenException(e -> {
                log.error("[media-artifact] Failed to emit retention-expired event artifactId={}", artifactId, e);
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

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":transcription:" + (transcriptId != null ? transcriptId : "completed"))
            .source("media-artifact-service")
            .build();

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

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":vision:" + (analysisType != null ? analysisType : "analysis"))
            .source("media-artifact-service")
            .build();

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

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":" + (operation != null ? operation : "processing"))
            .source("media-artifact-service")
            .build();

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
     * Emit an event when media processing fails (agent version).
     */
    public Promise<Offset> emitProcessingFailedByAgent(String artifactId, String tenantId, String agentId, String operation, String error) {
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

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .source("media-artifact-service")
            .build();

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

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":" + (updateType != null ? updateType : "update"))
            .source("media-artifact-service")
            .build();

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

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":multimodal:" + (indexType != null ? indexType : "index"))
            .source("media-artifact-service")
            .build();

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

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .idempotencyKey(artifactId + ":multimodal:" + (indexId != null ? indexId : "completed"))
            .source("media-artifact-service")
            .build();

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

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .eventVersion("1.0")
            .timestamp(Instant.now())
            .payload(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .contentType("application/json")
            .headers(headers)
            .source("media-artifact-service")
            .build();

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
            "{\"artifactId\":\"%s\",\"tenantId\":\"%s\",\"agentId\":\"%s\",\"mediaType\":\"%s\",\"sizeBytes\":%d,\"durationMs\":%d,\"contentClass\":\"%s\",\"privacyClass\":\"%s\",\"consentStatus\":\"%s\",\"storageProvider\":\"%s\",\"retentionPolicy\":\"%s\",\"retentionUntil\":\"%s\",\"lineageRef\":\"%s\",\"policyContext\":\"%s\",\"redactionState\":\"%s\"}",
            record.artifactId(), record.tenantId(), record.agentId(), 
            record.mediaType(), record.sizeBytes(), record.durationMs(),
            record.contentClass() != null ? record.contentClass() : "",
            record.privacyClass() != null ? record.privacyClass() : "",
            record.consentStatus() != null ? record.consentStatus() : "",
            record.storageProvider() != null ? record.storageProvider() : "",
            record.retentionPolicy() != null ? record.retentionPolicy() : "",
            record.retentionUntil() != null ? record.retentionUntil().toString() : "",
            record.lineageRef() != null ? record.lineageRef() : "",
            record.policyContext() != null ? record.policyContext() : "",
            record.redactionState() != null ? record.redactionState() : ""
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
