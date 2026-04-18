/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.domain.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for template marketplace.
 *
 * @doc.type class
 * @doc.purpose Unit tests for template marketplace
 * @doc.layer test
 */
@DisplayName("Template Marketplace Tests")
class TemplateMarketplaceTest {

    @Test
    @DisplayName("initializes with default curated templates")
    void initializesWithDefaultTemplates() {
        TemplateMarketplace marketplace = new TemplateMarketplace();

        assertThat(marketplace.getPopular(10)).hasSizeGreaterThan(0);
    }

    @Test
    @DisplayName("searches templates by query")
    void searchesTemplatesByQuery() {
        TemplateMarketplace marketplace = new TemplateMarketplace();

        List<TemplateMarketplace.TemplateListing> results = marketplace.search("fraud");

        assertThat(results).isNotEmpty();
        assertThat(results.stream().allMatch(l -> l.name().toLowerCase().contains("fraud") ||
                                              l.description().toLowerCase().contains("fraud"))).isTrue();
    }

    @Test
    @DisplayName("gets templates by category")
    void getsTemplatesByCategory() {
        TemplateMarketplace marketplace = new TemplateMarketplace();

        List<TemplateMarketplace.TemplateListing> results = marketplace.getByCategory("security");

        assertThat(results).isNotEmpty();
        assertThat(results.stream().allMatch(l -> l.category().equalsIgnoreCase("security"))).isTrue();
    }

    @Test
    @DisplayName("gets popular templates")
    void getsPopularTemplates() {
        TemplateMarketplace marketplace = new TemplateMarketplace();

        List<TemplateMarketplace.TemplateListing> popular = marketplace.getPopular(3);

        assertThat(popular).hasSizeLessThanOrEqualTo(3);
        // Should be sorted by popularity descending
        for (int i = 0; i < popular.size() - 1; i++) {
            assertThat(popular.get(i).popularity()).isGreaterThanOrEqualTo(popular.get(i + 1).popularity());
        }
    }

    @Test
    @DisplayName("gets new templates")
    void getsNewTemplates() {
        TemplateMarketplace marketplace = new TemplateMarketplace();

        List<TemplateMarketplace.TemplateListing> newTemplates = marketplace.getNew(3);

        assertThat(newTemplates).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("rates a template")
    void ratesTemplate() {
        TemplateMarketplace marketplace = new TemplateMarketplace();

        TemplateMarketplace.TemplateListing original = marketplace.getListing("fraud-detection").orElseThrow();
        double originalRating = original.averageRating();

        marketplace.rateTemplate("fraud-detection", 5);

        TemplateMarketplace.TemplateListing updated = marketplace.getListing("fraud-detection").orElseThrow();
        assertThat(updated.averageRating()).isNotEqualTo(originalRating);
        assertThat(updated.ratingCount()).isEqualTo(original.ratingCount() + 1);
    }

    @Test
    @DisplayName("records template usage")
    void recordsTemplateUsage() {
        TemplateMarketplace marketplace = new TemplateMarketplace();

        TemplateMarketplace.TemplateListing original = marketplace.getListing("fraud-detection").orElseThrow();
        int originalUsage = original.usageCount();

        marketplace.recordUsage("fraud-detection");

        TemplateMarketplace.TemplateListing updated = marketplace.getListing("fraud-detection").orElseThrow();
        assertThat(updated.usageCount()).isEqualTo(originalUsage + 1);
        assertThat(updated.popularity()).isGreaterThan(original.popularity());
    }

    @Test
    @DisplayName("gets usage statistics")
    void getsUsageStatistics() {
        TemplateMarketplace marketplace = new TemplateMarketplace();

        marketplace.recordUsage("fraud-detection");

        var stats = marketplace.getUsageStats("fraud-detection");
        assertThat(stats).isPresent();
        assertThat(stats.get().usageCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("gets spec builder for template")
    void getsSpecBuilderForTemplate() {
        TemplateMarketplace marketplace = new TemplateMarketplace();

        var specBuilder = marketplace.getSpecBuilder("fraud-detection");
        assertThat(specBuilder).isPresent();
    }

    @Test
    @DisplayName("gets all categories")
    void getsAllCategories() {
        TemplateMarketplace marketplace = new TemplateMarketplace();

        List<TemplateMarketplace.TemplateCategory> categories = marketplace.getCategories();

        assertThat(categories).isNotEmpty();
        assertThat(categories.stream().anyMatch(c -> c.id().equals("security"))).isTrue();
    }

    @Test
    @DisplayName("lists custom template")
    void listsCustomTemplate() {
        TemplateMarketplace marketplace = new TemplateMarketplace();

        PipelineSpecBuilder specBuilder = PipelineTemplateLibrary.rawIngest("custom-pipe", "tenant-1");
        TemplateMarketplace.TemplateListing listing = new TemplateMarketplace.TemplateListing(
            "custom-template",
            "Custom Template",
            "A custom template",
            "custom",
            "user",
            "1.0.0",
            specBuilder,
            List.of("custom"),
            "beginner",
            5.0,
            1,
            10,
            5,
            java.time.Instant.now()
        );

        String listingId = marketplace.listTemplate(listing);

        assertThat(marketplace.getListing(listingId)).isPresent();
    }

    @Test
    @DisplayName("searches by tags")
    void searchesByTags() {
        TemplateMarketplace marketplace = new TemplateMarketplace();

        List<TemplateMarketplace.TemplateListing> results = marketplace.search("ml");

        assertThat(results).isNotEmpty();
        assertThat(results.stream().anyMatch(l -> l.tags().contains("ml"))).isTrue();
    }
}
