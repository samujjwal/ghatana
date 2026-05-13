#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, isAbsolute, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const defaultOutputRoot = join(repoRoot, '.kernel', 'out', 'products');
const lifecyclePhases = new Set([
  'create',
  'bootstrap',
  'dev',
  'validate',
  'test',
  'build',
  'package',
  'release',
  'deploy',
  'verify',
  'promote',
  'rollback',
  'operate',
  'retire',
]);

function getIndent(line) {
  return line.length - line.trimStart().length;
}

function parseScalar(rawValue) {
  const value = rawValue.trim();
  if (value === '') {
    return '';
  }
  if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
    return value.slice(1, -1);
  }
  if (value === 'true') {
    return true;
  }
  if (value === 'false') {
    return false;
  }
  if (value === 'null') {
    return null;
  }
  if (value.startsWith('[') && value.endsWith(']')) {
    const inner = value.slice(1, -1).trim();
    if (inner === '') {
      return [];
    }
    return inner.split(',').map((entry) => parseScalar(entry));
  }
  const numericValue = Number(value);
  if (!Number.isNaN(numericValue) && `${numericValue}` === value) {
    return numericValue;
  }
  return value;
}

function splitKeyValue(content) {
  const separatorIndex = content.indexOf(':');
  if (separatorIndex === -1) {
    throw new Error(`Invalid YAML line: ${content}`);
  }
  return {
    key: content.slice(0, separatorIndex).trim(),
    value: content.slice(separatorIndex + 1).trim(),
  };
}

function parseYamlBlock(lines, startIndex, indent) {
  let index = startIndex;
  while (index < lines.length) {
    const candidate = lines[index];
    if (candidate.trim() === '' || candidate.trimStart().startsWith('#')) {
      index += 1;
      continue;
    }
    break;
  }

  if (index >= lines.length) {
    return [{}, index];
  }

  const currentIndent = getIndent(lines[index]);
  if (currentIndent < indent) {
    return [{}, index];
  }

  if (lines[index].trimStart().startsWith('- ')) {
    return parseYamlArray(lines, index, currentIndent);
  }

  return parseYamlObject(lines, index, currentIndent);
}

function parseYamlObject(lines, startIndex, indent) {
  const objectValue = {};
  let index = startIndex;

  while (index < lines.length) {
    const line = lines[index];
    const trimmed = line.trim();
    if (trimmed === '' || trimmed.startsWith('#')) {
      index += 1;
      continue;
    }

    const lineIndent = getIndent(line);
    if (lineIndent < indent) {
      break;
    }
    if (lineIndent > indent) {
      throw new Error(`Unexpected indentation at line: ${line}`);
    }
    if (line.trimStart().startsWith('- ')) {
      break;
    }

    const { key, value } = splitKeyValue(trimmed);
    if (value !== '') {
      objectValue[key] = parseScalar(value);
      index += 1;
      continue;
    }

    const [nestedValue, nextIndex] = parseYamlBlock(lines, index + 1, indent + 2);
    objectValue[key] = nestedValue;
    index = nextIndex;
  }

  return [objectValue, index];
}

function parseYamlArray(lines, startIndex, indent) {
  const arrayValue = [];
  let index = startIndex;

  while (index < lines.length) {
    const line = lines[index];
    const trimmed = line.trim();
    if (trimmed === '' || trimmed.startsWith('#')) {
      index += 1;
      continue;
    }

    const lineIndent = getIndent(line);
    if (lineIndent < indent) {
      break;
    }
    if (lineIndent > indent) {
      throw new Error(`Unexpected indentation at line: ${line}`);
    }
    if (!line.trimStart().startsWith('- ')) {
      break;
    }

    const itemContent = line.trimStart().slice(2).trim();
    if (itemContent === '') {
      const [nestedValue, nextIndex] = parseYamlBlock(lines, index + 1, indent + 2);
      arrayValue.push(nestedValue);
      index = nextIndex;
      continue;
    }

    if (itemContent.includes(':')) {
      const itemObject = {};
      const { key, value } = splitKeyValue(itemContent);
      itemObject[key] = value === '' ? {} : parseScalar(value);
      index += 1;

      while (index < lines.length) {
        const continuation = lines[index];
        const continuationTrimmed = continuation.trim();
        if (continuationTrimmed === '' || continuationTrimmed.startsWith('#')) {
          index += 1;
          continue;
        }

        const continuationIndent = getIndent(continuation);
        if (continuationIndent <= indent) {
          break;
        }

        if (continuationIndent !== indent + 2) {
          throw new Error(`Unexpected indentation at line: ${continuation}`);
        }

        const parsedLine = splitKeyValue(continuationTrimmed);
        if (parsedLine.value !== '') {
          itemObject[parsedLine.key] = parseScalar(parsedLine.value);
          index += 1;
          continue;
        }

        const [nestedValue, nextIndex] = parseYamlBlock(lines, index + 1, continuationIndent + 2);
        itemObject[parsedLine.key] = nestedValue;
        index = nextIndex;
      }

      arrayValue.push(itemObject);
      continue;
    }

    arrayValue.push(parseScalar(itemContent));
    index += 1;
  }

  return [arrayValue, index];
}

function parseYamlDocument(content) {
  const [document] = parseYamlBlock(content.replace(/\r\n/g, '\n').split('\n'), 0, 0);
  return document;
}

function normalizeLifecycleConfig(product, parsed) {
  const normalizedSurfaces = Array.isArray(parsed.surfaces)
    ? Object.fromEntries(
        parsed.surfaces.map((surface) => {
          const surfaceType = surface.type;
          if (!surfaceType) {
            throw new Error(`Lifecycle config for ${product.id} contains a surface without type`);
          }
          return [surfaceType, { ...surface.config, adapter: surface.adapter, source: surface.source }];
        }),
      )
    : parsed.surfaces;

  return {
    ...parsed,
    productId: parsed.productId ?? parsed.id,
    surfaces: normalizedSurfaces,
  };
}

function printUsage(exitCode = 1) {
  const stream = exitCode === 0 ? process.stdout : process.stderr;
  stream.write('Usage:\n');
  stream.write('  node scripts/kernel-product.mjs product plan <productId> <phase> [options]\n');
  stream.write('  node scripts/kernel-product.mjs product <phase> <productId> [options]\n');
  stream.write('  node scripts/kernel-product.mjs plan <productId> <phase> [options]\n');
  stream.write('  node scripts/kernel-product.mjs <phase> <productId> [options]\n');
  stream.write('\n');
  stream.write('Options:\n');
  stream.write('  --surface <surface>\n');
  stream.write('  --surfaces <surface1,surface2>\n');
  stream.write('  --env <environment>\n');
  stream.write('  --dry-run\n');
  stream.write('  --json\n');
  stream.write('  --output-dir <path>\n');
  stream.write('  --source-ref <git ref>\n');
  stream.write('  --artifact <manifest path>\n');
  process.exit(exitCode);
}

function parseOptions(tokens) {
  const options = {
    dryRun: false,
    json: false,
  };

  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    if (token === '--dry-run') {
      options.dryRun = true;
      continue;
    }
    if (token === '--json') {
      options.json = true;
      continue;
    }
    if (token.startsWith('--surface=')) {
      options.surface = token.split('=')[1];
      continue;
    }
    if (token === '--surface') {
      options.surface = tokens[index + 1];
      index += 1;
      continue;
    }
    if (token.startsWith('--surfaces=')) {
      options.surfaces = token.split('=')[1].split(',').filter(Boolean);
      continue;
    }
    if (token === '--surfaces') {
      options.surfaces = (tokens[index + 1] ?? '').split(',').filter(Boolean);
      index += 1;
      continue;
    }
    if (token.startsWith('--env=')) {
      options.environment = token.split('=')[1];
      continue;
    }
    if (token === '--env') {
      options.environment = tokens[index + 1];
      index += 1;
      continue;
    }
    if (token.startsWith('--output-dir=')) {
      options.outputDir = token.split('=')[1];
      continue;
    }
    if (token === '--output-dir') {
      options.outputDir = tokens[index + 1];
      index += 1;
      continue;
    }
    if (token.startsWith('--source-ref=')) {
      options.sourceRef = token.split('=')[1];
      continue;
    }
    if (token === '--source-ref') {
      options.sourceRef = tokens[index + 1];
      index += 1;
      continue;
    }
    if (token.startsWith('--artifact=')) {
      options.artifact = token.split('=')[1];
      continue;
    }
    if (token === '--artifact') {
      options.artifact = tokens[index + 1];
      index += 1;
      continue;
    }
    throw new Error(`Unknown option: ${token}`);
  }

  return options;
}

function parseInvocation(argv) {
  if (argv.length === 0 || argv.includes('--help')) {
    printUsage(argv.includes('--help') ? 0 : 1);
  }

  if (argv[0] === 'product') {
    if (argv[1] === 'plan') {
      if (argv.length < 4) {
        printUsage(1);
      }
      return {
        mode: 'plan',
        phase: argv[3],
        productId: argv[2],
        options: parseOptions(argv.slice(4)),
      };
    }

    if (argv.length < 3) {
      printUsage(1);
    }

    return {
      mode: 'execute',
      phase: argv[1],
      productId: argv[2],
      options: parseOptions(argv.slice(3)),
    };
  }

  if (argv[0] === 'plan') {
    if (argv.length < 3) {
      printUsage(1);
    }
    return {
      mode: 'plan',
      productId: argv[1],
      phase: argv[2],
      options: parseOptions(argv.slice(3)),
    };
  }

  if (argv.length < 2) {
    printUsage(1);
  }

  return {
    mode: 'execute',
    phase: argv[0],
    productId: argv[1],
    options: parseOptions(argv.slice(2)),
  };
}

function loadJson(relativePath) {
  return JSON.parse(readFileSync(join(repoRoot, relativePath), 'utf8'));
}

function loadRegistry() {
  return loadJson('config/canonical-product-registry.json').registry;
}

function loadProfiles() {
  return loadJson('config/product-lifecycle-profiles.json').profiles;
}

function loadToolchains() {
  return loadJson('config/toolchain-adapter-registry.json').adapters;
}

function loadProductLifecycleConfig(product) {
  if (!product.lifecycleConfigPath) {
    throw new Error(`Product ${product.id} does not declare lifecycleConfigPath`);
  }
  const absolutePath = join(repoRoot, product.lifecycleConfigPath);
  if (!existsSync(absolutePath)) {
    throw new Error(`Lifecycle config not found: ${product.lifecycleConfigPath}`);
  }
  const parsed = normalizeLifecycleConfig(product, parseYamlDocument(readFileSync(absolutePath, 'utf8')));
  if (!parsed || typeof parsed !== 'object') {
    throw new Error(`Lifecycle config is invalid: ${product.lifecycleConfigPath}`);
  }
  if (parsed.productId !== product.id) {
    throw new Error(
      `Lifecycle config productId mismatch for ${product.id}: found ${String(parsed.productId)}`,
    );
  }
  return parsed;
}

function normalizeSurfaceSelection(options, phaseDefaults) {
  if (options.surface) {
    return [options.surface];
  }
  if (options.surfaces && options.surfaces.length > 0) {
    return options.surfaces;
  }
  return phaseDefaults;
}

function resolveOutputDirectory(productId, phase, outputDir) {
  return outputDir
    ? isAbsolute(outputDir)
      ? outputDir
      : resolve(repoRoot, outputDir)
    : join(defaultOutputRoot, productId, phase, 'latest');
}

function resolvePhaseConfiguration(product, lifecycleConfig, profile, phase) {
  const phaseConfig = lifecycleConfig.phases?.[phase] ?? product.lifecycle?.phases?.[phase];
  if (phaseConfig) {
    return phaseConfig;
  }

  const defaultSurfaces = profile.defaultSurfaces?.[phase];
  if (!defaultSurfaces) {
    throw new Error(`Phase ${phase} is not defined for lifecycle profile ${lifecycleConfig.lifecycleProfile}`);
  }

  return {
    defaultSurfaces,
    mode: 'sequential',
  };
}

function resolveSurfaceConfig(product, lifecycleConfig, surfaceName) {
  const lifecycleSurface = lifecycleConfig.surfaces?.[surfaceName];
  if (!lifecycleSurface) {
    throw new Error(`Surface ${surfaceName} not found in ${product.lifecycleConfigPath}`);
  }

  const registrySurface = (product.surfaces ?? []).find((surface) => surface.type === surfaceName);
  if (!registrySurface) {
    throw new Error(`Surface ${surfaceName} not declared in canonical registry for ${product.id}`);
  }

  return {
    registrySurface,
    lifecycleSurface,
  };
}

function validateAdapter(surfaceName, lifecycleSurface, phase, toolchains) {
  const adapterId = lifecycleSurface.adapter;
  if (!adapterId) {
    throw new Error(`Surface ${surfaceName} does not declare an adapter`);
  }
  const adapter = toolchains[adapterId];
  if (!adapter) {
    throw new Error(`Unknown adapter ${adapterId} for surface ${surfaceName}`);
  }
  if (!adapter.supportedPhases?.includes(phase)) {
    throw new Error(`Adapter ${adapterId} does not support phase ${phase}`);
  }
  if (!adapter.supportedSurfaceTypes?.includes(surfaceName)) {
    throw new Error(`Adapter ${adapterId} does not support surface ${surfaceName}`);
  }
  for (const requiredField of adapter.requires ?? []) {
    if (lifecycleSurface[requiredField] === undefined) {
      throw new Error(`Adapter ${adapterId} for surface ${surfaceName} requires ${requiredField}`);
    }
  }
  return adapter;
}

function resolveExpectedArtifacts(product, phase) {
  if (phase !== 'build' && phase !== 'package') {
    return [];
  }
  return Object.entries(product.artifacts ?? {}).map(([surface, artifact]) => ({
    surface,
    type: artifact.type,
    packaging: artifact.packaging ?? null,
    required: artifact.required ?? true,
  }));
}

function resolveGates(profile, phase) {
  const requiredGates = (profile.requiredGates?.[phase] ?? []).map((gateId) => ({
    gateId,
    gateName: gateId,
    required: true,
    phase,
    status: 'pending',
  }));
  const optionalGates = (profile.optionalGates?.[phase] ?? []).map((gateId) => ({
    gateId,
    gateName: gateId,
    required: false,
    phase,
    status: 'pending',
  }));
  return [...requiredGates, ...optionalGates];
}

function resolveCommand(phase, surfaceName, lifecycleSurface) {
  if (lifecycleSurface.adapter === 'gradle-java-service') {
    const taskKey = phase === 'dev'
      ? 'devTask'
      : phase === 'validate'
        ? 'validateTask'
        : phase === 'test'
          ? 'testTask'
          : phase === 'build'
            ? 'buildTask'
            : 'packageTask';
    const taskName = lifecycleSurface[taskKey]
      ?? (phase === 'dev'
        ? 'bootRun'
        : phase === 'validate'
          ? 'check'
          : phase === 'test'
            ? 'test'
            : phase === 'package'
              ? 'assemble'
              : 'build');

    return {
      command: process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew',
      args: [`${lifecycleSurface.gradleModule}:${taskName}`, '--no-daemon'],
      workingDirectory: repoRoot,
    };
  }

  if (lifecycleSurface.adapter === 'pnpm-vite-react') {
    const packagePath = String(lifecycleSurface.packagePath);
    const packageDirectory = packagePath.endsWith('package.json') ? dirname(packagePath) : packagePath;
    const scriptKey = phase === 'dev'
      ? 'devScript'
      : phase === 'validate'
        ? 'validateScript'
        : phase === 'test'
          ? 'testScript'
          : phase === 'build'
            ? 'buildScript'
            : 'packageScript';
    const scriptName = lifecycleSurface[scriptKey] ?? (phase === 'validate' ? 'lint' : phase);

    return {
      command: 'pnpm',
      args: ['--dir', packageDirectory, 'run', scriptName],
      workingDirectory: repoRoot,
    };
  }

  throw new Error(`Execution mapping is not implemented for adapter ${lifecycleSurface.adapter} on ${surfaceName}`);
}

function buildPlan(productId, phase, options) {
  if (!lifecyclePhases.has(phase)) {
    throw new Error(`Unknown phase: ${phase}`);
  }

  const registry = loadRegistry();
  const profiles = loadProfiles();
  const toolchains = loadToolchains();
  const product = registry[productId];

  if (!product) {
    throw new Error(`Unknown product ${productId}`);
  }
  if (!product.lifecycleProfile) {
    throw new Error(`Product ${productId} does not declare a lifecycle profile`);
  }
  if (!product.lifecycle?.enabled && product.lifecycleStatus !== 'enabled') {
    throw new Error(`Product ${productId} does not have lifecycle execution enabled`);
  }

  const lifecycleConfig = loadProductLifecycleConfig(product);
  const profile = profiles[lifecycleConfig.lifecycleProfile];
  if (!profile) {
    throw new Error(`Lifecycle profile not found: ${lifecycleConfig.lifecycleProfile}`);
  }

  const phaseConfig = resolvePhaseConfiguration(product, lifecycleConfig, profile, phase);
  const surfaceNames = normalizeSurfaceSelection(options, phaseConfig.defaultSurfaces ?? []);
  if (surfaceNames.length === 0) {
    throw new Error(`Phase ${phase} does not resolve any surfaces`);
  }

  const surfaces = [];
  const steps = [];

  for (const [index, surfaceName] of surfaceNames.entries()) {
    const { registrySurface, lifecycleSurface } = resolveSurfaceConfig(product, lifecycleConfig, surfaceName);
    validateAdapter(surfaceName, lifecycleSurface, phase, toolchains);
    const execution = resolveCommand(phase, surfaceName, lifecycleSurface);

    surfaces.push({
      surface: surfaceName,
      type: registrySurface.type,
      adapter: lifecycleSurface.adapter,
      config: lifecycleSurface,
    });

    steps.push({
      id: `${phase}-${surfaceName}-${index}`,
      phase,
      surface: surfaceName,
      adapter: lifecycleSurface.adapter,
      description: `Execute ${phase} for ${surfaceName}`,
      dependsOn: phaseConfig.mode === 'sequential' && index > 0 ? [`${phase}-${surfaceNames[index - 1]}-${index - 1}`] : [],
      estimatedDurationMs: 30000,
      execution,
    });
  }

  return {
    schemaVersion: '1.0.0',
    productId,
    phase,
    lifecycleProfile: lifecycleConfig.lifecycleProfile,
    environment: options.environment,
    sourceRef: options.sourceRef,
    surfaces,
    gates: resolveGates(profile, phase),
    steps,
    expectedArtifacts: resolveExpectedArtifacts(product, phase),
    outputDirectory: resolveOutputDirectory(productId, phase, options.outputDir),
    estimatedDurationMs: steps.reduce((total, step) => total + step.estimatedDurationMs, 0),
  };
}

function executeStep(step, dryRun) {
  if (dryRun) {
    return {
      stepId: step.id,
      status: 'skipped',
      exitCode: 0,
      stdout: `[DRY-RUN] ${step.execution.command} ${step.execution.args.join(' ')}`,
      stderr: '',
      durationMs: 0,
    };
  }

  const result = spawnSync(step.execution.command, step.execution.args, {
    cwd: step.execution.workingDirectory,
    shell: false,
    encoding: 'utf8',
  });

  return {
    stepId: step.id,
    status: result.status === 0 ? 'succeeded' : 'failed',
    exitCode: result.status ?? 1,
    stdout: result.stdout?.slice(0, 20000) ?? '',
    stderr: result.stderr?.slice(0, 20000) ?? '',
    durationMs: 0,
  };
}

async function writeExecutionArtifacts(plan, result) {
  await mkdir(plan.outputDirectory, { recursive: true });
  await writeFile(join(plan.outputDirectory, 'lifecycle-plan.json'), `${JSON.stringify(plan, null, 2)}\n`);
  await writeFile(join(plan.outputDirectory, 'lifecycle-result.json'), `${JSON.stringify(result, null, 2)}\n`);
}

async function executePlan(plan, options) {
  const startedAt = new Date().toISOString();
  const steps = [];
  let failure;

  for (const step of plan.steps) {
    const stepResult = executeStep(step, options.dryRun);
    steps.push(stepResult);
    if (stepResult.status === 'failed') {
      failure = {
        stepId: step.id,
        message: `Lifecycle step failed for ${step.surface}`,
        cause: stepResult.stderr,
      };
      break;
    }
  }

  const result = {
    schemaVersion: '1.0.0',
    productId: plan.productId,
    phase: plan.phase,
    status: failure ? 'failed' : options.dryRun ? 'skipped' : 'succeeded',
    startedAt,
    completedAt: new Date().toISOString(),
    steps,
    gates: plan.gates.map((gate) => ({
      gateId: gate.gateId,
      gateName: gate.gateName,
      status: options.dryRun ? 'skipped' : 'passed',
      checkedAt: new Date().toISOString(),
    })),
    artifacts: [],
    outputDirectory: plan.outputDirectory,
    failure,
  };

  await writeExecutionArtifacts(plan, result);
  return result;
}

function render(data) {
  console.log(JSON.stringify(data, null, 2));
}

async function main() {
  const invocation = parseInvocation(process.argv.slice(2));
  const plan = buildPlan(invocation.productId, invocation.phase, invocation.options);

  if (invocation.mode === 'plan') {
    render(plan);
    return;
  }

  const result = await executePlan(plan, invocation.options);
  render({ plan, result });

  if (result.status === 'failed') {
    process.exit(1);
  }
}

main().catch((error) => {
  console.error(`Error: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
});
