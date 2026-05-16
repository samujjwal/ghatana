import { describe, expect, it } from 'vitest';
import { GradleAndroidAdapter } from '../adapters/GradleAndroidAdapter.js';
import { FakeCommandRunner } from '../execution/FakeCommandRunner.js';
import type { AdapterLogger, ToolchainAdapterContext } from '../ToolchainAdapter.js';

describe('GradleAndroidAdapter', () => {
  it('plans assembleRelease task for build phase', async () => {
    const adapter = new GradleAndroidAdapter();

    const plan = await adapter.plan(createContext({ phase: 'build' }));
    const cmd = plan[0].command.join(' ');

    expect(plan).toHaveLength(1);
    expect(plan[0].command[0]).toMatch(/gradlew/);
    expect(cmd).toContain('assembleRelease');
    expect(plan[0].command).toContain('--stacktrace');
  });

  it('plans bundleRelease task for package phase', async () => {
    const adapter = new GradleAndroidAdapter();

    const plan = await adapter.plan(createContext({ phase: 'package' }));
    const cmd = plan[0].command.join(' ');

    expect(cmd).toContain('bundleRelease');
    expect(cmd).not.toContain('assembleRelease');
  });

  it('uses module-scoped gradle task when gradleModule is configured', async () => {
    const adapter = new GradleAndroidAdapter();
    const context = createContext({ phase: 'build' });
    context.surfaceConfig.gradleModule = 'app';

    const plan = await adapter.plan(context);
    const cmd = plan[0].command.join(' ');

    expect(cmd).toContain(':app:assembleRelease');
  });

  it('uses plain task name when gradleModule is not configured', async () => {
    const adapter = new GradleAndroidAdapter();
    const context = createContext({ phase: 'build' });
    delete context.surfaceConfig.gradleModule;

    const plan = await adapter.plan(context);
    const cmd = plan[0].command.join(' ');

    expect(cmd).not.toContain(':app:assembleRelease');
    expect(cmd).toContain('assembleRelease');
  });

  it('returns skipped status for dry-run without invoking runner', async () => {
    const runner = new FakeCommandRunner([]);
    const adapter = new GradleAndroidAdapter(runner);
    const context = createContext({ phase: 'build', dryRun: true });

    const result = await adapter.execute(context);

    expect(result.status).toBe('skipped');
    expect(result.schemaVersion).toBe('1.0.0');
    expect(result.observability).toMatchObject({ durationMs: 0 });
    expect(runner.invocations).toHaveLength(0);
  });

  it('succeeds when Gradle exits with code 0', async () => {
    const runner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 30 },
    ]);
    const adapter = new GradleAndroidAdapter(runner);

    const result = await adapter.execute(createContext({ phase: 'build' }));

    expect(result.status).toBe('succeeded');
    expect(result.schemaVersion).toBe('1.0.0');
    expect(result.steps[0].exitCode).toBe(0);
    expect(result.observability?.exitCode).toBe(0);
  });

  it('returns failed result when Gradle exits non-zero', async () => {
    const runner = new FakeCommandRunner([
      { exitCode: 1, stdout: 'BUILD FAILED', stderr: 'Execution failed for task', durationMs: 8 },
    ]);
    const adapter = new GradleAndroidAdapter(runner);

    const result = await adapter.execute(createContext({ phase: 'build' }));

    expect(result.status).toBe('failed');
    expect(result.failure?.message).toContain('1');
    expect(result.failure?.cause).toContain('Execution failed');
    expect(result.artifacts).toHaveLength(0);
  });

  it('extracts APK paths from Gradle stdout', async () => {
    const stdout = [
      'Task :app:assembleRelease',
      'apk output: /workspace/app/build/outputs/apk/release/app-release.apk',
    ].join('\n');
    const runner = new FakeCommandRunner([
      { exitCode: 0, stdout, stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleAndroidAdapter(runner);

    const result = await adapter.execute(createContext({ phase: 'build' }));

    expect(result.status).toBe('succeeded');
    expect(result.artifacts.some((a) => a.endsWith('app-release.apk'))).toBe(true);
  });

  it('extracts AAB paths from Gradle stdout', async () => {
    const stdout = [
      'Task :app:bundleRelease',
      'bundle output: /workspace/app/build/outputs/bundle/release/app-release.aab',
    ].join('\n');
    const runner = new FakeCommandRunner([
      { exitCode: 0, stdout, stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleAndroidAdapter(runner);

    const result = await adapter.execute(createContext({ phase: 'package' }));

    expect(result.status).toBe('succeeded');
    expect(result.artifacts.some((a) => a.endsWith('app-release.aab'))).toBe(true);
  });

  it('deduplicates artifact paths extracted from stdout', async () => {
    const stdout = [
      'apk output: /workspace/app-release.apk',
      'apk output: /workspace/app-release.apk',
    ].join('\n');
    const runner = new FakeCommandRunner([
      { exitCode: 0, stdout, stderr: '', durationMs: 5 },
    ]);
    const adapter = new GradleAndroidAdapter(runner);

    const result = await adapter.execute(createContext({ phase: 'build' }));

    const apkPaths = result.artifacts.filter((a) => a.endsWith('.apk'));
    expect(apkPaths).toHaveLength(1);
  });

  it('propagates runId and correlationId from context', async () => {
    const runner = new FakeCommandRunner([
      { exitCode: 0, stdout: '', stderr: '', durationMs: 1 },
    ]);
    const adapter = new GradleAndroidAdapter(runner);
    const context = createContext({ phase: 'build' });
    context.runId = 'run-android-1';
    context.correlationId = 'corr-android-1';

    const result = await adapter.execute(context);

    expect(result.runId).toBe('run-android-1');
    expect(result.correlationId).toBe('corr-android-1');
  });

  it('validateOutputs reports missing APK for build phase', async () => {
    const adapter = new GradleAndroidAdapter();
    const context = createContext({ phase: 'build' });
    context.outputDir = '/tmp/nonexistent-android-output';

    const validation = await adapter.validateOutputs(context);

    expect(validation.status).toBe('invalid');
    expect(validation.missingArtifacts.length).toBeGreaterThan(0);
    expect(validation.errors[0].message).toContain('.apk');
  });

  it('validateOutputs reports missing AAB for package phase', async () => {
    const adapter = new GradleAndroidAdapter();
    const context = createContext({ phase: 'package' });
    context.outputDir = '/tmp/nonexistent-android-output';

    const validation = await adapter.validateOutputs(context);

    expect(validation.status).toBe('invalid');
    expect(validation.errors[0].message).toContain('.aab');
  });

  it('uses assembleDebug variant when variant is configured as debug', async () => {
    const adapter = new GradleAndroidAdapter();
    const context = createContext({ phase: 'build' });
    context.surfaceConfig.variant = 'debug';

    const plan = await adapter.plan(context);

    const cmd = plan[0].command.join(' ');
    expect(cmd).toContain('assembleDebug');
  });

  it('supports mobile-android surface type', () => {
    const adapter = new GradleAndroidAdapter();
    expect(adapter.supportedSurfaceTypes).toContain('mobile-android');
  });

  it('supports dev, validate, test, build, and package phases', () => {
    const adapter = new GradleAndroidAdapter();
    expect(adapter.supportedPhases).toContain('dev');
    expect(adapter.supportedPhases).toContain('test');
    expect(adapter.supportedPhases).toContain('build');
    expect(adapter.supportedPhases).toContain('package');
  });
});

function createContext(overrides: { phase?: string; dryRun?: boolean } = {}): ToolchainAdapterContext {
  return {
    productId: 'flashit',
    phase: (overrides.phase ?? 'build') as ToolchainAdapterContext['phase'],
    surface: {
      type: 'mobile-android',
      adapter: 'gradle-android',
      path: 'products/flashit/mobile-android',
    },
    dryRun: overrides.dryRun ?? false,
    surfaceConfig: {
      gradleModule: 'app',
      variant: 'release',
    },
    phaseConfig: {},
    logger: createLogger(),
    outputDir: '/tmp/kernel-android-test',
    runId: 'run-1',
    correlationId: 'corr-1',
    metadata: {
      version: '1.0.0',
      gitCommit: 'abc1234',
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
