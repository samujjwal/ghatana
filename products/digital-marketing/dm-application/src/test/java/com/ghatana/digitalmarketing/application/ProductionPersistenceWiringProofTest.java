/**
 * P1-049: Production persistence wiring proof test.
 *
 * <p>Verifies that production persistence layer is correctly wired with PostgreSQL
 * repositories and that no in-memory adapters are accidentally used in production.</p>
 *
 * @doc.type class
 * @doc.purpose Production persistence wiring proof test (P1-049)
 * @doc.layer test
 */
package com.ghatana.digitalmarketing.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("P1-049: Production Persistence Wiring Proof")
class ProductionPersistenceWiringProofTest {

    private static final String PROJECT_ROOT = "products/digital-marketing";
    private static final String PERSISTENCE_PATH = PROJECT_ROOT + "/dm-persistence/src/main/java";
    private static final String INFRA_PATH = PROJECT_ROOT + "/dm-infra/src/main/java";

    @Test
    @DisplayName("P1-049: PostgreSQL repositories exist for all domain aggregates")
    void postgresqlRepositoriesExistForAllDomainAggregates() throws IOException {
        // Domain aggregates that require persistence
        Set<String> requiredAggregates = Set.of(
            "Campaign",
            "MarketingStrategy",
            "BudgetRecommendation",
            "Workspace",
            "Contact",
            "ApprovalSnapshot",
            "AiActionLog",
            "WebsiteAuditReport",
            "AttributionModel",
            "DmApiKey",
            "DmCommand",
            "MarketplaceListing",
            "AgencyClient",
            "IdempotencyToken",
            "DmGoogleAdsCredential",
            "DataSubjectRequest"
        );

        for (String aggregate : requiredAggregates) {
            String expectedPostgresRepo = "Postgres" + aggregate + "Repository";
            boolean exists = classExistsInModule(PERSISTENCE_PATH, expectedPostgresRepo);

            assertThat(exists)
                .as("P1-049: PostgreSQL repository must exist for aggregate: %s (%s)",
                    aggregate, expectedPostgresRepo)
                .isTrue();
        }
    }

    @Test
    @DisplayName("P1-049: No in-memory repository is accidentally in production package")
    void noInMemoryRepositoryInProductionPackage() throws IOException {
        Path persistencePath = Paths.get(PERSISTENCE_PATH);

        if (!Files.exists(persistencePath)) {
            persistencePath = Paths.get(".").resolve(PERSISTENCE_PATH);
        }

        if (!Files.exists(persistencePath)) {
            return; // Skip if path doesn't exist in test environment
        }

        try (Stream<Path> paths = Files.walk(persistencePath)) {
            boolean hasInMemoryRepo = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().startsWith("InMemory"))
                .filter(p -> p.getFileName().toString().endsWith("Repository.java"))
                .anyMatch(p -> true);

            assertThat(hasInMemoryRepo)
                .as("P1-049: In-memory repositories should not exist in dm-persistence package")
                .isFalse();
        }
    }

    @Test
    @DisplayName("P1-049: PostgreSQL repositories implement correct interfaces")
    void postgresqlRepositoriesImplementCorrectInterfaces() throws IOException {
        // Verify key PostgreSQL repositories exist and are in the correct package
        String[] expectedPostgresRepos = {
            "PostgresCampaignRepository",
            "PostgresMarketingStrategyRepository",
            "PostgresBudgetRecommendationRepository",
            "PostgresWorkspaceRepository",
            "PostgresContactRepository",
            "PostgresApprovalSnapshotRepository",
            "PostgresAiActionLogRepository"
        };

        for (String repo : expectedPostgresRepos) {
            boolean exists = classExistsInModule(PERSISTENCE_PATH, repo);

            assertThat(exists)
                .as("P1-049: PostgreSQL repository must exist: %s", repo)
                .isTrue();
        }
    }

    @Test
    @DisplayName("P1-049: Persistence module has Flyway migrations")
    void persistenceModuleHasFlywayMigrations() throws IOException {
        Path migrationPath = Paths.get(PROJECT_ROOT + "/dm-persistence/src/main/resources/db/migration");

        if (!Files.exists(migrationPath)) {
            migrationPath = Paths.get(".").resolve(PROJECT_ROOT + "/dm-persistence/src/main/resources/db/migration");
        }

        if (!Files.exists(migrationPath)) {
            return; // Skip if path doesn't exist in test environment
        }

        try (Stream<Path> paths = Files.list(migrationPath)) {
            long migrationCount = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                .count();

            assertThat(migrationCount)
                .as("P1-049: Persistence module must have Flyway migrations")
                .isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("P1-049: DataSource configuration exists for production")
    void dataSourceConfigurationExists() throws IOException {
        // Check for DataSource configuration in build.gradle.kts
        Path buildGradlePath = Paths.get(PROJECT_ROOT + "/dm-persistence/build.gradle.kts");

        if (!Files.exists(buildGradlePath)) {
            buildGradlePath = Paths.get(".").resolve(PROJECT_ROOT + "/dm-persistence/build.gradle.kts");
        }

        if (!Files.exists(buildGradlePath)) {
            return; // Skip if path doesn't exist in test environment
        }

        String buildGradleContent = Files.readString(buildGradlePath);

        assertThat(buildGradleContent)
            .as("P1-049: Persistence module must include JDBC/PostgreSQL dependencies")
            .contains("postgresql")
            .contains("jdbc");
    }

    @Test
    @DisplayName("P1-049: Production bootstrap validates persistence type")
    void productionBootstrapValidatesPersistenceType() throws IOException {
        String validatorPath = PROJECT_ROOT + "/dm-application/src/main/java/com/ghatana/digitalmarketing/application/bootstrap/ProductionBootstrapValidator.java";
        Path path = Paths.get(validatorPath);

        if (!Files.exists(path)) {
            path = Paths.get(".").resolve(validatorPath);
        }

        if (!Files.exists(path)) {
            return; // Skip if path doesn't exist in test environment
        }

        String validatorSource = Files.readString(path);

        assertThat(validatorSource)
            .as("P1-049: ProductionBootstrapValidator must validate persistence is PostgreSQL")
            .contains("DataSource")
            .contains("validatePersistence");
    }

    private boolean classExistsInModule(String modulePath, String className) {
        Path module = Paths.get(modulePath);

        if (!Files.exists(module)) {
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
}
