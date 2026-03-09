/**
 * Schema Registry - Central registry for Zod schemas with validation
 *
 * <p><b>Architecture Role</b><br>
 * Provides centralized schema validation and versioning for component data,
 * API contracts, and domain models. Uses canonical types from @ghatana/yappc-types.
 *
 * @doc.type class
 * @doc.purpose Schema registry with migration support
 * @doc.layer product
 * @doc.pattern Registry
 */

import { z } from 'zod';

import type {
  SchemaDefinition,
  RegistryEntry,
  ValidationResult,
  MigrationHook,
} from '@ghatana/yappc-types';

/**
 *
 */
class SchemaRegistryClass {
  private schemas: Map<string, RegistryEntry<SchemaDefinition>> = new Map();
  private migrations: Map<string, MigrationHook[]> = new Map();

  /**
   * Register a schema definition
   */
  register(schema: SchemaDefinition): void {
    const key = this.makeKey(schema.name, schema.version);

    if (this.schemas.has(key)) {
      throw new Error(`Schema already registered: ${key}`);
    }

    const entry: RegistryEntry<SchemaDefinition> = {
      key,
      namespace: 'schema', // Required property for RegistryEntry
      value: schema,
      registeredAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    this.schemas.set(key, entry);
  }

  /**
   * Register a migration hook
   */
  registerMigration(schemaName: string, migration: MigrationHook): void {
    if (!this.migrations.has(schemaName)) {
      this.migrations.set(schemaName, []);
    }

    this.migrations.get(schemaName)!.push(migration);
  }

  /**
   * Get schema by name and version
   */
  get(name: string, version: string): SchemaDefinition | null {
    const key = this.makeKey(name, version);
    const entry = this.schemas.get(key);
    return entry ? entry.value : null;
  }

  /**
   * Get latest version of schema
   */
  getLatest(name: string): SchemaDefinition | null {
    const versions = this.listVersions(name);
    if (versions.length === 0) return null;

    // Sort by semantic version (simple implementation)
    versions.sort((a, b) => this.compareVersions(b, a));
    return this.get(name, versions[0]);
  }

  /**
   * List all versions of a schema
   */
  listVersions(name: string): string[] {
    const versions: string[] = [];

    for (const entry of this.schemas.values()) {
      if (entry.value.name === name) {
        versions.push(entry.value.version);
      }
    }

    return versions;
  }

  /**
   * Validate data against schema
   */
  validate(name: string, version: string, data: unknown): ValidationResult {
    const schema = this.get(name, version);

    if (!schema) {
      return {
        valid: false,
        errors: [`Schema not found: ${name}@${version}`],
      };
    }

    try {
      schema.schema.parse(data);
      return { valid: true };
    } catch (error) {
      if (error instanceof z.ZodError) {
        return {
          valid: false,
          errors: error.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`),
        };
      }

      return {
        valid: false,
        errors: ['Unknown validation error'],
      };
    }
  }

  /**
   * Migrate data from one version to another
   *
   * @param schemaName - Name of the schema
   * @param data - Data to migrate
   * @param fromVersion - Source version
   * @param toVersion - Target version
   * @returns Migrated data or errors
   */
  migrate(
    schemaName: string,
    data: unknown,
    fromVersion: string,
    toVersion: string,
  ): { data: unknown; errors?: string[] } {
    const migrations = this.migrations.get(schemaName) || [];
    
    // Find migration path
    const path = this.findMigrationPath(migrations, fromVersion, toVersion);

    if (path.length === 0) {
      return {
        data,
        errors: [`No migration path from ${fromVersion} to ${toVersion}`],
      };
    }

    // Apply migrations in sequence
    let migratedData: unknown = data;
    const errors: string[] = [];

    for (const migration of path) {
      try {
        migratedData = migration.migrate(migratedData);
      } catch (error) {
        errors.push(
          `Migration ${migration.fromVersion} → ${migration.toVersion} failed: ${error}`,
        );
        break;
      }
    }

    return { data: migratedData, errors: errors.length > 0 ? errors : undefined };
  }

  /**
   * Check if schema exists
   */
  has(name: string, version: string): boolean {
    return this.schemas.has(this.makeKey(name, version));
  }

  /**
   * List all schemas
   */
  list(): SchemaDefinition[] {
    return Array.from(this.schemas.values()).map((entry) => entry.value);
  }

  /**
   * Remove schema
   */
  remove(name: string, version: string): boolean {
    return this.schemas.delete(this.makeKey(name, version));
  }

  /**
   * Clear all schemas
   */
  clear(): void {
    this.schemas.clear();
    this.migrations.clear();
  }

  /**
   *
   */
  private makeKey(name: string, version: string): string {
    return `${name}@${version}`;
  }

  /**
   *
   */
  private compareVersions(a: string, b: string): number {
    const aParts = a.split('.').map(Number);
    const bParts = b.split('.').map(Number);

    for (let i = 0; i < Math.max(aParts.length, bParts.length); i++) {
      const aNum = aParts[i] || 0;
      const bNum = bParts[i] || 0;

      if (aNum !== bNum) {
        return aNum - bNum;
      }
    }

    return 0;
  }

  /**
   *
   */
  private findMigrationPath(
    migrations: MigrationHook[],
    fromVersion: string,
    toVersion: string,
  ): MigrationHook[] {
    // Simple linear path finding
    const path: MigrationHook[] = [];
    let currentVersion = fromVersion;

    while (currentVersion !== toVersion) {
      const next = migrations.find((m) => m.fromVersion === currentVersion);
      
      if (!next) break;
      
      path.push(next);
      currentVersion = next.toVersion;

      // Prevent infinite loops
      if (path.length > 100) break;
    }

    return currentVersion === toVersion ? path : [];
  }
}

// Singleton instance
export const SchemaRegistry = new SchemaRegistryClass();
