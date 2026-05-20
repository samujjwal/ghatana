/**
 * @fileoverview Tests for provenance and protected-region contracts.
 *
 * All tests invoke real production code — no object-literal assertions.
 */

import { describe, it, expect } from "vitest";
import type { SourceRef } from "../source.js";
import {
  ProvenanceRecordSchema,
  OwnershipRegionSchema,
  OwnershipKindSchema,
  FileOwnershipMapSchema,
  ProtectedRegionMarkerSchema,
  ProtectedRegionSchema,
  PROTECTED_REGION_MARKER_PREFIX,
  PROTECTED_REGION_MARKER_RE,
  createProtectedRegion,
} from "../provenance.js";

// ============================================================================
// Shared test fixture
// ============================================================================

const FIXTURE_SOURCE_REF: SourceRef = {
  repositoryUri: "https://github.com/ghatana/example",
  commitRef: "abc1234",
  file: {
    relativePath: "src/App.tsx",
    contentType: "text/typescript",
    kind: "component",
  },
};

// ============================================================================
// PROVENANCE RECORD
// ============================================================================

describe("ProvenanceRecordSchema", () => {
  it("parses a valid provenance record", () => {
    const result = ProvenanceRecordSchema.parse({
      nodeId: "node-abc",
      sourceChain: [FIXTURE_SOURCE_REF],
      recordedAt: "2025-01-01T00:00:00.000Z",
      derivationKind: "scanned",
    });
    expect(result.nodeId).toBe("node-abc");
    expect(result.derivationKind).toBe("scanned");
    expect(result.sourceChain).toHaveLength(1);
  });

  it("accepts all derivation kinds", () => {
    const derivations = [
      "scanned",
      "inferred",
      "generated",
      "user-authored",
      "merged",
    ] as const;
    for (const kind of derivations) {
      const result = ProvenanceRecordSchema.parse({
        nodeId: "n",
        sourceChain: [FIXTURE_SOURCE_REF],
        recordedAt: "2025-01-01T00:00:00.000Z",
        derivationKind: kind,
      });
      expect(result.derivationKind).toBe(kind);
    }
  });

  it("rejects missing sourceChain", () => {
    expect(() =>
      ProvenanceRecordSchema.parse({
        nodeId: "n",
        recordedAt: "2025-01-01T00:00:00.000Z",
        derivationKind: "scanned",
      }),
    ).toThrow();
  });

  it("rejects empty sourceChain", () => {
    expect(() =>
      ProvenanceRecordSchema.parse({
        nodeId: "n",
        sourceChain: [],
        recordedAt: "2025-01-01T00:00:00.000Z",
        derivationKind: "scanned",
      }),
    ).toThrow();
  });
});

// ============================================================================
// OWNERSHIP KIND
// ============================================================================

describe("OwnershipKindSchema", () => {
  it("accepts all four ownership kinds", () => {
    const kinds = ["generated", "user-authored", "protected", "manual-merge-required"] as const;
    for (const kind of kinds) {
      expect(OwnershipKindSchema.parse(kind)).toBe(kind);
    }
  });

  it("rejects unknown ownership kinds", () => {
    expect(() => OwnershipKindSchema.parse("unknown-kind")).toThrow();
  });
});

// ============================================================================
// OWNERSHIP REGION
// ============================================================================

describe("OwnershipRegionSchema", () => {
  it("parses a generated region", () => {
    const result = OwnershipRegionSchema.parse({
      filePath: "src/Button.tsx",
      startOffset: 0,
      endOffset: 500,
      kind: "generated",
    });
    expect(result.kind).toBe("generated");
    expect(result.filePath).toBe("src/Button.tsx");
  });

  it("parses a user-authored region with optional fields", () => {
    const result = OwnershipRegionSchema.parse({
      filePath: "src/Button.tsx",
      startOffset: 100,
      endOffset: 250,
      kind: "user-authored",
      regionMarker: "@ghatana-region:click-handler",
      note: "User customized handler",
    });
    expect(result.regionMarker).toBe("@ghatana-region:click-handler");
    expect(result.note).toBe("User customized handler");
  });

  it("rejects negative offsets", () => {
    expect(() =>
      OwnershipRegionSchema.parse({
        filePath: "src/Button.tsx",
        startOffset: -1,
        endOffset: 10,
        kind: "generated",
      }),
    ).toThrow();
  });

  it("rejects missing filePath", () => {
    expect(() =>
      OwnershipRegionSchema.parse({
        startOffset: 0,
        endOffset: 10,
        kind: "generated",
      }),
    ).toThrow();
  });
});

// ============================================================================
// FILE OWNERSHIP MAP
// ============================================================================

describe("FileOwnershipMapSchema", () => {
  it("parses a valid ownership map", () => {
    const result = FileOwnershipMapSchema.parse({
      filePath: "src/Component.tsx",
      regions: [
        { filePath: "src/Component.tsx", startOffset: 0, endOffset: 100, kind: "generated" },
        { filePath: "src/Component.tsx", startOffset: 100, endOffset: 200, kind: "user-authored" },
      ],
      hasUserContent: true,
    });
    expect(result.regions).toHaveLength(2);
    expect(result.hasUserContent).toBe(true);
  });

  it("parses a file with no user content", () => {
    const result = FileOwnershipMapSchema.parse({
      filePath: "src/Generated.tsx",
      regions: [],
      hasUserContent: false,
    });
    expect(result.regions).toHaveLength(0);
    expect(result.hasUserContent).toBe(false);
  });
});

// ============================================================================
// PROTECTED REGION MARKER
// ============================================================================

describe("PROTECTED_REGION_MARKER_PREFIX", () => {
  it("is the expected constant", () => {
    expect(PROTECTED_REGION_MARKER_PREFIX).toBe("@ghatana-protected:");
  });
});

describe("PROTECTED_REGION_MARKER_RE", () => {
  it("matches valid markers", () => {
    expect(PROTECTED_REGION_MARKER_RE.test("@ghatana-protected:my-handler")).toBe(true);
    expect(PROTECTED_REGION_MARKER_RE.test("@ghatana-protected:clickHandler_v2")).toBe(true);
    expect(PROTECTED_REGION_MARKER_RE.test("@ghatana-protected:abc123")).toBe(true);
  });

  it("rejects invalid markers", () => {
    expect(PROTECTED_REGION_MARKER_RE.test("@ghatana-protected:")).toBe(false);
    expect(PROTECTED_REGION_MARKER_RE.test("@ghatana-region:abc")).toBe(false);
    expect(PROTECTED_REGION_MARKER_RE.test("protected:abc")).toBe(false);
    expect(PROTECTED_REGION_MARKER_RE.test("@ghatana-protected:has spaces")).toBe(false);
  });

  it("captures the region id in group 1", () => {
    const match = "@ghatana-protected:my-handler".match(PROTECTED_REGION_MARKER_RE);
    expect(match).not.toBeNull();
    expect(match?.[1]).toBe("my-handler");
  });
});

describe("ProtectedRegionMarkerSchema", () => {
  it("accepts valid marker strings", () => {
    expect(ProtectedRegionMarkerSchema.parse("@ghatana-protected:my-handler")).toBe(
      "@ghatana-protected:my-handler",
    );
  });

  it("rejects empty marker", () => {
    expect(() => ProtectedRegionMarkerSchema.parse("@ghatana-protected:")).toThrow();
  });

  it("rejects markers with spaces", () => {
    expect(() =>
      ProtectedRegionMarkerSchema.parse("@ghatana-protected:has spaces"),
    ).toThrow();
  });
});

// ============================================================================
// PROTECTED REGION SCHEMA
// ============================================================================

describe("ProtectedRegionSchema", () => {
  it("parses a valid protected region", () => {
    const result = ProtectedRegionSchema.parse({
      filePath: "src/Button.tsx",
      startOffset: 200,
      endOffset: 350,
      kind: "protected",
      regionMarker: "@ghatana-protected:click-handler",
    });
    expect(result.kind).toBe("protected");
    expect(result.regionMarker).toBe("@ghatana-protected:click-handler");
  });

  it("rejects region with kind !== protected", () => {
    expect(() =>
      ProtectedRegionSchema.parse({
        filePath: "src/Button.tsx",
        startOffset: 0,
        endOffset: 100,
        kind: "generated",
        regionMarker: "@ghatana-protected:handler",
      }),
    ).toThrow();
  });

  it("rejects region with missing regionMarker", () => {
    expect(() =>
      ProtectedRegionSchema.parse({
        filePath: "src/Button.tsx",
        startOffset: 0,
        endOffset: 100,
        kind: "protected",
      }),
    ).toThrow();
  });

  it("rejects region with invalid regionMarker format", () => {
    expect(() =>
      ProtectedRegionSchema.parse({
        filePath: "src/Button.tsx",
        startOffset: 0,
        endOffset: 100,
        kind: "protected",
        regionMarker: "@wrong-prefix:handler",
      }),
    ).toThrow();
  });
});

// ============================================================================
// createProtectedRegion factory
// ============================================================================

describe("createProtectedRegion", () => {
  it("creates a valid ProtectedRegion with the correct marker", () => {
    const region = createProtectedRegion("src/Button.tsx", "click-handler", 100, 300);
    expect(region.kind).toBe("protected");
    expect(region.filePath).toBe("src/Button.tsx");
    expect(region.regionMarker).toBe("@ghatana-protected:click-handler");
    expect(region.startOffset).toBe(100);
    expect(region.endOffset).toBe(300);
    expect(region.note).toBeUndefined();
  });

  it("preserves optional note", () => {
    const region = createProtectedRegion(
      "src/Form.tsx",
      "submit-handler",
      0,
      50,
      "User customized submit",
    );
    expect(region.note).toBe("User customized submit");
  });

  it("produces a value parseable by ProtectedRegionSchema", () => {
    const region = createProtectedRegion("src/App.tsx", "main-block", 0, 200);
    // ProtectedRegionSchema.parse must not throw on the factory output
    expect(() => ProtectedRegionSchema.parse(region)).not.toThrow();
  });

  it("throws when regionId contains invalid characters", () => {
    expect(() =>
      createProtectedRegion("src/App.tsx", "has spaces", 0, 100),
    ).toThrow();
  });

  it("throws when regionId is empty", () => {
    expect(() => createProtectedRegion("src/App.tsx", "", 0, 100)).toThrow();
  });
});
