package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G10-001: PHR Intelligence Artifact Importer Tests
 *
 * Tests for importing PHR route contract and use case baseline files.
 *
 * @doc.type class
 * @doc.purpose Test PHR IA importer functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("PHR Intelligence Artifact Importer Tests")
class PhrIntelligenceArtifactImporterTest extends EventloopTestBase {

    private PhrIntelligenceArtifactImporter importer;
    private Path tempContractFile;
    private Path tempBaselineFile;

    @BeforeEach
    void setUp() throws Exception {
        importer = new PhrIntelligenceArtifactImporter();
        
        // Create temporary contract file
        tempContractFile = Files.createTempFile("phr-route-contract", ".json");
        String contractJson = """
            {
              "productId": "phr",
              "version": "1.0.0",
              "generatedAt": "2026-05-29T00:00:00.000Z",
              "routes": [
                {
                  "id": "dashboard",
                  "path": "/dashboard",
                  "stability": "stable",
                  "visibility": "public",
                  "component": "DashboardScreen",
                  "roles": ["patient", "caregiver"],
                  "requiredFeatures": []
                },
                {
                  "id": "records",
                  "path": "/records",
                  "stability": "stable",
                  "visibility": "public",
                  "component": "RecordsScreen",
                  "roles": ["patient"],
                  "requiredFeatures": []
                }
              ]
            }
            """;
        Files.writeString(tempContractFile, contractJson);
        
        // Create temporary baseline file
        tempBaselineFile = Files.createTempFile("phr-usecase-baseline", ".json");
        String baselineJson = """
            {
              "productId": "phr",
              "version": "1.0.0",
              "useCases": [
                {
                  "id": "uc-001",
                  "name": "View Dashboard",
                  "description": "Patient views their health dashboard",
                  "flow": "patient_navigates_to_dashboard",
                  "actors": ["patient"],
                  "preconditions": ["patient_authenticated"],
                  "postconditions": ["dashboard_displayed"]
                },
                {
                  "id": "uc-002",
                  "name": "View Records",
                  "description": "Patient views their health records",
                  "flow": "patient_navigates_to_records",
                  "actors": ["patient"],
                  "preconditions": ["patient_authenticated"],
                  "postconditions": ["records_displayed"]
                }
              ]
            }
            """;
        Files.writeString(tempBaselineFile, baselineJson);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Files.exists(tempContractFile)) {
            Files.delete(tempContractFile);
        }
        if (Files.exists(tempBaselineFile)) {
            Files.delete(tempBaselineFile);
        }
    }

    @Test
    @DisplayName("GIVEN valid route contract WHEN imported THEN parses correctly")
    void validRouteContract_whenImported_parsesCorrectly() {
        PhrIntelligenceArtifactImporter.PhrRouteContract contract = runPromise(() ->
            importer.importRouteContract(tempContractFile)
        );

        assertThat(contract.getProductId()).isEqualTo("phr");
        assertThat(contract.getVersion()).isEqualTo("1.0.0");
        assertThat(contract.getRoutes()).hasSize(2);
        
        PhrIntelligenceArtifactImporter.PhrRoute dashboardRoute = contract.getRoutes().get(0);
        assertThat(dashboardRoute.getId()).isEqualTo("dashboard");
        assertThat(dashboardRoute.getPath()).isEqualTo("/dashboard");
        assertThat(dashboardRoute.getStability()).isEqualTo("stable");
        assertThat(dashboardRoute.getVisibility()).isEqualTo("public");
        assertThat(dashboardRoute.getComponent()).isEqualTo("DashboardScreen");
        assertThat(dashboardRoute.getRoles()).containsExactly("patient", "caregiver");
    }

    @Test
    @DisplayName("GIVEN valid use case baseline WHEN imported THEN parses correctly")
    void validUseCaseBaseline_whenImported_parsesCorrectly() {
        PhrIntelligenceArtifactImporter.PhrUseCaseBaseline baseline = runPromise(() ->
            importer.importUseCaseBaseline(tempBaselineFile)
        );

        assertThat(baseline.getProductId()).isEqualTo("phr");
        assertThat(baseline.getVersion()).isEqualTo("1.0.0");
        assertThat(baseline.getUseCases()).hasSize(2);
        
        PhrIntelligenceArtifactImporter.PhrUseCase firstUseCase = baseline.getUseCases().get(0);
        assertThat(firstUseCase.getId()).isEqualTo("uc-001");
        assertThat(firstUseCase.getName()).isEqualTo("View Dashboard");
        assertThat(firstUseCase.getDescription()).isEqualTo("Patient views their health dashboard");
        assertThat(firstUseCase.getActors()).containsExactly("patient");
        assertThat(firstUseCase.getPreconditions()).containsExactly("patient_authenticated");
    }

    @Test
    @DisplayName("GIVEN malformed contract WHEN imported THEN throws exception")
    void malformedContract_whenImported_throwsException() {
        Path malformedFile = tempContractFile.resolveSibling("malformed.json");
        Files.writeString(malformedFile, "{ invalid json }");

        var exception = runPromise(() -> importer.importRouteContract(malformedFile));
        
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).contains("Failed to import PHR route contract");
    }
}
