/**
 * SourceProvider - interface for accessing source code and triggering builds.
 *
 * @doc.type interface
 * @doc.purpose Source provider interface for source code operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import { z } from "zod";
import type { KernelProvider } from "./KernelProvider.js";

/**
 * Source provider for accessing source code and triggering builds.
 */
export interface SourceProvider extends KernelProvider {
  /**
   * Gets the current branch or reference.
   */
  getCurrentRef(): Promise<string>;

  /**
   * Gets the commit hash for a reference.
   */
  getCommitHash(ref: string): Promise<string>;

  /**
   * Triggers a build for a reference.
   */
  triggerBuild(ref: string): Promise<{ buildId: string; status: string }>;

  /**
   * Gets build status.
   */
  getBuildStatus(buildId: string): Promise<{
    status: string;
    completed: boolean;
    success: boolean;
  }>;
}

export const SourceProviderSchema = z.custom<SourceProvider>(
  (value) => {
    if (typeof value !== "object" || value === null) {
      return false;
    }
    const provider = value as Record<string, unknown>;
    return (
      typeof provider.getCurrentRef === "function" &&
      typeof provider.getCommitHash === "function" &&
      typeof provider.triggerBuild === "function" &&
      typeof provider.getBuildStatus === "function"
    );
  },
  "SourceProvider requires source and build functions"
);

export function validateSourceProvider(
  value: unknown
): value is SourceProvider {
  return SourceProviderSchema.safeParse(value).success;
}
