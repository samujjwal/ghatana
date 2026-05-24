import assert from 'node:assert/strict';
import test from 'node:test';

import {
  generateGateDependencyGraph,
  renderGateDependencyGraphMarkdown,
} from '../generate-gate-dependency-graph.mjs';

test('generates aggregate gate nodes, edges, tiers, owners, and targets', () => {
  const scripts = {
    'check:phase8': 'pnpm check:phase8:fast && pnpm check:phase8:e2e',
    'check:phase8:fast': 'pnpm --dir platform/typescript/kernel-lifecycle exec vitest run src/api/__tests__/KernelLifecycleApiHandlers.test.ts',
    'check:phase8:e2e': 'pnpm --dir platform/typescript/ghatana-studio exec playwright test e2e/artifact-deep-interactions.spec.ts',
    'check:release-gate': 'pnpm check:phase8 && node ./scripts/check-release.mjs',
  };

  const graph = generateGateDependencyGraph({ scripts });
  const fastNode = graph.nodes.find((node) => node.scriptName === 'check:phase8:fast');
  const e2eNode = graph.nodes.find((node) => node.scriptName === 'check:phase8:e2e');

  assert.equal(fastNode?.tier, 'fast');
  assert.equal(fastNode?.owner, 'platform/typescript/kernel-lifecycle');
  assert.equal(
    fastNode?.targetHints.includes('target:src/api/__tests__/KernelLifecycleApiHandlers.test.ts'),
    true,
  );
  assert.equal(e2eNode?.tier, 'e2e');
  assert.equal(e2eNode?.owner, 'platform/typescript/ghatana-studio');
  assert.equal(
    graph.edges.some((edge) => edge.from === 'check:release-gate' && edge.to === 'check:phase8'),
    true,
  );
});

test('renders stable markdown for graph review', () => {
  const graph = generateGateDependencyGraph({
    aggregateScripts: ['check:release-gate'],
    scripts: {
      'check:release-gate': 'pnpm check:unit',
      'check:unit': 'node ./scripts/check-unit.mjs',
    },
  });

  const markdown = renderGateDependencyGraphMarkdown(graph);

  assert.match(markdown, /# Gate Dependency Graph/u);
  assert.match(markdown, /`check:release-gate`/u);
  assert.match(markdown, /`check:release-gate` -> `check:unit`/u);
});
