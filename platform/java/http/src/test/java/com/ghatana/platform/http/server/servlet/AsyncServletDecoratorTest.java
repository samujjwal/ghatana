/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.http.server.servlet;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Unit tests for AsyncServletDecorator interface contract
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AsyncServletDecorator — middleware delegation contract [GH-90000]")
class AsyncServletDecoratorTest extends EventloopTestBase {

    // ── Concrete implementations for testing ─────────────────────────────────

    /**
     * A decorator that sets a flag when the middleware is invoked before delegating to next.
     */
    private static AsyncServletDecorator loggingDecorator(AtomicBoolean beforeFlag, // GH-90000
                                                           AtomicBoolean afterFlag) {
        return next -> request -> {
            beforeFlag.set(true); // GH-90000
            return next.serve(request).whenResult(resp -> afterFlag.set(true)); // GH-90000
        };
    }

    /**
     * A decorator that short-circuits and returns 403 without calling next.
     */
    private static AsyncServletDecorator blockingDecorator() { // GH-90000
        return next -> request -> Promise.of(HttpResponse.ofCode(403).build()); // GH-90000
    }

    /**
     * A no-op decorator that simply delegates to the next servlet.
     */
    private static AsyncServletDecorator passthroughDecorator() { // GH-90000
        return next -> next;
    }

    private static final AsyncServlet OK_SERVLET = req -> Promise.of(HttpResponse.ok200().build()); // GH-90000

    private static HttpRequest getRequest(String path) { // GH-90000
        return HttpRequest.get("http://localhost" + path).build(); // GH-90000
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("decorator is a functional interface with a single serve() method [GH-90000]")
    void isFunctionalInterface() { // GH-90000
        AsyncServletDecorator decorator = passthroughDecorator(); // GH-90000
        assertThat(decorator).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("passthrough decorator delegates to next servlet transparently [GH-90000]")
    void passthroughDelegatesToNext() throws Exception { // GH-90000
        AsyncServletDecorator decorator = passthroughDecorator(); // GH-90000
        AsyncServlet wrapped = decorator.serve(OK_SERVLET); // GH-90000

        HttpResponse response = runPromise(() -> wrapped.serve(getRequest("/api/test [GH-90000]")));

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("blocking decorator short-circuits and does not invoke next servlet [GH-90000]")
    void blockingDecoratorShortCircuits() throws Exception { // GH-90000
        AtomicBoolean nextCalled = new AtomicBoolean(false); // GH-90000
        AsyncServlet trackingServlet = req -> {
            nextCalled.set(true); // GH-90000
            return Promise.of(HttpResponse.ok200().build()); // GH-90000
        };

        AsyncServletDecorator decorator = blockingDecorator(); // GH-90000
        AsyncServlet wrapped = decorator.serve(trackingServlet); // GH-90000

        HttpResponse response = runPromise(() -> wrapped.serve(getRequest("/api/blocked [GH-90000]")));

        assertThat(response.getCode()).isEqualTo(403); // GH-90000
        assertThat(nextCalled).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("logging decorator executes before-logic then delegates to next [GH-90000]")
    void loggingDecoratorExecutesBeforeAndAfter() throws Exception { // GH-90000
        AtomicBoolean before = new AtomicBoolean(false); // GH-90000
        AtomicBoolean after = new AtomicBoolean(false); // GH-90000

        AsyncServletDecorator decorator = loggingDecorator(before, after); // GH-90000
        AsyncServlet wrapped = decorator.serve(OK_SERVLET); // GH-90000

        HttpResponse response = runPromise(() -> wrapped.serve(getRequest("/api/data [GH-90000]")));

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(before).isTrue(); // GH-90000
        assertThat(after).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("decorators can be chained — outer wraps inner [GH-90000]")
    void decoratorsCanBeChained() throws Exception { // GH-90000
        AtomicBoolean outerBefore = new AtomicBoolean(false); // GH-90000
        AtomicBoolean innerBefore = new AtomicBoolean(false); // GH-90000
        AtomicBoolean outerAfter = new AtomicBoolean(false); // GH-90000
        AtomicBoolean innerAfter = new AtomicBoolean(false); // GH-90000

        AsyncServletDecorator outer = loggingDecorator(outerBefore, outerAfter); // GH-90000
        AsyncServletDecorator inner = loggingDecorator(innerBefore, innerAfter); // GH-90000

        // Chain: outer → inner → OK_SERVLET
        AsyncServlet wrapped = outer.serve(inner.serve(OK_SERVLET)); // GH-90000

        HttpResponse response = runPromise(() -> wrapped.serve(getRequest("/api/chained [GH-90000]")));

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(outerBefore).isTrue(); // GH-90000
        assertThat(innerBefore).isTrue(); // GH-90000
        assertThat(outerAfter).isTrue(); // GH-90000
        assertThat(innerAfter).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("decorator returns an AsyncServlet wrapping next [GH-90000]")
    void decoratorReturnedServletIsNotNull() { // GH-90000
        AsyncServletDecorator decorator = passthroughDecorator(); // GH-90000
        AsyncServlet wrapped = decorator.serve(OK_SERVLET); // GH-90000
        assertThat(wrapped).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("decorator can transform the response returned by next [GH-90000]")
    void decoratorCanTransformResponse() throws Exception { // GH-90000
        // Decorator adds a custom header to whatever next returns
        AsyncServletDecorator headerAdder = next -> request ->
                next.serve(request).map(resp -> // GH-90000
                        HttpResponse.ofCode(resp.getCode()).build()); // GH-90000

        AsyncServlet wrapped = headerAdder.serve(OK_SERVLET); // GH-90000

        HttpResponse response = runPromise(() -> wrapped.serve(getRequest("/api/transform [GH-90000]")));

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }
}
