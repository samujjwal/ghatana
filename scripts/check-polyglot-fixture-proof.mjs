#!/usr/bin/env node

import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const fixtureSpecs = [
  {
    productId: 'rust-fixture',
    manifestPath: 'products/rust-fixture/kernel-product.yaml',
    sourceFiles: [
      'products/rust-fixture/Cargo.toml',
      'products/rust-fixture/src/lib.rs',
      'products/rust-fixture/src/main.rs',
      'products/rust-fixture/tests/integration_test.rs',
    ],
    adapterId: 'cargo-rust',
    expectedLanguage: 'rust',
    expectedBuildSystem: 'cargo',
    expectedCommand: {
      command: 'cargo',
      args: ['test', '--manifest-path', 'products/rust-fixture/Cargo.toml'],
    },
  },
  {
    productId: 'python-fixture',
    manifestPath: 'products/python-fixture/kernel-product.yaml',
    sourceFiles: [
      'products/python-fixture/pyproject.toml',
      'products/python-fixture/src/main.py',
      'products/python-fixture/src/message.py',
      'products/python-fixture/tests/test_integration.py',
    ],
    adapterId: 'python-pyproject',
    expectedLanguage: 'python',
    expectedBuildSystem: 'pyproject',
    expectedCommand: {
      command: 'python-fixture-venv',
      args: [],
    },
  },
  {
    productId: 'typescript-fixture',
    manifestPath: 'products/typescript-fixture/kernel-product.yaml',
    sourceFiles: [
      'products/typescript-fixture/package.json',
      'products/typescript-fixture/src/index.ts',
      'products/typescript-fixture/src/main.ts',
      'products/typescript-fixture/tests/integration.test.ts',
    ],
    adapterId: 'pnpm-node-api',
    expectedLanguage: 'typescript',
    expectedBuildSystem: 'pnpm',
    expectedCommand: {
      command: 'pnpm',
      args: ['--filter', 'typescript-fixture', 'test', '--', '--run'],
    },
  },
];

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function readYaml(relativePath) {
  return YAML.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function fail(message) {
  throw new Error(message);
}

function assertFile(relativePath, label) {
  if (!existsSync(path.join(repoRoot, relativePath))) {
    fail(`${label} is missing: ${relativePath}`);
  }
}

function assertFixtureShape() {
  const registry = readJson('config/canonical-product-registry.json').registry;
  const profiles = readJson('config/product-lifecycle-profiles.json').profiles;
  const adapters = readJson('config/toolchain-adapter-registry.json').adapters;
  const workspace = readFileSync(path.join(repoRoot, 'pnpm-workspace.yaml'), 'utf8');

  const polyglotProfile = profiles['standard-polyglot-product'];
  if (!polyglotProfile || polyglotProfile.status !== 'stable') {
    fail('standard-polyglot-product lifecycle profile must exist and be stable');
  }

  for (const spec of fixtureSpecs) {
    const product = registry[spec.productId];
    if (!product) {
      fail(`${spec.productId} must be registered in canonical-product-registry.json`);
    }
    if (product.lifecycleProfile !== 'standard-polyglot-product') {
      fail(`${spec.productId} must use standard-polyglot-product lifecycle profile`);
    }
    if (product.lifecycleExecutionAllowed === true) {
      fail(`${spec.productId} must not bypass opening pilot lifecycle policy`);
    }
    if (spec.productId === 'typescript-fixture' && !workspace.includes('"products/typescript-fixture"')) {
      fail('typescript-fixture must be included in pnpm-workspace.yaml so its tests execute with workspace tooling');
    }

    const manifest = readYaml(spec.manifestPath);
    if (manifest.productExtensions?.lifecycleProfile !== 'standard-polyglot-product') {
      fail(`${spec.manifestPath} must declare standard-polyglot-product`);
    }
    const runtimeSurfaces = new Set(manifest.surfaces?.runtime ?? []);
    for (const surfaceType of ['sdk', 'backend-api']) {
      if (!runtimeSurfaces.has(surfaceType)) {
        fail(`${spec.productId} must define ${surfaceType} surface`);
      }
    }
    const adapterProof = manifest.capabilities?.find((capability) => capability.id === `${spec.productId}.adapter-proof`);
    if (!adapterProof) {
      fail(`${spec.productId} must declare adapter proof capability`);
    }
    if (
      adapterProof.metadata?.adapter !== spec.adapterId ||
      adapterProof.metadata?.buildSystem !== spec.expectedBuildSystem
    ) {
      fail(`${spec.productId} adapter proof must declare ${spec.adapterId}/${spec.expectedBuildSystem}`);
    }
    if (!manifest.productExtensions?.adapters?.includes(spec.adapterId)) {
      fail(`${spec.productId} must expose ${spec.adapterId} in productExtensions.adapters`);
    }

    for (const file of spec.sourceFiles) {
      assertFile(file, `${spec.productId} fixture proof`);
    }

    const adapter = adapters[spec.adapterId];
    if (!adapter || adapter.status !== 'implemented' || adapter.readiness !== 'execution-ready') {
      fail(`${spec.adapterId} must be implemented and execution-ready`);
    }
    if (adapter.outputValidation?.validateAfterExecute !== true) {
      fail(`${spec.adapterId} must validate outputs after execution`);
    }
  }
}

function runCommand(label, command, args, options = {}) {
  if (process.platform === 'win32' && command === 'pnpm') {
    return runCommand(label, 'cmd.exe', ['/d', '/s', '/c', ['pnpm', ...args].join(' ')], options);
  }
  const executable = process.platform === 'win32' && !path.isAbsolute(command)
    ? {
        cargo: 'cargo.exe',
        python: 'python.exe',
      }[command] ?? command
    : command;
  const result = spawnSync(executable, args, {
    cwd: repoRoot,
    encoding: 'utf8',
    shell: false,
    stdio: 'pipe',
    ...options,
  });
  if (result.status !== 0) {
    const spawnError = result.error ? `\n${result.error.message}` : '';
    const stderr = result.stderr?.trim();
    const stdout = result.stdout?.trim();
    fail(`${label} failed with exit ${result.status ?? 'unknown'}${spawnError}${stderr ? `\n${stderr}` : ''}${stdout ? `\n${stdout}` : ''}`);
  }
}

function commandExists(command) {
  const result = spawnSync(command, ['--version'], {
    cwd: repoRoot,
    encoding: 'utf8',
    shell: false,
    stdio: 'pipe',
  });
  return result.status === 0;
}

function resolvePythonCommand() {
  const candidates = process.platform === 'win32'
    ? ['python.exe', 'py.exe']
    : ['python3', 'python'];
  const resolved = candidates.find(commandExists);
  if (!resolved) {
    fail(`python fixture requires one of: ${candidates.join(', ')}`);
  }
  return resolved;
}

function runPythonFixtureTests() {
  const venv = mkdtempSync(path.join(tmpdir(), 'ghatana-python-fixture-'));
  const pythonCommand = resolvePythonCommand();
  const pythonExe = process.platform === 'win32'
    ? path.join(venv, 'Scripts', 'python.exe')
    : path.join(venv, 'bin', 'python');

  try {
    runCommand('python fixture venv create', pythonCommand, ['-m', 'venv', venv]);
    runCommand('python fixture pip upgrade', pythonExe, ['-m', 'pip', 'install', '--upgrade', 'pip']);
    runCommand('python fixture install', pythonExe, ['-m', 'pip', 'install', '-e', 'products/python-fixture[dev]']);
    runCommand('python fixture tests', pythonExe, ['-m', 'pytest', 'products/python-fixture/tests']);
  } finally {
    rmSync(venv, { recursive: true, force: true });
  }
}

export function runPolyglotFixtureProof() {
  assertFixtureShape();
  runCommand('rust fixture tests', 'cargo', ['test', '--manifest-path', 'products/rust-fixture/Cargo.toml']);
  runPythonFixtureTests();
  runCommand('typescript fixture tests', 'pnpm', [
    '--dir',
    'platform/typescript/kernel-toolchains',
    'exec',
    'vitest',
    'run',
    '--root',
    '../../..',
    'products/typescript-fixture/tests/integration.test.ts',
  ]);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runPolyglotFixtureProof();
  console.log('Polyglot fixture proof passed');
}
