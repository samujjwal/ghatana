#!/usr/bin/env node
/**
 * @fileoverview Package Rename Automation Script
 * Renames packages to fix SCOPE_MISMATCH and DEPRECATED_NAMING violations
 */

'use strict';

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Package rename mapping for SCOPE_MISMATCH fixes
const SCOPE_RENAMES = {
  // DCMAAR packages
  '@ghatana/dcmaar-desktop': '@dcmaar/desktop',
  '@ghatana/dcmaar-connectors': '@dcmaar/connectors',
  '@ghatana/dcmaar-dashboard-core': '@dcmaar/dashboard-core',
  '@ghatana/dcmaar-parent-mobile': '@dcmaar/parent-mobile',
  '@ghatana/dcmaar-child-mobile': '@dcmaar/child-mobile',
  '@ghatana/dcmaar-parent-dashboard': '@dcmaar/parent-dashboard',
  
  // Data Cloud packages
  '@ghatana/data-cloud-ui': '@data-cloud/ui',
  
  // AEP packages
  '@ghatana/aep-ui': '@aep/ui',
  
  // Flashit packages
  '@ghatana/flashit-web': '@flashit/web',
  '@ghatana/flashit-mobile': '@flashit/mobile',
  '@ghatana/flashit-web-api': '@flashit/web-api',
  
  // TutorPutor packages
  '@ghatana/tutorputor-platform': '@tutorputor/platform',
  '@ghatana/tutorputor-web': '@tutorputor/web',
  '@ghatana/tutorputor-db': '@tutorputor/db',
  '@ghatana/tutorputor-contracts': '@tutorputor/contracts',
  '@ghatana/tutorputor-learning-kernel': '@tutorputor/learning-kernel',
  '@ghatana/tutorputor-sim-renderer': '@tutorputor/sim-renderer',
  '@ghatana/tutorputor-simulation-engine': '@tutorputor/simulation-engine',
  '@ghatana/tutorputor-ui-shared': '@tutorputor/ui-shared',
  '@ghatana/tutorputor-vr-labs': '@tutorputor/vr-labs',
  
  // Software-Org packages
  '@ghatana/software-org-web': '@software-org/web',
  '@ghatana/software-org-backend': '@software-org/backend',
};

// Package rename mapping for DEPRECATED_NAMING fixes
const DEPRECATED_RENAMES = {
  // YAPPC packages (from @ghatana/yappc-* to @yappc/*)
  '@ghatana/yappc-ui': '@yappc/ui',
  '@ghatana/yappc-ai': '@yappc/ai',
  '@ghatana/yappc-canvas': '@yappc/canvas',
  '@ghatana/yappc-chat': '@yappc/chat',
  '@ghatana/yappc-notifications': '@yappc/notifications',
  '@ghatana/yappc-realtime': '@yappc/realtime',
  '@ghatana/yappc-core': '@yappc/core',
  '@ghatana/yappc-crdt': '@yappc/crdt',
  '@ghatana/yappc-ide': '@yappc/ide',
  '@ghatana/yappc-testing': '@yappc/testing',
  '@ghatana/yappc-types': '@yappc/types',
  '@ghatana/yappc-utils': '@yappc/utils',
  '@ghatana/yappc-config': '@yappc/config',
  '@ghatana/yappc-api-app': '@yappc/api-app',
  '@ghatana/yappc-web-app': '@yappc/web-app',
  '@ghatana/yappc-docs': '@yappc/docs',
  '@ghatana/yappc-eslint-config-custom': '@yappc/eslint-config-custom',
  '@ghatana/yappc-tsconfig': '@yappc/tsconfig',
  '@ghatana/yappc-vite-plugin-live-edit': '@yappc/vite-plugin-live-edit',
  '@ghatana/yappc-canvas-core': '@yappc/canvas-core',
  '@ghatana/yappc-live-preview-server': '@yappc/live-preview-server',
  '@ghatana/yappc-canvas-sync': '@yappc/canvas-sync',
};

const ALL_RENAMES = { ...SCOPE_RENAMES, ...DEPRECATED_RENAMES };

function findPackageJsonFiles() {
  const files = [];
  const workspaceRoot = process.cwd();
  
  function walk(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);
      
      if (entry.isDirectory() && !entry.name.startsWith('.') && entry.name !== 'node_modules') {
        // Check if this directory has a package.json
        const packageJsonPath = path.join(fullPath, 'package.json');
        if (fs.existsSync(packageJsonPath)) {
          files.push(packageJsonPath);
        }
        
        // Recurse into subdirectories
        walk(fullPath);
      }
    }
  }
  
  // Only walk products and platform directories
  ['products', 'platform'].forEach(dir => {
    const fullDir = path.join(workspaceRoot, dir);
    if (fs.existsSync(fullDir)) {
      walk(fullDir);
    }
  });
  
  return files;
}

function updatePackageJson(filePath, renames) {
  let content = fs.readFileSync(filePath, 'utf-8');
  const originalContent = content;
  let modified = false;
  
  // Update name field
  for (const [oldName, newName] of Object.entries(renames)) {
    // Match exact package name with quotes
    const nameRegex = new RegExp(`"name":\\s*"${oldName}"`, 'g');
    if (nameRegex.test(content)) {
      content = content.replace(nameRegex, `"name": "${newName}"`);
      modified = true;
      console.log(`  📝 ${filePath}: ${oldName} → ${newName}`);
    }
  }
  
  // Update dependencies
  for (const [oldName, newName] of Object.entries(renames)) {
    // Match in dependencies, devDependencies, peerDependencies
    const depRegex = new RegExp(`"${oldName}":`, 'g');
    if (depRegex.test(content)) {
      content = content.replace(depRegex, `"${newName}":`);
      modified = true;
      console.log(`  📦 ${filePath}: ${oldName} → ${newName} (dependency)`);
    }
  }
  
  if (modified) {
    fs.writeFileSync(filePath, content, 'utf-8');
    return true;
  }
  
  return false;
}

function updateSourceFiles(renames) {
  console.log('\n🔍 Scanning source files for imports...');
  
  const extensions = ['.ts', '.tsx', '.js', '.jsx'];
  const filesToCheck = [];
  
  function walk(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);
      
      if (entry.isDirectory() && !entry.name.startsWith('.') && entry.name !== 'node_modules') {
        walk(fullPath);
      } else if (entry.isFile() && extensions.some(ext => entry.name.endsWith(ext))) {
        filesToCheck.push(fullPath);
      }
    }
  }
  
  ['products', 'platform'].forEach(dir => {
    const fullDir = path.join(process.cwd(), dir);
    if (fs.existsSync(fullDir)) {
      walk(fullDir);
    }
  });
  
  let updatedFiles = 0;
  
  for (const filePath of filesToCheck) {
    let content = fs.readFileSync(filePath, 'utf-8');
    let modified = false;
    
    for (const [oldName, newName] of Object.entries(renames)) {
      // Match import statements
      const importRegex = new RegExp(
        `(import\\s+(?:[^'"]*\\s+from\\s+)?['"])${oldName}(['"]|/[^'"]*)`,
        'g'
      );
      
      if (importRegex.test(content)) {
        content = content.replace(importRegex, `$1${newName}$2`);
        modified = true;
      }
    }
    
    if (modified) {
      fs.writeFileSync(filePath, content, 'utf-8');
      updatedFiles++;
      console.log(`  📝 ${filePath}`);
    }
  }
  
  console.log(`\n✅ Updated ${updatedFiles} source files`);
}

function main() {
  const command = process.argv[2];
  
  switch (command) {
    case 'dry-run':
      console.log('🔍 DRY RUN: Checking what would be renamed...\n');
      
      console.log('SCOPE_MISMATCH fixes:');
      for (const [oldName, newName] of Object.entries(SCOPE_RENAMES)) {
        console.log(`  ${oldName} → ${newName}`);
      }
      
      console.log('\nDEPRECATED_NAMING fixes:');
      for (const [oldName, newName] of Object.entries(DEPRECATED_RENAMES)) {
        console.log(`  ${oldName} → ${newName}`);
      }
      
      const packageJsonFiles = findPackageJsonFiles();
      console.log(`\nFound ${packageJsonFiles.length} package.json files to scan`);
      break;
      
    case 'rename':
      console.log('🚀 Executing package renames...\n');
      
      const files = findPackageJsonFiles();
      console.log(`Found ${files.length} package.json files\n`);
      
      let updatedPackageCount = 0;
      
      for (const file of files) {
        if (updatePackageJson(file, ALL_RENAMES)) {
          updatedPackageCount++;
        }
      }
      
      console.log(`\n✅ Updated ${updatedPackageCount} package.json files`);
      
      // Update source files
      updateSourceFiles(ALL_RENAMES);
      
      console.log('\n⚠️  Next steps:');
      console.log('  1. Review changes: git diff');
      console.log('  2. Run pnpm install to update workspace');
      console.log('  3. Update pnpm-workspace.yaml if needed');
      console.log('  4. Run tests to verify');
      break;
      
    case 'stats':
      console.log('📊 Package Rename Statistics\n');
      console.log(`SCOPE_MISMATCH fixes: ${Object.keys(SCOPE_RENAMES).length}`);
      console.log(`DEPRECATED_NAMING fixes: ${Object.keys(DEPRECATED_RENAMES).length}`);
      console.log(`Total renames: ${Object.keys(ALL_RENAMES).length}`);
      break;
      
    default:
      console.log('Package Rename Automation Script\n');
      console.log('Usage: node rename-packages.js [command]\n');
      console.log('Commands:');
      console.log('  dry-run   - Preview changes without applying');
      console.log('  rename    - Execute renames (modifies files)');
      console.log('  stats     - Show rename statistics');
      console.log('');
      console.log('Examples:');
      console.log('  node rename-packages.js dry-run');
      console.log('  node rename-packages.js rename');
  }
}

if (require.main === module) {
  main();
}

module.exports = { SCOPE_RENAMES, DEPRECATED_RENAMES, ALL_RENAMES, updatePackageJson };
