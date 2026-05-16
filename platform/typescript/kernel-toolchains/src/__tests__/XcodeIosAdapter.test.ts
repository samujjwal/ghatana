import { describe, expect, it } from 'vitest';
import { XcodeIosAdapter } from '../adapters/XcodeIosAdapter.js';
import { FakeCommandRunner } from '../execution/FakeCommandRunner.js';
import type { AdapterLogger, ToolchainAdapterContext } from '../ToolchainAdapter.js';

describe('XcodeIosAdapter', () => {
  it('plans xcodebuild command with project, scheme and configuration', async () => {
    const adapter = new XcodeIosAdapter();

    const plan = await adapter.plan(createContext({ phase: 'build' }));

    expect(plan).toHaveLength(1);
    expect(plan[0].command[0]).toBe('xcodebuild');
    expect(plan[0].command).toContain('-project');
    expect(plan[0].command).toContain('MyApp.xcodeproj');
    expect(plan[0].command).toContain('-scheme');
    expect(plan[0].command).toContain('MyApp');
    expect(plan[0].command).toContain('-configuration');
    expect(plan[0].command).toContain('Release');
  });

  it('uses Debug configuration for dev phase', async () => {
    const adapter = new XcodeIosAdapter();

    const plan = await adapter.plan(createContext({ phase: 'dev' }));

    expect(plan[0].command).toContain('Debug');
    expect(plan[0].command).not.toContain('Release');
  });

  it('uses Release configuration for build phase', async () => {
    const adapter = new XcodeIosAdapter();

    const plan = await adapter.plan(createContext({ phase: 'build' }));

    expect(plan[0].command).toContain('Release');
  });

  it('falls back to workspace flag when xcodeProject is not configured', async () => {
    const adapter = new XcodeIosAdapter();
    const context = createContext({ phase: 'build' });
    delete context.surfaceConfig.xcodeProject;

    const plan = await adapter.plan(context);

    expect(plan[0].command).toContain('-workspace');
    expect(plan[0].command).not.toContain('-project');
  });

  it('returns skipped status for dry-run without invoking the runner', async () => {
    const runner = new FakeCommandRunner([]);
    const adapter = new XcodeIosAdapter(runner);
    const context = createContext({ phase: 'build', dryRun: true });

    const result = await adapter.execute(context);

    expect(result.status).toBe('skipped');
    expect(result.schemaVersion).toBe('1.0.0');
    expect(result.observability).toMatchObject({ durationMs: 0 });
    expect(runner.invocations).toHaveLength(0);
  });

  it('succeeds when xcodebuild exits with code 0', async () => {
    const runner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCEEDED', stderr: '', durationMs: 42 },
    ]);
    const adapter = new XcodeIosAdapter(runner);

    const result = await adapter.execute(createContext({ phase: 'build' }));

    expect(result.status).toBe('succeeded');
    expect(result.schemaVersion).toBe('1.0.0');
    expect(result.steps[0].exitCode).toBe(0);
    expect(result.steps[0].stdout).toContain('BUILD SUCCEEDED');
    expect(result.observability?.exitCode).toBe(0);
  });

  it('returns failed result when xcodebuild exits non-zero', async () => {
    const runner = new FakeCommandRunner([
      { exitCode: 65, stdout: '', stderr: 'compilation error: file.swift:10', durationMs: 5 },
    ]);
    const adapter = new XcodeIosAdapter(runner);

    const result = await adapter.execute(createContext({ phase: 'build' }));

    expect(result.status).toBe('failed');
    expect(result.failure?.message).toContain('65');
    expect(result.failure?.cause).toContain('compilation error');
    expect(result.artifacts).toHaveLength(0);
  });

  it('extracts xcarchive path from xcodebuild stdout', async () => {
    const stdout = 'Build completed.\nArchive Succeeded (/tmp/MyApp.xcarchive)';
    const runner = new FakeCommandRunner([
      { exitCode: 0, stdout, stderr: '', durationMs: 10 },
    ]);
    const adapter = new XcodeIosAdapter(runner);

    const result = await adapter.execute(createContext({ phase: 'package' }));

    expect(result.status).toBe('succeeded');
    expect(result.artifacts).toContain('/tmp/MyApp.xcarchive');
  });

  it('extracts .app path when BUILD_DIR line ends in .app', async () => {
    const stdout = 'BUILD_DIR = /tmp/Build/Products/Release-iphoneos/MyApp.app';
    const runner = new FakeCommandRunner([
      { exitCode: 0, stdout, stderr: '', durationMs: 10 },
    ]);
    const adapter = new XcodeIosAdapter(runner);

    const result = await adapter.execute(createContext({ phase: 'build' }));

    expect(result.status).toBe('succeeded');
    expect(result.artifacts).toContain('/tmp/Build/Products/Release-iphoneos/MyApp.app');
  });

  it('propagates runId and correlationId from context', async () => {
    const runner = new FakeCommandRunner([
      { exitCode: 0, stdout: '', stderr: '', durationMs: 1 },
    ]);
    const adapter = new XcodeIosAdapter(runner);
    const context = createContext({ phase: 'build' });
    context.runId = 'run-ios-1';
    context.correlationId = 'corr-ios-1';

    const result = await adapter.execute(context);

    expect(result.runId).toBe('run-ios-1');
    expect(result.correlationId).toBe('corr-ios-1');
  });

  it('validateOutputs returns valid when no expected outputs configured', async () => {
    const adapter = new XcodeIosAdapter();
    const context = createContext({ phase: 'dev' });

    const validation = await adapter.validateOutputs(context);

    expect(validation.status).toBe('valid');
    expect(validation.errors).toHaveLength(0);
    expect(validation.missingArtifacts).toHaveLength(0);
  });

  it('validateOutputs reports missing artifacts for build phase', async () => {
    const adapter = new XcodeIosAdapter();
    const context = createContext({ phase: 'build' });
    context.outputDir = '/tmp/nonexistent-output-dir-xcode-test';

    const validation = await adapter.validateOutputs(context);

    expect(validation.status).toBe('invalid');
    expect(validation.missingArtifacts).toHaveLength(1);
    expect(validation.errors[0].message).toContain('MyApp');
  });

  it('supports mobile-ios surface type', () => {
    const adapter = new XcodeIosAdapter();
    expect(adapter.supportedSurfaceTypes).toContain('mobile-ios');
  });

  it('supports dev, validate, test, build, and package phases', () => {
    const adapter = new XcodeIosAdapter();
    expect(adapter.supportedPhases).toContain('dev');
    expect(adapter.supportedPhases).toContain('validate');
    expect(adapter.supportedPhases).toContain('test');
    expect(adapter.supportedPhases).toContain('build');
    expect(adapter.supportedPhases).toContain('package');
  });
});

function createContext(overrides: Partial<ToolchainAdapterContext> & { phase?: string; dryRun?: boolean }): ToolchainAdapterContext {
  return {
    productId: 'flashit',
    phase: (overrides.phase ?? 'build') as ToolchainAdapterContext['phase'],
    surface: {
      type: 'mobile-ios',
      adapter: 'xcode-ios',
      path: 'products/flashit/mobile-ios',
    },
    dryRun: overrides.dryRun ?? false,
    surfaceConfig: {
      xcodeProject: 'MyApp.xcodeproj',
      scheme: 'MyApp',
    },
    phaseConfig: {},
    logger: createLogger(),
    outputDir: '/tmp/kernel-xcode-test',
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
