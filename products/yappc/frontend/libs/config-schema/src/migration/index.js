/**
 * Migration utilities
 *
 * Provides migration hooks for schema version changes.
 * Follows SchemaRegistry pattern from existing YAPPC implementation.
 *
 * @packageDocumentation
 */
/**
 * PageConfig migration registry
 */
export const pageConfigMigrations = [];
/**
 * IntentConfig migration registry
 */
export const intentConfigMigrations = [];
/**
 * RequirementConfig migration registry
 */
export const requirementConfigMigrations = [];
/**
 * Register a PageConfig migration
 */
export function registerPageConfigMigration(migration) {
    pageConfigMigrations.push(migration);
}
/**
 * Register an IntentConfig migration
 */
export function registerIntentConfigMigration(migration) {
    intentConfigMigrations.push(migration);
}
/**
 * Register a RequirementConfig migration
 */
export function registerRequirementConfigMigration(migration) {
    requirementConfigMigrations.push(migration);
}
/**
 * Get migration path for a schema
 */
export function getMigrationPath(migrations, fromVersion, toVersion) {
    const path = [];
    let currentVersion = fromVersion;
    while (currentVersion !== toVersion) {
        const next = migrations.find((m) => m.fromVersion === currentVersion);
        if (!next)
            break;
        path.push(next);
        currentVersion = next.toVersion;
        // Prevent infinite loops
        if (path.length > 100)
            break;
    }
    return currentVersion === toVersion ? path : [];
}
/**
 * Apply migrations to data
 */
export function applyMigrations(data, migrations) {
    let migratedData = data;
    const errors = [];
    for (const migration of migrations) {
        try {
            migratedData = migration.migrate(migratedData);
        }
        catch (error) {
            errors.push(`Migration ${migration.fromVersion} → ${migration.toVersion} failed: ${error}`);
            break;
        }
    }
    return { data: migratedData, errors: errors.length > 0 ? errors : undefined };
}
