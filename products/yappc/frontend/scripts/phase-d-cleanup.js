#!/usr/bin/env node
/**
 * Legacy Cleanup Script - Phase D
 *
 * Identifies and removes legacy .js files from src/ directories
 * as part of the Canvas library world-class implementation.
 *
 * This script:
 * 1. Scans for .js files in src/ directories
 * 2. Identifies which have corresponding .ts/.tsx files
 * 3. Provides safe cleanup commands
 * 4. Validates no references to removed files remain
 *
 * Usage:
 *   node scripts/phase-d-cleanup.js --scan     # Scan only
 *   node scripts/phase-d-cleanup.js --clean    # Interactive cleanup
 *   node scripts/phase-d-cleanup.js --force    # Automated cleanup
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const WORKSPACE_ROOT = path.resolve(__dirname, '..');

class LegacyCleanup {
  constructor() {
    this.jsFiles = [];
    this.safesToRemove = [];
    this.warnings = [];
  }

  /**
   * Scan for legacy .js files in src directories
   */
  scanLegacyFiles() {
    console.log('🔍 Scanning for legacy .js files in src/ directories...\n');

    this.jsFiles = this.findJSFilesInSrc(WORKSPACE_ROOT);

    console.log(
      `Found ${this.jsFiles.length} .js files in src/ directories:\n`
    );

    this.jsFiles.forEach((file) => {
      const relativePath = path.relative(WORKSPACE_ROOT, file);
      const hasTypeScript = this.hasTypeScriptEquivalent(file);
      const hasReferences = this.hasActiveReferences(file);

      if (hasTypeScript && !hasReferences) {
        this.safesToRemove.push(file);
        console.log(
          `✅ ${relativePath} (has .ts/.tsx equivalent, no active references)`
        );
      } else if (hasTypeScript && hasReferences) {
        this.warnings.push(file);
        console.log(
          `⚠️  ${relativePath} (has .ts/.tsx equivalent, but has references)`
        );
      } else {
        console.log(`❓ ${relativePath} (no .ts/.tsx equivalent found)`);
      }
    });

    console.log(`\n📊 Summary:`);
    console.log(`   Safe to remove: ${this.safesToRemove.length} files`);
    console.log(`   Needs review: ${this.warnings.length} files`);
    console.log(
      `   Manual check: ${this.jsFiles.length - this.safesToRemove.length - this.warnings.length} files`
    );
  }

  /**
   * Find all .js files in src directories
   */
  findJSFilesInSrc(dir) {
    const jsFiles = [];

    const scan = (currentDir) => {
      if (!fs.existsSync(currentDir)) return;

      const entries = fs.readdirSync(currentDir, { withFileTypes: true });

      for (const entry of entries) {
        const fullPath = path.join(currentDir, entry.name);

        if (
          entry.isDirectory() &&
          !entry.name.startsWith('.') &&
          entry.name !== 'node_modules'
        ) {
          // Only scan src directories and their subdirectories
          if (
            entry.name === 'src' ||
            currentDir.includes('/src/') ||
            currentDir.includes('\\src\\')
          ) {
            scan(fullPath);
          } else if (
            !currentDir.includes('/src/') &&
            !currentDir.includes('\\src\\')
          ) {
            // Continue scanning for src directories at root level
            scan(fullPath);
          }
        } else if (entry.isFile() && entry.name.endsWith('.js')) {
          // Only include if we're in a src directory
          if (currentDir.includes('/src/') || currentDir.includes('\\src\\')) {
            jsFiles.push(fullPath);
          }
        }
      }
    };

    scan(dir);
    return jsFiles;
  }

  /**
   * Check if a .js file has a corresponding .ts or .tsx file
   */
  hasTypeScriptEquivalent(jsFile) {
    const baseName = jsFile.replace(/\.js$/, '');
    return fs.existsSync(`${baseName}.ts`) || fs.existsSync(`${baseName}.tsx`);
  }

  /**
   * Check if file has active references in the codebase
   */
  hasActiveReferences(jsFile) {
    try {
      const relativePath = path.relative(WORKSPACE_ROOT, jsFile);
      const fileName = path.basename(jsFile, '.js');

      // Search for imports or requires referencing this file
      const searchPatterns = [
        `"${relativePath.replace(/\\/g, '/')}"`,
        `'${relativePath.replace(/\\/g, '/')}'`,
        `"${fileName}"`,
        `'${fileName}'`,
      ];

      for (const pattern of searchPatterns) {
        try {
          const result = execSync(
            `grep -r --include="*.ts" --include="*.tsx" --include="*.js" --include="*.jsx" ${pattern} ${WORKSPACE_ROOT}`,
            { encoding: 'utf8', stdio: 'pipe' }
          );
          if (result.trim()) {
            return true;
          }
        } catch (e) {
          // grep returns non-zero exit code when no matches found
          continue;
        }
      }
      return false;
    } catch (error) {
      console.warn(
        `Warning: Could not check references for ${jsFile}:`,
        error.message
      );
      return true; // Err on the side of caution
    }
  }

  /**
   * Interactive cleanup process
   */
  async interactiveCleanup() {
    if (this.safesToRemove.length === 0) {
      console.log('\n✨ No files identified as safe to remove automatically.');
      return;
    }

    console.log(
      `\n🧹 Ready to remove ${this.safesToRemove.length} legacy .js files:`
    );
    this.safesToRemove.forEach((file) => {
      const relativePath = path.relative(WORKSPACE_ROOT, file);
      console.log(`   ${relativePath}`);
    });

    // In a real implementation, you'd use a proper prompt library
    console.log('\nTo proceed with removal, run with --force flag');
    console.log('Or manually remove files after reviewing the list above.');
  }

  /**
   * Automated cleanup (force mode)
   */
  forceCleanup() {
    if (this.safesToRemove.length === 0) {
      console.log('\n✨ No files to remove.');
      return;
    }

    console.log(
      `\n🧹 Removing ${this.safesToRemove.length} legacy .js files...`
    );

    let removed = 0;
    for (const file of this.safesToRemove) {
      try {
        fs.unlinkSync(file);
        const relativePath = path.relative(WORKSPACE_ROOT, file);
        console.log(`   ✅ Removed ${relativePath}`);
        removed++;
      } catch (error) {
        const relativePath = path.relative(WORKSPACE_ROOT, file);
        console.log(`   ❌ Failed to remove ${relativePath}: ${error.message}`);
      }
    }

    console.log(`\n✨ Successfully removed ${removed} files.`);

    if (this.warnings.length > 0) {
      console.log(`\n⚠️  ${this.warnings.length} files need manual review:`);
      this.warnings.forEach((file) => {
        const relativePath = path.relative(WORKSPACE_ROOT, file);
        console.log(`   ${relativePath}`);
      });
    }
  }

  /**
   * Generate Phase D completion report
   */
  generateReport() {
    const report = {
      timestamp: new Date().toISOString(),
      phase: 'D - Legacy Cleanup & Examples',
      filesScanned: this.jsFiles.length,
      filesRemoved: this.safesToRemove.length,
      filesNeedingReview: this.warnings.length,
      cleanupComplete: this.warnings.length === 0,
      nextSteps:
        this.warnings.length > 0
          ? 'Manual review required for remaining .js files'
          : 'Legacy cleanup complete, ready for Phase E',
    };

    const reportPath = path.join(
      WORKSPACE_ROOT,
      'docs',
      'PHASE_D_CLEANUP_REPORT.json'
    );
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
    console.log(
      `\n📋 Phase D report saved to: docs/PHASE_D_CLEANUP_REPORT.json`
    );

    return report;
  }
}

// CLI execution
if (require.main === module) {
  const cleanup = new LegacyCleanup();
  const args = process.argv.slice(2);

  if (args.includes('--help') || args.includes('-h')) {
    console.log(`
Phase D Legacy Cleanup Script

Usage:
  node scripts/phase-d-cleanup.js [options]

Options:
  --scan     Scan for legacy files (default)
  --clean    Interactive cleanup 
  --force    Automated cleanup without prompts
  --help     Show this help message

Examples:
  node scripts/phase-d-cleanup.js --scan
  node scripts/phase-d-cleanup.js --clean
  node scripts/phase-d-cleanup.js --force
    `);
    process.exit(0);
  }

  try {
    cleanup.scanLegacyFiles();

    if (args.includes('--force')) {
      cleanup.forceCleanup();
      cleanup.generateReport();
    } else if (args.includes('--clean')) {
      cleanup.interactiveCleanup();
    } else {
      console.log(
        '\n💡 Run with --clean for interactive cleanup or --force for automated cleanup'
      );
    }
  } catch (error) {
    console.error('\n❌ Phase D cleanup failed:', error.message);
    process.exit(1);
  }
}

module.exports = { LegacyCleanup };
