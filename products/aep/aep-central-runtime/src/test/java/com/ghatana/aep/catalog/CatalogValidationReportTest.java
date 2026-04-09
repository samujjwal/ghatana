/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CatalogValidationReport")
class CatalogValidationReportTest {

    @Test
    @DisplayName("empty builder produces valid empty report")
    void emptyBuilderProducesValidReport() {
        CatalogValidationReport report = CatalogValidationReport.builder().build();

        assertThat(report.catalogCount()).isZero();
        assertThat(report.totalAgents()).isZero();
        assertThat(report.hasErrors()).isFalse();
        assertThat(report.isValid()).isTrue();
        assertThat(report.loadedCatalogs()).isEmpty();
        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).isEmpty();
    }

    @Test
    @DisplayName("builder aggregates counts issues and exposes immutable collections")
    void builderAggregatesCountsAndIssues() {
        CatalogValidationReport report = CatalogValidationReport.builder()
                .addLoadedCatalog("aep", 3)
                .addLoadedCatalog("yappc", 5)
                .addError("LOAD_FAILED", "catalog file unreadable")
                .addWarning("DUPLICATE_AGENT", "duplicate agent id detected")
                .build();

        assertThat(report.catalogCount()).isEqualTo(2);
        assertThat(report.totalAgents()).isEqualTo(8);
        assertThat(report.hasErrors()).isTrue();
        assertThat(report.isValid()).isFalse();
        assertThat(report.loadedCatalogs()).isEqualTo(Map.of("aep", 3, "yappc", 5));
        assertThat(report.errors()).singleElement()
                .extracting(CatalogValidationReport.ValidationIssue::code)
                .isEqualTo("LOAD_FAILED");
        assertThat(report.warnings()).singleElement()
                .extracting(CatalogValidationReport.ValidationIssue::code)
                .isEqualTo("DUPLICATE_AGENT");

        assertThatThrownBy(() -> report.loadedCatalogs().put("data-cloud", 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> report.errors().add(new CatalogValidationReport.ValidationIssue("X", "Y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
