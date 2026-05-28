#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { createChecker, readJson, readText, repoRoot } from './lib/yappc-release-check-utils.mjs';

const checker = createChecker({
  checkId: 'YAPPC-010 PHR import and generation roundtrip',
  evidencePath: '.kernel/evidence/yappc/phr-import-generation-roundtrip.json',
});

const gradle = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
const pnpm = process.platform === 'win32' ? 'pnpm.cmd' : 'pnpm';

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

checker.requireFile('products/phr/config/phr-route-contract.json');
checker.requireFile('products/phr/config/phr-usecase-baseline.json');
checker.requireFile('products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/PhrProductContractImporter.java');
checker.requireFile('products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/PhrProductUnitTemplatePack.java');
checker.requireFile('products/yappc/frontend/web/src/lib/phr/phrCompletenessOverlay.ts');
checker.requireFile('products/yappc/frontend/web/src/components/canvas/PhrCompletenessOverlay.tsx');
checker.requireFile('products/yappc/frontend/web/src/lib/canvas-ai/yappc-ai-adapter.ts');

const routeContract = readJson('products/phr/config/phr-route-contract.json');
const useCaseBaseline = readJson('products/phr/config/phr-usecase-baseline.json');
const stableRoutes = routeContract.routes.filter((route) => route.stability === 'stable');
const guardedRoutes = routeContract.routes.filter((route) => route.stability === 'hidden' || route.stability === 'blocked');

checker.record('PHR contract exposes stable routes for import', stableRoutes.length > 0, {
  stableRoutes: stableRoutes.length,
});
checker.record('PHR contract exposes guarded routes for canvas state overlay', guardedRoutes.length > 0, {
  guardedRoutes: guardedRoutes.length,
});
checker.record('PHR use-case baseline covers web mobile backend metadata', useCaseBaseline.usecases.some((useCase) =>
  Boolean(useCase.webRoute) && Boolean(useCase.mobileScreen) && Array.isArray(useCase.backendApis) && useCase.backendApis.length > 0
));

const importerSource = readText('products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/PhrProductContractImporter.java');
const templateSource = readText('products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/PhrProductUnitTemplatePack.java');
const overlaySource = readText('products/yappc/frontend/web/src/lib/phr/phrCompletenessOverlay.ts');
const canvasAiSource = readText('products/yappc/frontend/web/src/lib/canvas-ai/yappc-ai-adapter.ts');

checker.record('Importer emits Kernel ProductUnit intent', importerSource.includes('ProductUnitIntentExporter.Request'), {
  path: 'products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/PhrProductContractImporter.java',
});
checker.record('Template pack emits web mobile backend schema and journey artifacts', [
  'web-page',
  'mobile-screen',
  'java-route',
  'zod-schema',
  'backend-contract-test',
  'journey-test',
].every((token) => templateSource.includes(token)), {
  path: 'products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/PhrProductUnitTemplatePack.java',
});
checker.record('Canvas overlay models stable hidden blocked coverage', [
  'stableCoveragePercent',
  'hiddenRoutes',
  'blockedRoutes',
  'directLinkAllowed',
].every((token) => overlaySource.includes(token)), {
  path: 'products/yappc/frontend/web/src/lib/phr/phrCompletenessOverlay.ts',
});
checker.record('Canvas AI adapter reports degraded readiness instead of silent success', [
  'CanvasAIReadinessState',
  'degraded',
  'unavailable',
].every((token) => canvasAiSource.includes(token)), {
  path: 'products/yappc/frontend/web/src/lib/canvas-ai/yappc-ai-adapter.ts',
});

runCommand('YAPPC PHR importer and template pack tests', gradle, [
  ':products:yappc:core:scaffold:api:test',
  '--tests',
  'com.ghatana.yappc.kernel.PhrProductContractImporterTest',
  '--tests',
  'com.ghatana.yappc.kernel.PhrProductUnitTemplatePackTest',
  '--no-daemon',
]);
runCommand('YAPPC PHR canvas overlay and degraded AI tests', pnpm, [
  '--dir',
  'products/yappc/frontend/web',
  'exec',
  'vitest',
  'run',
  'src/lib/phr/__tests__/phrCompletenessOverlay.test.ts',
  'src/components/canvas/__tests__/CanvasOverlays.test.tsx',
  'src/lib/canvas-ai/__tests__/yappc-ai-adapter.test.ts',
]);

checker.finish({
  routeSummary: {
    routes: routeContract.routes.length,
    stableRoutes: stableRoutes.length,
    guardedRoutes: guardedRoutes.length,
    useCases: useCaseBaseline.usecases.length,
  },
});
