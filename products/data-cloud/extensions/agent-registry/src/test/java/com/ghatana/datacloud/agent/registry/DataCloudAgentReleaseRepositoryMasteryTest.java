/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.release.AgentRelease;
import com.ghatana.agent.release.AgentReleaseState;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import io.activej.eventloop.Eventloop;
import io.activej.eventloop.EventloopThread;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DataCloudAgentReleaseRepository mastery-related query methods.
 *
 * @doc.type class
 * @doc.purpose Test findByEvaluationPack and findByCapabilityMaturity methods
 * @doc.layer test
 */
@DisplayName("DataCloudAgentReleaseRepository Mastery Query Tests")
public class DataCloudAgentReleaseRepositoryMasteryTest {

    private Eventloop eventloop;
    private EventloopThread eventloopThread;
    private MockDataCloudClient dataCloudClient;
    private DataCloudAgentReleaseRepository repository;

    @BeforeEach
    void setUp() throws InterruptedException {
        eventloop = Eventloop.create().withCurrentThread();
        eventloopThread = EventloopThread.create().withName("test-eventloop").start();
        eventloopThread.submit(() -> {
            dataCloudClient = new MockDataCloudClient();
            repository = new DataCloudAgentReleaseRepository(dataCloudClient, "test-tenant");
        }).join();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        eventloopThread.submit(() -> {
            eventloop.breakEventloop();
        }).join();
        eventloopThread.join();
    }

    @Test
    @DisplayName("Should find releases by evaluation pack ID")
    void testFindByEvaluationPack() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Setup mock data
            Map<String, Object> release1Data = new HashMap<>();
            release1Data.put("agentReleaseId", "release-001");
            release1Data.put("agentId", "agent-001");
            release1Data.put("specVersion", "1.0.0");
            release1Data.put("releaseVersion", "v1.0.0");
            release1Data.put("state", "ACTIVE");
            release1Data.put("evaluationPackId", "eval-pack-001");
            release1Data.put("compatibleRuntimeVersions", List.of("v1.0"));
            release1Data.put("dataClassesHandled", List.of());
            release1Data.put("permittedPurposes", List.of());
            release1Data.put("createdAt", Instant.now().toString());
            release1Data.put("updatedAt", Instant.now().toString());

            dataCloudClient.addEntity(release1Data);

            // Query by evaluation pack
            Promise<List<AgentRelease>> result = repository.findByEvaluationPack("eval-pack-001");
            List<AgentRelease> releases = result.join();

            assertNotNull(releases);
            assertEquals(1, releases.size());
            assertEquals("release-001", releases.get(0).agentReleaseId());
            assertEquals("eval-pack-001", releases.get(0).evaluationPackId());
        }).join();
    }

    @Test
    @DisplayName("Should return empty list when no releases match evaluation pack")
    void testFindByEvaluationPackEmptyResult() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Query by non-existent evaluation pack
            Promise<List<AgentRelease>> result = repository.findByEvaluationPack("nonexistent-pack");
            List<AgentRelease> releases = result.join();

            assertNotNull(releases);
            assertTrue(releases.isEmpty());
        }).join();
    }

    @Test
    @DisplayName("Should find releases by capability maturity profile")
    void testFindByCapabilityMaturity() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Setup mock data
            Map<String, Object> release1Data = new HashMap<>();
            release1Data.put("agentReleaseId", "release-001");
            release1Data.put("agentId", "agent-001");
            release1Data.put("specVersion", "1.0.0");
            release1Data.put("releaseVersion", "v1.0.0");
            release1Data.put("state", "ACTIVE");
            release1Data.put("capabilityMaturityProfile", "MATURITY_LEVEL_3");
            release1Data.put("compatibleRuntimeVersions", List.of("v1.0"));
            release1Data.put("dataClassesHandled", List.of());
            release1Data.put("permittedPurposes", List.of());
            release1Data.put("createdAt", Instant.now().toString());
            release1Data.put("updatedAt", Instant.now().toString());

            dataCloudClient.addEntity(release1Data);

            // Query by capability maturity
            Promise<List<AgentRelease>> result = repository.findByCapabilityMaturity("MATURITY_LEVEL_3");
            List<AgentRelease> releases = result.join();

            assertNotNull(releases);
            assertEquals(1, releases.size());
            assertEquals("release-001", releases.get(0).agentReleaseId());
            assertEquals("MATURITY_LEVEL_3", releases.get(0).capabilityMaturityProfile());
        }).join();
    }

    @Test
    @DisplayName("Should return empty list when no releases match capability maturity")
    void testFindByCapabilityMaturityEmptyResult() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Query by non-existent capability maturity
            Promise<List<AgentRelease>> result = repository.findByCapabilityMaturity("nonexistent-maturity");
            List<AgentRelease> releases = result.join();

            assertNotNull(releases);
            assertTrue(releases.isEmpty());
        }).join();
    }

    @Test
    @DisplayName("Should handle null evaluation pack ID")
    void testHandleNullEvaluationPackId() throws InterruptedException {
        eventloopThread.submit(() -> {
            assertThrows(NullPointerException.class, () -> {
                repository.findByEvaluationPack(null).join();
            });
        }).join();
    }

    @Test
    @DisplayName("Should handle null capability maturity")
    void testHandleNullCapabilityMaturity() throws InterruptedException {
        eventloopThread.submit(() -> {
            assertThrows(NullPointerException.class, () -> {
                repository.findByCapabilityMaturity(null).join();
            });
        }).join();
    }

    @Test
    @DisplayName("Should find multiple releases with same evaluation pack")
    void testFindMultipleReleasesByEvaluationPack() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Setup mock data with multiple releases
            for (int i = 1; i <= 3; i++) {
                Map<String, Object> releaseData = new HashMap<>();
                releaseData.put("agentReleaseId", "release-00" + i);
                releaseData.put("agentId", "agent-00" + i);
                releaseData.put("specVersion", "1.0.0");
                releaseData.put("releaseVersion", "v1.0." + i);
                releaseData.put("state", "ACTIVE");
                releaseData.put("evaluationPackId", "eval-pack-001");
                releaseData.put("compatibleRuntimeVersions", List.of("v1.0"));
                releaseData.put("dataClassesHandled", List.of());
                releaseData.put("permittedPurposes", List.of());
                releaseData.put("createdAt", Instant.now().toString());
                releaseData.put("updatedAt", Instant.now().toString());

                dataCloudClient.addEntity(releaseData);
            }

            // Query by evaluation pack
            Promise<List<AgentRelease>> result = repository.findByEvaluationPack("eval-pack-001");
            List<AgentRelease> releases = result.join();

            assertNotNull(releases);
            assertEquals(3, releases.size());
        }).join();
    }

    @Test
    @DisplayName("Should find multiple releases with same capability maturity")
    void testFindMultipleReleasesByCapabilityMaturity() throws InterruptedException {
        eventloopThread.submit(() -> {
            // Setup mock data with multiple releases
            for (int i = 1; i <= 3; i++) {
                Map<String, Object> releaseData = new HashMap<>();
                releaseData.put("agentReleaseId", "release-00" + i);
                releaseData.put("agentId", "agent-00" + i);
                releaseData.put("specVersion", "1.0.0");
                releaseData.put("releaseVersion", "v1.0." + i);
                releaseData.put("state", "ACTIVE");
                releaseData.put("capabilityMaturityProfile", "MATURITY_LEVEL_3");
                releaseData.put("compatibleRuntimeVersions", List.of("v1.0"));
                releaseData.put("dataClassesHandled", List.of());
                releaseData.put("permittedPurposes", List.of());
                releaseData.put("createdAt", Instant.now().toString());
                releaseData.put("updatedAt", Instant.now().toString());

                dataCloudClient.addEntity(releaseData);
            }

            // Query by capability maturity
            Promise<List<AgentRelease>> result = repository.findByCapabilityMaturity("MATURITY_LEVEL_3");
            List<AgentRelease> releases = result.join();

            assertNotNull(releases);
            assertEquals(3, releases.size());
        }).join();
    }

    // ── Test Doubles ─────────────────────────────────────────────────────

    private static class MockDataCloudClient implements DataCloudClient {
        private final java.util.List<Map<String, Object>> entities = new java.util.ArrayList<>();

        void addEntity(Map<String, Object> data) {
            entities.add(data);
        }

        @Override
        public Promise<Map<String, Object>> createEntity(String tenantId, String collection, Map<String, Object> data) {
            entities.add(data);
            return Promise.of(Map.of("id", "entity-" + entities.size()));
        }

        @Override
        public Promise<List<Map<String, Object>>> queryEntities(String tenantId, String collection, QuerySpecInterface query) {
            // Simple filter implementation for testing
            String filter = query.getFilter();
            if (filter != null && filter.contains("evaluationPackId")) {
                String evalPackId = filter.split("'")[1];
                return Promise.of(entities.stream()
                        .filter(e -> evalPackId.equals(e.get("evaluationPackId")))
                        .toList());
            }
            if (filter != null && filter.contains("capabilityMaturityProfile")) {
                String maturity = filter.split("'")[1];
                return Promise.of(entities.stream()
                        .filter(e -> maturity.equals(e.get("capabilityMaturityProfile")))
                        .toList());
            }
            return Promise.of(List.copyOf(entities));
        }

        @Override
        public Promise<Optional<Map<String, Object>>> getEntity(String tenantId, String collection, String entityId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<Map<String, Object>> updateEntity(String tenantId, String collection, String entityId, Map<String, Object> data) {
            return Promise.of(data);
        }

        @Override
        public Promise<Void> deleteEntity(String tenantId, String collection, String entityId) {
            return Promise.complete();
        }
    }
}
