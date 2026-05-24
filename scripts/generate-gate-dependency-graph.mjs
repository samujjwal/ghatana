#!/usr/bin/env node
/**
 * @fileoverview Generates a dependency graph for aggregate release gates.
 *
 * @doc.type script
 * @doc.purpose Make aggregate gate ownership, tiering, and dependencies visible
 * @doc.layer governance
 * @doc.pattern ReportGenerator
 */

import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const DEFAULT_AGGREGATE_SCRIPTS = [
  'check:phase8',
  'check:release-gate',
];

function parseArgs(argv) {
  const options = {
    packageJsonPath: path.resolve('package.json'),
    aggregateScripts: [...DEFAULT_AGGREGATE_SCRIPTS],
    format: 'markdown',
    outputPath: null,
  };

  for (let index = 2; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--package-json') {
      options.packageJsonPath = path.resolve(requireValue(argv, index, arg));
      index += 1;
      continue;
    }
    if (arg.startsWith('--package-json=')) {
      options.packageJsonPath = path.resolve(arg.slice('--package-json='.length));
      continue;
    }
    if (arg === '--scripts') {
      options.aggregateScripts = splitScriptList(requireValue(argv, index, arg));
      index += 1;
      continue;
    }
    if (arg.startsWith('--scripts=')) {
      options.aggregateScripts = splitScriptList(arg.slice('--scripts='.length));
      continue;
    }
    if (arg === '--format') {
      options.format = parseFormat(requireValue(argv, index, arg));
      index += 1;
      continue;
    }
    if (arg.startsWith('--format=')) {
      options.format = parseFormat(arg.slice('--format='.length));
      continue;
    }
    if (arg === '--output') {
      options.outputPath = path.resolve(requireValue(argv, index, arg));
      index += 1;
      continue;
    }
    if (arg.startsWith('--output=')) {
      options.outputPath = path.resolve(arg.slice('--output='.length));
    }
  }

  return options;
}

function requireValue(argv, index, name) {
  const value = argv[index + 1];
  if (!value) {
    throw new Error(`Missing value for ${name}`);
  }
  return value;
}

function parseFormat(value) {
  if (value === 'json' || value === 'markdown') {
    return value;
  }
  throw new Error(`Unsupported gate graph format: ${value}`);
}

function splitScriptList(rawValue) {
  return rawValue
    .split(',')
    .map((name) => name.trim())
    .filter((name) => name.length > 0);
}

function parsePackageScripts(packageJsonPath) {
  const payload = JSON.parse(readFileSync(packageJsonPath, 'utf-8'));
  const scripts = payload.scripts;
  if (!scripts || typeof scripts !== 'object') {
    throw new Error(`No scripts block found in ${packageJsonPath}`);
  }
  return scripts;
}

export function generateGateDependencyGraph({
  scripts,
  aggregateScripts = DEFAULT_AGGREGATE_SCRIPTS,
}) {
  const nodesByName = new Map();
  const edges = [];
  const queue = [...aggregateScripts];
  const queued = new Set(queue);

  while (queue.length > 0) {
    const scriptName = queue.shift();
    const command = scripts[scriptName];
    const references = command ? findReferencedChecks(command) : [];
    nodesByName.set(scriptName, {
      scriptName,
      command: command ?? null,
      tier: classifyRuntimeTier(scriptName, command ?? ''),
      owner: inferOwner(scriptName, command ?? ''),
      references,
      targetHints: command ? collectTargetHints(command) : [],
      defined: command !== undefined,
    });

    for (const referencedScriptName of references) {
      edges.push({ from: scriptName, to: referencedScriptName });
      if (!queued.has(referencedScriptName)) {
        queued.add(referencedScriptName);
        queue.push(referencedScriptName);
      }
    }
  }

  return {
    aggregateScripts: [...aggregateScripts],
    nodes: [...nodesByName.values()].sort((left, right) => left.scriptName.localeCompare(right.scriptName)),
    edges: edges.sort((left, right) => `${left.from}:${left.to}`.localeCompare(`${right.from}:${right.to}`)),
  };
}

function findReferencedChecks(command) {
  const matches = command.match(/\bcheck:[a-z0-9:_-]+\b/g);
  if (!matches) {
    return [];
  }
  return [...new Set(matches)];
}

function classifyRuntimeTier(scriptName, command) {
  if (scriptName.includes(':phase8:fast')) return 'fast';
  if (scriptName.includes(':phase8:integration')) return 'integration';
  if (scriptName.includes(':phase8:e2e')) return 'e2e';
  if (scriptName.includes(':phase8:release')) return 'release';
  if (/\bplaywright\b|:e2e\b|e2e/i.test(command)) return 'e2e';
  if (/\bintegration\b|cross-product|runbook|gradle|run-gradle-wrapper/i.test(command)) return 'integration';
  if (/\brelease\b|production|readiness|rollback|evidence/i.test(command)) return 'release';
  return 'fast';
}

function inferOwner(scriptName, command) {
  const segments = splitCommandSegments(command);
  const owners = new Set();
  for (const segment of segments) {
    const tokens = tokenizeCommand(segment);
    const pnpmDir = findPnpmDir(tokens);
    if (pnpmDir !== null) {
      owners.add(normalizeOwnerPath(pnpmDir));
      continue;
    }
    const gradleOwner = findGradleOwner(tokens);
    if (gradleOwner !== null) {
      owners.add(gradleOwner);
      continue;
    }
    const nodeScript = tokens.map(cleanToken).find((token) => /^\.?\/?scripts\/.+\.(?:mjs|js)$/u.test(token));
    if (nodeScript !== undefined) {
      owners.add('root:scripts');
    }
  }

  if (owners.size > 0) {
    return [...owners].sort().join(', ');
  }
  const productMatch = scriptName.match(/(?:check|generate):([a-z0-9-]+)-/u);
  if (productMatch?.[1]) {
    return `product:${productMatch[1]}`;
  }
  return 'root';
}

function collectTargetHints(command) {
  return splitCommandSegments(command).flatMap((segment) => {
    const tokens = tokenizeCommand(segment);
    const hints = [];
    const pnpmDir = findPnpmDir(tokens);
    if (pnpmDir !== null) {
      hints.push(`pnpm:${normalizeOwnerPath(pnpmDir)}`);
    }
    const gradleOwner = findGradleOwner(tokens);
    if (gradleOwner !== null) {
      hints.push(`gradle:${gradleOwner}`);
    }
    for (const token of tokens.map(cleanToken)) {
      if (/^\.?\/?scripts\/.+\.(?:mjs|js)$/u.test(token)) {
        hints.push(`script:${token}`);
      }
      if (looksLikeTestTarget(token)) {
        hints.push(`target:${token}`);
      }
    }
    return hints;
  });
}

function splitCommandSegments(command) {
  return command
    .split(/\s+&&\s+|\s+\|\|\s+|;/)
    .map((segment) => segment.trim())
    .filter((segment) => segment.length > 0);
}

function tokenizeCommand(command) {
  const tokens = [];
  let current = '';
  let quote = null;
  for (let index = 0; index < command.length; index += 1) {
    const character = command[index];
    if (quote !== null) {
      if (character === quote) {
        quote = null;
      } else {
        current += character;
      }
      continue;
    }
    if (character === '"' || character === "'") {
      quote = character;
      continue;
    }
    if (/\s/.test(character)) {
      if (current.length > 0) {
        tokens.push(current);
        current = '';
      }
      continue;
    }
    current += character;
  }
  if (current.length > 0) {
    tokens.push(current);
  }
  return tokens;
}

function findPnpmDir(tokens) {
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    if (token === '--dir' && tokens[index + 1] !== undefined) {
      return cleanToken(tokens[index + 1]);
    }
    if (token.startsWith('--dir=')) {
      return cleanToken(token.slice('--dir='.length));
    }
  }
  return null;
}

function findGradleOwner(tokens) {
  const gradleTask = tokens.find((token) => token.startsWith(':'));
  if (gradleTask === undefined) {
    return null;
  }
  const segments = gradleTask.split(':').filter(Boolean);
  if (segments.length <= 1) {
    return null;
  }
  return segments.slice(0, -1).join('/');
}

function normalizeOwnerPath(ownerPath) {
  return ownerPath.replace(/^\.?\//u, '').replaceAll('\\', '/');
}

function looksLikeTestTarget(token) {
  return (
    token.includes('/') ||
    token.includes('\\') ||
    /\.(?:mjs|js|cjs|ts|tsx|jsx|json|spec|test)$/u.test(token)
  ) && !token.includes('*');
}

function cleanToken(token) {
  return token.replace(/^['"]|['"],?$/gu, '').replace(/,$/u, '');
}

export function renderGateDependencyGraphMarkdown(graph) {
  const lines = [
    '# Gate Dependency Graph',
    '',
    `Aggregate roots: ${graph.aggregateScripts.map((script) => `\`${script}\``).join(', ')}`,
    '',
    '| Script | Tier | Owner | References | Targets |',
    '|---|---|---|---|---|',
  ];
  for (const node of graph.nodes) {
    lines.push([
      `\`${node.scriptName}\``,
      node.tier,
      node.owner,
      node.references.map((ref) => `\`${ref}\``).join('<br>') || '-',
      node.targetHints.map((hint) => `\`${hint}\``).join('<br>') || '-',
    ].join(' | '));
  }
  lines.push('');
  lines.push('## Edges');
  lines.push('');
  for (const edge of graph.edges) {
    lines.push(`- \`${edge.from}\` -> \`${edge.to}\``);
  }
  return `${lines.join('\n')}\n`;
}

function main() {
  const options = parseArgs(process.argv);
  const graph = generateGateDependencyGraph({
    scripts: parsePackageScripts(options.packageJsonPath),
    aggregateScripts: options.aggregateScripts,
  });
  const output = options.format === 'json'
    ? `${JSON.stringify(graph, null, 2)}\n`
    : renderGateDependencyGraphMarkdown(graph);

  if (options.outputPath !== null) {
    writeFileSync(options.outputPath, output);
    return;
  }
  process.stdout.write(output);
}

const invokedAsScript = process.argv[1] !== undefined
  && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedAsScript) {
  main();
}
