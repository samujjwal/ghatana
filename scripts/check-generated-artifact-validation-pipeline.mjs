#!/usr/bin/env node
/**
 * @fileoverview CI gate for generated artifact validation pipeline stages.
 *
 * Executes a generated-project fixture through:
 * - typecheck
 * - lint
 * - test
 * - build
 * - preview smoke render
 *
 * @doc.type script
 * @doc.purpose Validate generated artifacts with executable pipeline stages
 * @doc.layer platform
 * @doc.pattern ValidationScript
 */

import { existsSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = process.cwd();
const FIXTURE_DIR = 'platform/typescript/artifact-compiler-ts/fixtures/generated-project';

const REQUIRED_FIXTURE_FILES = [
  `${FIXTURE_DIR}/package.json`,
  `${FIXTURE_DIR}/tsconfig.json`,
  `${FIXTURE_DIR}/tsconfig.build.json`,
  `${FIXTURE_DIR}/src/index.ts`,
  `${FIXTURE_DIR}/src/component.ts`,
  'platform/typescript/artifact-compiler-ts/src/generated-artifact-fixture.test.ts',
  `${FIXTURE_DIR}/scripts/lint-generated-project.mjs`,
  `${FIXTURE_DIR}/scripts/preview-smoke.mjs`,
];

function parseArgs(argv) {
  return {
    dryRun: argv.includes('--dry-run'),
  };
}

export function getGeneratedArtifactValidationStages() {
  return [
    {
      stageId: 'typecheck',
      command: 'pnpm',
      args: [
        '--dir',
        'platform/typescript/artifact-compiler-ts',
        'exec',
        'tsc',
        '--pretty',
        'false',
        '--noEmit',
        '-p',
        'fixtures/generated-project/tsconfig.json',
      ],
    },
    {
      stageId: 'lint',
      command: 'node',
      args: [`${FIXTURE_DIR}/scripts/lint-generated-project.mjs`],
    },
    {
      stageId: 'test',
      command: 'pnpm',
      args: [
        '--dir',
        'platform/typescript/artifact-compiler-ts',
        'exec',
        'vitest',
        'run',
        'src/generated-artifact-fixture.test.ts',
      ],
    },
    {
      stageId: 'build',
      command: 'pnpm',
      args: [
        '--dir',
        'platform/typescript/artifact-compiler-ts',
        'exec',
        'tsc',
        '--pretty',
        'false',
        '-p',
        'fixtures/generated-project/tsconfig.build.json',
      ],
    },
    {
      stageId: 'preview-render',
      command: 'node',
      args: [`${FIXTURE_DIR}/scripts/preview-smoke.mjs`],
    },
  ];
}

function ensureFixtureFilesExist() {
  const missing = REQUIRED_FIXTURE_FILES.filter((relativePath) =>
    !existsSync(path.join(REPO_ROOT, relativePath)),
  );

  if (missing.length > 0) {
    throw new Error(`Generated fixture is incomplete. Missing: ${missing.join(', ')}`);
  }
}

export function runGeneratedArtifactValidationPipeline({ dryRun = false } = {}) {
  ensureFixtureFilesExist();

  const stages = getGeneratedArtifactValidationStages();

  for (const stage of stages) {
    const printable = `${stage.command} ${stage.args.join(' ')}`;
    console.log(`- ${stage.stageId}: ${printable}`);

    if (dryRun) {
      continue;
    }

    const result = spawnSync(printable, {
      cwd: REPO_ROOT,
      encoding: 'utf-8',
      stdio: 'pipe',
      shell: true,
    });

    if (result.error) {
      throw new Error(`Stage ${stage.stageId} failed to start: ${result.error.message}`);
    }

    if (result.status !== 0) {
      if (result.stdout) process.stdout.write(result.stdout);
      if (result.stderr) process.stderr.write(result.stderr);
      throw new Error(`Stage ${stage.stageId} failed with exit code ${result.status ?? 1}`);
    }
  }
}

function main() {
  const options = parseArgs(process.argv);
  console.log('=== check:generated-artifact-validation-pipeline ===');

  runGeneratedArtifactValidationPipeline({ dryRun: options.dryRun });

  console.log(options.dryRun
    ? 'Generated artifact validation pipeline dry run completed.'
    : 'Generated artifact validation pipeline passed.');
}

const invokedAsScript = process.argv[1] !== undefined
  && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedAsScript) {
  main();
}
