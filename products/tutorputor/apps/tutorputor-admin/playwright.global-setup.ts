import type { FullConfig, GlobalSetup } from '@playwright/test';
import { spawn, type ChildProcess } from 'node:child_process';
import http from 'node:http';

async function checkHealthOnce(url: string): Promise<boolean> {
  return new Promise((resolve) => {
    const req = http.get(url, (res) => {
      // Any 2xx is considered healthy
      resolve(!!res.statusCode && res.statusCode >= 200 && res.statusCode < 300);
      res.resume();
    });

    req.on('error', () => resolve(false));
    req.setTimeout(2000, () => {
      req.destroy();
      resolve(false);
    });
  });
}

async function waitForHealth(url: string, timeoutMs: number): Promise<void> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await checkHealthOnce(url)) return;
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`API gateway at ${url} did not become healthy within ${timeoutMs}ms`);
}

const globalSetup: GlobalSetup = async (_config: FullConfig) => {
  const healthUrl = process.env.TUTORPUTOR_GATEWAY_HEALTH_URL ?? 'http://127.0.0.1:3200/health';

  // If gateway is already running, do nothing.
  if (await checkHealthOnce(healthUrl)) {
    return;
  }

  // Start API gateway via pnpm workspace filter.
  const child: ChildProcess = spawn(
    'pnpm',
    ['--filter', '@ghatana/tutorputor-api-gateway', 'dev'],
    {
      stdio: 'inherit',
      shell: process.platform === 'win32',
    },
  );

  // Ensure we clean up the child when Playwright process exits.
  const terminate = () => {
    if (!child.killed) {
      try {
        child.kill();
      } catch {
        // ignore
      }
    }
  };

  process.on('exit', terminate);
  process.on('SIGINT', () => {
    terminate();
    process.exit(130);
  });
  process.on('SIGTERM', () => {
    terminate();
    process.exit(143);
  });

  // Wait until the gateway is healthy before running tests.
  await waitForHealth(healthUrl, 120_000);
};

export default globalSetup;
