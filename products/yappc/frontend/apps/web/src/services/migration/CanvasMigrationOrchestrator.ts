/**
 * Canvas Migration Execution Script
 * Orchestrates Phase 2 migration activities: unified registry and hook migration
 */

import {
  initializeRegistries,
  RegistryMigration,
  REGISTRY_NAMESPACES,
} from '../registry/RegistryMigration';
import { UnifiedRegistry } from '../registry/UnifiedRegistry';
import { logger } from '../../utils/Logger';

import type { ComponentDefinition } from '@ghatana/yappc-types';

/**
 *
 */
export interface MigrationOptions {
  dryRun?: boolean;
  backupExisting?: boolean;
  validateIntegrity?: boolean;
  createReports?: boolean;
}

/**
 *
 */
export interface MigrationResult {
  success: boolean;
  registryStats?: unknown;
  migratedComponents: number;
  validationResults?: unknown;
  backupFiles?: string[];
  errors?: string[];
  warnings?: string[];
}

/**
 * Phase 2 Migration Orchestrator
 */
export class CanvasMigrationOrchestrator {
  private registry: UnifiedRegistry<ComponentDefinition>;
  private migrationLog: string[] = [];

  /**
   *
   */
  constructor() {
    this.registry = new UnifiedRegistry<ComponentDefinition>();
  }

  /**
   * Execute complete Phase 2 migration
   */
  async executePhase2Migration(
    options: MigrationOptions = {}
  ): Promise<MigrationResult> {
    const {
      dryRun = false,
      backupExisting = true,
      validateIntegrity = true,
      createReports = true,
    } = options;

    this.log('🚀 Starting Phase 2 Canvas Migration');
    this.log('Phase: Unified Registry and Hook Migration');

    const result: MigrationResult = {
      success: false,
      migratedComponents: 0,
      errors: [],
      warnings: [],
    };

    try {
      // Step 1: Initialize unified registry
      this.log('📋 Step 1: Initializing unified registry...');
      const registryInit = initializeRegistries();
      this.registry = registryInit.componentRegistry;
      result.registryStats = registryInit.stats;
      this.log(
        `✅ Registry initialized with ${registryInit.stats.totalEntries} components across ${registryInit.stats.namespaces} namespaces`
      );

      // Step 2: Migrate legacy component registries
      this.log('🔄 Step 2: Migrating legacy component registries...');
      const legacyRegistries = await this.discoverLegacyRegistries();

      for (const legacyRegistry of legacyRegistries) {
        if (!dryRun) {
          await this.migrateLegacyRegistry(legacyRegistry);
        } else {
          this.log(
            `[DRY RUN] Would migrate registry: ${legacyRegistry.name} (${legacyRegistry.components.length} components)`
          );
        }
        result.migratedComponents += legacyRegistry.components.length;
      }

      // Step 3: Validate registry integrity
      if (validateIntegrity) {
        this.log('🔍 Step 3: Validating registry integrity...');
        const validation = RegistryMigration.validateRegistry(this.registry);
        result.validationResults = validation;

        if (!validation.isValid) {
          result.errors?.push(...validation.errors);
        }
        if (validation.warnings.length > 0) {
          result.warnings?.push(...validation.warnings);
        }

        this.log(
          `✅ Validation complete - ${validation.errors.length} errors, ${validation.warnings.length} warnings`
        );
      }

      // Step 4: Create migration reports
      if (createReports && !dryRun) {
        this.log('📊 Step 4: Creating migration reports...');
        await this.createMigrationReports();
      }

      // Step 5: Create backup of registry data
      if (backupExisting && !dryRun) {
        this.log('💾 Step 5: Creating backup...');
        const backupFile = await this.createRegistryBackup();
        result.backupFiles = [backupFile];
      }

      result.success = true;
      this.log('🎉 Phase 2 migration completed successfully!');
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Unknown error';
      result.errors?.push(errorMessage);
      this.log(`❌ Migration failed: ${errorMessage}`);
    }

    return result;
  }

  /**
   * Discover existing legacy registries in the codebase
   */
  private async discoverLegacyRegistries(): Promise<
    Array<{ name: string; namespace: string; components: unknown[] }>
  > {
    // In a real implementation, this would scan the filesystem
    // For now, return mock legacy registries for demonstration
    return [
      {
        name: 'CanvasScene Components',
        namespace: REGISTRY_NAMESPACES.CANVAS_SCENE,
        components: [
          {
            id: 'scene-node',
            label: 'Scene Node',
            category: 'scene',
            type: 'node',
          },
          {
            id: 'scene-connection',
            label: 'Scene Connection',
            category: 'scene',
            type: 'edge',
          },
        ],
      },
      {
        name: 'Flow Diagram Components',
        namespace: REGISTRY_NAMESPACES.FLOW_DIAGRAM,
        components: [
          {
            id: 'flow-start',
            label: 'Flow Start',
            category: 'flow',
            type: 'start-node',
          },
          {
            id: 'flow-process',
            label: 'Process Node',
            category: 'flow',
            type: 'process-node',
          },
          {
            id: 'flow-decision',
            label: 'Decision Node',
            category: 'flow',
            type: 'decision-node',
          },
          {
            id: 'flow-end',
            label: 'Flow End',
            category: 'flow',
            type: 'end-node',
          },
        ],
      },
    ];
  }

  /**
   * Migrate a legacy registry to the unified registry
   */
  private async migrateLegacyRegistry(legacyRegistry: {
    name: string;
    namespace: string;
    components: unknown[];
  }) {
    this.log(`  Migrating ${legacyRegistry.name}...`);

    try {
      RegistryMigration.migrateLegacyComponents(
        this.registry,
        legacyRegistry.namespace as unknown,
        legacyRegistry.components
      );
      this.log(
        `  ✅ Migrated ${legacyRegistry.components.length} components to namespace '${legacyRegistry.namespace}'`
      );
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Unknown error';
      this.log(
        `  ❌ Failed to migrate ${legacyRegistry.name}: ${errorMessage}`
      );
      throw error;
    }
  }

  /**
   * Create comprehensive migration reports
   */
  private async createMigrationReports(): Promise<void> {
    const stats = this.registry.getStats();
    const namespaces = this.registry.getNamespaces();
    const categories = this.registry.getCategories();

    const report = {
      migrationDate: new Date().toISOString(),
      summary: {
        totalComponents: stats.totalEntries,
        namespaces: stats.namespaces,
        categories: stats.categories,
      },
      breakdown: {
        byNamespace: {} as Record<string, number>,
        byCategory: {} as Record<string, number>,
      },
      components: this.registry.export(),
    };

    // Generate namespace breakdown
    namespaces.forEach((namespace) => {
      const components = this.registry.list(namespace);
      report.breakdown.byNamespace[namespace] = components.length;
    });

    // Generate category breakdown
    categories.forEach((category) => {
      const components = this.registry.listByCategory(category);
      report.breakdown.byCategory[category] = components.length;
    });

    // In a real implementation, save to file
    this.log('  📊 Migration report generated');
    this.log(`     - Total components: ${report.summary.totalComponents}`);
    this.log(
      `     - Namespaces: ${Object.keys(report.breakdown.byNamespace).join(', ')}`
    );
    this.log(
      `     - Categories: ${Object.keys(report.breakdown.byCategory).join(', ')}`
    );
  }

  /**
   * Create backup of current registry state
   */
  private async createRegistryBackup(): Promise<string> {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const filename = `canvas-registry-backup-${timestamp}.json`;

    try {
      // Get registry data
      const backupData = RegistryMigration.exportRegistry(this.registry);

      // Write to filesystem using File System Access API (if available)
      if (this.isFileSystemAccessAvailable()) {
        await this.writeToFileSystem(filename, backupData);
      } else {
        // Fallback to download
        this.downloadBackup(filename, backupData);
      }

      this.log(
        `  💾 Registry backup created: ${filename} (${backupData.length} chars)`
      );

      return filename;
    } catch (error) {
      this.log(`  ❌ Failed to create backup: ${error}`);
      throw error;
    }
  }

  /**
   * Check if File System Access API is available
   */
  private isFileSystemAccessAvailable(): boolean {
    return 'showSaveFilePicker' in window &&
      typeof window.showSaveFilePicker === 'function';
  }

  /**
   * Write backup to filesystem using File System Access API
   */
  private async writeToFileSystem(filename: string, data: string): Promise<void> {
    try {
      const fileHandle = await (window as unknown).showSaveFilePicker({
        suggestedName: filename,
        types: [{
          description: 'JSON files',
          accept: {
            'application/json': ['.json'],
          },
        }],
      });

      const writable = await fileHandle.createWritable();
      await writable.write(data);
      await writable.close();

      this.log(`  ✅ Backup saved to filesystem: ${filename}`);
    } catch (error) {
      if (error.name === 'AbortError') {
        this.log(`  ⚠️ Backup cancelled by user`);
        throw new Error('Backup cancelled by user');
      }
      throw error;
    }
  }

  /**
   * Download backup as file (fallback method)
   */
  private downloadBackup(filename: string, data: string): void {
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    URL.revokeObjectURL(url);
    this.log(`  📥 Backup downloaded: ${filename}`);
  }

  /**
   * Log migration activity
   */
  private log(message: string): void {
    this.migrationLog.push(message);
    logger.info(message, 'migration');
  }

  /**
   * Get migration log
   */
  getMigrationLog(): string[] {
    return [...this.migrationLog];
  }

  /**
   * Get current registry instance
   */
  getRegistry(): UnifiedRegistry<ComponentDefinition> {
    return this.registry;
  }
}

/**
 * Convenience function to run Phase 2 migration
 */
export async function runPhase2Migration(
  options?: MigrationOptions
): Promise<MigrationResult> {
  const orchestrator = new CanvasMigrationOrchestrator();
  return orchestrator.executePhase2Migration(options);
}

/**
 * Hook migration status checker
 */
export class HookMigrationChecker {
  /**
   * Check if a file uses legacy canvas hooks
   */
  static checkFileForLegacyHooks(fileContent: string): {
    hasLegacyHooks: boolean;
    legacyHooks: string[];
    recommendations: string[];
  } {
    const legacyHookPatterns = [
      'useCanvasScene',
      'useFlowDiagram',
      'useInteractionHooks',
      'useDragDropCanvas',
      'useCanvasSelection',
    ];

    const foundHooks = legacyHookPatterns.filter((pattern) =>
      fileContent.includes(pattern)
    );

    const recommendations = foundHooks.map((hook) => {
      switch (hook) {
        case 'useCanvasScene':
          return 'Replace with useCanvasSceneMigrated() or migrate to useGenericCanvas()';
        case 'useFlowDiagram':
          return 'Replace with useFlowDiagramMigrated() or migrate to useGenericCanvas()';
        case 'useInteractionHooks':
          return 'Replace with useKeyboardShortcutsMigration() and generic canvas interactions';
        default:
          return `Migrate ${hook} to use generic canvas capabilities`;
      }
    });

    return {
      hasLegacyHooks: foundHooks.length > 0,
      legacyHooks: foundHooks,
      recommendations,
    };
  }

  /**
   * Generate migration checklist for a project
   */
  static generateMigrationChecklist(): string[] {
    return [
      '🔄 Phase 2 Migration Checklist',
      '',
      '✅ Registry Migration:',
      '  □ Initialize unified registry system',
      '  □ Migrate DevSecOps component registry',
      '  □ Migrate PageDesigner component registry',
      '  □ Migrate CanvasScene component registry',
      '  □ Migrate FlowDiagram component registry',
      '  □ Validate registry integrity',
      '  □ Create registry backup',
      '',
      '✅ Hook Migration:',
      '  □ Replace useCanvasScene with useCanvasSceneMigrated',
      '  □ Replace useFlowDiagram with useFlowDiagramMigrated',
      '  □ Migrate interaction hooks to useKeyboardShortcutsMigration',
      '  □ Update all canvas components to use generic canvas API',
      '  □ Test backward compatibility',
      '',
      '✅ Testing & Validation:',
      '  □ Unit tests for migrated components',
      '  □ Integration tests for canvas functionality',
      '  □ Performance testing',
      '  □ User acceptance testing',
      '',
      '✅ Documentation:',
      '  □ Update component documentation',
      '  □ Create migration guide',
      '  □ Update API documentation',
      '  □ Create troubleshooting guide',
    ];
  }
}
