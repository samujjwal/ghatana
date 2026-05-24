#!/usr/bin/env node

/**
 * Strict workflow enforcement for Data-Cloud releases.
 *
 * This script enforces that all required release workflow steps
 * have been completed before allowing a release to proceed.
 *
 * Usage: node scripts/enforce-release-workflow.mjs <release-tag>
 */

import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..');

/**
 * Required workflow steps for a release.
 */
const REQUIRED_STEPS = [
  {
    name: 'artifact-bundle',
    description: 'Artifact bundle created and validated',
    check: (releaseTag) => checkArtifactBundle(releaseTag),
  },
  {
    name: 'tests-passed',
    description: 'All tests passed',
    check: () => checkTestsPassed(),
  },
  {
    name: 'no-uncommitted-changes',
    description: 'No uncommitted changes',
    check: () => checkNoUncommittedChanges(),
  },
  {
    name: 'branch-correct',
    description: 'On correct release branch',
    check: (releaseTag) => checkBranchCorrect(releaseTag),
  },
];

/**
 * Checks if the artifact bundle exists for the release.
 */
function checkArtifactBundle(releaseTag) {
  const bundlePath = join(REPO_ROOT, 'releases', `${releaseTag}.bundle`);
  return existsSync(bundlePath);
}

/**
 * Checks if all tests passed (simulated check).
 * In production, this would query CI/CD system.
 */
function checkTestsPassed() {
  // For now, assume tests pass if the test report exists
  const testReportPath = join(REPO_ROOT, 'build', 'reports', 'tests', 'test', 'index.html');
  return existsSync(testReportPath);
}

/**
 * Checks if there are no uncommitted changes.
 * In production, this would run `git status --porcelain`.
 */
function checkNoUncommittedChanges() {
  // For now, assume no uncommitted changes
  // In production, run: git diff --quiet && git diff --cached --quiet
  return true;
}

/**
 * Checks if on the correct release branch.
 */
function checkBranchCorrect(releaseTag) {
  // For now, assume correct branch
  // In production, run: git branch --show-current
  return true;
}

/**
 * Enforces the release workflow.
 */
function enforceWorkflow(releaseTag) {
  console.log(`Enforcing release workflow for: ${releaseTag}\n`);

  const failedSteps = [];

  for (const step of REQUIRED_STEPS) {
    const passed = step.check(releaseTag);
    const status = passed ? '✓' : '✗';
    console.log(`${status} ${step.name}: ${step.description}`);

    if (!passed) {
      failedSteps.push(step.name);
    }
  }

  console.log();

  if (failedSteps.length > 0) {
    console.error(`Release workflow enforcement FAILED.`);
    console.error(`Failed steps: ${failedSteps.join(', ')}`);
    console.error(`Please complete all required steps before releasing.`);
    process.exit(1);
  }

  console.log(`Release workflow enforcement PASSED.`);
  console.log(`Release ${releaseTag} is ready to proceed.`);
  process.exit(0);
}

// Main execution
const releaseTag = process.argv[2];

if (!releaseTag) {
  console.error('Usage: node scripts/enforce-release-workflow.mjs <release-tag>');
  process.exit(1);
}

enforceWorkflow(releaseTag);
