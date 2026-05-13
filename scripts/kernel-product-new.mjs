#!/usr/bin/env node

import { existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ProductLifecyclePlanner, ProductLifecycleExecutor, SchemaValidator } from '../platform/typescript/kernel-lifecycle/dist/index.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');

/**
 * Verify kernel packages are built
 */
function verifyKernelPackages() {
  const lifecyclePackage = join(repoRoot, 'platform/typescript/kernel-lifecycle/dist/index.js');

  if (!existsSync(lifecyclePackage)) {
    console.error('ERROR: Kernel lifecycle package is not built.');
    console.error('Run: pnpm build:kernel-lifecycle-platform');
    process.exit(1);
  }
}

/**
 * Parse command line options
 */
function parseOptions(tokens) {
  const options = {
    dryRun: false,
    json: false,
  };

  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    if (token === '--dry-run') {
      options.dryRun = true;
    } else if (token === '--json') {
      options.json = true;
    } else if (token.startsWith('--surface=')) {
      options.surface = token.split('=')[1];
    } else if (token === '--surface') {
      options.surface = tokens[++index];
    } else if (token.startsWith('--env=')) {
      options.environment = token.split('=')[1];
    } else if (token === '--env') {
      options.environment = tokens[++index];
    } else if (token.startsWith('--output-dir=')) {
      options.outputDir = token.split('=')[1];
    } else if (token === '--output-dir') {
      options.outputDir = tokens[++index];
    } else {
      throw new Error(`Unknown option: ${token}`);
    }
  }

  return options;
}

/**
 * Parse command line invocation
 */
function parseInvocation(argv) {
  if (argv.length === 0 || argv.includes('--help')) {
    printUsage(argv.includes('--help') ? 0 : 1);
  }

  if (argv[0] === 'product') {
    if (argv[1] === 'validate-lifecycle') {
      return { mode: 'validate-lifecycle' };
    }

    if (argv[1] === 'validate-adapters') {
      return { mode: 'validate-adapters' };
    }

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

/**
 * Print usage information
 */
function printUsage(exitCode = 1) {
  const stream = exitCode === 0 ? process.stdout : process.stderr;
  stream.write('Usage:\n');
  stream.write('  node scripts/kernel-product.mjs product validate-lifecycle\n');
  stream.write('  node scripts/kernel-product.mjs product validate-adapters\n');
  stream.write('  node scripts/kernel-product.mjs product plan <productId> <phase> [options]\n');
  stream.write('  node scripts/kernel-product.mjs product <phase> <productId> [options]\n');
  stream.write('  node scripts/kernel-product.mjs plan <productId> <phase> [options]\n');
  stream.write('  node scripts/kernel-product.mjs <phase> <productId> [options]\n');
  stream.write('\n');
  stream.write('Options:\n');
  stream.write('  --surface <surface>\n');
  stream.write('  --env <environment>\n');
  stream.write('  --dry-run\n');
  stream.write('  --json\n');
  stream.write('  --output-dir <path>\n');
  process.exit(exitCode);
}

/**
 * Main entry point
 */
async function main() {
  verifyKernelPackages();

  const invocation = parseInvocation(process.argv.slice(2));

  // Handle schema validation commands
  if (invocation.mode === 'validate-lifecycle') {
    const validator = new SchemaValidator(repoRoot);
    const result = await validator.validateProductLifecycleProfilesWithArtifacts();
    
    if (result.errors.length > 0) {
      console.error('Validation errors:');
      for (const error of result.errors) {
        console.error(`  ${error.path}: ${error.message}`);
      }
    }
    
    if (result.warnings.length > 0) {
      console.warn('Validation warnings:');
      for (const warning of result.warnings) {
        console.warn(`  ${warning.path}: ${warning.message}`);
      }
    }
    
    if (!result.valid) {
      process.exit(1);
    }
    console.log('Product lifecycle profiles schema is valid');
    return;
  }

  if (invocation.mode === 'validate-adapters') {
    const validator = new SchemaValidator(repoRoot);
    const result = await validator.validateToolchainAdapterRegistry();
    
    if (result.errors.length > 0) {
      console.error('Validation errors:');
      for (const error of result.errors) {
        console.error(`  ${error.path}: ${error.message}`);
      }
    }
    
    if (!result.valid) {
      process.exit(1);
    }
    console.log('Toolchain adapter registry schema is valid');
    return;
  }

  const planner = new ProductLifecyclePlanner(repoRoot);

  try {
    const plan = await planner.plan(
      invocation.productId,
      invocation.phase,
      {
        surface: invocation.options.surface,
        environment: invocation.options.environment,
      }
    );

    if (invocation.mode === 'plan') {
      console.log(JSON.stringify(plan, null, 2));
      return;
    }

    const executor = new ProductLifecycleExecutor(invocation.options.dryRun);
    const result = await executor.execute(plan);

    if (invocation.options.json) {
      console.log(JSON.stringify({ plan, result }, null, 2));
    } else {
      console.log(`Lifecycle execution completed: ${result.status}`);
    }

    if (result.status === 'failed') {
      process.exit(1);
    }
  } catch (error) {
    console.error(`Error: ${error instanceof Error ? error.message : String(error)}`);
    process.exit(1);
  }
}

main().catch((error) => {
  console.error(`Fatal error: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
});
