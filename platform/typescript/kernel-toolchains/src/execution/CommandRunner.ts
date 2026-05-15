import type { CommandResult } from './CommandResult.js';

export interface CommandRunOptions {
  readonly cwd: string;
  readonly env?: NodeJS.ProcessEnv;
  readonly timeoutMs?: number;
  readonly commandId?: string;
  readonly maxStdoutBytes?: number;
  readonly maxStderrBytes?: number;
  readonly throwOnTimeout?: boolean;
  readonly redact?: (value: string) => string;
  readonly signal?: AbortSignal;
}

export interface CommandRunner {
  run(command: string, args: readonly string[], options: CommandRunOptions): Promise<CommandResult>;
}
