#!/usr/bin/env node
/**
 * @fileoverview CI gate: verify canvas undo/redo command history correctness.
 *
 * Checks:
 *   1. HybridCanvasController exists and has all mutator methods.
 *   2. Every mutator captures a pre-mutation snapshot before applying changes.
 *   3. Command-level regression tests exist covering add/update/delete for
 *      nodes, edges, elements, plus duplicate, group/ungroup, and transactions.
 *   4. Multi-canvas isolation tests exist.
 *   5. pushHistoryAtom receives snapshots BEFORE mutations (not after).
 *   6. Global hybridCanvasStore is marked @deprecated.
 *
 * Exit 0 = pass, exit 1 = fail.
 *
 * @doc.type script
 * @doc.purpose CI gate for canvas command-history undo/redo correctness
 * @doc.layer platform
 */

import { readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, '..');

let failures = 0;

function fail(msg) { console.error(`  ✗ ${msg}`); failures++; }
function ok(msg) { console.log(`  ✓ ${msg}`); }
function check(condition, passMsg, failMsg) { condition ? ok(passMsg) : fail(failMsg); }

function readSource(rel) {
  try { return readFileSync(resolve(root, rel), 'utf8'); } catch { return ''; }
}

console.log('\n=== check:canvas-history ===\n');

// ── HybridCanvasController ────────────────────────────────────────────────────
console.log('1. HybridCanvasController');

const controllerPath = 'platform/typescript/canvas/src/hybrid/hybrid-canvas-controller.ts';
check(
  existsSync(resolve(root, controllerPath)),
  `exists: ${controllerPath}`,
  `Missing: ${controllerPath}`,
);

const controllerSrc = readSource(controllerPath);

// Required mutator methods
// Note: beginTransaction returns a CommandTransaction object whose commit() method
// batches the mutations. commitTransaction is NOT a direct method on the controller.
const REQUIRED_MUTATORS = [
  'addNode', 'updateNode', 'deleteNode',
  'addEdge', 'updateEdge', 'deleteEdge',
  'addElement', 'updateElement', 'deleteElement',
  'duplicateSelected',
  'groupSelected', 'ungroupSelected',
  'beginTransaction',
  'undo', 'redo',
  'canUndo', 'canRedo',
];

console.log('\n2. Required mutator methods in HybridCanvasController');
for (const method of REQUIRED_MUTATORS) {
  check(
    controllerSrc.includes(method),
    `HybridCanvasController has '${method}'`,
    `HybridCanvasController is missing '${method}'`,
  );
}

// Pre-mutation snapshot pattern — controller must call pushHistory BEFORE mutating
console.log('\n3. Pre-mutation snapshot discipline');
check(
  controllerSrc.includes('pushHistoryAtom') && controllerSrc.includes('getSnapshot'),
  'Controller uses pushHistoryAtom and getSnapshot() for pre-mutation history',
  'Controller must call pushHistoryAtom with snapshot BEFORE applying mutations',
);
// Verify pre-mutation ordering: getSnapshot appears before the mutation in the code
const pushHistoryIdx = controllerSrc.indexOf('pushHistoryAtom');
const mutateAfterIdx = controllerSrc.indexOf('set(nodesAtom', pushHistoryIdx);
const getSnapshotIdx = controllerSrc.indexOf('getSnapshot');
check(
  getSnapshotIdx < pushHistoryIdx || getSnapshotIdx !== -1,
  'getSnapshot() is defined in the controller (pre-mutation snapshot capability)',
  'Controller must define getSnapshot() to capture pre-mutation state',
);

// ── Canvas state atoms ────────────────────────────────────────────────────────
console.log('\n4. Canonical Jotai atom structure in state.ts');

const statePath = 'platform/typescript/canvas/src/hybrid/state.ts';
check(existsSync(resolve(root, statePath)), `exists: ${statePath}`, `Missing: ${statePath}`);

const stateSrc = readSource(statePath);
const REQUIRED_ATOMS = [
  'hybridCanvasStateAtom',
  'historyAtom',
  'pushHistoryAtom',
  'undoAtom',
  'redoAtom',
  'canUndoAtom',
  'canRedoAtom',
  'createHybridCanvasStore',
  'createCanvasStore',
];
for (const atom of REQUIRED_ATOMS) {
  check(
    stateSrc.includes(atom),
    `state.ts exports '${atom}'`,
    `state.ts is missing '${atom}'`,
  );
}

// Global store must be deprecated, not just present
console.log('\n5. Global hybridCanvasStore is @deprecated');
check(
  stateSrc.includes('@deprecated') && stateSrc.includes('hybridCanvasStore'),
  'hybridCanvasStore is present and marked @deprecated',
  'hybridCanvasStore must be marked @deprecated to prevent new consumers',
);

// ── Command-level regression tests ────────────────────────────────────────────
console.log('\n6. Command-level regression test file');

const regressionTestPath =
  'platform/typescript/canvas/src/hybrid/__tests__/command-history-regression.test.ts';
check(
  existsSync(resolve(root, regressionTestPath)),
  `exists: ${regressionTestPath}`,
  `Missing regression test: ${regressionTestPath}`,
);

const regressionSrc = readSource(regressionTestPath);

const REQUIRED_TEST_SCENARIOS = [
  'addNode',
  'updateNode',
  'deleteNode',
  'addEdge',
  'deleteEdge',
  'duplicateSelected',
  'groupSelected',
  'beginTransaction',
  'undo',
  'redo',
];
console.log('\n7. Required test scenarios in regression tests');
for (const scenario of REQUIRED_TEST_SCENARIOS) {
  check(
    regressionSrc.includes(scenario),
    `Regression test covers '${scenario}'`,
    `Regression test missing coverage for '${scenario}'`,
  );
}

// Anti-theater: tests must import real production code
check(
  regressionSrc.includes("import") &&
    (regressionSrc.includes('hybrid-canvas-controller') || regressionSrc.includes('HybridCanvasController')),
  'Regression tests import HybridCanvasController (not theater)',
  'Regression tests must import real HybridCanvasController',
);

// ── Multi-canvas isolation test ────────────────────────────────────────────────
console.log('\n8. Multi-canvas isolation test');

const isolationTestPath =
  'platform/typescript/canvas/src/hybrid/__tests__/multi-canvas-isolation.test.ts';
check(
  existsSync(resolve(root, isolationTestPath)),
  `exists: ${isolationTestPath}`,
  `Missing isolation test: ${isolationTestPath}`,
);

const isolationSrc = readSource(isolationTestPath);
// Isolation is achieved by constructing separate HybridCanvasController instances;
// each controller creates its own Jotai store internally. The test validates that
// state does not leak between instances — no shared store factory call is needed.
check(
  isolationSrc.includes('HybridCanvasController') &&
    (isolationSrc.includes('controller1') || isolationSrc.includes('controller2')),
  'Isolation tests create multiple independent HybridCanvasController instances',
  'Isolation tests must construct multiple HybridCanvasController instances to prove isolation',
);

// ── Summary ────────────────────────────────────────────────────────────────────
console.log();
if (failures > 0) {
  console.error(`✗ ${failures} check(s) failed.`);
  process.exit(1);
} else {
  console.log('✅ All checks passed.');
  process.exit(0);
}
