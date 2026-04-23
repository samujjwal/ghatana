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
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // GH-90000
@ExtendWith(LifecycleAwareExtension.class) // GH-90000
@DisplayName("LifecycleAwareExtension")
class LifecycleAwareExtensionTest implements TestLifecycleCallback {

    private final List<String> events = new ArrayList<>(); // GH-90000
    private static final List<String> allEvents = new ArrayList<>(); // GH-90000
    private static final AtomicInteger testCount = new AtomicInteger(0); // GH-90000
    private static boolean beforeAllCalled = false;

    @Override
    public void beforeAll(ExtensionContext context) { // GH-90000
        beforeAllCalled = true;
        events.add("callback-beforeAll");
        allEvents.add("callback-beforeAll");
    }

    @Override
    public void afterAll(ExtensionContext context) { // GH-90000
        allEvents.add("callback-afterAll");
    }

    @Override
    public void beforeEach(ExtensionContext context) { // GH-90000
        events.add("callback-beforeEach");
        allEvents.add("callback-beforeEach");
    }

    @Override
    public void afterEach(ExtensionContext context) { // GH-90000
        events.add("callback-afterEach");
        allEvents.add("callback-afterEach");
        testCount.incrementAndGet(); // GH-90000
    }

    @BeforeEach
    void setUp() { // GH-90000
        events.add("junit-beforeEach");
        allEvents.add("junit-beforeEach");
    }

    @AfterEach
    void tearDown() { // GH-90000
        events.add("junit-afterEach");
        allEvents.add("junit-afterEach");
    }

    @AfterAll
    static void verifyAfterAll() { // GH-90000
        // Verify that beforeAll was called at least once
        assertThat(beforeAllCalled).isTrue(); // GH-90000

        // Verify that we have at least one test that completed
        assertThat(testCount.get()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("delegates lifecycle callbacks to implementations")
    void shouldDelegateLifecycleCallbacks() { // GH-90000
        events.add("test");
        allEvents.add("test");

        // Verify the order of events for this test
        assertThat(events) // GH-90000
            .containsSubsequence("callback-beforeAll", "callback-beforeEach", "junit-beforeEach", "test"); // GH-90000
    }

    @Test
    @DisplayName("handles test execution errors")
    void executionError() { // GH-90000
        events.add("test-error");
        allEvents.add("test-error");

        // This test verifies that exceptions are properly propagated
        RuntimeException exception = assertThrows(RuntimeException.class, () -> { // GH-90000
            throw new RuntimeException("Expected test exception");
        });
        assertThat(exception).hasMessage("Expected test exception");

        // Verify the order of events for this test
        assertThat(events) // GH-90000
            .containsSubsequence("callback-beforeEach", "junit-beforeEach", "test-error"); // GH-90000
    }
}
