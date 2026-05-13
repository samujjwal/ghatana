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
import { ArtifactManifestGenerator } from '../artifacts/ArtifactManifestGenerator.js';
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
    const { phase, surfaceConfig } = context;
    const gradleModule = surfaceConfig.gradleModule as string;

    if (!gradleModule) {
      throw new Error('gradleModule is required for GradleJavaServiceAdapter');
    }

    const task = this.mapPhaseToTask(phase, surfaceConfig);
    const command = [process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew', `${gradleModule}:${task}`, '--no-daemon'];

    return [
      {
        id: `gradle-${phase}`,
        description: `Run Gradle ${task} for ${gradleModule}`,
        command,
        workingDirectory: this.repoRoot,
      },
    ];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const startTime = Date.now();
    const plan = await this.plan(context);
    const step = plan[0];

    if (context.dryRun) {
      context.logger.info(`[DRY-RUN] Would execute: ${step.command.join(' ')}`);
      return {
        status: 'skipped',
        steps: [
          {
            stepId: step.id,
            status: 'skipped',
            durationMs: 0,
          },
        ],
        artifacts: [],
        durationMs: 0,
      };
    }

    context.logger.info(`Executing Gradle: ${step.command.join(' ')}`);

    try {
      const commandResult = await this.commandRunner.run(step.command[0], step.command.slice(1), {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
      });

      const durationMs = commandResult.durationMs || Date.now() - startTime;
      const validation = await this.validateOutputs(context);
      const artifactPaths = await this.extractArtifacts(context);

      if (commandResult.exitCode !== 0 || validation.status === 'invalid') {
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
          artifacts: artifactPaths,
          durationMs,
          failure: {
            stepId: step.id,
            message: validation.errors.map((error) => error.message).join('; ') || 'Gradle execution failed',
            cause: commandResult.stderr,
          },
        };
      }

      context.logger.info(`Gradle execution completed in ${durationMs}ms`);

      // Generate artifact manifest for build phase
      if (context.phase === 'build' && artifactPaths.length > 0) {
        await this.generateArtifactManifest(context, artifactPaths);
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
        artifacts: artifactPaths,
        durationMs,
      };
    } catch (error) {
      const durationMs = Date.now() - startTime;
      const execError = error as { message?: string; code?: number; stdout?: string; stderr?: string };
      context.logger.error(`Gradle execution failed: ${execError.message}`);

      const failedResult: ToolchainExecutionResult = {
        status: 'failed',
        steps: [
          {
            stepId: step.id,
            status: 'failed',
            exitCode: execError.code || 1,
            stdout: execError.stdout?.slice(0, 10000) || '',
            stderr: execError.stderr?.slice(0, 10000) || '',
            durationMs,
          },
        ],
        artifacts: [],
        durationMs,
      };

      if (execError.stderr) {
        failedResult.failure = {
          stepId: step.id,
          message: execError.message || 'Unknown error',
          cause: execError.stderr,
        };
      }

      return failedResult;
    }
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    const missingArtifacts: string[] = [];
    const errors: { path: string; message: string }[] = [];
    const surfacePath = this.resolveSurfacePath(context);

    if (context.phase === 'build' || context.phase === 'package') {
      const jarFiles = await this.findFiles(path.join(surfacePath, 'build', 'libs'), (entryName) => entryName.endsWith('.jar'));
      if (jarFiles.length === 0) {
        missingArtifacts.push('build/libs/*.jar');
      }
    }

    if (context.phase === 'test' || context.phase === 'validate') {
      const reportsDir = path.join(surfacePath, 'build', 'reports', 'tests');
      const testResultsDir = path.join(surfacePath, 'build', 'test-results', 'test');
      if (!(await this.exists(reportsDir)) && !(await this.exists(testResultsDir))) {
        missingArtifacts.push('build/reports/tests or build/test-results/test');
      }
    }

    if (missingArtifacts.length > 0) {
      errors.push({
        path: 'outputs',
        message: `Missing expected outputs: ${missingArtifacts.join(', ')}`,
      });
    }

    return {
      status: missingArtifacts.length === 0 ? 'valid' : 'invalid',
      errors,
      missingArtifacts,
      unexpectedArtifacts: [],
    };
  }

  private mapPhaseToTask(phase: ProductLifecyclePhase, surfaceConfig: Record<string, unknown>): string {
    const taskMap: Record<ProductLifecyclePhase, string> = {
      dev: (surfaceConfig.devTask as string) || 'bootRun',
      validate: (surfaceConfig.validateTask as string) || 'check',
      test: (surfaceConfig.testTask as string) || 'test',
      build: (surfaceConfig.buildTask as string) || 'build',
      package: (surfaceConfig.packageTask as string) || 'assemble',
      release: 'build',
      deploy: 'build',
      verify: 'test',
      promote: 'build',
      rollback: 'build',
      operate: 'test',
      retire: 'build',
      create: 'build',
      bootstrap: 'build',
    };
    return taskMap[phase];
  }

  private async extractArtifacts(context: ToolchainAdapterContext): Promise<string[]> {
    if (context.phase !== 'build' && context.phase !== 'package') {
      return [];
    }

    const jarFiles = await this.findFiles(
      path.join(this.resolveSurfacePath(context), 'build', 'libs'),
      (entryName) => entryName.endsWith('.jar'),
    );

    return jarFiles.map((filePath) => path.relative(this.repoRoot, filePath).replace(/\\/g, '/'));
  }

  private async generateArtifactManifest(context: ToolchainAdapterContext, artifactPaths: string[]): Promise<void> {
    try {
      context.logger.debug(`Generating artifact manifest for ${context.phase} phase`);

      // Convert artifact paths to manifest entries
      const artifactEntries = artifactPaths.map((artifactPath) => ({
        path: artifactPath,
        type: 'jar' as const,
        id: `${path.basename(artifactPath)}-${context.phase}`,
      }));

      const manifest = await ArtifactManifestGenerator.generateManifest(context, artifactEntries);

      // Write manifest to build directory
      const surfacePath = this.resolveSurfacePath(context);
      const manifestPath = path.join(surfacePath, 'build', 'artifact-manifest.json');

      await ArtifactManifestGenerator.writeManifest(manifest, manifestPath);
      context.logger.info(`Artifact manifest written to ${manifestPath}`);
    } catch (error) {
      context.logger.warn(`Failed to generate artifact manifest: ${error instanceof Error ? error.message : String(error)}`);
      // Non-blocking error - continue execution
    }
  }

  private resolveSurfacePath(context: ToolchainAdapterContext): string {
    const configuredSource = context.surfaceConfig.source;
    const relativePath = typeof configuredSource === 'string' && configuredSource.length > 0
      ? configuredSource
      : context.surface.path;
    return path.join(this.repoRoot, relativePath);
  }

  private async exists(targetPath: string): Promise<boolean> {
    try {
      await fs.access(targetPath);
      return true;
    } catch {
      return false;
    }
  }

  private async findFiles(directoryPath: string, matcher: (entryName: string) => boolean): Promise<string[]> {
    if (!(await this.exists(directoryPath))) {
      return [];
    }

    const entries = await fs.readdir(directoryPath, { withFileTypes: true });
    return entries
      .filter((entry) => entry.isFile() && matcher(entry.name))
      .map((entry) => path.join(directoryPath, entry.name));
  }
}
