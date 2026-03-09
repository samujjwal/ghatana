/**
 * Schema migration layer for workspace bundles and telemetry snapshots.
 * Handles version upgrades with audit trail.
 */

import type { WorkspaceBundle, TelemetrySnapshot } from './types';
import type { AuditChain } from './auditChain';

export interface MigrationContext {
  auditChain: AuditChain;
  actor: string;
}

export interface Migration {
  fromVersion: string;
  toVersion: string;
  migrate: (data: any, ctx: MigrationContext) => Promise<any>;
}

export class SchemaMigrator {
  private migrations: Map<string, Migration[]>;

  constructor() {
    this.migrations = new Map();
    this.registerBuiltInMigrations();
  }

  async migrateBundle(
    bundle: WorkspaceBundle,
    targetVersion: string,
    ctx: MigrationContext,
  ): Promise<WorkspaceBundle> {
    const currentVersion = bundle.workspaceVersion;

    if (currentVersion === targetVersion) {
      return bundle;
    }

    const path = this.findMigrationPath(currentVersion, targetVersion);
    if (!path) {
      throw new Error(
        `No migration path from ${currentVersion} to ${targetVersion}`,
      );
    }

    let migrated = bundle;

    for (const migration of path) {
      migrated = await migration.migrate(migrated, ctx);

      await ctx.auditChain.append({
        timestamp: new Date().toISOString(),
        action: 'schema_migration',
        actor: ctx.actor,
        details: {
          from: migration.fromVersion,
          to: migration.toVersion,
          bundleId: bundle.signature.substring(0, 16),
        },
      });
    }

    return migrated;
  }

  async migrateSnapshot(
    snapshot: TelemetrySnapshot,
    targetVersion: string,
    ctx: MigrationContext,
  ): Promise<TelemetrySnapshot> {
    const currentVersion = snapshot.version;

    if (currentVersion === targetVersion) {
      return snapshot;
    }

    const path = this.findMigrationPath(currentVersion, targetVersion);
    if (!path) {
      throw new Error(
        `No migration path from ${currentVersion} to ${targetVersion}`,
      );
    }

    let migrated = snapshot;

    for (const migration of path) {
      migrated = await migration.migrate(migrated, ctx);
    }

    return migrated;
  }

  registerMigration(migration: Migration): void {
    const existing = this.migrations.get(migration.fromVersion) ?? [];
    existing.push(migration);
    this.migrations.set(migration.fromVersion, existing);
  }

  private findMigrationPath(
    from: string,
    to: string,
  ): Migration[] | null {
    // Simple BFS to find migration path
    const queue: { version: string; path: Migration[] }[] = [
      { version: from, path: [] },
    ];
    const visited = new Set<string>([from]);

    while (queue.length > 0) {
      const current = queue.shift()!;

      if (current.version === to) {
        return current.path;
      }

      const migrations = this.migrations.get(current.version) ?? [];

      for (const migration of migrations) {
        if (!visited.has(migration.toVersion)) {
          visited.add(migration.toVersion);
          queue.push({
            version: migration.toVersion,
            path: [...current.path, migration],
          });
        }
      }
    }

    return null;
  }

  private registerBuiltInMigrations(): void {
    // Example: v1.0.0 -> v2.0.0
    this.registerMigration({
      fromVersion: '1.0.0',
      toVersion: '2.0.0',
      migrate: async (data: WorkspaceBundle) => {
        return {
          ...data,
          workspaceVersion: '2.0.0',
          // Add new v2 fields with defaults
          policies: data.policies ?? {
            allowRemote: true,
            requireMTLS: false,
          },
        };
      },
    });

    // Example: v2.0.0 -> v2.1.0
    this.registerMigration({
      fromVersion: '2.0.0',
      toVersion: '2.1.0',
      migrate: async (data: WorkspaceBundle) => {
        return {
          ...data,
          workspaceVersion: '2.1.0',
          // Add retention policy
          policies: {
            ...data.policies,
            retentionDays: data.policies?.retentionDays ?? 30,
          },
        };
      },
    });
  }
}

export const createSchemaMigrator = (): SchemaMigrator => {
  return new SchemaMigrator();
};
