#!/usr/bin/env node
/**
 * @fileoverview CI gate to enforce production Studio profile configuration.
 *
 * @doc.type script
 * @doc.purpose Production profile enforcement CI gate
 * @doc.layer governance
 * @doc.pattern ValidationScript
 */

import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';

const REQUIRED_PRODUCTION_VARS = [
  'VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION',
];

const KERNEL_PERSISTENCE_VARS = [
  'VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE',
  'VITE_GHATANA_KERNEL_API_BASE_URL',
  'VITE_STUDIO_TENANT_ID',
  'VITE_STUDIO_WORKSPACE_ID',
  'VITE_STUDIO_PROJECT_ID',
];

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    envFile: null,
    mode: 'production',
    strict: true,
  };

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    const [key, inlineValue] = arg.includes('=') ? arg.split(/=(.*)/s, 2) : [arg, undefined];
    if (key === '--env-file') {
      options.envFile = readOptionValue('--env-file', inlineValue, args, index);
      if (inlineValue === undefined) {
        index += 1;
      }
    } else if (key === '--mode') {
      options.mode = readOptionValue('--mode', inlineValue, args, index);
      if (inlineValue === undefined) {
        index += 1;
      }
    } else if (key === '--no-strict') {
      options.strict = false;
    } else if (key === '--help') {
      showHelp();
      process.exit(0);
    }
  }

  return options;
}

function readOptionValue(optionName, inlineValue, args, index) {
  if (inlineValue !== undefined && inlineValue.length > 0) {
    return inlineValue;
  }
  const nextValue = args[index + 1];
  if (!nextValue || nextValue.startsWith('--')) {
    throw new Error(`Missing value for ${optionName}`);
  }
  return nextValue;
}

function showHelp() {
  console.log(`
Usage: node check-studio-production-profile.mjs [options]

Options:
  --env-file <path>    Path to environment file to validate
  --mode <mode>        Environment mode (production, staging) [default: production]
  --no-strict          Allow warnings instead of errors for non-critical checks
  --help               Show this help message

Examples:
  node -- check-studio-production-profile.mjs --env-file=.env.production
  node -- check-studio-production-profile.mjs --mode=staging --no-strict
`);
}

function parseEnvFile(filePath) {
  if (!existsSync(filePath)) {
    throw new Error(`Environment file not found: ${filePath}`);
  }

  const content = readFileSync(filePath, 'utf-8');
  const env = {};

  for (const line of content.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }

    const equalsIndex = trimmed.indexOf('=');
    if (equalsIndex === -1) {
      continue;
    }

    const key = trimmed.substring(0, equalsIndex).trim();
    let value = trimmed.substring(equalsIndex + 1).trim();

    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }

    env[key] = value;
  }

  return env;
}

function validateProductionProfile(env, options) {
  const errors = [];
  const warnings = [];

  for (const key of REQUIRED_PRODUCTION_VARS) {
    const value = env[key];
    if (!value) {
      errors.push(`Missing required variable: ${key}`);
    } else if (value !== 'true') {
      errors.push(`${key} must be 'true' in production, got: ${value}`);
    }
  }

  const kernelPersistenceEnabled = env.VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE === 'true';
  if (kernelPersistenceEnabled) {
    for (const key of KERNEL_PERSISTENCE_VARS.slice(1)) {
      if (!env[key]) {
        errors.push(`Kernel persistence enabled but missing required variable: ${key}`);
      }
    }

    const apiUrl = env.VITE_GHATANA_KERNEL_API_BASE_URL;
    if (apiUrl && !apiUrl.startsWith('https://') && options.mode === 'production') {
      errors.push(`VITE_GHATANA_KERNEL_API_BASE_URL must use HTTPS in production: ${apiUrl}`);
    }
  } else {
    const message = 'Kernel workflow persistence is disabled. Production Studio must use Kernel-backed workflow persistence.';
    if (options.strict && options.mode === 'production') {
      errors.push(message);
    } else {
      warnings.push(message);
    }
  }

  if (!env.VITE_STUDIO_AUTH_TOKEN && kernelPersistenceEnabled) {
    errors.push('VITE_STUDIO_AUTH_TOKEN is required when kernel persistence is enabled');
  }

  return { errors, warnings };
}

function main() {
  const options = parseArgs();

  console.log(`Checking Studio production profile for mode: ${options.mode}`);
  console.log('='.repeat(60));

  try {
    let env;
    if (options.envFile) {
      const envPath = resolve(options.envFile);
      console.log(`Reading environment from: ${envPath}`);
      env = parseEnvFile(envPath);
    } else {
      console.log('Reading environment from process.env');
      env = process.env;
    }

    const { errors, warnings } = validateProductionProfile(env, options);

    if (warnings.length > 0) {
      console.log('\nWarnings:');
      for (const warning of warnings) {
        console.log(`  - ${warning}`);
      }
    }

    if (errors.length > 0) {
      console.log('\nErrors:');
      for (const error of errors) {
        console.log(`  - ${error}`);
      }
      console.log(`\nProduction profile validation FAILED (${errors.length} errors)`);
      process.exit(1);
    }

    console.log('\nProduction profile validation PASSED');
    console.log('\nConfiguration:');
    console.log(`  - Production acquisition: ${env.VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION === 'true' ? 'enabled' : 'disabled'}`);
    console.log(`  - Kernel persistence: ${env.VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE === 'true' ? 'enabled' : 'disabled'}`);
    if (env.VITE_GHATANA_KERNEL_API_BASE_URL) {
      console.log(`  - API base URL: ${env.VITE_GHATANA_KERNEL_API_BASE_URL}`);
    }

    process.exit(0);
  } catch (error) {
    console.error(`\nValidation error: ${error.message}`);
    process.exit(1);
  }
}

main();
