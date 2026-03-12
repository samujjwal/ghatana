package com.ghatana.tutorputor.explorer.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

/**
 * @doc.type test
 * @doc.purpose Unit tests for QualityReport model
 * @doc.layer test
 */
class QualityReportTest {

    @Test
    @DisplayName("Should create QualityReport with passing score")
    void shouldCreateQualityReportWithPassingScore() {
        // When
        QualityReport report = QualityReport.builder()
            .passed(true)
            .overallScore(0.85)
            .issues(List.of())
            .build();

        // Then
        assertThat(report.isPassed()).isTrue();
        assertThat(report.getOverallScore()).isEqualTo(0.85);
        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    @DisplayName("Should create QualityReport with failing score and issues")
    void shouldCreateQualityReportWithFailingScore() {
        // When
        QualityReport report = QualityReport.builder()
            .passed(false)
            .overallScore(0.45)
            .issues(List.of("Content too short", "Missing visual aid"))
            .build();

        // Then
        assertThat(report.isPassed()).isFalse();
        assertThat(report.getOverallScore()).isEqualTo(0.45);
        assertThat(report.getIssues()).hasSize(2);
    }

    @Test
    @DisplayName("Should handle boundary score values")
    void shouldHandleBoundaryScoreValues() {
        // Test minimum score
        QualityReport minReport = QualityReport.builder()
            .passed(false)
            .overallScore(0.0)
            .issues(List.of())
            .build();
        assertThat(minReport.getOverallScore()).isZero();

        // Test maximum score
        QualityReport maxReport = QualityReport.builder()
            .passed(true)
            .overallScore(1.0)
            .issues(List.of())
            .build();
        assertThat(maxReport.getOverallScore()).isEqualTo(1.0);
    }
}
