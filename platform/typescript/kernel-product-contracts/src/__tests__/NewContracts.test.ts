/**
 * Tests for ArtifactReferences, DeploymentReferences, LifecycleEventEnvelope,
 * and AgentLifecycleActionEvidence contracts.
 *
 * Per §2.2 requirements:
 * - Artifact reference with fingerprint validates correctly
 * - Deployment manifest preserves runId/correlationId
 * - Event envelope wraps events with envelope metadata
 * - Agent evidence is a reference, not the evidence itself (redacted flag)
 * - SemanticArtifactReference is a reference only (no YAPPC internals)
 */
import { describe, it, expect } from "vitest";
import {
  parseArtifactManifestReference,
  ArtifactDigestSchema,
  ArtifactFingerprintSchema,
  LifecycleArtifactReferenceSchema,
  ARTIFACT_TYPES,
} from "../artifact/ArtifactReferences.js";
import {
  parseDeploymentManifestReference,
  parseVerifyHealthReportReference,
  parseRollbackManifestReference,
} from "../deployment/DeploymentReferences.js";
import { parseLifecycleEventEnvelope } from "../events/LifecycleEventEnvelope.js";
import {
  parseAgentLifecycleActionEvidence,
  AGENT_EVIDENCE_KINDS,
} from "../agentic/AgentLifecycleActionEvidence.js";

// ---------------------------------------------------------------------------
// ArtifactDigest
// ---------------------------------------------------------------------------

describe("ArtifactDigestSchema", () => {
  it("accepts sha256 digest", () => {
    const digest = ArtifactDigestSchema.parse({
      algorithm: "sha256",
      value: "abc123def456",
    });
    expect(digest.algorithm).toBe("sha256");
  });

  it("rejects unsupported algorithm", () => {
    expect(() =>
      ArtifactDigestSchema.parse({ algorithm: "md5", value: "abc" }),
    ).toThrow();
  });
});

// ---------------------------------------------------------------------------
// ArtifactFingerprint
// ---------------------------------------------------------------------------

describe("ArtifactFingerprintSchema", () => {
  it("accepts a valid fingerprint", () => {
    const fp = ArtifactFingerprintSchema.parse({
      artifactId: "web-bundle-v1",
      digest: { algorithm: "sha256", value: "cafebabe1234" },
      generatedAt: "2026-06-01T10:00:00.000Z",
      sizeBytes: 10240,
    });
    expect(fp.artifactId).toBe("web-bundle-v1");
    expect(fp.digest.algorithm).toBe("sha256");
  });
});

// ---------------------------------------------------------------------------
// LifecycleArtifactReference
// ---------------------------------------------------------------------------

describe("LifecycleArtifactReferenceSchema", () => {
  it("accepts a docker-image artifact reference", () => {
    const ref = LifecycleArtifactReferenceSchema.parse({
      artifactId: "digital-marketing-api-img",
      artifactType: "docker-image",
      path: "artifacts/digital-marketing-api-img.tar",
      runId: "run-001",
      correlationId: "corr-001",
      producedAt: "2026-06-01T10:05:00.000Z",
    });
    expect(ref.artifactType).toBe("docker-image");
    expect(ref.runId).toBe("run-001");
  });

  it("rejects unknown artifact type", () => {
    expect(() =>
      LifecycleArtifactReferenceSchema.parse({
        artifactId: "x",
        artifactType: "binary",
        path: "/x",
        runId: "r",
        correlationId: "c",
      }),
    ).toThrow();
  });
});

// ---------------------------------------------------------------------------
// ArtifactManifestReference
// ---------------------------------------------------------------------------

describe("parseArtifactManifestReference", () => {
  it("accepts a valid artifact manifest", () => {
    const manifest = parseArtifactManifestReference({
      schemaVersion: "1.0.0",
      runId: "run-001",
      correlationId: "corr-001",
      createdAt: "2026-06-01T10:05:00.000Z",
      productId: "digital-marketing",
      phase: "build",
      manifestPath: ".lifecycle/run-001/artifact-manifest.json",
      artifacts: [
        {
          artifactId: "web-bundle",
          artifactType: "static-web-bundle",
          path: "artifacts/web-bundle.tar.gz",
          runId: "run-001",
          correlationId: "corr-001",
        },
      ],
    });
    expect(manifest.artifacts).toHaveLength(1);
    expect(manifest.runId).toBe("run-001");
  });

  it("rejects manifest with wrong schema version", () => {
    expect(() =>
      parseArtifactManifestReference({
        schemaVersion: "2.0.0",
        runId: "r",
        correlationId: "c",
        createdAt: "2026-06-01T10:00:00.000Z",
        productId: "x",
        phase: "build",
        manifestPath: "/x",
        artifacts: [],
      }),
    ).toThrow();
  });
});

// ---------------------------------------------------------------------------
// DeploymentManifestReference
// ---------------------------------------------------------------------------

describe("parseDeploymentManifestReference", () => {
  it("accepts a valid deployment manifest", () => {
    const manifest = parseDeploymentManifestReference({
      schemaVersion: "1.0.0",
      runId: "run-001",
      correlationId: "corr-001",
      createdAt: "2026-06-01T11:00:00.000Z",
      productId: "digital-marketing",
      manifestPath: ".lifecycle/run-001/deployment-manifest.json",
      deployment: {
        deploymentId: "deploy-001",
        runId: "run-001",
        correlationId: "corr-001",
        productId: "digital-marketing",
        environment: {
          environmentId: "compose-local",
          environmentType: "compose",
        },
        deployedAt: "2026-06-01T11:00:00.000Z",
      },
    });
    expect(manifest.deployment.environment.environmentType).toBe("compose");
    expect(manifest.runId).toBe("run-001");
  });
});

// ---------------------------------------------------------------------------
// VerifyHealthReportReference
// ---------------------------------------------------------------------------

describe("parseVerifyHealthReportReference", () => {
  it("accepts a valid health report", () => {
    const report = parseVerifyHealthReportReference({
      schemaVersion: "1.0.0",
      runId: "run-001",
      correlationId: "corr-001",
      createdAt: "2026-06-01T11:05:00.000Z",
      productId: "digital-marketing",
      reportPath: ".lifecycle/run-001/verify-health-report.json",
      environment: {
        environmentId: "compose-local",
        environmentType: "compose",
      },
      overallStatus: "healthy",
      checks: [
        {
          checkId: "api-health",
          status: "passed",
          durationMs: 500,
        },
      ],
    });
    expect(report.overallStatus).toBe("healthy");
    expect(report.checks[0]?.checkId).toBe("api-health");
  });
});

// ---------------------------------------------------------------------------
// RollbackManifestReference
// ---------------------------------------------------------------------------

describe("parseRollbackManifestReference", () => {
  it("accepts a valid rollback manifest", () => {
    const manifest = parseRollbackManifestReference({
      schemaVersion: "1.0.0",
      runId: "run-001",
      correlationId: "corr-001",
      createdAt: "2026-06-01T12:00:00.000Z",
      productId: "digital-marketing",
      manifestPath: ".lifecycle/run-001/rollback-manifest.json",
      environment: {
        environmentId: "compose-local",
        environmentType: "compose",
      },
      rollbackStrategyId: "compose-down-up",
      approvalRequired: false,
    });
    expect(manifest.approvalRequired).toBe(false);
    expect(manifest.rollbackStrategyId).toBe("compose-down-up");
  });
});

// ---------------------------------------------------------------------------
// LifecycleEventEnvelope
// ---------------------------------------------------------------------------

describe("parseLifecycleEventEnvelope", () => {
  it("wraps a lifecycle event and preserves correlationId and runId", () => {
    const envelope = parseLifecycleEventEnvelope({
      schemaVersion: "1.0.0",
      envelopeId: "env-001",
      eventType: "lifecycle:phase:completed",
      correlationId: "corr-001",
      runId: "run-001",
      productId: "digital-marketing",
      source: "kernel-lifecycle",
      publishedAt: "2026-06-01T10:05:00.000Z",
      payload: { phase: "build", status: "succeeded" },
    });
    expect(envelope.correlationId).toBe("corr-001");
    expect(envelope.runId).toBe("run-001");
    expect(envelope.eventType).toBe("lifecycle:phase:completed");
  });

  it("rejects envelope missing runId", () => {
    expect(() =>
      parseLifecycleEventEnvelope({
        schemaVersion: "1.0.0",
        envelopeId: "env-001",
        eventType: "lifecycle:phase:completed",
        correlationId: "corr-001",
        // runId missing
        productId: "digital-marketing",
        source: "kernel-lifecycle",
        publishedAt: "2026-06-01T10:05:00.000Z",
        payload: {},
      }),
    ).toThrow();
  });
});

// ---------------------------------------------------------------------------
// AgentLifecycleActionEvidence
// ---------------------------------------------------------------------------

describe("AGENT_EVIDENCE_KINDS", () => {
  it("contains approval-record and security-scan-report", () => {
    expect(AGENT_EVIDENCE_KINDS).toContain("approval-record");
    expect(AGENT_EVIDENCE_KINDS).toContain("security-scan-report");
  });
});

describe("parseAgentLifecycleActionEvidence", () => {
  it("accepts valid agent evidence as a reference only (not the artifact itself)", () => {
    const evidence = parseAgentLifecycleActionEvidence({
      evidenceId: "ev-001",
      kind: "test-run-report",
      ref: "evidence/test-reports/v1.xml",
      capturedAt: "2026-06-01T10:03:00.000Z",
      redacted: false,
    });
    // The evidence is a reference (ref field), not inline data
    expect(evidence.ref).toBe("evidence/test-reports/v1.xml");
    expect(evidence.redacted).toBe(false);
  });

  it("accepts evidence with redacted=true for sensitive evidence", () => {
    const evidence = parseAgentLifecycleActionEvidence({
      evidenceId: "ev-002",
      kind: "approval-record",
      ref: "evidence/approvals/prod-approval.json",
      capturedAt: "2026-06-01T10:04:00.000Z",
      redacted: true,
    });
    expect(evidence.redacted).toBe(true);
  });

  it("defaults redacted to false when omitted", () => {
    const evidence = parseAgentLifecycleActionEvidence({
      evidenceId: "ev-003",
      kind: "artifact-fingerprint",
      ref: "evidence/fingerprints/web-bundle.sha256",
      capturedAt: "2026-06-01T10:02:00.000Z",
    });
    expect(evidence.redacted).toBe(false);
  });

  it("rejects evidence with unknown kind", () => {
    expect(() =>
      parseAgentLifecycleActionEvidence({
        evidenceId: "ev-004",
        kind: "yappc-internal-bundle",
        ref: "evidence/yappc-internals.json",
        capturedAt: "2026-06-01T10:00:00.000Z",
      }),
    ).toThrow();
  });
});

// ---------------------------------------------------------------------------
// ARTIFACT_TYPES canonical list
// ---------------------------------------------------------------------------

describe("ARTIFACT_TYPES", () => {
  it("includes canonical artifact types and does not include product-specific names", () => {
    expect(ARTIFACT_TYPES).toContain("jvm-service");
    expect(ARTIFACT_TYPES).toContain("static-web-bundle");
    expect(ARTIFACT_TYPES).toContain("docker-image");
    // Must not reference product-specific types
    expect(ARTIFACT_TYPES).not.toContain("yappc-bundle");
    expect(ARTIFACT_TYPES).not.toContain("digital-marketing-package");
  });
});
