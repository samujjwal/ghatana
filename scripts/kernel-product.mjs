#!/usr/bin/env node

/**
 * Kernel product lifecycle CLI
 *
 * Usage:
 *   node scripts/kernel-product.mjs <command> <productId> [options]
 *
 * Examples:
 *   node scripts/kernel-product.mjs plan digital-marketing build
 *   node scripts/kernel-product.mjs plan digital-marketing dev --dry-run
 *   node scripts/kernel-product.mjs release digital-marketing
 *   node scripts/kernel-product.mjs promote digital-marketing --from dev --to staging
 *   node scripts/kernel-product.mjs promote digital-marketing --from staging --to prod
 *   node scripts/kernel-product.mjs rollback digital-marketing --env prod
 */

import { ProductLifecyclePlanner } from '../platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.js';
import {
  ProductReleaseManager,
  ProductPromotionPlanManager,
  ProductRollbackPlanManager,
  ProductApprovalGateManager,
} from '../platform/typescript/kernel-release/dist/index.js';

async function main() {
  const args = process.argv.slice(2);

  if (args.length < 2) {
    console.error('Usage: node scripts/kernel-product.mjs <command> <productId> [options]');
    console.error('');
    console.error('Commands:');
    console.error('  plan     - Generate a lifecycle plan (dry-run)');
    console.error('  release  - Create a release for a product');
    console.error('  promote  - Promote a product between environments');
    console.error('  rollback - Rollback a product to a previous version');
    console.error('');
    console.error('Examples:');
    console.error('  node scripts/kernel-product.mjs plan digital-marketing build');
    console.error('  node scripts/kernel-product.mjs plan digital-marketing dev --dry-run');
    console.error('  node scripts/kernel-product.mjs release digital-marketing');
    console.error('  node scripts/kernel-product.mjs promote digital-marketing --from dev --to staging');
    console.error('  node scripts/kernel-product.mjs promote digital-marketing --from staging --to prod');
    console.error('  node scripts/kernel-product.mjs rollback digital-marketing --env prod');
    process.exit(1);
  }

  const [command, productId, ...options] = args;

  try {
    const planner = new ProductLifecyclePlanner();
    const releaseManager = new ProductReleaseManager();
    const promotionManager = new ProductPromotionPlanManager();
    const rollbackManager = new ProductRollbackPlanManager();
    const approvalManager = new ProductApprovalGateManager();

    if (command === 'plan') {
      const phase = options[0];
      if (!phase) {
        console.error('Error: phase is required for plan command');
        process.exit(1);
      }

      const surfaceSelector = options
        .filter((opt) => opt.startsWith('--surfaces='))
        .map((opt) => opt.split('=')[1].split(','));

      const plan = await planner.plan(productId, phase, {
        surfaceSelector: surfaceSelector.length > 0 ? surfaceSelector : undefined,
      });

      console.log(JSON.stringify(plan, null, 2));
    } else if (command === 'release') {
      const version = options.find((opt) => opt.startsWith('--version='))?.split('=')[1] || 
                     options[options.indexOf('--version') + 1] || '1.0.0';
      const environment = options.find((opt) => opt.startsWith('--env='))?.split('=')[1] || 
                         options[options.indexOf('--env') + 1] || 'dev';

      const release = {
        productId,
        version,
        sourceRef: 'main',
        artifactManifest: `artifacts/${productId}-${version}-manifest.json`,
        deploymentManifest: `deploy/${productId}-${version}-manifest.json`,
        releaseManifest: `release/${productId}-${version}-manifest.json`,
        environment,
        timestamp: new Date().toISOString(),
        releasedBy: 'kernel-cli',
      };

      const createdRelease = releaseManager.createRelease(release);
      const validation = releaseManager.validateRelease(createdRelease);

      console.log(JSON.stringify({ release: createdRelease, validation }, null, 2));

      if (!validation.valid) {
        process.exit(1);
      }
    } else if (command === 'promote') {
      const fromEnv = options.find((opt) => opt.startsWith('--from='))?.split('=')[1] || 
                      options[options.indexOf('--from') + 1];
      const toEnv = options.find((opt) => opt.startsWith('--to='))?.split('=')[1] || 
                    options[options.indexOf('--to') + 1];

      if (!fromEnv || !toEnv) {
        console.error('Error: --from and --to are required for promote command');
        process.exit(1);
      }

      const promotionPlan = {
        productId,
        sourceEnvironment: fromEnv,
        targetEnvironment: toEnv,
        promotionRequirements: {
          artifactManifest: true,
          deploymentManifest: true,
          releaseManifest: true,
          securityChecks: true,
          privacyChecks: true,
          licenseChecks: true,
          conformanceChecks: true,
          e2eChecks: true,
          performanceChecks: true,
        },
        approvalGate: {
          required: toEnv === 'prod',
          approvers: toEnv === 'prod' ? ['tech-lead', 'product-manager'] : [],
          approved: toEnv !== 'prod',
        },
        rollbackPlan: {
          strategy: 'previous-artifact',
          previousArtifact: toEnv === 'prod' ? `${fromEnv}-latest` : undefined,
        },
      };

      const createdPlan = promotionManager.createPromotionPlan(promotionPlan);
      const validation = promotionManager.validatePromotionPlan(createdPlan);

      console.log(JSON.stringify({ promotionPlan: createdPlan, validation }, null, 2));

      if (!validation.valid) {
        process.exit(1);
      }
    } else if (command === 'rollback') {
      const envIndex = options.indexOf('--env');
      const environment = options.find((opt) => opt.startsWith('--env='))?.split('=')[1] || 
                         (envIndex !== -1 && options[envIndex + 1] && !options[envIndex + 1].startsWith('--') ? options[envIndex + 1] : undefined);
      
      const toIndex = options.indexOf('--to');
      const targetVersion = options.find((opt) => opt.startsWith('--to='))?.split('=')[1] || 
                            (toIndex !== -1 && options[toIndex + 1] && !options[toIndex + 1].startsWith('--') ? options[toIndex + 1] : undefined);
      
      const reasonIndex = options.indexOf('--reason');
      const reason = options.find((opt) => opt.startsWith('--reason='))?.split('=')[1] || 
                     (reasonIndex !== -1 && options[reasonIndex + 1] && !options[reasonIndex + 1].startsWith('--') ? options[reasonIndex + 1] : 'Rollback requested');

      if (!environment) {
        console.error('Error: --env is required for rollback command');
        process.exit(1);
      }

      const rollbackPlan = {
        productId,
        environment,
        currentVersion: 'latest',
        targetVersion: targetVersion || 'previous',
        strategy: 'previous-artifact',
        reason,
        rollbackBy: 'kernel-cli',
        timestamp: new Date().toISOString(),
        verificationPlan: {
          healthChecks: true,
          smokeTests: true,
          metrics: true,
        },
      };

      const createdPlan = rollbackManager.createRollbackPlan(rollbackPlan);
      const validation = rollbackManager.validateRollbackPlan(createdPlan);

      console.log(JSON.stringify({ rollbackPlan: createdPlan, validation }, null, 2));

      if (!validation.valid) {
        process.exit(1);
      }
    } else {
      console.error(`Unknown command: ${command}`);
      process.exit(1);
    }
  } catch (error) {
    console.error(`Error: ${error.message}`);
    process.exit(1);
  }
}

main();
