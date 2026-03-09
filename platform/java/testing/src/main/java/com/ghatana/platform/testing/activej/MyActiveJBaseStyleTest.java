package com.ghatana.platform.testing.activej;

import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Example test demonstrating ActiveJ Promise testing with custom thread name.
 *
 * <h2>Purpose</h2>
 * Template for writing tests with ActiveJ Promise while maintaining
 * custom thread naming for diagnostics and debugging.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Extends EventloopTestBase for managed eventloop</li>
 *   <li>Custom thread name prefix for test identification</li>
 *   <li>Uses runPromise() for Promise execution (NOT .getResult())</li>
 *   <li>Access to performance inspector via inspector().pretty()</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * {@code
 * class MyTestClass extends EventloopTestBase {
 *
 *     @Override
 *     protected String threadName() {
 *         return "my-test-" + super.threadName();
 *     }
 *
 *     @Test
 *     void shouldProcessPromise() {
 *         // GIVEN: A promise chain
 *         Promise<Integer> promise = Promise.of(10)
 *             .map(x -> x + 5)
 *             .map(x -> x * 2); // Result: (10+5)*2 = 30
 *
 *         // WHEN: Execute promise
 *         Integer result = runPromise(() -> promise);
 *
 *         // THEN: Verify result and inspect performance
 *         assertEquals(30, result);
 *         System.out.println(inspector().pretty());
 *     }
 * }
 * }
 *
 * <h2>Thread Naming Benefits</h2>
 * <ul>
 *   <li><b>Diagnostics</b>: Thread dumps show test context (e.g., "base-style-eventloop-1")</li>
 *   <li><b>Debugging</b>: IDE debugger shows meaningful thread names</li>
 *   <li><b>Logging</b>: Log output identifies which test produced the log</li>
 *   <li><b>Performance</b>: Profilers can correlate metrics to test runs</li>
 * </ul>
 *
 * <h2>Example Output</h2>
 * {@code
 * // Test passes with custom thread name visible in thread dumps
 * // Thread name: "base-style-eventloop-1"
 * // Performance inspector output shows:
 * // - Promise creation time
 * // - Execution time per map()
 * // - Total execution time
 * // - GC pressure (if any)
 * }
 *
 * <h2>Inspector Output</h2>
 * {@code
 * System.out.println(inspector().pretty())
 * // Output:
 * // Task state: COMPLETED
 * // Promises executed: 1
 * // Total time: 0.123ms
 * // GC collections: 0
 * }
 *
 * @see EventloopTestBase Managed eventloop for tests
 * @see io.activej.promise.Promise ActiveJ promise implementation
 * @doc.type class
 * @doc.layer testing
 * @doc.purpose example test demonstrating ActiveJ promises with custom thread naming
 * @doc.pattern test-template activej-testing base-class-extension
 */
class MyActiveJBaseStyleTest extends EventloopTestBase {

  @Override
  protected String threadName() {
    return "base-style-" + super.threadName();
  }

  @Test
  void pipeline() {
    Integer result = runPromise(() ->
        Promise.of(4).map(x -> x + 1).map(x -> x * 2) // (4+1)*2 = 10
    );
    assertEquals(10, result);
    System.out.println(inspector().pretty());
  }
}
