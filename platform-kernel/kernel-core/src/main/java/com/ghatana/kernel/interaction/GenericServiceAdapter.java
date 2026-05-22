package com.ghatana.kernel.interaction;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Generic service adapter for bridging kernel interaction handlers to product-specific services.
 *
 * <p>This adapter provides a consistent pattern for integrating product-specific services
 * (e.g., consent, preferences, notifications) with kernel interaction handlers. It handles
 * request/response transformation, error mapping, and policy context enrichment.</p>
 *
 * <p>The adapter is designed to be reusable across different service types while maintaining
 * type safety and proper error handling.</p>
 *
 * @doc.type class
 * @doc.purpose Generic adapter for product-specific service integration
 * @doc.layer kernel
 * @doc.pattern Adapter
 * @param <K> Kernel request type
 * @param <P> Product service request type
 * @param <R> Product service response type
 * @param <O> Kernel outcome type
 */
public final class GenericServiceAdapter<K, P, R, O> {

    private final Eventloop eventloop;
    private final String serviceName;
    private final Function<K, P> requestTransformer;
    private final Function<P, Promise<R>> serviceInvoker;
    private final Function<R, O> responseTransformer;
    private final Function<Throwable, O> errorTransformer;

    private GenericServiceAdapter(Builder<K, P, R, O> builder) {
        this.eventloop = Objects.requireNonNull(builder.eventloop, "eventloop must not be null");
        this.serviceName = Objects.requireNonNull(builder.serviceName, "serviceName must not be null");
        this.requestTransformer = Objects.requireNonNull(builder.requestTransformer, "requestTransformer must not be null");
        this.serviceInvoker = Objects.requireNonNull(builder.serviceInvoker, "serviceInvoker must not be null");
        this.responseTransformer = Objects.requireNonNull(builder.responseTransformer, "responseTransformer must not be null");
        this.errorTransformer = Objects.requireNonNull(builder.errorTransformer, "errorTransformer must not be null");
    }

    /**
     * Adapts a kernel request to a product service call.
     *
     * @param kernelRequest the kernel request
     * @return a Promise of the kernel outcome
     */
    public Promise<O> adapt(K kernelRequest) {
        Objects.requireNonNull(kernelRequest, "kernelRequest must not be null");

        return Promise.ofCallback(cb -> Promise.ofBlocking(eventloop, () -> {
                try {
                    return requestTransformer.apply(kernelRequest);
                } catch (Exception e) {
                    throw new ServiceAdapterException(serviceName, "request transformation", e);
                }
            })
            .then(productRequest -> {
                try {
                    return serviceInvoker.apply(productRequest);
                } catch (Exception e) {
                    return Promise.ofException(new ServiceAdapterException(serviceName, "service invocation", e));
                }
            })
            .then(productResponse -> {
                try {
                    return Promise.of(responseTransformer.apply(productResponse));
                } catch (Exception e) {
                    return Promise.ofException(new ServiceAdapterException(serviceName, "response transformation", e));
                }
            })
            .whenComplete((outcome, error) -> {
                if (error == null) {
                    cb.set(outcome);
                    return;
                }
                if (error instanceof ServiceAdapterException) {
                    cb.setException(error);
                    return;
                }
                O errorOutcome = errorTransformer.apply(error);
                if (errorOutcome != null) {
                    cb.set(errorOutcome);
                    return;
                }
                cb.setException(new ServiceAdapterException(serviceName, "service call", error));
            }));
    }

    /**
     * Gets the service name for logging/telemetry.
     *
     * @return the service name
     */
    public String serviceName() {
        return serviceName;
    }

    /**
     * Exception thrown when service adapter operations fail.
     */
    public static final class ServiceAdapterException extends RuntimeException {
        private final String serviceName;
        private final String operation;

        public ServiceAdapterException(String serviceName, String operation, Throwable cause) {
            super(String.format("[%s] %s failed: %s", serviceName, operation, cause.getMessage()), cause);
            this.serviceName = serviceName;
            this.operation = operation;
        }

        public String serviceName() { return serviceName; }
        public String operation() { return operation; }
    }

    /**
     * Builder for creating GenericServiceAdapter instances.
     */
    public static final class Builder<K, P, R, O> {
        private Eventloop eventloop;
        private String serviceName;
        private Function<K, P> requestTransformer;
        private Function<P, Promise<R>> serviceInvoker;
        private Function<R, O> responseTransformer;
        private Function<Throwable, O> errorTransformer;

        public Builder<K, P, R, O> eventloop(Eventloop eventloop) {
            this.eventloop = eventloop;
            return this;
        }

        public Builder<K, P, R, O> serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder<K, P, R, O> requestTransformer(Function<K, P> requestTransformer) {
            this.requestTransformer = requestTransformer;
            return this;
        }

        public Builder<K, P, R, O> serviceInvoker(Function<P, Promise<R>> serviceInvoker) {
            this.serviceInvoker = serviceInvoker;
            return this;
        }

        public Builder<K, P, R, O> responseTransformer(Function<R, O> responseTransformer) {
            this.responseTransformer = responseTransformer;
            return this;
        }

        public Builder<K, P, R, O> errorTransformer(Function<Throwable, O> errorTransformer) {
            this.errorTransformer = errorTransformer;
            return this;
        }

        public GenericServiceAdapter<K, P, R, O> build() {
            return new GenericServiceAdapter<>(this);
        }
    }
}
