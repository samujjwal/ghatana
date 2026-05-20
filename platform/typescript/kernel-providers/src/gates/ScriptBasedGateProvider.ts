/**
 * ScriptBasedGateProvider - executes gate checks by running shell scripts.
 *
 * @doc.type class
 * @doc.purpose Execute gate checks through shell scripts with proper validation
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
const NO_SCRIPT_GATES: readonly string[] = Object.freeze([]);

export interface ScriptBasedGateProviderOptions {
  readonly providerId: string;
  readonly version: string;
  readonly capabilities: readonly string[];
  readonly scriptPath: string;
  readonly scriptArgs?: readonly string[];
  readonly timeoutMs?: number;
}

export class ScriptBasedGateProvider implements GateProvider {
  readonly providerId: string;
  readonly version: string;
  readonly backingStore = "external" as const;
  readonly capabilities: readonly string[];
  private readonly scriptPath: string;
  private readonly scriptArgs: readonly string[];
  private readonly timeoutMs: number;

  constructor(options: ScriptBasedGateProviderOptions) {
    this.providerId = options.providerId;
    this.version = options.version;
    this.capabilities = options.capabilities;
    this.scriptPath = options.scriptPath;
    this.scriptArgs = options.scriptArgs ?? [];
    this.timeoutMs = options.timeoutMs ?? 30000;
  }

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
      const args = [...this.scriptArgs, gateId];
      const { stdout, stderr } = await execAsync(
        `"${this.scriptPath}" ${args.map((a) => `"${a}"`).join(" ")}`,
        {
          timeout: this.timeoutMs,
        }
      );

      const output = stdout.trim();
      const errorOutput = stderr.trim();

      // Script should exit with 0 for success, non-zero for failure
      if (output.includes("PASS") || output.includes("SUCCESS")) {
        return {
          gateId,
          passed: true,
          reason: `Gate ${gateId} passed: ${output}`,
          evidence: [output, errorOutput].filter(Boolean),
          evaluatedAt: new Date().toISOString(),
          duration: Date.now() - startedAt,
        };
      } else {
        return {
          gateId,
          passed: false,
          reason: `Gate ${gateId} failed: ${output || errorOutput || "Script returned non-zero exit code"}`,
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
        reason: `Gate ${gateId} execution failed: ${errorMessage}`,
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
      mode: "script-based",
      providerId: this.providerId,
      scriptPath: this.scriptPath,
      timeoutMs: this.timeoutMs,
    };
  }

  async listGates(): Promise<readonly string[]> {
    // Script-based providers typically handle a single gate or a related group
    // Return empty array - gate registration happens at factory level
    return Array.from(NO_SCRIPT_GATES);
  }
}
