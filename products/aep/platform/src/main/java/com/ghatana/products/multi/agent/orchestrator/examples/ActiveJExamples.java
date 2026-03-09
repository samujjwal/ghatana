package com.ghatana.products.multi.agent.orchestrator.examples;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Examples of ActiveJ features including Promises and Eventloop.
 * 
 * <p>This class demonstrates the proper usage of ActiveJ async primitives.
 * 
 * <p>Usage:
 * <pre>{@code
 * // Run examples
 * ActiveJExamples.runPromiseExamples();
 * ActiveJExamples.runEventloopExamples();
 * }</pre>
 * 
 * @author Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ActiveJExamples {
    
    /**
     * Runs examples of Promise usage.
     */
    public static void runPromiseExamples() {
        log.info("=== Starting Promise Examples ===");
        
        // Example 1: Basic Promise
        Promise<String> promise = Promise.of("Hello, ActiveJ!");
        promise.whenResult(result -> log.info("Promise resolved with: {}", result));
        
        // Example 2: Promise chaining
        Promise.complete()
            .then(() -> {
                log.info("First operation");
                return Promise.of(42);
            })
            .then(number -> {
                log.info("Second operation with number: {}", number);
                return Promise.of("Processed: " + number);
            })
            .whenResult(result -> log.info("Final result: {}", result));
            
        // Example 3: Combining promises
        Promise<String> promise1 = Promise.of("Hello");
        Promise<String> promise2 = Promise.of("World");
        
        Promises.toList(promise1, promise2)
            .map(values -> String.join(" ", values))
            .whenResult(result -> log.info("Combined result: {}", result));
            
        log.info("=== Finished Promise Examples ===\n");
    }
    
    /**
     * Runs examples of Eventloop usage.
     */
    public static void runEventloopExamples() {
        log.info("=== Starting Eventloop Examples ===");
        
        Eventloop eventloop = Eventloop.create();
        
        // Example 1: Schedule a task to run after 1 second
        eventloop.schedule(Duration.ofSeconds(1).toMillis(), () ->
                log.info("This task runs after 1 second"));

        // Example 2: Run a background task via Promise.ofBlocking
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Promise.ofBlocking(executor, () -> {
                    log.info("Running a background task");
                    return "Background task completed";
                })
                .whenResult(result -> log.info("Background task result: {}", result));
        
        // Example 3: Convert CompletableFuture to Promise
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Completed";
        }, executor);
        
        Promise.ofFuture(future)
                .whenResult(result -> log.info("Converted future result: {}", result));

        eventloop.run();

        executor.shutdown();
        
        log.info("=== Finished Eventloop Examples ===\n");
    }
    
    /**
     * Main method to run all examples.
     * 
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        log.info("=== Starting ActiveJ Examples ===");
        
        // Run Promise examples
        runPromiseExamples();
        
        // Run Eventloop examples
        runEventloopExamples();
        
        log.info("=== ActiveJ Examples Completed ===");
    }
}
