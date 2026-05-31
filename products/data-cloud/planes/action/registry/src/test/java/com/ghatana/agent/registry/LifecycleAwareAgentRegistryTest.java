/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.registry;

import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LifecycleAwareAgentRegistry (P4-04).
 *
 * Tests versioned and lifecycle-aware agent registry functionality:
 * - Agent registration with lifecycle states
 * - Version management with rollback support
 * - Lifecycle state transitions (DRAFT → ACTIVE → RETIRED)
 * - Owner and tenant isolation
 * - Validation result storage
 * - Learning feedback references
 */
@DisplayName("LifecycleAwareAgentRegistry Tests")
class LifecycleAwareAgentRegistryTest {

    @Test
    @DisplayName("Should register new agent with DRAFT state")
    void shouldRegisterNewAgentWithDraftState() {
        // Given
        InMemoryAgentRepository repository = new InMemoryAgentRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwareAgentRegistry registry = new LifecycleAwareAgentRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        String owner = "user-1";
        AgentManifestProto manifest = createTestAgentManifest("agent-1");
        LifecycleAwareAgentRegistry.AgentValidationResult validationResult = 
            LifecycleAwareAgentRegistry.AgentValidationResult.valid();
        
        // When
        LifecycleAwareAgentRegistry.RegisteredAgent result = 
            registry.register(tenantId, owner, manifest, validationResult).join();
        
        // Then
        assertNotNull(result);
        assertEquals("agent-1", result.agentId());
        assertEquals("tenant-1", result.tenantId());
        assertEquals("user-1", result.owner());
        assertEquals("1.0.0", result.version());
        assertEquals(LifecycleAwareAgentRegistry.LifecycleState.DRAFT, result.state());
        assertNotNull(result.registrationTime());
        assertNull(result.activationTime());
        assertTrue(result.validationResult().valid());
        assertTrue(result.learningFeedbackRefs().isEmpty());
    }

    @Test
    @DisplayName("Should register new version of existing agent")
    void shouldRegisterNewVersionOfExistingAgent() {
        // Given
        InMemoryAgentRepository repository = new InMemoryAgentRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwareAgentRegistry registry = new LifecycleAwareAgentRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        String owner = "user-1";
        AgentManifestProto manifestV1 = createTestAgentManifest("agent-1", "1.0.0");
        AgentManifestProto manifestV2 = createTestAgentManifest("agent-1", "2.0.0");
        
        LifecycleAwareAgentRegistry.AgentValidationResult validationResult = 
            LifecycleAwareAgentRegistry.AgentValidationResult.valid();
        
        // Register v1 and activate it
        registry.register(tenantId, owner, manifestV1, validationResult).join();
        registry.activate(tenantId, "agent-1", "system").join();
        
        // When
        LifecycleAwareAgentRegistry.RegisteredAgent result = 
            registry.registerVersion(tenantId, owner, manifestV2, validationResult).join();
        
        // Then
        assertNotNull(result);
        assertEquals("agent-1", result.agentId());
        assertEquals("2.0.0", result.version());
        assertEquals(LifecycleAwareAgentRegistry.LifecycleState.DRAFT, result.state());
        assertEquals("1.0.0", result.rollbackVersion()); // Previous active version
    }

    @Test
    @DisplayName("Should activate agent from DRAFT to ACTIVE")
    void shouldActivateAgentFromDraftToActive() {
        // Given
        InMemoryAgentRepository repository = new InMemoryAgentRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwareAgentRegistry registry = new LifecycleAwareAgentRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        String owner = "user-1";
        AgentManifestProto manifest = createTestAgentManifest("agent-1");
        LifecycleAwareAgentRegistry.AgentValidationResult validationResult = 
            LifecycleAwareAgentRegistry.AgentValidationResult.valid();
        
        registry.register(tenantId, owner, manifest, validationResult).join();
        
        // When
        LifecycleAwareAgentRegistry.RegisteredAgent result = 
            registry.activate(tenantId, "agent-1", "system").join();
        
        // Then
        assertNotNull(result);
        assertEquals(LifecycleAwareAgentRegistry.LifecycleState.ACTIVE, result.state());
        assertNotNull(result.activationTime());
    }

    @Test
    @DisplayName("Should retire agent from ACTIVE to RETIRED")
    void shouldRetireAgentFromActiveToRetired() {
        // Given
        InMemoryAgentRepository repository = new InMemoryAgentRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwareAgentRegistry registry = new LifecycleAwareAgentRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        String owner = "user-1";
        AgentManifestProto manifest = createTestAgentManifest("agent-1");
        LifecycleAwareAgentRegistry.AgentValidationResult validationResult = 
            LifecycleAwareAgentRegistry.AgentValidationResult.valid();
        
        registry.register(tenantId, owner, manifest, validationResult).join();
        registry.activate(tenantId, "agent-1", "system").join();
        
        // When
        LifecycleAwareAgentRegistry.RegisteredAgent result = 
            registry.retire(tenantId, "agent-1", "system").join();
        
        // Then
        assertNotNull(result);
        assertEquals(LifecycleAwareAgentRegistry.LifecycleState.RETIRED, result.state());
    }

    @Test
    @DisplayName("Should rollback to previous version")
    void shouldRollbackToPreviousVersion() {
        // Given
        InMemoryAgentRepository repository = new InMemoryAgentRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwareAgentRegistry registry = new LifecycleAwareAgentRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        String owner = "user-1";
        AgentManifestProto manifestV1 = createTestAgentManifest("agent-1", "1.0.0");
        AgentManifestProto manifestV2 = createTestAgentManifest("agent-1", "2.0.0");
        
        LifecycleAwareAgentRegistry.AgentValidationResult validationResult = 
            LifecycleAwareAgentRegistry.AgentValidationResult.valid();
        
        // Register v1, activate it, then register v2
        registry.register(tenantId, owner, manifestV1, validationResult).join();
        registry.activate(tenantId, "agent-1", "system").join();
        registry.registerVersion(tenantId, owner, manifestV2, validationResult).join();
        
        // When
        LifecycleAwareAgentRegistry.RegisteredAgent result = 
            registry.rollback(tenantId, "agent-1", "1.0.0", "system").join();
        
        // Then
        assertNotNull(result);
        assertEquals("1.0.0", result.version());
        assertEquals(LifecycleAwareAgentRegistry.LifecycleState.ACTIVE, result.state());
        assertEquals("2.0.0", result.rollbackVersion()); // Current version becomes rollback target
    }

    @Test
    @DisplayName("Should enforce tenant isolation")
    void shouldEnforceTenantIsolation() {
        // Given
        InMemoryAgentRepository repository = new InMemoryAgentRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwareAgentRegistry registry = new LifecycleAwareAgentRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenant1 = TenantId.of("tenant-1");
        TenantId tenant2 = TenantId.of("tenant-2");
        AgentManifestProto manifest = createTestAgentManifest("agent-1");
        LifecycleAwareAgentRegistry.AgentValidationResult validationResult = 
            LifecycleAwareAgentRegistry.AgentValidationResult.valid();
        
        // Register agent for tenant-1
        registry.register(tenant1, "user-1", manifest, validationResult).join();
        
        // When
        Optional<LifecycleAwareAgentRegistry.RegisteredAgent> result = 
            registry.get(tenant2, "agent-1").join();
        
        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should add learning feedback references")
    void shouldAddLearningFeedbackReferences() {
        // Given
        InMemoryAgentRepository repository = new InMemoryAgentRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwareAgentRegistry registry = new LifecycleAwareAgentRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        AgentManifestProto manifest = createTestAgentManifest("agent-1");
        LifecycleAwareAgentRegistry.AgentValidationResult validationResult = 
            LifecycleAwareAgentRegistry.AgentValidationResult.valid();
        
        registry.register(tenantId, "user-1", manifest, validationResult).join();
        
        // When
        registry.addLearningFeedback(tenantId, "agent-1", "feedback-1", Map.of("score", 0.9)).join();
        
        // Then
        Optional<LifecycleAwareAgentRegistry.RegisteredAgent> result = 
            registry.get(tenantId, "agent-1").join();
        assertTrue(result.isPresent());
        assertEquals(1, result.get().learningFeedbackRefs().size());
        assertEquals("feedback-1", result.get().learningFeedbackRefs().get(0));
    }

    @Test
    @DisplayName("Should list agents by lifecycle state")
    void shouldListAgentsByLifecycleState() {
        // Given
        InMemoryAgentRepository repository = new InMemoryAgentRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwareAgentRegistry registry = new LifecycleAwareAgentRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        LifecycleAwareAgentRegistry.AgentValidationResult validationResult = 
            LifecycleAwareAgentRegistry.AgentValidationResult.valid();
        
        registry.register(tenantId, "user-1", createTestAgentManifest("agent-1"), validationResult).join();
        registry.register(tenantId, "user-1", createTestAgentManifest("agent-2"), validationResult).join();
        registry.activate(tenantId, "agent-1", "system").join();
        
        // When
        List<LifecycleAwareAgentRegistry.RegisteredAgent> activeAgents = 
            registry.listByState(tenantId, LifecycleAwareAgentRegistry.LifecycleState.ACTIVE).join();
        
        // Then
        assertEquals(1, activeAgents.size());
        assertEquals("agent-1", activeAgents.get(0).agentId());
    }

    @Test
    @DisplayName("Should get all versions of an agent")
    void shouldGetAllVersionsOfAgent() {
        // Given
        InMemoryAgentRepository repository = new InMemoryAgentRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwareAgentRegistry registry = new LifecycleAwareAgentRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        LifecycleAwareAgentRegistry.AgentValidationResult validationResult = 
            LifecycleAwareAgentRegistry.AgentValidationResult.valid();
        
        registry.register(tenantId, "user-1", createTestAgentManifest("agent-1", "1.0.0"), validationResult).join();
        registry.registerVersion(tenantId, "user-1", createTestAgentManifest("agent-1", "2.0.0"), validationResult).join();
        
        // When
        List<LifecycleAwareAgentRegistry.RegisteredAgent> versions = 
            registry.getAllVersions(tenantId, "agent-1").join();
        
        // Then
        assertEquals(2, versions.size());
        assertTrue(versions.stream().anyMatch(a -> a.version().equals("1.0.0")));
        assertTrue(versions.stream().anyMatch(a -> a.version().equals("2.0.0")));
    }

    @Test
    @DisplayName("Should handle invalid validation results")
    void shouldHandleInvalidValidationResults() {
        // Given
        InMemoryAgentRepository repository = new InMemoryAgentRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwareAgentRegistry registry = new LifecycleAwareAgentRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        AgentManifestProto manifest = createTestAgentManifest("agent-1");
        LifecycleAwareAgentRegistry.AgentValidationResult validationResult = 
            LifecycleAwareAgentRegistry.AgentValidationResult.invalid(List.of("Missing required field"));
        
        // When
        LifecycleAwareAgentRegistry.RegisteredAgent result = 
            registry.register(tenantId, "user-1", manifest, validationResult).join();
        
        // Then
        assertNotNull(result);
        assertFalse(result.validationResult().valid());
        assertEquals(1, result.validationResult().errors().size());
        assertEquals("Missing required field", result.validationResult().errors().get(0));
    }

    // ==================== Helper Methods ====================

    private AgentManifestProto createTestAgentManifest(String agentId) {
        return createTestAgentManifest(agentId, "1.0.0");
    }

    private AgentManifestProto createTestAgentManifest(String agentId, String version) {
        return AgentManifestProto.newBuilder()
            .setId(agentId)
            .setVersion(version)
            .setName("Test Agent")
            .setDescription("Test agent for unit tests")
            .build();
    }

    // ==================== In-Memory Implementations ====================

    private static class InMemoryAgentRepository implements LifecycleAwareAgentRegistry.AgentRepository {
        private final Map<String, LifecycleAwareAgentRegistry.RegisteredAgent> agents = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Promise<Void> save(LifecycleAwareAgentRegistry.RegisteredAgent agent) {
            String key = agent.tenantId() + ":" + agent.agentId() + ":" + agent.version();
            agents.put(key, agent);
            return Promise.complete();
        }

        @Override
        public Promise<Optional<LifecycleAwareAgentRegistry.RegisteredAgent>> findById(String tenantId, String agentId) {
            // Find latest version
            return Promise.of(agents.values().stream()
                .filter(a -> a.tenantId().equals(tenantId) && a.agentId().equals(agentId))
                .max((a, b) -> a.version().compareTo(b.version())));
        }

        @Override
        public Promise<Optional<LifecycleAwareAgentRegistry.RegisteredAgent>> findVersion(String tenantId, String agentId, String version) {
            String key = tenantId + ":" + agentId + ":" + version;
            return Promise.of(Optional.ofNullable(agents.get(key)));
        }

        @Override
        public Promise<List<LifecycleAwareAgentRegistry.RegisteredAgent>> findByTenant(String tenantId) {
            return Promise.of(agents.values().stream()
                .filter(a -> a.tenantId().equals(tenantId))
                .toList());
        }

        @Override
        public Promise<List<LifecycleAwareAgentRegistry.RegisteredAgent>> findByTenantAndState(String tenantId, LifecycleAwareAgentRegistry.LifecycleState state) {
            return Promise.of(agents.values().stream()
                .filter(a -> a.tenantId().equals(tenantId) && a.state() == state)
                .toList());
        }

        @Override
        public Promise<List<LifecycleAwareAgentRegistry.RegisteredAgent>> findAllVersions(String tenantId, String agentId) {
            return Promise.of(agents.values().stream()
                .filter(a -> a.tenantId().equals(tenantId) && a.agentId().equals(agentId))
                .toList());
        }

        @Override
        public Promise<Void> recordLifecycleEvent(String tenantId, String agentId, String event, String actor, Map<String, Object> metadata) {
            return Promise.complete();
        }
    }

    private static class InMemoryValidationResultStore implements LifecycleAwareAgentRegistry.ValidationResultStore {
        private final Map<String, LifecycleAwareAgentRegistry.AgentValidationResult> results = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Promise<Void> save(String agentId, String version, LifecycleAwareAgentRegistry.AgentValidationResult result) {
            String key = agentId + ":" + version;
            results.put(key, result);
            return Promise.complete();
        }

        @Override
        public Promise<Optional<LifecycleAwareAgentRegistry.AgentValidationResult>> find(String agentId, String version) {
            String key = agentId + ":" + version;
            return Promise.of(Optional.ofNullable(results.get(key)));
        }
    }

    private static class InMemoryLearningFeedbackStore implements LifecycleAwareAgentRegistry.LearningFeedbackStore {
        private final Map<String, List<Map<String, Object>>> feedback = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Promise<Void> save(String agentId, String feedbackId, Map<String, Object> feedbackData) {
            feedback.computeIfAbsent(agentId, k -> new java.util.ArrayList<>()).add(feedbackData);
            return Promise.complete();
        }

        @Override
        public Promise<List<Map<String, Object>>> findByAgent(String agentId) {
            return Promise.of(feedback.getOrDefault(agentId, List.of()));
        }
    }
}
