/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.catalog;

import com.ghatana.agent.catalog.CatalogAgentEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conformance tests for the central catalog service.
 *
 * <p>Validates multi-root loading, deduplication, ownership enforcement,
 * and support for both canonical and legacy catalog layouts.
 */
@DisplayName("AepCentralCatalogService Conformance")
class AepCentralCatalogServiceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // Create canonical platform catalog (nested subdirectory matches real repo layout)
        Path platformCatalog = tempDir.resolve("platform/agent-catalog");
        Files.createDirectories(platformCatalog.resolve("core-agents/monitoring"));
        Files.writeString(platformCatalog.resolve("agent-catalog.yaml"), """
                catalog:
                  id: platform
                  name: Platform Core Agents
                  priority: 100
                  agents:
                    - "core-agents/**/*-agent.yaml"
                """);
        Files.writeString(platformCatalog.resolve("core-agents/monitoring/monitor-agent.yaml"), """
                agent:
                  id: platform-monitor
                  name: Platform Monitor
                  version: "1.0.0"
                  capabilities:
                    - monitoring
                    - health-check
                """);

        // Create canonical AEP catalog (nested subdirectory)
        Path aepCatalog = tempDir.resolve("products/aep/agent-catalog");
        Files.createDirectories(aepCatalog.resolve("operators/ingestion"));
        Files.writeString(aepCatalog.resolve("agent-catalog.yaml"), """
                catalog:
                  id: aep
                  name: AEP Operators
                  extends:
                    - platform
                  priority: 200
                  agents:
                    - "operators/**/*-agent.yaml"
                """);
        Files.writeString(aepCatalog.resolve("operators/ingestion/ingestion-agent.yaml"), """
                agent:
                  id: aep-ingestion
                  name: Ingestion Operator
                  version: "2.0.0"
                  capabilities:
                    - ingestion
                    - stream-processing
                """);

        // Create legacy YAPPC layout (nested subdirectory)
        Path yappcCatalog = tempDir.resolve("products/yappc/config/agents");
        Files.createDirectories(yappcCatalog.resolve("definitions/tutoring"));
        Files.writeString(yappcCatalog.resolve("agent-catalog.yaml"), """
                catalog:
                  id: yappc
                  name: YAPPC Agents
                  extends:
                    - platform
                  priority: 200
                  agents:
                    - "definitions/**/*.yaml"
                """);
        Files.writeString(yappcCatalog.resolve("definitions/tutoring/tutor-agent.yaml"), """
                agent:
                  id: yappc-tutor
                  name: YAPPC Tutor
                  version: "1.0.0"
                  capabilities:
                    - tutoring
                    - education
                """);
    }

    @Test
    @DisplayName("loads catalogs from multiple product roots")
    void loadsCatalogsFromMultipleRoots() {
        AepCentralCatalogService service = AepCentralCatalogService.fromRepositoryRoot(tempDir);
        CatalogValidationReport report = service.loadAndValidate();

        assertThat(report.catalogCount()).isEqualTo(3);
        assertThat(report.totalAgents()).isEqualTo(3);
        assertThat(report.isValid()).isTrue();
    }

    @Test
    @DisplayName("merged index resolves agents by ID across catalogs")
    void mergedIndexResolvesAgentsByIdAcrossCatalogs() {
        AepCentralCatalogService service = AepCentralCatalogService.fromRepositoryRoot(tempDir);
        service.loadAndValidate();

        assertThat(service.getRegistry().findById("platform-monitor")).isPresent();
        assertThat(service.getRegistry().findById("aep-ingestion")).isPresent();
        assertThat(service.getRegistry().findById("yappc-tutor")).isPresent();
        assertThat(service.getRegistry().findById("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("detects duplicate agent IDs across catalogs")
    void detectsDuplicateAgentIds() throws IOException {
        // Add a duplicate agent to AEP catalog with same ID as platform
        Path aepOperators = tempDir.resolve("products/aep/agent-catalog/operators/ingestion");
        Files.writeString(aepOperators.resolve("duplicate-agent.yaml"), """
                agent:
                  id: platform-monitor
                  name: Duplicate Monitor
                  version: "2.0.0"
                  capabilities:
                    - monitoring
                """);

        AepCentralCatalogService service = AepCentralCatalogService.fromRepositoryRoot(tempDir);
        CatalogValidationReport report = service.loadAndValidate();

        assertThat(report.warnings()).anyMatch(w ->
                w.code().equals("DUPLICATE_AGENT")
                        && w.message().contains("platform-monitor"));
    }

    @Test
    @DisplayName("platform catalog has higher priority than product catalogs")
    void platformCatalogHasHigherPriority() throws IOException {
        // Add duplicate with platform ID in AEP catalog
        Path aepOperators = tempDir.resolve("products/aep/agent-catalog/operators/ingestion");
        Files.writeString(aepOperators.resolve("override-agent.yaml"), """
                agent:
                  id: platform-monitor
                  name: AEP Override Monitor
                  version: "2.0.0"
                  capabilities:
                    - monitoring
                """);

        AepCentralCatalogService service = AepCentralCatalogService.fromRepositoryRoot(tempDir);
        service.loadAndValidate();

        // Platform (priority=100) should win over AEP (priority=200)
        CatalogAgentEntry entry = service.getRegistry().findById("platform-monitor").orElseThrow();
        assertThat(entry.getCatalogId()).isEqualTo("platform");
    }

    @Test
    @DisplayName("finds agents by capability across all catalogs")
    void findsAgentsByCapabilityAcrossAllCatalogs() {
        AepCentralCatalogService service = AepCentralCatalogService.fromRepositoryRoot(tempDir);
        service.loadAndValidate();

        List<CatalogAgentEntry> monitoring = service.getRegistry().findByCapability("monitoring");
        assertThat(monitoring).hasSize(1);
        assertThat(monitoring.get(0).getId()).isEqualTo("platform-monitor");

        List<CatalogAgentEntry> education = service.getRegistry().findByCapability("education");
        assertThat(education).hasSize(1);
    }

    @Test
    @DisplayName("skips missing catalog roots gracefully")
    void skipsMissingCatalogRootsGracefully() {
        List<Path> roots = List.of(
                tempDir.resolve("platform/agent-catalog"),
                tempDir.resolve("nonexistent/product/catalog")
        );
        AepCentralCatalogService service = new AepCentralCatalogService(roots);
        CatalogValidationReport report = service.loadAndValidate();

        assertThat(report.catalogCount()).isEqualTo(1);
        assertThat(report.isValid()).isTrue();
    }

    @Test
    @DisplayName("validates required fields and reports warnings")
    void validatesRequiredFieldsAndReportsWarnings() throws IOException {
        // Add agent with no capabilities
        Path aepOperators = tempDir.resolve("products/aep/agent-catalog/operators/ingestion");
        Files.writeString(aepOperators.resolve("bare-agent.yaml"), """
                agent:
                  id: bare-agent
                  name: Bare Agent
                  version: "1.0.0"
                """);

        AepCentralCatalogService service = AepCentralCatalogService.fromRepositoryRoot(tempDir);
        CatalogValidationReport report = service.loadAndValidate();

        assertThat(report.warnings()).anyMatch(w ->
                w.code().equals("NO_CAPABILITIES")
                        && w.message().contains("bare-agent"));
    }

    @Test
    @DisplayName("supports explicit product root configuration")
    void supportsExplicitProductRootConfiguration() {
        List<Path> roots = List.of(
                tempDir.resolve("products/aep/agent-catalog"),
                tempDir.resolve("products/yappc/config/agents")
        );
        AepCentralCatalogService service = new AepCentralCatalogService(roots);
        CatalogValidationReport report = service.loadAndValidate();

        assertThat(report.catalogCount()).isEqualTo(2);
        assertThat(service.getRegistry().findById("aep-ingestion")).isPresent();
        assertThat(service.getRegistry().findById("yappc-tutor")).isPresent();
        // Platform not loaded since root not included
        assertThat(service.getRegistry().findById("platform-monitor")).isEmpty();
    }
}
