import * as process from 'node:process';
import { describe, expect, it } from 'vitest';
import { SpawnCommandRunner } from '../SpawnCommandRunner.js';

describe('SpawnCommandRunner', () => {
  it('runs commands with shell false and records command metadata', async () => {
    const runner = new SpawnCommandRunner();

    const result = await runner.run(process.execPath, ['-e', 'console.log("ok")'], {
      cwd: process.cwd(),
      commandId: 'node-ok',
    });

    expect(result).toMatchObject({
      commandId: 'node-ok',
      exitCode: 0,
      stdout: 'ok\n',
      stderr: '',
      stdoutTruncated: false,
      stderrTruncated: false,
    });
    expect(result.startedAt).toMatch(/T/);
    expect(result.completedAt).toMatch(/T/);
  });

  it('times out and returns a structured timeout result by default', async () => {
    const runner = new SpawnCommandRunner();

    const result = await runner.run(process.execPath, ['-e', 'setTimeout(() => {}, 1000)'], {
      cwd: process.cwd(),
      timeoutMs: 10,
      commandId: 'node-timeout',
    });

    expect(result).toMatchObject({
      commandId: 'node-timeout',
      exitCode: 124,
      timedOut: true,
    });
  });

  it('throws on timeout only when requested', async () => {
    const runner = new SpawnCommandRunner();

    await expect(
      runner.run(process.execPath, ['-e', 'setTimeout(() => {}, 1000)'], {
        cwd: process.cwd(),
        timeoutMs: 10,
        throwOnTimeout: true,
      }),
    ).rejects.toThrow('Command timed out');
  });

  it('truncates large stdout and stderr independently', async () => {
    const runner = new SpawnCommandRunner();

    const result = await runner.run(
      process.execPath,
      ['-e', 'process.stdout.write("abcdef"); process.stderr.write("uvwxyz");'],
      {
        cwd: process.cwd(),
        maxStdoutBytes: 3,
        maxStderrBytes: 4,
      },
    );

    expect(result.stdout).toBe('abc');
    expect(result.stderr).toBe('uvwx');
    expect(result.stdoutTruncated).toBe(true);
    expect(result.stderrTruncated).toBe(true);
  });

  it('redacts output through the supplied redaction hook', async () => {
    const runner = new SpawnCommandRunner();

    const result = await runner.run(process.execPath, ['-e', 'console.log(process.env.SECRET_TOKEN)'], {
      cwd: process.cwd(),
      env: { ...process.env, SECRET_TOKEN: 'super-secret' },
      redact: (value) => value.replaceAll('super-secret', '[REDACTED]'),
    });

    expect(result.stdout).toContain('[REDACTED]');
    expect(result.stdout).not.toContain('super-secret');
  });

  it('supports cancellation signals', async () => {
    const runner = new SpawnCommandRunner();
    const abortController = new AbortController();
    const running = runner.run(process.execPath, ['-e', 'setTimeout(() => {}, 1000)'], {
      cwd: process.cwd(),
      signal: abortController.signal,
    });

    abortController.abort();

    await expect(running).resolves.toMatchObject({
      exitCode: 130,
      cancelled: true,
    });
  });
});
