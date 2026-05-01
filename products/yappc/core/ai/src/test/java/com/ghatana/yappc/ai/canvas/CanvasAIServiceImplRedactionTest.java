package com.ghatana.yappc.ai.canvas;

import com.ghatana.agent.memory.security.MemoryRedactionFilter;
import com.ghatana.contracts.canvas.v1.*;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.SimpleMetricsCollector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests that PII redaction is applied to canvas AI history
 * before persistence and after retrieval.
 *
 * @doc.type class
 * @doc.purpose Regression tests for PII redaction on canvas AI history
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CanvasAIServiceImpl PII Redaction")
class CanvasAIServiceImplRedactionTest {

    private CanvasAIServiceImpl service;
    private DataSource dataSource;
    private MemoryRedactionFilter redactionFilter;

    @BeforeEach
    void setUp() throws SQLException {
        org.h2.jdbcx.JdbcDataSource h2Ds = new org.h2.jdbcx.JdbcDataSource();
        h2Ds.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        h2Ds.setUser("sa");
        h2Ds.setPassword("");
        this.dataSource = h2Ds;

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE canvas_validation_history (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "canvas_id VARCHAR(255), " +
                "report_bytes BLOB, " +
                "created_at TIMESTAMP)");
            stmt.execute("CREATE TABLE canvas_generation_history (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "canvas_id VARCHAR(255), " +
                "result_bytes BLOB, " +
                "created_at TIMESTAMP)");
        }

        this.redactionFilter = MemoryRedactionFilter.defaultFilter();
        MetricsCollector metrics = new SimpleMetricsCollector(new SimpleMeterRegistry());

        CanvasValidationService validationService = mock(CanvasValidationService.class);
        CanvasGenerationService generationService = mock(CanvasGenerationService.class);

        this.service = new CanvasAIServiceImpl(
            validationService, generationService, metrics, dataSource, redactionFilter);
    }

    @Test
    @DisplayName("Validation report PII is redacted on save and load")
    void validationReportPiiIsRedacted() throws Exception {
        String piiEmail = "user@example.com";
        ValidationReport report = ValidationReport.newBuilder()
            .setCanvasId("canvas-1")
            .addIssues(ValidationIssue.newBuilder()
                .setId("issue-1")
                .setTitle("Issue with " + piiEmail)
                .setDescription("Contact " + piiEmail + " for details")
                .setSuggestion("Email " + piiEmail)
                .build())
            .addRisks(RiskAssessment.newBuilder()
                .setId("risk-1")
                .setTitle("Risk: " + piiEmail)
                .setDescription("Description with " + piiEmail)
                .setImpact("Impact: " + piiEmail)
                .setMitigation("Mitigation: " + piiEmail)
                .build())
            .addGaps("Gap: " + piiEmail)
            .build();

        Method saveMethod = CanvasAIServiceImpl.class.getDeclaredMethod(
            "saveValidationReport", String.class, ValidationReport.class);
        saveMethod.setAccessible(true);
        saveMethod.invoke(service, "canvas-1", report);

        Method loadMethod = CanvasAIServiceImpl.class.getDeclaredMethod(
            "loadValidationReports", String.class, int.class);
        loadMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ValidationReport> loaded = (List<ValidationReport>) loadMethod.invoke(service, "canvas-1", 10);

        assertThat(loaded).hasSize(1);
        ValidationReport loadedReport = loaded.get(0);

        ValidationIssue issue = loadedReport.getIssues(0);
        assertThat(issue.getTitle()).doesNotContain(piiEmail).contains("[REDACTED]");
        assertThat(issue.getDescription()).doesNotContain(piiEmail).contains("[REDACTED]");
        assertThat(issue.getSuggestion()).doesNotContain(piiEmail).contains("[REDACTED]");

        RiskAssessment risk = loadedReport.getRisks(0);
        assertThat(risk.getTitle()).doesNotContain(piiEmail).contains("[REDACTED]");
        assertThat(risk.getDescription()).doesNotContain(piiEmail).contains("[REDACTED]");
        assertThat(risk.getImpact()).doesNotContain(piiEmail).contains("[REDACTED]");
        assertThat(risk.getMitigation()).doesNotContain(piiEmail).contains("[REDACTED]");

        assertThat(loadedReport.getGaps(0)).doesNotContain(piiEmail).contains("[REDACTED]");
    }

    @Test
    @DisplayName("Generation result PII is redacted on save and load")
    void generationResultPiiIsRedacted() throws Exception {
        String piiEmail = "user@example.com";
        CodeGenerationResult result = CodeGenerationResult.newBuilder()
            .setGenerationId("gen-1")
            .setSummary("Summary with " + piiEmail)
            .addArtifacts(GeneratedArtifact.newBuilder()
                .setId("art-1")
                .setPath("/path/" + piiEmail)
                .setContent("Content with " + piiEmail)
                .setLanguage("typescript")
                .setFramework("react")
                .build())
            .addErrors("Error: " + piiEmail)
            .addWarnings("Warning: " + piiEmail)
            .build();

        Method saveMethod = CanvasAIServiceImpl.class.getDeclaredMethod(
            "saveGenerationResult", String.class, CodeGenerationResult.class);
        saveMethod.setAccessible(true);
        saveMethod.invoke(service, "canvas-1", result);

        Method loadMethod = CanvasAIServiceImpl.class.getDeclaredMethod(
            "loadGenerationResults", String.class, int.class);
        loadMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<CodeGenerationResult> loaded = (List<CodeGenerationResult>) loadMethod.invoke(service, "canvas-1", 10);

        assertThat(loaded).hasSize(1);
        CodeGenerationResult loadedResult = loaded.get(0);

        assertThat(loadedResult.getSummary()).doesNotContain(piiEmail).contains("[REDACTED]");

        GeneratedArtifact artifact = loadedResult.getArtifacts(0);
        assertThat(artifact.getPath()).doesNotContain(piiEmail).contains("[REDACTED]");
        assertThat(artifact.getContent()).doesNotContain(piiEmail).contains("[REDACTED]");

        assertThat(loadedResult.getErrors(0)).doesNotContain(piiEmail).contains("[REDACTED]");
        assertThat(loadedResult.getWarnings(0)).doesNotContain(piiEmail).contains("[REDACTED]");
    }
}
