/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — ScaffoldService Tests
 */
package com.ghatana.yappc.api.scaffold;

import com.ghatana.yappc.api.scaffold.dto.*;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ScaffoldService}.
 *
 * <p>Covers template listing/filtering, project scaffolding job lifecycle,
 * job status tracking, download, cancellation, feature packs, and conflict
 * management.
 
 * @doc.type class
 * @doc.purpose Handles scaffold service test operations
 * @doc.layer product
 * @doc.pattern Test
*/
class ScaffoldServiceTest extends EventloopTestBase {

    private ScaffoldEngine scaffoldEngine;
    private TemplateRegistry templateRegistry;
    private ScaffoldService service;

    private static final Template JAVA_TEMPLATE = new Template(
            "java-microservice", "Java Microservice", "Spring Boot microservice starter",
            "backend", List.of("java", "spring"), "1.0.0",
            Map.of("javaVersion", "17", "buildTool", "gradle"));

    private static final Template REACT_TEMPLATE = new Template(
            "react-app", "React App", "React SPA with TypeScript",
            "frontend", List.of("react", "typescript"), "2.0.0",
            Map.of("cssFramework", "tailwind"));

    private static final FeaturePack AUTH_PACK = new FeaturePack(
            "auth-oauth2", "OAuth2 Authentication", "Adds OAuth2 login flow",
            List.of("java-microservice"), List.of("spring-security-oauth2"),
            Map.of("provider", "google"));

    @BeforeEach
    void setUp() {
        scaffoldEngine = mock(ScaffoldEngine.class);
        templateRegistry = mock(TemplateRegistry.class);
        service = new ScaffoldService(scaffoldEngine, templateRegistry);

        when(templateRegistry.getAllTemplates())
                .thenReturn(List.of(JAVA_TEMPLATE, REACT_TEMPLATE));
        when(templateRegistry.getTemplate("java-microservice"))
                .thenReturn(Optional.of(JAVA_TEMPLATE));
        when(templateRegistry.getTemplate("react-app"))
                .thenReturn(Optional.of(REACT_TEMPLATE));
        when(templateRegistry.getTemplate("nonexistent"))
                .thenReturn(Optional.empty());
        when(templateRegistry.getAllFeaturePacks())
                .thenReturn(List.of(AUTH_PACK));
        when(templateRegistry.getFeaturePack("auth-oauth2"))
                .thenReturn(Optional.of(AUTH_PACK));
    }

    // =========================================================================
    // listTemplates
    // =========================================================================

    @Nested
    class ListTemplates {

        @Test
        void shouldListAllTemplates() {
            List<TemplateInfo> result = runPromise(() -> service.listTemplates());

            assertThat(result).hasSize(2);
            assertThat(result).extracting(TemplateInfo::id)
                    .containsExactlyInAnyOrder("java-microservice", "react-app");
        }

        @Test
        void shouldFilterByCategory() {
            List<TemplateInfo> result = runPromise(() -> service.listTemplates("backend"));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo("java-microservice");
        }

        @Test
        void shouldReturnEmptyForUnknownCategory() {
            List<TemplateInfo> result = runPromise(() -> service.listTemplates("mobile"));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // getTemplate
    // =========================================================================

    @Nested
    class GetTemplate {

        @Test
        void shouldReturnTemplateById() {
            Optional<TemplateInfo> result = runPromise(() -> service.getTemplate("java-microservice"));

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("Java Microservice");
            assertThat(result.get().category()).isEqualTo("backend");
            assertThat(result.get().tags()).contains("java", "spring");
        }

        @Test
        void shouldReturnEmptyForMissingTemplate() {
            Optional<TemplateInfo> result = runPromise(() -> service.getTemplate("nonexistent"));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // getTemplateConfigSchema
    // =========================================================================

    @Nested
    class GetTemplateConfigSchema {

        @Test
        void shouldReturnConfigSchema() {
            Map<String, Object> result = runPromise(() -> service.getTemplateConfigSchema("java-microservice"));

            assertThat(result).containsEntry("javaVersion", "17");
            assertThat(result).containsEntry("buildTool", "gradle");
        }

        @Test
        void shouldReturnEmptyMapForMissingTemplate() {
            Map<String, Object> result = runPromise(() -> service.getTemplateConfigSchema("nonexistent"));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // scaffoldProject — async job lifecycle
    // =========================================================================

    @Nested
    class ScaffoldProject {

        @Test
        void shouldReturnStartedResult() {
            ScaffoldRequest request = new ScaffoldRequest(
                    "java-microservice", "my-service", "/tmp/out", Map.of(), true);

            ScaffoldResult result = runPromise(() -> service.scaffoldProject(request));

            assertThat(result.jobId()).isNotNull().isNotBlank();
            assertThat(result.status()).isEqualTo("STARTED");
        }

        @Test
        void shouldTrackJobStatus() throws InterruptedException {
            when(scaffoldEngine.generate(any(GenerationContext.class)))
                    .thenReturn(new GenerationResult("/tmp/out", List.of("pom.xml", "App.java"), true));

            ScaffoldRequest request = new ScaffoldRequest(
                    "java-microservice", "my-service", "/tmp/out", Map.of(), true);
            ScaffoldResult started = runPromise(() -> service.scaffoldProject(request));

            // Initially the job should be tracked
            Optional<JobStatus> status = runPromise(() -> service.getJobStatus(started.jobId()));
            assertThat(status).isPresent();
        }
    }

    // =========================================================================
    // getJobStatus
    // =========================================================================

    @Nested
    class GetJobStatus {

        @Test
        void shouldReturnEmptyForUnknownJob() {
            Optional<JobStatus> result = runPromise(() -> service.getJobStatus("nonexistent-job"));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // downloadProject
    // =========================================================================

    @Nested
    class DownloadProject {

        @Test
        void shouldReturnEmptyForIncompleteJob() {
            // Start a job but don't let it complete
            ScaffoldRequest request = new ScaffoldRequest(
                    "java-microservice", "my-service", "/tmp/out", Map.of(), true);
            ScaffoldResult started = runPromise(() -> service.scaffoldProject(request));

            // Immediately try to download — job is RUNNING, not COMPLETED
            Optional<byte[]> result = runPromise(() -> service.downloadProject(started.jobId()));

            // Should be empty since job hasn't completed yet
            // (it might be empty or present depending on timing, but for nonexistent it's empty)
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyForNonexistentJob() {
            Optional<byte[]> result = runPromise(() -> service.downloadProject("no-such-job"));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // cancelJob
    // =========================================================================

    @Nested
    class CancelJob {

        @Test
        void shouldCancelRunningJob() {
            ScaffoldRequest request = new ScaffoldRequest(
                    "java-microservice", "my-service", "/tmp/out", Map.of(), true);
            // Make engine.generate block so the job stays RUNNING
            when(scaffoldEngine.generate(any(GenerationContext.class)))
                    .thenAnswer(inv -> {
                        Thread.sleep(5000);
                        return new GenerationResult("/tmp/out", List.of(), true);
                    });
            ScaffoldResult started = runPromise(() -> service.scaffoldProject(request));

            // Small delay to let async job start
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}

            Boolean cancelled = runPromise(() -> service.cancelJob(started.jobId()));

            assertThat(cancelled).isTrue();

            Optional<JobStatus> status = runPromise(() -> service.getJobStatus(started.jobId()));
            assertThat(status).isPresent();
            assertThat(status.get().status()).isEqualTo("CANCELLED");
        }

        @Test
        void shouldReturnFalseForNonexistentJob() {
            Boolean cancelled = runPromise(() -> service.cancelJob("unknown-job"));

            assertThat(cancelled).isFalse();
        }
    }

    // =========================================================================
    // listFeaturePacks
    // =========================================================================

    @Nested
    class ListFeaturePacks {

        @Test
        void shouldListAllFeaturePacks() {
            List<FeaturePackInfo> result = runPromise(() -> service.listFeaturePacks());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo("auth-oauth2");
            assertThat(result.get(0).name()).isEqualTo("OAuth2 Authentication");
            assertThat(result.get(0).compatibleTemplates()).contains("java-microservice");
        }
    }

    // =========================================================================
    // addFeaturePack
    // =========================================================================

    @Nested
    class AddFeaturePack {

        @Test
        void shouldStartFeaturePackJob() {
            when(scaffoldEngine.applyFeaturePack(any(GenerationContext.class)))
                    .thenReturn(Promise.of(true));

            FeaturePackRequest request = new FeaturePackRequest(
                    "project-1", "auth-oauth2", Map.of());
            ScaffoldResult result = runPromise(() -> service.addFeaturePack("project-1", request));

            assertThat(result.jobId()).isNotNull();
            assertThat(result.status()).isEqualTo("STARTED");
        }
    }

    // =========================================================================
    // getConflicts / resolveConflicts
    // =========================================================================

    @Nested
    class ConflictManagement {

        @Test
        void shouldReturnEmptyConflictReport() {
            ConflictReport report = runPromise(() -> service.getConflicts("job-1"));

            assertThat(report).isNotNull();
            assertThat(report.jobId()).isEqualTo("job-1");
            assertThat(report.conflicts()).isEmpty();
        }

        @Test
        void shouldResolveConflicts() {
            when(scaffoldEngine.resolveConflicts(anyString(), anyMap()))
                    .thenReturn(Promise.of(true));

            ConflictResolution resolution = new ConflictResolution(
                    Map.of("file.txt", "KEEP_OURS"));
            Boolean result = runPromise(() -> service.resolveConflicts("job-1", resolution));

            assertThat(result).isTrue();
            verify(scaffoldEngine).resolveConflicts("job-1", Map.of("file.txt", "KEEP_OURS"));
        }
    }
}
