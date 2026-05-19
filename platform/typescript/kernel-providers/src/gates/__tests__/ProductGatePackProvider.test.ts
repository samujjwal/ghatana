import { mkdtempSync, mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { ProductGatePackProvider } from "../ProductGatePackProvider";

function createRepoWithGatePack(options: {
  readonly productId?: string;
  readonly gateId?: string;
  readonly executionMode?: string;
  readonly status?: string;
  readonly evidencePath?: string;
  readonly writeEvidence?: boolean;
}) {
  const repoRoot = mkdtempSync(path.join(tmpdir(), "ghatana-gate-pack-"));
  const productId = options.productId ?? "phr";
  const gateId = options.gateId ?? "consent";
  const evidencePath =
    options.evidencePath ??
    "products/phr/policy-packs/healthcare-boundary-policy.yaml";
  const gatePackDir = path.join(
    repoRoot,
    "products",
    productId,
    "lifecycle",
    "gate-packs",
  );
  mkdirSync(gatePackDir, { recursive: true });
  if (options.writeEvidence ?? true) {
    mkdirSync(path.dirname(path.join(repoRoot, evidencePath)), {
      recursive: true,
    });
    writeFileSync(path.join(repoRoot, evidencePath), "schemaVersion: 1.0.0\n");
  }
  writeFileSync(
    path.join(gatePackDir, `${gateId}.yaml`),
    [
      "schemaVersion: 1.0.0",
      `productId: ${productId}`,
      `gateId: ${gateId}`,
      `executionMode: ${options.executionMode ?? "evidence-backed"}`,
      `status: ${options.status ?? "active"}`,
      "requiredEvidenceRefs:",
      `  - ${evidencePath}`,
      "",
    ].join("\n"),
  );
  return repoRoot;
}

describe("ProductGatePackProvider", () => {
  it("passes when the product gate pack and all required evidence exist", async () => {
    const repoRoot = createRepoWithGatePack({});
    const provider = new ProductGatePackProvider({
      repoRoot,
      gateId: "consent",
    });

    const result = await provider.evaluateGate({
      gateId: "consent",
      productUnitId: "phr",
      phase: "validate",
      context: {},
    });

    expect(result.passed).toBe(true);
    expect(result.evidence).toContain(
      "products/phr/lifecycle/gate-packs/consent.yaml",
    );
    expect(result.evidence).toContain(
      "products/phr/policy-packs/healthcare-boundary-policy.yaml",
    );
  });

  it("fails closed when required evidence is missing", async () => {
    const repoRoot = createRepoWithGatePack({ writeEvidence: false });
    const provider = new ProductGatePackProvider({
      repoRoot,
      gateId: "consent",
    });

    const result = await provider.evaluateGate({
      gateId: "consent",
      productUnitId: "phr",
      phase: "validate",
      context: {},
    });

    expect(result.passed).toBe(false);
    expect(result.reason).toContain("missing required evidence");
  });

  it("fails closed for planned gate packs", async () => {
    const repoRoot = createRepoWithGatePack({ status: "planned" });
    const provider = new ProductGatePackProvider({
      repoRoot,
      gateId: "consent",
    });

    const result = await provider.evaluateGate({
      gateId: "consent",
      productUnitId: "phr",
      phase: "validate",
      context: {},
    });

    expect(result.passed).toBe(false);
    expect(result.reason).toContain("status must be active or ready");
  });
});
