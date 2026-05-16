import { spawn } from 'node:child_process';
import * as path from 'node:path';
import type { CommandResult } from './CommandResult.js';
import type { CommandRunOptions, CommandRunner } from './CommandRunner.js';

const DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024;
const TIMEOUT_EXIT_CODE = 124;
const CANCELLED_EXIT_CODE = 130;

export class SpawnCommandRunner implements CommandRunner {
  async run(command: string, args: readonly string[], options: CommandRunOptions): Promise<CommandResult> {
    const startedAtMs = Date.now();
    const startedAt = new Date(startedAtMs).toISOString();
    const commandId = options.commandId ?? `${path.basename(command)}-${startedAtMs}`;
    const maxStdoutBytes = options.maxStdoutBytes ?? DEFAULT_MAX_OUTPUT_BYTES;
    const maxStderrBytes = options.maxStderrBytes ?? DEFAULT_MAX_OUTPUT_BYTES;
    const redact = options.redact ?? ((value: string): string => value);

    return new Promise<CommandResult>((resolve, reject) => {
      const child = spawn(command, [...args], {
        cwd: options.cwd,
        env: options.env,
        shell: false,
        detached: process.platform !== 'win32',
      });

      if (child.pid === undefined) {
        reject(new Error(`Failed to spawn process: PID not available for command ${command}`));
        return;
      }

      let stdout = '';
      let stderr = '';
      let stdoutBytes = 0;
      let stderrBytes = 0;
      let stdoutTruncated = false;
      let stderrTruncated = false;
      let settled = false;
      let timeoutHandle: NodeJS.Timeout | undefined;
      let abortListener: (() => void) | undefined;

      child.stdout.on('data', (chunk: Buffer | string) => {
        const appended = appendCappedOutput(stdout, stdoutBytes, chunk, maxStdoutBytes);
        stdout = appended.output;
        stdoutBytes = appended.bytes;
        stdoutTruncated = stdoutTruncated || appended.truncated;
      });

      child.stderr.on('data', (chunk: Buffer | string) => {
        const appended = appendCappedOutput(stderr, stderrBytes, chunk, maxStderrBytes);
        stderr = appended.output;
        stderrBytes = appended.bytes;
        stderrTruncated = stderrTruncated || appended.truncated;
      });

      child.on('error', (error) => {
        if (!settled) {
          cleanup();
          settled = true;
          reject(error);
        }
      });

      child.on('close', (code) => {
        if (!settled) {
          cleanup();
          settled = true;
          resolve(createResult(code ?? 1));
        }
      });

      if (options.timeoutMs !== undefined) {
        timeoutHandle = setTimeout(() => {
          terminateProcessTree(child.pid);
          if (!settled) {
            cleanup();
            settled = true;
            const result = createResult(TIMEOUT_EXIT_CODE, { timedOut: true });
            if (options.throwOnTimeout === true) {
              reject(new Error(`Command timed out after ${options.timeoutMs}ms: ${redact(command)} ${args.map(redact).join(' ')}`));
              return;
            }
            resolve(result);
          }
        }, options.timeoutMs);
      }

      if (options.signal !== undefined) {
        abortListener = () => {
          terminateProcessTree(child.pid);
          if (!settled) {
            cleanup();
            settled = true;
            resolve(createResult(CANCELLED_EXIT_CODE, { cancelled: true }));
          }
        };
        if (options.signal.aborted) {
          abortListener();
        } else {
          options.signal.addEventListener('abort', abortListener, { once: true });
        }
      }

      function cleanup(): void {
        if (timeoutHandle !== undefined) {
          clearTimeout(timeoutHandle);
        }
        if (abortListener !== undefined && options.signal !== undefined) {
          options.signal.removeEventListener('abort', abortListener);
        }
      }

      function createResult(
        exitCode: number,
        flags: { readonly timedOut?: boolean; readonly cancelled?: boolean } = {},
      ): CommandResult {
        return {
          commandId,
          exitCode,
          stdout: redact(stdout),
          stderr: redact(stderr),
          durationMs: Date.now() - startedAtMs,
          startedAt,
          completedAt: new Date().toISOString(),
          stdoutTruncated,
          stderrTruncated,
          pid: child.pid!,
          ...(flags.timedOut === undefined ? {} : { timedOut: flags.timedOut }),
          ...(flags.cancelled === undefined ? {} : { cancelled: flags.cancelled }),
        };
      }
    });
  }
}

function appendCappedOutput(
  currentOutput: string,
  currentBytes: number,
  chunk: Buffer | string,
  maxBytes: number,
): { readonly output: string; readonly bytes: number; readonly truncated: boolean } {
  if (currentBytes >= maxBytes) {
    return { output: currentOutput, bytes: currentBytes, truncated: true };
  }
  const value = chunk.toString();
  const valueBytes = Buffer.byteLength(value, 'utf-8');
  if (currentBytes + valueBytes <= maxBytes) {
    return {
      output: currentOutput + value,
      bytes: currentBytes + valueBytes,
      truncated: false,
    };
  }
  const remaining = maxBytes - currentBytes;
  return {
    output: currentOutput + Buffer.from(value).subarray(0, remaining).toString('utf-8'),
    bytes: maxBytes,
    truncated: true,
  };
}

function terminateProcessTree(pid: number | undefined): void {
  if (pid === undefined) {
    return;
  }
  try {
    if (process.platform === 'win32') {
      process.kill(pid);
      return;
    }
    process.kill(-pid, 'SIGTERM');
  } catch {
    try {
      process.kill(pid, 'SIGTERM');
    } catch {
      // Process already exited; close handling will settle the result.
    }
  }
}
