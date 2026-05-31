package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G10-001: Product Intelligence Artifact Importer Tests
 *
 * Tests for importing product route contract and use case baseline files.
 *
 * @doc.type class
 * @doc.purpose Test product IA importer functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("Product Intelligence Artifact Importer Tests")
class ProductIntelligenceArtifactImporterTest extends EventloopTestBase {

    private ProductIntelligenceArtifactImporter importer;
    private Path tempContractFile;
    private Path tempBaselineFile;

    @BeforeEach
    void setUp() throws Exception {
        importer = new ProductIntelligenceArtifactImporter();
        
        tempContractFile = Files.createTempFile("sample-product-route-contract", ".json");
        String contractJson = """
            {
              "productId": "sample-product",
              "version": "1.0.0",
              "generatedAt": "2026-05-29T00:00:00.000Z",
              "routes": [
                {
                  "id": "dashboard",
                  "path": "/dashboard",
                  "stability": "stable",
                  "visibility": "public",
                  "component": "DashboardScreen",
                  "roles": ["user", "reviewer"],
                  "requiredFeatures": []
                },
                {
                  "id": "records",
                  "path": "/records",
                  "stability": "stable",
                  "visibility": "public",
                  "component": "RecordsScreen",
                  "roles": ["user"],
                  "requiredFeatures": []
                }
              ]
            }
            """;
        Files.writeString(tempContractFile, contractJson);
        
        tempBaselineFile = Files.createTempFile("sample-product-usecase-baseline", ".json");
        String baselineJson = """
            {
              "productId": "sample-product",
              "version": "1.0.0",
              "useCases": [
                {
                  "id": "uc-001",
                  "name": "View Dashboard",
                  "description": "User views their workspace dashboard",
                  "flow": "user_navigates_to_dashboard",
                  "actors": ["user"],
                  "preconditions": ["user_authenticated"],
                  "postconditions": ["dashboard_displayed"]
                },
                {
                  "id": "uc-002",
                  "name": "View Records",
                  "description": "User views their workspace records",
                  "flow": "user_navigates_to_records",
                  "actors": ["user"],
                  "preconditions": ["user_authenticated"],
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
        ProductIntelligenceArtifactImporter.ProductRouteContract contract = runPromise(() ->
            importer.importRouteContract(tempContractFile)
        );

        assertThat(contract.getProductId()).isEqualTo("sample-product");
        assertThat(contract.getVersion()).isEqualTo("1.0.0");
        assertThat(contract.getRoutes()).hasSize(2);
        
        ProductIntelligenceArtifactImporter.ProductRoute dashboardRoute = contract.getRoutes().get(0);
        assertThat(dashboardRoute.getProductId()).isEqualTo("sample-product");
        assertThat(dashboardRoute.getId()).isEqualTo("dashboard");
        assertThat(dashboardRoute.getPath()).isEqualTo("/dashboard");
        assertThat(dashboardRoute.getStability()).isEqualTo("stable");
        assertThat(dashboardRoute.getVisibility()).isEqualTo("public");
        assertThat(dashboardRoute.getComponent()).isEqualTo("DashboardScreen");
        assertThat(dashboardRoute.getRoles()).containsExactly("user", "reviewer");
    }

    @Test
    @DisplayName("GIVEN valid use case baseline WHEN imported THEN parses correctly")
    void validUseCaseBaseline_whenImported_parsesCorrectly() {
        ProductIntelligenceArtifactImporter.ProductUseCaseBaseline baseline = runPromise(() ->
            importer.importUseCaseBaseline(tempBaselineFile)
        );

        assertThat(baseline.getProductId()).isEqualTo("sample-product");
        assertThat(baseline.getVersion()).isEqualTo("1.0.0");
        assertThat(baseline.getUseCases()).hasSize(2);
        
        ProductIntelligenceArtifactImporter.ProductUseCase firstUseCase = baseline.getUseCases().get(0);
        assertThat(firstUseCase.getId()).isEqualTo("uc-001");
        assertThat(firstUseCase.getName()).isEqualTo("View Dashboard");
        assertThat(firstUseCase.getDescription()).isEqualTo("User views their workspace dashboard");
        assertThat(firstUseCase.getActors()).containsExactly("user");
        assertThat(firstUseCase.getPreconditions()).containsExactly("user_authenticated");
    }

    @Test
    @DisplayName("GIVEN malformed contract WHEN imported THEN throws exception")
    void malformedContract_whenImported_throwsException() throws Exception {
        Path malformedFile = tempContractFile.resolveSibling("malformed.json");
        Files.writeString(malformedFile, "{ invalid json }");

        assertThatThrownBy(() -> runPromise(() -> importer.importRouteContract(malformedFile)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to import product route contract");
    }
}
