/**
 * createPlatformKernelProviders - factory for Data Cloud-backed Kernel providers.
 *
 * @doc.type factory
 * @doc.purpose Platform provider context construction with Data Cloud backing
 * @doc.layer kernel-providers
 * @doc.pattern Factory
 */

import * as path from "node:path";
import type {
  KernelLifecycleProviderContext,
  GateProvider,
} from "@ghatana/kernel-product-contracts";
import { DataCloudApprovalProvider } from "../approvals/DataCloudApprovalProvider.js";
import { DataCloudArtifactProvider } from "../artifacts/DataCloudArtifactProvider.js";
import { DataCloudLifecycleEventProvider } from "../events/DataCloudLifecycleEventProvider.js";
import { DataCloudHealthProvider } from "../health/DataCloudHealthProvider.js";
import { DataCloudMemoryProvider } from "../memory/DataCloudMemoryProvider.js";
import { DataCloudProvenanceProvider } from "../provenance/DataCloudProvenanceProvider.js";
import { DataCloudRuntimeTruthProvider } from "../runtime-truth/DataCloudRuntimeTruthProvider.js";
import { FileBootstrapGateProvider } from "../gates/FileBootstrapGateProvider.js";
import { RegistryValidationGateProvider } from "../gates/RegistryValidationGateProvider.js";
import { ManifestValidationGateProvider } from "../gates/ManifestValidationGateProvider.js";
import { BridgeComplianceGateProvider } from "../gates/BridgeComplianceGateProvider.js";
import { UnitTestCoverageGateProvider } from "../gates/UnitTestCoverageGateProvider.js";
import { ProductGatePackProvider } from "../gates/ProductGatePackProvider.js";
import { GhatanaFileRegistryProvider } from "../registry/GhatanaFileRegistryProvider.js";
import { FileSourceProvider } from "../source/FileSourceProvider.js";

export interface PlatformKernelProvidersOptions {
  readonly repoRoot: string;
  readonly outputRoot?: string;
  readonly allowOutputOutsideKernelOut?: boolean;
  readonly scope?: PlatformKernelProviderScope;
  readonly dataCloudEndpoint?: string;
  readonly dataCloudApiKey?: string;
  readonly tenantId?: string;
  readonly workspaceId?: string;
  readonly projectId?: string;
}

export interface PlatformKernelProviderScope {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
}

export interface PlatformKernelProviders {
  readonly repoRoot: string;
  readonly outputRoot: string;
  readonly context: KernelLifecycleProviderContext;
  readonly events: DataCloudLifecycleEventProvider;
  readonly artifacts: DataCloudArtifactProvider;
  readonly health: DataCloudHealthProvider;
  readonly approvals: DataCloudApprovalProvider;
  readonly gates: Record<string, GateProvider>;
  readonly provenance: DataCloudProvenanceProvider;
  readonly memory: DataCloudMemoryProvider;
  readonly runtimeTruth: DataCloudRuntimeTruthProvider;
  readonly registryProvider: GhatanaFileRegistryProvider;
}

const SYNTHETIC_PLATFORM_PILOT_GATES = new Set([
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

function createPlatformPilotGateProvider(
  gateId: string,
): FileBootstrapGateProvider {
  return new FileBootstrapGateProvider({
    supportedGates: SYNTHETIC_PLATFORM_PILOT_GATES.has(gateId) ? [gateId] : [],
  });
}

export function createPlatformKernelProviders(
  options: PlatformKernelProvidersOptions,
): PlatformKernelProviders {
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

  const scope = options.scope ?? {
    tenantId: options.tenantId ?? "default-tenant",
    workspaceId: options.workspaceId ?? "default-workspace",
    projectId: options.projectId ?? "default-project",
  };

  const dataCloudUrl = options.dataCloudEndpoint ?? process.env.GHATANA_DATACLOUD_ENDPOINT ?? "http://localhost:8080";
  const apiKey = options.dataCloudApiKey ?? process.env.GHATANA_DATACLOUD_API_KEY;

  const events = new DataCloudLifecycleEventProvider({
    dataCloudUrl,
    tenantId: scope.tenantId,
    apiKey,
  });
  const artifacts = new DataCloudArtifactProvider({
    dataCloudUrl,
    tenantId: scope.tenantId,
    apiKey,
  });
  const health = new DataCloudHealthProvider({
    dataCloudUrl,
    tenantId: scope.tenantId,
    apiKey,
  });
  const approvals = new DataCloudApprovalProvider({
    dataCloudUrl,
    tenantId: scope.tenantId,
    apiKey,
  });

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
    "dev-environment-check": createPlatformPilotGateProvider(
      "dev-environment-check",
    ),
    "local-dependency-resolution": createPlatformPilotGateProvider(
      "local-dependency-resolution",
    ),
    "lifecycle-contract-validation": createPlatformPilotGateProvider(
      "lifecycle-contract-validation",
    ),
    "dmos-boundary-workflow-coverage": createPlatformPilotGateProvider(
      "dmos-boundary-workflow-coverage",
    ),
    "marketing-consent-boundary": createPlatformPilotGateProvider(
      "marketing-consent-boundary",
    ),
    "non-regulated-customer-data-minimization":
      createPlatformPilotGateProvider(
        "non-regulated-customer-data-minimization",
      ),
    "integration-test-coverage": createPlatformPilotGateProvider(
      "integration-test-coverage",
    ),
    "contract-test-coverage": createPlatformPilotGateProvider(
      "contract-test-coverage",
    ),
    "backend-check": createPlatformPilotGateProvider("backend-check"),
    "web-route-contract":
      createPlatformPilotGateProvider("web-route-contract"),
    typecheck: createPlatformPilotGateProvider("typecheck"),
    "unit-test": createPlatformPilotGateProvider("unit-test"),
    conformance: createPlatformPilotGateProvider("conformance"),
    "web-typecheck": createPlatformPilotGateProvider("web-typecheck"),
    lint: createPlatformPilotGateProvider("lint"),
    "web-bundle-budget": createPlatformPilotGateProvider("web-bundle-budget"),
    "web-a11y-readiness":
      createPlatformPilotGateProvider("web-a11y-readiness"),
    "web-i18n-readiness":
      createPlatformPilotGateProvider("web-i18n-readiness"),
    "container-image-integrity": createPlatformPilotGateProvider(
      "container-image-integrity",
    ),
    "security-scan": createPlatformPilotGateProvider("security-scan"),
    "license-policy": createPlatformPilotGateProvider("license-policy"),
    "bundle-budget": createPlatformPilotGateProvider("bundle-budget"),
    "container-scan": createPlatformPilotGateProvider("container-scan"),
    "image-vulnerability-scan": createPlatformPilotGateProvider(
      "image-vulnerability-scan",
    ),
    "artifact-validation": createPlatformPilotGateProvider(
      "artifact-validation",
    ),
    "environment-validation": createPlatformPilotGateProvider(
      "environment-validation",
    ),
    "health-check": createPlatformPilotGateProvider("health-check"),
    "observability-check": createPlatformPilotGateProvider(
      "observability-check",
    ),
    "privacy-check": createPlatformPilotGateProvider("privacy-check"),
    e2e: createPlatformPilotGateProvider("e2e"),
    performance: createPlatformPilotGateProvider("performance"),
    "rollback-plan": createPlatformPilotGateProvider("rollback-plan"),
    approval: createPlatformPilotGateProvider("approval"),
    "artifact-manifest-integrity": createPlatformPilotGateProvider(
      "artifact-manifest-integrity",
    ),
    "supply-chain-provenance": createPlatformPilotGateProvider(
      "supply-chain-provenance",
    ),
    "deployment-readiness": createPlatformPilotGateProvider(
      "deployment-readiness",
    ),
    "environment-configuration-validation": createPlatformPilotGateProvider(
      "environment-configuration-validation",
    ),
    "promotion-policy": createPlatformPilotGateProvider("promotion-policy"),
    "promotion-readiness": createPlatformPilotGateProvider(
      "promotion-readiness",
    ),
    "target-environment-validation": createPlatformPilotGateProvider(
      "target-environment-validation",
    ),
    "data-migration-validation": createPlatformPilotGateProvider(
      "data-migration-validation",
    ),
    "runtime-readiness": createPlatformPilotGateProvider("runtime-readiness"),
    "health-slo": createPlatformPilotGateProvider("health-slo"),
    "rollback-safety": createPlatformPilotGateProvider("rollback-safety"),
    "rollback-readiness":
      createPlatformPilotGateProvider("rollback-readiness"),
    "rollback-impact-analysis": createPlatformPilotGateProvider(
      "rollback-impact-analysis",
    ),
    ...productGatePackProviders,
  } as const;

  const provenance = new DataCloudProvenanceProvider({
    dataCloudUrl,
    tenantId: scope.tenantId,
    apiKey,
  });
  const memory = new DataCloudMemoryProvider({
    dataCloudUrl,
    tenantId: scope.tenantId,
    apiKey,
  });
  const runtimeTruth = new DataCloudRuntimeTruthProvider({
    dataCloudUrl,
    tenantId: scope.tenantId,
    apiKey,
  });

  // Platform mode uses the file registry provider for ProductUnit resolution
  // In a full Data Cloud deployment, this would be replaced with a DataCloud-backed registry
  const registryProvider = new GhatanaFileRegistryProvider({
    registryPath: path.join(repoRoot, "config", "canonical-product-registry.json"),
  });

  // Platform mode uses file source provider for git operations
  // In a full Data Cloud deployment, this would be replaced with a DataCloud-backed source provider
  const sourceProvider = new FileSourceProvider({ repoRoot });

  const context: KernelLifecycleProviderContext = {
    mode: "platform",
    registryProvider,
    sourceProvider,
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
    registryProvider,
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
      `Platform Kernel outputRoot must be inside ${expectedRoot}; received ${outputRoot}`,
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
