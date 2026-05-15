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
import { FileProvenanceProvider } from "../provenance/FileProvenanceProvider.js";
import { FileRuntimeTruthProvider } from "../runtime-truth/FileRuntimeTruthProvider.js";

export interface BootstrapKernelProvidersOptions {
  readonly repoRoot: string;
  readonly outputRoot?: string;
  readonly allowOutputOutsideKernelOut?: boolean;
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
  readonly runtimeTruth: FileRuntimeTruthProvider;
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

  const events = new FileLifecycleEventProvider({ outputDirectory: outputRoot });
  const artifacts = new FileArtifactProvider({
    outputDirectory: outputRoot,
    artifactRootDirectory: repoRoot,
  });
  const health = new FileHealthProvider({ outputDirectory: outputRoot });
  const approvals = new FileApprovalProvider({ outputDirectory: outputRoot });
  const provenance = new FileProvenanceProvider({ outputDirectory: outputRoot });
  const runtimeTruth = new FileRuntimeTruthProvider({ outputDirectory: outputRoot });
  const context: KernelLifecycleProviderContext = {
    mode: "bootstrap",
    events,
    artifacts,
    health,
    approvals,
    provenance,
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
    runtimeTruth,
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
