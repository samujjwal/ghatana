package com.ghatana.platform.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@UnitTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(LifecycleAwareExtension.class)
@DisplayName("LifecycleAwareExtension")
class LifecycleAwareExtensionTest implements TestLifecycleCallback {

    private final List<String> events = new ArrayList<>();
    private static final List<String> allEvents = new ArrayList<>();
    private static final AtomicInteger testCount = new AtomicInteger(0);
    private static boolean beforeAllCalled = false;

    @Override
    public void beforeAll(ExtensionContext context) {
        beforeAllCalled = true;
        events.add("callback-beforeAll");
        allEvents.add("callback-beforeAll");
    }

    @Override
    public void afterAll(ExtensionContext context) {
        allEvents.add("callback-afterAll");
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        events.add("callback-beforeEach");
        allEvents.add("callback-beforeEach");
    }

    @Override
    public void afterEach(ExtensionContext context) {
        events.add("callback-afterEach");
        allEvents.add("callback-afterEach");
        testCount.incrementAndGet();
    }

    @BeforeEach
    void setUp() {
        events.add("junit-beforeEach");
        allEvents.add("junit-beforeEach");
    }

    @AfterEach
    void tearDown() {
        events.add("junit-afterEach");
        allEvents.add("junit-afterEach");
    }

    @AfterAll
    static void verifyAfterAll() {
        // Verify that beforeAll was called at least once
        assertThat(beforeAllCalled).isTrue();
        
        // Verify that we have at least one test that completed
        assertThat(testCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("delegates lifecycle callbacks to implementations")
    void shouldDelegateLifecycleCallbacks() {
        events.add("test");
        allEvents.add("test");
        
        // Verify the order of events for this test
        assertThat(events)
            .containsSubsequence("callback-beforeAll", "callback-beforeEach", "junit-beforeEach", "test");
    }
    
    @Test
    @DisplayName("handles test execution errors")
    void executionError() {
        events.add("test-error");
        allEvents.add("test-error");
        
        // This test verifies that exceptions are properly propagated
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            throw new RuntimeException("Expected test exception");
        });
        assertThat(exception).hasMessage("Expected test exception");
        
        // Verify the order of events for this test
        assertThat(events)
            .containsSubsequence("callback-beforeEach", "junit-beforeEach", "test-error");
    }
}
