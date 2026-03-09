package com.ghatana.agent.framework.runtime.generators;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Function;

/**
 * OutputGenerator that calls external services (HTTP, gRPC, etc.).
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Calling microservices for data/computation</li>
 *   <li>Integrating with external APIs</li>
 *   <li>Database queries</li>
 *   <li>Message queue operations</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * ServiceCallGenerator<CodeRequest, ValidationResult> validator = 
 *     new ServiceCallGenerator<>(
 *         httpClient,
 *         req -> String.format("/api/validate?lang=%s", req.getLanguage()),
 *         req -> req.getCode(),
 *         ValidationResult.class
 *     );
 * }</pre>
 * 
 * @param <TInput> Input type
 * @param <TOutput> Output type
 * 
 * @doc.type class
 * @doc.purpose External service call output generation
 * @doc.layer framework
 * @doc.pattern Strategy + Adapter
 */
public final class ServiceCallGenerator<TInput, TOutput> implements OutputGenerator<TInput, TOutput> {
    
    private final ServiceClient serviceClient;
    private final Function<TInput, String> endpointBuilder;
    private final Function<TInput, Object> requestBuilder;
    private final Class<TOutput> responseType;
    private final GeneratorMetadata metadata;
    
    /**
     * Creates a new ServiceCallGenerator.
     * 
     * @param serviceClient Service client for making calls
     * @param endpointBuilder Function to build endpoint from input
     * @param requestBuilder Function to build request payload from input
     * @param responseType Expected response type
     */
    public ServiceCallGenerator(
            @NotNull ServiceClient serviceClient,
            @NotNull Function<TInput, String> endpointBuilder,
            @NotNull Function<TInput, Object> requestBuilder,
            @NotNull Class<TOutput> responseType) {
        this.serviceClient = Objects.requireNonNull(serviceClient, "serviceClient cannot be null");
        this.endpointBuilder = Objects.requireNonNull(endpointBuilder, "endpointBuilder cannot be null");
        this.requestBuilder = Objects.requireNonNull(requestBuilder, "requestBuilder cannot be null");
        this.responseType = Objects.requireNonNull(responseType, "responseType cannot be null");
        this.metadata = GeneratorMetadata.builder()
            .name("ServiceCallGenerator")
            .type("service")
            .description("External service call generation")
            .property("serviceClient", serviceClient.getClass().getSimpleName())
            .property("responseType", responseType.getSimpleName())
            .build();
    }
    
    @Override
    @NotNull
    public Promise<TOutput> generate(@NotNull TInput input, @NotNull AgentContext context) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        
        try {
            // 1. Build endpoint and request
            String endpoint = endpointBuilder.apply(input);
            Object request = requestBuilder.apply(input);
            
            context.getLogger().debug("Calling service: {}", endpoint);
            context.addTraceTag("service.endpoint", endpoint);
            
            // 2. Call service
            long startTime = System.currentTimeMillis();
            return serviceClient.call(endpoint, request, responseType, context)
                .map(response -> {
                    // 3. Record metrics
                    long duration = System.currentTimeMillis() - startTime;
                    context.recordMetric("service.call.duration", duration);
                    context.recordMetric("service.call.success", 1);
                    
                    context.getLogger().debug("Service call completed in {}ms", duration);
                    
                    return response;
                })
                .whenException(ex -> {
                    context.getLogger().error("Service call failed", ex);
                    context.recordMetric("service.call.failure", 1);
                });
            
        } catch (Exception ex) {
            context.getLogger().error("Failed to build service request", ex);
            return Promise.ofException(ex);
        }
    }
    
    @Override
    @NotNull
    public GeneratorMetadata getMetadata() {
        return metadata;
    }
    
    @Override
    @NotNull
    public Promise<Double> estimateCost(@NotNull TInput input, @NotNull AgentContext context) {
        // Service calls may have costs (depends on service)
        // Default to 0, override in specific implementations
        return Promise.of(0.0);
    }
    
    /**
     * Service client interface.
     * Implementations handle protocol-specific details (HTTP, gRPC, etc.).
     */
    public interface ServiceClient {
        
        /**
         * Calls a service endpoint.
         * 
         * @param endpoint Service endpoint
         * @param request Request payload
         * @param responseType Expected response type
         * @param context Execution context
         * @param <TResponse> Response type
         * @return Promise of response
         */
        @NotNull
        <TResponse> Promise<TResponse> call(
            @NotNull String endpoint,
            @NotNull Object request,
            @NotNull Class<TResponse> responseType,
            @NotNull AgentContext context);
    }
}
