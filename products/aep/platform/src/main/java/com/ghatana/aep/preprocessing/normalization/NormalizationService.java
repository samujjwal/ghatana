/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.preprocessing.normalization;

import com.ghatana.aep.preprocessing.eventization.SemanticEvent;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Main service for normalizing semantic events to canonical format.
 * 
 * <p><b>Purpose</b><br>
 * Transforms events from different sources into a unified canonical schema.
 * Uses pluggable {@link SourceAdapter}s for source-specific normalization logic.
 * 
 * <p><b>Architecture Role</b><br>
 * Second stage of Data Preprocessing cluster. Receives semantic events from
 * Eventization Service and produces canonical events for Detection Engine.
 * 
 * <p><b>Normalization Pipeline</b><br>
 * <pre>
 * SemanticEvents[] → selectAdapter() → validate() → transform() 
 *                  → enrichMetadata() → CanonicalEvents[]
 * </pre>
 * 
 * <p><b>Adapter Registry</b><br>
 * Maintains registry of source adapters. Auto-selects appropriate adapter
 * based on event source or type. Supports runtime adapter registration.
 * 
 * <p><b>Example</b><br>
 * <pre>{@code
 * NormalizationService service = new NormalizationService(executor);
 * service.registerAdapter(new HttpSourceAdapter());
 * service.registerAdapter(new DatabaseSourceAdapter());
 * 
 * List<SemanticEvent> semanticEvents = List.of(...);
 * Promise<List<CanonicalEvent>> promise = service.normalize(semanticEvents);
 * List<CanonicalEvent> canonicalEvents = promise.getResult();
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Source-agnostic event normalization
 * @doc.layer product
 * @doc.pattern Service
 */
public class NormalizationService {
    private static final Logger logger = LoggerFactory.getLogger(NormalizationService.class);
    
    private final ExecutorService executor;
    private final Map<String, SourceAdapter> adapters = new ConcurrentHashMap<>();
    private final SchemaTranslator schemaTranslator;

    public NormalizationService(ExecutorService executor, SchemaTranslator schemaTranslator) {
        this.executor = Objects.requireNonNull(executor, "executor required");
        this.schemaTranslator = Objects.requireNonNull(schemaTranslator, "schemaTranslator required");
        
        // Register default adapters
        registerDefaultAdapters();
    }

    /**
     * Normalizes semantic events to canonical format.
     * 
     * @param semanticEvents Events from eventization service
     * @return Promise of canonical events
     */
    public Promise<List<CanonicalEvent>> normalize(List<SemanticEvent> semanticEvents) {
        return Promise.ofBlocking(executor, () -> {
            logger.debug("Normalizing {} semantic events", semanticEvents.size());
            
            List<CanonicalEvent> canonicalEvents = new ArrayList<>();
            
            for (SemanticEvent event : semanticEvents) {
                try {
                    CanonicalEvent canonical = normalizeEvent(event);
                    canonicalEvents.add(canonical);
                } catch (Exception e) {
                    logger.warn("Failed to normalize event {}: {}", event.eventId(), e.getMessage());
                    // Create best-effort canonical event
                    canonicalEvents.add(createFallbackEvent(event));
                }
            }
            
            logger.info("Normalized {} semantic events to {} canonical events",
                    semanticEvents.size(), canonicalEvents.size());
            
            return canonicalEvents;
        });
    }

    /**
     * Normalizes a single semantic event.
     */
    private CanonicalEvent normalizeEvent(SemanticEvent event) {
        // Select appropriate adapter
        SourceAdapter adapter = selectAdapter(event);
        
        if (adapter == null) {
            logger.debug("No adapter found for event type {}, using default", event.eventType());
            return createDefaultCanonicalEvent(event);
        }

        // Validate event
        if (!adapter.validate(event)) {
            logger.warn("Event {} failed validation for adapter {}", 
                    event.eventId(), adapter.getSourceType());
            return createFallbackEvent(event);
        }

        // Normalize using adapter
        CanonicalEvent canonical = adapter.normalize(event);
        
        // Enrich with metadata
        return enrichMetadata(canonical, event);
    }

    /**
     * Selects appropriate adapter for event.
     */
    private SourceAdapter selectAdapter(SemanticEvent event) {
        // Try to find adapter by event type first
        String eventType = event.eventType().toLowerCase();
        
        for (SourceAdapter adapter : adapters.values()) {
            if (adapter.canHandle(event)) {
                return adapter;
            }
        }

        // Try domain-based selection
        if (event.domain() != null) {
            String domain = event.domain().toLowerCase();
            return adapters.get(domain);
        }

        return null;
    }

    /**
     * Creates default canonical event when no adapter available.
     */
    private CanonicalEvent createDefaultCanonicalEvent(SemanticEvent event) {
        return CanonicalEvent.builder()
                .eventId(event.eventId())
                .eventType(schemaTranslator.normalizeEventType(event.eventType()))
                .timestamp(event.timestamp())
                .source("unknown")
                .tenantId(extractTenantId(event))
                .domain(event.domain() != null ? event.domain() : "default")
                .attributes(event.attributes())
                .metadata(event.context())
                .quality(event.confidence() * 0.8) // Reduced quality for default normalization
                .build();
    }

    /**
     * Creates fallback event when normalization fails.
     */
    private CanonicalEvent createFallbackEvent(SemanticEvent event) {
        return CanonicalEvent.builder()
                .eventId(event.eventId())
                .eventType("UnknownEvent")
                .timestamp(event.timestamp())
                .source("unknown")
                .tenantId("default")
                .domain(event.domain() != null ? event.domain() : "unknown")
                .attributes(new HashMap<>())
                .metadata(Map.of(
                        "originalEventType", event.eventType(),
                        "normalizationFailed", "true"
                ))
                .quality(0.3) // Low quality for fallback
                .build();
    }

    /**
     * Enriches canonical event with additional metadata.
     */
    private CanonicalEvent enrichMetadata(CanonicalEvent canonical, SemanticEvent original) {
        Map<String, String> enrichedMetadata = new HashMap<>(canonical.metadata());
        
        // Add original event information
        enrichedMetadata.put("originalEventType", original.eventType());
        enrichedMetadata.put("aggregatedSignals", String.valueOf(original.aggregatedSignalCount()));
        enrichedMetadata.put("normalizationTimestamp", String.valueOf(System.currentTimeMillis()));
        
        // Add context from original event
        if (original.context() != null) {
            original.context().forEach(enrichedMetadata::putIfAbsent);
        }

        return CanonicalEvent.builder()
                .eventId(canonical.eventId())
                .eventType(canonical.eventType())
                .timestamp(canonical.timestamp())
                .source(canonical.source())
                .tenantId(canonical.tenantId())
                .domain(canonical.domain())
                .attributes(canonical.attributes())
                .metadata(enrichedMetadata)
                .quality(canonical.quality())
                .build();
    }

    /**
     * Extracts tenant ID from event context or attributes.
     */
    private String extractTenantId(SemanticEvent event) {
        // Check context first
        if (event.context() != null && event.context().containsKey("tenantId")) {
            return event.context().get("tenantId");
        }

        // Check attributes
        if (event.attributes() != null && event.attributes().containsKey("tenantId")) {
            return event.attributes().get("tenantId").toString();
        }

        return "default";
    }

    /**
     * Registers a source adapter.
     */
    public void registerAdapter(SourceAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter required");
        adapters.put(adapter.getSourceType(), adapter);
        logger.info("Registered source adapter: {}", adapter.getSourceType());
    }

    /**
     * Registers default adapters for common sources.
     */
    private void registerDefaultAdapters() {
        registerAdapter(new GenericSourceAdapter("http", "HttpActivityEvent"));
        registerAdapter(new GenericSourceAdapter("database", "DataChangeEvent"));
        registerAdapter(new GenericSourceAdapter("filesystem", "FileSystemEvent"));
        registerAdapter(new GenericSourceAdapter("telemetry", "TelemetryEvent"));
    }

    /**
     * Returns all registered adapters.
     */
    public Collection<SourceAdapter> getAdapters() {
        return adapters.values();
    }

    /**
     * Generic adapter for simple source types.
     */
    private static class GenericSourceAdapter implements SourceAdapter {
        private final String sourceType;
        private final String targetEventType;

        GenericSourceAdapter(String sourceType, String targetEventType) {
            this.sourceType = sourceType;
            this.targetEventType = targetEventType;
        }

        @Override
        public String getSourceType() {
            return sourceType;
        }

        @Override
        public boolean canHandle(SemanticEvent event) {
            return event.eventType().equalsIgnoreCase(targetEventType) ||
                   (event.domain() != null && event.domain().equalsIgnoreCase(sourceType));
        }

        @Override
        public CanonicalEvent normalize(SemanticEvent event) {
            return CanonicalEvent.builder()
                    .eventId(event.eventId())
                    .eventType(targetEventType)
                    .timestamp(event.timestamp())
                    .source(sourceType)
                    .tenantId(extractTenantId(event))
                    .domain(event.domain() != null ? event.domain() : sourceType)
                    .attributes(event.attributes() != null ? new HashMap<>(event.attributes()) : new HashMap<>())
                    .metadata(event.context() != null ? new HashMap<>(event.context()) : new HashMap<>())
                    .quality(event.confidence())
                    .build();
        }

        private String extractTenantId(SemanticEvent event) {
            if (event.context() != null && event.context().containsKey("tenantId")) {
                return event.context().get("tenantId");
            }
            if (event.attributes() != null && event.attributes().containsKey("tenantId")) {
                return event.attributes().get("tenantId").toString();
            }
            return "default";
        }
    }
}
