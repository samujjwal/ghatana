package com.ghatana.platform.testing.internal.grpc;

import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Internal gRPC stream observer test utility.
 *
 * @param <T> response type
 * @doc.type class
 * @doc.purpose Internal gRPC stream observer test utility
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

    public T getResponse() {
        if (responses.isEmpty()) {
            throw new AssertionError("No response received");
        }
        return responses.get(0);
    }

    public List<T> getResponses() {
        return new ArrayList<>(responses);
    }

    public Throwable getError() {
        return error;
    }

    public boolean isCompleted() {
        return completed;
    }

    public T awaitResponse(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    public void awaitCompletion(long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        try {
            future.get(timeout, unit);
        } catch (ExecutionException e) {
            // Ignore execution exceptions for completion checks.
        }
    }
}