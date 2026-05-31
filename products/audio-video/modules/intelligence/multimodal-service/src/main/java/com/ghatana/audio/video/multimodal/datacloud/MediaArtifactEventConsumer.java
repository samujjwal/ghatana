/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.audio.video.multimodal.datacloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pass 6: Event consumer for Data Cloud media artifact lifecycle events.
 *
 * <p>Subscribes to Data Cloud EventLogStore and processes media artifact events:
 * <ul>
 *   <li>media.artifact.created - triggers transcription/vision analysis for audio/video</li>
 *   <li>media.artifact.transcription.requested - processes transcription jobs</li>
 *   <li>media.artifact.processing.requested - processes multimodal jobs</li>
 * </ul>
 *
 * <p>Integrates with DataCloudMediaLifecycleAdapter to submit jobs and update
 * media artifact lifecycle state.
 *
 * @doc.type class
 * @doc.purpose Consume Data Cloud media artifact events and trigger multimodal processing
 * @doc.layer product
 * @doc.pattern Event Consumer, Subscriber
 */
public class MediaArtifactEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MediaArtifactEventConsumer.class);
    private static final String EVENT_TYPE_PREFIX = "media.artifact.";

    private final EventLogStore eventLogStore;
    private final MediaProcessingProvider processingProvider;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final Map<String, EventLogStore.Subscription> subscriptions = new ConcurrentHashMap<>();

    public MediaArtifactEventConsumer(
            EventLogStore eventLogStore,
            MediaProcessingProvider processingProvider) {
        this.eventLogStore = eventLogStore;
        this.processingProvider = processingProvider;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Start consuming media artifact events for a tenant.
     *
     * @param tenantId Tenant identifier
     * @return Promise completing when subscription is active
     */
    public Promise<Void> start(String tenantId) {
        if (!running.compareAndSet(false, true)) {
            LOG.warn("[MediaArtifactConsumer] Already running for tenant {}", tenantId);
            return Promise.complete();
        }

        TenantContext tenantContext = TenantContext.of(tenantId);

        // Get the latest offset to start from
        return eventLogStore.getLatestOffset(tenantContext)
            .then(latestOffset -> {
                // Start from beginning if no events exist
                Offset fromOffset = latestOffset != null ? latestOffset : Offset.of(0);
                return subscribe(tenantId, tenantContext, fromOffset);
            })
            .whenResult(v -> {
                LOG.info("[MediaArtifactConsumer] Started for tenant {}", tenantId);
            })
            .whenException(e -> {
                LOG.error("[MediaArtifactConsumer] Failed to start for tenant {}", tenantId, e);
                running.set(false);
            });
    }

    /**
     * Stop consuming events for a tenant.
     *
     * @param tenantId Tenant identifier
     */
    public void stop(String tenantId) {
        EventLogStore.Subscription subscription = subscriptions.remove(tenantId);
        if (subscription != null) {
            subscription.cancel();
            LOG.info("[MediaArtifactConsumer] Stopped for tenant {}", tenantId);
        }
        running.set(false);
    }

    /**
     * Check if consumer is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get processing statistics.
     */
    public Map<String, Long> getStats() {
        return Map.of(
            "processed", processedCount.get(),
            "errors", errorCount.get()
        );
    }

    private Promise<Void> subscribe(String tenantId, TenantContext tenantContext, Offset fromOffset) {
        return eventLogStore.tail(tenantContext, fromOffset, event -> {
            try (MDC.MDCCloseable ignored = MDC.putCloseable("tenantId", tenantId)) {
                processEvent(tenantId, event);
            } catch (Exception e) {
                LOG.error("[MediaArtifactConsumer] Error processing event for tenant {}", tenantId, e);
                errorCount.incrementAndGet();
            }
        })
        .then(subscription -> {
            subscriptions.put(tenantId, subscription);
            return Promise.complete();
        });
    }

    private void processEvent(String tenantId, EventLogStore.EventEntry event) {
        String eventType = event.eventType();

        if (!eventType.startsWith(EVENT_TYPE_PREFIX)) {
            return; // Not a media artifact event
        }

        LOG.debug("[MediaArtifactConsumer] Processing event type: {}", eventType);

        try {
            // Parse event payload
            Map<String, Object> payload = objectMapper.readValue(
                new String(event.payload().array(), StandardCharsets.UTF_8),
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );

            String artifactId = (String) payload.get("artifactId");
            String mediaType = (String) payload.get("mediaType");

            if (artifactId == null || artifactId.isBlank()) {
                LOG.warn("[MediaArtifactConsumer] Event missing artifactId: {}", eventType);
                return;
            }

            // Route to appropriate handler based on event type
            switch (eventType) {
                case "media.artifact.created" -> handleArtifactCreated(tenantId, artifactId, mediaType, payload);
                case "media.artifact.transcription.requested" -> handleTranscriptionRequested(tenantId, artifactId, payload);
                case "media.artifact.processing.requested" -> handleProcessingRequested(tenantId, artifactId, mediaType, payload);
                default -> LOG.debug("[MediaArtifactConsumer] Unhandled event type: {}", eventType);
            }

            processedCount.incrementAndGet();

        } catch (Exception e) {
            LOG.error("[MediaArtifactConsumer] Failed to process event type: {}", eventType, e);
            errorCount.incrementAndGet();
        }
    }

    private void handleArtifactCreated(String tenantId, String artifactId, String mediaType, Map<String, Object> payload) {
        // Auto-process audio/video artifacts on creation
        if (mediaType != null && (mediaType.startsWith("audio/") || mediaType.startsWith("video/"))) {
            LOG.info("[MediaArtifactConsumer] Auto-processing artifact {} type {}", artifactId, mediaType);

            // Determine job type based on media type
            JobType jobType = mediaType.startsWith("audio/") ? JobType.TRANSCRIPTION : JobType.VISION_ANALYSIS;

            // Submit job via processing provider
            // Note: In production, we would fetch the actual media data from storage
            // For now, we trigger the job without data (placeholder)
            String jobId = processingProvider.submitJob(
                artifactId,
                tenantId,
                mediaType,
                new byte[0], // Placeholder - would fetch from storage in production
                jobType,
                Map.of("autoTriggered", "true")
            );

            LOG.info("[MediaArtifactConsumer] Submitted job {} for artifact {}", jobId, artifactId);
        }
    }

    private void handleTranscriptionRequested(String tenantId, String artifactId, Map<String, Object> payload) {
        String languageCode = (String) payload.getOrDefault("languageCode", "en-US");

        LOG.info("[MediaArtifactConsumer] Processing transcription request for artifact {} language {}",
            artifactId, languageCode);

        String jobId = processingProvider.submitJob(
            artifactId,
            tenantId,
            "audio/wav", // Default audio type
            new byte[0], // Placeholder - would fetch from storage
            JobType.TRANSCRIPTION,
            Map.of("languageCode", languageCode)
        );

        LOG.info("[MediaArtifactConsumer] Submitted transcription job {} for artifact {}", jobId, artifactId);
    }

    private void handleProcessingRequested(String tenantId, String artifactId, String mediaType, Map<String, Object> payload) {
        String operation = (String) payload.get("operation");
        String operationType = (String) payload.getOrDefault("operationType", "MULTIMODAL");

        LOG.info("[MediaArtifactConsumer] Processing request for artifact {} operation {} type {}",
            artifactId, operation, operationType);

        JobType jobType = switch (operationType.toUpperCase()) {
            case "TRANSCRIPTION" -> JobType.TRANSCRIPTION;
            case "VISION_ANALYSIS", "VISION" -> JobType.VISION_ANALYSIS;
            default -> JobType.MULTIMODAL;
        };

        String jobId = processingProvider.submitJob(
            artifactId,
            tenantId,
            mediaType != null ? mediaType : "video/mp4",
            new byte[0], // Placeholder - would fetch from storage
            jobType,
            Map.of("operation", operation != null ? operation : "auto")
        );

        LOG.info("[MediaArtifactConsumer] Submitted processing job {} for artifact {}", jobId, artifactId);
    }
}
