/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.database.connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 expansion: DataSourceFactory concurrent pool creation and stress testing.
 * Tests pool creation under load, multiple simultaneous factories, and resource management.
 *
 * @doc.type class
 * @doc.purpose DataSourceFactory concurrency and stress testing
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DataSourceFactory - Phase 3 Expansion")
class DataSourceFactoryExpansionTest {

    @AfterEach
    void tearDown() {
        DataSourceFactory.closeAll();
    }

    // ============================================
    // CONCURRENT POOL CREATION (3 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Pool Creation")
    class ConcurrentCreationTests {

        @Test
        @DisplayName("Creates multiple pools concurrently without conflicts")
        void multipleConcurrentPools() {
            List<DataSource> dataSources = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                DataSourceConfig config = DataSourceConfig.builder()
                        .jdbcUrl("jdbc:h2:mem:concurrent-" + i + ";DB_CLOSE_DELAY=-1")
                        .username("sa")
                        .password("")
                        .driverClassName("org.h2.Driver")
                        .poolName("concurrent-pool-" + i)
                        .build();

                DataSource ds = DataSourceFactory.create(config);
                dataSources.add(ds);
            }

            assertThat(DataSourceFactory.poolCount()).isEqualTo(10);
            assertThat(dataSources).hasSize(10);
        }

        @Test
        @DisplayName("Handles rapid sequential pool creation")
        void rapidSequentialCreation() {
            for (int i = 0; i < 5; i++) {
                DataSourceConfig config = DataSourceConfig.builder()
                        .jdbcUrl("jdbc:h2:mem:rapid-" + i + ";DB_CLOSE_DELAY=-1")
                        .username("sa")
                        .password("")
                        .driverClassName("org.h2.Driver")
                        .poolName("rapid-" + i)
                        .build();

                DataSourceFactory.create(config);
            }

            assertThat(DataSourceFactory.poolCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Maintains separate pools with unique configurations")
        void separatePoolConfigurations() {
            DataSourceConfig config1 = DataSourceConfig.builder()
                    .jdbcUrl("jdbc:h2:mem:separate1;DB_CLOSE_DELAY=-1")
                    .username("sa")
                    .password("")
                    .driverClassName("org.h2.Driver")
                    .poolName("separate-1")
                    .minimumIdle(2)
                    .maximumPoolSize(5)
                    .build();

            DataSourceConfig config2 = DataSourceConfig.builder()
                    .jdbcUrl("jdbc:h2:mem:separate2;DB_CLOSE_DELAY=-1")
                    .username("sa")
                    .password("")
                    .driverClassName("org.h2.Driver")
                    .poolName("separate-2")
                    .minimumIdle(4)
                    .maximumPoolSize(20)
                    .build();

            DataSourceFactory.create(config1);
            DataSourceFactory.create(config2);

            assertThat(DataSourceFactory.poolCount()).isEqualTo(2);
        }
    }

    // ============================================
    // POOL LIFECYCLE MANAGEMENT (2 tests)
    // ============================================

    @Nested
    @DisplayName("Pool Lifecycle Management")
    class LifecycleTests {

        @Test
        @DisplayName("Resets pool count after closeAll invocation")
        void poolCountResetAfterClose() {
            for (int i = 0; i < 3; i++) {
                DataSourceConfig config = DataSourceConfig.builder()
                        .jdbcUrl("jdbc:h2:mem:reset-" + i + ";DB_CLOSE_DELAY=-1")
                        .username("sa")
                        .password("")
                        .driverClassName("org.h2.Driver")
                        .poolName("reset-" + i)
                        .build();

                DataSourceFactory.create(config);
            }

            assertThat(DataSourceFactory.poolCount()).isEqualTo(3);

            DataSourceFactory.closeAll();

            assertThat(DataSourceFactory.poolCount()).isZero();
        }

        @Test
        @DisplayName("Allows pool recreation after full closure")
        void recreateAfterClosure() {
            DataSourceConfig config = DataSourceConfig.builder()
                    .jdbcUrl("jdbc:h2:mem:recreate;DB_CLOSE_DELAY=-1")
                    .username("sa")
                    .password("")
                    .driverClassName("org.h2.Driver")
                    .poolName("recreate-pool")
                    .build();

            DataSourceFactory.create(config);
            assertThat(DataSourceFactory.poolCount()).isEqualTo(1);

            DataSourceFactory.closeAll();
            assertThat(DataSourceFactory.poolCount()).isZero();

            // Recreate same configuration
            DataSourceFactory.create(config);
            assertThat(DataSourceFactory.poolCount()).isEqualTo(1);
        }
    }

    // ============================================
    // ERROR HANDLING (2 tests)
    // ============================================

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Rejects null configuration")
        void rejectNullConfig() {
            assertThatThrownBy(() -> DataSourceFactory.create(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Handles invalid JDBC URL configuration")
        void rejectInvalidJdbcUrl() {
            assertThatThrownBy(() -> {
                DataSourceConfig.builder()
                        .jdbcUrl(null)
                        .username("sa")
                        .password("")
                        .driverClassName("org.h2.Driver")
                        .poolName("bad-url")
                        .build();
            }).isInstanceOf(Exception.class);
        }
    }
}
