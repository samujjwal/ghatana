package com.ghatana.services.featurestore.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DisplayName("FeatureIngestConfig")
class FeatureIngestConfigTest {

    @Test
    @DisplayName("isPostgresMode is true only when postgres mode has a non-blank URL")
    void isPostgresModeRequiresNonBlankUrl() { // GH-90000
        FeatureIngestConfig postgres = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest")
                .build(); // GH-90000
        FeatureIngestConfig missingUrl = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres")
                .dbUrl("   ")
                .build(); // GH-90000
        FeatureIngestConfig inmemory = new FeatureIngestConfig.Builder() // GH-90000
                .mode("inmemory")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest")
                .build(); // GH-90000

        assertThat(postgres.isPostgresMode()).isTrue(); // GH-90000
        assertThat(missingUrl.isPostgresMode()).isFalse(); // GH-90000
        assertThat(inmemory.isPostgresMode()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("validate rejects postgres mode without DB URL")
    void validateRejectsMissingDbUrlInPostgresMode() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres")
                .dbUrl(" ")
                .build(); // GH-90000

        assertThatIllegalStateException() // GH-90000
                .isThrownBy(config::validate) // GH-90000
                .withMessageContaining("FEATURE_INGEST_DB_URL");
    }

            @Test
            @DisplayName("validate rejects postgres mode without DB password")
            void validateRejectsMissingDbPasswordInPostgresMode() { // GH-90000
            FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest")
                .dbPassword(" ")
                .build(); // GH-90000

            assertThatIllegalStateException() // GH-90000
                .isThrownBy(config::validate) // GH-90000
                .withMessageContaining("FEATURE_INGEST_DB_PASSWORD");
            }

    @Test
    @DisplayName("validate rejects non-positive batch size")
    void validateRejectsNonPositiveBatchSize() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .batchSize(0) // GH-90000
                .build(); // GH-90000

        assertThatIllegalStateException() // GH-90000
                .isThrownBy(config::validate) // GH-90000
                .withMessageContaining("FEATURE_INGEST_BATCH_SIZE");
    }

    @Test
    @DisplayName("validate rejects negative retry delay")
    void validateRejectsNegativeRetryDelay() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .retryDelayMs(-1) // GH-90000
                .build(); // GH-90000

        assertThatIllegalStateException() // GH-90000
                .isThrownBy(config::validate) // GH-90000
                .withMessageContaining("FEATURE_INGEST_RETRY_DELAY_MS");
    }

    @Test
    @DisplayName("validate accepts a well-formed postgres configuration")
    void validateAcceptsWellFormedPostgresConfig() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest")
                .dbUser("featureingest")
                .dbPassword("secret")
                .batchSize(100) // GH-90000
                .retryDelayMs(5_000) // GH-90000
                .build(); // GH-90000

        config.validate(); // GH-90000
        assertThat(config.isPostgresMode()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("event log data source uses conservative pool sizing")
    void eventLogDataSourceUsesConservativePoolSizing() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest")
                .dbUser("featureingest")
                .dbPassword("secret")
                .build(); // GH-90000

        HikariDataSource dataSource = config.buildEventLogStoreDataSource(); // GH-90000
        try {
            assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/feature_ingest");
            assertThat(dataSource.getUsername()).isEqualTo("featureingest");
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5); // GH-90000
            assertThat(dataSource.getMinimumIdle()).isEqualTo(1); // GH-90000
            assertThat(dataSource.getConnectionTimeout()).isEqualTo(10_000L); // GH-90000
            assertThat(dataSource.getPoolName()).isEqualTo("feature-ingest-eventlog-pool");
        } finally {
            dataSource.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("feature store data source uses write-oriented pool sizing")
    void featureStoreDataSourceUsesWriteOrientedPoolSizing() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .mode("postgres")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest")
                .dbUser("featureingest")
                .dbPassword("secret")
                .build(); // GH-90000

        HikariDataSource dataSource = config.buildFeatureStoreDataSource(); // GH-90000
        try {
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10); // GH-90000
            assertThat(dataSource.getMinimumIdle()).isEqualTo(2); // GH-90000
            assertThat(dataSource.getConnectionTimeout()).isEqualTo(5_000L); // GH-90000
            assertThat(dataSource.getIdleTimeout()).isEqualTo(600_000L); // GH-90000
            assertThat(dataSource.getMaxLifetime()).isEqualTo(1_800_000L); // GH-90000
            assertThat(dataSource.getPoolName()).isEqualTo("feature-ingest-pool");
        } finally {
            dataSource.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("data source builders reject non-postgres mode")
    void dataSourceBuildersRejectNonPostgresMode() { // GH-90000
        FeatureIngestConfig config = new FeatureIngestConfig.Builder() // GH-90000
                .mode("inmemory")
                .build(); // GH-90000

        assertThatIllegalStateException().isThrownBy(config::buildEventLogStoreDataSource); // GH-90000
        assertThatIllegalStateException().isThrownBy(config::buildFeatureStoreDataSource); // GH-90000
    }
}
