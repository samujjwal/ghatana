import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { CargoRustAdapter } from '../adapters/CargoRustAdapter.js';
import { FakeCommandRunner } from '../execution/FakeCommandRunner.js';
import type { AdapterLogger, ToolchainAdapterContext } from '../ToolchainAdapter.js';

describe('CargoRustAdapter', () => {
  let repoRoot: string;

  beforeEach(async () => {
    repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'cargo-rust-adapter-'));
  });

  afterEach(async () => {
    await fs.rm(repoRoot, { recursive: true, force: true });
  });

  it('plans Rust validation as fmt, check, and clippy with warnings denied', async () => {
    await writeCargoToml(repoRoot);
    const adapter = new CargoRustAdapter({ repoRoot });
    const context = createContext(repoRoot);
    context.phase = 'validate';

    const plan = await adapter.plan(context);

    expect(plan.map((step) => step.command)).toEqual([
      ['cargo', 'fmt', '--check'],
      ['cargo', 'check'],
      ['cargo', 'clippy', '--', '-D', 'warnings'],
    ]);
    expect(plan[1].dependsOn).toEqual(['cargo-validate-1']);
  });

  it('returns dry-run evidence without executing Cargo', async () => {
    await writeCargoToml(repoRoot);
    const commandRunner = new FakeCommandRunner([]);
    const adapter = new CargoRustAdapter({ repoRoot, commandRunner });
    const context = createContext(repoRoot);
    context.dryRun = true;

    const result = await adapter.execute(context);

    expect(result.status).toBe('skipped');
    expect(result.observability).toMatchObject({ commandId: 'cargo-build-1' });
    expect(commandRunner.invocations).toHaveLength(0);
  });

  it('executes release builds and emits an artifact manifest when the binary exists', async () => {
    await writeCargoToml(repoRoot);
    await fs.mkdir(path.join(repoRoot, 'products', 'rust-service', 'target', 'release'), { recursive: true });
    await fs.writeFile(path.join(repoRoot, 'products', 'rust-service', 'target', 'release', 'service-bin'), '');
    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'Finished release', stderr: '', durationMs: 25 },
    ]);
    const adapter = new CargoRustAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));

    expect(result.status).toBe('succeeded');
    expect(result.artifacts).toContain('products/rust-service/target/release/service-bin');
    expect(result.manifestRefs?.artifactManifest).toBe('.kernel/artifacts/rust-product/backend-api/artifact-manifest.json');
    const manifest = JSON.parse(await fs.readFile(
      path.join(repoRoot, '.kernel', 'artifacts', 'rust-product', 'backend-api', 'artifact-manifest.json'),
      'utf-8',
    )) as {
      schemaVersion: string;
      adapter: string;
      source: { path: string };
      trustState: { status: string; validation: string };
      artifacts: Array<{
        path: string;
        type: string;
        sizeBytes: number;
        fingerprint: { algorithm: string; hash: string };
        metadata: { adapter: string; sourcePath: string };
      }>;
    };
    expect(manifest.schemaVersion).toBe('1.0.0');
    expect(manifest.adapter).toBe('cargo-rust');
    expect(manifest.source.path).toBe('products/rust-service');
    expect(manifest.trustState).toMatchObject({ status: 'verified', validation: 'expected-output-validation' });
    expect(manifest.artifacts[0]).toMatchObject({
      path: 'products/rust-service/target/release/service-bin',
      type: 'rust-binary',
      fingerprint: { algorithm: 'sha256' },
      metadata: { adapter: 'cargo-rust', sourcePath: 'products/rust-service' },
    });
    expect(manifest.artifacts[0].fingerprint.hash).toMatch(/^[a-f0-9]{64}$/);
    expect(commandRunner.invocations[0].command).toBe('cargo');
    expect(commandRunner.invocations[0].options.cwd).toBe(path.join(repoRoot, 'products', 'rust-service'));
  });

  it('fails closed when the configured build artifact is missing', async () => {
    await writeCargoToml(repoRoot);
    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'Finished release', stderr: '', durationMs: 25 },
    ]);
    const adapter = new CargoRustAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));

    expect(result.status).toBe('failed');
    expect(result.failure?.message).toContain('Missing expected output');
  });

  it('classifies missing Cargo as environment blocked', async () => {
    const adapter = new CargoRustAdapter({ repoRoot });

    const classification = await adapter.classifyFailure(new Error('spawn cargo ENOENT'), createContext(repoRoot));

    expect(classification.relatedFailureCodes).toContain('cargo-rust-toolchain-missing');
    expect(classification.requiresHumanIntervention).toBe(true);
  });

  it('P1-01: parses cargo test output with duration', async () => {
    const adapter = new CargoRustAdapter({ repoRoot });
    const testOutput = `
test test_module::test_one ... ok
test test_module::test_two ... FAILED
test test_module::test_three ... ignored
test result: ok. 1 passed; 1 failed; 1 ignored
    `.trim();

    // Access private method via type assertion for testing
    const parseMethod = (adapter as any).parseCargoTestOutput.bind(adapter);
    const result = parseMethod(testOutput);

    expect(result.tests).toBe(3);
    expect(result.failures).toBe(1);
    expect(result.skipped).toBe(1);
    // Duration is 0 if not found in output
    expect(result.durationMs).toBe(0);
  });

  it('P1-01: includes target triple and Rust version in build metadata', async () => {
    await writeCargoToml(repoRoot);
    await fs.mkdir(path.join(repoRoot, 'products', 'rust-service', 'target', 'release'), { recursive: true });
    await fs.writeFile(path.join(repoRoot, 'products', 'rust-service', 'target', 'release', 'service-bin'), '');
    
    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'Finished release', stderr: '', durationMs: 25 },
      { exitCode: 1, stdout: '', stderr: 'rustc not found', durationMs: 5 },
      { exitCode: 1, stdout: '', stderr: 'rustc not found', durationMs: 5 },
    ]);
    const adapter = new CargoRustAdapter({ repoRoot, commandRunner });
    const context = createContext(repoRoot);
    context.phase = 'build';

    const result = await adapter.execute(context);

    expect(result.status).toBe('succeeded');
    expect(result.metadata).toBeDefined();
    expect(result.metadata).toHaveProperty('targetTriple');
    expect(result.metadata).toHaveProperty('rustVersion');
    expect(result.metadata).toHaveProperty('artifactType');
    expect(result.metadata?.artifactType).toBe('rust-binary');
  });

  it('P1-01: handles missing rustc gracefully when detecting target triple', async () => {
    await writeCargoToml(repoRoot);
    await fs.mkdir(path.join(repoRoot, 'products', 'rust-service', 'target', 'release'), { recursive: true });
    await fs.writeFile(path.join(repoRoot, 'products', 'rust-service', 'target', 'release', 'service-bin'), '');
    
    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'Finished release', stderr: '', durationMs: 25 },
      { exitCode: 1, stdout: '', stderr: 'rustc not found', durationMs: 5 },
      { exitCode: 1, stdout: '', stderr: 'rustc not found', durationMs: 5 },
    ]);
    const adapter = new CargoRustAdapter({ repoRoot, commandRunner });
    const context = createContext(repoRoot);
    context.phase = 'build';

    const result = await adapter.execute(context);

    expect(result.status).toBe('succeeded');
    expect(result.metadata).toBeDefined();
    expect(result.metadata?.targetTriple).toBe('unknown');
    expect(result.metadata?.rustVersion).toBe('unknown');
  });
});

function createContext(repoRoot: string): ToolchainAdapterContext {
  return {
    productId: 'rust-product',
    phase: 'build',
    surface: {
      type: 'backend-api',
      adapter: 'cargo-rust',
      path: 'products/rust-service',
    },
    dryRun: false,
    surfaceConfig: {
      cratePath: 'products/rust-service',
      artifactName: 'service-bin',
    },
    phaseConfig: {},
    logger: createLogger(),
    outputDir: path.join(repoRoot, '.kernel', 'artifacts', 'rust-product', 'backend-api'),
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

async function writeCargoToml(repoRoot: string): Promise<void> {
  const crateDir = path.join(repoRoot, 'products', 'rust-service');
  await fs.mkdir(crateDir, { recursive: true });
  await fs.writeFile(
    path.join(crateDir, 'Cargo.toml'),
    '[package]\nname = "service-bin"\nversion = "0.1.0"\nedition = "2021"\n',
    'utf-8',
  );
}
