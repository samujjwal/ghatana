package com.ghatana.platform.testing.eventloop;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Utility class for running test code in an Eventloop context.
 * 
 * @doc.type class
 * @doc.purpose Wrapper for testing code execution within ActiveJ Eventloop context
 * @doc.layer core
 * @doc.pattern Utility, Test Support
 */
public final class TestEventloop {
    private final Eventloop eventloop;

    private TestEventloop(Eventloop eventloop) {
        this.eventloop = eventloop;
    }

    /**
     * Creates a new TestEventloop instance with a new Eventloop.
     */
    public static TestEventloop create() {
        return new TestEventloop(Eventloop.create());
    }

    /**
     * Runs the provided supplier in the Eventloop and returns the result.
     * This method blocks until the operation is complete.
     */
    public static <T> T runInEventloop(Supplier<Promise<T>> supplier) {
        Eventloop eventloop = Eventloop.create();
        CompletableFuture<T> future = new CompletableFuture<>();
        
        // Set a timeout to prevent hanging
        eventloop.delay(10_000L, () -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("Test timed out after 10 seconds"));
                eventloop.keepAlive(false);
            }
        });
        
        eventloop.execute(() -> {
            try {
                Promise<T> promise = supplier.get();
                promise
                    .whenResult(future::complete)
                    .whenException(future::completeExceptionally);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        // Run the event loop
        eventloop.run();
        
        try {
            return future.get(11, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Test timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Test failed", e);
        }
    }

    /**
     * Runs the provided runnable in the Eventloop.
     * This method blocks until the operation is complete.
     */
    public <T> T run(Supplier<Promise<T>> supplier) {
        return runInEventloop(supplier);
    }
    
    public void run(Runnable runnable) {
        runInEventloop(() -> {
            try {
                runnable.run();
                return Promise.complete();
            } catch (Exception e) {
                return Promise.ofException(e);
            }
        });
    }

    /**
     * Returns the underlying Eventloop instance.
     */
    public Eventloop getEventloop() {
        return eventloop;
    }
}
