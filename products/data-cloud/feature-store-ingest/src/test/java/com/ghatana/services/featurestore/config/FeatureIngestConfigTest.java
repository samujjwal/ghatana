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
    void isPostgresModeRequiresNonBlankUrl() {
        FeatureIngestConfig postgres = new FeatureIngestConfig.Builder()
                .mode("postgres")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest")
                .build();
        FeatureIngestConfig missingUrl = new FeatureIngestConfig.Builder()
                .mode("postgres")
                .dbUrl("   ")
                .build();
        FeatureIngestConfig inmemory = new FeatureIngestConfig.Builder()
                .mode("inmemory")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest")
                .build();

        assertThat(postgres.isPostgresMode()).isTrue();
        assertThat(missingUrl.isPostgresMode()).isFalse();
        assertThat(inmemory.isPostgresMode()).isFalse();
    }

    @Test
    @DisplayName("validate rejects postgres mode without DB URL")
    void validateRejectsMissingDbUrlInPostgresMode() {
        FeatureIngestConfig config = new FeatureIngestConfig.Builder()
                .mode("postgres")
                .dbUrl(" ")
                .build();

        assertThatIllegalStateException()
                .isThrownBy(config::validate)
                .withMessageContaining("FEATURE_INGEST_DB_URL");
    }

            @Test
            @DisplayName("validate rejects postgres mode without DB password")
            void validateRejectsMissingDbPasswordInPostgresMode() {
            FeatureIngestConfig config = new FeatureIngestConfig.Builder()
                .mode("postgres")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest")
                .dbPassword(" ")
                .build();

            assertThatIllegalStateException()
                .isThrownBy(config::validate)
                .withMessageContaining("FEATURE_INGEST_DB_PASSWORD");
            }

    @Test
    @DisplayName("validate rejects non-positive batch size")
    void validateRejectsNonPositiveBatchSize() {
        FeatureIngestConfig config = new FeatureIngestConfig.Builder()
                .batchSize(0)
                .build();

        assertThatIllegalStateException()
                .isThrownBy(config::validate)
                .withMessageContaining("FEATURE_INGEST_BATCH_SIZE");
    }

    @Test
    @DisplayName("validate rejects negative retry delay")
    void validateRejectsNegativeRetryDelay() {
        FeatureIngestConfig config = new FeatureIngestConfig.Builder()
                .retryDelayMs(-1)
                .build();

        assertThatIllegalStateException()
                .isThrownBy(config::validate)
                .withMessageContaining("FEATURE_INGEST_RETRY_DELAY_MS");
    }

    @Test
    @DisplayName("validate accepts a well-formed postgres configuration")
    void validateAcceptsWellFormedPostgresConfig() {
        FeatureIngestConfig config = new FeatureIngestConfig.Builder()
                .mode("postgres")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest")
                .dbUser("featureingest")
                .dbPassword("secret")
                .batchSize(100)
                .retryDelayMs(5_000)
                .build();

        config.validate();
        assertThat(config.isPostgresMode()).isTrue();
    }

    @Test
    @DisplayName("event log data source uses conservative pool sizing")
    void eventLogDataSourceUsesConservativePoolSizing() {
        FeatureIngestConfig config = new FeatureIngestConfig.Builder()
                .mode("postgres")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest")
                .dbUser("featureingest")
                .dbPassword("secret")
                .build();

        HikariDataSource dataSource = config.buildEventLogStoreDataSource();
        try {
            assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/feature_ingest");
            assertThat(dataSource.getUsername()).isEqualTo("featureingest");
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
            assertThat(dataSource.getMinimumIdle()).isEqualTo(1);
            assertThat(dataSource.getConnectionTimeout()).isEqualTo(10_000L);
            assertThat(dataSource.getPoolName()).isEqualTo("feature-ingest-eventlog-pool");
        } finally {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("feature store data source uses write-oriented pool sizing")
    void featureStoreDataSourceUsesWriteOrientedPoolSizing() {
        FeatureIngestConfig config = new FeatureIngestConfig.Builder()
                .mode("postgres")
                .dbUrl("jdbc:postgresql://localhost:5432/feature_ingest")
                .dbUser("featureingest")
                .dbPassword("secret")
                .build();

        HikariDataSource dataSource = config.buildFeatureStoreDataSource();
        try {
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
            assertThat(dataSource.getMinimumIdle()).isEqualTo(2);
            assertThat(dataSource.getConnectionTimeout()).isEqualTo(5_000L);
            assertThat(dataSource.getIdleTimeout()).isEqualTo(600_000L);
            assertThat(dataSource.getMaxLifetime()).isEqualTo(1_800_000L);
            assertThat(dataSource.getPoolName()).isEqualTo("feature-ingest-pool");
        } finally {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("data source builders reject non-postgres mode")
    void dataSourceBuildersRejectNonPostgresMode() {
        FeatureIngestConfig config = new FeatureIngestConfig.Builder()
                .mode("inmemory")
                .build();

        assertThatIllegalStateException().isThrownBy(config::buildEventLogStoreDataSource);
        assertThatIllegalStateException().isThrownBy(config::buildFeatureStoreDataSource);
    }
}
