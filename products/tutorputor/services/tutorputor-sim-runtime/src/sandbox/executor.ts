/**
 * Sandbox Executor for Kernel Execution
 *
 * Provides isolated execution environment for kernels using Deno runtime.
 * Ensures kernels cannot access host system resources.
 *
 * @doc.type service
 * @doc.purpose Isolated kernel execution sandbox
 * @doc.layer platform
 * @doc.pattern SandboxService
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import { spawn, ChildProcess } from "child_process";
import path from "path";

const logger = createStandaloneLogger({ component: "SandboxExecutor" });

/**
 * Sandbox execution configuration
 */
export interface SandboxConfig {
  timeout?: number; // Execution timeout in milliseconds (default: 30000)
  memoryLimit?: number; // Memory limit in MB (default: 512)
  cpuLimit?: number; // CPU limit as percentage (default: 100)
  allowNetwork?: boolean; // Allow network access (default: false)
  allowFileSystem?: boolean; // Allow file system access (default: false)
  workingDirectory?: string; // Working directory for execution
}

/**
 * Sandbox execution result
 */
export interface SandboxResult {
  success: boolean;
  stdout: string;
  stderr: string;
  exitCode: number | null;
  signal: string | null;
  executionTime: number;
  memoryUsed: number;
  error?: string;
}

/**
 * Kernel execution context
 */
export interface KernelExecutionContext {
  kernelId: string;
  kernelCode: string;
  input: unknown;
  config?: SandboxConfig;
}

/**
 * Sandbox Executor
 */
export class SandboxExecutor {
  private defaultConfig: Required<SandboxConfig>;
  private activeExecutions: Map<string, ChildProcess> = new Map();

  constructor(config: SandboxConfig = {}) {
    this.defaultConfig = {
      timeout: config.timeout || 30000,
      memoryLimit: config.memoryLimit || 512,
      cpuLimit: config.cpuLimit || 100,
      allowNetwork: config.allowNetwork || false,
      allowFileSystem: config.allowFileSystem || false,
      workingDirectory: config.workingDirectory || "/tmp/tutorputor-sandbox",
    };
  }

  /**
   * Execute a kernel in the sandbox
   */
  async execute(context: KernelExecutionContext): Promise<SandboxResult> {
    const startTime = Date.now();
    const config = { ...this.defaultConfig, ...context.config };
    const executionId = `${context.kernelId}-${Date.now()}`;

    logger.info({
      kernelId: context.kernelId,
      executionId,
      config,
    }, "Starting sandbox execution");

    try {
      // Prepare sandbox environment
      const sandboxDir = await this.prepareSandbox(config.workingDirectory, executionId);

      // Write kernel code to file
      const kernelFile = path.join(sandboxDir, "kernel.js");
      await this.writeFile(kernelFile, this.wrapKernelCode(context.kernelCode, context.input));

      // Execute kernel in Deno sandbox
      const result = await this.executeInDeno(kernelFile, config, executionId);

      const executionTime = Date.now() - startTime;

      logger.info({
        kernelId: context.kernelId,
        executionId,
        executionTime,
        success: result.success,
      }, "Sandbox execution completed");

      return {
        ...result,
        executionTime,
      };
    } catch (error) {
      const executionTime = Date.now() - startTime;
      const errorMessage = error instanceof Error ? error.message : String(error);

      logger.error({
        kernelId: context.kernelId,
        executionId,
        error: errorMessage,
      }, "Sandbox execution failed");

      return {
        success: false,
        stdout: "",
        stderr: errorMessage,
        exitCode: null,
        signal: null,
        executionTime,
        memoryUsed: 0,
        error: errorMessage,
      };
    } finally {
      // Cleanup sandbox
      await this.cleanupSandbox(config.workingDirectory, executionId);
    }
  }

  /**
   * Cancel an active execution
   */
  cancelExecution(kernelId: string): boolean {
    const process = this.activeExecutions.get(kernelId);
    if (process) {
      process.kill("SIGKILL");
      this.activeExecutions.delete(kernelId);
      logger.info({ kernelId }, "Execution cancelled");
      return true;
    }
    return false;
  }

  /**
   * Get active execution count
   */
  getActiveExecutionCount(): number {
    return this.activeExecutions.size;
  }

  /**
   * Prepare sandbox directory
   */
  private async prepareSandbox(baseDir: string, executionId: string): Promise<string> {
    const fs = await import("fs/promises");
    const sandboxDir = path.join(baseDir, executionId);

    await fs.mkdir(sandboxDir, { recursive: true });

    return sandboxDir;
  }

  /**
   * Cleanup sandbox directory
   */
  private async cleanupSandbox(baseDir: string, executionId: string): Promise<void> {
    const fs = await import("fs/promises");
    const sandboxDir = path.join(baseDir, executionId);

    try {
      await fs.rm(sandboxDir, { recursive: true, force: true });
    } catch (error) {
      logger.warn({
        sandboxDir,
        error: error instanceof Error ? error.message : String(error),
      }, "Failed to cleanup sandbox directory");
    }
  }

  /**
   * Write file to disk
   */
  private async writeFile(filePath: string, content: string): Promise<void> {
    const fs = await import("fs/promises");
    await fs.writeFile(filePath, content, "utf-8");
  }

  /**
   * Wrap kernel code with sandbox isolation
   */
  private wrapKernelCode(code: string, input: unknown): string {
    return `
// Sandbox isolation wrapper
const input = ${JSON.stringify(input)};

// Kernel code starts here
${code}

// Kernel code ends here

// Export result if not already done
if (typeof result !== 'undefined') {
  console.log(JSON.stringify({ success: true, data: result }));
} else {
  console.log(JSON.stringify({ success: false, error: "No result returned" }));
}
`;
  }

  /**
   * Execute code in Deno sandbox
   */
  private async executeInDeno(
    kernelFile: string,
    config: Required<SandboxConfig>,
    executionId: string,
  ): Promise<Omit<SandboxResult, "executionTime">> {
    return new Promise((resolve, reject) => {
      const args = [
        "run",
        "--allow-none", // No permissions by default
        "--no-prompt", // Don't prompt for permissions
        "--quiet", // Suppress output
      ];

      // Add permissions if configured
      if (config.allowNetwork) {
        args.push("--allow-net");
      }
      if (config.allowFileSystem) {
        args.push("--allow-read", "--allow-write");
      }

      // Add resource limits (Deno doesn't support all, we handle timeout manually)
      args.push(kernelFile);

      const denoProcess = spawn("deno", args, {
        cwd: path.dirname(kernelFile),
        env: {
          ...process.env,
          DENO_NO_PROMPT: "1",
        },
      });

      this.activeExecutions.set(executionId, denoProcess);

      let stdout = "";
      let stderr = "";

      denoProcess.stdout?.on("data", (data) => {
        stdout += data.toString();
      });

      denoProcess.stderr?.on("data", (data) => {
        stderr += data.toString();
      });

      // Set up timeout
      const timeout = setTimeout(() => {
        denoProcess.kill("SIGKILL");
        this.activeExecutions.delete(executionId);
        reject(new Error(`Execution timeout after ${config.timeout}ms`));
      }, config.timeout);

      denoProcess.on("close", (code, signal) => {
        clearTimeout(timeout);
        this.activeExecutions.delete(executionId);

        resolve({
          success: code === 0,
          stdout: stdout.trim(),
          stderr: stderr.trim(),
          exitCode: code,
          signal: signal,
          memoryUsed: 0, // Deno doesn't provide this easily
        });
      });

      denoProcess.on("error", (error) => {
        clearTimeout(timeout);
        this.activeExecutions.delete(executionId);
        reject(error);
      });
    });
  }
}

/**
 * Singleton instance
 */
export const sandboxExecutor = new SandboxExecutor();
