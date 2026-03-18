#!/usr/bin/env node
/**
 * @fileoverview Structure Reorganization Script
 * Moves packages to target folder structure per audit plan
 */

'use strict';

const fs = require('fs');
const path = require('path');

// Target structure mapping
const REORGANIZATION_MAP = {
  // YAPPC libraries → domain/yappc/
  'products/yappc/frontend/libs/ui-new': 'domain/yappc/ui',
  'products/yappc/frontend/libs/ai-new': 'domain/yappc/ai',
  'products/yappc/frontend/libs/canvas-new': 'domain/yappc/canvas',
  'products/yappc/frontend/libs/chat': 'domain/yappc/chat',
  'products/yappc/frontend/libs/core': 'domain/yappc/core',
  'products/yappc/frontend/libs/types': 'domain/yappc/types',
  
  // YAPPC apps → apps/
  'products/yappc/frontend/apps/web': 'apps/yappc-web',
  
  // Platform organization
  'platform/typescript/design-system': 'platform/typescript/capabilities/design-system',
  'platform/typescript/canvas': 'platform/typescript/capabilities/canvas-core',
  'platform/typescript/realtime': 'platform/typescript/capabilities/realtime-engine',
  'platform/typescript/utils': 'platform/typescript/foundation/platform-utils',
};

// File update operations
const FILE_UPDATES = {
  'pnpm-workspace.yaml': updatePnpmWorkspace,
  'tsconfig.base.json': updateTsconfigPaths,
  'settings.gradle.kts': updateGradleSettings,
};

function updatePnpmWorkspace(content, moves) {
  let updated = content;
  for (const [oldPath, newPath] of Object.entries(moves)) {
    updated = updated.replace(oldPath, newPath);
  }
  return updated;
}

function updateTsconfigPaths(content, moves) {
  // Update path mappings in tsconfig
  let updated = content;
  for (const [oldPath, newPath] of Object.entries(moves)) {
    updated = updated.replace(oldPath, newPath);
  }
  return updated;
}

function updateGradleSettings(content, moves) {
  // Update Gradle module paths
  let updated = content;
  for (const [oldPath, newPath] of Object.entries(moves)) {
    updated = updated.replace(oldPath, newPath);
  }
  return updated;
}

function generateMoveScript() {
  console.log('#!/bin/bash\n');
  console.log('# Structure Reorganization Script');
  console.log('# Generated:', new Date().toISOString());
  console.log('set -e\n');
  
  for (const [oldPath, newPath] of Object.entries(REORGANIZATION_MAP)) {
    const fullOldPath = path.join(process.cwd(), oldPath);
    const fullNewPath = path.join(process.cwd(), newPath);
    
    if (fs.existsSync(fullOldPath)) {
      console.log(`# Move ${oldPath} → ${newPath}`);
      console.log(`mkdir -p "${path.dirname(fullNewPath)}"`);
      console.log(`git mv "${fullOldPath}" "${fullNewPath}"`);
      console.log('');
    }
  }
  
  console.log('# Update workspace configuration files');
  console.log('echo "Updating workspace configurations..."');
  console.log('node scripts/update-workspace-configs.js');
}

function main() {
  const command = process.argv[2];
  
  switch (command) {
    case 'generate':
      generateMoveScript();
      break;
    case 'verify':
      console.log('Verifying reorganization plan...');
      for (const [oldPath, newPath] of Object.entries(REORGANIZATION_MAP)) {
        const exists = fs.existsSync(path.join(process.cwd(), oldPath));
        console.log(`${exists ? '✅' : '❌'} ${oldPath} → ${newPath}`);
      }
      break;
    default:
      console.log('Usage: node reorg-structure.js [generate|verify]');
      console.log('');
      console.log('Commands:');
      console.log('  generate  - Generate bash script for moves');
      console.log('  verify    - Verify which moves are possible');
  }
}

if (require.main === module) {
  main();
}

module.exports = { REORGANIZATION_MAP, FILE_UPDATES };
