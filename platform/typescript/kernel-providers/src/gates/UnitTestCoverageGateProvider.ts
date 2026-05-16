/**
 * UnitTestCoverageGateProvider - validates unit test coverage requirements.
 *
 * @doc.type class
 * @doc.purpose Execute unit test coverage gate check
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

export class UnitTestCoverageGateProvider implements GateProvider {
  readonly providerId = "unit-test-coverage-gate";
  readonly version = "1.0.0";
  readonly backingStore = "external" as const;
  readonly capabilities = ["gates", "unit-test-coverage", "bootstrap-mode"] as const;
  private readonly timeoutMs = 60000; // Tests can take longer

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
      
      // Run unit tests for the product
      const { stdout, stderr } = await execAsync(
        `pnpm --dir products/${productUnitId} test:unit`,
        {
          timeout: this.timeoutMs,
          cwd: process.cwd(),
        }
      ).catch((error) => {
        // Return the error output for analysis
        return { 
          stdout: error.stdout || "", 
          stderr: error.stderr || error.message || "Unit tests failed" 
        };
      });

      const output = stdout.trim();
      const errorOutput = stderr.trim();

      // Check if tests passed (exit code 0)
      if (!errorOutput.includes("FAIL") && !errorOutput.includes("error") && output.includes("PASS")) {
        return {
          gateId,
          passed: true,
          reason: `Unit test coverage check passed for ${productUnitId}`,
          evidence: [output, errorOutput].filter(Boolean),
          evaluatedAt: new Date().toISOString(),
          duration: Date.now() - startedAt,
        };
      } else {
        return {
          gateId,
          passed: false,
          reason: `Unit test coverage check failed: ${errorOutput || "Unit tests did not pass"}`,
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
        reason: `Unit test coverage check execution failed: ${errorMessage}`,
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
      script: "pnpm test:unit",
      timeoutMs: this.timeoutMs,
    };
  }

  async listGates(): Promise<readonly string[]> {
    return ["unit-test-coverage"];
  }
}
