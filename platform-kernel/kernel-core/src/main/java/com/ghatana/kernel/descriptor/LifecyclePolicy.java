package com.ghatana.kernel.descriptor;

import java.time.Duration;

/**
 * Defines lifecycle policies for kernel components.
 *
 * <p>Lifecycle policies control startup behavior, shutdown behavior, restart policies,
 * and health check configurations for kernel modules and plugins.</p>
 *
 * @doc.type class
 * @doc.purpose Lifecycle policy configuration for kernel component management
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class LifecyclePolicy {

    private final StartupBehavior startupBehavior;
    private final ShutdownBehavior shutdownBehavior;
    private final RestartPolicy restartPolicy;
    private final int maxRestartAttempts;
    private final Duration restartBackoff;
    private final Duration startupTimeout;
    private final Duration shutdownTimeout;
    private final boolean eagerInitialization;
    private final boolean gracefulShutdown;

    /**
     * Creates a lifecycle policy with specified configuration.
     */
    private LifecyclePolicy(Builder builder) {
        this.startupBehavior = builder.startupBehavior != null ? builder.startupBehavior : StartupBehavior.AUTOMATIC;
        this.shutdownBehavior = builder.shutdownBehavior != null ? builder.shutdownBehavior : ShutdownBehavior.GRACEFUL;
        this.restartPolicy = builder.restartPolicy != null ? builder.restartPolicy : RestartPolicy.ON_FAILURE;
        this.maxRestartAttempts = builder.maxRestartAttempts;
        this.restartBackoff = builder.restartBackoff != null ? builder.restartBackoff : Duration.ofSeconds(5);
        this.startupTimeout = builder.startupTimeout != null ? builder.startupTimeout : Duration.ofMinutes(2);
        this.shutdownTimeout = builder.shutdownTimeout != null ? builder.shutdownTimeout : Duration.ofMinutes(1);
        this.eagerInitialization = builder.eagerInitialization;
        this.gracefulShutdown = builder.gracefulShutdown;
    }

    /**
     * Returns the default lifecycle policy.
     */
    public static LifecyclePolicy defaultPolicy() {
        return new Builder().build();
    }

    // Getters
    public StartupBehavior getStartupBehavior() { return startupBehavior; }
    public ShutdownBehavior getShutdownBehavior() { return shutdownBehavior; }
    public RestartPolicy getRestartPolicy() { return restartPolicy; }
    public int getMaxRestartAttempts() { return maxRestartAttempts; }
    public Duration getRestartBackoff() { return restartBackoff; }
    public Duration getStartupTimeout() { return startupTimeout; }
    public Duration getShutdownTimeout() { return shutdownTimeout; }
    public boolean isEagerInitialization() { return eagerInitialization; }
    public boolean isGracefulShutdown() { return gracefulShutdown; }

    // Business methods
    public boolean shouldRestartOnFailure() {
        return restartPolicy == RestartPolicy.ON_FAILURE || restartPolicy == RestartPolicy.ALWAYS;
    }

    public boolean shouldRestart() {
        return restartPolicy != RestartPolicy.NEVER;
    }

    // Builder
    public static class Builder {
        private StartupBehavior startupBehavior;
        private ShutdownBehavior shutdownBehavior;
        private RestartPolicy restartPolicy = RestartPolicy.ON_FAILURE;
        private int maxRestartAttempts = 3;
        private Duration restartBackoff;
        private Duration startupTimeout;
        private Duration shutdownTimeout;
        private boolean eagerInitialization = true;
        private boolean gracefulShutdown = true;

        public Builder withStartupBehavior(StartupBehavior behavior) {
            this.startupBehavior = behavior;
            return this;
        }

        public Builder withShutdownBehavior(ShutdownBehavior behavior) {
            this.shutdownBehavior = behavior;
            return this;
        }

        public Builder withRestartPolicy(RestartPolicy policy) {
            this.restartPolicy = policy;
            return this;
        }

        public Builder withMaxRestartAttempts(int attempts) {
            this.maxRestartAttempts = attempts;
            return this;
        }

        public Builder withRestartBackoff(Duration backoff) {
            this.restartBackoff = backoff;
            return this;
        }

        public Builder withStartupTimeout(Duration timeout) {
            this.startupTimeout = timeout;
            return this;
        }

        public Builder withShutdownTimeout(Duration timeout) {
            this.shutdownTimeout = timeout;
            return this;
        }

        public Builder withEagerInitialization(boolean eager) {
            this.eagerInitialization = eager;
            return this;
        }

        public Builder withGracefulShutdown(boolean graceful) {
            this.gracefulShutdown = graceful;
            return this;
        }

        public LifecyclePolicy build() {
            return new LifecyclePolicy(this);
        }
    }

    // Enums
    public enum StartupBehavior {
        AUTOMATIC,
        MANUAL,
        LAZY,
        ON_DEMAND
    }

    public enum ShutdownBehavior {
        GRACEFUL,
        IMMEDIATE,
        DEFERRED
    }

    public enum RestartPolicy {
        NEVER,
        ON_FAILURE,
        ALWAYS
    }
}
