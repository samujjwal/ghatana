import { describe, expect, it } from "vitest";
import {
  ProvenanceReferenceSchema,
  redactProvenanceReference,
} from "../ProvenanceReference.js";

describe("ProvenanceReference", () => {
  it("validates required provenance fields", () => {
    const reference = ProvenanceReferenceSchema.parse({
      provenanceId: "prov-1",
      subject: { type: "artifact-manifest", id: "artifact-manifest-1" },
      source: "kernel-lifecycle",
      assertion: "Artifact manifest was produced by lifecycle run run-1.",
      confidence: 0.96,
      evidenceRefs: ["datacloud://evidence/artifacts/run-1"],
      recordedAt: "2026-01-01T00:00:00.000Z",
    });

    expect(reference.subject.type).toBe("artifact-manifest");
    expect(reference.privacyClassification).toBe("internal");
  });

  it("rejects missing source", () => {
    expect(() =>
      ProvenanceReferenceSchema.parse({
        provenanceId: "prov-1",
        subject: { type: "agent-action", id: "agent-action-1" },
        source: "",
        assertion: "Agent action had evidence.",
        confidence: 0.8,
        evidenceRefs: ["datacloud://evidence/agent-action-1"],
        recordedAt: "2026-01-01T00:00:00.000Z",
      }),
    ).toThrow();
  });

  it("redacts sensitive evidence by default", () => {
    const reference = ProvenanceReferenceSchema.parse({
      provenanceId: "prov-sensitive",
      subject: { type: "agent-action", id: "agent-action-1" },
      source: "aep",
      assertion: "Sensitive agent evidence was captured.",
      confidence: 0.75,
      evidenceRefs: ["datacloud://tenant-a/workspace-a/raw/agent-input.json"],
      privacyClassification: "restricted",
      recordedAt: "2026-01-01T00:00:00.000Z",
    });

    expect(redactProvenanceReference(reference).evidenceRefs).toEqual([
      "redacted://agent-input.json",
    ]);
    expect(
      redactProvenanceReference(reference, { revealSensitive: true })
        .evidenceRefs,
    ).toEqual(reference.evidenceRefs);
  });
});
