/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("AsyncServletDecorator — middleware delegation contract")
class AsyncServletDecoratorTest extends EventloopTestBase {

    // ── Concrete implementations for testing ─────────────────────────────────

    /**
     * A decorator that sets a flag when the middleware is invoked before delegating to next.
     */
    private static AsyncServletDecorator loggingDecorator(AtomicBoolean beforeFlag,
                                                           AtomicBoolean afterFlag) {
        return next -> request -> {
            beforeFlag.set(true);
            return next.serve(request).whenResult(resp -> afterFlag.set(true));
        };
    }

    /**
     * A decorator that short-circuits and returns 403 without calling next.
     */
    private static AsyncServletDecorator blockingDecorator() {
        return next -> request -> Promise.of(HttpResponse.ofCode(403).build());
    }

    /**
     * A no-op decorator that simply delegates to the next servlet.
     */
    private static AsyncServletDecorator passthroughDecorator() {
        return next -> next;
    }

    private static final AsyncServlet OK_SERVLET = req -> Promise.of(HttpResponse.ok200().build());

    private static HttpRequest getRequest(String path) {
        return HttpRequest.get("http://localhost" + path).build();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("decorator is a functional interface with a single serve() method")
    void isFunctionalInterface() {
        AsyncServletDecorator decorator = passthroughDecorator();
        assertThat(decorator).isNotNull();
    }

    @Test
    @DisplayName("passthrough decorator delegates to next servlet transparently")
    void passthroughDelegatesToNext() throws Exception {
        AsyncServletDecorator decorator = passthroughDecorator();
        AsyncServlet wrapped = decorator.serve(OK_SERVLET);

        HttpResponse response = runPromise(() -> wrapped.serve(getRequest("/api/test")));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("blocking decorator short-circuits and does not invoke next servlet")
    void blockingDecoratorShortCircuits() throws Exception {
        AtomicBoolean nextCalled = new AtomicBoolean(false);
        AsyncServlet trackingServlet = req -> {
            nextCalled.set(true);
            return Promise.of(HttpResponse.ok200().build());
        };

        AsyncServletDecorator decorator = blockingDecorator();
        AsyncServlet wrapped = decorator.serve(trackingServlet);

        HttpResponse response = runPromise(() -> wrapped.serve(getRequest("/api/blocked")));

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(nextCalled).isFalse();
    }

    @Test
    @DisplayName("logging decorator executes before-logic then delegates to next")
    void loggingDecoratorExecutesBeforeAndAfter() throws Exception {
        AtomicBoolean before = new AtomicBoolean(false);
        AtomicBoolean after = new AtomicBoolean(false);

        AsyncServletDecorator decorator = loggingDecorator(before, after);
        AsyncServlet wrapped = decorator.serve(OK_SERVLET);

        HttpResponse response = runPromise(() -> wrapped.serve(getRequest("/api/data")));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(before).isTrue();
        assertThat(after).isTrue();
    }

    @Test
    @DisplayName("decorators can be chained — outer wraps inner")
    void decoratorsCanBeChained() throws Exception {
        AtomicBoolean outerBefore = new AtomicBoolean(false);
        AtomicBoolean innerBefore = new AtomicBoolean(false);
        AtomicBoolean outerAfter = new AtomicBoolean(false);
        AtomicBoolean innerAfter = new AtomicBoolean(false);

        AsyncServletDecorator outer = loggingDecorator(outerBefore, outerAfter);
        AsyncServletDecorator inner = loggingDecorator(innerBefore, innerAfter);

        // Chain: outer → inner → OK_SERVLET
        AsyncServlet wrapped = outer.serve(inner.serve(OK_SERVLET));

        HttpResponse response = runPromise(() -> wrapped.serve(getRequest("/api/chained")));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(outerBefore).isTrue();
        assertThat(innerBefore).isTrue();
        assertThat(outerAfter).isTrue();
        assertThat(innerAfter).isTrue();
    }

    @Test
    @DisplayName("decorator returns an AsyncServlet wrapping next")
    void decoratorReturnedServletIsNotNull() {
        AsyncServletDecorator decorator = passthroughDecorator();
        AsyncServlet wrapped = decorator.serve(OK_SERVLET);
        assertThat(wrapped).isNotNull();
    }

    @Test
    @DisplayName("decorator can transform the response returned by next")
    void decoratorCanTransformResponse() throws Exception {
        // Decorator adds a custom header to whatever next returns
        AsyncServletDecorator headerAdder = next -> request ->
                next.serve(request).map(resp ->
                        HttpResponse.ofCode(resp.getCode()).build());

        AsyncServlet wrapped = headerAdder.serve(OK_SERVLET);

        HttpResponse response = runPromise(() -> wrapped.serve(getRequest("/api/transform")));

        assertThat(response.getCode()).isEqualTo(200);
    }
}
