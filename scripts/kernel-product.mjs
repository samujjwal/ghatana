import { join, resolve } from 'node:path';
import {
  ConsoleExecutionLogger,
  ExecutionResultCollector,
  KernelLifecycleService,
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

const LIFECYCLE_ALIASES = new Map([
  ['develop', { phase: 'dev' }],
  ['ship-local', { phase: 'deploy', env: 'local' }],
  ['verify-local', { phase: 'verify', env: 'local' }],
]);

function isLifecycleIntent(value) {
  return VALID_PHASES.has(value) ||
    LIFECYCLE_ALIASES.has(value) ||
    value === 'status' ||
    value === 'recover';
}

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
    explain: false,
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
    if (key === 'explain') {
      options.explain = true;
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
    if (positional[2] === 'explain') {
      productId = positional[1];
      phase = positional[3];
      options.explain = true;
      mode = 'plan';
    } else if (!isLifecycleIntent(positional[1]) && isLifecycleIntent(positional[2])) {
      productId = positional[1];
      phase = positional[2];
    } else {
      phase = positional[1];
      productId = positional[2];
    }
  } else if (positional[0] === 'plan') {
    mode = 'plan';
    productId = positional[1];
    phase = positional[2];
  } else {
    phase = positional[0];
    productId = positional[1];
  }

  const alias = LIFECYCLE_ALIASES.get(phase);
  if (alias) {
    phase = alias.phase;
    options.env = options.env ?? alias.env;
  }

  if (!productId || !phase || !VALID_PHASES.has(phase)) {
    if (positional[0] === 'product' && (positional[1] === 'status' || positional[2] === 'status')) {
      return { mode: 'plan', productId: positional[2] === 'status' ? positional[1] : positional[2], phase: 'verify', options: { ...options, env: options.env ?? 'local', explain: true } };
    }
    if (positional[0] === 'product' && (positional[1] === 'recover' || positional[2] === 'recover')) {
      return { mode: 'recover', productId: positional[2] === 'recover' ? positional[1] : positional[2], phase: 'verify', options: { ...options, env: options.env ?? 'local', explain: true } };
    }
    throw new Error('Usage: product plan <productId> <phase> [options]');
  }

  return { mode, productId, phase, options };
}

function createProviderContext(repoRoot, options) {
  if (options.mode === 'bootstrap') {
    return createBootstrapKernelProviders({
      repoRoot,
      ...(options.outputDir
        ? {
            outputRoot: options.outputDir,
            allowOutputOutsideKernelOut: true,
          }
        : {}),
    }).context;
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

function assertAdapterContractCompliance(plan, registry) {
  const errors = [];
  
  // Check that all steps have adapter references
  const stepsWithoutAdapter = plan.steps.filter((step) => !step.adapter);
  if (stepsWithoutAdapter.length > 0) {
    errors.push(
      `Lifecycle execution requires all steps to have adapter contracts; ${stepsWithoutAdapter.length} steps missing adapter: ${stepsWithoutAdapter.map((s) => s.id).join(', ')}`,
    );
  }

  // Check that all adapter IDs are registered in the bridge
  const kernelNativeAdapters = new Set(['kernel-product-interaction-broker']);
  const adapterIds = new Set(
    plan.steps
      .filter((step) => step.stepKind !== 'interaction-preflight')
      .map((step) => step.adapter)
      .filter(Boolean),
  );
  const registeredAdapters = new Set(
    typeof registry.getAll === 'function'
      ? registry.getAll().map((adapter) => adapter.id)
      : [],
  );
  const unregisteredAdapters = [...adapterIds].filter((id) => !registeredAdapters.has(id) && !kernelNativeAdapters.has(id));
  if (unregisteredAdapters.length > 0) {
    errors.push(
      `Lifecycle execution requires all adapters to be registered; unregistered adapters: ${unregisteredAdapters.join(', ')}`,
    );
  }

  // Check that no step attempts to bypass adapter with raw command execution
  const stepsWithRawCommand = plan.steps.filter((step) => 
    step.execution && !step.adapter
  );
  if (stepsWithRawCommand.length > 0) {
    errors.push(
      `Lifecycle execution must go through adapter contracts; ${stepsWithRawCommand.length} steps attempt raw command execution: ${stepsWithRawCommand.map((s) => s.id).join(', ')}`,
    );
  }

  if (errors.length > 0) {
    throw new Error(`Adapter contract compliance check failed:\n${errors.map((e) => `  - ${e}`).join('\n')}`);
  }
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

function collectManifestPointers(plan, result) {
  return {
    lifecyclePlan: join(plan.outputDirectory, 'lifecycle-plan.json'),
    ...(result
      ? {
          lifecycleResult: result.manifestRefs?.lifecycleResult ?? join(plan.outputDirectory, 'lifecycle-result.json'),
        }
      : {}),
    ...(result?.manifestRefs ?? {}),
    runId: plan.runId,
    correlationId: plan.correlationId,
    providerMode: plan.providerMode,
  };
}

function createLifecycleService({ repoRoot, providerContext, planner, executor, options }) {
  return new KernelLifecycleService({
    repoRoot,
    providerContext,
    planner,
    executor,
    logger: {
      info: () => undefined,
      warn: (message, meta) => {
        if (!options.json) {
          console.warn(`[kernel-product] ${message}`, meta ?? '');
        }
      },
      error: (message, meta) => {
        if (!options.json) {
          console.error(`[kernel-product] ${message}`, meta ?? '');
        }
      },
    },
  });
}

function explainPlan(plan) {
  return {
    productId: plan.productId,
    productUnitId: plan.productUnitId,
    phase: plan.phase,
    providerMode: plan.providerMode,
    surfaces: plan.surfaces.map((surface) => ({
      surfaceId: surface.surfaceId ?? surface.surface,
      type: surface.type,
      adapter: surface.adapter,
    })),
    adapterIds: plan.adapterIds ?? [...new Set(plan.steps.map((step) => step.adapter).filter(Boolean))],
    gates: plan.gates.map((gate) => ({
      gateId: gate.gateId,
      required: gate.required,
      providerId: gate.providerId,
    })),
    approvals: plan.approvalRequirements.map((approval) => ({
      approvalId: approval.approvalId,
      action: approval.action,
      required: approval.required,
      riskLevel: approval.riskLevel,
      requiredApprovers: approval.requiredApprovers ?? [],
    })),
    expectedArtifacts: plan.expectedArtifacts.map((artifact) => ({
      artifactId: artifact.artifactId ?? artifact.semanticRef,
      type: artifact.type,
      surface: artifact.surface,
      required: artifact.required,
    })),
    interactionPreflights: (plan.interactionPreflights ?? []).map((preflight) => ({
      contractId: preflight.contractId,
      providerProductId: preflight.providerProductId,
      consumerProductId: preflight.consumerProductId,
      mode: preflight.mode,
      required: preflight.required,
      status: preflight.status,
      reasonCode: preflight.reasonCode,
      evidenceRequired: preflight.evidenceRequired,
      evidenceRefs: preflight.evidenceRefs ?? [],
    })),
    healthChecks: (plan.healthChecks ?? []).map((hc) => ({
      surface: hc.surface,
      type: hc.type,
      ...(hc.livePath !== undefined ? { livePath: hc.livePath } : {}),
      ...(hc.readyPath !== undefined ? { readyPath: hc.readyPath } : {}),
      ...(hc.defaultPort !== undefined ? { defaultPort: hc.defaultPort } : {}),
    })),
    warnings: plan.warnings ?? [],
    blockingReasons: plan.blockingReasons ?? [],
  };
}

function recoverPlan(plan) {
  const blockingReasons = plan.blockingReasons ?? [];
  const warnings = plan.warnings ?? [];
  const interactionActions = (plan.interactionPreflights ?? [])
    .filter((preflight) => preflight.status === 'blocked')
    .map((preflight) => ({
      reasonCode: preflight.reasonCode ?? 'product_interaction.blocked',
      action: `Resolve interaction contract ${preflight.contractId} with provider ${preflight.providerProductId}`,
      evidenceRequired: preflight.evidenceRequired,
    }));
  const approvalActions = plan.approvalRequirements
    .filter((approval) => approval.required && (!approval.requiredApprovers || approval.requiredApprovers.length === 0))
    .map((approval) => ({
      reasonCode: 'approval.required_approvers_missing',
      action: `Add required approvers for ${approval.approvalId}`,
      riskLevel: approval.riskLevel,
    }));

  return {
    productId: plan.productId,
    phase: plan.phase,
    status: blockingReasons.length > 0 ? 'blocked' : 'ready',
    summary: blockingReasons.length > 0
      ? `${blockingReasons.length} blocking reason(s) must be resolved before execution`
      : 'No blocking reasons detected in the latest lifecycle plan',
    actions: [
      ...blockingReasons.map((reasonCode) => ({
        reasonCode,
        action: `Resolve lifecycle blocker: ${reasonCode}`,
      })),
      ...interactionActions,
      ...approvalActions,
      ...warnings.map((warning) => ({
        reasonCode: 'lifecycle.warning',
        action: `Review lifecycle warning: ${warning}`,
      })),
    ],
    verifyCommand: `node scripts/kernel-product.mjs product plan ${plan.productId} ${plan.phase} --json --explain`,
    executeCommand: `node scripts/kernel-product.mjs product ${plan.phase} ${plan.productId} --dry-run --json`,
  };
}

async function main() {
  const { mode: commandMode, productId, phase, options } = parseArgs(process.argv.slice(2));
  const repoRoot = resolve('.');
  const providerContext = createProviderContext(repoRoot, options);

  const planner = new ProductLifecyclePlanner(repoRoot, undefined, providerContext);
  const planningService = createLifecycleService({
    repoRoot,
    providerContext,
    planner,
    options,
  });
  const plan = await planningService.createLifecyclePlan(productId, phase, {
    surfaceSelector: options.surfaces.length > 0 ? options.surfaces : undefined,
    environment: options.env,
    sourceRef: options.sourceRef,
    outputDir: options.outputDir,
    correlationId: options.correlationId,
    providerMode: options.mode,
  });

  let result = null;
  if (commandMode === 'execute') {
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
    const { registry, bridge } = createDefaultToolchainAdapterRegistry({ repoRoot });
    
    // Enforce adapter contract compliance before execution
    assertAdapterContractCompliance(plan, registry);
    
    const runner = new ProductLifecycleStepRunner(bridge);
    const executor = new ProductLifecycleExecutor(runner, collector);
    const executionService = createLifecycleService({
      repoRoot,
      providerContext: executionProviderContext,
      planner,
      executor,
      options,
    });

    result = await executionService.executeLifecyclePlan(plan, {
      dryRun: options.dryRun,
      environment: options.env,
      sourceRef: options.sourceRef,
    });
  }

  const manifestPointers = collectManifestPointers(plan, result);
  const recovery = commandMode === 'recover' ? recoverPlan(plan) : undefined;

  if (options.json) {
    const payload = commandMode === 'plan'
      ? (options.explain ? { plan, explain: explainPlan(plan) } : plan)
      : commandMode === 'recover'
        ? { plan, recover: recovery, manifests: manifestPointers, ...(options.explain ? { explain: explainPlan(plan) } : {}) }
        : { plan, result, manifests: manifestPointers, ...(options.explain ? { explain: explainPlan(plan) } : {}) };
    console.log(JSON.stringify(payload, null, 2));
  } else {
    const status = recovery ? recovery.status : result ? result.status : 'planned';
    const stepCount = plan.steps?.length ?? 0;
    const durationMs = result?.durationMs ?? 0;
    console.log(`\n[kernel] ${productId} / ${phase} — ${status.toUpperCase()} (${stepCount} steps, ${durationMs}ms)`);
    console.log(`  runId:         ${plan.runId}`);
    console.log(`  correlationId: ${plan.correlationId}`);
    console.log(`  providerMode:  ${plan.providerMode}`);
    console.log(`  manifests:     ${join(repoRoot, '.kernel', 'out', 'products', productId, phase, 'latest', 'manifest-pointers.json')}\n`);
    if (options.explain) {
      const explanation = explainPlan(plan);
      console.log(`  surfaces:      ${explanation.surfaces.map((surface) => surface.surfaceId).join(', ') || 'none'}`);
      console.log(`  adapters:      ${explanation.adapterIds.join(', ') || 'none'}`);
      console.log(`  gates:         ${explanation.gates.map((gate) => gate.gateId).join(', ') || 'none'}`);
      console.log(`  approvals:     ${explanation.approvals.map((approval) => approval.approvalId).join(', ') || 'none'}`);
      console.log(`  artifacts:     ${explanation.expectedArtifacts.map((artifact) => artifact.artifactId).join(', ') || 'none'}\n`);
    }
    if (recovery) {
      console.log(`  recovery:      ${recovery.summary}`);
      for (const action of recovery.actions.slice(0, 5)) {
        console.log(`  action:        ${action.action}`);
      }
      console.log(`  verify:        ${recovery.verifyCommand}\n`);
    }
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
