/**
 * FileSourceProvider - implementation of SourceProvider backed by local filesystem.
 *
 * @doc.type class
 * @doc.purpose Local filesystem source provider for bootstrap/platform mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import { spawnSync } from "node:child_process";
import type { SourceProvider } from "@ghatana/kernel-product-contracts";

export interface FileSourceProviderOptions {
  readonly repoRoot: string;
}

/**
 * FileSourceProvider provides source code operations using local git commands.
 */
export class FileSourceProvider implements SourceProvider {
  readonly providerId = "file-source";
  readonly version = "1.0.0";
  readonly capabilities = ["getCurrentRef", "getCommitHash", "triggerBuild", "getBuildStatus"] as const;
  readonly backingStore = "file" as const;

  private readonly repoRoot: string;

  constructor(options: FileSourceProviderOptions | string) {
    this.repoRoot =
      typeof options === "string"
        ? options
        : options.repoRoot;
  }

  async getCurrentRef(): Promise<string> {
    const result = spawnSync("git", ["rev-parse", "--abbrev-ref", "HEAD"], {
      cwd: this.repoRoot,
      encoding: "utf8",
    });
    if (result.error) {
      throw new Error(`Failed to get current ref: ${result.error.message}`);
    }
    if (result.status !== 0) {
      throw new Error(`Failed to get current ref: ${result.stderr}`);
    }
    return result.stdout.trim();
  }

  async getCommitHash(ref: string): Promise<string> {
    const result = spawnSync("git", ["rev-parse", ref], {
      cwd: this.repoRoot,
      encoding: "utf8",
    });
    if (result.error) {
      throw new Error(`Failed to get commit hash for ${ref}: ${result.error.message}`);
    }
    if (result.status !== 0) {
      throw new Error(`Failed to get commit hash for ${ref}: ${result.stderr}`);
    }
    return result.stdout.trim();
  }

  async triggerBuild(ref: string): Promise<{ buildId: string; status: string }> {
    // In local mode, we don't trigger remote builds
    // Return a placeholder build ID
    const buildId = `local-${ref}-${Date.now()}`;
    return {
      buildId,
      status: "not_applicable",
    };
  }

  async getBuildStatus(buildId: string): Promise<{
    status: string;
    completed: boolean;
    success: boolean;
  }> {
    // In local mode, builds are not tracked remotely
    return {
      status: "not_applicable",
      completed: true,
      success: true,
    };
  }
}
