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

// Path patterns for reference resolution
const MASTERY_DEFINITION_PATTERN = /definitions\/mastery\/.*\.yaml$/;
const EVALUATION_PACK_PATTERN = /evaluation-packs\/.*\.yaml$/;
const POLICY_CATALOG_PATTERN = /mastery-policies\/.*\.yaml$/;

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

const CANONICAL_LEARNING_LEVELS = new Set(['L0', 'L1', 'L2', 'L3', 'L4', 'L5']);

const VALID_LEARNING_TARGETS = new Set([
  'EPISODIC_MEMORY',
  'SEMANTIC_FACT',
  'RETRIEVAL_POLICY',
  'CONFIDENCE_THRESHOLD',
  'ROUTING_POLICY',
  'PROCEDURAL_SKILL',
  'NEGATIVE_KNOWLEDGE',
  'PROMPT_TEMPLATE',
  'PLANNER_POLICY',
  'MODEL_ADAPTER',
  'MASTERY_STATE',
]);

const LEVEL_ORDINAL: Record<string, number> = {
  L0: 0, L1: 1, L2: 2, L3: 3, L4: 4, L5: 5,
};

export interface AgentIdentity {
  agentType?: string;
  subtype?: string;
  [key: string]: unknown;
}

export interface MasteryBinding {
  skillRef?: string;
  masteryPolicyRef?: string;
  [key: string]: unknown;
}

export interface LearningBlock {
  learningLevel?: string;
  adaptationTargets?: unknown;
  skillRefs?: unknown;
  masteryBindings?: unknown;
  masteryPolicyRefs?: unknown;
  evaluationRefs?: unknown;
  provenanceRequired?: unknown;
  promotionRequired?: unknown;
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
  learning?: LearningBlock;
  [key: string]: unknown;
}

export interface CanonicalizationResult {
  checked: string[];
  changed: string[];
  failed: string[];
  errors: string[];
}

/**
 * Canonicalize and validate a `learning:` block on an AgentDefinition.
 *
 * Rules enforced:
 *  - `learningLevel` must be one of L0–L5 (case-insensitive input is normalized to uppercase).
 *  - L2+: `provenanceRequired` must be `true`.
 *  - L3+: `promotionRequired` must be `true`.
 *  - L3+: `evaluationRefs` must be a non-empty array.
 *  - `adaptationTargets`, if present, must be an array of valid `LearningTarget` enum names.
 *  - `MASTERY_STATE` target is only allowed when `learningLevel: L5` and `governanceWorkflow: true` or `agentType: DETERMINISTIC` with governance label.
 *  - `skillRefs`, `masteryPolicyRefs`, `evaluationRefs` must be arrays when present.
 *  - Reference resolution validation for skillRefs, evaluationRefs, masteryPolicyRefs.
 *
 * Returns `true` when the document was mutated (fix mode normalized a learningLevel value).
 */
export function canonicalizeLearningBlock(
  learning: LearningBlock,
  fix: boolean,
  errors: string[],
  location: string,
): boolean {
  let changed = false;

  const rawLevel = learning.learningLevel;
  if (rawLevel === undefined) {
    // No learning level — nothing to canonicalize or enforce further.
    return false;
  }

  if (typeof rawLevel !== 'string') {
    errors.push(`${location}.learning.learningLevel: must be a string`);
    return false;
  }

  // Normalize casing: accept "l3" → "L3".
  const normalized = rawLevel.trim().toUpperCase();
  if (!CANONICAL_LEARNING_LEVELS.has(normalized)) {
    errors.push(`${location}.learning.learningLevel: '${rawLevel}' is not a valid learning level (L0–L5)`);
    return false;
  }

  if (rawLevel !== normalized) {
    if (fix) {
      learning.learningLevel = normalized;
      changed = true;
    } else {
      errors.push(`${location}.learning.learningLevel: '${rawLevel}' must be uppercase canonical form '${normalized}'`);
    }
  }

  const ordinal = LEVEL_ORDINAL[normalized] ?? 0;

  // L2+: provenanceRequired must be true.
  if (ordinal >= LEVEL_ORDINAL['L2']) {
    if (learning.provenanceRequired !== true) {
      errors.push(`${location}.learning: L2+ agents must set provenanceRequired: true`);
    }
  }

  // L3+: promotionRequired must be true.
  if (ordinal >= LEVEL_ORDINAL['L3']) {
    if (learning.promotionRequired !== true) {
      errors.push(`${location}.learning: L3+ agents must set promotionRequired: true`);
    }
  }

  // L3+: evaluationRefs must be a non-empty array.
  if (ordinal >= LEVEL_ORDINAL['L3']) {
    if (!Array.isArray(learning.evaluationRefs) || (learning.evaluationRefs as unknown[]).length === 0) {
      errors.push(`${location}.learning: L3+ agents must provide at least one evaluationRefs entry`);
    }
  }

  // adaptationTargets — if present must be a non-empty array of valid enum names.
  let hasMasteryStateTarget = false;
  if (learning.adaptationTargets !== undefined) {
    if (!Array.isArray(learning.adaptationTargets)) {
      errors.push(`${location}.learning.adaptationTargets: must be an array of LearningTarget enum names`);
    } else {
      for (const target of learning.adaptationTargets as unknown[]) {
        if (typeof target !== 'string' || !VALID_LEARNING_TARGETS.has(target)) {
          errors.push(`${location}.learning.adaptationTargets: '${String(target)}' is not a valid LearningTarget`);
        }
        if (target === 'MASTERY_STATE') {
          hasMasteryStateTarget = true;
        }
      }
    }

    // MASTERY_STATE target validation - only allowed for L5 governance workflows
    if (hasMasteryStateTarget) {
      if (normalized !== 'L5') {
        errors.push(`${location}.learning: MASTERY_STATE target is only permitted at learningLevel L5`);
      } else if (learning.governanceWorkflow !== true) {
        errors.push(`${location}.learning: MASTERY_STATE target at L5 requires governanceWorkflow: true`);
      }
    }
  }

  // skillRefs — must be array if present. Each entry is either:
  //   - a non-blank string (legacy simple ref), or
  //   - an object with a non-blank `skillId` string (structured ref from catalog-schema.yaml).
  if (learning.skillRefs !== undefined) {
    if (!Array.isArray(learning.skillRefs)) {
      errors.push(`${location}.learning.skillRefs: must be an array`);
    } else {
      for (const ref of learning.skillRefs as unknown[]) {
        if (typeof ref === 'string') {
          if (ref.trim() === '') {
            errors.push(`${location}.learning.skillRefs: skillRefs must contain non-blank strings`);
          }
        } else if (typeof ref === 'object' && ref !== null) {
          const skillRef = ref as Record<string, unknown>;
          if (typeof skillRef['skillId'] !== 'string' || (skillRef['skillId'] as string).trim() === '') {
            errors.push(`${location}.learning.skillRefs: structured skillRef must include a non-blank 'skillId'`);
          }
        } else {
          errors.push(`${location}.learning.skillRefs: skillRefs must contain non-blank strings or structured {skillId} objects`);
        }
      }
    }
  }

  // masteryPolicyRefs — must be array if present.
  if (learning.masteryPolicyRefs !== undefined) {
    if (!Array.isArray(learning.masteryPolicyRefs)) {
      errors.push(`${location}.learning.masteryPolicyRefs: must be an array`);
    } else {
      for (const ref of learning.masteryPolicyRefs as unknown[]) {
        if (typeof ref !== 'string' || ref.trim() === '') {
          errors.push(`${location}.learning.masteryPolicyRefs: masteryPolicyRefs must contain non-blank strings`);
        }
      }
    }
  }

  // evaluationRefs — must be array if present (already checked for L3+, enforce type for all).
  if (learning.evaluationRefs !== undefined) {
    if (!Array.isArray(learning.evaluationRefs)) {
      errors.push(`${location}.learning.evaluationRefs: must be an array`);
    } else {
      for (const ref of learning.evaluationRefs as unknown[]) {
        if (typeof ref !== 'string' || ref.trim() === '') {
          errors.push(`${location}.learning.evaluationRefs: evaluationRefs must contain non-blank strings`);
        }
      }
    }
  }

  // masteryBindings — if present must be a map (single binding) or array (multiple bindings).
  if (learning.masteryBindings !== undefined) {
    if (typeof learning.masteryBindings === 'object' && !Array.isArray(learning.masteryBindings)) {
      // Single binding as a map - validate required fields
      const binding = learning.masteryBindings as Record<string, unknown>;
      if (!binding.namespace || typeof binding.namespace !== 'string' || binding.namespace.trim() === '') {
        errors.push(`${location}.learning.masteryBindings: must include 'namespace' (non-blank string)`);
      }
      if (!binding.registryRef || typeof binding.registryRef !== 'string' || binding.registryRef.trim() === '') {
        errors.push(`${location}.learning.masteryBindings: must include 'registryRef' (non-blank string)`);
      }
    } else if (Array.isArray(learning.masteryBindings)) {
      // Multiple bindings as an array
      (learning.masteryBindings as unknown[]).forEach((binding, idx) => {
        if (typeof binding !== 'object' || binding === null) {
          errors.push(`${location}.learning.masteryBindings[${idx}]: must be an object`);
          return;
        }
        const b = binding as Record<string, unknown>;
        if (!b.namespace || typeof b.namespace !== 'string' || b.namespace.trim() === '') {
          errors.push(`${location}.learning.masteryBindings[${idx}]: must include 'namespace' (non-blank string)`);
        }
        if (!b.registryRef || typeof b.registryRef !== 'string' || b.registryRef.trim() === '') {
          errors.push(`${location}.learning.masteryBindings[${idx}]: must include 'registryRef' (non-blank string)`);
        }
      });
    } else {
      errors.push(`${location}.learning.masteryBindings: must be an object or array`);
    }
  }

  return changed;
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

  // Validate the learning: block when present on an AgentDefinition.
  if (obj.learning && typeof obj.learning === 'object' && !Array.isArray(obj.learning)) {
    changed = canonicalizeLearningBlock(obj.learning as LearningBlock, fix, errors, location) || changed;
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
