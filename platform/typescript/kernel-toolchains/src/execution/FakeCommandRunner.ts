import type { CommandResult } from './CommandResult.js';
import type { CommandRunOptions, CommandRunner } from './CommandRunner.js';

export interface FakeCommandInvocation {
  readonly command: string;
  readonly args: readonly string[];
  readonly options: CommandRunOptions;
}

export class FakeCommandRunner implements CommandRunner {
  readonly invocations: FakeCommandInvocation[] = [];
  private readonly results: CommandResult[];

  constructor(results: readonly CommandResult[] = [{ exitCode: 0, stdout: '', stderr: '', durationMs: 0, pid: 9999 }]) {
    this.results = [...results];
  }

  async run(command: string, args: readonly string[], options: CommandRunOptions): Promise<CommandResult> {
    this.invocations.push({ command, args, options });
    const result = this.results.shift();
    if (!result) {
      throw new Error('No fake command result configured');
    }
    return result;
  }
}