/**
 * ToolchainProvider - interface for executing build tools.
 *
 * @doc.type interface
 * @doc.purpose Toolchain provider interface for build tool execution
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import type { KernelProvider } from "./KernelProvider";

/**
 * Toolchain provider for executing build tools (Gradle, pnpm, Docker, etc.).
 */
export interface ToolchainProvider extends KernelProvider {
  /**
   * Executes a toolchain command.
   */
  executeCommand(
    command: string,
    args: readonly string[],
    cwd?: string
  ): Promise<{
    exitCode: number;
    stdout: string;
    stderr: string;
  }>;

  /**
   * Checks if a tool is available.
   */
  isToolAvailable(tool: string): Promise<boolean>;

  /**
   * Gets tool version.
   */
  getToolVersion(tool: string): Promise<string | null>;
}
