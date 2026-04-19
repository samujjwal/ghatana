/**
 * Migration utilities
 *
 * Provides migration hooks for schema version changes.
 * Follows SchemaRegistry pattern from existing YAPPC implementation.
 *
 * @packageDocumentation
 */

/**
 * Migration hook type for config schemas
 */
export interface MigrationHook {
  fromVersion: string;
  toVersion: string;
  migrate: (data: unknown) => unknown;
  description: string;
}

/**
 * PageConfig migration registry
 */
export const pageConfigMigrations: MigrationHook[] = [];

/**
 * IntentConfig migration registry
 */
export const intentConfigMigrations: MigrationHook[] = [];

/**
 * RequirementConfig migration registry
 */
export const requirementConfigMigrations: MigrationHook[] = [];

/**
 * Register a PageConfig migration
 */
export function registerPageConfigMigration(migration: MigrationHook): void {
  pageConfigMigrations.push(migration);
}

/**
 * Register an IntentConfig migration
 */
export function registerIntentConfigMigration(migration: MigrationHook): void {
  intentConfigMigrations.push(migration);
}

/**
 * Register a RequirementConfig migration
 */
export function registerRequirementConfigMigration(
  migration: MigrationHook
): void {
  requirementConfigMigrations.push(migration);
}

/**
 * Get migration path for a schema
 */
export function getMigrationPath(
  migrations: MigrationHook[],
  fromVersion: string,
  toVersion: string
): MigrationHook[] {
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

/**
 * Apply migrations to data
 */
export function applyMigrations(
  data: unknown,
  migrations: MigrationHook[]
): { data: unknown; errors?: string[] } {
  let migratedData: unknown = data;
  const errors: string[] = [];

  for (const migration of migrations) {
    try {
      migratedData = migration.migrate(migratedData);
    } catch (error) {
      errors.push(
        `Migration ${migration.fromVersion} → ${migration.toVersion} failed: ${error}`
      );
      break;
    }
  }

  return { data: migratedData, errors: errors.length > 0 ? errors : undefined };
}
