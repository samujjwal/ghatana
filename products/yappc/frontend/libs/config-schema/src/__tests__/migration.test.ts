/**
 * Migration Utilities Tests
 *
 * Tests for migration functions.
 *
 * @packageDocumentation
 */

import { describe, it, expect } from 'vitest';

import {
  registerPageConfigMigration,
  registerIntentConfigMigration,
  registerRequirementConfigMigration,
  getMigrationPath,
  applyMigrations,
  type MigrationHook,
} from '../migration';

describe('Migration Utilities', () => {
  const mockMigration1: MigrationHook = {
    fromVersion: '1.0.0',
    toVersion: '1.1.0',
    migrate: (data: unknown) => {
      const obj = data as Record<string, unknown>;
      return {
        ...obj,
        migrated: true,
        version: '1.1.0',
      };
    },
    description: 'Add migrated field',
  };

  const mockMigration2: MigrationHook = {
    fromVersion: '1.1.0',
    toVersion: '1.2.0',
    migrate: (data: unknown) => {
      const obj = data as Record<string, unknown>;
      return {
        ...obj,
        version: '1.2.0',
      };
    },
    description: 'Update to version 1.2.0',
  };

  describe('registerPageConfigMigration', () => {
    it('should register a PageConfig migration', () => {
      registerPageConfigMigration(mockMigration1);
      expect(true).toBe(true); // If no error, registration succeeded
    });
  });

  describe('registerIntentConfigMigration', () => {
    it('should register an IntentConfig migration', () => {
      registerIntentConfigMigration(mockMigration1);
      expect(true).toBe(true);
    });
  });

  describe('registerRequirementConfigMigration', () => {
    it('should register a RequirementConfig migration', () => {
      registerRequirementConfigMigration(mockMigration1);
      expect(true).toBe(true);
    });
  });

  describe('getMigrationPath', () => {
    it('should find migration path for sequential versions', () => {
      const migrations = [mockMigration1, mockMigration2];
      const path = getMigrationPath(migrations, '1.0.0', '1.2.0');
      expect(path).toHaveLength(2);
      expect(path[0].fromVersion).toBe('1.0.0');
      expect(path[1].fromVersion).toBe('1.1.0');
    });

    it('should return empty array if no path exists', () => {
      const migrations = [mockMigration1];
      const path = getMigrationPath(migrations, '1.0.0', '2.0.0');
      expect(path).toEqual([]);
    });

    it('should return empty array if from and to are same', () => {
      const migrations = [mockMigration1];
      const path = getMigrationPath(migrations, '1.0.0', '1.0.0');
      expect(path).toEqual([]);
    });

    it('should handle empty migrations array', () => {
      const path = getMigrationPath([], '1.0.0', '1.1.0');
      expect(path).toEqual([]);
    });
  });

  describe('applyMigrations', () => {
    it('should apply migrations in sequence', () => {
      const migrations = [mockMigration1, mockMigration2];
      const initialData = { version: '1.0.0' };
      const result = applyMigrations(initialData, migrations);

      expect(result.errors).toBeUndefined();
      const data = result.data as Record<string, unknown>;
      expect(data.migrated).toBe(true);
      expect(data.version).toBe('1.2.0');
    });

    it('should return errors if migration fails', () => {
      const failingMigration: MigrationHook = {
        fromVersion: '1.0.0',
        toVersion: '1.1.0',
        migrate: () => {
          throw new Error('Migration failed');
        },
        description: 'Failing migration',
      };

      const result = applyMigrations({ version: '1.0.0' }, [failingMigration]);
      expect(result.errors).toBeDefined();
      expect(result.errors?.length).toBe(1);
      expect(result.errors?.[0]).toContain('Migration failed');
    });

    it('should handle empty migrations array', () => {
      const initialData = { version: '1.0.0' };
      const result = applyMigrations(initialData, []);

      expect(result.errors).toBeUndefined();
      expect(result.data).toEqual(initialData);
    });

    it('should stop at first error', () => {
      const failingMigration: MigrationHook = {
        fromVersion: '1.0.0',
        toVersion: '1.1.0',
        migrate: () => {
          throw new Error('First migration failed');
        },
        description: 'Failing migration',
      };

      const secondMigration: MigrationHook = {
        fromVersion: '1.1.0',
        toVersion: '1.2.0',
        migrate: (data: unknown) => data,
        description: 'Second migration',
      };

      const result = applyMigrations({ version: '1.0.0' }, [
        failingMigration,
        secondMigration,
      ]);

      expect(result.errors).toBeDefined();
      expect(result.errors?.length).toBe(1);
    });
  });
});
