#!/usr/bin/env node
/**
 * @fileoverview Validates aggregate readiness gates reference defined scripts and existing targets.
 *
 * @doc.type script
 * @doc.purpose Prevent aggregate gate regressions from undefined script references
 * @doc.layer governance
 * @doc.pattern ValidationScript
 */

import { existsSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const DEFAULT_AGGREGATE_SCRIPTS = [
  'check:phase8',
  'check:release-gate',
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
  const matches = command.match(/\bcheck:[a-z0-9:_-]+\b/g);
  if (!matches) {
    return [];
  }
  return [...new Set(matches)];
}

export function checkAggregateGateIntegrity({
  scripts,
  aggregateScripts = DEFAULT_AGGREGATE_SCRIPTS,
  rootDir = process.cwd(),
}) {
  const violations = [];
  const normalizedRootDir = path.resolve(rootDir);
  const visiting = new Set();
  const visited = new Set();

  const visit = (scriptName, stack) => {
    const command = scripts[scriptName];
    if (!command) {
      violations.push(`${stack[0] ?? scriptName}: references undefined script ${scriptName}`);
      return;
    }
    if (visiting.has(scriptName)) {
      const cycleStart = stack.indexOf(scriptName);
      const cyclePath = stack.slice(cycleStart).join(' -> ');
      violations.push(`${stack[0] ?? scriptName}: recursive aggregate script cycle detected: ${cyclePath}`);
      return;
    }
    if (visited.has(scriptName)) {
      return;
    }

    visiting.add(scriptName);
    validateCommandTargets({
      scriptName,
      command,
      rootDir: normalizedRootDir,
      violations,
    });

    const referencedChecks = findReferencedChecks(command);
    for (const referencedScriptName of referencedChecks) {
      if (!scripts[referencedScriptName]) {
        violations.push(`${scriptName}: references undefined script ${referencedScriptName}`);
        continue;
      }
      visit(referencedScriptName, [...stack, referencedScriptName]);
    }

    visiting.delete(scriptName);
    visited.add(scriptName);
  };

  for (const aggregateScriptName of aggregateScripts) {
    if (!scripts[aggregateScriptName]) {
      violations.push(`${aggregateScriptName}: aggregate script is not defined`);
      continue;
    }
    visit(aggregateScriptName, [aggregateScriptName]);
  }

  return [...new Set(violations)];
}

function validateCommandTargets({ scriptName, command, rootDir, violations }) {
  const segments = splitCommandSegments(command);
  for (const segment of segments) {
    const tokens = tokenizeCommand(segment);
    validateNodeScriptTargets({ scriptName, tokens, rootDir, violations });
    validatePnpmDirTargets({ scriptName, tokens, rootDir, violations });
    validateTestRunnerTargets({ scriptName, tokens, rootDir, violations });
    validateGradleTargets({ scriptName, tokens, rootDir, violations });
  }
}

function splitCommandSegments(command) {
  return command
    .split(/\s+&&\s+|\s+\|\|\s+|;/)
    .map((segment) => segment.trim())
    .filter((segment) => segment.length > 0);
}

function tokenizeCommand(command) {
  const tokens = [];
  let current = '';
  let quote = null;
  for (let index = 0; index < command.length; index += 1) {
    const character = command[index];
    if (quote !== null) {
      if (character === quote) {
        quote = null;
      } else {
        current += character;
      }
      continue;
    }
    if (character === '"' || character === "'") {
      quote = character;
      continue;
    }
    if (/\s/.test(character)) {
      if (current.length > 0) {
        tokens.push(current);
        current = '';
      }
      continue;
    }
    current += character;
  }
  if (current.length > 0) {
    tokens.push(current);
  }
  return tokens;
}

function validateNodeScriptTargets({ scriptName, tokens, rootDir, violations }) {
  for (const token of tokens) {
    const cleaned = cleanToken(token);
    if (/^\.?\/?scripts\/.+\.(?:mjs|js)$/u.test(cleaned)) {
      validateExistingFile({
        scriptName,
        rootDir,
        relativePath: cleaned,
        kind: 'node script',
        violations,
      });
    }
  }
}

function validatePnpmDirTargets({ scriptName, tokens, rootDir, violations }) {
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    let directory;
    if (token === '--dir') {
      directory = tokens[index + 1];
    } else if (token.startsWith('--dir=')) {
      directory = token.slice('--dir='.length);
    }
    if (directory !== undefined) {
      validateExistingDirectory({
        scriptName,
        rootDir,
        relativePath: cleanToken(directory),
        kind: 'pnpm --dir target',
        violations,
      });
    }
  }
}

function validateTestRunnerTargets({ scriptName, tokens, rootDir, violations }) {
  const commandCwd = resolveCommandCwd(tokens, rootDir);
  for (const runner of ['vitest', 'playwright']) {
    const runnerIndex = tokens.findIndex((token) => token === runner);
    if (runnerIndex === -1) {
      continue;
    }
    const runIndex = tokens.indexOf('run', runnerIndex);
    const testIndex = tokens.indexOf('test', runnerIndex);
    const targetStart = runner === 'vitest'
      ? (runIndex === -1 ? runnerIndex + 1 : runIndex + 1)
      : (testIndex === -1 ? runnerIndex + 1 : testIndex + 1);
    const testRoot = runner === 'vitest' ? resolveVitestRoot(tokens, commandCwd) : commandCwd;
    for (let index = targetStart; index < tokens.length; index += 1) {
      const token = cleanToken(tokens[index]);
      if (token.startsWith('-')) {
        if (takesOptionValue(token) && tokens[index + 1] !== undefined) {
          index += 1;
        }
        continue;
      }
      if (!looksLikePathTarget(token)) {
        continue;
      }
      validateExistingPath({
        scriptName,
        rootDir: testRoot,
        relativePath: token,
        kind: `${runner} target`,
        violations,
      });
    }
  }
}

function validateGradleTargets({ scriptName, tokens, rootDir, violations }) {
  for (const token of tokens) {
    if (!token.startsWith(':')) {
      continue;
    }
    const directory = gradleTaskDirectory(token, rootDir);
    if (directory === null) {
      violations.push(`${scriptName}: gradle target ${token} does not map to an existing project directory`);
    }
  }
}

function resolveCommandCwd(tokens, rootDir) {
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    if (token === '--dir' && tokens[index + 1] !== undefined) {
      return path.resolve(rootDir, cleanToken(tokens[index + 1]));
    }
    if (token.startsWith('--dir=')) {
      return path.resolve(rootDir, cleanToken(token.slice('--dir='.length)));
    }
  }
  return rootDir;
}

function resolveVitestRoot(tokens, commandCwd) {
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    if (token === '--root' && tokens[index + 1] !== undefined) {
      return path.resolve(commandCwd, cleanToken(tokens[index + 1]));
    }
    if (token.startsWith('--root=')) {
      return path.resolve(commandCwd, cleanToken(token.slice('--root='.length)));
    }
  }
  return commandCwd;
}

function gradleTaskDirectory(task, rootDir) {
  const segments = task.split(':').filter(Boolean);
  for (let length = segments.length - 1; length >= 1; length -= 1) {
    const candidate = path.resolve(rootDir, ...segments.slice(0, length));
    if (existsSync(candidate) && statSync(candidate).isDirectory()) {
      return candidate;
    }
  }
  return null;
}

function takesOptionValue(token) {
  return [
    '--root',
    '--config',
    '--project',
    '--grep',
    '--reporter',
    '--output',
  ].includes(token);
}

function looksLikePathTarget(token) {
  return (
    token.includes('/') ||
    token.includes('\\') ||
    /\.(?:mjs|js|cjs|ts|tsx|jsx|json|spec|test)$/u.test(token)
  ) && !token.includes('*');
}

function cleanToken(token) {
  return token.replace(/^['"]|['"],?$/gu, '').replace(/,$/u, '');
}

function validateExistingFile({ scriptName, rootDir, relativePath, kind, violations }) {
  const target = path.resolve(rootDir, relativePath);
  if (!existsSync(target) || !statSync(target).isFile()) {
    violations.push(`${scriptName}: ${kind} does not exist: ${relativePath}`);
  }
}

function validateExistingDirectory({ scriptName, rootDir, relativePath, kind, violations }) {
  const target = path.resolve(rootDir, relativePath);
  if (!existsSync(target) || !statSync(target).isDirectory()) {
    violations.push(`${scriptName}: ${kind} does not exist: ${relativePath}`);
  }
}

function validateExistingPath({ scriptName, rootDir, relativePath, kind, violations }) {
  const target = path.resolve(rootDir, relativePath);
  if (!existsSync(target)) {
    violations.push(`${scriptName}: ${kind} does not exist: ${relativePath}`);
  }
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
