/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.aep.server.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.Jedis;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis integration smoke test for the AEP Phase-1 Testcontainers suite.
 *
 * @doc.type class
 * @doc.purpose Verify AEP integration test wiring can reach a live Redis container
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Tag("integration")
@Tag("infrastructure-backed")
@Testcontainers(disabledWithoutDocker = true) 
@DisplayName("RedisIntegrationTest")
class RedisIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379); 

    @Test
    @DisplayName("can ping and round-trip a key")
    void canPingAndRoundTripKey() { 
        try (Jedis jedis = new Jedis(REDIS.getHost(), REDIS.getMappedPort(6379))) { 
            assertThat(jedis.ping()).isEqualTo("PONG");
            jedis.set("aep:probe", "ok"); 
            assertThat(jedis.get("aep:probe")).isEqualTo("ok");
        }
    }
}
