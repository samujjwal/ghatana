/**
 * YAPPC-T07: Fix-Forward Cleanup Mode
 * 
 * Provides cleanup mode for generated replacements.
 * When code is regenerated, obsolete placeholder/legacy files are removed in the same plan.
 */

import { readFileSync, existsSync, unlinkSync, readdirSync, statSync } from 'fs';
import { basename, join } from 'path';

export interface CleanupConfig {
  targetDirectory: string;
  generatedFilePattern: RegExp;
  legacyFilePattern?: RegExp;
  dryRun?: boolean;
}

export interface CleanupResult {
  deletedFiles: string[];
  skippedFiles: string[];
  errors: Array<{ file: string; error: string }>;
}

/**
 * Manages fix-forward cleanup for generated code replacements.
 */
export class FixForwardCleanup {
  /**
   * Performs cleanup of obsolete files based on configuration.
   */
  cleanup(config: CleanupConfig): CleanupResult {
    const result: CleanupResult = {
      deletedFiles: [],
      skippedFiles: [],
      errors: [],
    };

    if (!existsSync(config.targetDirectory)) {
      return result;
    }

    const files = this.scanDirectory(config.targetDirectory);
    
    for (const file of files) {
      const relativePath = file.replace(config.targetDirectory, '').replace(/^[\\/]/, '');
      
      // Check if file matches legacy pattern
      if (config.legacyFilePattern && matchesPath(config.legacyFilePattern, relativePath)) {
        // Check if corresponding generated file exists
        const hasGeneratedReplacement = files.some(f => 
          matchesPath(config.generatedFilePattern, f.replace(config.targetDirectory, '').replace(/^[\\/]/, ''))
        );
        
        if (hasGeneratedReplacement) {
          if (config.dryRun) {
            result.skippedFiles.push(relativePath);
          } else {
            try {
              unlinkSync(file);
              result.deletedFiles.push(relativePath);
            } catch (error) {
              result.errors.push({
                file: relativePath,
                error: error instanceof Error ? error.message : String(error),
              });
            }
          }
        }
      }
    }

    return result;
  }

  /**
   * Scans directory recursively for files.
   */
  private scanDirectory(directory: string): string[] {
    const files: string[] = [];
    
    const items = readdirSync(directory);
    
    for (const item of items) {
      const fullPath = join(directory, item);
      const stat = statSync(fullPath);
      
      if (stat.isDirectory()) {
        files.push(...this.scanDirectory(fullPath));
      } else if (stat.isFile()) {
        files.push(fullPath);
      }
    }
    
    return files;
  }

  /**
   * Identifies obsolete placeholder files that should be cleaned up.
   */
  identifyObsoleteFiles(config: CleanupConfig): string[] {
    const obsoleteFiles: string[] = [];
    
    if (!existsSync(config.targetDirectory)) {
      return obsoleteFiles;
    }

    const files = this.scanDirectory(config.targetDirectory);
    
    for (const file of files) {
      const relativePath = file.replace(config.targetDirectory, '').replace(/^[\\/]/, '');
      
      // Check if file matches legacy pattern
      if (config.legacyFilePattern && matchesPath(config.legacyFilePattern, relativePath)) {
        // Check if corresponding generated file exists
        const hasGeneratedReplacement = files.some(f => 
          matchesPath(config.generatedFilePattern, f.replace(config.targetDirectory, '').replace(/^[\\/]/, ''))
        );
        
        if (hasGeneratedReplacement) {
          obsoleteFiles.push(relativePath);
        }
      }
    }

    return obsoleteFiles;
  }

  /**
   * Creates a cleanup configuration for Product generated files.
   */
  static createProductCleanupConfig(targetDirectory: string, dryRun = false): CleanupConfig {
    return {
      targetDirectory,
      generatedFilePattern: /^(generated|Generated).*\.(ts|tsx|java)$/,
      legacyFilePattern: /^(placeholder|legacy|old|manual).*\.(ts|tsx|java)$/,
      dryRun,
    };
  }

  /**
   * Performs cleanup with product patterns.
   */
  static cleanupProductGenerated(targetDirectory: string, dryRun = false): CleanupResult {
    const config = this.createProductCleanupConfig(targetDirectory, dryRun);
    const cleanup = new FixForwardCleanup();
    return cleanup.cleanup(config);
  }
}

/**
 * Creates a fix-forward cleanup instance.
 */
export function createFixForwardCleanup(): FixForwardCleanup {
  return new FixForwardCleanup();
}

/**
 * Performs fix-forward cleanup with given configuration.
 */
export function performFixForwardCleanup(config: CleanupConfig): CleanupResult {
  return createFixForwardCleanup().cleanup(config);
}

function matchesPath(pattern: RegExp, relativePath: string): boolean {
  return pattern.test(relativePath) || pattern.test(basename(relativePath));
}
