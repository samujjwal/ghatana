/**
 * RegistryValidationGateProvider - validates product registry conformance.
 *
 * @doc.type class
 * @doc.purpose Execute registry validation gate check
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

export class RegistryValidationGateProvider implements GateProvider {
  readonly providerId = "registry-validation-gate";
  readonly version = "1.0.0";
  readonly backingStore = "external" as const;
  readonly capabilities = ["gates", "registry-validation", "bootstrap-mode"] as const;
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
      // Run the actual registry validation script
      const { stdout, stderr } = await execAsync(
        "pnpm check:product-registry",
        {
          timeout: this.timeoutMs,
          cwd: process.cwd(),
        }
      );

      const output = stdout.trim();
      const errorOutput = stderr.trim();

      // Check if the validation passed (script exits with 0 on success)
      if (!errorOutput.includes("error") && !errorOutput.includes("failed")) {
        return {
          gateId,
          passed: true,
          reason: `Registry validation passed: product registry is valid`,
          evidence: [output, errorOutput].filter(Boolean),
          evaluatedAt: new Date().toISOString(),
          duration: Date.now() - startedAt,
        };
      } else {
        return {
          gateId,
          passed: false,
          reason: `Registry validation failed: ${errorOutput || "Registry check returned errors"}`,
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
        reason: `Registry validation execution failed: ${errorMessage}`,
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
      script: "pnpm check:product-registry",
      timeoutMs: this.timeoutMs,
    };
  }

  async listGates(): Promise<readonly string[]> {
    return ["registry-validation"];
  }
}
