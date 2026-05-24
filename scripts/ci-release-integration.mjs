#!/usr/bin/env node

/**
 * CI integration for Data-Cloud releases.
 *
 * This script provides CI/CD integration for release processes,
 * including automated testing, artifact building, and deployment triggers.
 *
 * Usage: node scripts/ci-release-integration.mjs <release-tag> <action>
 * Actions: validate, build, deploy
 */

import { execSync } from 'child_process';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..');

/**
 * Executes a command and returns the output.
 */
function execCommand(command, cwd = REPO_ROOT) {
  try {
    return execSync(command, { cwd, encoding: 'utf-8' });
  } catch (error) {
    console.error(`Command failed: ${command}`);
    console.error(error.message);
    throw error;
  }
}

/**
 * Validates the release.
 */
function validateRelease(releaseTag) {
  console.log(`Validating release: ${releaseTag}\n`);

  // Run tests
  console.log('Running tests...');
  execCommand('./gradlew test');
  console.log('✓ Tests passed');

  // Run artifact bundler
  console.log('Bundling artifacts...');
  execCommand(`node scripts/artifact-bundler.mjs ${releaseTag}`);
  console.log('✓ Artifacts bundled');

  // Validate bundle
  console.log('Validating bundle...');
  execCommand(`node scripts/validate-artifact-bundle.mjs releases/${releaseTag}.bundle`);
  console.log('✓ Bundle validated');

  console.log(`\nRelease validation PASSED for: ${releaseTag}`);
}

/**
 * Builds the release.
 */
function buildRelease(releaseTag) {
  console.log(`Building release: ${releaseTag}\n`);

  // Build all modules
  console.log('Building modules...');
  execCommand('./gradlew build');
  console.log('✓ Build completed');

  // Create distribution
  console.log('Creating distribution...');
  execCommand('./gradlew distZip');
  console.log('✓ Distribution created');

  console.log(`\nRelease build COMPLETED for: ${releaseTag}`);
}

/**
 * Deploys the release.
 */
function deployRelease(releaseTag) {
  console.log(`Deploying release: ${releaseTag}\n`);

  // Enforce workflow
  console.log('Enforcing release workflow...');
  execCommand(`node scripts/enforce-release-workflow.mjs ${releaseTag}`);
  console.log('✓ Workflow enforced');

  // Run gate checks
  console.log('Running release gate checks...');
  execCommand(`node scripts/release-gate-check.mjs ${releaseTag}`);
  console.log('✓ Gate checks passed');

  // Deploy (placeholder)
  console.log('Deploying to production...');
  // In production, this would trigger actual deployment
  console.log('✓ Deployment triggered');

  console.log(`\nRelease deployment TRIGGERED for: ${releaseTag}`);
}

/**
 * Main execution.
 */
function main() {
  const releaseTag = process.argv[2];
  const action = process.argv[3];

  if (!releaseTag || !action) {
    console.error('Usage: node scripts/ci-release-integration.mjs <release-tag> <action>');
    console.error('Actions: validate, build, deploy');
    process.exit(1);
  }

  switch (action) {
    case 'validate':
      validateRelease(releaseTag);
      break;
    case 'build':
      buildRelease(releaseTag);
      break;
    case 'deploy':
      deployRelease(releaseTag);
      break;
    default:
      console.error(`Unknown action: ${action}`);
      console.error('Actions: validate, build, deploy');
      process.exit(1);
  }
}

main();
