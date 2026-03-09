package com.ghatana.platform.testing.activej;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import com.ghatana.platform.testing.activej.EventloopTestUtil;

import java.time.Duration;

/**
 * JUnit 5 extension that manages an ActiveJ Eventloop thread per test.
 * Injects an {@link EventloopTestUtil.EventloopRunner} parameter when requested.
 * 
 * @doc.type class
 * @doc.purpose JUnit 5 extension for managing ActiveJ Eventloop lifecycle per test
 * @doc.layer core
 * @doc.pattern Extension, JUnit Plugin
*/
public final class EventloopExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
  private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(EventloopExtension.class);

  private final Duration timeout;
  private final Duration watchdog;
  private final EventloopTestUtil.EventloopListener listener;

  /** Default: 10s timeout, 2s watchdog, SLF4J logging. */
  public EventloopExtension() {
    this(Duration.ofSeconds(10), Duration.ofSeconds(2), new EventloopTestUtil.Slf4jListener());
  }

  public EventloopExtension(Duration timeout, Duration watchdog, EventloopTestUtil.EventloopListener listener) {
    this.timeout = timeout;
    this.watchdog = watchdog;
    this.listener = listener;
  }

  @Override
  public void beforeEach(ExtensionContext ctx) {
    String displayName = ctx.getDisplayName().replaceAll("\\s+", "_");
    String threadName = "eventloop-" + displayName;

    var runner = EventloopTestUtil.newRunnerBuilder()
        .timeout(timeout)
        .watchdogEvery(watchdog)
        .threadName(threadName)
        .listener(listener)
        .build();

    runner.start();
    ctx.getStore(NS).put("runner", runner);
  }

  @Override
  public void afterEach(ExtensionContext ctx) {
    var store = ctx.getStore(NS);
    EventloopTestUtil.EventloopRunner runner = store.remove("runner", EventloopTestUtil.EventloopRunner.class);
    if (runner != null) runner.close();
  }

  @Override
  public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) {
    return pc.getParameter().getType().equals(EventloopTestUtil.EventloopRunner.class);
  }

  @Override
  public Object resolveParameter(ParameterContext pc, ExtensionContext ec) {
    return ec.getStore(NS).get("runner", EventloopTestUtil.EventloopRunner.class);
  }
}
