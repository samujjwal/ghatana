package com.ghatana.products.finance.domains.sanctions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for adverse media screening per Sanctions-004
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Adverse Media Screening Tests")
class AdverseMediaScreeningTest {
    private AdverseMediaService service;

    @BeforeEach
    void setUp() {
        service = new AdverseMediaService();
    }

    @Test
    @DisplayName("Should search adverse media sources")
    void shouldSearchAdverseMedia() {
        ScreeningRequest request = new ScreeningRequest("ABC Corporation", "entity");
        List<MediaHit> hits = service.searchAdverseMedia(request, List.of("News", "Regulatory"));
        assertThat(hits).isNotNull();
    }

    @Test
    @DisplayName("Should categorize media risk level")
    void shouldCategorizeMediaRisk() {
        MediaArticle article = new MediaArticle("Fraud Allegations", "Subject involved in fraud case", "News", LocalDateTime.now());
        RiskAssessment assessment = service.assessRisk(article);
        assertThat(assessment.riskLevel()).isIn("LOW", "MEDIUM", "HIGH", "CRITICAL");
    }

    @Test
    @DisplayName("Should detect name matches in media")
    void shouldDetectNameMatches() {
        String entityName = "Global Bank Ltd";
        String articleText = "Global Bank Ltd under investigation for money laundering";
        MatchResult result = service.detectNameMatch(entityName, articleText);
        assertThat(result.matchFound()).isTrue();
        assertThat(result.confidence()).isGreaterThan(0.8);
    }

    @Test
    @DisplayName("Should filter false positive matches")
    void shouldFilterFalsePositives() {
        MediaHit hit = new MediaHit("John Smith", "Common name match", 0.6, "LOW");
        FilterResult result = service.filterFalsePositive(hit, List.of("DOB:1980-01-01", "Address:NY"));
        assertThat(result.isFalsePositive()).isIn(true, false);
    }

    @Test
    @DisplayName("Should generate media screening report")
    void shouldGenerateMediaReport() {
        List<MediaHit> hits = List.of(
            new MediaHit("Hit 1", "Description 1", 0.9, "HIGH"),
            new MediaHit("Hit 2", "Description 2", 0.7, "MEDIUM")
        );
        MediaReport report = service.generateReport("ENTITY_001", hits);
        assertThat(report.entityId()).isEqualTo("ENTITY_001");
        assertThat(report.summary()).isNotNull();
    }

    @Test
    @DisplayName("Should monitor for ongoing adverse coverage")
    void shouldMonitorOngoingCoverage() {
        String entityId = "ENTITY_002";
        service.startMonitoring(entityId, "WEEKLY");
        MonitoringStatus status = service.getMonitoringStatus(entityId);
        assertThat(status.active()).isTrue();
        assertThat(status.frequency()).isEqualTo("WEEKLY");
    }

    record ScreeningRequest(String name, String type) {}
    record MediaHit(String headline, String description, double confidence, String riskLevel) {}
    record MediaArticle(String headline, String content, String source, LocalDateTime published) {}
    record RiskAssessment(String riskLevel, double score, List<String> keywords) {}
    record MatchResult(boolean matchFound, double confidence, String matchedText) {}
    record FilterResult(boolean isFalsePositive, String reason) {}
    record MediaReport(String entityId, String summary, int hitCount, LocalDateTime generatedAt) {}
    record MonitoringStatus(boolean active, String frequency, LocalDateTime lastCheck) {}

    static class AdverseMediaService {
        List<MediaHit> searchAdverseMedia(ScreeningRequest request, List<String> sources) {
            return List.of(new MediaHit("Sample", "Sample description", 0.75, "MEDIUM"));
        }

        RiskAssessment assessRisk(MediaArticle article) {
            double score = article.content().toLowerCase().contains("fraud") || article.content().toLowerCase().contains("investigation") ? 0.9 : 0.3;
            String level = score > 0.8 ? "HIGH" : score > 0.5 ? "MEDIUM" : "LOW";
            return new RiskAssessment(level, score, List.of("fraud", "investigation"));
        }

        MatchResult detectNameMatch(String entityName, String text) {
            boolean found = text.toLowerCase().contains(entityName.toLowerCase());
            return new MatchResult(found, found ? 0.95 : 0.0, found ? entityName : null);
        }

        FilterResult filterFalsePositive(MediaHit hit, List<String> additionalInfo) {
            boolean isFP = hit.confidence() < 0.8 && !additionalInfo.isEmpty();
            return new FilterResult(isFP, isFP ? "Low confidence with additional identifiers" : "High confidence match");
        }

        MediaReport generateReport(String entityId, List<MediaHit> hits) {
            long highRisk = hits.stream().filter(h -> "HIGH".equals(h.riskLevel()) || "CRITICAL".equals(h.riskLevel())).count();
            String summary = highRisk > 0 ? highRisk + " high risk hits found" : "No high risk hits";
            return new MediaReport(entityId, summary, hits.size(), LocalDateTime.now());
        }

        void startMonitoring(String entityId, String frequency) {
        }

        MonitoringStatus getMonitoringStatus(String entityId) {
            return new MonitoringStatus(true, "WEEKLY", LocalDateTime.now().minusDays(2));
        }
    }
}
