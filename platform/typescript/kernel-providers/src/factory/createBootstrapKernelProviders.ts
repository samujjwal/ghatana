/**
 * createBootstrapKernelProviders - factory for file-backed Kernel providers.
 *
 * @doc.type factory
 * @doc.purpose Bootstrap Kernel provider context construction
 * @doc.layer kernel-providers
 * @doc.pattern Factory
 */

import * as path from "node:path";
import type { KernelLifecycleProviderContext } from "@ghatana/kernel-product-contracts";
import { FileApprovalProvider } from "../approvals/FileApprovalProvider.js";
import { FileArtifactProvider } from "../artifacts/FileArtifactProvider.js";
import { FileLifecycleEventProvider } from "../events/FileLifecycleEventProvider.js";
import { FileHealthProvider } from "../health/FileHealthProvider.js";
import { FileMemoryProvider } from "../memory/FileMemoryProvider.js";
import { FileProvenanceProvider } from "../provenance/FileProvenanceProvider.js";
import { FileRuntimeTruthProvider } from "../runtime-truth/FileRuntimeTruthProvider.js";
import { GhatanaFileRegistryProvider } from "../registry/GhatanaFileRegistryProvider.js";

/**
 * Asserts that the current execution environment allows bootstrap mode.
 *
 * Bootstrap mode is intended for local development, CLI operations, and testing.
 * Production deployments must use platform mode with Data Cloud-backed providers.
 *
 * @param environment - Current environment (e.g., 'development', 'production', 'test')
 * @param allowBootstrapInProduction - If true, bypass the production guard (for testing only)
 * @throws Error if bootstrap mode is used in production without explicit override
 *
 * @doc.type function
 * @doc.purpose Guard against using bootstrap mode in production
 * @doc.layer kernel-providers
 * @doc.pattern Guard Function
 */
export function assertBootstrapOnly(
  environment: string = process.env.NODE_ENV ?? process.env.GHATANA_ENV ?? 'development',
  allowBootstrapInProduction: boolean = false
): void {
  const isProduction = environment === 'production' || environment === 'prod';
  if (isProduction && !allowBootstrapInProduction) {
    throw new Error(
      'Bootstrap mode is not allowed in production environment. Use platform mode with Data Cloud-backed providers. ' +
      'Set allowBootstrapInProduction=true to bypass this guard (for testing only).'
    );
  }
}

export interface BootstrapKernelProvidersOptions {
  readonly repoRoot: string;
  readonly outputRoot?: string;
  readonly allowOutputOutsideKernelOut?: boolean;
  readonly scope?: BootstrapKernelProviderScope;
  readonly includeRegistryProvider?: boolean;
  readonly registryPath?: string;
}

export interface BootstrapKernelProviderScope {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
}

export interface BootstrapKernelProviders {
  readonly repoRoot: string;
  readonly outputRoot: string;
  readonly context: KernelLifecycleProviderContext;
  readonly events: FileLifecycleEventProvider;
  readonly artifacts: FileArtifactProvider;
  readonly health: FileHealthProvider;
  readonly approvals: FileApprovalProvider;
  readonly provenance: FileProvenanceProvider;
  readonly memory: FileMemoryProvider;
  readonly runtimeTruth: FileRuntimeTruthProvider;
  readonly registryProvider?: GhatanaFileRegistryProvider;
}

export function createBootstrapKernelProviders(
  options: BootstrapKernelProvidersOptions
): BootstrapKernelProviders {
  const repoRoot = path.resolve(options.repoRoot);
  const outputRoot = path.resolve(
    repoRoot,
    options.outputRoot ?? path.join(".kernel", "out")
  );

  validateOutputRoot(repoRoot, outputRoot, options.allowOutputOutsideKernelOut ?? false);

  const events = new FileLifecycleEventProvider({
    outputDirectory: outputRoot,
    ...(options.scope ? { scope: options.scope } : {}),
  });
  const artifacts = new FileArtifactProvider({
    outputDirectory: outputRoot,
    artifactRootDirectory: repoRoot,
  });
  const health = new FileHealthProvider({ outputDirectory: outputRoot });
  const approvals = new FileApprovalProvider({ outputDirectory: outputRoot });
  const provenance = new FileProvenanceProvider({ outputDirectory: outputRoot });
  const memory = new FileMemoryProvider({ outputDirectory: outputRoot });
  const runtimeTruth = new FileRuntimeTruthProvider({ outputDirectory: outputRoot });
  const registryProvider =
    options.includeRegistryProvider === true
      ? new GhatanaFileRegistryProvider({
          registryPath:
            options.registryPath ??
            path.join(repoRoot, "config", "canonical-product-registry.json"),
        })
      : undefined;
  const context: KernelLifecycleProviderContext = {
    mode: "bootstrap",
    ...(registryProvider ? { registryProvider } : {}),
    events,
    artifacts,
    health,
    approvals,
    provenance,
    memory,
    runtimeTruth,
  };

  return {
    repoRoot,
    outputRoot,
    context,
    events,
    artifacts,
    health,
    approvals,
    provenance,
    memory,
    runtimeTruth,
    ...(registryProvider ? { registryProvider } : {}),
  };
}

function validateOutputRoot(
  repoRoot: string,
  outputRoot: string,
  allowOutputOutsideKernelOut: boolean
): void {
  const expectedRoot = path.join(repoRoot, ".kernel", "out");
  if (allowOutputOutsideKernelOut) {
    return;
  }
  if (!isSamePathOrChild(outputRoot, expectedRoot)) {
    throw new Error(
      `Bootstrap Kernel outputRoot must be inside ${expectedRoot}; received ${outputRoot}`
    );
  }
}

function isSamePathOrChild(candidate: string, parent: string): boolean {
  const relativePath = path.relative(parent, candidate);
  return relativePath === "" || (!relativePath.startsWith("..") && !path.isAbsolute(relativePath));
}
