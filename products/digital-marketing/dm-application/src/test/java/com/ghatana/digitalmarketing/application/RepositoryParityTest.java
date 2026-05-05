package com.ghatana.digitalmarketing.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * P1-005: Repository adapter parity test.
 *
 * <p>Verifies that every repository port (interface) in the application layer
 * has both a production PostgreSQL implementation and a test/local in-memory
 * implementation. This ensures no port is left without a production adapter.</p>
 *
 * <p>Test strategy:</p>
 * <ul>
 *   <li>Find all repository interfaces in application layer</li>
 *   <li>Verify each has a Postgres* implementation in dm-persistence</li>
 *   <li>Verify each has an InMemory* implementation in dm-infra</li>
 *   <li>Fail if any production adapter is missing</li>
 * </ul>
 */
@DisplayName("P1-005: Repository Adapter Parity Tests")
class RepositoryParityTest {

    private static final String PROJECT_ROOT = "products/digital-marketing";
    private static final String APPLICATION_PATH = PROJECT_ROOT + "/dm-application/src/main/java";
    private static final String PERSISTENCE_PATH = PROJECT_ROOT + "/dm-persistence/src/main/java";
    private static final String INFRA_PATH = PROJECT_ROOT + "/dm-infra/src/main/java";

    @Test
    @DisplayName("P1-005: All repository ports have PostgreSQL implementation")
    void allRepositoryPortsHavePostgresImplementation() throws IOException {
        // Given: Find all repository interfaces in application layer
        Set<String> repositoryInterfaces = findRepositoryInterfaces();

        // Then: Each should have a Postgres implementation
        for (String repoInterface : repositoryInterfaces) {
            String expectedPostgresClass = "Postgres" + repoInterface;

            boolean hasPostgresImpl = classExistsInModule(PERSISTENCE_PATH, expectedPostgresClass);

            assertThat(hasPostgresImpl)
                .as("P1-005: Repository %s must have PostgreSQL implementation %s in dm-persistence",
                    repoInterface, expectedPostgresClass)
                .isTrue();
        }
    }

    @Test
    @DisplayName("P1-005: All repository ports have in-memory implementation for tests")
    void allRepositoryPortsHaveInMemoryImplementation() throws IOException {
        // Given: Find all repository interfaces in application layer
        Set<String> repositoryInterfaces = findRepositoryInterfaces();

        // Then: Each should have an InMemory implementation (for local/test use)
        for (String repoInterface : repositoryInterfaces) {
            String expectedInMemoryClass = "InMemory" + repoInterface;

            boolean hasInMemoryImpl = classExistsInModule(INFRA_PATH, expectedInMemoryClass);

            assertThat(hasInMemoryImpl)
                .as("P1-005: Repository %s should have InMemory implementation %s in dm-infra for testing",
                    repoInterface, expectedInMemoryClass)
                .isTrue();
        }
    }

    @Test
    @DisplayName("P1-005: Campaign repository has production adapter")
    void campaignRepositoryHasProductionAdapter() {
        // This is a specific check for the critical CampaignRepository
        boolean hasPostgresCampaignRepo = classExistsInModule(
            PERSISTENCE_PATH,
            "PostgresCampaignRepository"
        );

        assertThat(hasPostgresCampaignRepo)
            .as("P1-005: CampaignRepository must have PostgresCampaignRepository production adapter")
            .isTrue();

        boolean hasInMemoryCampaignRepo = classExistsInModule(
            INFRA_PATH,
            "InMemoryCampaignRepository"
        );

        assertThat(hasInMemoryCampaignRepo)
            .as("P1-005: CampaignRepository should have InMemoryCampaignRepository for local/test use")
            .isTrue();
    }

    @Test
    @DisplayName("P1-005: No production port has only in-memory implementation")
    void noProductionPortHasOnlyInMemoryImplementation() throws IOException {
        // Find all repository interfaces
        Set<String> repositoryInterfaces = findRepositoryInterfaces();

        // Verify each has a production (PostgreSQL) implementation
        List<String> missingProductionImpls = repositoryInterfaces.stream()
            .filter(repo -> !classExistsInModule(PERSISTENCE_PATH, "Postgres" + repo))
            .filter(repo -> !isExternalRepository(repo)) // Skip external/Kernel repositories
            .toList();

        if (!missingProductionImpls.isEmpty()) {
            fail("P1-005: Following repository ports lack production PostgreSQL implementations: %s. " +
                 "All production ports must have durable persistence adapters.",
                String.join(", ", missingProductionImpls));
        }
    }

    @Test
    @DisplayName("P1-005: Production bootstrap validator checks repository type")
    void productionBootstrapValidatorChecksRepositoryType() throws IOException {
        // Verify the ProductionBootstrapValidator has repository validation logic
        String validatorPath = APPLICATION_PATH +
            "/com/ghatana/digitalmarketing/application/bootstrap/ProductionBootstrapValidator.java";
        Path path = Paths.get(validatorPath);

        if (!Files.exists(path)) {
            fail("P1-005: ProductionBootstrapValidator not found at expected path: " + validatorPath);
        }

        String validatorSource = Files.readString(path);

        assertThat(validatorSource)
            .as("P1-005: ProductionBootstrapValidator must validate repository is not in-memory")
            .contains("InMemory")
            .contains("PERSISTENCE");
    }

    // Helper methods

    private Set<String> findRepositoryInterfaces() throws IOException {
        Path appPath = Paths.get(APPLICATION_PATH);

        if (!Files.exists(appPath)) {
            // Running from different working directory - try alternative
            return Set.of("CampaignRepository"); // Fallback for test environments
        }

        try (Stream<Path> paths = Files.walk(appPath)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Repository.java"))
                .map(this::extractClassName)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
        }
    }

    private String extractClassName(Path path) {
        String fileName = path.getFileName().toString();
        // Remove .java extension
        return fileName.substring(0, fileName.length() - 5);
    }

    private boolean classExistsInModule(String modulePath, String className) {
        Path module = Paths.get(modulePath);

        if (!Files.exists(module)) {
            // Try alternative paths
            module = Paths.get(".").resolve(modulePath);
        }

        if (!Files.exists(module)) {
            return false;
        }

        String fileName = className + ".java";

        try (Stream<Path> paths = Files.walk(module)) {
            return paths
                .filter(Files::isRegularFile)
                .anyMatch(p -> p.getFileName().toString().equals(fileName));
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isExternalRepository(String repoName) {
        // These repositories may be provided by Kernel/platform and don't need local implementations
        Set<String> externalRepos = Set.of(
            "AuditRepository",        // Provided by platform audit plugin
            "ConsentRepository",    // Provided by platform consent plugin
            "IdentityRepository",   // Provided by platform identity service
            "FeatureFlagRepository", // Provided by platform feature flag service
            "TenantRepository"      // Provided by platform tenant service
        );

        return externalRepos.contains(repoName);
    }
}
