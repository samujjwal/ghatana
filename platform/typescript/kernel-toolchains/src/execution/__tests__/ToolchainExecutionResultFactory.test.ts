import { describe, expect, it } from 'vitest';
import type { AdapterLogger, ToolchainAdapterContext } from '../../ToolchainAdapter.js';
import {
  TOOLCHAIN_OUTPUT_LIMIT_BYTES,
  createCommandObservability,
  createDryRunObservability,
  createToolchainExecutionResult,
  truncateToolchainOutput,
} from '../ToolchainExecutionResultFactory.js';

describe('ToolchainExecutionResultFactory', () => {
  it('stamps schema version and execution identifiers', () => {
    const result = createToolchainExecutionResult(createContext(), {
      status: 'succeeded',
      steps: [{ stepId: 'build', status: 'succeeded', exitCode: 0, stdout: 'ok', stderr: '', durationMs: 4 }],
      artifacts: [],
      durationMs: 4,
    });

    expect(result).toMatchObject({
      schemaVersion: '1.0.0',
      runId: 'run-123',
      correlationId: 'corr-123',
      observability: {
        commandId: 'build',
        exitCode: 0,
        stdoutBytes: 2,
        stderrBytes: 0,
      },
    });
  });

  it('uses adapter-level fallback observability when no step exists', () => {
    const context = createContext();
    delete context.runId;
    delete context.correlationId;

    const result = createToolchainExecutionResult(context, {
      status: 'skipped',
      steps: [],
      artifacts: [],
      durationMs: 0,
    });

    expect(result.runId).toBeUndefined();
    expect(result.correlationId).toBeUndefined();
    expect(result.observability).toMatchObject({
      commandId: 'toolchain-adapter',
      durationMs: 0,
      stdoutBytes: 0,
      stderrBytes: 0,
    });
    expect(result.observability?.exitCode).toBeUndefined();
  });

  it('marks oversized command output as truncated', () => {
    const oversized = 'x'.repeat(TOOLCHAIN_OUTPUT_LIMIT_BYTES + 1);

    const observability = createCommandObservability(
      'pnpm-build',
      { exitCode: 0, stdout: oversized, stderr: oversized, durationMs: 8 },
      8,
    );

    expect(observability.stdoutBytes).toBe(TOOLCHAIN_OUTPUT_LIMIT_BYTES + 1);
    expect(observability.stderrBytes).toBe(TOOLCHAIN_OUTPUT_LIMIT_BYTES + 1);
    expect(observability.stdoutTruncated).toBe(true);
    expect(observability.stderrTruncated).toBe(true);
    expect(truncateToolchainOutput(oversized)).toHaveLength(TOOLCHAIN_OUTPUT_LIMIT_BYTES);
  });

  it('creates dry-run observability without an exit code', () => {
    expect(createDryRunObservability('pnpm-build')).toEqual({
      commandId: 'pnpm-build',
      durationMs: 0,
      stdoutBytes: 0,
      stderrBytes: 0,
      stdoutTruncated: false,
      stderrTruncated: false,
      outputLimitBytes: TOOLCHAIN_OUTPUT_LIMIT_BYTES,
    });
  });
});

function createContext(): ToolchainAdapterContext {
  return {
    productId: 'digital-marketing',
    runId: 'run-123',
    correlationId: 'corr-123',
    phase: 'build',
    surface: {
      type: 'web',
      adapter: 'pnpm-vite-react',
      path: 'products/digital-marketing/web',
    },
    dryRun: false,
    surfaceConfig: {},
    phaseConfig: {},
    logger: createLogger(),
  };
}

function createLogger(): AdapterLogger {
  return {
    info: () => undefined,
    warn: () => undefined,
    error: () => undefined,
    debug: () => undefined,
  };
}
