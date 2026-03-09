package com.ghatana.platform.testing.activej;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

/**
 * Small helpers for exercising ActiveJ AsyncServlets under a managed EventloopRunner.
 *
 * Usage:
 *   try (var runner = EventloopTestUtil.newRunnerBuilder().build()) {
 *     runner.start();
 *     HttpResponse resp = ActiveJServletTestUtil.serve(servlet, request, runner);
 *   }
 * 
 * @doc.type class
 * @doc.purpose Convenience helpers for testing ActiveJ AsyncServlets with managed Eventloop
 * @doc.layer core
 * @doc.pattern Utility, Test Support
 */
public final class ActiveJServletTestUtil {
  private ActiveJServletTestUtil() {}

  /** Serve the given request using the provided servlet on the runner's eventloop. */
  public static HttpResponse serve(AsyncServlet servlet, HttpRequest request, EventloopTestUtil.EventloopRunner runner) {
    return runner.runPromise(() -> servlet.serve(request));
  }

  /** Run an arbitrary Promise-producing task on the runner's eventloop and return the resolved value. */
  public static <T> T runPromise(EventloopTestUtil.EventloopRunner runner, java.util.concurrent.Callable<Promise<T>> task) {
    return runner.runPromise(task);
  }
}
