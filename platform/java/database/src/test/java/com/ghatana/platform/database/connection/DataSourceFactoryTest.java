/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.database.connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DataSourceFactory Tests")
class DataSourceFactoryTest {

    @AfterEach
    void tearDown() { // GH-90000
        DataSourceFactory.closeAll(); // GH-90000
    }

    @Test
    void shouldCreateDataSource() { // GH-90000
        DataSourceConfig config = DataSourceConfig.builder() // GH-90000
            .jdbcUrl("jdbc:h2:mem:test-factory;DB_CLOSE_DELAY=-1")
            .username("sa")
            .password("")
            .driverClassName("org.h2.Driver")
            .poolName("factory-test")
            .build(); // GH-90000

        DataSource ds = DataSourceFactory.create(config); // GH-90000

        assertThat(ds).isNotNull(); // GH-90000
        assertThat(DataSourceFactory.poolCount()).isEqualTo(1); // GH-90000
    }

    @Test
    void shouldTrackMultiplePools() { // GH-90000
        DataSourceConfig config1 = DataSourceConfig.builder() // GH-90000
            .jdbcUrl("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1")
            .username("sa").password("")
            .driverClassName("org.h2.Driver")
            .poolName("pool-1")
            .build(); // GH-90000

        DataSourceConfig config2 = DataSourceConfig.builder() // GH-90000
            .jdbcUrl("jdbc:h2:mem:test2;DB_CLOSE_DELAY=-1")
            .username("sa").password("")
            .driverClassName("org.h2.Driver")
            .poolName("pool-2")
            .build(); // GH-90000

        DataSourceFactory.create(config1); // GH-90000
        DataSourceFactory.create(config2); // GH-90000

        assertThat(DataSourceFactory.poolCount()).isEqualTo(2); // GH-90000
    }

    @Test
    void shouldCloseAllPools() { // GH-90000
        DataSourceConfig config = DataSourceConfig.builder() // GH-90000
            .jdbcUrl("jdbc:h2:mem:closetest;DB_CLOSE_DELAY=-1")
            .username("sa").password("")
            .driverClassName("org.h2.Driver")
            .poolName("close-test")
            .build(); // GH-90000

        DataSourceFactory.create(config); // GH-90000
        assertThat(DataSourceFactory.poolCount()).isEqualTo(1); // GH-90000

        DataSourceFactory.closeAll(); // GH-90000
        assertThat(DataSourceFactory.poolCount()).isZero(); // GH-90000
    }

    @Test
    void shouldRejectNullConfig() { // GH-90000
        assertThatThrownBy(() -> DataSourceFactory.create(null)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
