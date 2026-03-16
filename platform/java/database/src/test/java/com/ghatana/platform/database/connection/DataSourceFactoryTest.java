/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void tearDown() {
        DataSourceFactory.closeAll();
    }

    @Test
    void shouldCreateDataSource() {
        DataSourceConfig config = DataSourceConfig.builder()
            .jdbcUrl("jdbc:h2:mem:test-factory;DB_CLOSE_DELAY=-1")
            .username("sa")
            .password("")
            .driverClassName("org.h2.Driver")
            .poolName("factory-test")
            .build();

        DataSource ds = DataSourceFactory.create(config);

        assertThat(ds).isNotNull();
        assertThat(DataSourceFactory.poolCount()).isEqualTo(1);
    }

    @Test
    void shouldTrackMultiplePools() {
        DataSourceConfig config1 = DataSourceConfig.builder()
            .jdbcUrl("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1")
            .username("sa").password("")
            .driverClassName("org.h2.Driver")
            .poolName("pool-1")
            .build();

        DataSourceConfig config2 = DataSourceConfig.builder()
            .jdbcUrl("jdbc:h2:mem:test2;DB_CLOSE_DELAY=-1")
            .username("sa").password("")
            .driverClassName("org.h2.Driver")
            .poolName("pool-2")
            .build();

        DataSourceFactory.create(config1);
        DataSourceFactory.create(config2);

        assertThat(DataSourceFactory.poolCount()).isEqualTo(2);
    }

    @Test
    void shouldCloseAllPools() {
        DataSourceConfig config = DataSourceConfig.builder()
            .jdbcUrl("jdbc:h2:mem:closetest;DB_CLOSE_DELAY=-1")
            .username("sa").password("")
            .driverClassName("org.h2.Driver")
            .poolName("close-test")
            .build();

        DataSourceFactory.create(config);
        assertThat(DataSourceFactory.poolCount()).isEqualTo(1);

        DataSourceFactory.closeAll();
        assertThat(DataSourceFactory.poolCount()).isZero();
    }

    @Test
    void shouldRejectNullConfig() {
        assertThatThrownBy(() -> DataSourceFactory.create(null))
            .isInstanceOf(NullPointerException.class);
    }
}
