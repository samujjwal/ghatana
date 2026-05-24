#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

const DEFAULT_SCOPES = [
  'platform/java/agent-core/src/main/java',
  'products/data-cloud/planes/action/operator-contracts/src/main/java',
  'products/data-cloud/planes/action/agent-runtime/src/main/java',
  'products/aep/ARCHITECTURE.md',
  'products/aep/docs/DISSERTATION_TRACEABILITY.md',
  'products/data-cloud/ARCHITECTURE.md',
  'docs/03-architecture',
  'docs/implementation',
  'docs/agent-system',
  'products/data-cloud/planes/action/agent-runtime/README.md',
  'products/data-cloud/planes/action/agent-runtime/OWNER.md',
  '.github/copilot-instructions.md',
];

const FORBIDDEN_PATTERNS = [
  {
    id: 'agent-operator-interface',
    pattern: /\b(?:interface|class|record)\s+AgentOperator\b/,
    message: 'AgentOperator must not be reintroduced as a canonical type',
  },
  {
    id: 'agent-operator-adapter',
    pattern: /\bAgentOperatorAdapter\b/,
    message: 'Use AgentEventOperatorCapabilityAdapter',
  },
  {
    id: 'agent-operator-factory',
    pattern: /\bAgentOperatorFactory\b/,
    message: 'Use AgentCapabilityExecutionFactory',
  },
  {
    id: 'agent-operator-import',
    pattern: /import\s+com\.ghatana\.core\.operator\.agent\.AgentOperator\s*;/,
    message: 'Do not import the removed AgentOperator contract',
  },
  {
    id: 'agent-operator-inheritance',
    pattern: /\bAgentOperator\b[^{;\n]*\bextends\b[^{;\n]*\bEventOperator\b/,
    message: 'EventOperator is an AgentCapability, not the parent type of Agent',
  },
  {
    id: 'pattern-agent-operator-helper',
    pattern: /\bisAgentOperator\s*\(/,
    message: 'Pattern runtime nodes must expose agent capabilities',
  },
  {
    id: 'stale-agent-as-event-operator-language',
    pattern: /agent-as-operator|Agent-as-operator|first-class EventOperator|AgentOperator\s+extends\s+EventOperator/i,
    message: 'Docs must describe event processing as an AgentCapability',
  },
];

const TEXT_EXTENSIONS = new Set(['.java', '.md', '.kts', '.gradle', '.mjs', '.js', '.json', '.yaml', '.yml']);

function walk(root, relativePath, files) {
  const fullPath = path.join(root, relativePath);
  if (!existsSync(fullPath)) {
    return;
  }
  const stats = statSync(fullPath);
  if (stats.isFile()) {
    if (TEXT_EXTENSIONS.has(path.extname(fullPath))) {
      files.push(relativePath.replaceAll(path.sep, '/'));
    }
    return;
  }
  for (const entry of readdirSync(fullPath)) {
    if (entry === 'build' || entry === '.gradle' || entry === 'node_modules') {
      continue;
    }
    walk(root, path.join(relativePath, entry), files);
  }
}

export function findAgentCapabilityDuplicateViolations(root = process.cwd(), scopes = DEFAULT_SCOPES) {
  const files = [];
  for (const scope of scopes) {
    walk(root, scope, files);
  }

  const violations = [];
  for (const file of files) {
    const source = readFileSync(path.join(root, file), 'utf8');
    for (const rule of FORBIDDEN_PATTERNS) {
      const match = source.match(rule.pattern);
      if (match) {
        violations.push({
          file,
          rule: rule.id,
          message: rule.message,
          match: match[0],
        });
      }
    }
  }
  return violations;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  const violations = findAgentCapabilityDuplicateViolations(root);

  if (violations.length === 0) {
    console.log('Agent capability duplicate check passed.');
    return;
  }

  console.error('Agent capability duplicate check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation.file}: ${violation.message} (${violation.rule}: ${violation.match})`);
  }
  process.exit(1);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
