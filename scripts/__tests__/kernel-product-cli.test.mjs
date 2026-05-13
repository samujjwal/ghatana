#!/usr/bin/env node

/**
 * Integration tests for kernel-product-new.mjs
 * Verifies that the CLI works with built ProductLifecyclePlanner and ProductLifecycleExecutor
 */

import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '../..');
const cliPath = resolve(repoRoot, 'scripts/kernel-product-new.mjs');

/**
 * Run a CLI command and return the result
 */
function runCli(args) {
  const result = spawnSync('node', [cliPath, ...args], {
    cwd: repoRoot,
    encoding: 'utf8',
  });

  return {
    exitCode: result.status,
    stdout: result.stdout,
    stderr: result.stderr,
    error: result.error,
  };
}

/**
 * Parse JSON from CLI output
 */
function parseJsonOutput(output) {
  try {
    // Try to parse the entire output as JSON
    return JSON.parse(output.trim());
  } catch (e) {
    // If that fails, look for JSON block
    const lines = output.split('\n');
    let jsonStart = -1;
    let braceCount = 0;
    let jsonLine = '';

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (line.startsWith('{') || jsonStart >= 0) {
        if (jsonStart === -1) jsonStart = i;
        jsonLine += line;
        braceCount += (line.match(/{/g) || []).length - (line.match(/}/g) || []).length;
        if (braceCount === 0 && jsonStart >= 0) {
          return JSON.parse(jsonLine);
        }
      }
    }

    throw new Error(`No valid JSON found in output: ${output}`);
  }
}

/**
 * Test suite
 */
function runTests() {
  let passed = 0;
  let failed = 0;

  const tests = [
    {
      name: 'Plan command with product plan syntax',
      run: () => {
        const result = runCli(['product', 'plan', 'digital-marketing', 'build']);
        assert.equal(result.exitCode, 0, 'Exit code should be 0');
        const data = parseJsonOutput(result.stdout);
        assert.equal(data.productId, 'digital-marketing');
        assert.equal(data.phase, 'build');
        assert(Array.isArray(data.steps), 'Should have steps');
        assert(data.steps.length > 0, 'Should have at least one step');
      },
    },
    {
      name: 'Plan command with alternate syntax',
      run: () => {
        const result = runCli(['plan', 'digital-marketing', 'build']);
        assert.equal(result.exitCode, 0, 'Exit code should be 0');
        const data = parseJsonOutput(result.stdout);
        assert.equal(data.productId, 'digital-marketing');
        assert.equal(data.phase, 'build');
      },
    },
    {
      name: 'Execute command with dry-run (new syntax)',
      run: () => {
        const result = runCli(['product', 'build', 'digital-marketing', '--dry-run']);
        assert.equal(result.exitCode, 0, 'Exit code should be 0');
        assert(result.stdout.includes('skipped'), 'Should indicate dry-run');
      },
    },
    {
      name: 'Execute command with dry-run and JSON output',
      run: () => {
        const result = runCli(['product', 'build', 'digital-marketing', '--dry-run', '--json']);
        assert.equal(result.exitCode, 0, 'Exit code should be 0');
        const data = parseJsonOutput(result.stdout);
        assert(data.plan, 'Should have plan');
        assert(data.result, 'Should have result');
        assert.equal(data.result.status, 'skipped', 'Status should be skipped for dry-run');
        assert(Array.isArray(data.result.steps), 'Result should have steps');
      },
    },
    {
      name: 'Execute command with alternate syntax',
      run: () => {
        const result = runCli(['build', 'digital-marketing', '--dry-run']);
        assert.equal(result.exitCode, 0, 'Exit code should be 0');
        assert(result.stdout.includes('skipped'), 'Should indicate dry-run');
      },
    },
    {
      name: 'Help command',
      run: () => {
        const result = runCli(['--help']);
        assert.equal(result.exitCode, 0, 'Help should exit with 0');
        assert(result.stdout.includes('Usage'), 'Should show usage');
      },
    },
    {
      name: 'Invalid product should fail',
      run: () => {
        const result = runCli(['product', 'plan', 'nonexistent-product', 'build']);
        assert.notEqual(result.exitCode, 0, 'Should exit with non-zero for invalid product');
        assert(result.stderr.includes('Error'), 'Should show error message');
      },
    },
    {
      name: 'Invalid phase should fail',
      run: () => {
        const result = runCli(['product', 'plan', 'digital-marketing', 'invalid-phase']);
        assert.notEqual(result.exitCode, 0, 'Should exit with non-zero for invalid phase');
      },
    },
  ];

  for (const test of tests) {
    try {
      test.run();
      console.log(`✓ ${test.name}`);
      passed += 1;
    } catch (error) {
      console.log(`✗ ${test.name}`);
      console.log(`  Error: ${error.message}`);
      failed += 1;
    }
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed > 0 ? 1 : 0);
}

// Run tests
runTests();
