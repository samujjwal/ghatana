#!/usr/bin/env node
/**
 * Circular Dependency Detection for TypeScript Workspace Packages
 *
 * Builds a dependency graph from all workspace package.json files and detects
 * cycles using iterative DFS. Reports every cycle and exits non-zero when any
 * are found.
 *
 * Only workspace: protocol dependencies are considered — external npm packages
 * cannot form workspace cycles.
 *
 * Usage: node scripts/check-circular-deps.mjs
 * Exit:  0 = no cycles found, 1 = cycles detected
 *
 * @doc.type   tooling
 * @doc.purpose Detect circular dependencies in TypeScript workspace packages
 * @doc.layer  infrastructure
 */

import { readFileSync, readdirSync, existsSync } from 'fs';
import { join, relative, resolve } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = resolve(__dirname, '..');

// ---------------------------------------------------------------------------
// Collect all workspace package roots (matching pnpm-workspace.yaml globs)
// ---------------------------------------------------------------------------

const WORKSPACE_ROOTS = [
  'platform/typescript',
  'products/data-cloud',
  'products/aep',
  'products/yappc',
  'products/audio-video',
  'products/flashit',
  'products/tutorputor',
  'products/dcmaar',
  'products/phr',
  'products/software-org',
  'shared-services',
];

const SKIP_DIRS = new Set(['node_modules', '.git', 'dist', 'build', '.turbo', 'coverage']);

/**
 * Recursively find all package.json files under `dir`, skipping ignored dirs.
 * @param {string} dir
 * @returns {string[]}
 */
function findPackageJsonFiles(dir) {
  const results = [];
  if (!existsSync(dir)) return results;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...findPackageJsonFiles(full));
    } else if (entry.name === 'package.json') {
      results.push(full);
    }
  }
  return results;
}

// ---------------------------------------------------------------------------
// Build package name → workspace: dep names map
// ---------------------------------------------------------------------------

/** @type {Map<string, Set<string>>} packageName → set of workspace dep names */
const graph = new Map();

/** @type {Map<string, string>} packageName → package.json path (for reporting) */
const locations = new Map();

let totalScanned = 0;

for (const wsRoot of WORKSPACE_ROOTS) {
  const absRoot = join(REPO_ROOT, wsRoot);
  for (const pkgFile of findPackageJsonFiles(absRoot)) {
    let pkg;
    try {
      pkg = JSON.parse(readFileSync(pkgFile, 'utf8'));
    } catch {
      continue;
    }
    if (!pkg.name) continue;

    totalScanned++;
    locations.set(pkg.name, relative(REPO_ROOT, pkgFile).replace(/\\/g, '/'));

    const allDeps = {
      ...pkg.dependencies,
      ...pkg.devDependencies,
      ...pkg.peerDependencies,
    };

    const wsEdges = new Set();
    for (const [dep, version] of Object.entries(allDeps)) {
      if (String(version).startsWith('workspace:')) {
        wsEdges.add(dep);
      }
    }

    // Merge: a package may appear in multiple workspace roots (shouldn't happen, but safe)
    if (!graph.has(pkg.name)) {
      graph.set(pkg.name, wsEdges);
    } else {
      for (const e of wsEdges) graph.get(pkg.name).add(e);
    }
  }
}

// ---------------------------------------------------------------------------
// Prune edges that point to packages not in this workspace (external refs)
// ---------------------------------------------------------------------------

for (const [, deps] of graph) {
  for (const dep of [...deps]) {
    if (!graph.has(dep)) deps.delete(dep);
  }
}

// ---------------------------------------------------------------------------
// Cycle detection via iterative DFS (Johnson's algorithm simplified to SCC)
// For a monorepo dependency graph, a simple colour-DFS is sufficient.
// ---------------------------------------------------------------------------

/** @type {string[][]} */
const cycles = [];

/** @type {'white'|'grey'|'black'} */
const colour = new Map();
/** @type {string[]} */
const stack = [];

/**
 * Iterative DFS that detects back-edges (cycles).
 * We use an explicit stack of [node, iterator] pairs to avoid call stack overflow
 * on very large graphs.
 * @param {string} start
 */
function dfs(start) {
  // Each entry: [packageName, iteratorOverChildren]
  /** @type {Array<[string, Iterator<string>]>} */
  const dfsStack = [[start, graph.get(start)?.[Symbol.iterator]() ?? [].values()]];
  colour.set(start, 'grey');
  stack.push(start);

  while (dfsStack.length > 0) {
    const [node, iter] = dfsStack[dfsStack.length - 1];
    const next = iter.next();

    if (next.done) {
      // Backtrack
      colour.set(node, 'black');
      stack.pop();
      dfsStack.pop();
    } else {
      const child = next.value;
      const childColour = colour.get(child) ?? 'white';

      if (childColour === 'grey') {
        // Back edge — found a cycle
        const cycleStart = stack.indexOf(child);
        if (cycleStart !== -1) {
          cycles.push([...stack.slice(cycleStart), child]);
        }
      } else if (childColour === 'white') {
        colour.set(child, 'grey');
        stack.push(child);
        dfsStack.push([child, graph.get(child)?.[Symbol.iterator]() ?? [].values()]);
      }
    }
  }
}

for (const node of graph.keys()) {
  if ((colour.get(node) ?? 'white') === 'white') {
    dfs(node);
  }
}

// ---------------------------------------------------------------------------
// Deduplicate cycles (same set of nodes, different starting point)
// ---------------------------------------------------------------------------

/** @param {string[]} cycle @returns {string} */
function canonicalCycleKey(cycle) {
  // Drop the repeated last node, rotate to smallest node, rejoin
  const nodes = cycle.slice(0, -1);
  const minIdx = nodes.indexOf([...nodes].sort()[0]);
  const rotated = [...nodes.slice(minIdx), ...nodes.slice(0, minIdx)];
  return rotated.join(' → ');
}

const seen = new Set();
const dedupedCycles = cycles.filter((c) => {
  const key = canonicalCycleKey(c);
  if (seen.has(key)) return false;
  seen.add(key);
  return true;
});

// ---------------------------------------------------------------------------
// Report
// ---------------------------------------------------------------------------

console.log(`\nCircular Dependency Check`);
console.log(`Scanned ${totalScanned} workspace packages.\n`);

if (dedupedCycles.length === 0) {
  console.log('✅ No circular workspace dependencies detected.');
  process.exit(0);
}

console.error(`❌ Found ${dedupedCycles.length} circular dependency cycle(s):\n`);
for (const [i, cycle] of dedupedCycles.entries()) {
  console.error(`  Cycle ${i + 1}:`);
  for (let j = 0; j < cycle.length - 1; j++) {
    const pkg = cycle[j];
    const loc = locations.get(pkg) ?? '(unknown)';
    const arrow = j < cycle.length - 2 ? ' →' : ' → (back to start)';
    console.error(`    ${pkg}${arrow}`);
    console.error(`      (${loc})`);
  }
  console.error('');
}

console.error(
  `Circular dependencies cause build-order failures and initialization deadlocks.\n` +
  `Break each cycle by extracting the shared code into a lower-level platform package.\n`
);
process.exit(1);
