/**
 * Strict AgentSpec canonicalization and validation tool.
 *
 * Usage:
 *   pnpm tsx platform/agent-catalog/schema-migration.ts --check products
 *   pnpm tsx platform/agent-catalog/schema-migration.ts --fix products
 */

import * as fs from 'fs';
import * as path from 'path';
import * as yaml from 'js-yaml';

const CANONICAL_TYPES = new Set([
  'DETERMINISTIC',
  'PROBABILISTIC',
  'STREAM_PROCESSOR',
  'PLANNING',
  'HYBRID',
  'ADAPTIVE',
  'COMPOSITE',
  'REACTIVE',
  'CUSTOM',
]);

const TYPE_FIXES: Record<string, { agentType: string; subtype?: string }> = {
  LLM: { agentType: 'PROBABILISTIC', subtype: 'LLM' },
  'LLM-BASED': { agentType: 'PROBABILISTIC', subtype: 'LLM' },
  ML_BASED: { agentType: 'PROBABILISTIC', subtype: 'ML_MODEL' },
  'ML-BASED': { agentType: 'PROBABILISTIC', subtype: 'ML_MODEL' },
  RULE_BASED: { agentType: 'DETERMINISTIC', subtype: 'RULE_ENGINE' },
  'RULE-BASED': { agentType: 'DETERMINISTIC', subtype: 'RULE_ENGINE' },
  POLICY: { agentType: 'DETERMINISTIC', subtype: 'POLICY_ENGINE' },
  PATTERN: { agentType: 'DETERMINISTIC', subtype: 'PATTERN_MATCHER' },
};

export interface AgentIdentity {
  agentType?: string;
  subtype?: string;
  [key: string]: unknown;
}

export interface AgentDefinition {
  id?: string;
  agentId?: string;
  name?: string;
  namespace?: string;
  version?: string;
  status?: string;
  type?: string;
  subtype?: string;
  identity?: AgentIdentity;
  [key: string]: unknown;
}

export interface CanonicalizationResult {
  checked: string[];
  changed: string[];
  failed: string[];
  errors: string[];
}

function normalizeType(value: string): string {
  return value.trim().toUpperCase().replace(/-/g, '_');
}

function isYamlFile(filePath: string): boolean {
  return filePath.endsWith('.yaml') || filePath.endsWith('.yml');
}

function collectYamlFiles(target: string): string[] {
  const stat = fs.statSync(target);
  if (stat.isFile()) {
    return isYamlFile(target) ? [target] : [];
  }

  const files: string[] = [];
  for (const entry of fs.readdirSync(target)) {
    const fullPath = path.join(target, entry);
    const entryStat = fs.statSync(fullPath);
    if (entryStat.isDirectory()) {
      files.push(...collectYamlFiles(fullPath));
    } else if (isYamlFile(fullPath)) {
      files.push(fullPath);
    }
  }
  return files;
}

function canonicalizeType(
  obj: Record<string, unknown>,
  key: 'type' | 'agentType',
  fix: boolean,
  errors: string[],
  location: string,
): boolean {
  const raw = obj[key];
  if (typeof raw !== 'string') return false;
  if (raw.includes('{{') || raw.includes('${')) return false;

  const normalized = normalizeType(raw);
  if (CANONICAL_TYPES.has(normalized)) {
    if (raw !== normalized && fix) {
      obj[key] = normalized;
      return true;
    }
    if (raw !== normalized) {
      errors.push(`${location}: ${key} must use uppercase canonical value ${normalized}`);
    }
    return false;
  }

  const mapped = TYPE_FIXES[normalized];
  if (!mapped) {
    errors.push(`${location}: unknown canonical agent type '${raw}'`);
    return false;
  }

  if (!fix) {
    errors.push(`${location}: noncanonical agent type '${raw}' must be ${mapped.agentType}` +
      (mapped.subtype ? ` with subtype ${mapped.subtype}` : ''));
    return false;
  }

  obj[key] = mapped.agentType;
  if (mapped.subtype && typeof obj.subtype !== 'string') {
    obj.subtype = mapped.subtype;
  }
  return true;
}

function visit(value: unknown, fix: boolean, errors: string[], location: string): boolean {
  let changed = false;
  if (Array.isArray(value)) {
    value.forEach((item, index) => {
      changed = visit(item, fix, errors, `${location}[${index}]`) || changed;
    });
    return changed;
  }
  if (!value || typeof value !== 'object') {
    return false;
  }

  const obj = value as Record<string, unknown>;
  changed = canonicalizeType(obj, 'agentType', fix, errors, location) || changed;

  const hasAgentShape = typeof obj.id === 'string'
    || typeof obj.agentId === 'string'
    || typeof obj.identity === 'object'
    || typeof obj.subtype === 'string';
  if (hasAgentShape && typeof obj.agentType !== 'string') {
    changed = canonicalizeType(obj, 'type', fix, errors, location) || changed;
  }

  if (obj.identity && typeof obj.identity === 'object') {
    changed = visit(obj.identity, fix, errors, `${location}.identity`) || changed;
  }

  for (const [key, child] of Object.entries(obj)) {
    if (key === 'identity') continue;
    changed = visit(child, fix, errors, `${location}.${key}`) || changed;
  }
  return changed;
}

function orderedDump(doc: unknown): string {
  return yaml.dump(doc, {
    indent: 2,
    lineWidth: 120,
    noRefs: true,
    sortKeys: false,
  });
}

export function canonicalize(target: string, fix: boolean): CanonicalizationResult {
  const result: CanonicalizationResult = { checked: [], changed: [], failed: [], errors: [] };
  for (const filePath of collectYamlFiles(target)) {
    result.checked.push(filePath);
    try {
      const original = fs.readFileSync(filePath, 'utf8');
      const doc = yaml.load(original);
      const errors: string[] = [];
      const changed = visit(doc, fix, errors, filePath);

      if (errors.length > 0) {
        result.failed.push(filePath);
        result.errors.push(...errors);
        continue;
      }

      if (fix && changed) {
        fs.writeFileSync(filePath, orderedDump(doc), 'utf8');
        result.changed.push(filePath);
      }
    } catch (error) {
      result.failed.push(filePath);
      result.errors.push(`${filePath}: ${(error as Error).message}`);
    }
  }
  return result;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const args = process.argv.slice(2);
  const mode = args[0];
  const target = args[1];

  if ((mode !== '--check' && mode !== '--fix') || !target) {
    console.error('Usage: pnpm tsx platform/agent-catalog/schema-migration.ts --check|--fix <file-or-directory>');
    process.exit(2);
  }

  const result = canonicalize(target, mode === '--fix');
  for (const error of result.errors) {
    console.error(error);
  }
  console.log(`checked=${result.checked.length} changed=${result.changed.length} failed=${result.failed.length}`);
  process.exit(result.failed.length > 0 ? 1 : 0);
}
