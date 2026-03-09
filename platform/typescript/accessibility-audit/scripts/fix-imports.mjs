#!/usr/bin/env node
/**
 * Post-build script to add .js extensions to relative imports in compiled output
 * This is needed for Node ESM compatibility when TypeScript is configured with
 * moduleResolution: "bundler"
 */
import { readdir, readFile, writeFile } from 'fs/promises';
import { join } from 'path';

const distDir = new URL('../dist', import.meta.url).pathname;

async function* walkDir(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  for (const entry of entries) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      yield* walkDir(path);
    } else {
      yield path;
    }
  }
}

async function fixImports(filePath) {
  if (!filePath.endsWith('.js')) return;
  
  let content = await readFile(filePath, 'utf-8');
  let modified = false;
  
  // Match import/export statements with relative paths that don't have .js extension
  // Patterns to match:
  // - import ... from './path'
  // - export ... from './path'
  // - import('./path')
  const patterns = [
    // Static imports/exports: from './path' or from "./path"
    /(from\s+['"])(\.\.?\/[^'"]+?)(['"])/g,
    // Dynamic imports: import('./path') or import("./path")
    /(import\s*\(\s*['"])(\.\.?\/[^'"]+?)(['"]\s*\))/g,
  ];
  
  for (const pattern of patterns) {
    content = content.replace(pattern, (match, prefix, path, suffix) => {
      // Skip if already has an extension
      if (path.match(/\.[a-z]+$/i)) {
        return match;
      }
      
      // Check if path ends with a known directory name that has an index.js
      // In this package: formatters, scoring, api, types all have index.js
      const knownDirs = ['formatters', 'scoring', 'api', 'types'];
      const pathSegments = path.split('/');
      const lastSegment = pathSegments[pathSegments.length - 1];
      
      if (knownDirs.includes(lastSegment)) {
        // It's a directory import, add /index.js
        modified = true;
        return `${prefix}${path}/index.js${suffix}`;
      }
      
      // Otherwise it's a file, add .js
      modified = true;
      return `${prefix}${path}.js${suffix}`;
    });
  }
  
  if (modified) {
    await writeFile(filePath, content, 'utf-8');
    console.log(`Fixed: ${filePath.replace(distDir, 'dist')}`);
  }
}

async function main() {
  console.log('Adding .js extensions to imports in dist/...');
  let count = 0;
  
  for await (const file of walkDir(distDir)) {
    await fixImports(file);
    count++;
  }
  
  console.log(`Processed ${count} files`);
}

main().catch(console.error);
