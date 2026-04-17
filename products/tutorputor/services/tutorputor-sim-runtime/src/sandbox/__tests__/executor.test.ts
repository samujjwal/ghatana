/**
 * Sandbox Executor Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for sandbox executor
 * @doc.layer platform
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, afterEach } from "vitest";
import {
  SandboxExecutor,
  type KernelExecutionContext,
} from "../executor";

describe("SandboxExecutor", () => {
  let executor: SandboxExecutor;

  beforeEach(() => {
    executor = new SandboxExecutor({
      timeout: 5000,
      memoryLimit: 256,
    });
  });

  afterEach(() => {
    // Cleanup
  });

  describe("execute", () => {
    it("should execute a simple kernel successfully", async () => {
      const context: KernelExecutionContext = {
        kernelId: "test-kernel-1",
        kernelCode: `
          const result = {
            message: "Hello from sandbox",
            input: input
          };
        `,
        input: { name: "Test" },
      };

      const result = await executor.execute(context);

      expect(result.success).toBe(true);
      expect(result.stdout).toContain("Hello from sandbox");
      expect(result.exitCode).toBe(0);
    });

    it("should handle kernel with syntax errors", async () => {
      const context: KernelExecutionContext = {
        kernelId: "test-kernel-2",
        kernelCode: `
          const result = invalid syntax here
        `,
        input: {},
      };

      const result = await executor.execute(context);

      expect(result.success).toBe(false);
      expect(result.stderr).toBeDefined();
    });

    it("should handle kernel with runtime errors", async () => {
      const context: KernelExecutionContext = {
        kernelId: "test-kernel-3",
        kernelCode: `
          throw new Error("Test error");
        `,
        input: {},
      };

      const result = await executor.execute(context);

      expect(result.success).toBe(false);
      expect(result.stderr).toContain("Test error");
    });

    it("should respect timeout configuration", async () => {
      const executorWithTimeout = new SandboxExecutor({
        timeout: 1000, // 1 second timeout
      });

      const context: KernelExecutionContext = {
        kernelId: "test-kernel-4",
        kernelCode: `
          while (true) {
            // Infinite loop
          }
        `,
        input: {},
      };

      const result = await executorWithTimeout.execute(context);

      expect(result.success).toBe(false);
      expect(result.error).toContain("timeout");
    }, 10000);
  });

  describe("cancelExecution", () => {
    it("should cancel an active execution", async () => {
      const context: KernelExecutionContext = {
        kernelId: "test-kernel-5",
        kernelCode: `
          await new Promise(resolve => setTimeout(resolve, 10000));
          const result = { message: "Should not reach here" };
        `,
        input: {},
      };

      // Start execution but don't await
      const executionPromise = executor.execute(context);

      // Cancel after a short delay
      setTimeout(() => {
        executor.cancelExecution(context.kernelId);
      }, 100);

      const result = await executionPromise;

      expect(result.success).toBe(false);
    });
  });

  describe("getActiveExecutionCount", () => {
    it("should return zero when no executions are active", () => {
      const count = executor.getActiveExecutionCount();
      expect(count).toBe(0);
    });
  });
});
