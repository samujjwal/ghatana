package com.ghatana.platform.testing.activej;

import com.ghatana.platform.testing.activej.EventloopTestUtil;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Example test demonstrating EventloopExtension (JUnit 5 extension approach).
 *
 * <h2>Purpose</h2>
 * Template for writing ActiveJ Promise tests using JUnit 5 extension
 * pattern with parameter injection (non-inheritance approach).
 *
 * <h2>Three Approaches Compared</h2>
 * <table>
 *   <tr>
 *     <th>Approach</th>
 *     <th>Base Class</th>
 *     <th>Extension</th>
 *     <th>Injection</th>
 *     <th>Best For</th>
 *   </tr>
 *   <tr>
 *     <td>Inheritance (MyActiveJBaseStyleTest)</td>
 *     <td>extends EventloopTestBase</td>
 *     <td>N/A</td>
 *     <td>Inherited methods</td>
 *     <td>Simple tests, single inheritance</td>
 *   </tr>
 *   <tr>
 *     <td>Delegation (MyBaseActiveJTest)</td>
 *     <td>extends EventloopTestBase</td>
 *     <td>N/A</td>
 *     <td>runner field</td>
 *     <td>Explicit runner context</td>
 *   </tr>
 *   <tr>
 *     <td>Extension (MyActiveJTest - THIS)</td>
 *     <td>None</td>
 *     <td>@ExtendWith(EventloopExtension.class)</td>
 *     <td>Parameter injection</td>
 *     <td>Tests with other extensions, no inheritance</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage Pattern</h2>
 * {@code
 * @ExtendWith(EventloopExtension.class)
 * class MyTest {
 *
 *     @Test
 *     void shouldExecutePromiseChain(
 *         EventloopTestUtil.EventloopRunner runner) {
 *         // GIVEN: Promise chain
 *         Promise<Integer> promise = Promise.of(7)
 *             .map(x -> x * 2)
 *             .map(x -> x + 1); // Result: 7*2 + 1 = 15
 *
 *         // WHEN: Execute via injected runner
 *         Integer result = runner.runPromise(() -> promise);
 *
 *         // THEN: Assert and inspect
 *         assertEquals(15, result);
 *         System.out.println(runner.inspector().pretty());
 *     }
 * }
 * }
 *
 * <h2>Advantages of Extension Approach</h2>
 * <ul>
 *   <li><b>No Inheritance</b>: Tests can extend other base classes</li>
 *   <li><b>Composable</b>: Can combine with other JUnit 5 extensions</li>
 *   <li><b>Explicit</b>: Parameter injection makes dependencies clear</li>
 *   <li><b>Flexible</b>: Extension lifecycle independent of test class hierarchy</li>
 * </ul>
 *
 * <h2>When to Use This Pattern</h2>
 * <ul>
 *   <li><b>Multiple Extensions</b>: Need EventloopExtension + other extensions</li>
 *   <li><b>Inheritance Conflict</b>: Test class needs different base class</li>
 *   <li><b>Explicit Dependencies</b>: Prefer parameter injection to inheritance</li>
 *   <li><b>Composition Over Inheritance</b>: Design principle preference</li>
 * </ul>
 *
 * <h2>Runner Capabilities</h2>
 * Injected EventloopTestUtil.EventloopRunner provides:
 * <ul>
 *   <li><b>runPromise(supplier)</b>: Execute Promise, block and return result</li>
 *   <li><b>inspector()</b>: Access performance metrics and diagnostics</li>
 *   <li><b>eventloop()</b>: Direct eventloop access if needed</li>
 * </ul>
 *
 * <h2>Test Lifecycle</h2>
 * {@code
 * Test Execution:
 * 1. EventloopExtension.beforeEach() - Creates managed eventloop
 * 2. Parameter resolution - Injects EventloopRunner
 * 3. Test method execution - Uses runner.runPromise()
 * 4. EventloopExtension.afterEach() - Cleans up eventloop
 * }
 *
 * <h2>Performance Characteristics</h2>
 * Same as inheritance approaches:
 * <ul>
 *   <li><b>Setup</b>: ~5-10ms eventloop creation</li>
 *   <li><b>Promise execution</b>: <1ms (in-process)</li>
 *   <li><b>Teardown</b>: ~5ms eventloop shutdown</li>
 * </ul>
 *
 * @see EventloopExtension JUnit 5 extension for eventloop
 * @see EventloopTestUtil.EventloopRunner runner implementation
 * @see io.activej.promise.Promise ActiveJ promise
 * @doc.type class
 * @doc.layer testing
 * @doc.purpose example test using JUnit 5 extension for eventloop
 * @doc.pattern test-template activej-testing junit5-extension parameter-injection
 *
 * NOTE: This is an example/template file. To use, copy to src/test/java and uncomment @ExtendWith.
 */
// @ExtendWith(EventloopExtension.class)  // Uncomment when using in actual test
class MyActiveJTest {

  @Test
  void runPromisePipeline(EventloopTestUtil.EventloopRunner runner) {
    Integer result = runner.runPromise(() ->
      Promise.of(10).map(x -> x + 5).map(x -> x * 3)
    );
    assertEquals(45, result);

    System.out.println(runner.inspector().pretty());
  }
}
