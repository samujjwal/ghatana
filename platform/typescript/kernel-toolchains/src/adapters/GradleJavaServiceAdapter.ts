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
} from '../ToolchainAdapter.js';
import { SpawnCommandRunner } from '../execution/SpawnCommandRunner.js';
import type { CommandRunner } from '../execution/CommandRunner.js';

/**
 * Gradle Java service adapter
 */
export class GradleJavaServiceAdapter implements ToolchainAdapter {
  readonly id = 'gradle-java-service';
  readonly supportedPhases: ProductLifecyclePhase[] = ['dev', 'validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'worker', 'operator'];

  private readonly repoRoot: string;
  private readonly commandRunner: CommandRunner;

  constructor(options: { repoRoot?: string; commandRunner?: CommandRunner } = {}) {
    this.repoRoot = options.repoRoot ?? process.cwd();
    this.commandRunner = options.commandRunner ?? new SpawnCommandRunner();
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const gradleModule = context.surfaceConfig.gradleModule;
    if (typeof gradleModule !== 'string' || gradleModule.length === 0) {
      throw new Error('gradleModule is required for GradleJavaServiceAdapter');
    }

    const task = this.mapPhaseToTask(context.phase, context.surfaceConfig);
    return [
      {
        id: `gradle-${context.phase}`,
        description: `Run Gradle ${task} for ${gradleModule}`,
        command: [process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew', `${gradleModule}:${task}`, '--no-daemon'],
        workingDirectory: this.repoRoot,
      },
    ];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const startedAt = Date.now();
    const [step] = await this.plan(context);

    if (context.dryRun) {
      context.logger.info(`[DRY-RUN] Would execute: ${step.command.join(' ')}`);
      return {
        status: 'skipped',
        steps: [{ stepId: step.id, status: 'skipped', durationMs: 0 }],
        artifacts: [],
        durationMs: 0,
      };
    }

    const commandResult = await this.commandRunner.run(step.command[0], step.command.slice(1), {
      cwd: step.workingDirectory,
      env: { ...process.env, ...step.env },
    });

    const durationMs = commandResult.durationMs || Date.now() - startedAt;

    if (commandResult.exitCode !== 0) {
      return {
        status: 'failed',
        steps: [
          {
            stepId: step.id,
            status: 'failed',
            exitCode: commandResult.exitCode,
            stdout: commandResult.stdout.slice(0, 10000),
            stderr: commandResult.stderr.slice(0, 10000),
            durationMs,
          },
        ],
        artifacts: [],
        durationMs,
        failure: {
          stepId: step.id,
          message: 'Gradle execution failed',
          ...(commandResult.stderr ? { cause: commandResult.stderr } : {}),
        },
      };
    }

    // dev phase: the process is long-running; write processes.json and return immediately.
    // Do NOT validate dist/jar outputs for dev — the server runs in the foreground.
    if (context.phase === 'dev') {
      await this.writeProcessesJson(context, step.id, durationMs);
      return {
        status: 'succeeded',
        steps: [
          {
            stepId: step.id,
            status: 'succeeded',
            exitCode: commandResult.exitCode,
            stdout: commandResult.stdout.slice(0, 10000),
            stderr: commandResult.stderr.slice(0, 10000),
            durationMs,
          },
        ],
        artifacts: [],
        durationMs,
      };
    }

    const validation = await this.validateOutputs(context);
    const artifacts = await this.extractArtifacts(context);

    const failed = validation.status === 'invalid';
    if (failed) {
      return {
        status: 'failed',
        steps: [
          {
            stepId: step.id,
            status: 'failed',
            exitCode: commandResult.exitCode,
            stdout: commandResult.stdout.slice(0, 10000),
            stderr: commandResult.stderr.slice(0, 10000),
            durationMs,
          },
        ],
        artifacts,
        durationMs,
        failure: {
          stepId: step.id,
          message: validation.errors.map((error) => error.message).join('; ') || 'Output validation failed',
          ...(commandResult.stderr ? { cause: commandResult.stderr } : {}),
        },
      };
    }

    return {
      status: 'succeeded',
      steps: [
        {
          stepId: step.id,
          status: 'succeeded',
          exitCode: commandResult.exitCode,
          stdout: commandResult.stdout.slice(0, 10000),
          stderr: commandResult.stderr.slice(0, 10000),
          durationMs,
        },
      ],
      artifacts,
      durationMs,
    };
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    // dev phase: outputs are a running process — nothing to validate on disk.
    if (context.phase === 'dev') {
      return { status: 'valid', errors: [], missingArtifacts: [], unexpectedArtifacts: [] };
    }

    const expectedOutputs = this.getExpectedOutputsForPhase(context);
    const fallbackExpectedOutputs = this.getFallbackExpectedOutputs(context);
    const outputPatterns = expectedOutputs.length > 0 ? expectedOutputs : fallbackExpectedOutputs;

    const missingArtifacts: string[] = [];
    for (const pattern of outputPatterns) {
      if (!(await this.patternExists(pattern))) {
        missingArtifacts.push(pattern);
      }
    }

    return {
      status: missingArtifacts.length === 0 ? 'valid' : 'invalid',
      errors: missingArtifacts.map((pattern) => ({
        path: 'outputs',
        message: `Missing expected output: ${pattern}`,
      })),
      missingArtifacts,
      unexpectedArtifacts: [],
    };
  }

  /** Write a processes.json to outputDir for the dev phase. */
  private async writeProcessesJson(
    context: ToolchainAdapterContext,
    stepId: string,
    durationMs: number,
  ): Promise<void> {
    const outputDir = context.outputDir ?? path.join(this.repoRoot, '.kernel', 'out', 'dev');
    await fs.mkdir(outputDir, { recursive: true });

    const gradleModule = context.surfaceConfig.gradleModule ?? context.surface.path;
    const processEntry = {
      schemaVersion: '1.0.0',
      productId: context.productId,
      surface: context.surface.type,
      adapter: 'gradle-java-service',
      startedAt: new Date().toISOString(),
      stepId,
      durationMs,
      gradleModule,
      task: this.mapPhaseToTask('dev', context.surfaceConfig),
      note: 'bootRun completes when the server terminates. PID not captured in batch mode.',
    };

    await fs.writeFile(
      path.join(outputDir, 'processes.json'),
      JSON.stringify(processEntry, null, 2),
      'utf-8',
    );
  }

  private mapPhaseToTask(phase: ProductLifecyclePhase, surfaceConfig: Record<string, unknown>): string {
    switch (phase) {
      case 'dev':
        return String(surfaceConfig.devTask ?? 'bootRun');
      case 'validate':
        return String(surfaceConfig.validateTask ?? 'check');
      case 'test':
        return String(surfaceConfig.testTask ?? 'test');
      case 'build':
        return String(surfaceConfig.buildTask ?? 'build');
      case 'package':
        return String(surfaceConfig.packageTask ?? 'assemble');
      default:
        throw new Error(`GradleJavaServiceAdapter does not support phase ${phase}`);
    }
  }

  private getExpectedOutputsForPhase(context: ToolchainAdapterContext): string[] {
    const configured = context.surfaceConfig.expectedOutputs;
    if (!configured || typeof configured !== 'object') {
      return [];
    }

    const byPhase = configured as Record<string, unknown>;
    const forPhase = byPhase[context.phase];
    if (!Array.isArray(forPhase)) {
      return [];
    }

    return forPhase.filter((value): value is string => typeof value === 'string' && value.length > 0);
  }

  private getFallbackExpectedOutputs(context: ToolchainAdapterContext): string[] {
    const surfacePath = this.resolveSurfacePath(context);
    if (context.phase === 'build' || context.phase === 'package') {
      return [path.join(surfacePath, 'build', 'libs', '*.jar').replace(/\\/g, '/')];
    }
    if (context.phase === 'test' || context.phase === 'validate') {
      return [
        path.join(surfacePath, 'build', 'reports', 'tests').replace(/\\/g, '/'),
        path.join(surfacePath, 'build', 'test-results', 'test').replace(/\\/g, '/'),
      ];
    }
    return [];
  }

  private async extractArtifacts(context: ToolchainAdapterContext): Promise<string[]> {
    if (context.phase !== 'build' && context.phase !== 'package') {
      return [];
    }

    const outputPatterns = this.getExpectedOutputsForPhase(context);
    const artifactCandidates = outputPatterns.length > 0 ? outputPatterns : [
      path.join(this.resolveSurfacePath(context), 'build', 'libs', '*.jar').replace(/\\/g, '/'),
    ];

    const artifacts = new Set<string>();
    for (const pattern of artifactCandidates) {
      const matches = await this.resolveMatches(pattern);
      for (const match of matches) {
        artifacts.add(path.relative(this.repoRoot, match).replace(/\\/g, '/'));
      }
    }

    return [...artifacts];
  }

  private async patternExists(pattern: string): Promise<boolean> {
    const matches = await this.resolveMatches(pattern);
    return matches.length > 0;
  }

  private async resolveMatches(pattern: string): Promise<string[]> {
    const absolutePattern = path.isAbsolute(pattern) ? pattern : path.join(this.repoRoot, pattern);

    if (!absolutePattern.includes('*')) {
      return (await this.exists(absolutePattern)) ? [absolutePattern] : [];
    }

    const normalizedPattern = absolutePattern.replace(/\\/g, '/');
    const firstWildcardIndex = normalizedPattern.indexOf('*');
    const prefix = normalizedPattern.slice(0, firstWildcardIndex);
    const searchRoot = path.dirname(prefix);

    if (!(await this.exists(searchRoot))) {
      return [];
    }

    const regex = new RegExp(`^${normalizedPattern
      .replace(/[.+?^${}()|[\]\\]/g, '\\$&')
      .replace(/\*/g, '[^/]*')}$`);

    const discovered = await this.walkFiles(searchRoot);
    return discovered.filter((candidate) => regex.test(candidate.replace(/\\/g, '/')));
  }

  private resolveSurfacePath(context: ToolchainAdapterContext): string {
    const configuredSource = context.surfaceConfig.source;
    const relativePath = typeof configuredSource === 'string' && configuredSource.length > 0
      ? configuredSource
      : context.surface.path;
    return path.join(this.repoRoot, relativePath);
  }

  private async walkFiles(directoryPath: string): Promise<string[]> {
    const entries = await fs.readdir(directoryPath, { withFileTypes: true });
    const nested = await Promise.all(entries.map(async (entry) => {
      const fullPath = path.join(directoryPath, entry.name);
      if (entry.isDirectory()) {
        return this.walkFiles(fullPath);
      }
      return [fullPath];
    }));
    return nested.flat();
  }

  private async exists(targetPath: string): Promise<boolean> {
    try {
      await fs.access(targetPath);
      return true;
    } catch {
      return false;
    }
  }
}
