import { mkdir, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import {
  ConsoleExecutionLogger,
  ExecutionResultCollector,
  ProductLifecycleExecutor,
  ProductLifecyclePlanner,
  ProductLifecycleStepRunner,
} from '../platform/typescript/kernel-lifecycle/dist/index.js';
import { createBootstrapKernelProviders } from '../platform/typescript/kernel-providers/dist/index.js';
import { createDefaultToolchainAdapterRegistry } from '../platform/typescript/kernel-toolchains/dist/ToolchainAdapterRegistry.js';

const VALID_PHASES = new Set([
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

function parseArgs(argv) {
  const options = {
    surfaces: [],
    env: undefined,
    dryRun: false,
    json: false,
    mode: 'bootstrap',
    requireApproval: false,
    outputDir: undefined,
    sourceRef: undefined,
    artifact: undefined,
    from: undefined,
    to: undefined,
    approvalId: undefined,
    correlationId: undefined,
    tenant: undefined,
    workspace: undefined,
    project: undefined,
    releaseId: undefined,
  };

  const positional = [];
  for (let index = 0; index < argv.length; index += 1) {
    const current = argv[index];
    if (!current.startsWith('--')) {
      positional.push(current);
      continue;
    }

    const key = current.slice(2);
    if (key === 'dry-run') {
      options.dryRun = true;
      continue;
    }
    if (key === 'json') {
      options.json = true;
      continue;
    }
    if (key === 'require-approval') {
      options.requireApproval = true;
      continue;
    }

    const value = argv[index + 1];
    if (!value || value.startsWith('--')) {
      throw new Error(`Missing value for option --${key}`);
    }
    index += 1;

    if (key === 'surface' || key === 'surfaces') {
      options.surfaces.push(...value.split(',').map((v) => v.trim()).filter(Boolean));
    } else if (key === 'env') {
      options.env = value;
    } else if (key === 'mode') {
      if (!['bootstrap', 'platform'].includes(value)) {
        throw new Error(`Invalid --mode "${value}"; expected bootstrap or platform`);
      }
      options.mode = value;
    } else if (key === 'output-dir') {
      options.outputDir = value;
    } else if (key === 'source-ref') {
      options.sourceRef = value;
    } else if (key === 'artifact') {
      options.artifact = value;
    } else if (key === 'from') {
      options.from = value;
    } else if (key === 'to') {
      options.to = value;
    } else if (key === 'approval-id') {
      options.approvalId = value;
    } else if (key === 'correlation-id') {
      options.correlationId = value;
    } else if (key === 'tenant') {
      options.tenant = value;
    } else if (key === 'workspace') {
      options.workspace = value;
    } else if (key === 'project') {
      options.project = value;
    } else if (key === 'release-id') {
      options.releaseId = value;
    }
  }

  let mode = 'execute';
  let productId;
  let phase;

  if (positional[0] === 'product' && positional[1] === 'plan') {
    mode = 'plan';
    productId = positional[2];
    phase = positional[3];
  } else if (positional[0] === 'product') {
    phase = positional[1];
    productId = positional[2];
  } else if (positional[0] === 'plan') {
    mode = 'plan';
    productId = positional[1];
    phase = positional[2];
  } else {
    phase = positional[0];
    productId = positional[1];
  }

  if (!productId || !phase || !VALID_PHASES.has(phase)) {
    throw new Error('Usage: product plan <productId> <phase> [options]');
  }

  return { mode, productId, phase, options };
}

function createProviderContext(repoRoot, options) {
  if (options.mode === 'bootstrap') {
    return createBootstrapKernelProviders({ repoRoot }).context;
  }

  throw new Error(
    'Kernel platform mode requires the Data Cloud-backed provider bridge, which is not registered in this repository snapshot.',
  );
}

function assertApprovalSafety(plan, options) {
  const environment = options.env ?? plan.environment ?? 'local';
  const isProductionDeploy = plan.phase === 'deploy' && ['prod', 'production'].includes(environment);
  if (!isProductionDeploy && !options.requireApproval) {
    return;
  }

  if (options.requireApproval && !options.approvalId) {
    throw new Error('--require-approval requires --approval-id so approval evidence can be correlated');
  }

  const approvalId = options.approvalId;
  const matchingRequirement = approvalId
    ? plan.approvalRequirements.find((approval) => approval.approvalId === approvalId)
    : plan.approvalRequirements.find((approval) => approval.required);

  if (matchingRequirement === undefined) {
    throw new Error(
      `Lifecycle ${plan.phase} for ${environment} requires an explicit approval requirement before execution`,
    );
  }
  if (!matchingRequirement.requiredApprovers || matchingRequirement.requiredApprovers.length === 0) {
    throw new Error(
      `Approval requirement ${matchingRequirement.approvalId} must declare requiredApprovers before execution`,
    );
  }
}

function collectManifestPointers(outputDirectory, plan, result) {
  return {
    lifecyclePlan: join(outputDirectory, 'lifecycle-plan.json'),
    ...(result ? { lifecycleResult: join(outputDirectory, 'lifecycle-result.json') } : {}),
    ...(result?.manifestRefs ?? {}),
    runId: plan.runId,
    correlationId: plan.correlationId,
    providerMode: plan.providerMode,
  };
}

function createDryRunGateProviders(plan) {
  return Object.fromEntries(
    (plan.gates ?? []).map((gate) => [
      gate.providerId ?? gate.gateId,
      {
        providerId: gate.providerId ?? gate.gateId,
        version: '1.0.0',
        capabilities: ['gates', 'dry-run'],
        evaluateGate: async () => ({
          gateId: gate.gateId,
          passed: true,
          reason: `Dry-run gate evaluation for ${gate.gateId}`,
          evidence: [`dry-run:gate:${gate.gateId}`],
          evaluatedAt: new Date().toISOString(),
          duration: 0,
        }),
        getGateConfig: async () => gate,
        listGates: async () => [gate.gateId],
      },
    ]),
  );
}

async function writeLatestPointers(repoRoot, productId, phase, pointers) {
  const latestDir = join(repoRoot, '.kernel', 'out', 'products', productId, phase, 'latest');
  await mkdir(latestDir, { recursive: true });
  await writeFile(join(latestDir, 'run-id.txt'), pointers.runId);
  await writeFile(join(latestDir, 'manifest-pointers.json'), `${JSON.stringify(pointers, null, 2)}\n`);
}

async function main() {
  const { mode: commandMode, productId, phase, options } = parseArgs(process.argv.slice(2));
  const repoRoot = resolve('.');
  const providerContext = createProviderContext(repoRoot, options);

  const planner = new ProductLifecyclePlanner(repoRoot, undefined, providerContext);
  const plan = await planner.plan(productId, phase, {
    surfaceSelector: options.surfaces.length > 0 ? options.surfaces : undefined,
    environment: options.env,
    sourceRef: options.sourceRef,
    outputDir: options.outputDir,
    correlationId: options.correlationId,
    providerMode: options.mode,
  });

  const runId = plan.runId ?? `run-${Date.now()}`;
  const outputDirectory = plan.outputDirectory
    ?? join(repoRoot, '.kernel', 'out', 'products', productId, phase, runId);
  await mkdir(outputDirectory, { recursive: true });
  await writeFile(join(outputDirectory, 'lifecycle-plan.json'), `${JSON.stringify(plan, null, 2)}\n`);

  let result = null;
  if (commandMode !== 'plan') {
    assertApprovalSafety(plan, options);
    const logger = new ConsoleExecutionLogger('[kernel-product]');
    const collector = new ExecutionResultCollector(logger);
    const executionProviderContext = options.dryRun
      ? {
          ...providerContext,
          gates: {
            ...(providerContext.gates ?? {}),
            ...createDryRunGateProviders(plan),
          },
        }
      : providerContext;

    // Build real adapter registry with safe SpawnCommandRunner
    const { bridge } = createDefaultToolchainAdapterRegistry({ repoRoot });
    const runner = new ProductLifecycleStepRunner(bridge);
    const executor = new ProductLifecycleExecutor(runner, collector);

    result = await executor.executePlan(plan, {
      dryRun: options.dryRun,
      outputDirectory,
      environment: options.env,
      sourceRef: options.sourceRef,
      logger,
      providerContext: executionProviderContext,
    });

    await writeFile(join(outputDirectory, 'lifecycle-result.json'), `${JSON.stringify(result, null, 2)}\n`);
  }

  const manifestPointers = collectManifestPointers(outputDirectory, plan, result);
  await writeLatestPointers(repoRoot, productId, phase, manifestPointers);

  if (options.json) {
    console.log(JSON.stringify(commandMode === 'plan' ? plan : { plan, result, manifests: manifestPointers }, null, 2));
  } else {
    const status = result ? result.status : 'planned';
    const stepCount = plan.steps?.length ?? 0;
    const durationMs = result?.durationMs ?? 0;
    console.log(`\n[kernel] ${productId} / ${phase} — ${status.toUpperCase()} (${stepCount} steps, ${durationMs}ms)`);
    console.log(`  runId:         ${runId}`);
    console.log(`  correlationId: ${plan.correlationId}`);
    console.log(`  providerMode:  ${plan.providerMode}`);
    console.log(`  manifests:     ${join(repoRoot, '.kernel', 'out', 'products', productId, phase, 'latest', 'manifest-pointers.json')}\n`);
    if (result?.failure) {
      console.error(`  FAILURE: ${result.failure.message}`);
      if (result.failure.reasonCode) {
        console.error(`  REASON:  ${result.failure.reasonCode}`);
      }
      if (result.failure.cause) {
        console.error(`  CAUSE:   ${result.failure.cause}`);
      }
      console.error(`  VERIFY:  node scripts/kernel-product.mjs product plan ${productId} ${phase} --json`);
    }
  }

  if (result && result.status === 'failed') {
    process.exit(1);
  }
}

main().catch((error) => {
  const message = error instanceof Error ? error.message : String(error);
  console.error(message);
  process.exit(1);
});
