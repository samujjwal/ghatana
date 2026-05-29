/**
 * PluginHealthSnapshot - health snapshot for plugins.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for plugin operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import { z } from "zod";
import { HealthStatusSchema, type HealthStatus } from "./HealthStatus.js";

/**
 * Plugin health status.
 */
export interface PluginHealthStatus {
  readonly pluginId: string;
  readonly pluginKind: string;
  readonly status: HealthStatus;
  readonly message: string;
  readonly lastExecuted: string;
}

export const PluginHealthStatusSchema = z
  .object({
    pluginId: z.string().trim().min(1),
    pluginKind: z.string().trim().min(1),
    status: HealthStatusSchema,
    message: z.string().trim().min(1),
    lastExecuted: z.string().datetime({ offset: true }),
  })
  .strict();

/**
 * Plugin health snapshot.
 */
export interface PluginHealthSnapshot {
  /**
   * ProductUnit identifier (optional for global plugins).
   */
  readonly productUnitId?: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Plugin health statuses.
   */
  readonly plugins: readonly PluginHealthStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}

export const PluginHealthSnapshotSchema = z
  .object({
    productUnitId: z.string().trim().min(1).optional(),
    status: HealthStatusSchema,
    plugins: z.array(PluginHealthStatusSchema),
    snapshotAt: z.string().datetime({ offset: true }),
  })
  .strict();

export function validatePluginHealthStatus(
  value: unknown
): value is PluginHealthStatus {
  return PluginHealthStatusSchema.safeParse(value).success;
}

export function validatePluginHealthSnapshot(
  value: unknown
): value is PluginHealthSnapshot {
  return PluginHealthSnapshotSchema.safeParse(value).success;
}
