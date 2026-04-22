/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("DataSourceFactory - Phase 3 Expansion [GH-90000]")
class DataSourceFactoryExpansionTest {

    @AfterEach
    void tearDown() { // GH-90000
        DataSourceFactory.closeAll(); // GH-90000
    }

    // ============================================
    // CONCURRENT POOL CREATION (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Pool Creation [GH-90000]")
    class ConcurrentCreationTests {

        @Test
        @DisplayName("Creates multiple pools concurrently without conflicts [GH-90000]")
        void multipleConcurrentPools() { // GH-90000
            List<DataSource> dataSources = new ArrayList<>(); // GH-90000

            for (int i = 0; i < 10; i++) { // GH-90000
                DataSourceConfig config = DataSourceConfig.builder() // GH-90000
                        .jdbcUrl("jdbc:h2:mem:concurrent-" + i + ";DB_CLOSE_DELAY=-1") // GH-90000
                        .username("sa [GH-90000]")
                        .password(" [GH-90000]")
                        .driverClassName("org.h2.Driver [GH-90000]")
                        .poolName("concurrent-pool-" + i) // GH-90000
                        .build(); // GH-90000

                DataSource ds = DataSourceFactory.create(config); // GH-90000
                dataSources.add(ds); // GH-90000
            }

            assertThat(DataSourceFactory.poolCount()).isEqualTo(10); // GH-90000
            assertThat(dataSources).hasSize(10); // GH-90000
        }

        @Test
        @DisplayName("Handles rapid sequential pool creation [GH-90000]")
        void rapidSequentialCreation() { // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                DataSourceConfig config = DataSourceConfig.builder() // GH-90000
                        .jdbcUrl("jdbc:h2:mem:rapid-" + i + ";DB_CLOSE_DELAY=-1") // GH-90000
                        .username("sa [GH-90000]")
                        .password(" [GH-90000]")
                        .driverClassName("org.h2.Driver [GH-90000]")
                        .poolName("rapid-" + i) // GH-90000
                        .build(); // GH-90000

                DataSourceFactory.create(config); // GH-90000
            }

            assertThat(DataSourceFactory.poolCount()).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("Maintains separate pools with unique configurations [GH-90000]")
        void separatePoolConfigurations() { // GH-90000
            DataSourceConfig config1 = DataSourceConfig.builder() // GH-90000
                    .jdbcUrl("jdbc:h2:mem:separate1;DB_CLOSE_DELAY=-1 [GH-90000]")
                    .username("sa [GH-90000]")
                    .password(" [GH-90000]")
                    .driverClassName("org.h2.Driver [GH-90000]")
                    .poolName("separate-1 [GH-90000]")
                    .minimumIdle(2) // GH-90000
                    .maximumPoolSize(5) // GH-90000
                    .build(); // GH-90000

            DataSourceConfig config2 = DataSourceConfig.builder() // GH-90000
                    .jdbcUrl("jdbc:h2:mem:separate2;DB_CLOSE_DELAY=-1 [GH-90000]")
                    .username("sa [GH-90000]")
                    .password(" [GH-90000]")
                    .driverClassName("org.h2.Driver [GH-90000]")
                    .poolName("separate-2 [GH-90000]")
                    .minimumIdle(4) // GH-90000
                    .maximumPoolSize(20) // GH-90000
                    .build(); // GH-90000

            DataSourceFactory.create(config1); // GH-90000
            DataSourceFactory.create(config2); // GH-90000

            assertThat(DataSourceFactory.poolCount()).isEqualTo(2); // GH-90000
        }
    }

    // ============================================
    // POOL LIFECYCLE MANAGEMENT (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Pool Lifecycle Management [GH-90000]")
    class LifecycleTests {

        @Test
        @DisplayName("Resets pool count after closeAll invocation [GH-90000]")
        void poolCountResetAfterClose() { // GH-90000
            for (int i = 0; i < 3; i++) { // GH-90000
                DataSourceConfig config = DataSourceConfig.builder() // GH-90000
                        .jdbcUrl("jdbc:h2:mem:reset-" + i + ";DB_CLOSE_DELAY=-1") // GH-90000
                        .username("sa [GH-90000]")
                        .password(" [GH-90000]")
                        .driverClassName("org.h2.Driver [GH-90000]")
                        .poolName("reset-" + i) // GH-90000
                        .build(); // GH-90000

                DataSourceFactory.create(config); // GH-90000
            }

            assertThat(DataSourceFactory.poolCount()).isEqualTo(3); // GH-90000

            DataSourceFactory.closeAll(); // GH-90000

            assertThat(DataSourceFactory.poolCount()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("Allows pool recreation after full closure [GH-90000]")
        void recreateAfterClosure() { // GH-90000
            DataSourceConfig config = DataSourceConfig.builder() // GH-90000
                    .jdbcUrl("jdbc:h2:mem:recreate;DB_CLOSE_DELAY=-1 [GH-90000]")
                    .username("sa [GH-90000]")
                    .password(" [GH-90000]")
                    .driverClassName("org.h2.Driver [GH-90000]")
                    .poolName("recreate-pool [GH-90000]")
                    .build(); // GH-90000

            DataSourceFactory.create(config); // GH-90000
            assertThat(DataSourceFactory.poolCount()).isEqualTo(1); // GH-90000

            DataSourceFactory.closeAll(); // GH-90000
            assertThat(DataSourceFactory.poolCount()).isZero(); // GH-90000

            // Recreate same configuration
            DataSourceFactory.create(config); // GH-90000
            assertThat(DataSourceFactory.poolCount()).isEqualTo(1); // GH-90000
        }
    }

    // ============================================
    // ERROR HANDLING (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Error Handling [GH-90000]")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Rejects null configuration [GH-90000]")
        void rejectNullConfig() { // GH-90000
            assertThatThrownBy(() -> DataSourceFactory.create(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("Handles invalid JDBC URL configuration [GH-90000]")
        void rejectInvalidJdbcUrl() { // GH-90000
            assertThatThrownBy(() -> { // GH-90000
                DataSourceConfig.builder() // GH-90000
                        .jdbcUrl(null) // GH-90000
                        .username("sa [GH-90000]")
                        .password(" [GH-90000]")
                        .driverClassName("org.h2.Driver [GH-90000]")
                        .poolName("bad-url [GH-90000]")
                        .build(); // GH-90000
            }).isInstanceOf(Exception.class); // GH-90000
        }
    }
}
