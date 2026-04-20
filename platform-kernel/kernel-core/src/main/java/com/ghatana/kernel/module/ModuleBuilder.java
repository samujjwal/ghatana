package com.ghatana.kernel.module;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fluent builder for creating lightweight kernel modules.
 *
 * @doc.type class
 * @doc.purpose Builder-based module composition with lifecycle hooks and lazy state
 * @doc.layer core
 * @doc.pattern Builder
 */
public final class ModuleBuilder {

    private String moduleId;
    private String version = "1.0.0";
    private final Set<KernelCapability> capabilities = new HashSet<>();
    private final Set<KernelDependency> dependencies = new HashSet<>();

    private Consumer<KernelContext> initializeAction = context -> { };
    private Supplier<Promise<Void>> startAction = Promise::complete;
    private Supplier<Promise<Void>> stopAction = Promise::complete;
    private Supplier<HealthStatus> healthAction = HealthStatus::healthy;

    public ModuleBuilder withModuleId(String moduleId) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId cannot be null");
        return this;
    }

    public ModuleBuilder withVersion(String version) {
        this.version = Objects.requireNonNull(version, "version cannot be null");
        return this;
    }

    public ModuleBuilder addCapability(KernelCapability capability) {
        this.capabilities.add(Objects.requireNonNull(capability, "capability cannot be null"));
        return this;
    }

    public ModuleBuilder addDependency(KernelDependency dependency) {
        this.dependencies.add(Objects.requireNonNull(dependency, "dependency cannot be null"));
        return this;
    }

    public ModuleBuilder onInitialize(Consumer<KernelContext> action) {
        this.initializeAction = Objects.requireNonNull(action, "action cannot be null");
        return this;
    }

    public ModuleBuilder onStart(Supplier<Promise<Void>> action) {
        this.startAction = Objects.requireNonNull(action, "action cannot be null");
        return this;
    }

    public ModuleBuilder onStop(Supplier<Promise<Void>> action) {
        this.stopAction = Objects.requireNonNull(action, "action cannot be null");
        return this;
    }

    public ModuleBuilder onHealth(Supplier<HealthStatus> action) {
        this.healthAction = Objects.requireNonNull(action, "action cannot be null");
        return this;
    }

    public KernelModule build() {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalStateException("moduleId must be configured");
        }

        final String finalModuleId = moduleId;
        final String finalVersion = version;
        final Set<KernelCapability> finalCapabilities = Set.copyOf(capabilities);
        final Set<KernelDependency> finalDependencies = Set.copyOf(dependencies);
        final Consumer<KernelContext> finalInitializeAction = initializeAction;
        final Supplier<Promise<Void>> finalStartAction = startAction;
        final Supplier<Promise<Void>> finalStopAction = stopAction;
        final Supplier<HealthStatus> finalHealthAction = healthAction;

        final LifecycleHookInvoker hookInvoker = new LifecycleHookInvoker();

        return new KernelModule() {
            @Override
            public String getModuleId() {
                return finalModuleId;
            }

            @Override
            public String getVersion() {
                return finalVersion;
            }

            @Override
            public Set<KernelCapability> getCapabilities() {
                return finalCapabilities;
            }

            @Override
            public Set<KernelDependency> getDependencies() {
                return finalDependencies;
            }

            @Override
            public void initialize(KernelContext context) {
                finalInitializeAction.accept(context);
                hookInvoker.invokePostInitialize(this);
            }

            @Override
            public Promise<Void> start() {
                hookInvoker.invokePreStart(this);
                return finalStartAction.get();
            }

            @Override
            public Promise<Void> stop() {
                return finalStopAction.get()
                    .whenComplete(($, e) -> hookInvoker.invokePostStop(this));
            }

            @Override
            public HealthStatus getHealthStatus() {
                return finalHealthAction.get();
            }
        };
    }
}
