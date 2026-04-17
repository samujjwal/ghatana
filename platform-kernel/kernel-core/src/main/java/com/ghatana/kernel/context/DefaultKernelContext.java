package com.ghatana.kernel.context;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.event.EventHandler;
import com.ghatana.kernel.registry.KernelRegistry;
import com.ghatana.kernel.security.EnvironmentSecretProvider;
import com.ghatana.kernel.security.SecretProvider;
import io.activej.eventloop.Eventloop;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Default implementation of KernelContext.
 *
 * <p>Provides runtime context for kernel modules including dependency lookup,
 * event registration, tenant context, and event loop access.</p>
 *
 * @doc.type class
 * @doc.purpose Default implementation of kernel runtime context
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class DefaultKernelContext implements KernelContext {

    private final KernelRegistry registry;
    private final KernelConfigResolver configResolver;
    private final Eventloop eventloop;
    private final Map<Class<?>, Object> dependencies;
    private final Map<String, Object> namedDependencies;
    private final Map<Class<?>, List<EventHandler<?>>> eventHandlers;
    private final Map<String, KernelTenantContext> tenantContexts;
    private final String kernelVersion;
    private final String environment;

    public DefaultKernelContext(KernelRegistry registry,
                                 KernelConfigResolver configResolver,
                                 Eventloop eventloop,
                                 String kernelVersion,
                                 String environment) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.configResolver = Objects.requireNonNull(configResolver, "configResolver cannot be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop cannot be null");
        this.kernelVersion = Objects.requireNonNull(kernelVersion, "kernelVersion cannot be null");
        this.environment = Objects.requireNonNull(environment, "environment cannot be null");

        this.dependencies = new ConcurrentHashMap<>();
        this.namedDependencies = new ConcurrentHashMap<>();
        this.eventHandlers = new ConcurrentHashMap<>();
        this.tenantContexts = new ConcurrentHashMap<>();

        // Register kernel-provided defaults so modules can rely on them without
        // explicit wiring. Callers may override by calling registerDependency() later.
        this.dependencies.put(SecretProvider.class, EnvironmentSecretProvider.INSTANCE);
    }

    // ==================== Dependency Registration ====================

    /**
     * Registers a typed dependency.
     *
     * @param type the dependency type
     * @param instance the dependency instance
     * @param <T> the dependency type
     */
    public <T> void registerDependency(Class<T> type, T instance) {
        dependencies.put(type, instance);
    }

    /**
     * Registers a named dependency.
     *
     * @param name the dependency name
     * @param instance the dependency instance
     */
    public void registerDependency(String name, Object instance) {
        namedDependencies.put(name, instance);
    }

    // ==================== Dependency Lookup ====================

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getDependency(Class<T> type) {
        Object dep = dependencies.get(type);
        if (dep == null) {
            throw new IllegalStateException("Dependency not found: " + type.getName());
        }
        return (T) dep;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptionalDependency(Class<T> type) {
        return Optional.ofNullable((T) dependencies.get(type));
    }

    @Override
    public <T> boolean hasDependency(Class<T> type) {
        return dependencies.containsKey(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getDependency(String name, Class<T> type) {
        Object dep = namedDependencies.get(name);
        if (dep == null) {
            throw new IllegalStateException("Dependency not found: " + name);
        }
        if (!type.isInstance(dep)) {
            throw new IllegalStateException("Dependency type mismatch for: " + name);
        }
        return (T) dep;
    }

    // ==================== Event System ====================

    @Override
    @SuppressWarnings("unchecked")
    public <E> void registerEventHandler(Class<E> eventType, EventHandler<E> handler) {
        eventHandlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
            .add(handler);
    }

    @Override
    public <E> void unregisterEventHandler(Class<E> eventType, EventHandler<E> handler) {
        List<EventHandler<?>> handlers = eventHandlers.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> void publishEvent(E event) {
        if (event == null) return;

        List<EventHandler<?>> handlers = eventHandlers.get(event.getClass());
        if (handlers != null) {
            for (EventHandler<?> handler : handlers) {
                try {
                    ((EventHandler<E>) handler).handle(event);
                } catch (Exception e) {
                    System.getLogger(DefaultKernelContext.class.getName())
                            .log(System.Logger.Level.WARNING, "Event handler failed", e);
                }
            }
        }
    }

    // ==================== Tenant Context ====================

    @Override
    public KernelTenantContext getTenantContext() {
        // Return default/system tenant
        return tenantContexts.getOrDefault("system", createDefaultTenantContext());
    }

    @Override
    public KernelTenantContext getTenantContext(String tenantId) {
        KernelTenantContext ctx = tenantContexts.get(tenantId);
        if (ctx == null) {
            throw new IllegalArgumentException("Tenant not found: " + tenantId);
        }
        return ctx;
    }

    /**
     * Registers a tenant context.
     *
     * @param tenantId the tenant identifier
     * @param context the tenant context
     */
    public void registerTenantContext(String tenantId, KernelTenantContext context) {
        tenantContexts.put(tenantId, context);
    }

    private KernelTenantContext createDefaultTenantContext() {
        return new KernelTenantContext(
            "system",
            KernelTenantContext.TenantType.SYSTEM,
            Map.of(),
            Set.of(),
            null,
            null
        );
    }

    // ==================== Runtime ====================

    @Override
    public Eventloop getEventloop() {
        return eventloop;
    }

    @Override
    public Set<KernelCapability> getAvailableCapabilities() {
        return new HashSet<>(registry.getAllCapabilities());
    }

    @Override
    public boolean hasCapability(KernelCapability capability) {
        return registry.getAllCapabilities().stream()
            .anyMatch(c -> c.getCapabilityId().equals(capability.getCapabilityId()));
    }

    @Override
    public <T> T getConfig(String key, Class<T> type) {
        return configResolver.resolve(key, type, getTenantContext());
    }

    @Override
    public <T> Optional<T> getOptionalConfig(String key, Class<T> type) {
        return configResolver.resolveOptional(key, type, getTenantContext());
    }

    @Override
    public String getKernelVersion() {
        return kernelVersion;
    }

    @Override
    public String getEnvironment() {
        return environment;
    }

    @Override
    public Executor getExecutor(String executorName) {
        return ForkJoinPool.commonPool();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getCapability(String capabilityId) {
        return Optional.ofNullable((T) namedDependencies.get("capability:" + capabilityId));
    }

    @Override
    public <T> void registerService(Class<T> type, T service) {
        // Validate inputs
        Objects.requireNonNull(type, "Service type cannot be null");
        Objects.requireNonNull(service, "Service instance cannot be null");

        // Verify service type matches instance
        if (!type.isInstance(service)) {
            throw new IllegalArgumentException(
                "Service instance does not match type. Expected: " + type.getName() +
                ", Got: " + service.getClass().getName()
            );
        }

        // Verify service implements KernelLifecycleAware
        if (!(service instanceof com.ghatana.kernel.service.KernelLifecycleAware)) {
            throw new IllegalArgumentException(
                "Service must implement KernelLifecycleAware: " + type.getName()
            );
        }

        // Register service
        dependencies.put(type, service);
    }

    // ==================== Registry Access ====================

    /**
     * Gets the kernel registry.
     *
     * @return the kernel registry
     */
    public KernelRegistry getRegistry() {
        return registry;
    }
}
