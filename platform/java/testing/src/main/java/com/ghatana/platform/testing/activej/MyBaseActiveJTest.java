package com.ghatana.platform.testing.activej;

import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Example test demonstrating EventloopTestBase with runner pattern.
 *
 * <h2>Purpose</h2>
 * Template for writing ActiveJ Promise tests using runner delegate
 * instead of direct inheritance method calls.
 *
 * <h2>Key Differences from MyActiveJBaseStyleTest</h2>
 * <ul>
 *   <li><b>This class</b>: Uses runner.runPromise() and runner.inspector()</li>
 *   <li><b>Base style</b>: Uses inherited runPromise() and inspector()</li>
 *   <li>Both approaches equivalent - choose based on code style preference</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * {@code
 * class MyTest extends EventloopTestBase {
 *
 *     @Test
 *     void shouldExecutePromiseChain() {
 *         // GIVEN: A promise transformation
 *         Promise<Integer> promise = Promise.of(5)
 *             .map(x -> x * 2)
 *             .map(x -> x + 10); // Result: 5*2 + 10 = 20
 *
 *         // WHEN: Execute via runner
 *         Integer result = runner.runPromise(() -> promise);
 *
 *         // THEN: Assert and inspect performance
 *         assertEquals(20, result);
 *         System.out.println(runner.inspector().pretty());
 *     }
 * }
 * }
 *
 * <h2>Runner Delegation Model</h2>
 * {@code
 * EventloopTestBase provides protected runner field:
 * - runner: PromiseTestRunner instance
 * - runner.runPromise(supplier) - Execute promise, return result
 * - runner.inspector() - Access performance metrics
 *
 * Alternative: call inherited methods directly
 * - runPromise(supplier) - Same behavior
 * - inspector() - Same behavior
 * }
 *
 * <h2>When to Use This Pattern</h2>
 * <ul>
 *   <li><b>Explicit Context</b>: When runner usage makes code intent clearer</li>
 *   <li><b>Multiple Runners</b>: When test needs multiple eventloop runners</li>
 *   <li><b>Composition</b>: When delegating to helper methods</li>
 *   <li><b>Code Style</b>: Preference for explicit vs. inherited method calls</li>
 * </ul>
 *
 * <h2>Performance Inspector Example</h2>
 * {@code
 * runner.inspector().pretty()
 * // Output:
 * // Promise state: COMPLETED
 * // Execution time: 0.045ms
 * // GC events: 0
 * // Eventloop cycles: 1
 * }
 *
 * @see EventloopTestBase Managed eventloop with runner
 * @see io.activej.promise.Promise ActiveJ promise
 * @doc.type class
 * @doc.layer testing
 * @doc.purpose example test using runner delegate for promise execution
 * @doc.pattern test-template activej-testing delegation-pattern
 */
class MyBaseActiveJTest extends EventloopTestBase {

  @Test
  void runPromisePipeline() {
    Integer result = runner.runPromise(() ->
      Promise.of(2).map(x -> x * 10)
    );
    assertEquals(20, result);
    System.out.println(runner.inspector().pretty());
  }
}
