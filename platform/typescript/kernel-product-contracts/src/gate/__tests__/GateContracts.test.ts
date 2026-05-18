/**
 * Tests for GateContracts — gate definition, result manifest, and failure reason schemas.
 *
 * Per §2.2 requirements:
 * - Valid gate definition passes parsing
 * - GateResultManifest with all-passing gates succeeds
 * - GateResultManifest with failed required gate captures failure
 * - RequiredGateReference is a reference only (no product-specific business logic)
 */
import { describe, it, expect } from "vitest";
import {
  parseGateResultManifest,
  GateDefinitionSchema,
  RequiredGateReferenceSchema,
  GATE_KINDS,
} from "../GateContracts.js";

describe("GATE_KINDS", () => {
  it("contains the canonical gate kinds", () => {
    const expected = [
      "policy",
      "compliance",
      "security",
      "artifact",
      "health",
      "approval",
      "test",
      "build",
      "custom",
    ] as const;
    for (const kind of expected) {
      expect(GATE_KINDS).toContain(kind);
    }
  });
});

describe("GateDefinitionSchema", () => {
  it("accepts a valid gate definition", () => {
    const gate = GateDefinitionSchema.parse({
      gateId: "security-scan",
      displayName: "Security Scan Gate",
      kind: "security",
      phase: "build",
      required: true,
    });
    expect(gate.gateId).toBe("security-scan");
    expect(gate.required).toBe(true);
  });

  it("rejects an unknown gate kind", () => {
    expect(() =>
      GateDefinitionSchema.parse({
        gateId: "x",
        displayName: "X",
        kind: "unknown-kind",
        phase: "build",
        required: false,
      }),
    ).toThrow();
  });
});

describe("RequiredGateReferenceSchema", () => {
  it("accepts a valid required gate reference", () => {
    const ref = RequiredGateReferenceSchema.parse({
      gateId: "security-scan",
      phase: "build",
      required: true,
    });
    expect(ref.gateId).toBe("security-scan");
  });

  it("rejects a reference with empty gateId", () => {
    expect(() =>
      RequiredGateReferenceSchema.parse({
        gateId: "",
        phase: "build",
        required: true,
      }),
    ).toThrow();
  });
});

describe("parseGateResultManifest", () => {
  const BASE_MANIFEST = {
    schemaVersion: "1.0.0" as const,
    runId: "run-001",
    correlationId: "corr-001",
    createdAt: "2026-06-01T10:05:00.000Z",
    productId: "digital-marketing",
    phase: "build",
    overallPassed: true,
    gates: [
      {
        gateId: "security-scan",
        phase: "build",
        required: true,
        passed: true,
        evaluatedAt: "2026-06-01T10:04:00.000Z",
        durationMs: 2000,
        reason: "No critical vulnerabilities found",
        evidenceRefs: ["scan-report/v1.sarif"],
      },
    ],
  };

  it("accepts a valid all-passing gate result manifest", () => {
    const manifest = parseGateResultManifest(BASE_MANIFEST);
    expect(manifest.overallPassed).toBe(true);
    expect(manifest.gates).toHaveLength(1);
    expect(manifest.gates[0]?.gateId).toBe("security-scan");
  });

  it("accepts a manifest where a required gate failed", () => {
    const manifest = parseGateResultManifest({
      ...BASE_MANIFEST,
      overallPassed: false,
      gates: [
        {
          ...BASE_MANIFEST.gates[0],
          passed: false,
          reason: "Critical vulnerability CVE-2026-9999 found",
          evidenceRefs: ["scan-report/v1.sarif"],
        },
      ],
    });
    expect(manifest.overallPassed).toBe(false);
    expect(manifest.gates[0]?.passed).toBe(false);
  });

  it("preserves runId and correlationId in the manifest", () => {
    const manifest = parseGateResultManifest(BASE_MANIFEST);
    expect(manifest.runId).toBe("run-001");
    expect(manifest.correlationId).toBe("corr-001");
  });

  it("rejects a manifest with wrong schemaVersion", () => {
    expect(() =>
      parseGateResultManifest({ ...BASE_MANIFEST, schemaVersion: "2.0.0" }),
    ).toThrow();
  });

  it("accepts a manifest with no gates", () => {
    const manifest = parseGateResultManifest({
      ...BASE_MANIFEST,
      overallPassed: true,
      gates: [],
    });
    expect(manifest.gates).toHaveLength(0);
  });
});
