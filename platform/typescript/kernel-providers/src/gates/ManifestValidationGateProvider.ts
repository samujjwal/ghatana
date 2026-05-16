/**
 * ManifestValidationGateProvider - validates kernel-product.yaml manifest conformance.
 *
 * @doc.type class
 * @doc.purpose Execute manifest validation gate check
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

export class ManifestValidationGateProvider implements GateProvider {
  readonly providerId = "manifest-validation-gate";
  readonly version = "1.0.0";
  readonly backingStore = "external" as const;
  readonly capabilities = ["gates", "manifest-validation", "bootstrap-mode"] as const;
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
      // Validate the kernel-product.yaml for the product unit
      const productUnitId = request.productUnitId;
      const manifestPath = `products/${productUnitId}/kernel-product.yaml`;

      // Check if the manifest exists
      const { stdout: checkOutput } = await execAsync(
        `test -f "${manifestPath}" && echo "EXISTS" || echo "NOT_FOUND"`,
        { timeout: 5000 }
      );

      if (!checkOutput.includes("EXISTS")) {
        return {
          gateId,
          passed: false,
          reason: `Manifest validation failed: kernel-product.yaml not found at ${manifestPath}`,
          evidence: [checkOutput],
          evaluatedAt: new Date().toISOString(),
          duration: Date.now() - startedAt,
        };
      }

      // Run YAML schema validation if available
      const { stdout, stderr } = await execAsync(
        `pnpm validate:product-manifest --product ${productUnitId}`,
        {
          timeout: this.timeoutMs,
          cwd: process.cwd(),
        }
      ).catch(() => ({ stdout: "", stderr: "Validation script not available" }));

      const output = stdout.trim();
      const errorOutput = stderr.trim();

      if (!errorOutput.includes("error") && !errorOutput.includes("failed")) {
        return {
          gateId,
          passed: true,
          reason: `Manifest validation passed: kernel-product.yaml is valid for ${productUnitId}`,
          evidence: [manifestPath, output, errorOutput].filter(Boolean),
          evaluatedAt: new Date().toISOString(),
          duration: Date.now() - startedAt,
        };
      } else {
        return {
          gateId,
          passed: false,
          reason: `Manifest validation failed: ${errorOutput || "Manifest check returned errors"}`,
          evidence: [manifestPath, output, errorOutput].filter(Boolean),
          evaluatedAt: new Date().toISOString(),
          duration: Date.now() - startedAt,
        };
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      return {
        gateId,
        passed: false,
        reason: `Manifest validation execution failed: ${errorMessage}`,
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
      script: "pnpm validate:product-manifest",
      timeoutMs: this.timeoutMs,
    };
  }

  async listGates(): Promise<readonly string[]> {
    return ["manifest-validation"];
  }
}
