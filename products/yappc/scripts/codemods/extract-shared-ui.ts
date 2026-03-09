#!/usr/bin/env node
/**
 * Codemod: Extract Shared UI Components
 * 
 * This script extracts common UI components from canvas and ide libraries
 * to the shared @ghatana/yappc-ui library (Phase 2 of consolidation).
 * 
 * Run with: npx ts-node extract-shared-ui.ts [directory]
 * 
 * @doc.type codemod
 * @doc.purpose Extract shared UI primitives to @ghatana/yappc-ui
 * @doc.layer migration
 * @doc.phase 2
 */

import { readFileSync, writeFileSync, readdirSync, statSync, existsSync, mkdirSync } from 'fs';
import { join, extname, resolve, dirname } from 'path';

// Component extraction mapping
const COMPONENT_MAPPINGS: Record<string, { from: string; to: string }> = {
  // Panels
  'PropertyPanels': { from: '@ghatana/yappc-canvas', to: '@ghatana/yappc-ui' },
  'OutlinePanel': { from: '@ghatana/yappc-canvas', to: '@ghatana/yappc-ui' },
  'MinimapPanel': { from: '@ghatana/yappc-canvas', to: '@ghatana/yappc-ui' },
  
  // Toolbars
  'GroupingToolbar': { from: '@ghatana/yappc-canvas', to: '@ghatana/yappc-ui' },
  'TestGenToolbar': { from: '@ghatana/yappc-ide', to: '@ghatana/yappc-ui' },
  'OperationsToolbar': { from: '@ghatana/yappc-canvas', to: '@ghatana/yappc-ui' },
  
  // Menus
  'ContextMenu': { from: '@ghatana/yappc-ide', to: '@ghatana/yappc-ui' },
  'CanvasContextMenu': { from: '@ghatana/yappc-canvas', to: '@ghatana/yappc-ui' },
  
  // Layout primitives
  'SplitPanel': { from: '@ghatana/yappc-ide', to: '@ghatana/yappc-ui' },
  'ResizablePanel': { from: '@ghatana/yappc-canvas', to: '@ghatana/yappc-ui' },
  
  // Loading states
  'LoadingOverlay': { from: '@ghatana/yappc-canvas', to: '@ghatana/yappc-ui' },
  'LoadingStates': { from: '@ghatana/yappc-ide', to: '@ghatana/yappc-ui' },
  
  // Dialogs
  'Dialog': { from: '@ghatana/yappc-canvas', to: '@ghatana/yappc-ui' },
  'Modal': { from: '@ghatana/yappc-ide', to: '@ghatana/yappc-ui' },
};

// File extensions to process
const EXTENSIONS = ['.ts', '.tsx', '.js', '.jsx'];

interface ExtractionResult {
  filePath: string;
  changes: string[];
  errors?: string[];
}

/**
 * Check if file should be processed
 */
function shouldProcessFile(filePath: string): boolean {
  const ext = extname(filePath);
  return EXTENSIONS.includes(ext);
}

/**
 * Extract and rewrite imports
 */
function extractImports(filePath: string): ExtractionResult {
  const result: ExtractionResult = {
    filePath,
    changes: [],
    errors: [],
  };

  try {
    let content = readFileSync(filePath, 'utf8');
    let modified = false;

    // Process each component mapping
    for (const [component, mapping] of Object.entries(COMPONENT_MAPPINGS)) {
      // Pattern: import { Component } from '@ghatana/old-lib'
      const importRegex = new RegExp(
        `import\\s+\\{([^}]*)${component}([^}]*)\\}\\s+from\\s+['"]${mapping.from}['"];?`,
        'g'
      );

      content = content.replace(importRegex, (match, before, after) => {
        modified = true;
        const otherImports = (before + after).trim();
        
        result.changes.push(
          `Extracted ${component}: ${mapping.from} → ${mapping.to}`
        );

        if (otherImports) {
          // Keep other imports from old lib, add new import
          return `import {${otherImports}} from '${mapping.from}';\nimport { ${component} } from '${mapping.to}';`;
        } else {
          // Just replace the import
          return `import { ${component} } from '${mapping.to}';`;
        }
      });

      // Pattern: import * as X from '@ghatana/old-lib'
      const wildcardRegex = new RegExp(
        `import\\s+\\*\\s+as\\s+(\\w+)\\s+from\\s+['"]${mapping.from}['"];?`,
        'g'
      );

      content = content.replace(wildcardRegex, (match, alias) => {
        modified = true;
        result.changes.push(
          `Extracted ${component} from wildcard: ${mapping.from} → ${mapping.to}`
        );
        return `${match}\nimport { ${component} } from '${mapping.to}';`;
      });
    }

    // Write changes if modified
    if (modified) {
      writeFileSync(filePath, content, 'utf8');
    }

  } catch (error) {
    result.errors?.push(error instanceof Error ? error.message : String(error));
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
 * Generate extraction report
 */
function generateReport(results: ExtractionResult[]): string {
  const report = [
    '# Shared UI Components Extraction Report',
    `\nGenerated: ${new Date().toISOString()}`,
    '\n## Summary',
    `- Total files processed: ${results.length}`,
    `- Files with changes: ${results.filter(r => r.changes.length > 0).length}`,
    `- Total changes: ${results.reduce((acc, r) => acc + r.changes.length, 0)}`,
    `- Errors: ${results.reduce((acc, r) => acc + (r.errors?.length || 0), 0)}`,
    '\n## Detailed Changes',
  ];

  for (const result of results) {
    if (result.changes.length > 0) {
      report.push(`\n### ${result.filePath}`);
      for (const change of result.changes) {
        report.push(`- ${change}`);
      }
    }
    if (result.errors && result.errors.length > 0) {
      report.push(`\n**Errors in ${result.filePath}:**`);
      for (const error of result.errors) {
        report.push(`- ⚠️ ${error}`);
      }
    }
  }

  report.push('\n## Migration Guide');
  report.push('\nComponents have been extracted to @ghatana/yappc-ui:');
  for (const [component, mapping] of Object.entries(COMPONENT_MAPPINGS)) {
    report.push(`- ${component}: ${mapping.from} → ${mapping.to}`);
  }

  return report.join('\n');
}

/**
 * Main extraction function
 */
function runExtraction(targetDir: string = './', outputDir?: string): void {
  console.log('🚀 Starting Shared UI Components Extraction (Phase 2)...\n');

  const absoluteDir = resolve(targetDir);
  console.log(`📁 Target directory: ${absoluteDir}\n`);

  // Find all files
  const files = findFiles(absoluteDir);
  console.log(`🔍 Found ${files.length} files to process\n`);

  // Process each file
  const results: ExtractionResult[] = [];

  for (const file of files) {
    const result = extractImports(file);
    results.push(result);

    if (result.changes.length > 0) {
      console.log(`✅ ${file}`);
      for (const change of result.changes) {
        console.log(`   → ${change}`);
      }
    }

    if (result.errors && result.errors.length > 0) {
      console.log(`❌ ${file}`);
      for (const error of result.errors) {
        console.log(`   ⚠️ ${error}`);
      }
    }
  }

  // Generate report
  const report = generateReport(results);
  
  if (outputDir) {
    const reportPath = join(outputDir, 'ui-extraction-report.md');
    mkdirSync(outputDir, { recursive: true });
    writeFileSync(reportPath, report, 'utf8');
    console.log(`\n📝 Report saved to: ${reportPath}`);
  } else {
    console.log('\n' + report);
  }

  // Summary
  console.log('\n📊 Extraction Summary:');
  console.log(`   Files processed: ${files.length}`);
  console.log(`   Files with changes: ${results.filter(r => r.changes.length > 0).length}`);
  console.log(`   Total component migrations: ${results.reduce((acc, r) => acc + r.changes.length, 0)}`);
  console.log(`   Errors: ${results.reduce((acc, r) => acc + (r.errors?.length || 0), 0)}`);

  if (results.every(r => !r.errors || r.errors.length === 0)) {
    console.log('\n✨ Extraction completed successfully!');
    console.log('\nNext steps:');
    console.log('1. Review the extraction report');
    console.log('2. Update @ghatana/yappc-ui package.json exports');
    console.log('3. Run tests to verify component functionality');
    console.log('4. Update component documentation');
  } else {
    console.log('\n⚠️  Extraction completed with errors. Please review.');
    process.exit(1);
  }
}

// Run if called directly
const isMainModule = import.meta.url === `file://${process.argv[1]}`;
if (isMainModule) {
  const targetDir = process.argv[2] || './';
  const outputDir = process.argv[3];
  runExtraction(targetDir, outputDir);
}

export { runExtraction, extractImports, COMPONENT_MAPPINGS, generateReport };
