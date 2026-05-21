/**
 * Tests for LifecycleFailureClassifier and enhanced PlanExplain contracts.
 *
 * @doc.type test
 * @doc.purpose Conformance tests for lifecycle failure classification and plan explain output
 * @doc.layer kernel-product-contracts
 * @doc.pattern ConformanceTest
 */

import { describe, expect, it } from "vitest";
import {
  LifecycleFailureClassifierSchema,
  DependencyGraphSchema,
  ProviderChecksSchema,
  GateChecksSchema,
  ArtifactExpectationsSchema,
  ApprovalPolicySchema,
  ApprovalStatusSchema,
  EnvironmentPreflightSchema,
  PlanExplainSchema,
  parsePlanExplain,
  isPlanExplain,
  type LifecycleFailureClassifier,
  type DependencyGraph,
  type ProviderChecks,
  type GateChecks,
  type ArtifactExpectations,
  type ApprovalPolicy,
  type ApprovalStatus,
  type EnvironmentPreflight,
  type PlanExplain,
} from "../LifecycleContracts";

describe("LifecycleFailureClassifier", () => {
  it("accepts a valid failure classifier with all fields", () => {
    const classifier: LifecycleFailureClassifier = {
      category: "adapter",
      severity: "high",
      retryable: true,
      requiresHumanIntervention: false,
      remediationSteps: ["Check adapter configuration", "Verify dependencies"],
      relatedFailureCodes: ["adapter-failed", "command-failed"],
      component: "nextjs-build-adapter",
      knownWorkaround: {
        description: "Use legacy build adapter",
        workaroundSteps: ["Set adapter to legacy mode", "Re-run build"],
      },
    };

    const result = LifecycleFailureClassifierSchema.parse(classifier);
    expect(result.category).toBe("adapter");
    expect(result.severity).toBe("high");
    expect(result.retryable).toBe(true);
    expect(result.requiresHumanIntervention).toBe(false);
  });

  it("accepts a minimal failure classifier", () => {
    const classifier: LifecycleFailureClassifier = {
      category: "config",
      severity: "medium",
      retryable: false,
      requiresHumanIntervention: true,
    };

    const result = LifecycleFailureClassifierSchema.parse(classifier);
    expect(result.category).toBe("config");
    expect(result.severity).toBe("medium");
  });

  it("rejects invalid failure category", () => {
    const classifier = {
      category: "invalid-category",
      severity: "high",
      retryable: true,
      requiresHumanIntervention: false,
    };

    expect(() => LifecycleFailureClassifierSchema.parse(classifier)).toThrow();
  });

  it("rejects invalid failure severity", () => {
    const classifier = {
      category: "adapter",
      severity: "invalid-severity",
      retryable: true,
      requiresHumanIntervention: false,
    };

    expect(() => LifecycleFailureClassifierSchema.parse(classifier)).toThrow();
  });
});

describe("DependencyGraph", () => {
  it("accepts a valid dependency graph with nodes and edges", () => {
    const graph: DependencyGraph = {
      nodes: [
        {
          stepId: "step-1",
          phase: "build",
          surface: "web",
          adapter: "nextjs-build",
          status: "pending",
          estimatedDurationMs: 120000,
          dependencies: [],
          dependents: ["step-2"],
        },
        {
          stepId: "step-2",
          phase: "deploy",
          surface: "web",
          adapter: "k8s-deploy",
          status: "pending",
          estimatedDurationMs: 60000,
          dependencies: ["step-1"],
          dependents: [],
        },
      ],
      edges: [
        { from: "step-1", to: "step-2", type: "depends-on" },
      ],
      criticalPath: ["step-1", "step-2"],
    };

    const result = DependencyGraphSchema.parse(graph);
    expect(result.nodes).toHaveLength(2);
    expect(result.edges).toHaveLength(1);
    expect(result.criticalPath).toEqual(["step-1", "step-2"]);
  });

  it("accepts a dependency graph without critical path", () => {
    const graph: DependencyGraph = {
      nodes: [
        {
          stepId: "step-1",
          phase: "build",
          surface: "web",
          adapter: "nextjs-build",
          status: "pending",
          estimatedDurationMs: 120000,
          dependencies: [],
          dependents: [],
        },
      ],
      edges: [],
    };

    const result = DependencyGraphSchema.parse(graph);
    expect(result.criticalPath).toBeUndefined();
  });
});

describe("ProviderChecks", () => {
  it("accepts valid provider checks with all statuses", () => {
    const checks: ProviderChecks = {
      overallStatus: "healthy",
      totalProviders: 3,
      healthyProviders: 2,
      degradedProviders: 1,
      unhealthyProviders: 0,
      checks: [
        {
          providerId: "file-registry",
          providerKind: "registry",
          status: "healthy",
          message: "Registry operational",
          latencyMs: 45,
          checkedAt: new Date().toISOString(),
          capabilities: [
            { name: "registry-read", available: true, required: true },
            { name: "registry-write", available: true, required: true },
          ],
        },
        {
          providerId: "file-artifacts",
          providerKind: "artifacts",
          status: "degraded",
          message: "High latency",
          latencyMs: 500,
          checkedAt: new Date().toISOString(),
          capabilities: [
            { name: "artifact-storage", available: true, required: true },
          ],
        },
      ],
      missingCapabilities: [],
    };

    const result = ProviderChecksSchema.parse(checks);
    expect(result.overallStatus).toBe("healthy");
    expect(result.totalProviders).toBe(3);
    expect(result.healthyProviders).toBe(2);
  });

  it("includes missing capabilities in provider checks", () => {
    const checks: ProviderChecks = {
      overallStatus: "degraded",
      totalProviders: 2,
      healthyProviders: 1,
      degradedProviders: 0,
      unhealthyProviders: 1,
      checks: [
        {
          providerId: "file-registry",
          providerKind: "registry",
          status: "healthy",
          message: "Registry operational",
          checkedAt: new Date().toISOString(),
          capabilities: [
            { name: "registry-read", available: true, required: true },
            { name: "registry-write", available: false, required: true },
          ],
        },
      ],
      missingCapabilities: ["registry-write"],
    };

    const result = ProviderChecksSchema.parse(checks);
    expect(result.missingCapabilities).toContain("registry-write");
  });
});

describe("GateChecks", () => {
  it("accepts valid gate checks with all statuses", () => {
    const checks: GateChecks = {
      overallStatus: "passed",
      totalGates: 3,
      passedGates: 2,
      failedGates: 0,
      blockedGates: 0,
      pendingGates: 1,
      checks: [
        {
          gateId: "security-scan",
          gateKind: "security",
          phase: "build",
          status: "passed",
          message: "Security scan passed",
          evaluatedAt: new Date().toISOString(),
          policyPack: "security-baseline",
          required: true,
        },
        {
          gateId: "license-check",
          gateKind: "compliance",
          phase: "build",
          status: "pending",
          message: "License check pending",
          evaluatedAt: new Date().toISOString(),
          required: false,
        },
      ],
      blockingGates: [],
    };

    const result = GateChecksSchema.parse(checks);
    expect(result.overallStatus).toBe("passed");
    expect(result.totalGates).toBe(3);
  });

  it("includes blocking gates when present", () => {
    const checks: GateChecks = {
      overallStatus: "blocked",
      totalGates: 2,
      passedGates: 0,
      failedGates: 1,
      blockedGates: 1,
      pendingGates: 0,
      checks: [
        {
          gateId: "approval-gate",
          gateKind: "approval",
          phase: "deploy",
          status: "blocked",
          message: "Approval required",
          evaluatedAt: new Date().toISOString(),
          required: true,
        },
      ],
      blockingGates: ["approval-gate"],
    };

    const result = GateChecksSchema.parse(checks);
    expect(result.blockingGates).toContain("approval-gate");
  });
});

describe("ArtifactExpectations", () => {
  it("accepts valid artifact expectations with validation", () => {
    const expectations: ArtifactExpectations = {
      totalArtifacts: 2,
      availableArtifacts: 1,
      missingArtifacts: 1,
      invalidArtifacts: 0,
      expectations: [
        {
          artifactId: "web-app-image",
          artifactKind: "docker-image",
          required: true,
          expectedPath: "/artifacts/web-app:latest",
          expectedFingerprint: "sha256:abc123",
          status: "available",
          actualPath: "/artifacts/web-app:latest",
          actualFingerprint: "sha256:abc123",
          validatedAt: new Date().toISOString(),
        },
        {
          artifactId: "test-results",
          artifactKind: "test-report",
          required: false,
          status: "missing",
        },
      ],
      missingRequired: ["test-results"],
    };

    const result = ArtifactExpectationsSchema.parse(expectations);
    expect(result.totalArtifacts).toBe(2);
    expect(result.availableArtifacts).toBe(1);
    expect(result.missingRequired).toContain("test-results");
  });
});

describe("ApprovalPolicy", () => {
  it("accepts a valid manual approval policy", () => {
    const policy: ApprovalPolicy = {
      policyId: "manual-deploy-approval",
      policyKind: "manual",
      requiresApproval: true,
      approvers: ["admin@ghatana.com", "ops@ghatana.com"],
      quorum: 2,
      timeoutMs: 86400000,
      escalationPolicy: "escalate-to-cto",
    };

    const result = ApprovalPolicySchema.parse(policy);
    expect(result.policyKind).toBe("manual");
    expect(result.approvers).toHaveLength(2);
    expect(result.quorum).toBe(2);
  });

  it("accepts an automatic approval policy", () => {
    const policy: ApprovalPolicy = {
      policyId: "auto-dev-approval",
      policyKind: "automatic",
      requiresApproval: false,
    };

    const result = ApprovalPolicySchema.parse(policy);
    expect(result.policyKind).toBe("automatic");
    expect(result.requiresApproval).toBe(false);
  });
});

describe("ApprovalStatus", () => {
  it("accepts a valid approved status", () => {
    const status: ApprovalStatus = {
      policy: {
        policyId: "manual-deploy-approval",
        policyKind: "manual",
        requiresApproval: true,
        approvers: ["admin@ghatana.com"],
        quorum: 1,
      },
      status: "approved",
      requestedAt: new Date().toISOString(),
      approvedBy: ["admin@ghatana.com"],
    };

    const result = ApprovalStatusSchema.parse(status);
    expect(result.status).toBe("approved");
    expect(result.approvedBy).toContain("admin@ghatana.com");
  });

  it("accepts a valid rejected status with reason", () => {
    const status: ApprovalStatus = {
      policy: {
        policyId: "manual-deploy-approval",
        policyKind: "manual",
        requiresApproval: true,
        approvers: ["admin@ghatana.com"],
        quorum: 1,
      },
      status: "rejected",
      requestedAt: new Date().toISOString(),
      rejectedBy: ["admin@ghatana.com"],
      rejectionReason: "Security concerns in build artifact",
    };

    const result = ApprovalStatusSchema.parse(status);
    expect(result.status).toBe("rejected");
    expect(result.rejectionReason).toBe("Security concerns in build artifact");
  });
});

describe("EnvironmentPreflight", () => {
  it("accepts valid environment preflight checks", () => {
    const preflight: EnvironmentPreflight = {
      environmentName: "staging",
      environmentTarget: "k8s-staging-cluster",
      overallStatus: "ready",
      totalChecks: 3,
      passedChecks: 2,
      failedChecks: 0,
      warningChecks: 1,
      checks: [
        {
          checkId: "resource-quota",
          checkKind: "resource",
          status: "passed",
          message: "Resource quota available",
          checkedAt: new Date().toISOString(),
          severity: "high",
        },
        {
          checkId: "network-policy",
          checkKind: "network",
          status: "warning",
          message: "Network policy not enforced",
          checkedAt: new Date().toISOString(),
          severity: "medium",
          remediation: "Enable network policy enforcement",
        },
      ],
      blockingIssues: [],
      variables: {
        "ENVIRONMENT": "staging",
        "CLUSTER": "staging-1",
      },
    };

    const result = EnvironmentPreflightSchema.parse(preflight);
    expect(result.overallStatus).toBe("ready");
    expect(result.totalChecks).toBe(3);
    expect(result.variables).toHaveProperty("ENVIRONMENT");
  });

  it("includes blocking issues when environment is not ready", () => {
    const preflight: EnvironmentPreflight = {
      environmentName: "production",
      environmentTarget: "k8s-prod-cluster",
      overallStatus: "not-ready",
      totalChecks: 2,
      passedChecks: 0,
      failedChecks: 2,
      warningChecks: 0,
      checks: [
        {
          checkId: "resource-quota",
          checkKind: "resource",
          status: "failed",
          message: "Insufficient resources",
          checkedAt: new Date().toISOString(),
          severity: "critical",
          remediation: "Scale cluster or reduce resource requests",
        },
      ],
      blockingIssues: ["Insufficient resources"],
    };

    const result = EnvironmentPreflightSchema.parse(preflight);
    expect(result.overallStatus).toBe("not-ready");
    expect(result.blockingIssues).toContain("Insufficient resources");
  });
});

describe("PlanExplain", () => {
  it("accepts a valid plan explain output with all components", () => {
    const planExplain: PlanExplain = {
      schemaVersion: "1.0.0",
      runId: "run-123",
      correlationId: "corr-123",
      productUnitId: "digital-marketing",
      phase: "deploy",
      environment: "staging",
      lifecycleProfile: "standard-web-product",
      generatedAt: new Date().toISOString(),
      dependencyGraph: {
        nodes: [
          {
            stepId: "step-1",
            phase: "build",
            surface: "web",
            adapter: "nextjs-build",
            status: "pending",
            estimatedDurationMs: 120000,
            dependencies: [],
            dependents: [],
          },
        ],
        edges: [],
      },
      providerChecks: {
        overallStatus: "healthy",
        totalProviders: 3,
        healthyProviders: 3,
        degradedProviders: 0,
        unhealthyProviders: 0,
        checks: [],
        missingCapabilities: [],
      },
      gateChecks: {
        overallStatus: "passed",
        totalGates: 2,
        passedGates: 2,
        failedGates: 0,
        blockedGates: 0,
        pendingGates: 0,
        checks: [],
        blockingGates: [],
      },
      artifactExpectations: {
        totalArtifacts: 1,
        availableArtifacts: 1,
        missingArtifacts: 0,
        invalidArtifacts: 0,
        expectations: [],
        missingRequired: [],
      },
      approvalPolicy: {
        policyId: "auto-dev-approval",
        policyKind: "automatic",
        requiresApproval: false,
      },
      environmentPreflight: {
        environmentName: "staging",
        environmentTarget: "k8s-staging-cluster",
        overallStatus: "ready",
        totalChecks: 0,
        passedChecks: 0,
        failedChecks: 0,
        warningChecks: 0,
        checks: [],
        blockingIssues: [],
      },
      overallReadiness: "ready",
      estimatedTotalDurationMs: 120000,
      warnings: [],
    };

    const result = PlanExplainSchema.parse(planExplain);
    expect(result.overallReadiness).toBe("ready");
    expect(result.dependencyGraph.nodes).toHaveLength(1);
  });

  it("accepts a plan explain with blocking reasons when not ready", () => {
    const planExplain: PlanExplain = {
      schemaVersion: "1.0.0",
      runId: "run-123",
      correlationId: "corr-123",
      productUnitId: "digital-marketing",
      phase: "deploy",
      environment: "production",
      lifecycleProfile: "standard-web-product",
      generatedAt: new Date().toISOString(),
      dependencyGraph: {
        nodes: [],
        edges: [],
      },
      providerChecks: {
        overallStatus: "unhealthy",
        totalProviders: 1,
        healthyProviders: 0,
        degradedProviders: 0,
        unhealthyProviders: 1,
        checks: [],
        missingCapabilities: ["registry-read"],
      },
      gateChecks: {
        overallStatus: "blocked",
        totalGates: 1,
        passedGates: 0,
        failedGates: 0,
        blockedGates: 1,
        pendingGates: 0,
        checks: [],
        blockingGates: ["approval-gate"],
      },
      artifactExpectations: {
        totalArtifacts: 1,
        availableArtifacts: 0,
        missingArtifacts: 1,
        invalidArtifacts: 0,
        expectations: [],
        missingRequired: ["web-app-image"],
      },
      environmentPreflight: {
        environmentName: "production",
        environmentTarget: "k8s-prod-cluster",
        overallStatus: "not-ready",
        totalChecks: 1,
        passedChecks: 0,
        failedChecks: 1,
        warningChecks: 0,
        checks: [],
        blockingIssues: ["Insufficient resources"],
      },
      overallReadiness: "not-ready",
      blockingReasons: [
        "Provider registry-read capability missing",
        "Approval gate blocked",
        "Artifact web-app-image missing",
        "Environment not ready: Insufficient resources",
      ],
      estimatedTotalDurationMs: 120000,
    };

    const result = PlanExplainSchema.parse(planExplain);
    expect(result.overallReadiness).toBe("not-ready");
    expect(result.blockingReasons).toHaveLength(4);
  });

  it("parses plan explain using parsePlanExplain", () => {
    const planExplain = {
      schemaVersion: "1.0.0",
      runId: "run-123",
      correlationId: "corr-123",
      productUnitId: "digital-marketing",
      phase: "deploy",
      lifecycleProfile: "standard-web-product",
      generatedAt: new Date().toISOString(),
      dependencyGraph: { nodes: [], edges: [] },
      providerChecks: {
        overallStatus: "healthy",
        totalProviders: 0,
        healthyProviders: 0,
        degradedProviders: 0,
        unhealthyProviders: 0,
        checks: [],
        missingCapabilities: [],
      },
      gateChecks: {
        overallStatus: "passed",
        totalGates: 0,
        passedGates: 0,
        failedGates: 0,
        blockedGates: 0,
        pendingGates: 0,
        checks: [],
        blockingGates: [],
      },
      artifactExpectations: {
        totalArtifacts: 0,
        availableArtifacts: 0,
        missingArtifacts: 0,
        invalidArtifacts: 0,
        expectations: [],
        missingRequired: [],
      },
      overallReadiness: "ready",
      estimatedTotalDurationMs: 120000,
    };

    const result = parsePlanExplain(planExplain);
    expect(result.productUnitId).toBe("digital-marketing");
  });

  it("validates plan explain using isPlanExplain", () => {
    const validPlanExplain = {
      schemaVersion: "1.0.0",
      runId: "run-123",
      correlationId: "corr-123",
      productUnitId: "digital-marketing",
      phase: "deploy",
      lifecycleProfile: "standard-web-product",
      generatedAt: new Date().toISOString(),
      dependencyGraph: { nodes: [], edges: [] },
      providerChecks: {
        overallStatus: "healthy",
        totalProviders: 0,
        healthyProviders: 0,
        degradedProviders: 0,
        unhealthyProviders: 0,
        checks: [],
        missingCapabilities: [],
      },
      gateChecks: {
        overallStatus: "passed",
        totalGates: 0,
        passedGates: 0,
        failedGates: 0,
        blockedGates: 0,
        pendingGates: 0,
        checks: [],
        blockingGates: [],
      },
      artifactExpectations: {
        totalArtifacts: 0,
        availableArtifacts: 0,
        missingArtifacts: 0,
        invalidArtifacts: 0,
        expectations: [],
        missingRequired: [],
      },
      overallReadiness: "ready",
      estimatedTotalDurationMs: 120000,
    };

    const invalidPlanExplain = {
      schemaVersion: "2.0.0",
      runId: "run-123",
      correlationId: "corr-123",
      productUnitId: "digital-marketing",
      phase: "deploy",
      lifecycleProfile: "standard-web-product",
      generatedAt: new Date().toISOString(),
      dependencyGraph: { nodes: [], edges: [] },
      providerChecks: {
        overallStatus: "healthy",
        totalProviders: 0,
        healthyProviders: 0,
        degradedProviders: 0,
        unhealthyProviders: 0,
        checks: [],
        missingCapabilities: [],
      },
      gateChecks: {
        overallStatus: "passed",
        totalGates: 0,
        passedGates: 0,
        failedGates: 0,
        blockedGates: 0,
        pendingGates: 0,
        checks: [],
        blockingGates: [],
      },
      artifactExpectations: {
        totalArtifacts: 0,
        availableArtifacts: 0,
        missingArtifacts: 0,
        invalidArtifacts: 0,
        expectations: [],
        missingRequired: [],
      },
      overallReadiness: "ready",
      estimatedTotalDurationMs: 120000,
    };

    expect(isPlanExplain(validPlanExplain)).toBe(true);
    expect(isPlanExplain(invalidPlanExplain)).toBe(false);
  });

  it("rejects plan explain with invalid schema version", () => {
    const planExplain = {
      schemaVersion: "2.0.0",
      runId: "run-123",
      correlationId: "corr-123",
      productUnitId: "digital-marketing",
      phase: "deploy",
      lifecycleProfile: "standard-web-product",
      generatedAt: new Date().toISOString(),
      dependencyGraph: { nodes: [], edges: [] },
      providerChecks: {
        overallStatus: "healthy",
        totalProviders: 0,
        healthyProviders: 0,
        degradedProviders: 0,
        unhealthyProviders: 0,
        checks: [],
        missingCapabilities: [],
      },
      gateChecks: {
        overallStatus: "passed",
        totalGates: 0,
        passedGates: 0,
        failedGates: 0,
        blockedGates: 0,
        pendingGates: 0,
        checks: [],
        blockingGates: [],
      },
      artifactExpectations: {
        totalArtifacts: 0,
        availableArtifacts: 0,
        missingArtifacts: 0,
        invalidArtifacts: 0,
        expectations: [],
        missingRequired: [],
      },
      overallReadiness: "ready",
      estimatedTotalDurationMs: 120000,
    };

    expect(() => PlanExplainSchema.parse(planExplain)).toThrow();
  });
});
