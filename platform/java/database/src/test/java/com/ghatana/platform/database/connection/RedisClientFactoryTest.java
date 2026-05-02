/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.platform.database.connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RedisClientFactory Tests")
class RedisClientFactoryTest {

    @AfterEach
    void tearDown() { 
        RedisClientFactory.closeAll(); 
    }

    @Test
    void shouldRejectNullConfig() { 
        assertThatThrownBy(() -> RedisClientFactory.create(null)) 
            .isInstanceOf(NullPointerException.class); 
    }

    @Test
    void shouldTrackPoolCount() { 
        assertThat(RedisClientFactory.poolCount()).isZero(); 
    }

    @Test
    void shouldCloseAll() { 
        RedisClientFactory.closeAll(); 
        assertThat(RedisClientFactory.poolCount()).isZero(); 
    }

    @Test
    void shouldCreateRedisConfigWithDefaults() { 
        RedisConfig config = RedisConfig.localhost(); 

        assertThat(config.host()).isEqualTo("localhost");
        assertThat(config.port()).isEqualTo(6379); 
        assertThat(config.password()).isNull(); 
        assertThat(config.maxTotal()).isEqualTo(8); 
    }

    @Test
    void shouldCreateRedisConfigWithBuilder() { 
        RedisConfig config = RedisConfig.builder() 
            .host("redis.internal")
            .port(6380) 
            .password("secret")
            .maxTotal(16) 
            .maxIdle(8) 
            .build(); 

        assertThat(config.host()).isEqualTo("redis.internal");
        assertThat(config.port()).isEqualTo(6380); 
        assertThat(config.password()).isEqualTo("secret");
        assertThat(config.maxTotal()).isEqualTo(16); 
        assertThat(config.maxIdle()).isEqualTo(8); 
    }

    @Test
    void shouldRejectInvalidPort() { 
        assertThatThrownBy(() -> RedisConfig.builder().port(0).build()) 
            .isInstanceOf(IllegalArgumentException.class); 
    }

    @Test
    void shouldRejectNegativeMaxTotal() { 
        assertThatThrownBy(() -> RedisConfig.builder().maxTotal(0).build()) 
            .isInstanceOf(IllegalArgumentException.class); 
    }
}
