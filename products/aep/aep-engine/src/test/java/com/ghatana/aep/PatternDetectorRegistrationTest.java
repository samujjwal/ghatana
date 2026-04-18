/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for PatternDetector registration functionality in AepEngine.
 *
 * @doc.type class
 * @doc.purpose Test PatternDetector registration and invocation
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("PatternDetector Registration")
class PatternDetectorRegistrationTest extends EventloopTestBase {

    private AepEngine engine;
    private AepEngine.PatternDetector testDetector;
    private AtomicInteger invocationCount;

    @BeforeEach
    void setUp() {
        engine = Aep.forTesting();
        invocationCount = new AtomicInteger(0);
        
        // Create a simple test detector that just counts invocations
        testDetector = (tenantId, event, patterns) -> {
            invocationCount.incrementAndGet();
            return Promise.of(List.of(new AepEngine.Detection(
                "test-pattern",
                event.type(),
                0.9,
                Map.of("detectedAt", Instant.now().toString()),
                Instant.now()
            )));
        };
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Nested
    @DisplayName("registerPatternDetector()")
    class RegisterTests {

        @Test
        @DisplayName("registers detector successfully")
        void registersDetectorSuccessfully() {
            engine.registerPatternDetector("tenant-1", testDetector);
            
            // Registration should not throw
            assertThat(invocationCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("throws on null detector")
        void throwsOnNullDetector() {
            assertThatThrownBy(() -> engine.registerPatternDetector("tenant-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("detector");
        }

        @Test
        @DisplayName("throws on null tenantId")
        void throwsOnNullTenantId() {
            assertThatThrownBy(() -> engine.registerPatternDetector(null, testDetector))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("allows multiple detectors for same tenant")
        void allowsMultipleDetectorsForSameTenant() {
            AepEngine.PatternDetector detector2 = (tenantId, event, patterns) -> Promise.of(List.of());
            
            engine.registerPatternDetector("tenant-1", testDetector);
            engine.registerPatternDetector("tenant-1", detector2);
            
            // Should not throw
        }

        @Test
        @DisplayName("allows detectors for different tenants")
        void allowsDetectorsForDifferentTenants() {
            engine.registerPatternDetector("tenant-1", testDetector);
            engine.registerPatternDetector("tenant-2", testDetector);
            
            // Should not throw
        }
    }

    @Nested
    @DisplayName("unregisterPatternDetector()")
    class UnregisterTests {

        @Test
        @DisplayName("removes registered detector")
        void removesRegisteredDetector() {
            engine.registerPatternDetector("tenant-1", testDetector);
            engine.unregisterPatternDetector("tenant-1", testDetector);
            
            // Should not throw
        }

        @Test
        @DisplayName("throws on null detector")
        void throwsOnNullDetector() {
            assertThatThrownBy(() -> engine.unregisterPatternDetector("tenant-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("detector");
        }

        @Test
        @DisplayName("throws on null tenantId")
        void throwsOnNullTenantId() {
            assertThatThrownBy(() -> engine.unregisterPatternDetector(null, testDetector))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("handles unregister of non-existent detector gracefully")
        void handlesUnregisterOfNonExistentDetector() {
            // Should not throw even if detector was never registered
            engine.unregisterPatternDetector("tenant-1", testDetector);
        }
    }

    @Nested
    @DisplayName("Detector Invocation")
    class InvocationTests {

        @Test
        @DisplayName("registered detector is invoked during event processing")
        void registeredDetectorIsInvokedDuringProcessing() {
            engine.registerPatternDetector("tenant-1", testDetector);
            
            AepEngine.Event event = new AepEngine.Event("test.event", 
                Map.of("key", "value"), Map.of(), Instant.now());
            
            AepEngine.ProcessingResult result = runPromise(() -> engine.process("tenant-1", event));
            
            assertThat(result.success()).isTrue();
            assertThat(invocationCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("detector is not invoked for different tenant")
        void detectorNotInvokedForDifferentTenant() {
            engine.registerPatternDetector("tenant-1", testDetector);
            
            AepEngine.Event event = new AepEngine.Event("test.event", 
                Map.of("key", "value"), Map.of(), Instant.now());
            
            runPromise(() -> engine.process("tenant-2", event));
            
            assertThat(invocationCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("detector errors do not fail event processing")
        void detectorErrorsDoNotFailEventProcessing() {
            AepEngine.PatternDetector failingDetector = (tenantId, event, patterns) -> {
                throw new RuntimeException("Detector failed");
            };
            
            engine.registerPatternDetector("tenant-1", failingDetector);
            
            AepEngine.Event event = new AepEngine.Event("test.event", 
                Map.of("key", "value"), Map.of(), Instant.now());
            
            AepEngine.ProcessingResult result = runPromise(() -> engine.process("tenant-1", event));
            
            // Event processing should succeed even if detector fails
            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("unregistered detector is not invoked")
        void unregisteredDetectorIsNotInvoked() {
            engine.registerPatternDetector("tenant-1", testDetector);
            engine.unregisterPatternDetector("tenant-1", testDetector);
            
            AepEngine.Event event = new AepEngine.Event("test.event", 
                Map.of("key", "value"), Map.of(), Instant.now());
            
            runPromise(() -> engine.process("tenant-1", event));
            
            assertThat(invocationCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("multiple detectors are all invoked")
        void multipleDetectorsAreAllInvoked() {
            AtomicInteger count2 = new AtomicInteger(0);
            AepEngine.PatternDetector detector2 = (tenantId, event, patterns) -> {
                count2.incrementAndGet();
                return Promise.of(List.of());
            };
            
            engine.registerPatternDetector("tenant-1", testDetector);
            engine.registerPatternDetector("tenant-1", detector2);
            
            AepEngine.Event event = new AepEngine.Event("test.event", 
                Map.of("key", "value"), Map.of(), Instant.now());
            
            runPromise(() -> engine.process("tenant-1", event));
            
            assertThat(invocationCount.get()).isEqualTo(1);
            assertThat(count2.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Closed Engine Behavior")
    class ClosedEngineTests {

        @Test
        @DisplayName("cannot register detector after engine closed")
        void cannotRegisterAfterClosed() {
            engine.close();
            
            assertThatThrownBy(() -> engine.registerPatternDetector("tenant-1", testDetector))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("cannot unregister detector after engine closed")
        void cannotUnregisterAfterClosed() {
            engine.registerPatternDetector("tenant-1", testDetector);
            engine.close();
            
            assertThatThrownBy(() -> engine.unregisterPatternDetector("tenant-1", testDetector))
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
