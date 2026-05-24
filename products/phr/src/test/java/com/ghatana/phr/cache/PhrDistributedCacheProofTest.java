package com.ghatana.phr.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.DistributedCachePort;
import com.ghatana.platform.cache.RedisDistributedCacheAdapter;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production cache proof for PHR consent cache.
 *
 * @doc.type class
 * @doc.purpose Verify distributed consent cache behavior against Redis
 * @doc.layer product
 * @doc.pattern Integration test
 */
@DisplayName("PHR Distributed Cache Proof")
@Tag("integration")
@Tag("infrastructure-backed")
final class PhrDistributedCacheProofTest extends EventloopTestBase {

    private static final String REDIS_IMAGE = "redis:7-alpine";

    private GenericContainer<?> redisContainer;
    private JedisPool jedisPool;
    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (jedisPool != null) {
            jedisPool.close();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("distributed cache shares values across adapters")
    void distributedCacheSharesValuesAcrossAdapters() {
        bootRedis();

        DistributedCachePort<String, String> nodeA = newAdapter();
        DistributedCachePort<String, String> nodeB = newAdapter();

        String key = "patient-1:recipient-1";
        String value = "granted";

        runPromise(() -> nodeA.put(key, value, Duration.ofMinutes(5)));
        Optional<String> fetched = runPromise(() -> nodeB.get(key));

        assertThat(fetched).isPresent();
        assertThat(fetched.orElseThrow()).isEqualTo("granted");
    }

    @Test
    @DisplayName("distributed invalidation is observed across adapters")
    void distributedInvalidationIsObservedAcrossAdapters() {
        bootRedis();

        DistributedCachePort<String, String> nodeA = newAdapter();
        DistributedCachePort<String, String> nodeB = newAdapter();

        String key = "patient-2:recipient-2";
        String value = "granted";

        runPromise(() -> nodeA.put(key, value, Duration.ofMinutes(5)));
        runPromise(() -> nodeB.invalidate(key));

        Optional<String> after = runPromise(() -> nodeA.get(key));
        assertThat(after).isEmpty();
    }

    private void bootRedis() {
        redisContainer = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
        redisContainer.start();

        jedisPool = new JedisPool(redisContainer.getHost(), redisContainer.getMappedPort(6379));
        executor = Executors.newFixedThreadPool(2);
    }

    private DistributedCachePort<String, String> newAdapter() {
        return new RedisDistributedCacheAdapter<>(
            jedisPool,
            new ObjectMapper(),
            String.class,
            executor,
            "phr.consent",
            Duration.ofHours(1)
        );
    }
}
