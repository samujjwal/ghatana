#!/usr/bin/env node
/**
 * @fileoverview Codemod for migrating @ghatana/yappc-* packages to @yappc/*
 * Run: node scripts/codemods/migrate-yappc-packages.js [target-directory]
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const MIGRATION_MAP = {
  // Package name migrations
  '@ghatana/yappc-ui': '@yappc/ui',
  '@ghatana/yappc-ai': '@yappc/ai',
  '@ghatana/yappc-canvas': '@yappc/canvas',
  '@ghatana/yappc-chat': '@yappc/chat',
  '@ghatana/yappc-notifications': '@yappc/notifications',
  '@ghatana/yappc-core': '@yappc/core',

  // Import path migrations (subpath patterns)
  '@ghatana/yappc-ui/': '@yappc/ui/',
  '@ghatana/yappc-ai/': '@yappc/ai/',
  '@ghatana/yappc-canvas/': '@yappc/canvas/',
  '@ghatana/yappc-chat/': '@yappc/chat/',
  '@ghatana/yappc-notifications/': '@yappc/notifications/',
  '@ghatana/yappc-core/': '@yappc/core/',
};

const DEPRECATED_PACKAGES = [
  '@ghatana/ui',
  '@ghatana/utils',
];

const REPLACEMENTS = {
  '@ghatana/ui': '@ghatana/design-system',
  '@ghatana/utils': '@ghatana/platform-utils',
};

function findFiles(dir, extensions = ['.ts', '.tsx', '.js', '.jsx', '.json']) {
  const files = [];

  function walk(currentDir) {
    const entries = fs.readdirSync(currentDir, { withFileTypes: true });

    for (const entry of entries) {
      const fullPath = path.join(currentDir, entry.name);

      if (entry.isDirectory()) {
        // Skip node_modules and .git
        if (entry.name === 'node_modules' || entry.name === '.git') continue;
        walk(fullPath);
      } else if (entry.isFile()) {
        const ext = path.extname(entry.name);
        if (extensions.includes(ext)) {
          files.push(fullPath);
        }
      }
    }
  }

  walk(dir);
  return files;
}

function migrateImports(content, filePath) {
  let modified = false;
  let newContent = content;

  // Migrate @ghatana/yappc-* to @yappc/*
  for (const [oldPkg, newPkg] of Object.entries(MIGRATION_MAP)) {
    const regex = new RegExp(`(['"])${oldPkg}(['"/])`, 'g');
    if (regex.test(newContent)) {
      newContent = newContent.replace(regex, `$1${newPkg}$2`);
      modified = true;
      console.log(`  [IMPORT] ${oldPkg} → ${newPkg} in ${filePath}`);
    }
  }

  // Migrate deprecated packages
  for (const [oldPkg, newPkg] of Object.entries(REPLACEMENTS)) {
    const regex = new RegExp(`(['"])${oldPkg}(['"/])`, 'g');
    if (regex.test(newContent)) {
      newContent = newContent.replace(regex, `$1${newPkg}$2`);
      modified = true;
      console.log(`  [DEPRECATED] ${oldPkg} → ${newPkg} in ${filePath}`);
    }
  }

  return { content: newContent, modified };
}

function migratePackageJson(content, filePath) {
  let modified = false;
  const pkg = JSON.parse(content);

  // Migrate dependencies
  if (pkg.dependencies) {
    for (const [oldPkg, newPkg] of Object.entries(MIGRATION_MAP)) {
      if (pkg.dependencies[oldPkg]) {
        const version = pkg.dependencies[oldPkg];
        delete pkg.dependencies[oldPkg];
        pkg.dependencies[newPkg] = version;
        modified = true;
        console.log(`  [DEPS] ${oldPkg} → ${newPkg} in ${filePath}`);
      }
    }

    for (const [oldPkg, newPkg] of Object.entries(REPLACEMENTS)) {
      if (pkg.dependencies[oldPkg]) {
        const version = pkg.dependencies[oldPkg];
        delete pkg.dependencies[oldPkg];
        pkg.dependencies[newPkg] = version;
        modified = true;
        console.log(`  [DEPS] ${oldPkg} → ${newPkg} in ${filePath}`);
      }
    }
  }

  // Migrate devDependencies
  if (pkg.devDependencies) {
    for (const [oldPkg, newPkg] of Object.entries(MIGRATION_MAP)) {
      if (pkg.devDependencies[oldPkg]) {
        const version = pkg.devDependencies[oldPkg];
        delete pkg.devDependencies[oldPkg];
        pkg.devDependencies[newPkg] = version;
        modified = true;
        console.log(`  [DEVDEPS] ${oldPkg} → ${newPkg} in ${filePath}`);
      }
    }
  }

  // Migrate peerDependencies
  if (pkg.peerDependencies) {
    for (const [oldPkg, newPkg] of Object.entries(MIGRATION_MAP)) {
      if (pkg.peerDependencies[oldPkg]) {
        const version = pkg.peerDependencies[oldPkg];
        delete pkg.peerDependencies[oldPkg];
        pkg.peerDependencies[newPkg] = version;
        modified = true;
        console.log(`  [PEERDEPS] ${oldPkg} → ${newPkg} in ${filePath}`);
      }
    }
  }

  return { content: JSON.stringify(pkg, null, 2), modified };
}

function processFile(filePath) {
  const content = fs.readFileSync(filePath, 'utf-8');
  const fileName = path.basename(filePath);

  let result;

  if (fileName === 'package.json') {
    result = migratePackageJson(content, filePath);
  } else {
    result = migrateImports(content, filePath);
  }

  if (result.modified) {
    fs.writeFileSync(filePath, result.content, 'utf-8');
    return true;
  }

  return false;
}

function main() {
  const targetDir = process.argv[2] || '.';
  const absoluteDir = path.resolve(targetDir);

  console.log(`🔧 YAPPC Package Migration Codemod`);
  console.log(`   Target: ${absoluteDir}\n`);

  const files = findFiles(absoluteDir);
  let modifiedCount = 0;

  for (const file of files) {
    if (processFile(file)) {
      modifiedCount++;
    }
  }

  console.log(`\n✅ Migration complete!`);
  console.log(`   Files modified: ${modifiedCount}`);
  console.log(`\nNext steps:`);
  console.log(`   1. Run pnpm install to update lockfile`);
  console.log(`   2. Run pnpm build to verify changes`);
  console.log(`   3. Run pnpm test to ensure no regressions`);
}

if (require.main === module) {
  main();
}

module.exports = { migrateImports, migratePackageJson, MIGRATION_MAP };
