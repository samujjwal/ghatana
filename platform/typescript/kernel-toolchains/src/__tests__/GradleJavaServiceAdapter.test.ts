import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { GradleJavaServiceAdapter } from '../adapters/GradleJavaServiceAdapter.js';
import { FakeCommandRunner } from '../execution/FakeCommandRunner.js';
import type { AdapterLogger, ToolchainAdapterContext } from '../ToolchainAdapter.js';

describe('GradleJavaServiceAdapter', () => {
  let repoRoot: string;

  beforeEach(async () => {
    repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'gradle-adapter-'));
    await fs.writeFile(path.join(repoRoot, 'settings.gradle.kts'), 'include(":products:digital-marketing:backend")');
  });

  afterEach(async () => {
    await fs.rm(repoRoot, { recursive: true, force: true });
  });

  it('plans Gradle execution from the repo root', async () => {
    const adapter = new GradleJavaServiceAdapter({ repoRoot });

    const plan = await adapter.plan(createContext(repoRoot));

    expect(plan).toHaveLength(1);
    expect(plan[0].workingDirectory).toBe(repoRoot);
    expect(plan[0].command).toContain('--no-daemon');
    expect(plan[0].command[1]).toBe(':products:digital-marketing:backend:build');
  });

  it('requires gradleModule during planning', async () => {
    const adapter = new GradleJavaServiceAdapter({ repoRoot });
    const context = createContext(repoRoot);
    delete context.surfaceConfig.gradleModule;

    await expect(adapter.plan(context)).rejects.toThrow('gradleModule is required');
  });

  it('preflights Gradle wrapper, module config, and source path', async () => {
    await fs.writeFile(path.join(repoRoot, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew'), '');
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend'), { recursive: true });
    const adapter = new GradleJavaServiceAdapter({ repoRoot });

    const result = await adapter.preflight(createContext(repoRoot));

    expect(result.status).toBe('ready');
    expect(result.blockingIssues).toHaveLength(0);
    expect(result.checks.map((check) => check.checkId)).toEqual([
      'gradle-wrapper',
      'gradle-module-config',
      'gradle-surface-source',
    ]);
  });

  it('blocks preflight when Gradle wrapper or source path is missing', async () => {
    const adapter = new GradleJavaServiceAdapter({ repoRoot });

    const result = await adapter.preflight(createContext(repoRoot));

    expect(result.status).toBe('blocked');
    expect(result.blockingIssues).toEqual(
      expect.arrayContaining([
        expect.stringContaining('gradle-wrapper'),
        expect.stringContaining('gradle-surface-source'),
      ]),
    );
  });

  it('classifies Gradle module configuration failures', async () => {
    const adapter = new GradleJavaServiceAdapter({ repoRoot });

    const classification = await adapter.classifyFailure(
      new Error('gradle-module-not-found: missing module'),
      createContext(repoRoot),
    );

    expect(classification.category).toBe('config');
    expect(classification.relatedFailureCodes).toContain('gradle-java-service-module-config');
    expect(classification.requiresHumanIntervention).toBe(true);
  });

  it('returns schema-backed dry-run evidence without executing commands', async () => {
    const commandRunner = new FakeCommandRunner([]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });
    const context = createContext(repoRoot);
    context.dryRun = true;

    const result = await adapter.execute(context);

    expect(result.status).toBe('skipped');
    expect(result.schemaVersion).toBe('1.0.0');
    expect(result.observability).toMatchObject({ commandId: 'gradle-build', durationMs: 0 });
    expect(commandRunner.invocations).toHaveLength(0);
  });

  it('executes successfully when the expected jar output exists', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs'), { recursive: true });
    await fs.writeFile(
      path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs', 'digital-marketing.jar'),
      'jar-content',
    );

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));

    expect(result.status).toBe('succeeded');
    expect(result.schemaVersion).toBe('1.0.0');
    expect(result.artifacts).toContain('products/digital-marketing/backend/build/libs/digital-marketing.jar');
    expect(result.evidenceRefs).toContain('artifact:products/digital-marketing/backend/build/libs/digital-marketing.jar');
    expect(result.observability).toMatchObject({
      commandId: 'gradle-build',
      exitCode: 0,
      stdoutBytes: 'BUILD SUCCESSFUL'.length,
    });
    expect(commandRunner.invocations).toHaveLength(1);
    expect(commandRunner.invocations[0].options.cwd).toBe(repoRoot);
    expect(commandRunner.invocations[0].options.timeoutMs).toBe(1_200_000);
  });

  it('uses configured command timeout', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs'), { recursive: true });
    await fs.writeFile(
      path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs', 'digital-marketing.jar'),
      'jar-content',
    );

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });
    const context = createContext(repoRoot);
    context.phaseConfig.timeoutMs = 123_000;

    await adapter.execute(context);

    expect(commandRunner.invocations[0].options.timeoutMs).toBe(123_000);
  });

  it('uses surface timeout when phase timeout is absent', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs'), { recursive: true });
    await fs.writeFile(
      path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs', 'digital-marketing.jar'),
      'jar-content',
    );

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });
    const context = createContext(repoRoot);
    context.surfaceConfig.timeoutMs = 456_000;

    await adapter.execute(context);

    expect(commandRunner.invocations[0].options.timeoutMs).toBe(456_000);
  });

  it('falls back to default timeout when configured timeout is invalid', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs'), { recursive: true });
    await fs.writeFile(
      path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs', 'digital-marketing.jar'),
      'jar-content',
    );

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });
    const context = createContext(repoRoot);
    context.phaseConfig.timeoutMs = -1;

    await adapter.execute(context);

    expect(commandRunner.invocations[0].options.timeoutMs).toBe(1_200_000);
  });

  it('returns failure cause when Gradle exits non-zero', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend'), { recursive: true });

    const commandRunner = new FakeCommandRunner([
      { exitCode: 1, stdout: 'BUILD FAILED', stderr: 'Compilation failed', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));

    expect(result.status).toBe('failed');
    expect(result.failure?.cause).toBe('Compilation failed');
    expect(result.steps[0].stderr).toBe('Compilation failed');
  });

  it('fails closed when required configured output is missing', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs'), { recursive: true });
    await fs.writeFile(
      path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs', 'digital-marketing.jar'),
      'jar-content',
    );

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

    const context = createContext(repoRoot);
    context.surfaceConfig.expectedOutputs = {
      build: ['products/digital-marketing/backend/build/libs/missing.jar'],
    };

    const result = await adapter.execute(context);

    expect(result.status).toBe('failed');
    expect(result.failure?.message).toContain('Missing expected output');
  });

  it('returns existing artifact evidence when one configured output is missing', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs'), { recursive: true });
    await fs.writeFile(
      path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs', 'digital-marketing.jar'),
      'jar-content',
    );

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

    const context = createContext(repoRoot);
    context.surfaceConfig.expectedOutputs = {
      build: [
        'products/digital-marketing/backend/build/libs/digital-marketing.jar',
        'products/digital-marketing/backend/build/libs/missing.jar',
      ],
    };

    const result = await adapter.execute(context);

    expect(result.status).toBe('failed');
    expect(result.artifacts).toContain('products/digital-marketing/backend/build/libs/digital-marketing.jar');
    expect(result.evidenceRefs).toContain('artifact:products/digital-marketing/backend/build/libs/digital-marketing.jar');
  });

  it('rejects unsupported phases instead of falling back', async () => {
    const adapter = new GradleJavaServiceAdapter({ repoRoot });
    const context = createContext(repoRoot);
    context.phase = 'deploy';

    await expect(adapter.plan(context)).rejects.toThrow('does not support phase deploy');
  });

  it('fails closed when gradle module is not declared and source path is missing', async () => {
    await fs.writeFile(path.join(repoRoot, 'settings.gradle.kts'), 'include(":other:module")');

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });
    const context = createContext(repoRoot);

    await expect(adapter.execute(context)).rejects.toThrow('gradle-module-not-found');
    expect(commandRunner.invocations).toHaveLength(0);
  });

  it('accepts a module declared in settings.gradle.kts', async () => {
    await fs.writeFile(path.join(repoRoot, 'settings.gradle.kts'), 'include(":products:digital-marketing:backend")');
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs'), { recursive: true });
    await fs.writeFile(
      path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs', 'digital-marketing.jar'),
      'jar-content',
    );

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));
    expect(result.status).toBe('succeeded');
  });

  it('accepts a module declared in settings.gradle', async () => {
    await fs.writeFile(path.join(repoRoot, 'settings.gradle'), "include ':products:digital-marketing:backend'");
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs'), { recursive: true });
    await fs.writeFile(
      path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs', 'digital-marketing.jar'),
      'jar-content',
    );

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));
    expect(result.status).toBe('succeeded');
  });

  it('accepts a module declared in a settings file loaded via apply(from=...)', async () => {
    await fs.mkdir(path.join(repoRoot, 'config', 'generated'), { recursive: true });
    await fs.writeFile(
      path.join(repoRoot, 'settings.gradle.kts'),
      'apply(from = file("config/generated/settings-gradle-includes.kts"))',
    );
    await fs.writeFile(
      path.join(repoRoot, 'config', 'generated', 'settings-gradle-includes.kts'),
      'include(":products:digital-marketing:backend")',
    );
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs'), {
      recursive: true,
    });
    await fs.writeFile(
      path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs', 'digital-marketing.jar'),
      'jar-content',
    );

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));
    expect(result.status).toBe('succeeded');
  });

  it('fails output validation when wildcard search root is missing', async () => {
    await fs.writeFile(path.join(repoRoot, 'settings.gradle.kts'), 'include(":products:digital-marketing:backend")');

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));

    expect(result.status).toBe('failed');
    expect(result.failure?.message).toContain('Missing expected output');
  });

  describe('dev phase', () => {
    it('skips output validation and writes processes.json', async () => {
      await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend'), { recursive: true });
      const outputDir = path.join(repoRoot, '.kernel', 'out', 'dev');
      const commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'Started application', stderr: '', durationMs: 50 },
      ]);
      const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

      const context = createContext(repoRoot);
      context.phase = 'dev';
      context.outputDir = outputDir;

      const result = await adapter.execute(context);

      expect(result.status).toBe('succeeded');
      expect(result.artifacts).toHaveLength(0);

      // processes.json should be written
      const processesJson = path.join(outputDir, 'processes.json');
      const raw = await fs.readFile(processesJson, 'utf-8');
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      expect(parsed.schemaVersion).toBe('1.0.0');
      expect(parsed.adapter).toBe('gradle-java-service');
      expect(parsed.productId).toBe('digital-marketing');
      expect(parsed.runId).toBe('run-1');
      expect(parsed.correlationId).toBe('corr-1');
      expect(parsed.healthUrl).toBe('http://localhost:8080/health');
    });

    it('validateOutputs returns valid for dev phase without checking disk', async () => {
      const adapter = new GradleJavaServiceAdapter({ repoRoot });
      const context = createContext(repoRoot);
      context.phase = 'dev';

      const result = await adapter.validateOutputs(context);
      expect(result.status).toBe('valid');
      expect(result.missingArtifacts).toHaveLength(0);
    });

    it('plans bootRun task for dev phase', async () => {
      const adapter = new GradleJavaServiceAdapter({ repoRoot });
      const context = createContext(repoRoot);
      context.phase = 'dev';

      const plan = await adapter.plan(context);
      expect(plan[0].command[1]).toBe(':products:digital-marketing:backend:bootRun');
    });
  });

  describe('test phase', () => {
    it('fails when test result directories are missing', async () => {
      await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend'), { recursive: true });
      const commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'Tests passed', stderr: '', durationMs: 10 },
      ]);
      const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

      const context = createContext(repoRoot);
      context.phase = 'test';

      const result = await adapter.execute(context);

      // No test result directories exist -> validation fails
      expect(result.status).toBe('failed');
      expect(result.failure?.message).toContain('Missing expected output');
    });

    it('succeeds when test result directories exist', async () => {
      await fs.mkdir(
        path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'reports', 'tests'),
        { recursive: true },
      );
      await fs.mkdir(
        path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'test-results', 'test'),
        { recursive: true },
      );
      await fs.writeFile(
        path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'test-results', 'test', 'TEST-dmos.xml'),
        '<testsuite tests="4" failures="1" errors="0" skipped="1" time="1.5"></testsuite>',
      );
      await fs.mkdir(
        path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'reports', 'jacoco', 'test'),
        { recursive: true },
      );
      await fs.writeFile(
        path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'reports', 'jacoco', 'test', 'jacocoTestReport.csv'),
        [
          'GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED',
          'dmos,com.ghatana,Service,10,90,2,8,5,95',
        ].join('\n'),
      );

      const commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
      ]);
      const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

      const context = createContext(repoRoot);
      context.phase = 'test';

      const result = await adapter.execute(context);
      expect(result.status).toBe('succeeded');
      expect(result.testResults).toEqual({
        tests: 4,
        failures: 1,
        skipped: 1,
        durationMs: 1500,
      });
      expect(result.coverageResults).toEqual({
        lineCoverage: 95,
        branchCoverage: 80,
        instructionCoverage: 90,
      });
      expect(result.artifacts).toEqual(
        expect.arrayContaining([
          'products/digital-marketing/backend/build/reports/tests',
          'products/digital-marketing/backend/build/test-results/test',
          'products/digital-marketing/backend/build/reports/jacoco',
        ]),
      );
    });
  });

  describe('package phase', () => {
    it('returns jar artifact paths for package phase', async () => {
      await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs'), { recursive: true });
      await fs.writeFile(
        path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs', 'digital-marketing.jar'),
        'jar-content',
      );

      const commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
      ]);
      const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });
      const context = createContext(repoRoot);
      context.phase = 'package';

      const result = await adapter.execute(context);

      expect(result.status).toBe('succeeded');
      expect(result.artifacts).toContain('products/digital-marketing/backend/build/libs/digital-marketing.jar');
    });
  });

  describe('validate phase', () => {
    it('uses fallback report validation when configured expected outputs are invalid', async () => {
      await fs.mkdir(
        path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'reports', 'tests'),
        { recursive: true },
      );
      await fs.mkdir(
        path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'test-results', 'test'),
        { recursive: true },
      );

      const commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
      ]);
      const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });
      const context = createContext(repoRoot);
      context.phase = 'validate';
      context.surfaceConfig.expectedOutputs = { validate: 'not-an-array' };

      const result = await adapter.execute(context);
      expect(result.status).toBe('succeeded');
    });
  });
});

function createContext(repoRoot: string): ToolchainAdapterContext {
  return {
    productId: 'digital-marketing',
    phase: 'build',
    surface: {
      type: 'backend-api',
      adapter: 'gradle-java-service',
      path: 'products/digital-marketing/backend',
    },
    dryRun: false,
    surfaceConfig: {
      gradleModule: ':products:digital-marketing:backend',
      source: 'products/digital-marketing/backend',
      healthUrl: 'http://localhost:8080/health',
    },
    phaseConfig: {},
    logger: createLogger(),
    outputDir: path.join(repoRoot, '.kernel', 'artifacts'),
    runId: 'run-1',
    correlationId: 'corr-1',
    metadata: {
      version: '1.0.0',
      gitCommit: 'abcdef0',
      gitBranch: 'main',
    },
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
