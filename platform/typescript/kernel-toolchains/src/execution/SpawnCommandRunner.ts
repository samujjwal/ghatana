import { spawn } from 'node:child_process';
import type { CommandResult } from './CommandResult.js';
import type { CommandRunOptions, CommandRunner } from './CommandRunner.js';

export class SpawnCommandRunner implements CommandRunner {
  async run(command: string, args: readonly string[], options: CommandRunOptions): Promise<CommandResult> {
    const startedAt = Date.now();

    return new Promise<CommandResult>((resolve, reject) => {
      const child = spawn(command, [...args], {
        cwd: options.cwd,
        env: options.env,
        shell: false,
      });

      let stdout = '';
      let stderr = '';
      let timeoutHandle: NodeJS.Timeout | undefined;

      child.stdout.on('data', (chunk: Buffer | string) => {
        stdout += chunk.toString();
      });

      child.stderr.on('data', (chunk: Buffer | string) => {
        stderr += chunk.toString();
      });

      child.on('error', (error) => {
        if (timeoutHandle) {
          clearTimeout(timeoutHandle);
        }
        reject(error);
      });

      child.on('close', (code) => {
        if (timeoutHandle) {
          clearTimeout(timeoutHandle);
        }
        resolve({
          exitCode: code ?? 1,
          stdout,
          stderr,
          durationMs: Date.now() - startedAt,
        });
      });

      if (options.timeoutMs !== undefined) {
        timeoutHandle = setTimeout(() => {
          child.kill();
          reject(new Error(`Command timed out after ${options.timeoutMs}ms: ${command} ${args.join(' ')}`));
        }, options.timeoutMs);
      }
    });
  }
}