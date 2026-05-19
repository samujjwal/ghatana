/**
 * ProductGatePackProvider - evaluates product-owned lifecycle gate packs.
 *
 * @doc.type class
 * @doc.purpose Evaluate product-local gate pack evidence without embedding product policy in Kernel
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import type {
  GateEvaluationRequest,
  GateEvaluationResult,
  GateProvider,
} from "@ghatana/kernel-product-contracts";

export interface ProductGatePackProviderOptions {
  readonly repoRoot: string;
  readonly gateId: string;
  readonly providerId?: string;
  readonly version?: string;
}

export class ProductGatePackProvider implements GateProvider {
  readonly providerId: string;
  readonly version: string;
  readonly backingStore = "file" as const;
  readonly capabilities = [
    "gates",
    "product-gate-pack",
    "evidence-backed",
  ] as const;

  private readonly repoRoot: string;
  private readonly gateId: string;

  constructor(options: ProductGatePackProviderOptions) {
    this.repoRoot = path.resolve(options.repoRoot);
    this.gateId = options.gateId;
    this.providerId =
      options.providerId ?? `product-gate-pack:${options.gateId}`;
    this.version = options.version ?? "1.0.0";
  }

  async evaluateGate(
    request: GateEvaluationRequest,
  ): Promise<GateEvaluationResult> {
    const startedAt = Date.now();
    const gateId = request.gateId.trim();
    const productUnitId = request.productUnitId.trim();
    if (gateId.length === 0 || productUnitId.length === 0) {
      return this.result(
        gateId,
        false,
        "Product gate pack requires non-empty gateId and productUnitId",
        [],
        startedAt,
      );
    }
    if (gateId !== this.gateId) {
      return this.result(
        gateId,
        false,
        `Provider ${this.providerId} cannot evaluate gate ${gateId}`,
        [],
        startedAt,
      );
    }

    const relativeGatePackPath = `products/${productUnitId}/lifecycle/gate-packs/${gateId}.yaml`;
    const absoluteGatePackPath = path.join(this.repoRoot, relativeGatePackPath);
    if (!existsSync(absoluteGatePackPath)) {
      return this.result(
        gateId,
        false,
        `Product gate pack not found at ${relativeGatePackPath}`,
        [],
        startedAt,
      );
    }

    const gatePack = parseGatePack(readFileSync(absoluteGatePackPath, "utf8"));
    const issues: string[] = [];
    if (gatePack.productId !== productUnitId) {
      issues.push(`productId must be ${productUnitId}`);
    }
    if (gatePack.gateId !== gateId) {
      issues.push(`gateId must be ${gateId}`);
    }
    if (
      !["evidence-backed", "declarative-only"].includes(
        gatePack.executionMode ?? "",
      )
    ) {
      issues.push("executionMode must be evidence-backed or declarative-only");
    }
    if (
      gatePack.status !== undefined &&
      !["active", "ready"].includes(gatePack.status)
    ) {
      issues.push("status must be active or ready");
    }
    if (gatePack.requiredEvidenceRefs.length === 0) {
      issues.push(
        "requiredEvidenceRefs must include at least one evidence file",
      );
    }

    const missingEvidence = gatePack.requiredEvidenceRefs.filter(
      (evidenceRef) => {
        const normalized = normalizeEvidenceRef(evidenceRef);
        return (
          normalized === null ||
          !existsSync(path.join(this.repoRoot, normalized))
        );
      },
    );
    if (missingEvidence.length > 0) {
      issues.push(`missing required evidence: ${missingEvidence.join(", ")}`);
    }

    if (issues.length > 0) {
      return this.result(
        gateId,
        false,
        `Product gate pack ${relativeGatePackPath} failed: ${issues.join("; ")}`,
        [relativeGatePackPath, ...gatePack.requiredEvidenceRefs],
        startedAt,
      );
    }

    return this.result(
      gateId,
      true,
      `Product gate pack ${relativeGatePackPath} passed with required evidence`,
      [relativeGatePackPath, ...gatePack.requiredEvidenceRefs],
      startedAt,
    );
  }

  async getGateConfig(gateId: string): Promise<Record<string, unknown> | null> {
    const normalizedGateId = gateId.trim();
    if (normalizedGateId.length === 0 || normalizedGateId !== this.gateId) {
      return null;
    }
    return {
      gateId: normalizedGateId,
      mode: "product-gate-pack",
      providerId: this.providerId,
      pathTemplate:
        "products/<productUnitId>/lifecycle/gate-packs/<gateId>.yaml",
    };
  }

  async listGates(): Promise<readonly string[]> {
    return [this.gateId];
  }

  private result(
    gateId: string,
    passed: boolean,
    reason: string,
    evidence: readonly string[],
    startedAt: number,
  ): GateEvaluationResult {
    return {
      gateId,
      passed,
      reason,
      evidence,
      evaluatedAt: new Date().toISOString(),
      duration: Date.now() - startedAt,
    };
  }
}

interface ParsedGatePack {
  readonly productId?: string;
  readonly gateId?: string;
  readonly executionMode?: string;
  readonly status?: string;
  readonly requiredEvidenceRefs: readonly string[];
}

function parseGatePack(content: string): ParsedGatePack {
  return {
    productId: readScalar(content, "productId"),
    gateId: readScalar(content, "gateId"),
    executionMode: readScalar(content, "executionMode"),
    status: readScalar(content, "status"),
    requiredEvidenceRefs: readStringList(content, "requiredEvidenceRefs"),
  };
}

function readScalar(content: string, key: string): string | undefined {
  const match = new RegExp(
    `^${escapeRegExp(key)}:\\s*([^#\\n]+)\\s*$`,
    "m",
  ).exec(content);
  return match?.[1]?.trim().replace(/^["']|["']$/g, "");
}

function readStringList(content: string, key: string): readonly string[] {
  const lines = content.split(/\r?\n/);
  const startIndex = lines.findIndex((line) => line.trim() === `${key}:`);
  if (startIndex === -1) {
    return [];
  }

  const values: string[] = [];
  for (const line of lines.slice(startIndex + 1)) {
    if (/^\S/.test(line) && !line.trim().startsWith("-")) {
      break;
    }
    const match = /^\s*-\s+(.+?)\s*$/.exec(line);
    if (match?.[1] !== undefined) {
      values.push(match[1].trim().replace(/^["']|["']$/g, ""));
    }
  }
  return values;
}

function normalizeEvidenceRef(evidenceRef: string): string | null {
  const normalized = evidenceRef.trim();
  if (
    normalized.length === 0 ||
    normalized.startsWith("..") ||
    path.isAbsolute(normalized)
  ) {
    return null;
  }
  return normalized;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
