#!/usr/bin/env node

import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const productRoot = path.join(repoRoot, 'products/yappc');
const evidencePath = path.join(repoRoot, '.kernel/evidence/yappc/feature-completeness-matrix.json');
const markdownPath = path.join(productRoot, 'docs/audits/yappc-feature-completeness-matrix.md');

const statusOrder = [
  'WORKING',
  'PARTIAL',
  'CLAIMED_NOT_WIRED',
  'WIRED_NOT_TESTED',
  'BROKEN',
  'DUPLICATE_OR_CONFLICTING',
  'DEPRECATED_OR_DEAD',
];

const ignoreSegments = new Set([
  '.git',
  '.gradle',
  'build',
  'dist',
  'node_modules',
  '.turbo',
  'coverage',
  'tests-output',
  'generated',
]);

function rel(absPath) {
  return path.relative(repoRoot, absPath).replaceAll(path.sep, '/');
}

function readText(relativePath) {
  const absolutePath = path.join(repoRoot, relativePath);
  return existsSync(absolutePath) ? readFileSync(absolutePath, 'utf8') : '';
}

function walk(dir, predicate = () => true) {
  if (!existsSync(dir)) return [];
  const output = [];
  const stack = [dir];
  while (stack.length > 0) {
    const current = stack.pop();
    for (const entry of readdirSync(current, { withFileTypes: true })) {
      if (ignoreSegments.has(entry.name)) continue;
      const absolute = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(absolute);
      } else if (predicate(absolute)) {
        output.push(absolute);
      }
    }
  }
  return output.sort((a, b) => rel(a).localeCompare(rel(b)));
}

const sourceFiles = walk(productRoot, (file) => {
  const relative = rel(file);
  if (relative.includes('/clients/generated/') || relative.includes('/stories/data/')) return false;
  return /\.(java|ts|tsx|js|mjs|yaml|yml|json|md|openapi\.yaml)$/.test(file);
});
const implementationFiles = sourceFiles.filter((file) =>
  /\/(src|config|api|scripts|platform|services|tools)\//.test(`/${rel(file)}`) &&
  !/\/(src\/test|__tests__|test\/|tests\/|e2e|docs\/|web-page-specs\/)/.test(`/${rel(file)}`),
);
const testFiles = sourceFiles.filter((file) =>
  /\/(src\/test|__tests__|test\/|tests\/|e2e|integrationTest\/)/.test(`/${rel(file)}`),
);
const implementationFileSet = new Set(implementationFiles.map((file) => rel(file)));

const implementationIndex = implementationFiles.map((file) => {
  const relative = rel(file);
  return [relative, relative.toLowerCase()];
});
const testIndex = testFiles.map((file) => {
  const relative = rel(file);
  return [relative, relative.toLowerCase()];
});

function words(value) {
  return String(value)
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter(
      (word) =>
        word.length >= 3 &&
        ![
          'yappc',
          'api',
          'src',
          'main',
          'java',
          'test',
          'tests',
          'agent',
          'service',
          'controller',
          'workflow',
          'feature',
          'task',
          'config',
          'route',
          'routes',
          'request',
          'response',
        ].includes(word),
    );
}

function keyFor(value) {
  return words(value).slice(0, 6).join('-') || String(value).toLowerCase().replace(/[^a-z0-9]+/g, '-');
}

function tokenScore(text, tokens) {
  return tokens.reduce((score, token) => score + (text.includes(token) ? 1 : 0), 0);
}

function findRefs(candidates, feature, minScore = 1, limit = 6) {
  const tokens = words([feature.name, feature.id, feature.path, feature.operationId].filter(Boolean).join(' '));
  if (tokens.length === 0) return [];
  return [...candidates]
    .map(([file, content]) => ({
      file,
      score: tokenScore(file, tokens) + tokenScore(content, tokens),
    }))
    .filter((entry) => entry.score >= minScore)
    .sort((a, b) => b.score - a.score || a.file.localeCompare(b.file))
    .slice(0, limit)
    .map((entry) => entry.file);
}

function buildStatus(feature, duplicateKeys) {
  const hasImplementation = feature.implementationRefs.length > 0;
  const hasWiring = feature.wiringRefs.length > 0;
  const hasTests = feature.testRefs.length > 0;
  const lower = `${feature.name} ${feature.path ?? ''} ${feature.sourceRefs.join(' ')}`.toLowerCase();

  if (duplicateKeys.has(feature.identityKey)) return 'DUPLICATE_OR_CONFLICTING';
  if (/deprecated|legacy|dead|preserved for deep-links/.test(lower)) return 'DEPRECATED_OR_DEAD';
  if (!hasImplementation && !hasWiring) return 'CLAIMED_NOT_WIRED';
  if (!hasImplementation && hasWiring) return 'BROKEN';
  if (hasImplementation && !hasWiring) return 'PARTIAL';
  if (!hasTests) return 'WIRED_NOT_TESTED';
  return 'WORKING';
}

function addFeature(features, feature) {
  const identityKey = `${feature.surface}:${keyFor(feature.name)}:${feature.path ?? ''}`;
  features.push({
    id: `${feature.surface}:${keyFor(feature.path ?? feature.name ?? feature.id)}`,
    identityKey,
    name: feature.name,
    surface: feature.surface,
    category: feature.category,
    path: feature.path ?? null,
    method: feature.method ?? null,
    operationId: feature.operationId ?? null,
    sourceRefs: [...new Set(feature.sourceRefs ?? [])].sort(),
    implementationRefs: [],
    wiringRefs: [],
    testRefs: [],
    status: 'BROKEN',
    notes: feature.notes ?? '',
  });
}

function collectOpenApiFeatures(features) {
  for (const apiFile of ['products/yappc/api/yappc-api.openapi.yaml', 'products/yappc/api/yappc-refactorer.openapi.yaml']) {
    const source = readText(apiFile);
    if (!source) continue;
    const doc = YAML.parse(source);
    for (const [routePath, operations] of Object.entries(doc.paths ?? {})) {
      for (const method of ['get', 'post', 'put', 'patch', 'delete']) {
        const operation = operations?.[method];
        if (!operation) continue;
        addFeature(features, {
          name: operation.summary ?? operation.operationId ?? `${method.toUpperCase()} ${routePath}`,
          surface: 'API',
          category: (operation.tags?.[0] ?? 'HTTP').toString(),
          path: routePath,
          method: method.toUpperCase(),
          operationId: operation.operationId ?? null,
          sourceRefs: [apiFile],
        });
      }
    }
  }
}

function collectFastifyRouteFeatures(features) {
  const routeDir = path.join(productRoot, 'frontend/apps/api/src/routes');
  for (const file of walk(routeDir, (candidate) => candidate.endsWith('.ts'))) {
    const source = readFileSync(file, 'utf8');
    const routeMatches = source.matchAll(/\b(?:fastify|app|instance|server)\.(get|post|put|patch|delete)\(\s*['"`]([^'"`]+)['"`]/g);
    for (const match of routeMatches) {
      addFeature(features, {
        name: `${path.basename(file, '.ts')} ${match[1].toUpperCase()} ${match[2]}`,
        surface: 'API',
        category: 'Fastify route',
        path: match[2],
        method: match[1].toUpperCase(),
        sourceRefs: [rel(file)],
      });
    }
  }
}

function collectUiFeatures(features) {
  const routesFile = 'products/yappc/frontend/web/src/routes.ts';
  const source = readText(routesFile);
  for (const match of source.matchAll(/\b(route|index|layout)\(\s*(?:['"`]([^'"`]+)['"`]\s*,\s*)?['"`]([^'"`]+)['"`]/g)) {
    const type = match[1];
    const routePath = type === 'index' ? '(index)' : match[2];
    const target = match[3];
    addFeature(features, {
      name: `UI route ${routePath}`,
      surface: 'UI',
      category: type,
      path: routePath,
      sourceRefs: [
        routesFile,
        `products/yappc/frontend/web/src/${target}`,
      ].filter((candidate) => existsSync(path.join(repoRoot, candidate))),
      notes: target,
    });
  }
}

function collectJavaClassFeatures(features, rootRelative, surface, categoryFromPath) {
  const root = path.join(repoRoot, rootRelative);
  for (const file of walk(root, (candidate) => candidate.endsWith('.java') && candidate.includes(`${path.sep}src${path.sep}main${path.sep}`))) {
    const source = readFileSync(file, 'utf8');
    const classMatch = source.match(/\b(?:public\s+)?(?:final\s+)?(?:class|interface|record|enum)\s+([A-Z][A-Za-z0-9_]*)/);
    if (!classMatch) continue;
    const className = classMatch[1];
    if (/(Dto|Request|Response|Result|Spec|Config|Exception|Context|Model|Type|Status|State)$/.test(className)) continue;
    addFeature(features, {
      name: className.replace(/([a-z])([A-Z])/g, '$1 $2'),
      surface,
      category: categoryFromPath(rel(file), className),
      sourceRefs: [rel(file)],
    });
  }
}

function collectSdkFeatures(features) {
  const clientFile = 'products/yappc/core/yappc-shared/src/main/java/com/ghatana/yappc/client/YAPPCClient.java';
  const source = readText(clientFile);
  for (const match of source.matchAll(/\b(?:default\s+)?(?:<[^>]+>\s+)?Promise<[^>]+>\s+([a-z][A-Za-z0-9_]*)\(/g)) {
    addFeature(features, {
      name: `YAPPCClient.${match[1]}`,
      surface: 'SDK/facade',
      category: 'Java client',
      sourceRefs: [clientFile],
    });
  }
}

function collectAgentAndWorkflowFeatures(features) {
  const agentFiles = walk(path.join(productRoot, 'config/agents'), (file) => /\.(yaml|yml)$/.test(file));
  for (const file of agentFiles) {
    const relative = rel(file);
    const source = readFileSync(file, 'utf8');
    const parsed = YAML.parse(source) ?? {};
    const id = parsed.id ?? parsed.agentId ?? parsed.name ?? path.basename(file, path.extname(file));
    addFeature(features, {
      name: `Agent ${id}`,
      surface: 'agent workflows',
      category: relative.includes('/definitions/') ? 'agent definition' : 'agent catalog',
      sourceRefs: [relative],
    });
  }

  for (const file of walk(path.join(productRoot, 'config/workflows'), (candidate) => /\.(yaml|yml)$/.test(candidate))) {
    const relative = rel(file);
    const parsed = YAML.parse(readFileSync(file, 'utf8')) ?? {};
    for (const workflow of parsed.workflows ?? []) {
      addFeature(features, {
        name: `Workflow ${workflow.id ?? workflow.name}`,
        surface: 'agent workflows',
        category: 'canonical workflow',
        sourceRefs: [relative],
      });
    }
  }
}

function collectScaffoldGeneratorFeatures(features) {
  const roots = [
    'products/yappc/core/scaffold/generators',
    'products/yappc/core/scaffold/templates',
    'products/yappc/core/scaffold/engine',
  ];
  for (const root of roots) {
    collectJavaClassFeatures(features, root, 'scaffold generators', (file) =>
      file.includes('/templates/') ? 'template engine' : file.includes('/generators/') ? 'generator' : 'scaffold engine',
    );
  }
  for (const template of walk(path.join(productRoot, 'core/scaffold/templates'), (file) => /\.(hbs|mustache|yaml|yml)$/.test(file))) {
    addFeature(features, {
      name: `Template ${path.basename(template)}`,
      surface: 'scaffold generators',
      category: 'template',
      sourceRefs: [rel(template)],
    });
  }
}

function collectAllFeatures() {
  const features = [];
  collectOpenApiFeatures(features);
  collectFastifyRouteFeatures(features);
  collectUiFeatures(features);
  collectSdkFeatures(features);
  collectJavaClassFeatures(features, 'products/yappc/core/yappc-facades', 'SDK/facade', () => 'facade module');
  collectJavaClassFeatures(features, 'products/yappc/core/yappc-services', 'service interfaces', (file, className) =>
    className.endsWith('Controller') ? 'controller' : className.endsWith('Service') ? 'service' : 'service support',
  );
  collectJavaClassFeatures(features, 'products/yappc/core/yappc-infrastructure', 'service interfaces', () => 'infrastructure');
  collectJavaClassFeatures(features, 'products/yappc/platform', 'service interfaces', () => 'platform service');
  collectJavaClassFeatures(features, 'products/yappc/core/agents', 'agent workflows', (file) =>
    file.includes('/workflow/') ? 'workflow step' : 'agent runtime',
  );
  collectAgentAndWorkflowFeatures(features);
  collectScaffoldGeneratorFeatures(features);
  collectJavaClassFeatures(features, 'products/yappc/core/knowledge-graph', 'knowledge graph', () => 'knowledge graph');
  collectJavaClassFeatures(features, 'products/yappc/core/refactorer', 'refactorer', () => 'refactorer');
  return features;
}

function enrichFeatures(features) {
  const counts = new Map();
  for (const feature of features) {
    counts.set(feature.identityKey, (counts.get(feature.identityKey) ?? 0) + 1);
  }
  const duplicateKeys = new Set([...counts.entries()].filter(([, count]) => count > 1).map(([key]) => key));

  for (const feature of features) {
    const sourceImplementationRefs = feature.sourceRefs.filter((sourceRef) =>
      implementationFileSet.has(sourceRef),
    );
    feature.implementationRefs = [
      ...new Set([...sourceImplementationRefs, ...findRefs(implementationIndex, feature, 1, 8)]),
    ].slice(0, 8);
    feature.wiringRefs = [
      ...new Set([...sourceImplementationRefs, ...findRefs(implementationIndex, feature, 2, 5)]),
    ].slice(0, 5);
    feature.testRefs = findRefs(testIndex, feature, 1, 8);
    feature.status = buildStatus(feature, duplicateKeys);
    delete feature.identityKey;
  }
  return features.sort((a, b) => a.surface.localeCompare(b.surface) || a.name.localeCompare(b.name));
}

function buildMatrix() {
  const features = enrichFeatures(collectAllFeatures());
  const summary = Object.fromEntries(statusOrder.map((status) => [status, 0]));
  for (const feature of features) {
    summary[feature.status] += 1;
  }
  return {
    productId: 'yappc',
    generatedAt: new Date().toISOString(),
    statuses: statusOrder,
    summary: {
      totalFeatures: features.length,
      byStatus: summary,
      bySurface: features.reduce((acc, feature) => {
        acc[feature.surface] = (acc[feature.surface] ?? 0) + 1;
        return acc;
      }, {}),
    },
    features,
  };
}

function renderRefs(refs) {
  return refs.length > 0 ? refs.slice(0, 3).map((item) => `\`${item}\``).join('<br>') : '';
}

function renderMarkdown(matrix) {
  const lines = [
    '# YAPPC UI/API Feature Completeness Matrix',
    '',
    '> Auto-generated by `scripts/check-yappc-feature-completeness-matrix.mjs`.',
    '> Do not mark a feature `WORKING` unless implementation, wiring, and tests are all present.',
    '',
    '## Summary',
    '',
    '| Metric | Value |',
    '| --- | ---: |',
    `| Total features inventoried | ${matrix.summary.totalFeatures} |`,
    ...statusOrder.map((status) => `| ${status} | ${matrix.summary.byStatus[status]} |`),
    '',
    '## Surface Counts',
    '',
    '| Surface | Features |',
    '| --- | ---: |',
    ...Object.entries(matrix.summary.bySurface)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([surface, count]) => `| ${surface} | ${count} |`),
    '',
    '## Matrix',
    '',
    '| Surface | Feature | Status | Source | Implementation | Wiring | Tests |',
    '| --- | --- | --- | --- | --- | --- | --- |',
  ];

  for (const feature of matrix.features) {
    lines.push(
      `| ${feature.surface} | ${feature.name.replaceAll('|', '\\|')} | ${feature.status} | ${renderRefs(feature.sourceRefs)} | ${renderRefs(feature.implementationRefs)} | ${renderRefs(feature.wiringRefs)} | ${renderRefs(feature.testRefs)} |`,
    );
  }

  return `${lines.join('\n')}\n`;
}

function ensureParent(file) {
  mkdirSync(path.dirname(file), { recursive: true });
}

function main() {
  const matrix = buildMatrix();
  const json = `${JSON.stringify(matrix, null, 2)}\n`;
  const markdown = renderMarkdown(matrix);

  ensureParent(evidencePath);
  ensureParent(markdownPath);
  writeFileSync(evidencePath, json);
  writeFileSync(markdownPath, markdown);

  const invalidStatuses = matrix.features.filter((feature) => !statusOrder.includes(feature.status));
  if (invalidStatuses.length > 0) {
    throw new Error(`Invalid YAPPC feature statuses: ${invalidStatuses.map((feature) => feature.name).join(', ')}`);
  }

  const overclaimed = matrix.features.filter(
    (feature) =>
      feature.status === 'WORKING' &&
      (feature.implementationRefs.length === 0 || feature.wiringRefs.length === 0 || feature.testRefs.length === 0),
  );
  if (overclaimed.length > 0) {
    throw new Error(`YAPPC features claimed WORKING without complete evidence: ${overclaimed.map((feature) => feature.name).join(', ')}`);
  }

  console.log(`YAPPC feature completeness matrix generated: ${rel(evidencePath)}`);
  console.log(`YAPPC feature completeness audit generated: ${rel(markdownPath)}`);
  console.log(`Inventoried ${matrix.summary.totalFeatures} YAPPC features.`);
}

try {
  main();
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
