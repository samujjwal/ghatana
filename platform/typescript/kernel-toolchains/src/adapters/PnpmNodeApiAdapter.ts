import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import type {
  AdapterPreflightResult,
  LifecycleFailureClassifier,
  ProductLifecyclePhase,
  ProductSurfaceType,
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ToolchainPlanStep,
} from '../ToolchainAdapter.js';
import { SpawnCommandRunner } from '../execution/SpawnCommandRunner.js';
import type { CommandRunner } from '../execution/CommandRunner.js';
import {
  createEnvironmentBlockedClassifier,
  defaultTimeoutMs,
  executePolyglotPlan,
  exists,
  extractExistingArtifacts,
  getConfiguredExpectedOutputs,
  validateExpectedOutputs,
  writePolyglotArtifactManifest,
} from './PolyglotAdapterSupport.js';

/**
 * pnpm Node API adapter for TypeScript backend services and workers.
 */
export class PnpmNodeApiAdapter implements ToolchainAdapter {
  readonly id = 'pnpm-node-api';
  readonly supportedPhases: ProductLifecyclePhase[] = ['dev', 'validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'worker'];

  private readonly repoRoot: string;
  private readonly commandRunner: CommandRunner;

  constructor(options: { repoRoot?: string; commandRunner?: CommandRunner } = {}) {
    this.repoRoot = options.repoRoot ?? process.cwd();
    this.commandRunner = options.commandRunner ?? new SpawnCommandRunner();
  }

  async preflight(context: ToolchainAdapterContext): Promise<AdapterPreflightResult> {
    const checkedAt = new Date().toISOString();
    const packageDirectory = this.resolvePackageDirectory(String(context.surfaceConfig.packagePath ?? context.surface.path));
    const packageJsonPath = path.join(this.absolutePackageDirectory(packageDirectory), 'package.json');
    const packageJsonExists = await exists(packageJsonPath);
    const checks: AdapterPreflightResult['checks'] = [
      {
        checkId: 'node-package-json',
        checkName: 'Node package manifest',
        status: packageJsonExists ? 'passed' : 'failed',
        message: packageJsonExists ? `Found ${packageJsonPath}` : `Missing ${packageJsonPath}`,
        ...(packageJsonExists ? {} : { severity: 'critical', remediation: ['Add package.json or configure packagePath'] }),
        checkedAt,
      },
    ];
    try {
      const result = await this.commandRunner.run('pnpm', ['--version'], { cwd: this.repoRoot, timeoutMs: 10_000 });
      checks.push({
        checkId: 'pnpm-version',
        checkName: 'pnpm executable',
        status: result.exitCode === 0 ? 'passed' : 'failed',
        message: result.exitCode === 0 ? result.stdout.trim() : result.stderr.trim(),
        ...(result.exitCode === 0 ? {} : { severity: 'critical', remediation: ['Install pnpm'] }),
        checkedAt,
      });
    } catch (error) {
      checks.push({
        checkId: 'pnpm-version',
        checkName: 'pnpm executable',
        status: 'failed',
        message: error instanceof Error ? error.message : 'pnpm executable check failed',
        severity: 'critical',
        remediation: ['Install pnpm'],
        checkedAt,
      });
    }
    const blockingIssues = checks.filter((check) => check.status === 'failed').map((check) => check.message);
    return { status: blockingIssues.length === 0 ? 'ready' : 'blocked', checks, blockingIssues, warnings: [] };
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const packagePath = context.surfaceConfig.packagePath;
    if (typeof packagePath !== 'string' || packagePath.length === 0) {
      throw new Error('packagePath is required for PnpmNodeApiAdapter');
    }
    const packageDirectory = this.resolvePackageDirectory(packagePath);
    const packageJson = await this.readPackageJson(packageDirectory);
    const script = this.mapPhaseToScript(context.phase, context.surfaceConfig, packageJson);
    if (!hasPackageScript(packageJson, script)) {
      throw new Error(`script-not-found: package "${packageDirectory}" does not define script "${script}"`);
    }
    return [
      {
        id: `pnpm-node-${context.phase}`,
        description: `Run pnpm ${script} for ${packageDirectory}`,
        command: ['pnpm', '--dir', packageDirectory, 'run', script],
        workingDirectory: this.repoRoot,
      },
    ];
  }

  async execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult> {
    const steps = await this.plan(context);
    return executePolyglotPlan({
      adapterId: this.id,
      context,
      commandRunner: this.commandRunner,
      steps,
      validateOutputs: () => this.validateOutputs(context),
      extractArtifacts: () => this.extractArtifacts(context),
      writeArtifactManifest: (artifacts) => writePolyglotArtifactManifest(
        this.repoRoot,
        context,
        this.id,
        'node-bundle',
        artifacts,
      ),
      timeoutMs: this.resolveTimeoutMs(context),
    });
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    if (context.phase === 'dev') {
      return { status: 'valid', errors: [], missingArtifacts: [], unexpectedArtifacts: [] };
    }
    const configured = getConfiguredExpectedOutputs(context);
    const outputs = configured.length > 0 ? configured : this.fallbackOutputs(context);
    return validateExpectedOutputs(this.repoRoot, outputs);
  }

  async classifyFailure(error: Error, _context: ToolchainAdapterContext): Promise<LifecycleFailureClassifier> {
    return createEnvironmentBlockedClassifier(error, this.id, 'pnpm');
  }

  private mapPhaseToScript(
    phase: ProductLifecyclePhase,
    surfaceConfig: Record<string, unknown>,
    packageJson: PackageJson,
  ): string {
    switch (phase) {
      case 'dev':
        return String(surfaceConfig.devScript ?? 'dev');
      case 'validate':
        if (surfaceConfig.validateScript !== undefined) {
          return String(surfaceConfig.validateScript);
        }
        return hasPackageScript(packageJson, 'typecheck') ? 'typecheck' : 'lint';
      case 'test':
        return String(surfaceConfig.testScript ?? 'test');
      case 'build':
        return String(surfaceConfig.buildScript ?? 'build');
      case 'package':
        return String(surfaceConfig.packageScript ?? 'build');
      default:
        throw new Error(`PnpmNodeApiAdapter does not support phase ${phase}`);
    }
  }

  private fallbackOutputs(context: ToolchainAdapterContext): readonly string[] {
    const packageDirectory = this.resolvePackageDirectory(String(context.surfaceConfig.packagePath ?? context.surface.path));
    if (context.phase === 'build' || context.phase === 'package') {
      return [path.join(packageDirectory, 'dist').replace(/\\/g, '/')];
    }
    return [path.join(packageDirectory, 'package.json').replace(/\\/g, '/')];
  }

  private async extractArtifacts(context: ToolchainAdapterContext): Promise<readonly string[]> {
    if (context.phase !== 'build' && context.phase !== 'package') {
      return [];
    }
    const configured = getConfiguredExpectedOutputs(context);
    const candidates = configured.length > 0 ? configured : this.fallbackOutputs(context);
    return extractExistingArtifacts(this.repoRoot, candidates);
  }

  private resolvePackageDirectory(packagePath: string): string {
    return packagePath.endsWith('package.json') ? path.dirname(packagePath) : packagePath;
  }

  private absolutePackageDirectory(packageDirectory: string): string {
    return path.isAbsolute(packageDirectory) ? packageDirectory : path.join(this.repoRoot, packageDirectory);
  }

  private async readPackageJson(packageDirectory: string): Promise<PackageJson> {
    const packageJsonPath = path.join(this.absolutePackageDirectory(packageDirectory), 'package.json');
    let content: string;
    try {
      content = await fs.readFile(packageJsonPath, 'utf-8');
    } catch (error) {
      if (isNodeFileNotFound(error)) {
        throw new Error(`package-path-not-found: ${packageJsonPath}`);
      }
      throw error;
    }
    const parsed: unknown = JSON.parse(content);
    if (!isPackageJson(parsed)) {
      throw new Error(`invalid-package-json: ${packageJsonPath}`);
    }
    return parsed;
  }

  private resolveTimeoutMs(context: ToolchainAdapterContext): number {
    const configured = context.phaseConfig.timeoutMs ?? context.surfaceConfig.timeoutMs;
    return typeof configured === 'number' && Number.isFinite(configured) && configured > 0
      ? configured
      : defaultTimeoutMs(context.phase);
  }
}

interface PackageJson {
  readonly scripts?: Record<string, string>;
}

function isPackageJson(value: unknown): value is PackageJson {
  return typeof value === 'object' && value !== null;
}

function hasPackageScript(packageJson: PackageJson, script: string): boolean {
  return typeof packageJson.scripts?.[script] === 'string' && packageJson.scripts[script].trim().length > 0;
}

function isNodeFileNotFound(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'code' in error &&
    (error as { readonly code?: unknown }).code === 'ENOENT'
  );
}
