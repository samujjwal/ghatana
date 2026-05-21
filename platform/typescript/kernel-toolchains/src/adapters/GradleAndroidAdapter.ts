import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type {
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainPlanStep,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ProductLifecyclePhase,
  ProductSurfaceType,
  AdapterPreflightResult,
  LifecycleFailureClassifier,
} from '../ToolchainAdapter.js';
import type { CommandRunner } from '../execution/CommandRunner.js';
import { SpawnCommandRunner } from '../execution/SpawnCommandRunner.js';
import {
  createCommandObservability,
  createDryRunObservability,
  createToolchainExecutionResult,
  truncateToolchainOutput,
  TOOLCHAIN_OUTPUT_LIMIT_BYTES,
} from '../execution/ToolchainExecutionResultFactory.js';
import {
  createDefaultPreflightResult,
  createDefaultFailureClassifier,
} from '../ToolchainAdapter.js';

const APK_PATTERN = /(?:apk|output).*?(\S+\.apk)/gi;
const AAB_PATTERN = /(?:aab|bundle).*?(\S+\.aab)/gi;
const GRADLEW_WINDOWS = 'gradlew.bat';
const GRADLEW_UNIX = './gradlew';

/**
 * Gradle Android adapter for Android app builds.
 * Uses SpawnCommandRunner for safe, non-shell subprocess execution with
 * bounded output capture and structured observability.
 */
export class GradleAndroidAdapter implements ToolchainAdapter {
  readonly id = 'gradle-android';
  readonly supportedPhases: ProductLifecyclePhase[] = ['dev', 'validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['mobile-android'];

  private readonly runner: CommandRunner;

  constructor(runner?: CommandRunner) {
    this.runner = runner ?? new SpawnCommandRunner();
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const gradleModule = typeof surfaceConfig.gradleModule === 'string' ? surfaceConfig.gradleModule : undefined;
    const variant = typeof surfaceConfig.variant === 'string' ? surfaceConfig.variant : 'release';
    const workingDirectory = context.outputDir ?? process.cwd();

    const taskVariant = variant.charAt(0).toUpperCase() + variant.slice(1);
    const taskName = phase === 'package'
      ? `bundle${taskVariant}`
      : `assemble${taskVariant}`;

    const gradlew = process.platform === 'win32' ? GRADLEW_WINDOWS : GRADLEW_UNIX;
    const args: string[] = gradleModule !== undefined
      ? [`:${gradleModule}:${taskName}`, '--stacktrace']
      : [taskName, '--stacktrace'];

    const expectedExt = phase === 'package' ? '.aab' : '.apk';

    return [
      {
        id: `gradle-android-${phase}`,
        description: `Build Android app variant=${variant} task=${taskName}`,
        command: [gradlew, ...args],
        workingDirectory,
        expectedOutputs: [`app/build/outputs/${phase === 'package' ? 'bundle' : 'apk'}/${variant}/app-${variant}${expectedExt}`],
      },
    ];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const steps = await this.plan(context);
    const step = steps[0];
    if (step === undefined) {
      return createToolchainExecutionResult(context, {
        status: 'failed',
        steps: [],
        artifacts: [],
        durationMs: 0,
        failure: { stepId: 'gradle-android-plan', message: 'Gradle Android adapter produced no plan steps' },
      });
    }

    const [command, ...args] = step.command;
    if (command === undefined) {
      return createToolchainExecutionResult(context, {
        status: 'failed',
        steps: [],
        artifacts: [],
        durationMs: 0,
        failure: { stepId: step.id, message: 'Gradle Android adapter plan step has no command' },
      });
    }

    if (context.dryRun) {
      context.logger.info(`[DRY-RUN] gradle-android: would execute: ${step.command.join(' ')}`);
      return createToolchainExecutionResult(context, {
        status: 'skipped',
        steps: [{ stepId: step.id, status: 'skipped', durationMs: 0 }],
        artifacts: [],
        durationMs: 0,
        observability: createDryRunObservability(step.id),
      });
    }

    context.logger.info(`gradle-android: executing ${step.command.join(' ')}`);

    const commandResult = await this.runner.run(command, args, {
      cwd: step.workingDirectory,
      env: { ...process.env, ...(step.env ?? {}) },
      maxStdoutBytes: TOOLCHAIN_OUTPUT_LIMIT_BYTES,
      maxStderrBytes: TOOLCHAIN_OUTPUT_LIMIT_BYTES,
      commandId: step.id,
    });

    const durationMs = commandResult.durationMs;
    const succeeded = commandResult.exitCode === 0;
    const stdout = truncateToolchainOutput(commandResult.stdout);
    const stderr = truncateToolchainOutput(commandResult.stderr);

    if (succeeded) {
      const artifacts = this.extractArtifactPaths(stdout, step.workingDirectory);
      context.logger.info(`gradle-android: succeeded in ${durationMs}ms, artifacts: ${artifacts.length}`);
      return createToolchainExecutionResult(context, {
        status: 'succeeded',
        steps: [
          {
            stepId: step.id,
            status: 'succeeded',
            exitCode: commandResult.exitCode,
            stdout,
            stderr,
            durationMs,
          },
        ],
        artifacts,
        durationMs,
        observability: createCommandObservability(step.id, commandResult, durationMs),
      });
    }

    context.logger.error(`gradle-android: failed with exit code ${commandResult.exitCode} in ${durationMs}ms`);
    return createToolchainExecutionResult(context, {
      status: 'failed',
      steps: [
        {
          stepId: step.id,
          status: 'failed',
          exitCode: commandResult.exitCode,
          stdout,
          stderr,
          durationMs,
        },
      ],
      artifacts: [],
      durationMs,
      failure: {
        stepId: step.id,
        message: `Gradle exited with code ${commandResult.exitCode}`,
        cause: stderr.slice(0, 2000),
      },
      observability: createCommandObservability(step.id, commandResult, durationMs),
    });
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    const steps = await this.plan(context);
    const step = steps[0];
    if (step === undefined || step.expectedOutputs === undefined || step.expectedOutputs.length === 0) {
      return { status: 'valid', errors: [], missingArtifacts: [], unexpectedArtifacts: [] };
    }

    const missing: string[] = [];
    const errors: Array<{ path: string; message: string }> = [];

    for (const expected of step.expectedOutputs) {
      const fullPath = path.join(step.workingDirectory, expected);
      try {
        await fs.access(fullPath);
      } catch {
        missing.push(expected);
        errors.push({ path: expected, message: `Expected Gradle artifact not found: ${fullPath}` });
      }
    }

    return {
      status: missing.length === 0 ? 'valid' : 'invalid',
      errors,
      missingArtifacts: missing,
      unexpectedArtifacts: [],
    };
  }

  async preflight(_context: ToolchainAdapterContext): Promise<AdapterPreflightResult> {
    return createDefaultPreflightResult();
  }

  async classifyFailure(error: Error, _context: ToolchainAdapterContext): Promise<LifecycleFailureClassifier> {
    return createDefaultFailureClassifier(error, this.id);
  }

  private extractArtifactPaths(stdout: string, workingDirectory: string): string[] {
    const paths: string[] = [];

    const apkMatches = [...stdout.matchAll(APK_PATTERN)];
    for (const match of apkMatches) {
      if (match[1] !== undefined) {
        paths.push(path.isAbsolute(match[1]) ? match[1] : path.join(workingDirectory, match[1]));
      }
    }

    const aabMatches = [...stdout.matchAll(AAB_PATTERN)];
    for (const match of aabMatches) {
      if (match[1] !== undefined) {
        paths.push(path.isAbsolute(match[1]) ? match[1] : path.join(workingDirectory, match[1]));
      }
    }

    return [...new Set(paths)];
  }
}
