package com.ghatana.phr.cache;

import com.ghatana.platform.cache.DistributedCachePort;
import com.ghatana.platform.cache.RedisDistributedCacheAdapter;
import com.ghatana.phr.consent.ConsentGrant;
import io.activej.eventloop.Eventloop;
import io.activej.eventloop.EventloopThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Production cache proof test for PHR.
 * 
 * <p>This test proves that DistributedCachePort is a real distributed cache (not in-memory)
 * and that consent invalidation propagates across simulated nodes. This is required for
 * PHR-P0-004: Production cache proof.
 *
 * <p>The test uses a real Redis instance via TestContainers to simulate multi-node scenarios.
 * It validates:
 * <ul>
 *   <li>DistributedCachePort is backed by Redis (not in-memory)</li>
 *   <li>Consent invalidation propagates across multiple cache clients</li>
 *   <li>Cache operations are consistent across nodes</li>
 *   <li>TTL expiration works correctly</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Production cache proof test for PHR consent invalidation
 * @doc.layer product
 * @doc.pattern Integration test
 */
@Testcontainers
@DisplayName("PHR Distributed Cache Production Proof Tests")
public final class PhrDistributedCacheProofTest {

    @Container
    private static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(
        DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    private DistributedCachePort<String, ConsentGrant> cachePort;
    private Eventloop eventloop;
    private EventloopThread eventloopThread;

    @BeforeEach
    void setUp() throws Exception {
        String redisUrl = String.format(
            "redis://%s:%d",
            REDIS_CONTAINER.getHost(),
            REDIS_CONTAINER.getMappedPort(6379)
        );

        eventloop = Eventloop.create().withCurrentThread();
        eventloopThread = EventloopThread.create(eventloop).start();

        cachePort = new RedisDistributedCacheAdapter<>(redisUrl, eventloop);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (cachePort != null) {
            cachePort.invalidateAll().get();
        }
        if (eventloopThread != null) {
            eventloopThread.shutdown();
        }
    }

    @Test
    @DisplayName("DistributedCachePort should be backed by Redis (not in-memory)")
    void testCacheIsDistributedNotInMemory() throws Exception {
        // This test proves the cache is distributed by checking Redis connectivity
        // and verifying data persists across cache instances
        
        String tenantId = "tenant-1";
        String patientId = "patient-1";
        String key = String.format("consent:%s:%s", tenantId, patientId);
        
        ConsentGrant grant = new ConsentGrant(
            tenantId,
            patientId,
            "data-access",
            true,
            System.currentTimeMillis()
        );

        // Put value in cache
        cachePort.put(key, grant, Duration.ofMinutes(5)).get();

        // Create a new cache instance (simulating a different node)
        DistributedCachePort<String, ConsentGrant> secondCachePort = 
            new RedisDistributedCacheAdapter<>(
                String.format("redis://%s:%d", 
                    REDIS_CONTAINER.getHost(), 
                    REDIS_CONTAINER.getMappedPort(6379)),
                eventloop
            );

        // Verify the second cache instance can read the value
        Optional<ConsentGrant> retrieved = secondCachePort.get(key).get();
        
        assertThat(retrieved)
            .as("Second cache instance should read value from Redis")
            .isPresent();
        
        assertThat(retrieved.get().patientId())
            .as("Retrieved consent should match original")
            .isEqualTo(patientId);
        
        // Cleanup
        secondCachePort.invalidateAll().get();
    }

    @Test
    @DisplayName("Consent invalidation should propagate across simulated nodes")
    void testConsentInvalidationPropagatesAcrossNodes() throws Exception {
        // This test simulates multiple nodes and verifies invalidation propagates
        
        String tenantId = "tenant-1";
        String patientId = "patient-1";
        String key = String.format("consent:%s:%s", tenantId, patientId);
        
        ConsentGrant grant = new ConsentGrant(
            tenantId,
            patientId,
            "data-access",
            true,
            System.currentTimeMillis()
        );

        // Create multiple cache instances (simulating multiple nodes)
        DistributedCachePort<String, ConsentGrant> node1 = cachePort;
        DistributedCachePort<String, ConsentGrant> node2 = 
            new RedisDistributedCacheAdapter<>(
                String.format("redis://%s:%d", 
                    REDIS_CONTAINER.getHost(), 
                    REDIS_CONTAINER.getMappedPort(6379)),
                eventloop
            );
        DistributedCachePort<String, ConsentGrant> node3 = 
            new RedisDistributedCacheAdapter<>(
                String.format("redis://%s:%d", 
                    REDIS_CONTAINER.getHost(), 
                    REDIS_CONTAINER.getMappedPort(6379)),
                eventloop
            );

        // Node 1 writes consent
        node1.put(key, grant, Duration.ofMinutes(5)).get();

        // All nodes should be able to read the consent
        assertThat(node1.get(key).get()).isPresent();
        assertThat(node2.get(key).get()).isPresent();
        assertThat(node3.get(key).get()).isPresent();

        // Node 2 invalidates consent (simulating consent revocation)
        node2.invalidateAll().get();

        // All nodes should see the invalidation
        Optional<ConsentGrant> node1After = node1.get(key).get();
        Optional<ConsentGrant> node2After = node2.get(key).get();
        Optional<ConsentGrant> node3After = node3.get(key).get();

        assertThat(node1After)
            .as("Node 1 should see invalidation from Node 2")
            .isEmpty();
        assertThat(node2After)
            .as("Node 2 should see its own invalidation")
            .isEmpty();
        assertThat(node3After)
            .as("Node 3 should see invalidation from Node 2")
            .isEmpty();
        
        // Cleanup
        node3.invalidateAll().get();
    }

    @Test
    @DisplayName("Cache operations should be consistent across concurrent nodes")
    void testConcurrentCacheConsistency() throws Exception {
        // This test validates cache consistency under concurrent access
        
        String tenantId = "tenant-1";
        int patientCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(3);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicBoolean hasErrors = new AtomicBoolean(false);

        // Create 3 cache instances (simulating 3 nodes)
        DistributedCachePort<String, ConsentGrant>[] nodes = new DistributedCachePort[3];
        for (int i = 0; i < 3; i++) {
            nodes[i] = new RedisDistributedCacheAdapter<>(
                String.format("redis://%s:%d", 
                    REDIS_CONTAINER.getHost(), 
                    REDIS_CONTAINER.getMappedPort(6379)),
                eventloop
            );
        }

        // Simulate concurrent writes from different nodes
        for (int i = 0; i < 3; i++) {
            final int nodeIndex = i;
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < patientCount; j++) {
                        String patientId = "patient-" + j;
                        String key = String.format("consent:%s:%s", tenantId, patientId);
                        
                        ConsentGrant grant = new ConsentGrant(
                            tenantId,
                            patientId,
                            "data-access",
                            true,
                            System.currentTimeMillis()
                        );
                        
                        nodes[nodeIndex].put(key, grant, Duration.ofMinutes(5)).get();
                    }
                    
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    hasErrors.set(true);
                    e.printStackTrace();
                } finally {
                    completeLatch.countDown();
                }
            });
            thread.start();
        }

        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for completion
        assertThat(completeLatch.await(30, TimeUnit.SECONDS))
            .as("All nodes should complete within timeout")
            .isTrue();
        
        assertThat(hasErrors.get())
            .as("No errors should occur during concurrent operations")
            .isFalse();
        
        assertThat(successCount.get())
            .as("All nodes should succeed")
            .isEqualTo(3);

        // Verify all nodes can read all values
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < patientCount; j++) {
                String patientId = "patient-" + j;
                String key = String.format("consent:%s:%s", tenantId, patientId);
                
                Optional<ConsentGrant> retrieved = nodes[i].get(key).get();
                assertThat(retrieved)
                    .as("Node " + i + " should read consent for patient " + j)
                    .isPresent();
            }
        }
        
        // Cleanup
        for (int i = 0; i < 3; i++) {
            nodes[i].invalidateAll().get();
        }
    }

    @Test
    @DisplayName("TTL expiration should work correctly")
    void testTtlExpiration() throws Exception {
        String tenantId = "tenant-1";
        String patientId = "patient-1";
        String key = String.format("consent:%s:%s", tenantId, patientId);
        
        ConsentGrant grant = new ConsentGrant(
            tenantId,
            patientId,
            "data-access",
            true,
            System.currentTimeMillis()
        );

        // Put value with short TTL
        cachePort.put(key, grant, Duration.ofMillis(100)).get();

        // Value should be present immediately
        assertThat(cachePort.get(key).get()).isPresent();

        // Wait for expiration
        Thread.sleep(150);

        // Value should be expired
        Optional<ConsentGrant> expired = cachePort.get(key).get();
        assertThat(expired)
            .as("Value should be expired after TTL")
            .isEmpty();
    }

    /**
     * Simple consent grant record for testing.
     */
    record ConsentGrant(
        String tenantId,
        String patientId,
        String consentType,
        boolean granted,
        long timestamp
    ) {}
}
