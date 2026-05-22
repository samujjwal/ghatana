#!/usr/bin/env node
/**
 * @fileoverview Validates aggregate readiness gates only reference defined scripts.
 *
 * @doc.type script
 * @doc.purpose Prevent aggregate gate regressions from undefined script references
 * @doc.layer governance
 * @doc.pattern ValidationScript
 */

import { readFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const DEFAULT_AGGREGATE_SCRIPTS = [
  'check:phase8',
  'check:release-gate',
  'check:world-class-platform-readiness',
];

function parseArgs(argv) {
  const options = {
    packageJsonPath: path.resolve('package.json'),
    aggregateScripts: [...DEFAULT_AGGREGATE_SCRIPTS],
  };

  for (let index = 2; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg.startsWith('--package-json=')) {
      options.packageJsonPath = path.resolve(arg.slice('--package-json='.length));
      continue;
    }
    if (arg === '--package-json') {
      const value = argv[index + 1];
      if (!value) {
        throw new Error('Missing value for --package-json');
      }
      options.packageJsonPath = path.resolve(value);
      index += 1;
      continue;
    }
    if (arg.startsWith('--scripts=')) {
      options.aggregateScripts = splitScriptList(arg.slice('--scripts='.length));
      continue;
    }
    if (arg === '--scripts') {
      const value = argv[index + 1];
      if (!value) {
        throw new Error('Missing value for --scripts');
      }
      options.aggregateScripts = splitScriptList(value);
      index += 1;
      continue;
    }
  }

  return options;
}

function splitScriptList(rawValue) {
  return rawValue
    .split(',')
    .map((name) => name.trim())
    .filter((name) => name.length > 0);
}

function parsePackageScripts(packageJsonPath) {
  const payload = JSON.parse(readFileSync(packageJsonPath, 'utf-8'));
  const scripts = payload.scripts;
  if (!scripts || typeof scripts !== 'object') {
    throw new Error(`No scripts block found in ${packageJsonPath}`);
  }
  return scripts;
}

function findReferencedChecks(command) {
  const matches = command.match(/\bcheck:[a-z0-9-]+\b/g);
  if (!matches) {
    return [];
  }
  return [...new Set(matches)];
}

export function checkAggregateGateIntegrity({
  scripts,
  aggregateScripts = DEFAULT_AGGREGATE_SCRIPTS,
}) {
  const violations = [];

  for (const aggregateScriptName of aggregateScripts) {
    const aggregateCommand = scripts[aggregateScriptName];
    if (!aggregateCommand) {
      violations.push(`${aggregateScriptName}: aggregate script is not defined`);
      continue;
    }

    const referencedChecks = findReferencedChecks(aggregateCommand);
    for (const referencedScriptName of referencedChecks) {
      if (!scripts[referencedScriptName]) {
        violations.push(
          `${aggregateScriptName}: references undefined script ${referencedScriptName}`,
        );
      }
    }
  }

  return violations;
}

function main() {
  const options = parseArgs(process.argv);
  const scripts = parsePackageScripts(options.packageJsonPath);
  const violations = checkAggregateGateIntegrity({
    scripts,
    aggregateScripts: options.aggregateScripts,
  });

  if (violations.length > 0) {
    console.error('Aggregate gate integrity check failed:');
    for (const violation of violations) {
      console.error(`  - ${violation}`);
    }
    process.exit(1);
  }

  console.log('Aggregate gate integrity check passed.');
}

const invokedAsScript = process.argv[1] !== undefined
  && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedAsScript) {
  main();
}
