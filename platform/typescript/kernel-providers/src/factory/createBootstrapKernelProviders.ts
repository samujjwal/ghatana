/**
 * createBootstrapKernelProviders - factory for file-backed Kernel providers.
 *
 * @doc.type factory
 * @doc.purpose Bootstrap Kernel provider context construction
 * @doc.layer kernel-providers
 * @doc.pattern Factory
 */

import * as path from "node:path";
import type {
  KernelLifecycleProviderContext,
  GateProvider,
} from "@ghatana/kernel-product-contracts";
import { FileApprovalProvider } from "../approvals/FileApprovalProvider.js";
import { FileArtifactProvider } from "../artifacts/FileArtifactProvider.js";
import { FileLifecycleEventProvider } from "../events/FileLifecycleEventProvider.js";
import { FileBootstrapGateProvider } from "../gates/FileBootstrapGateProvider.js";
import { RegistryValidationGateProvider } from "../gates/RegistryValidationGateProvider.js";
import { ManifestValidationGateProvider } from "../gates/ManifestValidationGateProvider.js";
import { BridgeComplianceGateProvider } from "../gates/BridgeComplianceGateProvider.js";
import { UnitTestCoverageGateProvider } from "../gates/UnitTestCoverageGateProvider.js";
import { ProductGatePackProvider } from "../gates/ProductGatePackProvider.js";
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
  environment: string = process.env.NODE_ENV ??
    process.env.GHATANA_ENV ??
    "development",
  allowBootstrapInProduction: boolean = false,
): void {
  const isProduction = environment === "production" || environment === "prod";
  if (isProduction && !allowBootstrapInProduction) {
    throw new Error(
      "Bootstrap mode is not allowed in production environment. Use platform mode with Data Cloud-backed providers. " +
        "Set allowBootstrapInProduction=true to bypass this guard (for testing only).",
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
  readonly gates: Record<string, GateProvider>;
  readonly provenance: FileProvenanceProvider;
  readonly memory: FileMemoryProvider;
  readonly runtimeTruth: FileRuntimeTruthProvider;
  readonly registryProvider?: GhatanaFileRegistryProvider;
}

const SYNTHETIC_BOOTSTRAP_PILOT_GATES = new Set([
  "backend-check",
  "web-route-contract",
  "typecheck",
  "unit-test",
  "conformance",
  "web-typecheck",
  "web-bundle-budget",
  "web-a11y-readiness",
  "web-i18n-readiness",
  "container-image-integrity",
  "lifecycle-contract-validation",
  "dmos-boundary-workflow-coverage",
  "marketing-consent-boundary",
  "non-regulated-customer-data-minimization",
  "integration-test-coverage",
  "contract-test-coverage",
  "container-scan",
  "image-vulnerability-scan",
  "artifact-validation",
  "environment-validation",
  "health-check",
  "observability-check",
  "deployment-readiness",
  "environment-configuration-validation",
]);

const PRODUCT_GATE_PACK_GATES = [
  "consent",
  "pii-classification",
  "audit-evidence",
  "fhir-contract-validation",
  "tenant-data-sovereignty",
] as const;

function createBootstrapPilotGateProvider(
  gateId: string,
): FileBootstrapGateProvider {
  return new FileBootstrapGateProvider({
    supportedGates: SYNTHETIC_BOOTSTRAP_PILOT_GATES.has(gateId) ? [gateId] : [],
  });
}

export function createBootstrapKernelProviders(
  options: BootstrapKernelProvidersOptions,
): BootstrapKernelProviders {
  const repoRoot = path.resolve(options.repoRoot);
  const outputRoot = path.resolve(
    repoRoot,
    options.outputRoot ?? path.join(".kernel", "out"),
  );

  validateOutputRoot(
    repoRoot,
    outputRoot,
    options.allowOutputOutsideKernelOut ?? false,
  );

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
  // Register concrete gate providers for supported gates
  // Gates without concrete providers will return NOT_READY
  const productGatePackProviders = Object.fromEntries(
    PRODUCT_GATE_PACK_GATES.map((gateId) => [
      gateId,
      new ProductGatePackProvider({ repoRoot, gateId }),
    ]),
  );

  const gates = {
    // Concrete implementations for critical gates
    "registry-validation": new RegistryValidationGateProvider(),
    "manifest-validation": new ManifestValidationGateProvider(),
    "bridge-compliance": new BridgeComplianceGateProvider(),
    "unit-test-coverage": new UnitTestCoverageGateProvider(),

    // Fallback providers for gates not yet implemented with concrete checks
    // These will return NOT_READY when evaluated
    "dev-environment-check": createBootstrapPilotGateProvider(
      "dev-environment-check",
    ),
    "local-dependency-resolution": createBootstrapPilotGateProvider(
      "local-dependency-resolution",
    ),
    "lifecycle-contract-validation": createBootstrapPilotGateProvider(
      "lifecycle-contract-validation",
    ),
    "dmos-boundary-workflow-coverage": createBootstrapPilotGateProvider(
      "dmos-boundary-workflow-coverage",
    ),
    "marketing-consent-boundary": createBootstrapPilotGateProvider(
      "marketing-consent-boundary",
    ),
    "non-regulated-customer-data-minimization":
      createBootstrapPilotGateProvider(
        "non-regulated-customer-data-minimization",
      ),
    "integration-test-coverage": createBootstrapPilotGateProvider(
      "integration-test-coverage",
    ),
    "contract-test-coverage": createBootstrapPilotGateProvider(
      "contract-test-coverage",
    ),
    "backend-check": createBootstrapPilotGateProvider("backend-check"),
    "web-route-contract":
      createBootstrapPilotGateProvider("web-route-contract"),
    typecheck: createBootstrapPilotGateProvider("typecheck"),
    "unit-test": createBootstrapPilotGateProvider("unit-test"),
    conformance: createBootstrapPilotGateProvider("conformance"),
    "web-typecheck": createBootstrapPilotGateProvider("web-typecheck"),
    lint: createBootstrapPilotGateProvider("lint"),
    "web-bundle-budget": createBootstrapPilotGateProvider("web-bundle-budget"),
    "web-a11y-readiness":
      createBootstrapPilotGateProvider("web-a11y-readiness"),
    "web-i18n-readiness":
      createBootstrapPilotGateProvider("web-i18n-readiness"),
    "container-image-integrity": createBootstrapPilotGateProvider(
      "container-image-integrity",
    ),
    "security-scan": createBootstrapPilotGateProvider("security-scan"),
    "license-policy": createBootstrapPilotGateProvider("license-policy"),
    "bundle-budget": createBootstrapPilotGateProvider("bundle-budget"),
    "container-scan": createBootstrapPilotGateProvider("container-scan"),
    "image-vulnerability-scan": createBootstrapPilotGateProvider(
      "image-vulnerability-scan",
    ),
    "artifact-validation": createBootstrapPilotGateProvider(
      "artifact-validation",
    ),
    "environment-validation": createBootstrapPilotGateProvider(
      "environment-validation",
    ),
    "health-check": createBootstrapPilotGateProvider("health-check"),
    "observability-check": createBootstrapPilotGateProvider(
      "observability-check",
    ),
    "privacy-check": createBootstrapPilotGateProvider("privacy-check"),
    e2e: createBootstrapPilotGateProvider("e2e"),
    performance: createBootstrapPilotGateProvider("performance"),
    "rollback-plan": createBootstrapPilotGateProvider("rollback-plan"),
    approval: createBootstrapPilotGateProvider("approval"),
    "artifact-manifest-integrity": createBootstrapPilotGateProvider(
      "artifact-manifest-integrity",
    ),
    "supply-chain-provenance": createBootstrapPilotGateProvider(
      "supply-chain-provenance",
    ),
    "deployment-readiness": createBootstrapPilotGateProvider(
      "deployment-readiness",
    ),
    "environment-configuration-validation": createBootstrapPilotGateProvider(
      "environment-configuration-validation",
    ),
    "promotion-policy": createBootstrapPilotGateProvider("promotion-policy"),
    "promotion-readiness": createBootstrapPilotGateProvider(
      "promotion-readiness",
    ),
    "target-environment-validation": createBootstrapPilotGateProvider(
      "target-environment-validation",
    ),
    "data-migration-validation": createBootstrapPilotGateProvider(
      "data-migration-validation",
    ),
    "runtime-readiness": createBootstrapPilotGateProvider("runtime-readiness"),
    "health-slo": createBootstrapPilotGateProvider("health-slo"),
    "rollback-safety": createBootstrapPilotGateProvider("rollback-safety"),
    "rollback-readiness":
      createBootstrapPilotGateProvider("rollback-readiness"),
    "rollback-impact-analysis": createBootstrapPilotGateProvider(
      "rollback-impact-analysis",
    ),
    ...productGatePackProviders,
  } as const;
  const provenance = new FileProvenanceProvider({
    outputDirectory: outputRoot,
  });
  const memory = new FileMemoryProvider({ outputDirectory: outputRoot });
  const runtimeTruth = new FileRuntimeTruthProvider({
    outputDirectory: outputRoot,
  });
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
    gates,
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
    gates,
    provenance,
    memory,
    runtimeTruth,
    ...(registryProvider ? { registryProvider } : {}),
  };
}

function validateOutputRoot(
  repoRoot: string,
  outputRoot: string,
  allowOutputOutsideKernelOut: boolean,
): void {
  const expectedRoot = path.join(repoRoot, ".kernel", "out");
  if (allowOutputOutsideKernelOut) {
    return;
  }
  if (!isSamePathOrChild(outputRoot, expectedRoot)) {
    throw new Error(
      `Bootstrap Kernel outputRoot must be inside ${expectedRoot}; received ${outputRoot}`,
    );
  }
}

function isSamePathOrChild(candidate: string, parent: string): boolean {
  const relativePath = path.relative(parent, candidate);
  return (
    relativePath === "" ||
    (!relativePath.startsWith("..") && !path.isAbsolute(relativePath))
  );
}
