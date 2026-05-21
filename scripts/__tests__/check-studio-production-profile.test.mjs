/**
 * @fileoverview Tests for check-studio-production-profile.mjs
 *
 * @doc.type test
 * @doc.purpose Production profile checker validation
 * @doc.layer governance
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { spawnSync } from 'child_process';
import { writeFileSync, mkdtempSync, rmSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { fileURLToPath } from 'url';

const SCRIPT_PATH = fileURLToPath(new URL('../check-studio-production-profile.mjs', import.meta.url));

function runScript(args = []) {
  const result = spawnSync(process.execPath, ['--', SCRIPT_PATH, ...args], {
    encoding: 'utf-8',
    env: { ...process.env, NODE_ENV: 'test' },
  });

  return {
    exitCode: result.status ?? 1,
    stdout: result.stdout,
    stderr: result.stderr,
  };
}

describe('check-studio-production-profile', () => {
  let tempDir;

  beforeEach(() => {
    tempDir = mkdtempSync(join(tmpdir(), 'studio-profile-test-'));
  });

  afterEach(() => {
    if (tempDir) {
      rmSync(tempDir, { recursive: true, force: true });
    }
  });

  it('passes with valid production configuration', () => {
    const envContent = `
VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION=true
VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE=true
VITE_GHATANA_KERNEL_API_BASE_URL=https://api.ghatana.io
VITE_STUDIO_TENANT_ID=tenant-prod
VITE_STUDIO_WORKSPACE_ID=workspace-prod
VITE_STUDIO_PROJECT_ID=project-prod
VITE_STUDIO_AUTH_TOKEN=secret-token
`;
    const envFile = join(tempDir, '.env.valid');
    writeFileSync(envFile, envContent);

    const result = runScript([`--env-file=${envFile}`]);

    expect(result.exitCode).toBe(0);
    expect(result.stdout).toContain('PASSED');
    expect(result.stdout).toContain('Production acquisition: enabled');
    expect(result.stdout).toContain('Kernel persistence: enabled');
  });

  it('fails when production acquisition is not enabled', () => {
    const envContent = `
VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION=false
`;
    const envFile = join(tempDir, '.env.invalid');
    writeFileSync(envFile, envContent);

    const result = runScript([`--env-file=${envFile}`]);

    expect(result.exitCode).toBe(1);
    expect(result.stdout).toContain('FAILED');
    expect(result.stdout).toContain('must be \'true\'');
  });

  it('fails when production acquisition is missing', () => {
    const envContent = `
VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE=true
VITE_GHATANA_KERNEL_API_BASE_URL=https://api.ghatana.io
`;
    const envFile = join(tempDir, '.env.missing');
    writeFileSync(envFile, envContent);

    const result = runScript([`--env-file=${envFile}`]);

    expect(result.exitCode).toBe(1);
    expect(result.stdout).toContain('FAILED');
    expect(result.stdout).toContain('Missing required variable');
  });

  it('fails when kernel persistence is enabled but API URL uses HTTP', () => {
    const envContent = `
VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION=true
VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE=true
VITE_GHATANA_KERNEL_API_BASE_URL=http://api.ghatana.io
`;
    const envFile = join(tempDir, '.env.http');
    writeFileSync(envFile, envContent);

    const result = runScript([`--env-file=${envFile}`, '--mode=production']);

    expect(result.exitCode).toBe(1);
    expect(result.stdout).toContain('FAILED');
    expect(result.stdout).toContain('HTTPS');
  });

  it('fails closed when kernel persistence is disabled in strict production mode', () => {
    const envContent = `
VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION=true
VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE=false
`;
    const envFile = join(tempDir, '.env.local');
    writeFileSync(envFile, envContent);

    const result = runScript([`--env-file=${envFile}`]);

    expect(result.exitCode).toBe(1);
    expect(result.stdout).toContain('FAILED');
    expect(result.stdout).toContain('Kernel-backed workflow persistence');
  });

  it('warns when kernel persistence is disabled outside strict production mode', () => {
    const envContent = `
VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION=true
VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE=false
`;
    const envFile = join(tempDir, '.env.local-warning');
    writeFileSync(envFile, envContent);

    const result = runScript([`--env-file=${envFile}`, '--mode=staging', '--no-strict']);

    expect(result.exitCode).toBe(0);
    expect(result.stdout).toContain('PASSED');
    expect(result.stdout).toContain('Warnings');
    expect(result.stdout).toContain('Kernel-backed workflow persistence');
  });

  it('fails when kernel persistence is enabled but required vars are missing', () => {
    const envContent = `
VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION=true
VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE=true
# Missing: VITE_GHATANA_KERNEL_API_BASE_URL, VITE_STUDIO_TENANT_ID, etc.
`;
    const envFile = join(tempDir, '.env.incomplete');
    writeFileSync(envFile, envContent);

    const result = runScript([`--env-file=${envFile}`]);

    expect(result.exitCode).toBe(1);
    expect(result.stdout).toContain('FAILED');
    expect(result.stdout).toContain('missing required variable');
  });

  it('fails when auth token is missing with kernel persistence enabled', () => {
    const envContent = `
VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION=true
VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE=true
VITE_GHATANA_KERNEL_API_BASE_URL=https://api.ghatana.io
VITE_STUDIO_TENANT_ID=tenant-test
VITE_STUDIO_WORKSPACE_ID=workspace-test
VITE_STUDIO_PROJECT_ID=project-test
# Missing: VITE_STUDIO_AUTH_TOKEN
`;
    const envFile = join(tempDir, '.env.no-auth');
    writeFileSync(envFile, envContent);

    const result = runScript([`--env-file=${envFile}`]);

    expect(result.exitCode).toBe(1);
    expect(result.stdout).toContain('FAILED');
    expect(result.stdout).toContain('VITE_STUDIO_AUTH_TOKEN is required');
  });

  it('fails when env file does not exist', () => {
    const result = runScript(['--env-file=/nonexistent/.env']);

    expect(result.exitCode).toBe(1);
    expect(result.stderr).toContain('Environment file not found');
  });

  it('handles quoted values in env file', () => {
    const envContent = `
VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION="true"
VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE='true'
VITE_GHATANA_KERNEL_API_BASE_URL="https://api.ghatana.io"
VITE_STUDIO_TENANT_ID="tenant-quoted"
VITE_STUDIO_WORKSPACE_ID="workspace-quoted"
VITE_STUDIO_PROJECT_ID="project-quoted"
VITE_STUDIO_AUTH_TOKEN="token-quoted"
`;
    const envFile = join(tempDir, '.env.quoted');
    writeFileSync(envFile, envContent);

    const result = runScript([`--env-file=${envFile}`]);

    expect(result.exitCode).toBe(0);
    expect(result.stdout).toContain('PASSED');
  });

  it('shows help with --help flag', () => {
    const result = runScript(['--help']);

    expect(result.exitCode).toBe(0);
    expect(result.stdout).toContain('Usage');
    expect(result.stdout).toContain('Options');
  });
});
