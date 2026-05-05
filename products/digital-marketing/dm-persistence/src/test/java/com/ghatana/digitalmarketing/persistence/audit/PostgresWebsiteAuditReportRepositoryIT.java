package com.ghatana.digitalmarketing.persistence.audit;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.audit.AuditSeverity;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditFinding;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditReport;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PostgresWebsiteAuditReportRepository} using a real PostgreSQL container.
 *
 * <p>Tests are event-loop safe: all {@code Promise}-returning calls are executed
 * via {@link #runPromise(java.util.concurrent.Callable)}.</p>
 *
 * @doc.type class
 * @doc.purpose Testcontainers integration test for PostgresWebsiteAuditReportRepository
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PostgresWebsiteAuditReportRepository — integration tests")
class PostgresWebsiteAuditReportRepositoryIT extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dmos_test")
            .withUsername("dmos")
            .withPassword("dmos_secret");

    private static PostgresWebsiteAuditReportRepository repository;

    @BeforeAll
    static void migrateSchema() {
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Executor executor = Runnable::run;
        repository = new PostgresWebsiteAuditReportRepository(ds, executor);
    }

    @BeforeEach
    void cleanTable() {
        try (var conn = POSTGRES.createConnection("")) {
            conn.createStatement().executeUpdate("DELETE FROM dmos_website_audit_reports");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean dmos_website_audit_reports table", e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static WebsiteAuditReport buildReport(String reportId, String workspaceId) {
        WebsiteAuditFinding finding = new WebsiteAuditFinding(
            AuditSeverity.CRITICAL,
            "performance",
            "Page load time exceeds 3 seconds",
            "Slow LCP impacts user experience and SEO ranking",
            "Optimize images and defer non-critical JavaScript",
            "https://example.com/performance"
        );

        return WebsiteAuditReport.builder()
            .reportId(reportId)
            .workspaceId(DmWorkspaceId.of(workspaceId))
            .websiteUrl("https://example.com")
            .findings(List.of(finding))
            .generatedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .generatedBy("test-user")
            .build();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save — persists a new report and returns it unchanged")
    void save_persistsNewReport() {
        WebsiteAuditReport report = buildReport("rep-001", "ws-alpha");

        WebsiteAuditReport saved = runPromise(() -> repository.save(report));

        assertThat(saved.getReportId()).isEqualTo("rep-001");
        assertThat(saved.getWorkspaceId().getValue()).isEqualTo("ws-alpha");
        assertThat(saved.getWebsiteUrl()).isEqualTo("https://example.com");
        assertThat(saved.getFindings()).hasSize(1);
    }

    @Test
    @DisplayName("save — idempotent upsert updates findings on conflict")
    void save_upsert_updatesOnConflict() {
        WebsiteAuditReport original = buildReport("rep-004", "ws-delta");
        runPromise(() -> repository.save(original));

        WebsiteAuditFinding updatedFinding = new WebsiteAuditFinding(
            AuditSeverity.WARNING,
            "seo",
            "Missing meta descriptions",
            "Meta descriptions help with SEO click-through rates",
            "Add unique meta descriptions to all pages",
            "https://example.com/seo"
        );

        WebsiteAuditReport updated = WebsiteAuditReport.builder()
            .reportId("rep-004")
            .workspaceId(DmWorkspaceId.of("ws-delta"))
            .websiteUrl("https://updated-example.com")
            .findings(List.of(updatedFinding))
            .generatedAt(Instant.parse("2026-02-01T00:00:00Z"))
            .generatedBy("updater")
            .build();
        runPromise(() -> repository.save(updated));

        Optional<WebsiteAuditReport> found = runPromise(() ->
            repository.findLatestByWorkspace(DmWorkspaceId.of("ws-delta")));
        assertThat(found).isPresent();
        assertThat(found.get().getWebsiteUrl()).isEqualTo("https://updated-example.com");
        assertThat(found.get().getFindings()).hasSize(1);
        assertThat(found.get().getFindings().get(0).severity()).isEqualTo(AuditSeverity.WARNING);
    }

    @Test
    @DisplayName("findLatestByWorkspace — returns latest report for workspace")
    void findLatestByWorkspace_returnsLatest() {
        WebsiteAuditReport oldReport = buildReport("rep-005", "ws-latest");
        runPromise(() -> repository.save(oldReport));

        WebsiteAuditReport newReport = buildReport("rep-006", "ws-latest");
        runPromise(() -> repository.save(newReport));

        Optional<WebsiteAuditReport> found = runPromise(() ->
            repository.findLatestByWorkspace(DmWorkspaceId.of("ws-latest")));

        assertThat(found).isPresent();
        assertThat(found.get().getReportId()).isEqualTo("rep-006");
    }

    @Test
    @DisplayName("findLatestByWorkspace — returns empty when workspace has no reports")
    void findLatestByWorkspace_returnsEmpty_whenNone() {
        Optional<WebsiteAuditReport> found = runPromise(() ->
            repository.findLatestByWorkspace(DmWorkspaceId.of("ws-empty")));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save — reports in different workspaces are stored independently")
    void save_multipleWorkspaces_storedIndependently() {
        WebsiteAuditReport r1 = buildReport("rep-007", "ws-one");
        WebsiteAuditReport r2 = buildReport("rep-008", "ws-two");
        runPromise(() -> repository.save(r1));
        runPromise(() -> repository.save(r2));

        Optional<WebsiteAuditReport> found1 = runPromise(() ->
            repository.findLatestByWorkspace(DmWorkspaceId.of("ws-one")));
        Optional<WebsiteAuditReport> found2 = runPromise(() ->
            repository.findLatestByWorkspace(DmWorkspaceId.of("ws-two")));

        assertThat(found1).isPresent();
        assertThat(found2).isPresent();
        assertThat(found1.get().getWorkspaceId().getValue()).isEqualTo("ws-one");
        assertThat(found2.get().getWorkspaceId().getValue()).isEqualTo("ws-two");
    }

    @Test
    @DisplayName("save — preserves findings list with multiple items")
    void save_preservesMultipleFindings() {
        WebsiteAuditFinding finding1 = new WebsiteAuditFinding(
            AuditSeverity.CRITICAL,
            "security",
            "Missing SSL certificate",
            "No SSL exposes data in transit",
            "Install SSL certificate",
            "https://example.com/security"
        );
        WebsiteAuditFinding finding2 = new WebsiteAuditFinding(
            AuditSeverity.WARNING,
            "accessibility",
            "Missing alt text on images",
            "Alt text improves accessibility",
            "Add alt text to all images",
            "https://example.com/a11y"
        );

        WebsiteAuditReport report = WebsiteAuditReport.builder()
            .reportId("rep-008")
            .workspaceId(DmWorkspaceId.of("ws-multi"))
            .websiteUrl("https://multi-example.com")
            .findings(List.of(finding1, finding2))
            .generatedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .generatedBy("test-user")
            .build();

        runPromise(() -> repository.save(report));

        Optional<WebsiteAuditReport> found = runPromise(() ->
            repository.findLatestByWorkspace(DmWorkspaceId.of("ws-multi")));

        assertThat(found).isPresent();
        assertThat(found.get().getFindings()).hasSize(2);
    }
}
