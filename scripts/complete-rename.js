#!/usr/bin/env node
/**
 * @fileoverview Complete Package Rename Script
 * Fixes all SCOPE_MISMATCH and DEPRECATED_NAMING violations
 */

'use strict';

const fs = require('fs');
const path = require('path');

// Complete rename mapping for all remaining violations
const RENAME_MAP = {
  // AEP packages
  '@ghatana/aep-api': '@aep/api',
  
  // DCMAAR packages
  '@ghatana/dcmaar-agent-core': '@dcmaar/agent-core',
  '@ghatana/dcmaar-agent-react-native': '@dcmaar/agent-react-native',
  '@ghatana/dcmaar-backend': '@dcmaar/backend',
  '@ghatana/dcmaar-browser-extension': '@dcmaar/browser-extension',
  '@ghatana/dcmaar-device-health': '@dcmaar/device-health',
  '@ghatana/dcmaar-guardian-plugins': '@dcmaar/guardian-plugins',
  '@ghatana/dcmaar-host': '@dcmaar/host',
  '@ghatana/dcmaar-bridge-protocol': '@dcmaar/bridge-protocol',
  '@ghatana/dcmaar-agent-types': '@dcmaar/agent-types',
  '@ghatana/dcmaar-agent-ui': '@dcmaar/agent-ui',
  '@ghatana/dcmaar-browser-extension-core': '@dcmaar/browser-extension-core',
  '@ghatana/dcmaar-browser-extension-ui': '@dcmaar/browser-extension-ui',
  '@ghatana/dcmaar-config-presets': '@dcmaar/config-presets',
  '@ghatana/dcmaar-plugin-abstractions': '@dcmaar/plugin-abstractions',
  '@ghatana/dcmaar-plugin-extension': '@dcmaar/plugin-extension',
  '@ghatana/dcmaar-shared-ui-charts': '@dcmaar/shared-ui-charts',
  '@ghatana/dcmaar-shared-ui-core': '@dcmaar/shared-ui-core',
  '@ghatana/dcmaar-shared-ui-tailwind': '@dcmaar/shared-ui-tailwind',
  '@ghatana/dcmaar-types': '@dcmaar/types',
  '@ghatana/dcmaar-ai-platform-adapters': '@dcmaar/ai-platform-adapters',
  
  // Flashit packages
  '@ghatana/flashit-shared': '@flashit/shared',
  
  // TutorPutor packages
  '@ghatana/tutorputor-api-gateway': '@tutorputor/api-gateway',
  '@ghatana/tutorputor-admin': '@tutorputor/admin',
  '@ghatana/tutorputor-mobile': '@tutorputor/mobile',
  '@ghatana/tutorputor-assessments': '@tutorputor/assessments',
  '@ghatana/tutorputor-physics-simulation': '@tutorputor/physics-simulation',
  '@ghatana/tutorputor-ai-proxy': '@tutorputor/ai-proxy',
  '@ghatana/tutorputor-sim-sdk': '@tutorputor/sim-sdk',
  '@ghatana/tutorputor-kernel-registry': '@tutorputor/kernel-registry',
  '@ghatana/tutorputor-lti': '@tutorputor/lti',
  '@ghatana/tutorputor-payments': '@tutorputor/payments',
  '@ghatana/tutorputor-domain-loader': '@tutorputor/domain-loader',
  
  // Audio-Video packages
  '@ghatana/audio-video-desktop': '@audio-video/desktop',
  '@ghatana/audio-video-client': '@audio-video/client',
  '@ghatana/audio-video-types': '@audio-video/types',
  '@ghatana/audio-video-ui': '@audio-video/ui',
  
  // YAPPC packages (DEPRECATED_NAMING fixes)
  '@ghatana/yappc-api': '@yappc/api',
  '@ghatana/yappc-auth': '@yappc/auth',
  '@ghatana/yappc-code-editor': '@yappc/code-editor',
  '@ghatana/yappc-collab': '@yappc/collab',
  '@ghatana/yappc-component-traceability': '@yappc/ui/traceability',
  '@ghatana/yappc-governance': '@yappc/governance',
  '@ghatana/yappc-eslint-local-rules': '@yappc/eslint-local-rules',
};

function findFiles(dir, extensions, exclude = ['node_modules', '.git']) {
  const files = [];
  
  function walk(currentDir) {
    const entries = fs.readdirSync(currentDir, { withFileTypes: true });
    
    for (const entry of entries) {
      const fullPath = path.join(currentDir, entry.name);
      
      if (entry.isDirectory()) {
        if (!exclude.includes(entry.name) && !entry.name.startsWith('.')) {
          walk(fullPath);
        }
      } else if (entry.isFile() && extensions.some(ext => entry.name.endsWith(ext))) {
        files.push(fullPath);
      }
    }
  }
  
  walk(dir);
  return files;
}

function updateFile(filePath, renames) {
  let content = fs.readFileSync(filePath, 'utf-8');
  let modified = false;
  
  for (const [oldName, newName] of Object.entries(renames)) {
    // Update package name declarations
    const nameRegex = new RegExp(`"name":\\s*"${oldName}"`, 'g');
    if (nameRegex.test(content)) {
      content = content.replace(nameRegex, `"name": "${newName}"`);
      modified = true;
      console.log(`  📦 ${filePath}: ${oldName} → ${newName}`);
    }
    
    // Update dependencies
    const depRegex = new RegExp(`"${oldName}":`, 'g');
    if (depRegex.test(content)) {
      content = content.replace(depRegex, `"${newName}":`);
      modified = true;
    }
    
    // Update imports in source files
    if (filePath.endsWith('.ts') || filePath.endsWith('.tsx') || filePath.endsWith('.js')) {
      const importRegex = new RegExp(
        `(import\\s+(?:[^'"]*\\s+from\\s+)?['"])${oldName}(['"]|/[^'"]*)`,
        'g'
      );
      if (importRegex.test(content)) {
        content = content.replace(importRegex, `$1${newName}$2`);
        modified = true;
      }
    }
  }
  
  if (modified) {
    fs.writeFileSync(filePath, content, 'utf-8');
    return true;
  }
  
  return false;
}

function main() {
  const command = process.argv[2];
  const workspaceRoot = process.cwd();
  
  switch (command) {
    case 'dry-run':
      console.log('🔍 DRY RUN: Checking what would be renamed...\n');
      console.log(`Total renames in mapping: ${Object.keys(RENAME_MAP).length}\n`);
      
      for (const [oldName, newName] of Object.entries(RENAME_MAP)) {
        console.log(`  ${oldName} → ${newName}`);
      }
      break;
      
    case 'rename':
      console.log('🚀 Executing complete package renames...\n');
      
      // Update package.json files
      const packageJsonFiles = findFiles(workspaceRoot, ['package.json'], ['node_modules', '.git']);
      console.log(`Found ${packageJsonFiles.length} package.json files\n`);
      
      let updatedPackageCount = 0;
      for (const file of packageJsonFiles) {
        if (updateFile(file, RENAME_MAP)) {
          updatedPackageCount++;
        }
      }
      
      console.log(`\n✅ Updated ${updatedPackageCount} package.json files`);
      
      // Update source files
      const sourceFiles = findFiles(
        path.join(workspaceRoot, 'products'),
        ['.ts', '.tsx', '.js', '.jsx'],
        ['node_modules', '.git', 'dist', 'build']
      );
      
      let updatedSourceCount = 0;
      for (const file of sourceFiles) {
        if (updateFile(file, RENAME_MAP)) {
          updatedSourceCount++;
        }
      }
      
      console.log(`\n✅ Updated ${updatedSourceCount} source files`);
      
      console.log('\n⚠️  Next steps:');
      console.log('  1. Review changes: git diff --stat');
      console.log('  2. Run pnpm install to update workspace');
      console.log('  3. Run architecture compliance check');
      break;
      
    case 'stats':
      console.log('📊 Package Rename Statistics\n');
      console.log(`Total renames: ${Object.keys(RENAME_MAP).length}`);
      
      const byPrefix = {};
      for (const oldName of Object.keys(RENAME_MAP)) {
        const prefix = oldName.split('/')[0];
        byPrefix[prefix] = (byPrefix[prefix] || 0) + 1;
      }
      
      console.log('\nBy prefix:');
      for (const [prefix, count] of Object.entries(byPrefix).sort()) {
        console.log(`  ${prefix}: ${count}`);
      }
      break;
      
    default:
      console.log('Complete Package Rename Script\n');
      console.log('Usage: node complete-rename.js [command]\n');
      console.log('Commands:');
      console.log('  dry-run   - Preview changes without applying');
      console.log('  rename    - Execute renames (modifies files)');
      console.log('  stats     - Show rename statistics');
      console.log('');
      console.log('Examples:');
      console.log('  node complete-rename.js dry-run');
      console.log('  node complete-rename.js rename');
  }
}

if (require.main === module) {
  main();
}

module.exports = { RENAME_MAP, updateFile };
