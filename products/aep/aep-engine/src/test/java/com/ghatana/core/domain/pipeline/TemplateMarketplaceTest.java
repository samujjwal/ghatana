/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void initializesWithDefaultTemplates() { // GH-90000
        TemplateMarketplace marketplace = new TemplateMarketplace(); // GH-90000

        assertThat(marketplace.getPopular(10)).hasSizeGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("searches templates by query")
    void searchesTemplatesByQuery() { // GH-90000
        TemplateMarketplace marketplace = new TemplateMarketplace(); // GH-90000

        List<TemplateMarketplace.TemplateListing> results = marketplace.search("fraud");

        assertThat(results).isNotEmpty(); // GH-90000
        assertThat(results.stream().allMatch(l -> l.name().toLowerCase().contains("fraud") ||
                                              l.description().toLowerCase().contains("fraud"))).isTrue();
    }

    @Test
    @DisplayName("gets templates by category")
    void getsTemplatesByCategory() { // GH-90000
        TemplateMarketplace marketplace = new TemplateMarketplace(); // GH-90000

        List<TemplateMarketplace.TemplateListing> results = marketplace.getByCategory("security");

        assertThat(results).isNotEmpty(); // GH-90000
        assertThat(results.stream().allMatch(l -> l.category().equalsIgnoreCase("security"))).isTrue();
    }

    @Test
    @DisplayName("gets popular templates")
    void getsPopularTemplates() { // GH-90000
        TemplateMarketplace marketplace = new TemplateMarketplace(); // GH-90000

        List<TemplateMarketplace.TemplateListing> popular = marketplace.getPopular(3); // GH-90000

        assertThat(popular).hasSizeLessThanOrEqualTo(3); // GH-90000
        // Should be sorted by popularity descending
        for (int i = 0; i < popular.size() - 1; i++) { // GH-90000
            assertThat(popular.get(i).popularity()).isGreaterThanOrEqualTo(popular.get(i + 1).popularity()); // GH-90000
        }
    }

    @Test
    @DisplayName("gets new templates")
    void getsNewTemplates() { // GH-90000
        TemplateMarketplace marketplace = new TemplateMarketplace(); // GH-90000

        List<TemplateMarketplace.TemplateListing> newTemplates = marketplace.getNew(3); // GH-90000

        assertThat(newTemplates).hasSizeLessThanOrEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("rates a template")
    void ratesTemplate() { // GH-90000
        TemplateMarketplace marketplace = new TemplateMarketplace(); // GH-90000

        TemplateMarketplace.TemplateListing original = marketplace.getListing("fraud-detection").orElseThrow();
        double originalRating = original.averageRating(); // GH-90000

        marketplace.rateTemplate("fraud-detection", 5); // GH-90000

        TemplateMarketplace.TemplateListing updated = marketplace.getListing("fraud-detection").orElseThrow();
        assertThat(updated.averageRating()).isNotEqualTo(originalRating); // GH-90000
        assertThat(updated.ratingCount()).isEqualTo(original.ratingCount() + 1); // GH-90000
    }

    @Test
    @DisplayName("records template usage")
    void recordsTemplateUsage() { // GH-90000
        TemplateMarketplace marketplace = new TemplateMarketplace(); // GH-90000

        TemplateMarketplace.TemplateListing original = marketplace.getListing("fraud-detection").orElseThrow();
        int originalUsage = original.usageCount(); // GH-90000

        marketplace.recordUsage("fraud-detection");

        TemplateMarketplace.TemplateListing updated = marketplace.getListing("fraud-detection").orElseThrow();
        assertThat(updated.usageCount()).isEqualTo(originalUsage + 1); // GH-90000
        assertThat(updated.popularity()).isGreaterThan(original.popularity()); // GH-90000
    }

    @Test
    @DisplayName("gets usage statistics")
    void getsUsageStatistics() { // GH-90000
        TemplateMarketplace marketplace = new TemplateMarketplace(); // GH-90000

        marketplace.recordUsage("fraud-detection");

        var stats = marketplace.getUsageStats("fraud-detection");
        assertThat(stats).isPresent(); // GH-90000
        assertThat(stats.get().usageCount()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("gets spec builder for template")
    void getsSpecBuilderForTemplate() { // GH-90000
        TemplateMarketplace marketplace = new TemplateMarketplace(); // GH-90000

        var specBuilder = marketplace.getSpecBuilder("fraud-detection");
        assertThat(specBuilder).isPresent(); // GH-90000
    }

    @Test
    @DisplayName("gets all categories")
    void getsAllCategories() { // GH-90000
        TemplateMarketplace marketplace = new TemplateMarketplace(); // GH-90000

        List<TemplateMarketplace.TemplateCategory> categories = marketplace.getCategories(); // GH-90000

        assertThat(categories).isNotEmpty(); // GH-90000
        assertThat(categories.stream().anyMatch(c -> c.id().equals("security"))).isTrue();
    }

    @Test
    @DisplayName("lists custom template")
    void listsCustomTemplate() { // GH-90000
        TemplateMarketplace marketplace = new TemplateMarketplace(); // GH-90000

        PipelineSpecBuilder specBuilder = PipelineTemplateLibrary.rawIngest("custom-pipe", "tenant-1"); // GH-90000
        TemplateMarketplace.TemplateListing listing = new TemplateMarketplace.TemplateListing( // GH-90000
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
            java.time.Instant.now() // GH-90000
        );

        String listingId = marketplace.listTemplate(listing); // GH-90000

        assertThat(marketplace.getListing(listingId)).isPresent(); // GH-90000
    }

    @Test
    @DisplayName("searches by tags")
    void searchesByTags() { // GH-90000
        TemplateMarketplace marketplace = new TemplateMarketplace(); // GH-90000

        List<TemplateMarketplace.TemplateListing> results = marketplace.search("ml");

        assertThat(results).isNotEmpty(); // GH-90000
        assertThat(results.stream().anyMatch(l -> l.tags().contains("ml"))).isTrue();
    }
}
