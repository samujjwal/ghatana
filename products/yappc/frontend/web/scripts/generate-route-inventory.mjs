#!/usr/bin/env node
/**
 * Route Inventory Generator
 *
 * Parses src/routes.ts and generates a markdown inventory of all mounted routes.
 * Can be run as:
 *   node scripts/generate-route-inventory.mjs
 *   node scripts/generate-route-inventory.mjs --check
 *
 * --check validates the generated inventory against the existing inventory file
 * and exits with non-zero if they differ.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const ROUTES_FILE = path.join(ROOT, 'src', 'routes.ts');
const INVENTORY_FILE = path.join(ROOT, 'docs', 'route-inventory.md');

function parseRoutes(content) {
  const lines = content.split('\n');
  const routes = [];
  const stack = [{ prefix: '', indent: 0 }];

  for (const rawLine of lines) {
    const line = rawLine.replace(/\/\/.*/, ''); // strip inline comments
    const indent = rawLine.search(/\S/);
    if (indent === -1) continue;

    // Pop stack to current indent level
    while (stack.length > 1 && stack[stack.length - 1].indent >= indent) {
      stack.pop();
    }

    const parent = stack[stack.length - 1];

    // route('path', 'file')
    const routeMatch = line.match(/route\(['"]([^'"]+)['"],\s*['"]([^'"]+)['"]\s*(?:,\s*\[)?/);
    if (routeMatch) {
      const pathSegment = routeMatch[1];
      const file = routeMatch[2];
      const fullPath =
        pathSegment === '*'
          ? `${parent.prefix}/*`
          : pathSegment === ''
            ? parent.prefix
            : `${parent.prefix}/${pathSegment}`.replace(/^\//, '');

      routes.push({ path: fullPath, file, children: line.includes('[') });

      if (line.includes('[')) {
        stack.push({ prefix: fullPath, indent });
      }
      continue;
    }

    // index('file')
    const indexMatch = line.match(/index\(['"]([^'"]+)['"]\)/);
    if (indexMatch) {
      const file = indexMatch[1];
      routes.push({ path: parent.prefix || '/', file, children: false });
      continue;
    }

    // layout('file', [ ... ]) — doesn't add a path prefix
    const layoutMatch = line.match(/layout\(['"]([^'"]+)['"],\s*\[/);
    if (layoutMatch) {
      stack.push({ prefix: parent.prefix, indent });
      continue;
    }
  }

  return routes;
}

function generateMarkdown(routes) {
  const now = new Date().toISOString().split('T')[0];
  const lines = [
    '# YAPPC Route Inventory',
    '',
    `> Auto-generated from \`src/routes.ts\` on ${now}.`,
    '> Run `node scripts/generate-route-inventory.mjs` to regenerate.',
    '',
    '| # | URL Path | Route File |',
    '|---|----------|------------|',
  ];

  routes.forEach((r, i) => {
    lines.push(`| ${i + 1} | \`${r.path}\` | \`${r.file}\` |`);
  });

  lines.push('');
  return lines.join('\n');
}

function main() {
  if (!fs.existsSync(ROUTES_FILE)) {
    console.error(`Routes file not found: ${ROUTES_FILE}`);
    process.exit(1);
  }

  const content = fs.readFileSync(ROUTES_FILE, 'utf8');
  const routes = parseRoutes(content);

  const markdown = generateMarkdown(routes);

  const checkMode = process.argv.includes('--check');

  if (checkMode) {
    if (!fs.existsSync(INVENTORY_FILE)) {
      console.error(`Inventory file not found: ${INVENTORY_FILE}`);
      process.exit(1);
    }
    const existing = fs.readFileSync(INVENTORY_FILE, 'utf8');
    // Compare ignoring the generation date line
    const stripDate = (s) => s.replace(/> Auto-generated from `src\/routes\.ts` on \d{4}-\d{2}-\d{2}\./, '');
    if (stripDate(existing) !== stripDate(markdown)) {
      console.error('Route inventory is out of date. Run `node scripts/generate-route-inventory.mjs` to regenerate.');
      process.exit(1);
    }
    console.log('Route inventory is up to date.');
    process.exit(0);
  }

  // Ensure docs directory exists
  const docsDir = path.dirname(INVENTORY_FILE);
  if (!fs.existsSync(docsDir)) {
    fs.mkdirSync(docsDir, { recursive: true });
  }

  fs.writeFileSync(INVENTORY_FILE, markdown);
  console.log(`Route inventory written to ${INVENTORY_FILE} (${routes.length} routes).`);
}

main();
