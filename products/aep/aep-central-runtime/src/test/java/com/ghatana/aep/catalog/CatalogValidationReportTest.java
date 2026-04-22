/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CatalogValidationReport [GH-90000]")
class CatalogValidationReportTest {

    @Test
    @DisplayName("empty builder produces valid empty report [GH-90000]")
    void emptyBuilderProducesValidReport() { // GH-90000
        CatalogValidationReport report = CatalogValidationReport.builder().build(); // GH-90000

        assertThat(report.catalogCount()).isZero(); // GH-90000
        assertThat(report.totalAgents()).isZero(); // GH-90000
        assertThat(report.hasErrors()).isFalse(); // GH-90000
        assertThat(report.isValid()).isTrue(); // GH-90000
        assertThat(report.loadedCatalogs()).isEmpty(); // GH-90000
        assertThat(report.errors()).isEmpty(); // GH-90000
        assertThat(report.warnings()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("builder aggregates counts issues and exposes immutable collections [GH-90000]")
    void builderAggregatesCountsAndIssues() { // GH-90000
        CatalogValidationReport report = CatalogValidationReport.builder() // GH-90000
                .addLoadedCatalog("aep", 3) // GH-90000
                .addLoadedCatalog("yappc", 5) // GH-90000
                .addError("LOAD_FAILED", "catalog file unreadable") // GH-90000
                .addWarning("DUPLICATE_AGENT", "duplicate agent id detected") // GH-90000
                .build(); // GH-90000

        assertThat(report.catalogCount()).isEqualTo(2); // GH-90000
        assertThat(report.totalAgents()).isEqualTo(8); // GH-90000
        assertThat(report.hasErrors()).isTrue(); // GH-90000
        assertThat(report.isValid()).isFalse(); // GH-90000
        assertThat(report.loadedCatalogs()).isEqualTo(Map.of("aep", 3, "yappc", 5)); // GH-90000
        assertThat(report.errors()).singleElement() // GH-90000
                .extracting(CatalogValidationReport.ValidationIssue::code) // GH-90000
                .isEqualTo("LOAD_FAILED [GH-90000]");
        assertThat(report.warnings()).singleElement() // GH-90000
                .extracting(CatalogValidationReport.ValidationIssue::code) // GH-90000
                .isEqualTo("DUPLICATE_AGENT [GH-90000]");

        assertThatThrownBy(() -> report.loadedCatalogs().put("data-cloud", 1)) // GH-90000
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        assertThatThrownBy(() -> report.errors().add(new CatalogValidationReport.ValidationIssue("X", "Y"))) // GH-90000
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }
}
