import {
  TOOLCHAIN_EXECUTION_RESULT_SCHEMA_VERSION,
  type ToolchainAdapterContext,
  type ToolchainExecutionObservability,
  type ToolchainExecutionResult,
  type ToolchainStepResult,
} from '../ToolchainAdapter.js';
import type { CommandResult } from './CommandResult.js';

export const TOOLCHAIN_OUTPUT_LIMIT_BYTES = 10_000;

export type ToolchainExecutionResultInput =
  Omit<ToolchainExecutionResult, 'schemaVersion' | 'runId' | 'correlationId' | 'observability'> & {
    observability?: ToolchainExecutionObservability;
  };

export function createToolchainExecutionResult(
  context: ToolchainAdapterContext,
  input: ToolchainExecutionResultInput,
): ToolchainExecutionResult {
  return {
    schemaVersion: TOOLCHAIN_EXECUTION_RESULT_SCHEMA_VERSION,
    ...(context.runId !== undefined ? { runId: context.runId } : {}),
    ...(context.correlationId !== undefined ? { correlationId: context.correlationId } : {}),
    ...input,
    observability: input.observability ?? createToolchainObservability(input.steps, input.durationMs),
  };
}

export function createCommandObservability(
  commandId: string,
  commandResult: CommandResult,
  durationMs: number,
): ToolchainExecutionObservability {
  return {
    commandId,
    durationMs,
    exitCode: commandResult.exitCode,
    stdoutBytes: Buffer.byteLength(commandResult.stdout, 'utf-8'),
    stderrBytes: Buffer.byteLength(commandResult.stderr, 'utf-8'),
    stdoutTruncated: Buffer.byteLength(commandResult.stdout, 'utf-8') > TOOLCHAIN_OUTPUT_LIMIT_BYTES,
    stderrTruncated: Buffer.byteLength(commandResult.stderr, 'utf-8') > TOOLCHAIN_OUTPUT_LIMIT_BYTES,
    outputLimitBytes: TOOLCHAIN_OUTPUT_LIMIT_BYTES,
  };
}

export function createDryRunObservability(
  commandId: string,
): ToolchainExecutionObservability {
  return {
    commandId,
    durationMs: 0,
    stdoutBytes: 0,
    stderrBytes: 0,
    stdoutTruncated: false,
    stderrTruncated: false,
    outputLimitBytes: TOOLCHAIN_OUTPUT_LIMIT_BYTES,
  };
}

export function truncateToolchainOutput(output: string): string {
  return output.slice(0, TOOLCHAIN_OUTPUT_LIMIT_BYTES);
}

function createToolchainObservability(
  steps: readonly ToolchainStepResult[],
  durationMs: number,
): ToolchainExecutionObservability {
  const firstStep = steps[0];
  const stdout = firstStep?.stdout ?? '';
  const stderr = firstStep?.stderr ?? '';
  return {
    commandId: firstStep?.stepId ?? 'toolchain-adapter',
    durationMs,
    ...(firstStep?.exitCode !== undefined ? { exitCode: firstStep.exitCode } : {}),
    stdoutBytes: Buffer.byteLength(stdout, 'utf-8'),
    stderrBytes: Buffer.byteLength(stderr, 'utf-8'),
    stdoutTruncated: false,
    stderrTruncated: false,
    outputLimitBytes: TOOLCHAIN_OUTPUT_LIMIT_BYTES,
  };
}
