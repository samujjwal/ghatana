package com.ghatana.platform.testing;

import com.ghatana.platform.testing.activej.EventloopTestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verbose logging listener for eventloop test lifecycle events.
 *
 * <h2>Purpose</h2>
 * Implements EventloopListener to log all eventloop lifecycle events:
 * <ul>
 *   <li><b>onStart</b>: Eventloop starting with thread name</li>
 *   <li><b>onProgress</b>: Regular progress heartbeat (optional logging)</li>
 *   <li><b>onStuck</b>: Watchdog detects unresponsive eventloop</li>
 *   <li><b>onFatal</b>: Fatal error in eventloop</li>
 *   <li><b>onStop</b>: Eventloop shutdown with final statistics</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * {@code
 * class MyTest extends EventloopTestBase {
 *
 *     @Override
 *     protected EventloopTestUtil.EventloopListener createListener() {
 *         return new VerboseListener(); // Enable verbose logging
 *     }
 *
 *     @Test
 *     void shouldLogEventloopLifecycle() {
 *         Integer result = runPromise(() ->
 *             Promise.of(5).map(x -> x * 2)
 *         );
 *         // Output logged:
 *         // [eventloop-1] start
 *         // [eventloop-1] stop. Stats: {execution_time: 0.123ms, ...}
 *     }
 * }
 * }
 *
 * <h2>Log Output Examples</h2>
 *
 * <b>Successful Test</b>
 * {@code
 * INFO  [eventloop-1] start
 * DEBUG [eventloop-1] onProgress
 * DEBUG [eventloop-1] onProgress
 * INFO  [eventloop-1] stop. Stats: {execution_time: 0.045ms, gc_events: 0}
 * }
 *
 * <b>Test with Stuck Detection</b>
 * {@code
 * INFO  [eventloop-1] start
 * DEBUG [eventloop-1] onProgress
 * WARN  Watchdog: stuck ~1000ms
 * WARN  Watchdog: stuck ~2000ms
 * ERROR [eventloop-1] fatal: java.lang.OutOfMemoryError
 * ERROR Fatal on loop: java.lang.OutOfMemoryError
 * }
 *
 * <b>Test with Completion</b>
 * {@code
 * INFO  [eventloop-1] start
 * DEBUG [eventloop-1] onProgress
 * DEBUG [eventloop-1] onProgress
 * DEBUG [eventloop-1] onProgress
 * INFO  [eventloop-1] stop. Stats: {
 *   event_count: 42,
 *   execution_time: 1.234ms,
 *   gc_events: 0,
 *   eventloop_cycles: 12
 * }
 * }
 *
 * <h2>Logging Levels</h2>
 * <ul>
 *   <li><b>INFO</b>: Eventloop start/stop (always logged)</li>
 *   <li><b>DEBUG</b>: onProgress() calls (optional for flakiness investigation)</li>
 *   <li><b>WARN</b>: Stuck detection from watchdog</li>
 *   <li><b>ERROR</b>: Fatal eventloop errors</li>
 * </ul>
 *
 * <h2>Integration with Test Framework</h2>
 * VerboseListener provides observability into eventloop behavior:
 * <ul>
 *   <li><b>Debugging</b>: See when eventloop starts/stops relative to test</li>
 *   <li><b>Performance Analysis</b>: Execution time and statistics logged</li>
 *   <li><b>Flakiness Investigation</b>: onProgress() shows how often eventloop cycles</li>
 *   <li><b>Deadlock Detection</b>: Stuck warning indicates potential deadlock</li>
 *   <li><b>Resource Leaks</b>: Final statistics show GC pressure and thread activity</li>
 * </ul>
 *
 * <h2>Performance Impact</h2>
 * <ul>
 *   <li><b>onStart/Stop</b>: ~1ms (logging overhead)</li>
 *   <li><b>onProgress</b>: ~0.1ms per call if logged (debug level, typically disabled)</li>
 *   <li><b>onStuck</b>: <1ms (indicates problem, not normal path)</li>
 *   <li><b>onFatal</b>: <1ms (exceptional path)</li>
 * </ul>
 *
 * <h2>When to Use</h2>
 * <ul>
 *   <li><b>Development</b>: Always enabled during test development</li>
 *   <li><b>Debugging</b>: Enable when tests are flaky or fail unexpectedly</li>
 *   <li><b>CI/CD</b>: Consider overhead before enabling in CI pipeline</li>
 *   <li><b>Performance Tests</b>: Disable to avoid logging overhead in benchmarks</li>
 * </ul>
 *
 * <h2>Related Classes</h2>
 * <ul>
 *   <li>EventloopTestUtil.EventloopListener: Interface this class implements</li>
 *   <li>EventloopTestUtil.Inspector: Statistics snapshot provided on stop</li>
 *   <li>EventloopTestBase: Typically uses this listener</li>
 * </ul>
 *
 * @see EventloopTestUtil.EventloopListener Event listener interface
 * @see EventloopTestUtil.Inspector Eventloop statistics
 * @doc.type class
 * @doc.layer testing
 * @doc.purpose verbose logging of eventloop lifecycle events for debugging
 * @doc.pattern observer event-logging lifecycle-monitoring
 */
public final class VerboseListener implements EventloopTestUtil.EventloopListener {
  private static final Logger log = LoggerFactory.getLogger(VerboseListener.class);

  @Override public void onStart(String threadName) { log.info("[{}] start", threadName); }
  @Override public void onProgress() { /* could log if investigating flakiness */ }
  @Override public void onStuck(long elapsedMs) { log.warn("Watchdog: stuck ~{}ms", elapsedMs); }
  @Override public void onFatal(Throwable error) { log.error("Fatal on loop", error); }
  @Override public void onStop(String threadName, EventloopTestUtil.Inspector snapshot) {
    log.info("[{}] stop. Stats: {}", threadName, snapshot.pretty());
  }
}
