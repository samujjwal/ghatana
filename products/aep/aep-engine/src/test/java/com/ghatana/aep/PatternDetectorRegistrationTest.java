/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
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
    void setUp() { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        invocationCount = new AtomicInteger(0); // GH-90000
        
        // Create a simple test detector that just counts invocations
        testDetector = (tenantId, event, patterns) -> { // GH-90000
            invocationCount.incrementAndGet(); // GH-90000
            return Promise.of(List.of(new AepEngine.Detection( // GH-90000
                "test-pattern",
                event.type(), // GH-90000
                0.9,
                Map.of("detectedAt", Instant.now().toString()), // GH-90000
                Instant.now() // GH-90000
            )));
        };
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (engine != null) { // GH-90000
            engine.close(); // GH-90000
        }
    }

    @Nested
    @DisplayName("registerPatternDetector()")
    class RegisterTests {

        @Test
        @DisplayName("registers detector successfully")
        void registersDetectorSuccessfully() { // GH-90000
            engine.registerPatternDetector("tenant-1", testDetector); // GH-90000
            
            // Registration should not throw
            assertThat(invocationCount.get()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("throws on null detector")
        void throwsOnNullDetector() { // GH-90000
            assertThatThrownBy(() -> engine.registerPatternDetector("tenant-1", null)) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("detector");
        }

        @Test
        @DisplayName("throws on null tenantId")
        void throwsOnNullTenantId() { // GH-90000
            assertThatThrownBy(() -> engine.registerPatternDetector(null, testDetector)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("allows multiple detectors for same tenant")
        void allowsMultipleDetectorsForSameTenant() { // GH-90000
            AepEngine.PatternDetector detector2 = (tenantId, event, patterns) -> Promise.of(List.of()); // GH-90000
            
            engine.registerPatternDetector("tenant-1", testDetector); // GH-90000
            engine.registerPatternDetector("tenant-1", detector2); // GH-90000
            
            // Should not throw
        }

        @Test
        @DisplayName("allows detectors for different tenants")
        void allowsDetectorsForDifferentTenants() { // GH-90000
            engine.registerPatternDetector("tenant-1", testDetector); // GH-90000
            engine.registerPatternDetector("tenant-2", testDetector); // GH-90000
            
            // Should not throw
        }
    }

    @Nested
    @DisplayName("unregisterPatternDetector()")
    class UnregisterTests {

        @Test
        @DisplayName("removes registered detector")
        void removesRegisteredDetector() { // GH-90000
            engine.registerPatternDetector("tenant-1", testDetector); // GH-90000
            engine.unregisterPatternDetector("tenant-1", testDetector); // GH-90000
            
            // Should not throw
        }

        @Test
        @DisplayName("throws on null detector")
        void throwsOnNullDetector() { // GH-90000
            assertThatThrownBy(() -> engine.unregisterPatternDetector("tenant-1", null)) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("detector");
        }

        @Test
        @DisplayName("throws on null tenantId")
        void throwsOnNullTenantId() { // GH-90000
            assertThatThrownBy(() -> engine.unregisterPatternDetector(null, testDetector)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("handles unregister of non-existent detector gracefully")
        void handlesUnregisterOfNonExistentDetector() { // GH-90000
            // Should not throw even if detector was never registered
            engine.unregisterPatternDetector("tenant-1", testDetector); // GH-90000
        }
    }

    @Nested
    @DisplayName("Detector Invocation")
    class InvocationTests {

        @Test
        @DisplayName("registered detector is invoked during event processing")
        void registeredDetectorIsInvokedDuringProcessing() { // GH-90000
            engine.registerPatternDetector("tenant-1", testDetector); // GH-90000
            
            AepEngine.Event event = new AepEngine.Event("test.event",  // GH-90000
                Map.of("key", "value"), Map.of(), Instant.now()); // GH-90000
            
            AepEngine.ProcessingResult result = runPromise(() -> engine.process("tenant-1", event)); // GH-90000
            
            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(invocationCount.get()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("detector is not invoked for different tenant")
        void detectorNotInvokedForDifferentTenant() { // GH-90000
            engine.registerPatternDetector("tenant-1", testDetector); // GH-90000
            
            AepEngine.Event event = new AepEngine.Event("test.event",  // GH-90000
                Map.of("key", "value"), Map.of(), Instant.now()); // GH-90000
            
            runPromise(() -> engine.process("tenant-2", event)); // GH-90000
            
            assertThat(invocationCount.get()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("detector errors do not fail event processing")
        void detectorErrorsDoNotFailEventProcessing() { // GH-90000
            AepEngine.PatternDetector failingDetector = (tenantId, event, patterns) -> { // GH-90000
                throw new RuntimeException("Detector failed");
            };
            
            engine.registerPatternDetector("tenant-1", failingDetector); // GH-90000
            
            AepEngine.Event event = new AepEngine.Event("test.event",  // GH-90000
                Map.of("key", "value"), Map.of(), Instant.now()); // GH-90000
            
            AepEngine.ProcessingResult result = runPromise(() -> engine.process("tenant-1", event)); // GH-90000
            
            // Event processing should succeed even if detector fails
            assertThat(result.success()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("unregistered detector is not invoked")
        void unregisteredDetectorIsNotInvoked() { // GH-90000
            engine.registerPatternDetector("tenant-1", testDetector); // GH-90000
            engine.unregisterPatternDetector("tenant-1", testDetector); // GH-90000
            
            AepEngine.Event event = new AepEngine.Event("test.event",  // GH-90000
                Map.of("key", "value"), Map.of(), Instant.now()); // GH-90000
            
            runPromise(() -> engine.process("tenant-1", event)); // GH-90000
            
            assertThat(invocationCount.get()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("multiple detectors are all invoked")
        void multipleDetectorsAreAllInvoked() { // GH-90000
            AtomicInteger count2 = new AtomicInteger(0); // GH-90000
            AepEngine.PatternDetector detector2 = (tenantId, event, patterns) -> { // GH-90000
                count2.incrementAndGet(); // GH-90000
                return Promise.of(List.of()); // GH-90000
            };
            
            engine.registerPatternDetector("tenant-1", testDetector); // GH-90000
            engine.registerPatternDetector("tenant-1", detector2); // GH-90000
            
            AepEngine.Event event = new AepEngine.Event("test.event",  // GH-90000
                Map.of("key", "value"), Map.of(), Instant.now()); // GH-90000
            
            runPromise(() -> engine.process("tenant-1", event)); // GH-90000
            
            assertThat(invocationCount.get()).isEqualTo(1); // GH-90000
            assertThat(count2.get()).isEqualTo(1); // GH-90000
        }
    }

    @Nested
    @DisplayName("Closed Engine Behavior")
    class ClosedEngineTests {

        @Test
        @DisplayName("cannot register detector after engine closed")
        void cannotRegisterAfterClosed() { // GH-90000
            engine.close(); // GH-90000
            
            assertThatThrownBy(() -> engine.registerPatternDetector("tenant-1", testDetector)) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
        }

        @Test
        @DisplayName("cannot unregister detector after engine closed")
        void cannotUnregisterAfterClosed() { // GH-90000
            engine.registerPatternDetector("tenant-1", testDetector); // GH-90000
            engine.close(); // GH-90000
            
            assertThatThrownBy(() -> engine.unregisterPatternDetector("tenant-1", testDetector)) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
        }
    }
}
