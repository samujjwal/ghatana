/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.registry;

import com.ghatana.aep.pattern.spec.PatternSpec;
import com.ghatana.aep.pattern.spec.PatternSpecMetadata;
import com.ghatana.aep.pattern.spec.PatternSpecPattern;
import com.ghatana.aep.pattern.spec.PatternSpecWindow;
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
 * Tests for LifecycleAwarePatternRegistry (P4-04).
 *
 * Tests versioned and lifecycle-aware pattern registry functionality:
 * - Pattern registration with lifecycle states
 * - Version management with rollback support
 * - Lifecycle state transitions (DRAFT → ACTIVE → RETIRED)
 * - Owner and tenant isolation
 * - Validation result storage
 * - Learning feedback references
 */
@DisplayName("LifecycleAwarePatternRegistry Tests")
class LifecycleAwarePatternRegistryTest {

    @Test
    @DisplayName("Should register new pattern with DRAFT state")
    void shouldRegisterNewPatternWithDraftState() {
        // Given
        InMemoryPatternRepository repository = new InMemoryPatternRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwarePatternRegistry registry = new LifecycleAwarePatternRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        String owner = "user-1";
        PatternSpec spec = createTestPatternSpec("pattern-1");
        LifecycleAwarePatternRegistry.PatternSpecValidationResult validationResult = 
            LifecycleAwarePatternRegistry.PatternSpecValidationResult.valid();
        
        // When
        LifecycleAwarePatternRegistry.RegisteredPattern result = 
            registry.register(tenantId, owner, spec, validationResult).join();
        
        // Then
        assertNotNull(result);
        assertEquals("pattern-1", result.patternId());
        assertEquals("tenant-1", result.tenantId());
        assertEquals("user-1", result.owner());
        assertEquals("1.0.0", result.version());
        assertEquals(LifecycleAwarePatternRegistry.LifecycleState.DRAFT, result.state());
        assertNotNull(result.registrationTime());
        assertNull(result.activationTime());
        assertTrue(result.validationResult().valid());
        assertTrue(result.learningFeedbackRefs().isEmpty());
    }

    @Test
    @DisplayName("Should register new version of existing pattern")
    void shouldRegisterNewVersionOfExistingPattern() {
        // Given
        InMemoryPatternRepository repository = new InMemoryPatternRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwarePatternRegistry registry = new LifecycleAwarePatternRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        String owner = "user-1";
        PatternSpec specV1 = createTestPatternSpec("pattern-1", "1.0.0");
        PatternSpec specV2 = createTestPatternSpec("pattern-1", "2.0.0");
        
        LifecycleAwarePatternRegistry.PatternSpecValidationResult validationResult = 
            LifecycleAwarePatternRegistry.PatternSpecValidationResult.valid();
        
        // Register v1 and activate it
        registry.register(tenantId, owner, specV1, validationResult).join();
        registry.activate(tenantId, "pattern-1", "system").join();
        
        // When
        LifecycleAwarePatternRegistry.RegisteredPattern result = 
            registry.registerVersion(tenantId, owner, specV2, validationResult).join();
        
        // Then
        assertNotNull(result);
        assertEquals("pattern-1", result.patternId());
        assertEquals("2.0.0", result.version());
        assertEquals(LifecycleAwarePatternRegistry.LifecycleState.DRAFT, result.state());
        assertEquals("1.0.0", result.rollbackVersion()); // Previous active version
    }

    @Test
    @DisplayName("Should activate pattern from DRAFT to ACTIVE")
    void shouldActivatePatternFromDraftToActive() {
        // Given
        InMemoryPatternRepository repository = new InMemoryPatternRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwarePatternRegistry registry = new LifecycleAwarePatternRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        String owner = "user-1";
        PatternSpec spec = createTestPatternSpec("pattern-1");
        LifecycleAwarePatternRegistry.PatternSpecValidationResult validationResult = 
            LifecycleAwarePatternRegistry.PatternSpecValidationResult.valid();
        
        registry.register(tenantId, owner, spec, validationResult).join();
        
        // When
        LifecycleAwarePatternRegistry.RegisteredPattern result = 
            registry.activate(tenantId, "pattern-1", "system").join();
        
        // Then
        assertNotNull(result);
        assertEquals(LifecycleAwarePatternRegistry.LifecycleState.ACTIVE, result.state());
        assertNotNull(result.activationTime());
    }

    @Test
    @DisplayName("Should retire pattern from ACTIVE to RETIRED")
    void shouldRetirePatternFromActiveToRetired() {
        // Given
        InMemoryPatternRepository repository = new InMemoryPatternRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwarePatternRegistry registry = new LifecycleAwarePatternRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        String owner = "user-1";
        PatternSpec spec = createTestPatternSpec("pattern-1");
        LifecycleAwarePatternRegistry.PatternSpecValidationResult validationResult = 
            LifecycleAwarePatternRegistry.PatternSpecValidationResult.valid();
        
        registry.register(tenantId, owner, spec, validationResult).join();
        registry.activate(tenantId, "pattern-1", "system").join();
        
        // When
        LifecycleAwarePatternRegistry.RegisteredPattern result = 
            registry.retire(tenantId, "pattern-1", "system").join();
        
        // Then
        assertNotNull(result);
        assertEquals(LifecycleAwarePatternRegistry.LifecycleState.RETIRED, result.state());
    }

    @Test
    @DisplayName("Should rollback to previous version")
    void shouldRollbackToPreviousVersion() {
        // Given
        InMemoryPatternRepository repository = new InMemoryPatternRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwarePatternRegistry registry = new LifecycleAwarePatternRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        String owner = "user-1";
        PatternSpec specV1 = createTestPatternSpec("pattern-1", "1.0.0");
        PatternSpec specV2 = createTestPatternSpec("pattern-1", "2.0.0");
        
        LifecycleAwarePatternRegistry.PatternSpecValidationResult validationResult = 
            LifecycleAwarePatternRegistry.PatternSpecValidationResult.valid();
        
        // Register v1, activate it, then register v2
        registry.register(tenantId, owner, specV1, validationResult).join();
        registry.activate(tenantId, "pattern-1", "system").join();
        registry.registerVersion(tenantId, owner, specV2, validationResult).join();
        
        // When
        LifecycleAwarePatternRegistry.RegisteredPattern result = 
            registry.rollback(tenantId, "pattern-1", "1.0.0", "system").join();
        
        // Then
        assertNotNull(result);
        assertEquals("1.0.0", result.version());
        assertEquals(LifecycleAwarePatternRegistry.LifecycleState.ACTIVE, result.state());
        assertEquals("2.0.0", result.rollbackVersion()); // Current version becomes rollback target
    }

    @Test
    @DisplayName("Should enforce tenant isolation")
    void shouldEnforceTenantIsolation() {
        // Given
        InMemoryPatternRepository repository = new InMemoryPatternRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwarePatternRegistry registry = new LifecycleAwarePatternRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenant1 = TenantId.of("tenant-1");
        TenantId tenant2 = TenantId.of("tenant-2");
        PatternSpec spec = createTestPatternSpec("pattern-1");
        LifecycleAwarePatternRegistry.PatternSpecValidationResult validationResult = 
            LifecycleAwarePatternRegistry.PatternSpecValidationResult.valid();
        
        // Register pattern for tenant-1
        registry.register(tenant1, "user-1", spec, validationResult).join();
        
        // When
        Optional<LifecycleAwarePatternRegistry.RegisteredPattern> result = 
            registry.get(tenant2, "pattern-1").join();
        
        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should add learning feedback references")
    void shouldAddLearningFeedbackReferences() {
        // Given
        InMemoryPatternRepository repository = new InMemoryPatternRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwarePatternRegistry registry = new LifecycleAwarePatternRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        PatternSpec spec = createTestPatternSpec("pattern-1");
        LifecycleAwarePatternRegistry.PatternSpecValidationResult validationResult = 
            LifecycleAwarePatternRegistry.PatternSpecValidationResult.valid();
        
        registry.register(tenantId, "user-1", spec, validationResult).join();
        
        // When
        registry.addLearningFeedback(tenantId, "pattern-1", "feedback-1", Map.of("score", 0.9)).join();
        
        // Then
        Optional<LifecycleAwarePatternRegistry.RegisteredPattern> result = 
            registry.get(tenantId, "pattern-1").join();
        assertTrue(result.isPresent());
        assertEquals(1, result.get().learningFeedbackRefs().size());
        assertEquals("feedback-1", result.get().learningFeedbackRefs().get(0));
    }

    @Test
    @DisplayName("Should list patterns by lifecycle state")
    void shouldListPatternsByLifecycleState() {
        // Given
        InMemoryPatternRepository repository = new InMemoryPatternRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwarePatternRegistry registry = new LifecycleAwarePatternRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        LifecycleAwarePatternRegistry.PatternSpecValidationResult validationResult = 
            LifecycleAwarePatternRegistry.PatternSpecValidationResult.valid();
        
        registry.register(tenantId, "user-1", createTestPatternSpec("pattern-1"), validationResult).join();
        registry.register(tenantId, "user-1", createTestPatternSpec("pattern-2"), validationResult).join();
        registry.activate(tenantId, "pattern-1", "system").join();
        
        // When
        List<LifecycleAwarePatternRegistry.RegisteredPattern> activePatterns = 
            registry.listByState(tenantId, LifecycleAwarePatternRegistry.LifecycleState.ACTIVE).join();
        
        // Then
        assertEquals(1, activePatterns.size());
        assertEquals("pattern-1", activePatterns.get(0).patternId());
    }

    @Test
    @DisplayName("Should get all versions of a pattern")
    void shouldGetAllVersionsOfPattern() {
        // Given
        InMemoryPatternRepository repository = new InMemoryPatternRepository();
        InMemoryValidationResultStore validationStore = new InMemoryValidationResultStore();
        InMemoryLearningFeedbackStore feedbackStore = new InMemoryLearningFeedbackStore();
        
        LifecycleAwarePatternRegistry registry = new LifecycleAwarePatternRegistry(
            repository, validationStore, feedbackStore
        );
        
        TenantId tenantId = TenantId.of("tenant-1");
        LifecycleAwarePatternRegistry.PatternSpecValidationResult validationResult = 
            LifecycleAwarePatternRegistry.PatternSpecValidationResult.valid();
        
        registry.register(tenantId, "user-1", createTestPatternSpec("pattern-1", "1.0.0"), validationResult).join();
        registry.registerVersion(tenantId, "user-1", createTestPatternSpec("pattern-1", "2.0.0"), validationResult).join();
        
        // When
        List<LifecycleAwarePatternRegistry.RegisteredPattern> versions = 
            registry.getAllVersions(tenantId, "pattern-1").join();
        
        // Then
        assertEquals(2, versions.size());
        assertTrue(versions.stream().anyMatch(p -> p.version().equals("1.0.0")));
        assertTrue(versions.stream().anyMatch(p -> p.version().equals("2.0.0")));
    }

    // ==================== Helper Methods ====================

    private PatternSpec createTestPatternSpec(String patternId) {
        return createTestPatternSpec(patternId, "1.0.0");
    }

    private PatternSpec createTestPatternSpec(String patternId, String version) {
        PatternSpecMetadata metadata = new PatternSpecMetadata(
            patternId,
            version,
            "Test Pattern",
            "Test pattern for unit tests",
            Map.of()
        );

        PatternSpecPattern pattern = new PatternSpecPattern(
            List.of(),
            Map.of()
        );

        PatternSpecWindow window = new PatternSpecWindow(
            "10m",
            "1m"
        );

        return new PatternSpec(metadata, pattern, window);
    }

    // ==================== In-Memory Implementations ====================

    private static class InMemoryPatternRepository implements LifecycleAwarePatternRegistry.PatternRepository {
        private final Map<String, LifecycleAwarePatternRegistry.RegisteredPattern> patterns = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Promise<Void> save(LifecycleAwarePatternRegistry.RegisteredPattern pattern) {
            String key = pattern.tenantId() + ":" + pattern.patternId() + ":" + pattern.version();
            patterns.put(key, pattern);
            return Promise.complete();
        }

        @Override
        public Promise<Optional<LifecycleAwarePatternRegistry.RegisteredPattern>> findById(String tenantId, String patternId) {
            // Find latest version
            return Promise.of(patterns.values().stream()
                .filter(p -> p.tenantId().equals(tenantId) && p.patternId().equals(patternId))
                .max((a, b) -> a.version().compareTo(b.version())));
        }

        @Override
        public Promise<Optional<LifecycleAwarePatternRegistry.RegisteredPattern>> findVersion(String tenantId, String patternId, String version) {
            String key = tenantId + ":" + patternId + ":" + version;
            return Promise.of(Optional.ofNullable(patterns.get(key)));
        }

        @Override
        public Promise<List<LifecycleAwarePatternRegistry.RegisteredPattern>> findByTenant(String tenantId) {
            return Promise.of(patterns.values().stream()
                .filter(p -> p.tenantId().equals(tenantId))
                .toList());
        }

        @Override
        public Promise<List<LifecycleAwarePatternRegistry.RegisteredPattern>> findByTenantAndState(String tenantId, LifecycleAwarePatternRegistry.LifecycleState state) {
            return Promise.of(patterns.values().stream()
                .filter(p -> p.tenantId().equals(tenantId) && p.state() == state)
                .toList());
        }

        @Override
        public Promise<List<LifecycleAwarePatternRegistry.RegisteredPattern>> findAllVersions(String tenantId, String patternId) {
            return Promise.of(patterns.values().stream()
                .filter(p -> p.tenantId().equals(tenantId) && p.patternId().equals(patternId))
                .toList());
        }

        @Override
        public Promise<Void> recordLifecycleEvent(String tenantId, String patternId, String event, String actor, Map<String, Object> metadata) {
            return Promise.complete();
        }
    }

    private static class InMemoryValidationResultStore implements LifecycleAwarePatternRegistry.ValidationResultStore {
        private final Map<String, LifecycleAwarePatternRegistry.PatternSpecValidationResult> results = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Promise<Void> save(String patternId, String version, LifecycleAwarePatternRegistry.PatternSpecValidationResult result) {
            String key = patternId + ":" + version;
            results.put(key, result);
            return Promise.complete();
        }

        @Override
        public Promise<Optional<LifecycleAwarePatternRegistry.PatternSpecValidationResult>> find(String patternId, String version) {
            String key = patternId + ":" + version;
            return Promise.of(Optional.ofNullable(results.get(key)));
        }
    }

    private static class InMemoryLearningFeedbackStore implements LifecycleAwarePatternRegistry.LearningFeedbackStore {
        private final Map<String, List<Map<String, Object>>> feedback = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Promise<Void> save(String patternId, String feedbackId, Map<String, Object> feedbackData) {
            feedback.computeIfAbsent(patternId, k -> new java.util.ArrayList<>()).add(feedbackData);
            return Promise.complete();
        }

        @Override
        public Promise<List<Map<String, Object>>> findByAgent(String patternId) {
            return Promise.of(feedback.getOrDefault(patternId, List.of()));
        }
    }
}
