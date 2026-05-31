#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { createChecker, readJson, readText, repoRoot } from './lib/yappc-release-check-utils.mjs';

const checker = createChecker({
  checkId: 'YAPPC-010 Kernel product contract import and generation roundtrip',
  evidencePath: '.kernel/evidence/yappc/product-contract-import-generation-roundtrip.json',
});

const [routeContractArg, useCaseBaselineArg] = process.argv.slice(2);
const routeContractPath = routeContractArg ?? 'products/yappc/core/scaffold/api/src/test/resources/kernel/sample-route-contract.json';
const useCaseBaselinePath = useCaseBaselineArg ?? 'products/yappc/core/scaffold/api/src/test/resources/kernel/sample-usecase-baseline.json';
const gradle = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

function runCommand(name, command, args, cwd = repoRoot) {
  try {
    if (process.platform === 'win32') {
      execFileSync('cmd.exe', ['/d', '/s', '/c', command, ...args], { cwd, stdio: 'inherit' });
    } else {
      execFileSync(command, args, { cwd, stdio: 'inherit' });
    }
    checker.record(name, true, { command: [command, ...args].join(' ') });
  } catch (error) {
    checker.record(name, false, {
      command: [command, ...args].join(' '),
      message: error instanceof Error ? error.message : String(error),
    });
  }
}

checker.requireFile(routeContractPath, 'Kernel route contract input exists');
checker.requireFile(useCaseBaselinePath, 'Kernel use-case baseline input exists');
checker.requireFile('products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/KernelProductContractImporter.java');
checker.requireFile('products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/KernelProductUnitTemplatePack.java');
checker.requireFile('products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/KernelBackendRouteGenerator.java');

const routeContract = readJson(routeContractPath);
const useCaseBaseline = readJson(useCaseBaselinePath);
const routes = Array.isArray(routeContract.routes) ? routeContract.routes : [];
const useCases = Array.isArray(useCaseBaseline.usecases) ? useCaseBaseline.usecases : [];
const stableRoutes = routes.filter((route) => route.stability === 'stable');

checker.record('Product contract declares a product id', typeof routeContract.product === 'string' && routeContract.product.trim().length > 0, {
  product: routeContract.product,
});
checker.record('Product contract exposes stable routes for import', stableRoutes.length > 0, {
  stableRoutes: stableRoutes.length,
});
checker.record('Use-case baseline covers web mobile backend metadata', useCases.some((useCase) =>
  Boolean(useCase.webRoute) && Boolean(useCase.mobileScreen) && Array.isArray(useCase.backendApis) && useCase.backendApis.length > 0
));

const importerSource = readText('products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/KernelProductContractImporter.java');
const templateSource = readText('products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/KernelProductUnitTemplatePack.java');
const backendGeneratorSource = readText('products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/KernelBackendRouteGenerator.java');

checker.record('Importer emits Kernel ProductUnit intent', importerSource.includes('ProductUnitIntentExporter.Request'));
checker.record('Importer does not pin a product id', !importerSource.includes('Expected ') && !importerSource.includes('route contract, got:'));
checker.record('Template pack emits web mobile backend schema and journey artifacts', [
  'web-page',
  'mobile-screen',
  'java-route',
  'zod-schema',
  'backend-contract-test',
  'journey-test',
].every((token) => templateSource.includes(token)));
checker.record('Backend generator derives package and policy types from product id', [
  'toPackageSegment',
  'toJavaTypePrefix',
  'canAccessResourceAsync',
].every((token) => backendGeneratorSource.includes(token)));

runCommand('YAPPC generic Kernel product importer and generator tests', gradle, [
  ':products:yappc:core:scaffold:api:test',
  '--tests',
  'com.ghatana.yappc.kernel.KernelProductContractImporterTest',
  '--tests',
  'com.ghatana.yappc.kernel.KernelProductUnitTemplatePackTest',
  '--tests',
  'com.ghatana.yappc.kernel.KernelBackendRouteGeneratorTest',
  '--no-daemon',
]);

checker.finish({
  inputs: {
    routeContractPath: path.normalize(routeContractPath).replaceAll(path.sep, '/'),
    useCaseBaselinePath: path.normalize(useCaseBaselinePath).replaceAll(path.sep, '/'),
  },
  routeSummary: {
    product: routeContract.product,
    routes: routes.length,
    stableRoutes: stableRoutes.length,
    useCases: useCases.length,
  },
});
