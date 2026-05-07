/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.domain.pipeline;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Curated marketplace for pipeline templates with discovery, ratings, and analytics.
 * Enhances existing template libraries with marketplace features.
 *
 * @doc.type class
 * @doc.purpose Curated template marketplace with discovery and ratings
 * @doc.layer core
 * @doc.pattern Registry, Service
 */
public final class TemplateMarketplace {

    private final Map<String, TemplateListing> listings = new ConcurrentHashMap<>();
    private final Map<String, TemplateCategory> categories = new ConcurrentHashMap<>();
    private final Map<String, TemplateUsageStats> usageStats = new ConcurrentHashMap<>();

    /**
     * Creates a template marketplace with default curated templates.
     */
    public TemplateMarketplace() {
        initializeDefaultTemplates();
        initializeDefaultCategories();
    }

    /**
     * Lists a template in the marketplace.
     *
     * @param listing the template listing
     * @return the listing ID
     */
    public String listTemplate(TemplateListing listing) {
        listings.put(listing.id(), listing);
        usageStats.put(listing.id(), new TemplateUsageStats(listing.id(), 0, 0, 0.0, Instant.now()));
        return listing.id();
    }

    /**
     * Gets a template listing by ID.
     *
     * @param listingId listing identifier
     * @return the listing, or empty if not found
     */
    public Optional<TemplateListing> getListing(String listingId) {
        return Optional.ofNullable(listings.get(listingId));
    }

    /**
     * Searches for templates by query.
     *
     * @param query search query
     * @return list of matching listings
     */
    public List<TemplateListing> search(String query) {
        String lowerQuery = query.toLowerCase();
        return listings.values().stream()
            .filter(l -> l.name().toLowerCase().contains(lowerQuery) ||
                        l.description().toLowerCase().contains(lowerQuery) ||
                        l.tags().stream().anyMatch(t -> t.toLowerCase().contains(lowerQuery)))
            .sorted(Comparator.comparing(TemplateListing::popularity).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Gets templates by category.
     *
     * @param category category name
     * @return list of listings in the category
     */
    public List<TemplateListing> getByCategory(String category) {
        return listings.values().stream()
            .filter(l -> l.category().equalsIgnoreCase(category))
            .sorted(Comparator.comparing(TemplateListing::popularity).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Gets popular templates.
     *
     * @param limit maximum number of results
     * @return list of popular templates
     */
    public List<TemplateListing> getPopular(int limit) {
        return listings.values().stream()
            .sorted(Comparator.comparing(TemplateListing::popularity).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Gets newly added templates.
     *
     * @param limit maximum number of results
     * @return list of new templates
     */
    public List<TemplateListing> getNew(int limit) {
        return listings.values().stream()
            .sorted(Comparator.comparing(TemplateListing::createdAt).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Gets all categories.
     *
     * @return list of categories
     */
    public List<TemplateCategory> getCategories() {
        return List.copyOf(categories.values());
    }

    /**
     * Rates a template.
     *
     * @param listingId listing identifier
     * @param rating rating (1-5)
     */
    public void rateTemplate(String listingId, int rating) {
        TemplateListing listing = listings.get(listingId);
        if (listing != null && rating >= 1 && rating <= 5) {
            int newRatingCount = listing.ratingCount() + 1;
            double newAverageRating = ((listing.averageRating() * listing.ratingCount()) + rating) / newRatingCount;
            
            listings.put(listingId, new TemplateListing(
                listing.id(),
                listing.name(),
                listing.description(),
                listing.category(),
                listing.author(),
                listing.version(),
                listing.specBuilder(),
                listing.tags(),
                listing.difficulty(),
                newAverageRating,
                newRatingCount,
                listing.popularity(),
                listing.usageCount(),
                listing.createdAt()
            ));
        }
    }

    /**
     * Records a template usage.
     *
     * @param listingId listing identifier
     */
    public void recordUsage(String listingId) {
        TemplateUsageStats stats = usageStats.get(listingId);
        if (stats != null) {
            TemplateUsageStats newStats = new TemplateUsageStats(
                stats.listingId(),
                stats.usageCount() + 1,
                stats.downloadCount() + 1,
                stats.averageRating(),
                Instant.now()
            );
            usageStats.put(listingId, newStats);

            // Update listing popularity
            TemplateListing listing = listings.get(listingId);
            if (listing != null) {
                listings.put(listingId, new TemplateListing(
                    listing.id(),
                    listing.name(),
                    listing.description(),
                    listing.category(),
                    listing.author(),
                    listing.version(),
                    listing.specBuilder(),
                    listing.tags(),
                    listing.difficulty(),
                    listing.averageRating(),
                    listing.ratingCount(),
                    listing.popularity() + 1,
                    listing.usageCount() + 1,
                    listing.createdAt()
                ));
            }
        }
    }

    /**
     * Gets usage statistics for a template.
     *
     * @param listingId listing identifier
     * @return usage statistics, or empty if not found
     */
    public Optional<TemplateUsageStats> getUsageStats(String listingId) {
        return Optional.ofNullable(usageStats.get(listingId));
    }

    /**
     * Gets the template spec builder for a listing.
     *
     * @param listingId listing identifier
     * @return the spec builder, or empty if not found
     */
    public Optional<PipelineSpecBuilder> getSpecBuilder(String listingId) {
        return Optional.ofNullable(listings.get(listingId))
            .map(TemplateListing::specBuilder);
    }

    /**
     * Initializes default curated templates from existing libraries.
     */
    private void initializeDefaultTemplates() {
        // Fraud detection template
        listTemplate(new TemplateListing(
            "fraud-detection",
            "Real-time Fraud Detection",
            "Detects fraudulent transactions with ML-based anomaly scoring and real-time alerting",
            "security",
            "Ghatana",
            "1.0.0",
            AepQuickStartTemplates.fraudDetection("fraud-detection", "default"),
            List.of("fraud", "security", "ml", "real-time"),
            "intermediate",
            4.5,
            120,
            500,
            1500,
            Instant.now()
        ));

        // Clickstream analytics template
        listTemplate(new TemplateListing(
            "clickstream-analytics",
            "Clickstream Analytics",
            "Enriches user click events with session data and aggregates metrics",
            "analytics",
            "Ghatana",
            "1.0.0",
            AepQuickStartTemplates.clickstreamAnalytics("clickstream-analytics", "default"),
            List.of("analytics", "clickstream", "session", "aggregation"),
            "beginner",
            4.2,
            85,
            300,
            800,
            Instant.now()
        ));

        // IoT telemetry template
        listTemplate(new TemplateListing(
            "iot-telemetry",
            "IoT Telemetry Pipeline",
            "Ingests device telemetry with validation, threshold alerting, and time-series storage",
            "iot",
            "Ghatana",
            "1.0.0",
            AepQuickStartTemplates.iotTelemetry("iot-telemetry", "default"),
            List.of("iot", "telemetry", "mqtt", "alerting"),
            "intermediate",
            4.0,
            60,
            200,
            600,
            Instant.now()
        ));

        // Audit log template
        listTemplate(new TemplateListing(
            "audit-log-pipeline",
            "Compliance Audit Log",
            "Collects audit logs with compliance filtering and PII masking for archival",
            "compliance",
            "Ghatana",
            "1.0.0",
            AepQuickStartTemplates.auditLogPipeline("audit-log-pipeline", "default"),
            List.of("compliance", "audit", "pii", "archival"),
            "advanced",
            4.8,
            40,
            150,
            400,
            Instant.now()
        ));

        // Multi-tenant router template
        listTemplate(new TemplateListing(
            "multi-tenant-router",
            "Multi-Tenant Event Router",
            "Routes events to per-tenant downstream sinks with tenant classification",
            "multi-tenant",
            "Ghatana",
            "1.0.0",
            AepQuickStartTemplates.multiTenantRouter("multi-tenant-router", "default"),
            List.of("multi-tenant", "routing", "tenant-isolation"),
            "advanced",
            4.3,
            35,
            120,
            350,
            Instant.now()
        ));

        // Event enrichment template
        listTemplate(new TemplateListing(
            "event-enrichment",
            "Event Enrichment",
            "Enriches events with external data from lookup services before sinking",
            "enrichment",
            "Ghatana",
            "1.0.0",
            PipelineTemplateLibrary.eventEnrichment("event-enrichment", "default"),
            List.of("enrichment", "lookup", "transformation"),
            "beginner",
            4.1,
            90,
            250,
            700,
            Instant.now()
        ));

        // Windowed aggregation template
        listTemplate(new TemplateListing(
            "windowed-aggregation",
            "Windowed Aggregation",
            "Aggregates events in tumbling time windows and emits summary records",
            "analytics",
            "Ghatana",
            "1.0.0",
            PipelineTemplateLibrary.windowedAggregation("windowed-aggregation", "default"),
            List.of("aggregation", "windowing", "analytics"),
            "intermediate",
            4.4,
            75,
            220,
            650,
            Instant.now()
        ));
    }

    /**
     * Initializes default categories.
     */
    private void initializeDefaultCategories() {
        categories.put("security", new TemplateCategory("security", "Security & Fraud Detection", "Templates for security monitoring and fraud detection"));
        categories.put("analytics", new TemplateCategory("analytics", "Analytics & Metrics", "Templates for data analytics and metric aggregation"));
        categories.put("iot", new TemplateCategory("iot", "IoT & Telemetry", "Templates for IoT device telemetry and monitoring"));
        categories.put("compliance", new TemplateCategory("compliance", "Compliance & Audit", "Templates for compliance logging and audit trails"));
        categories.put("multi-tenant", new TemplateCategory("multi-tenant", "Multi-Tenant", "Templates for multi-tenant event routing"));
        categories.put("enrichment", new TemplateCategory("enrichment", "Data Enrichment", "Templates for event enrichment and transformation"));
    }

    /**
     * Template listing record.
     *
     * @param id unique listing identifier
     * @param name template name
     * @param description template description
     * @param category template category
     * @param author template author
     * @param version template version
     * @param specBuilder pipeline spec builder
     * @param tags template tags
     * @param difficulty difficulty level
     * @param averageRating average rating (1-5)
     * @param ratingCount number of ratings
     * @param popularity popularity score
     * @param usageCount number of times used
     * @param createdAt when the listing was created
     */
    public record TemplateListing(
        String id,
        String name,
        String description,
        String category,
        String author,
        String version,
        PipelineSpecBuilder specBuilder,
        List<String> tags,
        String difficulty,
        double averageRating,
        int ratingCount,
        int popularity,
        int usageCount,
        Instant createdAt
    ) {
        public TemplateListing {
            tags = List.copyOf(tags);
            averageRating = Math.max(1.0, Math.min(5.0, averageRating));
        }
    }

    /**
     * Template category record.
     *
     * @param id category identifier
     * @param name category name
     * @param description category description
     */
    public record TemplateCategory(
        String id,
        String name,
        String description
    ) {}

    /**
     * Template usage statistics.
     *
     * @param listingId listing identifier
     * @param usageCount number of uses
     * @param downloadCount number of downloads
     * @param averageRating average rating
     * @param lastUsed when last used
     */
    public record TemplateUsageStats(
        String listingId,
        int usageCount,
        int downloadCount,
        double averageRating,
        Instant lastUsed
    ) {}
}
