/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.core.activej.async;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for AsyncBridge utility.
 *
 * @doc.type class
 * @doc.purpose Test AsyncBridge blocking/future conversion utilities
 * @doc.layer core
 * @doc.pattern Test
 */
public class AsyncBridgeTest extends EventloopTestBase {

    @Test
    public void testRunBlocking() { // GH-90000
        String result = runPromise(() -> AsyncBridge.runBlocking(() -> { // GH-90000
            // Simulate blocking operation
            try {
                Thread.sleep(10); // GH-90000
            } catch (InterruptedException e) { // GH-90000
                Thread.currentThread().interrupt(); // GH-90000
            }
            return "result";
        }));

        assertThat(result).isEqualTo("result [GH-90000]");
    }

    @Test
    public void testRunBlockingWithException() { // GH-90000
        Eventloop eventloop = eventloop(); // GH-90000

        assertThatThrownBy(() -> { // GH-90000
            runPromise(() -> AsyncBridge.runBlocking(() -> { // GH-90000
                throw new RuntimeException("test error [GH-90000]");
            }));
        }).hasMessageContaining("test error [GH-90000]");
        clearFatalError(); // GH-90000
    }

    @Test
    public void testFromFuture() { // GH-90000
        Eventloop eventloop = eventloop(); // GH-90000

        CompletableFuture<String> future = CompletableFuture.completedFuture("future-result [GH-90000]");
        Promise<String> promise = AsyncBridge.fromFuture(future); // GH-90000

        String result = runPromise(() -> promise); // GH-90000
        assertThat(result).isEqualTo("future-result [GH-90000]");
    }

    @Test
    public void testFromFutureWithException() { // GH-90000
        CompletableFuture<String> future = new CompletableFuture<>(); // GH-90000
        future.completeExceptionally(new RuntimeException("future error [GH-90000]"));

        assertThatThrownBy(() -> { // GH-90000
            runPromise(() -> AsyncBridge.fromFuture(future)); // GH-90000
        }).hasMessageContaining("future error [GH-90000]");
        clearFatalError(); // GH-90000
    }
    @Test
    public void testToFuture() { // GH-90000
        Promise<String> promise = Promise.of("promise-result [GH-90000]");
        CompletableFuture<String> future = AsyncBridge.toFuture(promise); // GH-90000

        // Run eventloop to complete promise
        runPromise(() -> promise); // GH-90000

        assertThat(future).isCompletedWithValue("promise-result [GH-90000]");
    }

    @Test
    public void testToFutureWithException() { // GH-90000
        Promise<String> promise = Promise.ofException(new RuntimeException("promise error [GH-90000]"));
        CompletableFuture<String> future = AsyncBridge.toFuture(promise); // GH-90000

        // Don't need to run the eventloop for an already exceptional promise
        // runPromise(() -> promise); // GH-90000

        assertThat(future).isCompletedExceptionally(); // GH-90000
        assertThatThrownBy(future::join) // GH-90000
            .hasMessageContaining("promise error [GH-90000]");
        // No need to clear fatal error since we didn't run the promise
    }

    @Test
    public void testRunBlockingToFuture() { // GH-90000
        CompletableFuture<String> future = AsyncBridge.runBlockingToFuture(() -> { // GH-90000
            return "blocking-result";
        });

        assertThat(future.join()).isEqualTo("blocking-result [GH-90000]");
    }
}
