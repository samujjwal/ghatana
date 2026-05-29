#!/usr/bin/env node

import { existsSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');

const MARKDOWN_EXTENSIONS = new Set(['.md', '.mdx']);
const SCRIPT_EXTENSIONS = new Set(['.mjs', '.js', '.cjs', '.sh', '.ps1']);

function normalizePath(filePath) {
  return filePath.replace(/\\/g, '/');
}

function walkFiles(rootDir, predicate) {
  if (!existsSync(rootDir)) {
    return [];
  }

  const collected = [];
  const stack = [rootDir];

  while (stack.length > 0) {
    const current = stack.pop();
    if (!current) {
      continue;
    }

    for (const entry of readdirSync(current)) {
      const fullPath = path.join(current, entry);
      const stats = statSync(fullPath);

      if (stats.isDirectory()) {
        if (entry === 'node_modules' || entry === '.git' || entry === 'dist' || entry === 'build') {
          continue;
        }
        stack.push(fullPath);
        continue;
      }

      if (predicate(fullPath)) {
        collected.push(normalizePath(path.relative(repoRoot, fullPath)));
      }
    }
  }

  return collected.sort();
}

export function collectDocumentationSurfaces(root = repoRoot) {
  return walkFiles(path.join(root, 'docs'), (filePath) =>
    MARKDOWN_EXTENSIONS.has(path.extname(filePath).toLowerCase()),
  );
}

export function collectScriptSurfaces(root = repoRoot, scriptRoots = ['scripts']) {
  const all = new Set();
  for (const scriptRoot of scriptRoots) {
    const absoluteRoot = path.join(root, scriptRoot);
    const files = walkFiles(absoluteRoot, (filePath) =>
      SCRIPT_EXTENSIONS.has(path.extname(filePath).toLowerCase()),
    );
    for (const file of files) {
      all.add(file);
    }
  }
  return [...all].sort();
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const output = {
    docs: collectDocumentationSurfaces(repoRoot),
    scripts: collectScriptSurfaces(repoRoot, ['scripts', 'products/yappc/scripts', 'products/yappc/frontend/scripts']),
  };
  console.log(JSON.stringify(output, null, 2));
}
