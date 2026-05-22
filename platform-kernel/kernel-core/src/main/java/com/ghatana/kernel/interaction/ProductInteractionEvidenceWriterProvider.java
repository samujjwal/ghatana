package com.ghatana.kernel.interaction;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Provider factory for ProductInteractionEvidenceWriter based on provider mode.
 *
 * <p>This factory creates the appropriate evidence writer implementation based on the
 * provider mode (bootstrap or platform). In bootstrap mode, it uses file-backed storage
 * for local development and testing. In platform mode, it uses Data Cloud-backed storage
 * for production durability and audit requirements.</p>
 *
 * @doc.type class
 * @doc.purpose Factory for creating evidence writers based on provider mode
 * @doc.layer kernel
 * @doc.pattern Factory
 */
public final class ProductInteractionEvidenceWriterProvider {

    private ProductInteractionEvidenceWriterProvider() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates an evidence writer based on the provider mode.
     *
     * @param providerMode the provider mode (bootstrap or platform)
     * @param bootstrapConfig configuration for bootstrap mode
     * @param platformConfig configuration for platform mode
     * @return the appropriate evidence writer
     */
    public static ProductInteractionEvidenceWriter create(
            ProviderMode providerMode,
            BootstrapConfig bootstrapConfig,
            PlatformConfig platformConfig) {
        Objects.requireNonNull(providerMode, "providerMode must not be null");
        
        return switch (providerMode) {
            case BOOTSTRAP -> createBootstrapWriter(bootstrapConfig);
            case PLATFORM -> createPlatformWriter(platformConfig);
        };
    }

    /**
     * Creates a file-backed evidence writer for bootstrap mode.
     *
     * @param config the bootstrap configuration
     * @return a file-backed evidence writer
     */
    private static ProductInteractionEvidenceWriter createBootstrapWriter(BootstrapConfig config) {
        Objects.requireNonNull(config, "bootstrapConfig must not be null for bootstrap mode");
        Objects.requireNonNull(config.evidenceRoot, "evidenceRoot must not be null");
        Objects.requireNonNull(config.executor, "executor must not be null");
        
        return new FileProductInteractionEvidenceWriter(config.evidenceRoot, config.executor);
    }

    /**
     * Creates a Data Cloud-backed evidence writer for platform mode.
     *
     * @param config the platform configuration
     * @return a Data Cloud-backed evidence writer
     */
    private static ProductInteractionEvidenceWriter createPlatformWriter(PlatformConfig config) {
        Objects.requireNonNull(config, "platformConfig must not be null for platform mode");
        Objects.requireNonNull(config.dataCloudClient, "dataCloudClient must not be null");
        Objects.requireNonNull(config.executor, "executor must not be null");
        
        return new DataCloudProductInteractionEvidenceWriter(config.dataCloudClient, config.executor);
    }

    /**
     * Provider mode enumeration.
     */
    public enum ProviderMode {
        /**
         * Bootstrap mode uses file-backed storage for local development.
         */
        BOOTSTRAP,
        
        /**
         * Platform mode uses Data Cloud-backed storage for production.
         */
        PLATFORM
    }

    /**
     * Configuration for bootstrap mode.
     */
    public static final class BootstrapConfig {
        private final Path evidenceRoot;
        private final Executor executor;

        private BootstrapConfig(Builder builder) {
            this.evidenceRoot = builder.evidenceRoot;
            this.executor = builder.executor;
        }

        public Path evidenceRoot() {
            return evidenceRoot;
        }

        public Executor executor() {
            return executor;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private Path evidenceRoot;
            private Executor executor;

            public Builder evidenceRoot(Path evidenceRoot) {
                this.evidenceRoot = Objects.requireNonNull(evidenceRoot, "evidenceRoot must not be null");
                return this;
            }

            public Builder executor(Executor executor) {
                this.executor = Objects.requireNonNull(executor, "executor must not be null");
                return this;
            }

            public BootstrapConfig build() {
                return new BootstrapConfig(this);
            }
        }
    }

    /**
     * Configuration for platform mode.
     */
    public static final class PlatformConfig {
        private final DataCloudEvidenceClient dataCloudClient;
        private final Executor executor;

        private PlatformConfig(Builder builder) {
            this.dataCloudClient = builder.dataCloudClient;
            this.executor = builder.executor;
        }

        public DataCloudEvidenceClient dataCloudClient() {
            return dataCloudClient;
        }

        public Executor executor() {
            return executor;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private DataCloudEvidenceClient dataCloudClient;
            private Executor executor;

            public Builder dataCloudClient(DataCloudEvidenceClient dataCloudClient) {
                this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient must not be null");
                return this;
            }

            public Builder executor(Executor executor) {
                this.executor = Objects.requireNonNull(executor, "executor must not be null");
                return this;
            }

            public PlatformConfig build() {
                return new PlatformConfig(this);
            }
        }
    }
}
