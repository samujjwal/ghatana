#!/usr/bin/env node
/**
 * Codemod: Migrate IDE Library to Canvas
 * 
 * This script automates the migration of imports from @ghatana/yappc-ide to @ghatana/yappc-canvas.
 * Run with: npx ts-node migrate-ide-to-canvas.ts [directory]
 * 
 * @doc.type codemod
 * @doc.purpose Automated migration of IDE library imports
 * @doc.layer migration
 * @doc.pattern Codemod
 */

import { readFileSync, writeFileSync, readdirSync, statSync, existsSync } from 'fs';
import { join, extname, resolve } from 'path';

// Import mapping: Old IDE imports -> New Canvas exports
const IMPORT_MAPPINGS: Record<string, string> = {
  // Core IDE components
  'IDEShell': '@ghatana/yappc-canvas',
  'ProfessionalIDELayout': '@ghatana/yappc-canvas',
  'IDEShellProps': '@ghatana/yappc-canvas',
  'ProfessionalIDELayoutProps': '@ghatana/yappc-canvas',
  
  // Editor components
  'EditorPanel': '@ghatana/yappc-canvas',
  'CodeEditor': '@ghatana/yappc-canvas',
  'EditorPanelProps': '@ghatana/yappc-canvas',
  'CodeEditorProps': '@ghatana/yappc-canvas',
  
  // File explorer
  'FileExplorer': '@ghatana/yappc-canvas',
  'FileTree': '@ghatana/yappc-canvas',
  
  // UI components
  'ContextMenu': '@ghatana/yappc-canvas',
  'TabBar': '@ghatana/yappc-canvas',
  
  // Search and operations
  'AdvancedSearchPanel': '@ghatana/yappc-canvas',
  'BulkOperationsToolbar': '@ghatana/yappc-canvas',
  
  // Collaboration
  'CursorOverlay': '@ghatana/yappc-canvas',
  'RealTimeCursorTracking': '@ghatana/yappc-canvas',
  
  // Utils
  'KeyboardShortcutsManager': '@ghatana/yappc-canvas',
  'LoadingStates': '@ghatana/yappc-canvas',
};

// File extensions to process
const EXTENSIONS = ['.ts', '.tsx', '.js', '.jsx'];

interface MigrationResult {
  filePath: string;
  changes: string[];
  error?: string;
}

/**
 * Check if file should be processed
 */
function shouldProcessFile(filePath: string): boolean {
  const ext = extname(filePath);
  return EXTENSIONS.includes(ext);
}

/**
 * Process a single file
 */
function processFile(filePath: string): MigrationResult {
  const result: MigrationResult = {
    filePath,
    changes: [],
  };

  try {
    let content = readFileSync(filePath, 'utf8');
    let modified = false;

    // Pattern 1: Direct import statements
    // import { X } from '@ghatana/yappc-ide';
    const directImportRegex = /import\s+\{([^}]+)\}\s+from\s+['"]@ghatana\/yappc-ide['"];?/g;
    
    content = content.replace(directImportRegex, (match, imports) => {
      modified = true;
      const importList = imports.split(',').map((i: string) => i.trim());
      const newImports = importList.filter((imp: string) => IMPORT_MAPPINGS[imp]);
      
      if (newImports.length === 0) {
        result.changes.push(`Removed unused IDE import: ${match.trim()}`);
        return '';
      }
      
      result.changes.push(`Migrated: ${match.trim()} -> from '@ghatana/yappc-canvas'`);
      return `import { ${newImports.join(', ')} } from '@ghatana/yappc-canvas';`;
    });

    // Pattern 2: Import with alias
    // import * as IDE from '@ghatana/yappc-ide';
    const aliasImportRegex = /import\s+\*\s+as\s+(\w+)\s+from\s+['"]@ghatana\/yappc-ide['"];?/g;
    
    content = content.replace(aliasImportRegex, (match, alias) => {
      modified = true;
      result.changes.push(`Migrated namespace: ${match.trim()} -> from '@ghatana/yappc-canvas'`);
      return `import * as ${alias} from '@ghatana/yappc-canvas';`;
    });

    // Pattern 3: Default import
    // import IDE from '@ghatana/yappc-ide';
    const defaultImportRegex = /import\s+(\w+)\s+from\s+['"]@ghatana\/yappc-ide['"];?/g;
    
    content = content.replace(defaultImportRegex, (match, alias) => {
      modified = true;
      result.changes.push(`Migrated default import: ${match.trim()} -> from '@ghatana/yappc-canvas'`);
      return `import ${alias} from '@ghatana/yappc-canvas';`;
    });

    // Pattern 4: Import with subpath
    // import { X } from '@ghatana/yappc-ide/components';
    const subpathImportRegex = /from\s+['"]@ghatana\/yappc-ide\/([^'"]+)['"];?/g;
    
    content = content.replace(subpathImportRegex, (match, subpath) => {
      modified = true;
      result.changes.push(`Migrated subpath: ${match.trim()} -> from '@ghatana/yappc-canvas/${subpath}'`);
      return `from '@ghatana/yappc-canvas/${subpath}';`;
    });

    // Pattern 5: Dynamic imports
    // await import('@ghatana/yappc-ide')
    const dynamicImportRegex = /import\(['"]@ghatana\/yappc-ide['"]\)/g;
    
    content = content.replace(dynamicImportRegex, (match) => {
      modified = true;
      result.changes.push(`Migrated dynamic import: ${match} -> '@ghatana/yappc-canvas'`);
      return `import('@ghatana/yappc-canvas')`;
    });

    // Write changes if modified
    if (modified) {
      writeFileSync(filePath, content, 'utf8');
    }

  } catch (error) {
    result.error = error instanceof Error ? error.message : String(error);
  }

  return result;
}

/**
 * Recursively find files in directory
 */
function findFiles(dir: string, files: string[] = []): string[] {
  if (!existsSync(dir)) {
    return files;
  }

  const items = readdirSync(dir);

  for (const item of items) {
    const fullPath = join(dir, item);
    const stat = statSync(fullPath);

    if (stat.isDirectory()) {
      // Skip node_modules and dist
      if (item !== 'node_modules' && item !== 'dist' && item !== '.git') {
        findFiles(fullPath, files);
      }
    } else if (shouldProcessFile(fullPath)) {
      files.push(fullPath);
    }
  }

  return files;
}

/**
 * Main migration function
 */
function runMigration(targetDir: string = './'): void {
  console.log('🚀 Starting IDE to Canvas migration...\n');

  const absoluteDir = resolve(targetDir);
  console.log(`📁 Target directory: ${absoluteDir}\n`);

  // Find all files
  const files = findFiles(absoluteDir);
  console.log(`🔍 Found ${files.length} files to process\n`);

  // Process each file
  const results: MigrationResult[] = [];
  let successCount = 0;
  let errorCount = 0;
  let changeCount = 0;

  for (const file of files) {
    const result = processFile(file);
    results.push(result);

    if (result.error) {
      errorCount++;
      console.log(`❌ Error in ${file}: ${result.error}`);
    } else if (result.changes.length > 0) {
      successCount++;
      changeCount += result.changes.length;
      console.log(`✅ ${file}`);
      for (const change of result.changes) {
        console.log(`   → ${change}`);
      }
    }
  }

  // Summary
  console.log('\n📊 Migration Summary:');
  console.log(`   Files processed: ${files.length}`);
  console.log(`   Files with changes: ${successCount}`);
  console.log(`   Total changes: ${changeCount}`);
  console.log(`   Errors: ${errorCount}`);

  if (errorCount === 0) {
    console.log('\n✨ Migration completed successfully!');
    console.log('\nNext steps:');
    console.log('1. Review the changes in your IDE');
    console.log('2. Run your test suite: npm test');
    console.log('3. Run TypeScript check: npx tsc --noEmit');
    console.log('4. Update your package.json dependencies');
  } else {
    console.log('\n⚠️  Migration completed with errors. Please review.');
    process.exit(1);
  }
}

// Run if called directly
const isMainModule = import.meta.url === `file://${process.argv[1]}`;
if (isMainModule) {
  const targetDir = process.argv[2] || './';
  runMigration(targetDir);
}

export { runMigration, processFile, IMPORT_MAPPINGS };
