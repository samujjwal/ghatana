import { mkdir, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import {
  ConsoleExecutionLogger,
  ExecutionResultCollector,
  ProductLifecycleExecutor,
  ProductLifecyclePlanner,
  ProductLifecycleStepRunner,
} from '../platform/typescript/kernel-lifecycle/dist/index.js';

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
    outputDir: undefined,
    sourceRef: undefined,
    artifact: undefined,
    from: undefined,
    to: undefined,
    approvalId: undefined,
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

    const value = argv[index + 1];
    if (!value || value.startsWith('--')) {
      throw new Error(`Missing value for option --${key}`);
    }
    index += 1;

    if (key === 'surface' || key === 'surfaces') {
      options.surfaces.push(...value.split(',').map((v) => v.trim()).filter(Boolean));
    } else if (key === 'env') {
      options.env = value;
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

function createFallbackAdapterRegistry() {
  return {
    getAdapter(adapterId) {
      return {
        async execute() {
          throw new Error(
            `Step adapter ${adapterId} was invoked without planner-provided execution command.`,
          );
        },
      };
    },
  };
}

async function main() {
  const { mode, productId, phase, options } = parseArgs(process.argv.slice(2));
  const repoRoot = resolve('.');

  const planner = new ProductLifecyclePlanner(repoRoot);
  const plan = await planner.plan(productId, phase, {
    surfaceSelector: options.surfaces.length > 0 ? options.surfaces : undefined,
    environment: options.env,
    sourceRef: options.sourceRef,
    outputDir: options.outputDir,
  });

  const outputDirectory = plan.outputDirectory ?? join(repoRoot, '.kernel', 'out', 'products', productId, phase, 'latest');
  await mkdir(outputDirectory, { recursive: true });
  await writeFile(join(outputDirectory, 'lifecycle-plan.json'), `${JSON.stringify(plan, null, 2)}\n`);

  let result = null;
  if (mode !== 'plan') {
    const logger = new ConsoleExecutionLogger('[kernel-product]');
    const collector = new ExecutionResultCollector(logger);
    const runner = new ProductLifecycleStepRunner(createFallbackAdapterRegistry());
    const executor = new ProductLifecycleExecutor(runner, collector);

    result = await executor.executePlan(plan, {
      dryRun: options.dryRun,
      outputDirectory,
      environment: options.env,
      sourceRef: options.sourceRef,
      logger,
    });

    await writeFile(join(outputDirectory, 'lifecycle-result.json'), `${JSON.stringify(result, null, 2)}\n`);
  }

  console.log(JSON.stringify({ plan, result }, null, 2));
}

main().catch((error) => {
  const message = error instanceof Error ? error.message : String(error);
  console.error(message);
  process.exit(1);
});
