import { promises as fs } from 'node:fs';
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

const XCODE_APP_PATTERN = /BUILD_DIR\s*=\s*(.+)/;
const XCODE_ARCHIVE_PATTERN = /Archive\s+Succeeded\s+\((.+\.xcarchive)\)/;
const IPA_EXTENSION = 'ipa';
const APP_EXTENSION = 'app';

/**
 * Xcode iOS adapter for iOS app builds.
 * Uses SpawnCommandRunner for safe, non-shell subprocess execution with
 * bounded output capture and structured observability.
 */
export class XcodeIosAdapter implements ToolchainAdapter {
  readonly id = 'xcode-ios';
  readonly supportedPhases: ProductLifecyclePhase[] = ['dev', 'validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['mobile-ios'];

  private readonly runner: CommandRunner;

  constructor(runner?: CommandRunner) {
    this.runner = runner ?? new SpawnCommandRunner();
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const xcodeProject = typeof surfaceConfig.xcodeProject === 'string' ? surfaceConfig.xcodeProject : undefined;
    const scheme = typeof surfaceConfig.scheme === 'string' ? surfaceConfig.scheme : 'App';
    const configuration = phase === 'dev' ? 'Debug' : 'Release';
    const workingDirectory = context.outputDir ?? process.cwd();

    const args: string[] = [
      '-scheme', scheme,
      '-configuration', configuration,
      'clean', 'build',
    ];

    if (xcodeProject !== undefined) {
      args.unshift('-project', xcodeProject);
    } else {
      args.unshift('-workspace', '.');
    }

    return [
      {
        id: `xcode-${phase}`,
        description: `Build iOS app ${scheme} (${configuration})`,
        command: ['xcodebuild', ...args],
        workingDirectory,
        expectedOutputs: phase === 'package' || phase === 'build'
          ? [`${scheme}.${phase === 'package' ? IPA_EXTENSION : APP_EXTENSION}`]
          : [],
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
        failure: { stepId: 'xcode-plan', message: 'Xcode iOS adapter produced no plan steps' },
      });
    }

    const [command, ...args] = step.command;
    if (command === undefined) {
      return createToolchainExecutionResult(context, {
        status: 'failed',
        steps: [],
        artifacts: [],
        durationMs: 0,
        failure: { stepId: step.id, message: 'Xcode iOS adapter plan step has no command' },
      });
    }

    if (context.dryRun) {
      context.logger.info(`[DRY-RUN] xcode-ios: would execute: ${step.command.join(' ')}`);
      return createToolchainExecutionResult(context, {
        status: 'skipped',
        steps: [{ stepId: step.id, status: 'skipped', durationMs: 0 }],
        artifacts: [],
        durationMs: 0,
        observability: createDryRunObservability(step.id),
      });
    }

    context.logger.info(`xcode-ios: executing ${step.command.join(' ')}`, );

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
      const artifacts = this.extractArtifactPaths(stdout);
      context.logger.info(`xcode-ios: succeeded in ${durationMs}ms, artifacts: ${artifacts.length}`);
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

    context.logger.error(`xcode-ios: failed with exit code ${commandResult.exitCode} in ${durationMs}ms`);
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
        message: `xcodebuild exited with code ${commandResult.exitCode}`,
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
      const fullPath = `${step.workingDirectory}/${expected}`;
      try {
        await fs.access(fullPath);
      } catch {
        missing.push(expected);
        errors.push({ path: expected, message: `Expected Xcode artifact not found: ${fullPath}` });
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

  private extractArtifactPaths(stdout: string): string[] {
    const paths: string[] = [];

    const archiveMatch = XCODE_ARCHIVE_PATTERN.exec(stdout);
    if (archiveMatch?.[1] !== undefined) {
      paths.push(archiveMatch[1]);
    }

    const buildDirMatch = XCODE_APP_PATTERN.exec(stdout);
    if (buildDirMatch?.[1] !== undefined) {
      const buildDir = buildDirMatch[1].trim();
      if (buildDir.endsWith(`.${APP_EXTENSION}`) || buildDir.endsWith(`.${IPA_EXTENSION}`)) {
        paths.push(buildDir);
      }
    }

    return paths;
  }
}
