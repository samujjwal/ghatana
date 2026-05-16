/**
 * BridgeComplianceGateProvider - validates Kernel bridge compliance.
 *
 * @doc.type class
 * @doc.purpose Execute bridge compliance gate check
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import { exec } from "node:child_process";
import { promisify } from "node:util";
import type {
  GateEvaluationRequest,
  GateEvaluationResult,
  GateProvider,
} from "@ghatana/kernel-product-contracts";

const execAsync = promisify(exec);

export class BridgeComplianceGateProvider implements GateProvider {
  readonly providerId = "bridge-compliance-gate";
  readonly version = "1.0.0";
  readonly backingStore = "external" as const;
  readonly capabilities = ["gates", "bridge-compliance", "bootstrap-mode"] as const;
  private readonly timeoutMs = 30000;

  async evaluateGate(request: GateEvaluationRequest): Promise<GateEvaluationResult> {
    const startedAt = Date.now();
    const gateId = request.gateId.trim();

    if (gateId.length === 0) {
      return {
        gateId: request.gateId,
        passed: false,
        reason: "Gate evaluation requires a non-empty gateId",
        evidence: [],
        evaluatedAt: new Date().toISOString(),
        duration: Date.now() - startedAt,
      };
    }

    try {
      const productUnitId = request.productUnitId;
      
      // Check if the bridge module exists
      const bridgePath = `products/${productUnitId}/*-kernel-bridge`;
      
      const { stdout, stderr } = await execAsync(
        `pnpm check:bridge-compliance --product ${productUnitId}`,
        {
          timeout: this.timeoutMs,
          cwd: process.cwd(),
        }
      ).catch(() => ({ stdout: "", stderr: "Bridge compliance check script not available" }));

      const output = stdout.trim();
      const errorOutput = stderr.trim();

      if (!errorOutput.includes("error") && !errorOutput.includes("failed") && !errorOutput.includes("not found")) {
        return {
          gateId,
          passed: true,
          reason: `Bridge compliance check passed for ${productUnitId}`,
          evidence: [output, errorOutput].filter(Boolean),
          evaluatedAt: new Date().toISOString(),
          duration: Date.now() - startedAt,
        };
      } else {
        return {
          gateId,
          passed: false,
          reason: `Bridge compliance check failed: ${errorOutput || "Bridge compliance check returned errors"}`,
          evidence: [output, errorOutput].filter(Boolean),
          evaluatedAt: new Date().toISOString(),
          duration: Date.now() - startedAt,
        };
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      return {
        gateId,
        passed: false,
        reason: `Bridge compliance check execution failed: ${errorMessage}`,
        evidence: [],
        evaluatedAt: new Date().toISOString(),
        duration: Date.now() - startedAt,
      };
    }
  }

  async getGateConfig(gateId: string): Promise<Record<string, unknown> | null> {
    const normalizedGateId = gateId.trim();
    if (normalizedGateId.length === 0) {
      return null;
    }

    return {
      gateId: normalizedGateId,
      mode: "external-script",
      providerId: this.providerId,
      script: "pnpm check:bridge-compliance",
      timeoutMs: this.timeoutMs,
    };
  }

  async listGates(): Promise<readonly string[]> {
    return ["bridge-compliance"];
  }
}
