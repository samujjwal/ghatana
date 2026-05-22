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
  configuredString,
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
 * Python environment strategy for dependency management and virtualization.
 */
type PythonEnvironment = 'venv' | 'uv' | 'poetry' | 'system';

/**
 * Python pyproject adapter for Kernel-managed Python services, workers, and libraries.
 */
export class PythonPyprojectAdapter implements ToolchainAdapter {
  readonly id = 'python-pyproject';
  readonly supportedPhases: ProductLifecyclePhase[] = ['validate', 'test', 'build', 'package'];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = ['backend-api', 'worker', 'sdk'];

  private readonly repoRoot: string;
  private readonly commandRunner: CommandRunner;
  private pythonEnvironmentCache: PythonEnvironment | null = null;

  constructor(options: { repoRoot?: string; commandRunner?: CommandRunner } = {}) {
    this.repoRoot = options.repoRoot ?? process.cwd();
    this.commandRunner = options.commandRunner ?? new SpawnCommandRunner();
  }

  async preflight(context: ToolchainAdapterContext): Promise<AdapterPreflightResult> {
    const checkedAt = new Date().toISOString();
    const pyprojectPath = this.resolvePyprojectPath(context);
    const manifestExists = await exists(pyprojectPath);
    
    // Detect Python environment strategy
    this.pythonEnvironmentCache = await this.detectPythonEnvironment(context);
    
    const checks: AdapterPreflightResult['checks'] = [
      {
        checkId: 'pyproject-manifest',
        checkName: 'pyproject manifest',
        status: manifestExists ? 'passed' : 'failed',
        message: manifestExists ? `Found ${pyprojectPath}` : `Missing ${pyprojectPath}`,
        ...(manifestExists ? {} : { severity: 'critical', remediation: ['Add pyproject.toml or configure pyprojectPath'] }),
        checkedAt,
      },
    ];
    try {
      const result = await this.commandRunner.run(this.pythonCommand(context), ['--version'], {
        cwd: this.repoRoot,
        timeoutMs: 10_000,
      });
      checks.push({
        checkId: 'python-version',
        checkName: 'Python executable',
        status: result.exitCode === 0 ? 'passed' : 'failed',
        message: (result.stdout || result.stderr).trim(),
        ...(result.exitCode === 0 ? {} : { severity: 'critical', remediation: ['Install Python 3'] }),
        checkedAt,
      });
    } catch (error) {
      checks.push({
        checkId: 'python-version',
        checkName: 'Python executable',
        status: 'failed',
        message: error instanceof Error ? error.message : 'Python executable check failed',
        severity: 'critical',
        remediation: ['Install Python 3'],
        checkedAt,
      });
    }

    // Check for mypy and ruff if configured
    if (context.phaseConfig.enableLinting !== false) {
      try {
        const mypyResult = await this.commandRunner.run(this.pythonCommand(context), ['-m', 'mypy', '--version'], {
          cwd: this.repoRoot,
          timeoutMs: 10_000,
        });
        checks.push({
          checkId: 'mypy-version',
          checkName: 'mypy type checker',
          status: mypyResult.exitCode === 0 ? 'passed' : 'failed',
          message: mypyResult.exitCode === 0 ? mypyResult.stdout.trim() : mypyResult.stderr.trim(),
          ...(mypyResult.exitCode === 0 ? {} : { severity: 'low', remediation: ['Install mypy: pip install mypy'] }),
          checkedAt,
        });
      } catch {
        checks.push({
          checkId: 'mypy-version',
          checkName: 'mypy type checker',
          status: 'failed',
          message: 'mypy not found',
          severity: 'low',
          remediation: ['Install mypy: pip install mypy'],
          checkedAt,
        });
      }

      try {
        const ruffResult = await this.commandRunner.run('ruff', ['--version'], {
          cwd: this.repoRoot,
          timeoutMs: 10_000,
        });
        checks.push({
          checkId: 'ruff-version',
          checkName: 'ruff linter',
          status: ruffResult.exitCode === 0 ? 'passed' : 'failed',
          message: ruffResult.exitCode === 0 ? ruffResult.stdout.trim() : ruffResult.stderr.trim(),
          ...(ruffResult.exitCode === 0 ? {} : { severity: 'low', remediation: ['Install ruff: pip install ruff'] }),
          checkedAt,
        });
      } catch {
        checks.push({
          checkId: 'ruff-version',
          checkName: 'ruff linter',
          status: 'failed',
          message: 'ruff not found',
          severity: 'low',
          remediation: ['Install ruff: pip install ruff'],
          checkedAt,
        });
      }
    }

    const blockingIssues = checks.filter((check) => check.status === 'failed' && check.severity === 'critical').map((check) => check.message);
    const warnings = checks.filter((check) => check.status === 'failed' && check.severity === 'low').map((check) => check.message);
    return { status: blockingIssues.length === 0 ? 'ready' : 'blocked', checks, blockingIssues, warnings };
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const packageDirectory = this.resolvePackageDirectory(context);
    if (!(await exists(this.resolvePyprojectPath(context)))) {
      throw new Error(`pyproject-not-found: ${path.join(packageDirectory, 'pyproject.toml')}`);
    }
    return this.mapPhaseToCommands(context).map((command, index) => ({
      id: `python-${context.phase}-${index + 1}`,
      description: `Run ${command.join(' ')} for ${packageDirectory}`,
      command,
      workingDirectory: packageDirectory,
      ...(index === 0 ? {} : { dependsOn: [`python-${context.phase}-${index}`] }),
    }));
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
        this.artifactType(context),
        artifacts,
      ),
      timeoutMs: this.resolveTimeoutMs(context),
    });
  }

  async validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult> {
    const configured = getConfiguredExpectedOutputs(context);
    const outputs = configured.length > 0 ? configured : this.fallbackOutputs(context);
    return validateExpectedOutputs(this.repoRoot, outputs);
  }

  async classifyFailure(error: Error, _context: ToolchainAdapterContext): Promise<LifecycleFailureClassifier> {
    return createEnvironmentBlockedClassifier(error, this.id, 'python');
  }

  private mapPhaseToCommands(context: ToolchainAdapterContext): readonly string[][] {
    const python = this.pythonCommand(context);
    const env = this.pythonEnvironmentCache ?? 'system';
    switch (context.phase) {
      case 'validate':
        const validateCommands = this.configuredCommands(context, 'validateCommands');
        if (validateCommands) {
          return validateCommands;
        }
        const baseValidate = [[python, '-m', 'compileall', this.sourceDirectory(context)]];
        if (context.phaseConfig.enableLinting !== false) {
          return [
            ...baseValidate,
            [python, '-m', 'mypy', this.sourceDirectory(context)],
            ['ruff', 'check', this.sourceDirectory(context)],
          ];
        }
        return baseValidate;
      case 'test':
        return this.configuredCommands(context, 'testCommands') ?? [[python, '-m', 'pytest']];
      case 'build':
      case 'package':
        const buildCommands = this.configuredCommands(context, 'buildCommands');
        if (buildCommands) {
          return buildCommands;
        }
        // Use environment-specific build commands
        if (env === 'poetry') {
          return [['poetry', 'build']];
        }
        if (env === 'uv') {
          return [['uv', 'build']];
        }
        return [[python, '-m', 'build']];
      default:
        throw new Error(`PythonPyprojectAdapter does not support phase ${context.phase}`);
    }
  }

  private configuredCommands(
    context: ToolchainAdapterContext,
    key: string,
  ): readonly string[][] | undefined {
    const value = context.surfaceConfig[key];
    if (!Array.isArray(value)) {
      return undefined;
    }
    const commands = value.filter((command): command is string[] =>
      Array.isArray(command) &&
      command.length > 0 &&
      command.every((part): part is string => typeof part === 'string' && part.trim().length > 0),
    );
    return commands.length > 0 ? commands : undefined;
  }

  private fallbackOutputs(context: ToolchainAdapterContext): readonly string[] {
    const packageDirectory = this.relativePackageDirectory(context);
    if (context.phase === 'validate') {
      return [path.join(packageDirectory, 'pyproject.toml').replace(/\\/g, '/')];
    }
    if (context.phase === 'test') {
      return [packageDirectory.replace(/\\/g, '/')];
    }
    return [path.join(packageDirectory, 'dist').replace(/\\/g, '/')];
  }

  private async extractArtifacts(context: ToolchainAdapterContext): Promise<readonly string[]> {
    if (context.phase !== 'build' && context.phase !== 'package') {
      return [];
    }
    const configured = getConfiguredExpectedOutputs(context);
    const candidates = configured.length > 0 ? configured : this.fallbackOutputs(context);
    return extractExistingArtifacts(this.repoRoot, candidates);
  }

  private sourceDirectory(context: ToolchainAdapterContext): string {
    return typeof context.surfaceConfig.sourceDirectory === 'string'
      ? context.surfaceConfig.sourceDirectory
      : '.';
  }

  private pythonCommand(context: ToolchainAdapterContext): string {
    return typeof context.surfaceConfig.pythonCommand === 'string'
      ? context.surfaceConfig.pythonCommand
      : process.platform === 'win32' ? 'python' : 'python3';
  }

  private resolvePackageDirectory(context: ToolchainAdapterContext): string {
    return path.join(this.repoRoot, this.relativePackageDirectory(context));
  }

  private relativePackageDirectory(context: ToolchainAdapterContext): string {
    const configured = context.surfaceConfig.pyprojectPath ?? context.surfaceConfig.packagePath ?? context.surface.path;
    const pyprojectPath = configuredString(configured, 'pyprojectPath');
    return pyprojectPath.endsWith('pyproject.toml') ? path.dirname(pyprojectPath) : pyprojectPath;
  }

  private resolvePyprojectPath(context: ToolchainAdapterContext): string {
    const configured = context.surfaceConfig.pyprojectPath;
    if (typeof configured === 'string' && configured.trim().length > 0) {
      return path.isAbsolute(configured) ? configured : path.join(this.repoRoot, configured);
    }
    return path.join(this.resolvePackageDirectory(context), 'pyproject.toml');
  }

  private resolveTimeoutMs(context: ToolchainAdapterContext): number {
    const configured = context.phaseConfig.timeoutMs ?? context.surfaceConfig.timeoutMs;
    return typeof configured === 'number' && Number.isFinite(configured) && configured > 0
      ? configured
      : defaultTimeoutMs(context.phase);
  }

  private artifactType(context: ToolchainAdapterContext): string {
    if (context.surface.type === 'sdk') {
      return 'python-package';
    }
    if (context.surface.type === 'worker') {
      return 'python-worker';
    }
    return 'python-service';
  }

  private async detectPythonEnvironment(context: ToolchainAdapterContext): Promise<PythonEnvironment> {
    const packageDirectory = this.resolvePackageDirectory(context);
    
    // Check for poetry.lock
    const poetryLockPath = path.join(packageDirectory, 'poetry.lock');
    if (await exists(poetryLockPath)) {
      try {
        const result = await this.commandRunner.run('poetry', ['--version'], {
          cwd: packageDirectory,
          timeoutMs: 10_000,
        });
        if (result.exitCode === 0) {
          return 'poetry';
        }
      } catch {
        // Fall through to other checks
      }
    }

    // Check for uv.lock
    const uvLockPath = path.join(packageDirectory, 'uv.lock');
    if (await exists(uvLockPath)) {
      try {
        const result = await this.commandRunner.run('uv', ['--version'], {
          cwd: packageDirectory,
          timeoutMs: 10_000,
        });
        if (result.exitCode === 0) {
          return 'uv';
        }
      } catch {
        // Fall through to other checks
      }
    }

    // Check for venv
    const venvPath = path.join(packageDirectory, 'venv');
    if (await exists(venvPath)) {
      return 'venv';
    }

    // Default to system
    return 'system';
  }
}
