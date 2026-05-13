import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import { promisify } from 'node:util';
import type {
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainPlanStep,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ProductLifecyclePhase,
  ProductSurfaceType,
} from '../ToolchainAdapter.js';
import { ArtifactManifestGenerator, type ArtifactEntryInput } from '@ghatana/kernel-artifacts';

const execAsync = promisify(require('node:child_process').exec);

/**
 * Gradle Java service adapter
 */
export class GradleJavaServiceAdapter implements ToolchainAdapter {
  readonly id = 'gradle-java-service';
  readonly supportedPhases: ProductLifecyclePhase[] = ['dev', 'validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'worker', 'operator'];

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { phase, surfaceConfig } = context;
    const gradleModule = surfaceConfig.gradleModule as string;

    if (!gradleModule) {
      throw new Error('gradleModule is required for GradleJavaServiceAdapter');
    }

    const task = this.mapPhaseToTask(phase, surfaceConfig);
    const command = ['./gradlew', `${gradleModule}:${task}`];

    return [
      {
        id: `gradle-${phase}`,
        description: `Run Gradle ${task} for ${gradleModule}`,
        command,
        workingDirectory: this.resolveModulePath(gradleModule),
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
      const { stdout, stderr } = await execAsync(step.command.join(' '), {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
      });

      const durationMs = Date.now() - startTime;
      const artifactPaths = this.extractArtifacts(stdout, context);

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
            exitCode: 0,
            stdout: stdout.slice(0, 10000),
            stderr: stderr.slice(0, 10000),
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

  async validateOutputs(_context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    // TODO: Implement actual validation
    return {
      status: 'valid',
      errors: [],
      missingArtifacts: [],
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

  private resolveModulePath(gradleModule: string): string {
    // Convert Gradle module path to filesystem path
    return gradleModule.replace(/:/g, '/');
  }

  private extractArtifacts(_stdout: string, _context: ToolchainAdapterContext): string[] {
    // TODO: Parse Gradle output to extract artifact paths
    // For now, return empty array
    return [];
  }

  private async generateArtifactManifest(context: ToolchainAdapterContext, artifactPaths: string[]): Promise<void> {
    const generator = new ArtifactManifestGenerator();
    const artifacts: ArtifactEntryInput[] = artifactPaths.map((artifactPath, index) => ({
      id: `artifact-${index}`,
      path: artifactPath,
      metadata: {
        type: 'jar',
        version: context.metadata?.version || '1.0.0',
        gitCommit: context.metadata?.gitCommit,
        gitBranch: context.metadata?.gitBranch,
        timestamp: new Date().toISOString(),
        sizeBytes: 0, // TODO: Get actual file size
      },
      fingerprint: {
        algorithm: 'sha256',
        hash: '', // TODO: Calculate actual hash
      },
      expected: true,
    }));

    const manifest = generator.createManifest({
      productId: context.productId,
      phase: context.phase,
      surface: String(context.surface),
      artifacts,
    });

    const outputDir = context.outputDir || path.join(process.cwd(), 'build', 'artifacts');
    await fs.mkdir(outputDir, { recursive: true });
    const manifestPath = path.join(outputDir, `artifact-manifest-${context.phase}-${context.surface}.json`);
    await fs.writeFile(manifestPath, JSON.stringify(manifest, null, 2));

    context.logger.info(`Generated artifact manifest at ${manifestPath}`);
  }
}
