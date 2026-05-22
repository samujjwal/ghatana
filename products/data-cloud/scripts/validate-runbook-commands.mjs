#!/usr/bin/env node

import { accessSync, constants, existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const drDir = path.join(__dirname, '..', 'planes', 'action', 'scripts', 'disaster-recovery');

const scripts = ['backup-aep.sh', 'restore-aep.sh', 'dr-drill.sh'];
const requiredFlags = {
  'backup-aep.sh': ['--tenant-id', '--output-dir', '--retention', '--full', '--no-compress'],
  'restore-aep.sh': ['--tenant-id', '--backup-file', '--validate-only', '--dry-run'],
  'dr-drill.sh': ['--tenant-id', '--scenario', '--rto-target', '--no-cleanup'],
};

let pass = 0;
let fail = 0;
const errors = [];

function passCheck(message) {
  console.log(`  OK ${message}`);
  pass += 1;
}

function failCheck(message) {
  console.error(`  FAIL ${message}`);
  errors.push(message);
  fail += 1;
}

function bashAvailable() {
  const probe = process.platform === 'win32'
    ? spawnSync('where', ['bash'], { stdio: 'ignore', shell: true })
    : spawnSync('bash', ['-lc', 'command -v bash'], { stdio: 'ignore' });
  return probe.status === 0;
}

console.log('=== Recovery Runbook Command Smoke Tests (DC-P1-452) ===');

console.log('\n1. Script existence checks');
for (const script of scripts) {
  const scriptPath = path.join(drDir, script);
  if (existsSync(scriptPath)) {
    passCheck(`${script} exists at expected path`);
  } else {
    failCheck(`${script} missing at ${scriptPath}`);
  }
}

console.log('\n2. Execute permission checks');
for (const script of scripts) {
  const scriptPath = path.join(drDir, script);
  if (!existsSync(scriptPath)) {
    failCheck(`${script} execute check skipped (file missing)`);
    continue;
  }
  try {
    accessSync(scriptPath, constants.X_OK);
    passCheck(`${script} is executable`);
  } catch {
    if (process.platform === 'win32') {
      passCheck(`${script} execute bit check skipped on Windows`);
    } else {
      failCheck(`${script} is not executable`);
    }
  }
}

console.log('\n3. Bash syntax validation');
const hasBash = process.platform !== 'win32' && bashAvailable();
for (const script of scripts) {
  const scriptPath = path.join(drDir, script);
  if (!existsSync(scriptPath)) {
    failCheck(`${script} syntax check skipped (file missing)`);
    continue;
  }
  if (!hasBash) {
    passCheck(`${script} syntax check skipped (bash unavailable on this host)`);
    continue;
  }
  const lint = spawnSync('bash', ['-n', scriptPath], { encoding: 'utf8' });
  if (lint.status === 0) {
    passCheck(`${script} passes bash -n`);
  } else {
    failCheck(`${script} has syntax errors: ${(lint.stderr || lint.stdout || '').trim()}`);
  }
}

console.log('\n4. Required option flag presence (README alignment)');
for (const script of scripts) {
  const scriptPath = path.join(drDir, script);
  if (!existsSync(scriptPath)) {
    failCheck(`${script} flag check skipped (file missing)`);
    continue;
  }
  const content = readFileSync(scriptPath, 'utf8');
  for (const flag of requiredFlags[script]) {
    if (content.includes(flag)) {
      passCheck(`${script}: flag '${flag}' present`);
    } else {
      failCheck(`${script}: missing documented flag '${flag}'`);
    }
  }
}

console.log('\n5. Runbook documentation checks');
const readme = path.join(drDir, 'README.md');
if (existsSync(readme)) {
  passCheck('README.md exists');
  const lines = readFileSync(readme, 'utf8').split(/\r?\n/).length;
  if (lines > 50) {
    passCheck(`README.md has substantial content (${lines} lines)`);
  } else {
    failCheck(`README.md appears too short (${lines} lines)`);
  }
} else {
  failCheck(`README.md missing at ${readme}`);
}

console.log(`\n=== Results: ${pass} passed, ${fail} failed ===`);
if (fail > 0) {
  console.error('\nFailures:');
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}

console.log('\nAll recovery runbook smoke tests passed.');
