/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.activej.async;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    public void testRunBlocking() {
        String result = runPromise(() -> AsyncBridge.runBlocking(() -> {
            // Simulate blocking operation
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "result";
        }));

        assertThat(result).isEqualTo("result");
    }

    @Test
    public void testRunBlockingWithException() {
        Eventloop eventloop = eventloop();
        
        assertThatThrownBy(() -> {
            runPromise(() -> AsyncBridge.runBlocking(() -> {
                throw new RuntimeException("test error");
            }));
        }).hasMessageContaining("test error");
        clearFatalError();
    }

    @Test
    public void testFromFuture() {
        Eventloop eventloop = eventloop();
        
        CompletableFuture<String> future = CompletableFuture.completedFuture("future-result");
        Promise<String> promise = AsyncBridge.fromFuture(future);

        String result = runPromise(() -> promise);
        assertThat(result).isEqualTo("future-result");
    }

    @Test
    public void testFromFutureWithException() {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("future error"));
        
        assertThatThrownBy(() -> {
            runPromise(() -> AsyncBridge.fromFuture(future));
        }).hasMessageContaining("future error");
        clearFatalError();
    }
    @Test
    public void testToFuture() {
        Promise<String> promise = Promise.of("promise-result");
        CompletableFuture<String> future = AsyncBridge.toFuture(promise);

        // Run eventloop to complete promise
        runPromise(() -> promise);
        
        assertThat(future).isCompletedWithValue("promise-result");
    }

    @Test
    public void testToFutureWithException() {
        Promise<String> promise = Promise.ofException(new RuntimeException("promise error"));
        CompletableFuture<String> future = AsyncBridge.toFuture(promise);

        // Don't need to run the eventloop for an already exceptional promise
        // runPromise(() -> promise);
        
        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::join)
            .hasMessageContaining("promise error");
        // No need to clear fatal error since we didn't run the promise
    }

    @Test
    public void testRunBlockingToFuture() {
        CompletableFuture<String> future = AsyncBridge.runBlockingToFuture(() -> {
            return "blocking-result";
        });

        assertThat(future.join()).isEqualTo("blocking-result");
    }
}
