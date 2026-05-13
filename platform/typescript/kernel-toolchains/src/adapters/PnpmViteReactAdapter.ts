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
import { ArtifactManifestGenerator } from '../artifacts/ArtifactManifestGenerator.js';

/**
 * pnpm Vite React adapter
 */
export class PnpmViteReactAdapter implements ToolchainAdapter {
  readonly id = 'pnpm-vite-react';
  readonly supportedPhases: ProductLifecyclePhase[] = ['dev', 'validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['web'];

  private readonly repoRoot: string;
  private readonly commandRunner: CommandRunner;

  constructor(options: { repoRoot?: string; commandRunner?: CommandRunner } = {}) {
    this.repoRoot = options.repoRoot ?? process.cwd();
    this.commandRunner = options.commandRunner ?? new SpawnCommandRunner();
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const packagePath = surfaceConfig.packagePath as string;

    if (!packagePath) {
      throw new Error('packagePath is required for PnpmViteReactAdapter');
    }

    const script = this.mapPhaseToScript(phase, surfaceConfig);
    const packageDirectory = this.resolvePackageDirectory(packagePath);
    const command = ['pnpm', '--dir', packageDirectory, 'run', script];

    return [
      {
        id: `pnpm-${phase}`,
        description: `Run pnpm ${script} for ${packageDirectory}`,
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

    context.logger.info(`Executing pnpm: ${step.command.join(' ')}`);

    try {
      const commandResult = await this.commandRunner.run(step.command[0], step.command.slice(1), {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
      });

      const durationMs = commandResult.durationMs || Date.now() - startTime;
      const validation = await this.validateOutputs(context);

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
          artifacts: [],
          durationMs,
          failure: {
            stepId: step.id,
            message: validation.errors.map((error) => error.message).join('; ') || 'pnpm execution failed',
            cause: commandResult.stderr,
          },
        };
      }

      context.logger.info(`pnpm execution completed in ${durationMs}ms`);

      const artifacts = await this.extractArtifacts(context);
      
      // During package phase, generate artifact manifest
      if (context.phase === 'package') {
        await this.generateArtifactManifest(context, artifacts);
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
    } catch (error) {
      const durationMs = Date.now() - startTime;
      const execError = error as { message?: string; code?: number; stdout?: string; stderr?: string };
      context.logger.error(`pnpm execution failed: ${execError.message}`);

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
    const packageDirectory = path.join(this.repoRoot, this.resolvePackageDirectory(String(context.surfaceConfig.packagePath ?? context.surface.path)));

    if (!(await this.exists(path.join(packageDirectory, 'package.json')))) {
      missingArtifacts.push('package.json');
    }

    if (context.phase === 'build' || context.phase === 'package') {
      const distDirectory = path.join(packageDirectory, 'dist');
      if (!(await this.exists(distDirectory))) {
        missingArtifacts.push('dist');
      } else if (!(await this.exists(path.join(distDirectory, 'index.html')))) {
        missingArtifacts.push('dist/index.html');
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

  private mapPhaseToScript(phase: ProductLifecyclePhase, surfaceConfig: Record<string, unknown>): string {
    const scriptMap: Record<ProductLifecyclePhase, string> = {
      dev: (surfaceConfig.devScript as string) || 'dev',
      validate: (surfaceConfig.validateScript as string) || 'lint',
      test: (surfaceConfig.testScript as string) || 'test',
      build: (surfaceConfig.buildScript as string) || 'build',
      package: (surfaceConfig.packageScript as string) || 'build',
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
    return scriptMap[phase];
  }

  private async extractArtifacts(context: ToolchainAdapterContext): Promise<string[]> {
    if (context.phase !== 'build' && context.phase !== 'package') {
      return [];
    }

    const packageDirectory = this.resolvePackageDirectory(String(context.surfaceConfig.packagePath ?? context.surface.path));
    const distDirectory = path.join(this.repoRoot, packageDirectory, 'dist');
    if (!(await this.exists(distDirectory))) {
      return [];
    }

    return [path.relative(this.repoRoot, distDirectory).replace(/\\/g, '/')];
  }

  private resolvePackageDirectory(packagePath: string): string {
    return packagePath.endsWith('package.json') ? path.dirname(packagePath) : packagePath;
  }

  private async exists(targetPath: string): Promise<boolean> {
    try {
      await fs.access(targetPath);
      return true;
    } catch {
      return false;
    }
  }

  private async hasDockerfile(packageDirectory: string): Promise<boolean> {
    return this.exists(path.join(this.repoRoot, packageDirectory, 'Dockerfile'));
  }

  private async generateArtifactManifest(context: ToolchainAdapterContext, artifacts: string[]): Promise<void> {
    try {
      context.logger.debug(`Generating artifact manifest for package phase`);

      // Convert artifact paths to manifest entries with explicit type casting
      const artifactEntries: Array<{ path: string; type: 'static-web-bundle' | 'docker-image'; id?: string }> = artifacts.map((artifactPath) => ({
        path: artifactPath,
        type: 'static-web-bundle' as const,
        id: `web-bundle-${context.metadata?.buildNumber || 'unknown'}`,
      }));

      // Check for Dockerfile and add docker-image artifact if present
      const packageDirectory = this.resolvePackageDirectory(String(context.surfaceConfig.packagePath ?? context.surface.path));
      if (await this.hasDockerfile(packageDirectory)) {
        context.logger.debug(`Dockerfile detected, adding docker-image artifact`);
        const dockerfilePath = path.relative(this.repoRoot, path.join(this.repoRoot, packageDirectory, 'Dockerfile')).replace(/\\/g, '/');
        artifactEntries.push({
          path: dockerfilePath,
          type: 'docker-image' as const,
          id: `docker-${context.productId}-${context.surface.id || 'unknown'}-${context.metadata?.buildNumber || 'unknown'}`,
        });
      }

      const manifest = await ArtifactManifestGenerator.generateManifest(context, artifactEntries);

      // Write manifest to output directory
      const manifestPath = path.join(this.repoRoot, packageDirectory, 'build', 'artifact-manifest.json');

      await ArtifactManifestGenerator.writeManifest(manifest, manifestPath);
      context.logger.info(`Artifact manifest written to ${manifestPath}`);
    } catch (error) {
      context.logger.warn(`Failed to generate artifact manifest: ${error instanceof Error ? error.message : String(error)}`);
      // Non-blocking error - continue execution
    }
  }
}
