#!/usr/bin/env node

/**
 * @file migrate-library-names.js
 * @description Automated migration script for library naming convention updates
 * @doc.type script
 * @doc.purpose Migrate @ghatana/yappc-* imports to @yappc/* convention
 * @doc.layer tooling
 * @doc.pattern automation
 * 
 * Features:
 * - Automated import transformation
 * - Rollback capability with backup creation
 * - Dry-run mode for safe testing
 * - Validation before and after migration
 * 
 * Usage:
 *   node scripts/migrate-library-names.js [options]
 * 
 * Options:
 *   --dry-run          Preview changes without applying
 *   --target=<lib>     Migrate specific library only (e.g., types, ui, canvas)
 *   --rollback         Restore from backup
 *   --validate         Validate imports after migration
 *   --verbose          Show detailed output
 *   --help             Show this help message
 * 
 * Examples:
 *   node scripts/migrate-library-names.js --dry-run --verbose
 *   node scripts/migrate-library-names.js --target=types
 *   node scripts/migrate-library-names.js --rollback
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// ============================================================================
// CONFIGURATION
// ============================================================================

// Migration mapping: old name -> new name
const MIGRATION_MAP = {
  // Phase 1: Core libraries -> @yappc/core
  '@ghatana/yappc-types': '@yappc/core/types',
  '@ghatana/yappc-utils': '@yappc/core/utils',
  '@ghatana/yappc-api': '@yappc/core/api',
  '@ghatana/yappc-config': '@yappc/core/config',
  
  // Phase 2: UI libraries -> @yappc/ui
  '@ghatana/yappc-ui': '@yappc/ui',
  '@ghatana/yappc-chat': '@yappc/ui/chat',
  '@ghatana/yappc-notifications': '@yappc/ui/notifications',
  
  // Phase 3: Canvas libraries -> @yappc/canvas
  '@ghatana/yappc-canvas': '@yappc/canvas',
  '@ghatana/yappc-collab': '@yappc/canvas/collab',
  '@ghatana/yappc-crdt': '@yappc/canvas/crdt',
  
  // Phase 4: IDE libraries -> @yappc/ide
  '@ghatana/yappc-code-editor': '@yappc/ide/code-editor',
  '@ghatana/yappc-live-preview-server': '@yappc/ide/live-preview',
  '@ghatana/yappc-vite-plugin-live-edit': '@yappc/ide/vite-plugin',
  
  // Phase 5: AI libraries -> @yappc/ai
  '@ghatana/yappc-ai': '@yappc/ai',
  
  // Phase 6: Testing libraries -> @yappc/testing
  '@ghatana/yappc-testing': '@yappc/testing',
  '@ghatana/yappc-auth': '@yappc/testing/auth-mocks',
  '@ghatana/yappc-component-traceability': '@yappc/testing/traceability',
  '@ghatana/yappc-realtime': '@yappc/testing/realtime',
};

// File extensions to process
const FILE_EXTENSIONS = ['.ts', '.tsx', '.js', '.jsx', '.json'];

// Directories to exclude
const EXCLUDE_DIRS = [
  'node_modules',
  'dist',
  'build',
  '.git',
  '.turbo',
  'coverage',
  '__snapshots__',
  '.backup-migration',
];

// Backup directory
const BACKUP_DIR = '.backup-migration';

// ============================================================================
// LOGGING UTILITIES
// ============================================================================

const verbose = process.argv.includes('--verbose');
const dryRun = process.argv.includes('--dry-run');

function log(message) {
  console.log(message);
}

function logVerbose(message) {
  if (verbose) {
    console.log(`[VERBOSE] ${message}`);
  }
}

function logSuccess(message) {
  console.log(`✅ ${message}`);
}

function logWarning(message) {
  console.log(`⚠️  ${message}`);
}

function logError(message) {
  console.error(`❌ ${message}`);
}

function logInfo(message) {
  console.log(`ℹ️  ${message}`);
}

// ============================================================================
// ARGUMENT PARSING
// ============================================================================

function parseArgs() {
  const args = {
    dryRun: process.argv.includes('--dry-run'),
    rollback: process.argv.includes('--rollback'),
    validate: process.argv.includes('--validate'),
    verbose: process.argv.includes('--verbose'),
    help: process.argv.includes('--help'),
    target: null,
  };

  // Parse --target=<value>
  const targetArg = process.argv.find(arg => arg.startsWith('--target='));
  if (targetArg) {
    args.target = targetArg.split('=')[1];
  }

  return args;
}

function showHelp() {
  console.log(`
Library Naming Migration Tool

Usage: node scripts/migrate-library-names.js [options]

Options:
  --dry-run          Preview changes without applying
  --target=<lib>     Migrate specific library only
  --rollback         Restore from backup
  --validate         Validate imports after migration
  --verbose          Show detailed output
  --help             Show this help message

Examples:
  node scripts/migrate-library-names.js --dry-run
  node scripts/migrate-library-names.js --target=types
  node scripts/migrate-library-names.js --rollback
  node scripts/migrate-library-names.js --validate

Migration Phases:
  core   - types, utils, api, config -> @yappc/core
  ui     - ui, chat, notifications -> @yappc/ui
  canvas - canvas, collab, crdt -> @yappc/canvas
  ide    - code-editor, live-preview, vite-plugin -> @yappc/ide
  ai     - ai -> @yappc/ai
  testing- testing, auth, traceability, realtime -> @yappc/testing
`);
}

// ============================================================================
// FILE UTILITIES
// ============================================================================

function findFiles(dir, extensions, excludeDirs) {
  const files = [];
  
  function traverse(currentDir) {
    const entries = fs.readdirSync(currentDir, { withFileTypes: true });
    
    for (const entry of entries) {
      const fullPath = path.join(currentDir, entry.name);
      
      if (entry.isDirectory()) {
        if (!excludeDirs.includes(entry.name)) {
          traverse(fullPath);
        }
      } else if (entry.isFile()) {
        const ext = path.extname(entry.name);
        if (extensions.includes(ext)) {
          files.push(fullPath);
        }
      }
    }
  }
  
  traverse(dir);
  return files;
}

function readFile(filePath) {
  try {
    return fs.readFileSync(filePath, 'utf-8');
  } catch (error) {
    logError(`Failed to read ${filePath}: ${error.message}`);
    return null;
  }
}

function writeFile(filePath, content) {
  if (dryRun) {
    logInfo(`[DRY-RUN] Would write to ${filePath}`);
    return true;
  }
  
  try {
    fs.writeFileSync(filePath, content, 'utf-8');
    logVerbose(`Wrote ${filePath}`);
    return true;
  } catch (error) {
    logError(`Failed to write ${filePath}: ${error.message}`);
    return false;
  }
}

// ============================================================================
// BACKUP UTILITIES
// ============================================================================

function createBackup(files) {
  if (dryRun) {
    logInfo('[DRY-RUN] Would create backup');
    return true;
  }

  try {
    // Create backup directory
    if (!fs.existsSync(BACKUP_DIR)) {
      fs.mkdirSync(BACKUP_DIR, { recursive: true });
    }

    // Create timestamped backup subdir
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const backupSubdir = path.join(BACKUP_DIR, timestamp);
    fs.mkdirSync(backupSubdir, { recursive: true });

    // Backup files
    let backedUpCount = 0;
    for (const file of files) {
      const relativePath = path.relative(process.cwd(), file);
      const backupPath = path.join(backupSubdir, relativePath);
      const backupDir = path.dirname(backupPath);
      
      if (!fs.existsSync(backupDir)) {
        fs.mkdirSync(backupDir, { recursive: true });
      }
      
      fs.copyFileSync(file, backupPath);
      backedUpCount++;
    }

    // Write backup manifest
    const manifest = {
      timestamp: new Date().toISOString(),
      fileCount: backedUpCount,
      files: files.map(f => path.relative(process.cwd(), f)),
    };
    fs.writeFileSync(
      path.join(backupSubdir, 'manifest.json'),
      JSON.stringify(manifest, null, 2)
    );

    logSuccess(`Created backup of ${backedUpCount} files in ${backupSubdir}`);
    return backupSubdir;
  } catch (error) {
    logError(`Failed to create backup: ${error.message}`);
    return null;
  }
}

function restoreFromBackup() {
  try {
    if (!fs.existsSync(BACKUP_DIR)) {
      logError('No backup directory found');
      return false;
    }

    // Find latest backup
    const backups = fs.readdirSync(BACKUP_DIR)
      .filter(dir => dir !== '.' && dir !== '..')
      .map(dir => ({
        name: dir,
        path: path.join(BACKUP_DIR, dir),
        time: fs.statSync(path.join(BACKUP_DIR, dir)).mtime,
      }))
      .sort((a, b) => b.time - a.time);

    if (backups.length === 0) {
      logError('No backups found');
      return false;
    }

    const latestBackup = backups[0];
    logInfo(`Restoring from backup: ${latestBackup.name}`);

    // Read manifest
    const manifestPath = path.join(latestBackup.path, 'manifest.json');
    if (!fs.existsSync(manifestPath)) {
      logError('Backup manifest not found');
      return false;
    }

    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));

    // Restore files
    let restoredCount = 0;
    for (const file of manifest.files) {
      const backupFile = path.join(latestBackup.path, file);
      const originalFile = path.join(process.cwd(), file);
      
      if (fs.existsSync(backupFile)) {
        const originalDir = path.dirname(originalFile);
        if (!fs.existsSync(originalDir)) {
          fs.mkdirSync(originalDir, { recursive: true });
        }
        fs.copyFileSync(backupFile, originalFile);
        restoredCount++;
        logVerbose(`Restored ${file}`);
      }
    }

    logSuccess(`Restored ${restoredCount} files from backup`);
    return true;
  } catch (error) {
    logError(`Failed to restore backup: ${error.message}`);
    return false;
  }
}

// ============================================================================
// MIGRATION LOGIC
// ============================================================================

function transformImports(content, mapping) {
  let transformed = content;
  let changeCount = 0;

  for (const [oldName, newName] of Object.entries(mapping)) {
    // Match various import patterns
    const patterns = [
      // ES6 imports
      { regex: new RegExp(`from ['"]${oldName}([^'"]*)['"]`, 'g'), replacement: `from '${newName}$1'` },
      { regex: new RegExp(`import\\(['"]${oldName}([^'"]*)['"]\\)`, 'g'), replacement: `import('${newName}$1')` },
      { regex: new RegExp(`require\\(['"]${oldName}([^'"]*)['"]\\)`, 'g'), replacement: `require('${newName}$1')` },
      // Package.json dependencies
      { regex: new RegExp(`"${oldName}":`, 'g'), replacement: `"${newName}":` },
    ];

    for (const { regex, replacement } of patterns) {
      const matches = transformed.match(regex);
      if (matches) {
        changeCount += matches.length;
        transformed = transformed.replace(regex, replacement);
      }
    }
  }

  return { content: transformed, changeCount };
}

function filterMappingByTarget(target) {
  if (!target) return MIGRATION_MAP;

  const targetPatterns = {
    'core': ['@ghatana/yappc-types', '@ghatana/yappc-utils', '@ghatana/yappc-api', '@ghatana/yappc-config'],
    'ui': ['@ghatana/yappc-ui', '@ghatana/yappc-chat', '@ghatana/yappc-notifications'],
    'canvas': ['@ghatana/yappc-canvas', '@ghatana/yappc-collab', '@ghatana/yappc-crdt'],
    'ide': ['@ghatana/yappc-code-editor', '@ghatana/yappc-live-preview-server', '@ghatana/yappc-vite-plugin-live-edit'],
    'ai': ['@ghatana/yappc-ai'],
    'testing': ['@ghatana/yappc-testing', '@ghatana/yappc-auth', '@ghatana/yappc-component-traceability', '@ghatana/yappc-realtime'],
  };

  const patterns = targetPatterns[target];
  if (!patterns) {
    logError(`Unknown target: ${target}. Use: core, ui, canvas, ide, ai, testing`);
    return null;
  }

  return Object.fromEntries(
    Object.entries(MIGRATION_MAP).filter(([key]) => patterns.includes(key))
  );
}

// ============================================================================
// VALIDATION
// ============================================================================

function validateImports(files, mapping) {
  const issues = [];
  let totalOldImports = 0;

  for (const file of files) {
    const content = readFile(file);
    if (!content) continue;

    for (const oldName of Object.keys(mapping)) {
      const regex = new RegExp(`from ['"]${oldName}([^'"]*)['"]`, 'g');
      const matches = content.match(regex);
      if (matches) {
        totalOldImports += matches.length;
        issues.push({
          file: path.relative(process.cwd(), file),
          oldName,
          count: matches.length,
        });
      }
    }
  }

  if (issues.length > 0) {
    logWarning(`Found ${totalOldImports} old-style imports in ${issues.length} files`);
    
    if (verbose) {
      for (const issue of issues.slice(0, 10)) {
        log(`  - ${issue.file}: ${issue.oldName} (${issue.count} occurrences)`);
      }
      if (issues.length > 10) {
        log(`  ... and ${issues.length - 10} more files`);
      }
    }
  } else {
    logSuccess('No old-style imports found - migration complete!');
  }

  return { issues, totalOldImports };
}

// ============================================================================
// MAIN EXECUTION
// ============================================================================

async function main() {
  const args = parseArgs();

  if (args.help) {
    showHelp();
    return;
  }

  if (args.rollback) {
    log('🔄 Starting rollback...');
    const success = restoreFromBackup();
    process.exit(success ? 0 : 1);
  }

  if (args.validate) {
    log('🔍 Validating imports...');
    const targetDir = process.cwd();
    const files = findFiles(targetDir, FILE_EXTENSIONS, EXCLUDE_DIRS);
    const { issues, totalOldImports } = validateImports(files, MIGRATION_MAP);
    
    if (totalOldImports > 0) {
      logWarning(`Validation failed: ${totalOldImports} old imports remain`);
      process.exit(1);
    } else {
      logSuccess('Validation passed: All imports use new naming convention');
      process.exit(0);
    }
  }

  // Normal migration flow
  log('🚀 Starting library naming migration...');
  logInfo(`Mode: ${dryRun ? 'DRY RUN' : 'APPLY CHANGES'}`);

  // Filter mapping if target specified
  const mapping = filterMappingByTarget(args.target);
  if (!mapping) {
    process.exit(1);
  }

  if (Object.keys(mapping).length === 0) {
    logWarning('No libraries to migrate');
    process.exit(0);
  }

  logInfo(`Libraries to migrate: ${Object.keys(mapping).join(', ')}`);

  // Find files to process
  const targetDir = process.cwd();
  logInfo(`Scanning ${targetDir}...`);
  
  const files = findFiles(targetDir, FILE_EXTENSIONS, EXCLUDE_DIRS);
  logInfo(`Found ${files.length} files to scan`);

  // Pre-scan to count changes
  let totalChanges = 0;
  const filesToModify = [];

  for (const file of files) {
    const content = readFile(file);
    if (!content) continue;

    const { changeCount } = transformImports(content, mapping);
    if (changeCount > 0) {
      filesToModify.push({ file, changeCount });
      totalChanges += changeCount;
    }
  }

  logInfo(`Will modify ${filesToModify.length} files with ${totalChanges} total changes`);

  if (filesToModify.length === 0) {
    logSuccess('No changes needed - all imports already use new naming!');
    process.exit(0);
  }

  // Show files that will be modified
  if (verbose || dryRun) {
    log('\nFiles to modify:');
    for (const { file, changeCount } of filesToModify.slice(0, 20)) {
      log(`  - ${path.relative(process.cwd(), file)} (${changeCount} changes)`);
    }
    if (filesToModify.length > 20) {
      log(`  ... and ${filesToModify.length - 20} more files`);
    }
  }

  if (dryRun) {
    log('\n[DRY-RUN] No changes applied. Run without --dry-run to apply.');
    process.exit(0);
  }

  // Create backup
  log('\n📦 Creating backup...');
  const backupPath = createBackup(filesToModify.map(f => f.file));
  if (!backupPath) {
    logError('Backup failed - aborting migration');
    process.exit(1);
  }

  // Apply changes
  log('\n📝 Applying changes...');
  let successCount = 0;
  let failCount = 0;

  for (const { file } of filesToModify) {
    const content = readFile(file);
    if (!content) {
      failCount++;
      continue;
    }

    const { content: transformed, changeCount } = transformImports(content, mapping);
    
    if (writeFile(file, transformed)) {
      successCount++;
      logVerbose(`Updated ${path.relative(process.cwd(), file)} (${changeCount} changes)`);
    } else {
      failCount++;
    }
  }

  // Summary
  log('\n============================');
  logSuccess(`Migration complete!`);
  log(`  Files updated: ${successCount}`);
  log(`  Total changes: ${totalChanges}`);
  if (failCount > 0) {
    logError(`  Failures: ${failCount}`);
  }
  log(`  Backup: ${backupPath}`);

  log('\nNext steps:');
  log('  1. Review changes: git diff');
  log('  2. Run typecheck: pnpm typecheck');
  log('  3. Run tests: pnpm test');
  log('  4. If issues found, rollback: node scripts/migrate-library-names.js --rollback');
  log('  5. Commit changes: git commit -m "chore: migrate to @yappc/* naming"');

  // Post-migration validation
  log('\n🔍 Running post-migration validation...');
  const { totalOldImports } = validateImports(files, mapping);
  
  if (totalOldImports > 0) {
    logWarning(`Some old imports may remain - manual review recommended`);
  }
}

// Error handling
process.on('unhandledRejection', (error) => {
  logError(`Unhandled rejection: ${error.message}`);
  process.exit(1);
});

// Run main
main().catch((error) => {
  logError(`Fatal error: ${error.message}`);
  if (verbose) {
    console.error(error.stack);
  }
  process.exit(1);
});
