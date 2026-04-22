package com.ghatana.services.featurestore.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DisplayName("FeatureIngestConfig [GH-90000]")
class FeatureIngestConfigTest {

    @Test
    @DisplayName("isPostgresMode is true only when postgres mode has a non-blank URL [GH-90000]")
    void isPostgresModeRequiresNonBlankUrl() { // GH-90000
        FeatureIngestConfig postgres = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres [GH-90000]")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest [GH-90000]")
                .build(); // GH-90000
        FeatureIngestConfig missingUrl = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres [GH-90000]")
                .dbUrl("    [GH-90000]")
                .build(); // GH-90000
        FeatureIngestConfig inmemory = new FeatureIngestConfig.Builder() // GH-90000
                .mode("inmemory [GH-90000]")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest [GH-90000]")
                .build(); // GH-90000

        assertThat(postgres.isPostgresMode()).isTrue(); // GH-90000
        assertThat(missingUrl.isPostgresMode()).isFalse(); // GH-90000
        assertThat(inmemory.isPostgresMode()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("validate rejects postgres mode without DB URL [GH-90000]")
    void validateRejectsMissingDbUrlInPostgresMode() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres [GH-90000]")
                .dbUrl("  [GH-90000]")
                .build(); // GH-90000

        assertThatIllegalStateException() // GH-90000
                .isThrownBy(config::validate) // GH-90000
                .withMessageContaining("FEATURE_INGEST_DB_URL [GH-90000]");
    }

            @Test
            @DisplayName("validate rejects postgres mode without DB password [GH-90000]")
            void validateRejectsMissingDbPasswordInPostgresMode() { // GH-90000
            FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres [GH-90000]")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest [GH-90000]")
                .dbPassword("  [GH-90000]")
                .build(); // GH-90000

            assertThatIllegalStateException() // GH-90000
                .isThrownBy(config::validate) // GH-90000
                .withMessageContaining("FEATURE_INGEST_DB_PASSWORD [GH-90000]");
            }

    @Test
    @DisplayName("validate rejects non-positive batch size [GH-90000]")
    void validateRejectsNonPositiveBatchSize() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .batchSize(0) // GH-90000
                .build(); // GH-90000

        assertThatIllegalStateException() // GH-90000
                .isThrownBy(config::validate) // GH-90000
                .withMessageContaining("FEATURE_INGEST_BATCH_SIZE [GH-90000]");
    }

    @Test
    @DisplayName("validate rejects negative retry delay [GH-90000]")
    void validateRejectsNegativeRetryDelay() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .retryDelayMs(-1) // GH-90000
                .build(); // GH-90000

        assertThatIllegalStateException() // GH-90000
                .isThrownBy(config::validate) // GH-90000
                .withMessageContaining("FEATURE_INGEST_RETRY_DELAY_MS [GH-90000]");
    }

    @Test
    @DisplayName("validate accepts a well-formed postgres configuration [GH-90000]")
    void validateAcceptsWellFormedPostgresConfig() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres [GH-90000]")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest [GH-90000]")
                .dbUser("featureingest [GH-90000]")
                .dbPassword("secret [GH-90000]")
                .batchSize(100) // GH-90000
                .retryDelayMs(5_000) // GH-90000
                .build(); // GH-90000

        config.validate(); // GH-90000
        assertThat(config.isPostgresMode()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("event log data source uses conservative pool sizing [GH-90000]")
    void eventLogDataSourceUsesConservativePoolSizing() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres [GH-90000]")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest [GH-90000]")
                .dbUser("featureingest [GH-90000]")
                .dbPassword("secret [GH-90000]")
                .build(); // GH-90000

        HikariDataSource dataSource = config.buildEventLogStoreDataSource(); // GH-90000
        try {
            assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/feature_ingest [GH-90000]");
            assertThat(dataSource.getUsername()).isEqualTo("featureingest [GH-90000]");
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5); // GH-90000
            assertThat(dataSource.getMinimumIdle()).isEqualTo(1); // GH-90000
            assertThat(dataSource.getConnectionTimeout()).isEqualTo(10_000L); // GH-90000
            assertThat(dataSource.getPoolName()).isEqualTo("feature-ingest-eventlog-pool [GH-90000]");
        } finally {
            dataSource.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("feature store data source uses write-oriented pool sizing [GH-90000]")
    void featureStoreDataSourceUsesWriteOrientedPoolSizing() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres [GH-90000]")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest [GH-90000]")
                .dbUser("featureingest [GH-90000]")
                .dbPassword("secret [GH-90000]")
                .build(); // GH-90000

        HikariDataSource dataSource = config.buildFeatureStoreDataSource(); // GH-90000
        try {
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10); // GH-90000
            assertThat(dataSource.getMinimumIdle()).isEqualTo(2); // GH-90000
            assertThat(dataSource.getConnectionTimeout()).isEqualTo(5_000L); // GH-90000
            assertThat(dataSource.getIdleTimeout()).isEqualTo(600_000L); // GH-90000
            assertThat(dataSource.getMaxLifetime()).isEqualTo(1_800_000L); // GH-90000
            assertThat(dataSource.getPoolName()).isEqualTo("feature-ingest-pool [GH-90000]");
        } finally {
            dataSource.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("data source builders reject non-postgres mode [GH-90000]")
    void dataSourceBuildersRejectNonPostgresMode() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .mode("inmemory [GH-90000]")
                .build(); // GH-90000

        assertThatIllegalStateException().isThrownBy(config::buildEventLogStoreDataSource); // GH-90000
        assertThatIllegalStateException().isThrownBy(config::buildFeatureStoreDataSource); // GH-90000
    }
}
