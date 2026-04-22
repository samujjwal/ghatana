/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.database.connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RedisClientFactory Tests [GH-90000]")
class RedisClientFactoryTest {

    @AfterEach
    void tearDown() { // GH-90000
        RedisClientFactory.closeAll(); // GH-90000
    }

    @Test
    void shouldRejectNullConfig() { // GH-90000
        assertThatThrownBy(() -> RedisClientFactory.create(null)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    void shouldTrackPoolCount() { // GH-90000
        assertThat(RedisClientFactory.poolCount()).isZero(); // GH-90000
    }

    @Test
    void shouldCloseAll() { // GH-90000
        RedisClientFactory.closeAll(); // GH-90000
        assertThat(RedisClientFactory.poolCount()).isZero(); // GH-90000
    }

    @Test
    void shouldCreateRedisConfigWithDefaults() { // GH-90000
        RedisConfig config = RedisConfig.localhost(); // GH-90000

        assertThat(config.host()).isEqualTo("localhost [GH-90000]");
        assertThat(config.port()).isEqualTo(6379); // GH-90000
        assertThat(config.password()).isNull(); // GH-90000
        assertThat(config.maxTotal()).isEqualTo(8); // GH-90000
    }

    @Test
    void shouldCreateRedisConfigWithBuilder() { // GH-90000
        RedisConfig config = RedisConfig.builder() // GH-90000
            .host("redis.internal [GH-90000]")
            .port(6380) // GH-90000
            .password("secret [GH-90000]")
            .maxTotal(16) // GH-90000
            .maxIdle(8) // GH-90000
            .build(); // GH-90000

        assertThat(config.host()).isEqualTo("redis.internal [GH-90000]");
        assertThat(config.port()).isEqualTo(6380); // GH-90000
        assertThat(config.password()).isEqualTo("secret [GH-90000]");
        assertThat(config.maxTotal()).isEqualTo(16); // GH-90000
        assertThat(config.maxIdle()).isEqualTo(8); // GH-90000
    }

    @Test
    void shouldRejectInvalidPort() { // GH-90000
        assertThatThrownBy(() -> RedisConfig.builder().port(0).build()) // GH-90000
            .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    void shouldRejectNegativeMaxTotal() { // GH-90000
        assertThatThrownBy(() -> RedisConfig.builder().maxTotal(0).build()) // GH-90000
            .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
}
