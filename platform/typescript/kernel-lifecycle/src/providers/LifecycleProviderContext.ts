/**
 * LifecycleProviderContext - context for lifecycle execution with provider references.
 *
 * This interface defines the provider context that lifecycle operations use to
 * access registry, source, artifact, deployment, environment, telemetry, and health providers.
 *
 * @doc.type interface
 * @doc.purpose Provider context for lifecycle execution
 * @doc.layer kernel-lifecycle
 * @doc.pattern Context
 */

import type {
  RegistryProvider,
  SourceProvider,
  ArtifactProvider,
  DeploymentProvider,
  EnvironmentProvider,
  TelemetryProvider,
  HealthProvider,
} from "@ghatana/kernel-product-contracts";

/**
 * Provider context for lifecycle execution.
 */
export interface LifecycleProviderContext {
  /**
   * Registry provider for reading product metadata and lifecycle configuration.
   */
  readonly registryProvider: RegistryProvider;

  /**
   * Source provider for accessing source code and triggering builds.
   */
  readonly sourceProvider?: SourceProvider;

  /**
   * Artifact provider for storing and retrieving build artifacts.
   */
  readonly artifactProvider?: ArtifactProvider;

  /**
   * Deployment provider for deploying to environments.
   */
  readonly deploymentProvider?: DeploymentProvider;

  /**
   * Environment provider for managing environment configuration.
   */
  readonly environmentProvider?: EnvironmentProvider;

  /**
   * Telemetry provider for emitting lifecycle events and metrics.
   */
  readonly telemetryProvider?: TelemetryProvider;

  /**
   * Health provider for performing health checks and status reporting.
   */
  readonly healthProvider?: HealthProvider;
}
