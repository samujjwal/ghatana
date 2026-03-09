package com.ghatana.platform.testing.eventloop.grpc;

import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A StreamObserver implementation for testing gRPC services.
 * Captures responses and errors for assertions in tests.
 *
 * @param <T> The type of the response
 
 *
 * @doc.type class
 * @doc.purpose Grpc test observer
 * @doc.layer core
 * @doc.pattern Component
*/
public class GrpcTestObserver<T> implements StreamObserver<T> {
    private final CompletableFuture<T> future = new CompletableFuture<>();
    private final List<T> responses = new ArrayList<>();
    private volatile Throwable error;
    private volatile boolean completed;

    @Override
    public void onNext(T value) {
        responses.add(value);
        future.complete(value);
    }

    @Override
    public void onError(Throwable t) {
        this.error = t;
        future.completeExceptionally(t);
    }

    @Override
    public void onCompleted() {
        this.completed = true;
        if (responses.isEmpty()) {
            future.complete(null);
        }
    }

    /**
     * Gets the first response received.
     *
     * @return The first response
     * @throws AssertionError if no response was received
     */
    public T getResponse() {
        if (responses.isEmpty()) {
            throw new AssertionError("No response received");
        }
        return responses.get(0);
    }

    /**
     * Gets all responses received.
     *
     * @return List of all responses
     */
    public List<T> getResponses() {
        return new ArrayList<>(responses);
    }

    /**
     * Gets the error if one occurred.
     *
     * @return The error or null if none occurred
     */
    public Throwable getError() {
        return error;
    }

    /**
     * Checks if the stream has completed.
     *
     * @return true if onCompleted was called
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Waits for the first response with a timeout.
     *
     * @param timeout The timeout duration
     * @param unit    The time unit of the timeout
     * @return The first response
     * @throws InterruptedException if the current thread was interrupted
     * @throws TimeoutException     if the wait timed out
     * @throws ExecutionException   if the computation threw an exception
     */
    public T awaitResponse(long timeout, TimeUnit unit) 
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    /**
     * Waits for the stream to complete with a timeout.
     *
     * @param timeout The timeout duration
     * @param unit    The time unit of the timeout
     * @throws InterruptedException if the current thread was interrupted
     * @throws TimeoutException     if the wait timed out
     */
    public void awaitCompletion(long timeout, TimeUnit unit) 
            throws InterruptedException, TimeoutException {
        try {
            future.get(timeout, unit);
        } catch (ExecutionException e) {
            // Ignore execution exceptions for completion check
        }
    }
}
