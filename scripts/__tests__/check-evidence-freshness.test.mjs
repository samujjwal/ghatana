#!/usr/bin/env node

/**
 * Tests for Phase 0 evidence freshness check script
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, utimesSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { getSourceFileForEvidence } from '../check-evidence-freshness.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/check-evidence-freshness.mjs');

function createTempRepo() {
  return mkdtempSync(path.join(os.tmpdir(), 'ghatana-evidence-freshness-'));
}

function createEvidenceFile(root, name, content) {
  const evidenceDir = path.join(root, '.kernel', 'evidence');
  mkdirSync(evidenceDir, { recursive: true });
  const filePath = path.join(evidenceDir, name);
  writeFileSync(filePath, JSON.stringify(content, null, 2));
  return filePath;
}

function runFreshnessCheck(root, thresholdHours) {
  return spawnSync('node', [scriptPath, '--root', root, '--threshold', String(thresholdHours)], {
    cwd: root,
    encoding: 'utf-8',
  });
}

test('should pass when evidence is fresh', () => {
  const tempRoot = createTempRepo();
  try {
    createEvidenceFile(tempRoot, 'test-evidence.json', {
      status: 'passed',
      generatedAt: new Date().toISOString(),
    });

    const result = runFreshnessCheck(tempRoot, 24);
    assert.equal(result.status, 0);
    assert.match(result.stdout, /All evidence is fresh/);
  } finally {
    rmSync(tempRoot, { recursive: true, force: true });
  }
});

test('should detect stale evidence based on age threshold', () => {
  const tempRoot = createTempRepo();
  try {
    const evidencePath = createEvidenceFile(tempRoot, 'test-evidence.json', {
      status: 'passed',
      generatedAt: new Date().toISOString(),
    });
    const staleTime = new Date(Date.now() - 25 * 60 * 60 * 1000);
    utimesSync(evidencePath, staleTime, staleTime);

    const result = runFreshnessCheck(tempRoot, 24);
    assert.equal(result.status, 1);
    assert.match(result.stdout, /Stale evidence found/);
  } finally {
    rmSync(tempRoot, { recursive: true, force: true });
  }
});

test('should detect source changes after evidence generation', () => {
  const tempRoot = createTempRepo();
  try {
    const sourcePath = path.join(tempRoot, 'scripts', 'check-kernel-implementation-plan-coverage.mjs');
    mkdirSync(path.dirname(sourcePath), { recursive: true });
    writeFileSync(sourcePath, 'console.log("source v1");\n');

    const evidencePath = createEvidenceFile(tempRoot, 'kernel-implementation-plan-progress.json', {
      status: 'passed',
      generatedAt: new Date().toISOString(),
    });

    const current = readFileSync(sourcePath, 'utf8');
    writeFileSync(sourcePath, `${current}console.log("source v2");\n`);
    const now = new Date();
    utimesSync(sourcePath, now, now);

    const result = runFreshnessCheck(tempRoot, 24);
    assert.equal(result.status, 1);
    assert.match(result.stdout, /source file changed after evidence/);
    assert.ok(result.stdout.includes(path.join('.kernel', 'evidence', 'kernel-implementation-plan-progress.json')));
    assert.ok(existsSync(evidencePath));
  } finally {
    rmSync(tempRoot, { recursive: true, force: true });
  }
});

test('should map release summary and per-product scorecards to their generators', () => {
  assert.match(
    getSourceFileForEvidence(path.join(repoRoot, '.kernel', 'evidence', 'release-summary.json')) ?? '',
    /generate-release-maturity-summary\.mjs$/,
  );
  assert.match(
    getSourceFileForEvidence(path.join(repoRoot, '.kernel', 'evidence', 'product-release-readiness.phr.json')) ?? '',
    /check-product-release-readiness\.mjs$/,
  );
});

test('should handle missing evidence directory gracefully', () => {
  const tempRoot = createTempRepo();
  try {
    const result = runFreshnessCheck(tempRoot, 24);
    assert.equal(result.status, 0);
    assert.match(result.stdout, /Total evidence files: 0/);
    assert.match(result.stdout, /All evidence is fresh/);
  } finally {
    rmSync(tempRoot, { recursive: true, force: true });
  }
});
