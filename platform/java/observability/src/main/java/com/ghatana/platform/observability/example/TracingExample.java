package com.ghatana.platform.observability.example;

import com.ghatana.platform.observability.OpenTelemetryTracingProvider;
import com.ghatana.platform.observability.Traced;
import com.ghatana.platform.observability.TracingManager;
import com.ghatana.platform.observability.TracingProvider;
import com.ghatana.platform.observability.TracingUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Example application that demonstrates how to use the tracing functionality.
 * This class shows how to create and use tracing providers and spans.
 
 *
 * @doc.type class
 * @doc.purpose Tracing example
 * @doc.layer core
 * @doc.pattern Component
*/
public class TracingExample {

    private final TracingProvider tracingProvider;

    /**
     * Creates a new TracingExample with the specified tracing provider.
     *
     * @param tracingProvider The tracing provider
     */
    public TracingExample(TracingProvider tracingProvider) {
        this.tracingProvider = tracingProvider;
    }

    /**
     * Main method.
     *
     * @param args The command line arguments
     * @throws Exception If an error occurs
     */
    public static void main(String[] args) throws Exception {
        // Create a tracing manager
        TracingManager tracingManager = TracingManager.createDefault("tracing-example", "1.0.0");
        
        // Get a tracing provider
        TracingProvider tracingProvider = tracingManager.getProvider("example-provider");
        
        // Create an example
        TracingExample example = new TracingExample(tracingProvider);
        
        // Run the examples
        example.runBasicExample();
        example.runNestedSpansExample();
        example.runAttributesExample();
        example.runErrorExample();
        example.runUtilsExample();
        example.runAnnotationExample();
        
        // Wait for spans to be exported
        TimeUnit.SECONDS.sleep(2);
    }

    /**
     * Runs a basic example that creates a span.
     */
    public void runBasicExample() {
        System.out.println("\nRunning basic example...");
        
        // Create a span
        Span span = tracingProvider.createSpan("basic-example");
        
        try (Scope scope = span.makeCurrent()) {
            // Do some work
            System.out.println("Doing some work in the basic example...");
            sleep(100);
        } finally {
            // End the span
            span.end();
        }
    }

    /**
     * Runs an example that creates nested spans.
     */
    public void runNestedSpansExample() {
        System.out.println("\nRunning nested spans example...");
        
        // Create a parent span
        Span parentSpan = tracingProvider.createSpan("parent-span");
        
        try (Scope parentScope = parentSpan.makeCurrent()) {
            // Do some work in the parent span
            System.out.println("Doing some work in the parent span...");
            sleep(100);
            
            // Create a child span
            Span childSpan = tracingProvider.createChildSpan("child-span", parentSpan);
            
            try (Scope childScope = childSpan.makeCurrent()) {
                // Do some work in the child span
                System.out.println("Doing some work in the child span...");
                sleep(100);
            } finally {
                // End the child span
                childSpan.end();
            }
            
            // Do some more work in the parent span
            System.out.println("Doing some more work in the parent span...");
            sleep(100);
        } finally {
            // End the parent span
            parentSpan.end();
        }
    }

    /**
     * Runs an example that adds attributes to spans.
     */
    public void runAttributesExample() {
        System.out.println("\nRunning attributes example...");
        
        // Create a span with attributes
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("string-attribute", "string-value");
        attributes.put("boolean-attribute", true);
        attributes.put("int-attribute", 42);
        attributes.put("double-attribute", 3.14);
        
        Span span = tracingProvider.createSpan("attributes-example", attributes);
        
        try (Scope scope = span.makeCurrent()) {
            // Do some work
            System.out.println("Doing some work in the attributes example...");
            sleep(100);
            
            // Add more attributes
            span.setAttribute("another-attribute", "another-value");
        } finally {
            // End the span
            span.end();
        }
    }

    /**
     * Runs an example that records an error in a span.
     */
    public void runErrorExample() {
        System.out.println("\nRunning error example...");
        
        // Create a span
        Span span = tracingProvider.createSpan("error-example");
        
        try (Scope scope = span.makeCurrent()) {
            // Do some work
            System.out.println("Doing some work in the error example...");
            sleep(100);
            
            // Simulate an error
            try {
                throw new RuntimeException("Simulated error");
            } catch (Exception e) {
                // Record the error in the span
                span.recordException(e);
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
                
                // Log the error
                System.out.println("Error: " + e.getMessage());
            }
        } finally {
            // End the span
            span.end();
        }
    }

    /**
     * Runs an example that uses the TracingUtils class.
     */
    public void runUtilsExample() {
        System.out.println("\nRunning utils example...");
        
        // Use TracingUtils.withSpan with a runnable
        TracingUtils.withSpan(tracingProvider, "utils-runnable-example", () -> {
            System.out.println("Doing some work in the utils runnable example...");
            sleep(100);
        });
        
        // Use TracingUtils.withSpan with a supplier
        String result = TracingUtils.withSpan(tracingProvider, "utils-supplier-example", (java.util.function.Supplier<String>) () -> {
            System.out.println("Doing some work in the utils supplier example...");
            sleep(100);
            return "result";
        });
        
        System.out.println("Result: " + result);
        
        // Use TracingUtils.withSpan with a callable
        try {
            result = TracingUtils.withSpan(tracingProvider, "utils-callable-example", (java.util.concurrent.Callable<String>) () -> {
                System.out.println("Doing some work in the utils callable example...");
                sleep(100);
                return "result";
            });
            
            System.out.println("Result: " + result);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Runs an example that uses the @Traced annotation.
     */
    public void runAnnotationExample() {
        System.out.println("\nRunning annotation example...");
        
        // Call a method annotated with @Traced
        tracedMethod();
        
        // Call a method annotated with @Traced with parameters
        tracedMethodWithParameters("param1", 42);
    }

    /**
     * Method annotated with @Traced.
     */
    @Traced
    public void tracedMethod() {
        System.out.println("Doing some work in the traced method...");
        sleep(100);
    }

    /**
     * Method annotated with @Traced with parameters.
     *
     * @param stringParam The string parameter
     * @param intParam The int parameter
     */
    @Traced(value = "traced-method-with-parameters", includeParameters = true, includeReturnValue = true)
    public String tracedMethodWithParameters(String stringParam, int intParam) {
        System.out.println("Doing some work in the traced method with parameters...");
        System.out.println("Parameters: " + stringParam + ", " + intParam);
        sleep(100);
        return "result";
    }

    /**
     * Sleeps for the specified number of milliseconds.
     *
     * @param millis The number of milliseconds to sleep
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
