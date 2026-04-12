/**
 * @fileoverview Builder-to-DesignSystem binding — validates BuilderDocument nodes
 * against registered ComponentContracts and enforces story/contract parity.
 *
 * This module is the boundary that keeps the builder honest: every component
 * instance must have a matching registered contract. Props are validated against
 * contract definitions. Missing or unknown props are flagged as violations.
 */

import type { ComponentContract, ComponentProp } from '@ghatana/ds-schema';
import type {
  BuilderDocument,
  ComponentInstance,
  ValidationResult,
  ValidationError,
  ValidationWarning,
  NodeId,
} from './types.js';

// ============================================================================
// DS Binding Validator
// ============================================================================

export interface DSBindingViolation {
  readonly nodeId: NodeId;
  readonly contractName: string;
  readonly kind: DSViolationKind;
  readonly message: string;
  readonly propName?: string;
}

export type DSViolationKind =
  | 'contract-not-found'
  | 'required-prop-missing'
  | 'prop-type-mismatch'
  | 'unknown-prop'
  | 'deprecated-prop';

export interface DSBindingValidationResult {
  readonly valid: boolean;
  readonly violations: readonly DSBindingViolation[];
}

/**
 * Validates every node in a BuilderDocument against its registered
 * ComponentContract. Produces typed violations for display in the builder UI.
 */
export function validateDocumentAgainstDS(
  document: BuilderDocument,
  contracts: readonly ComponentContract[],
): DSBindingValidationResult {
  const contractMap = new Map<string, ComponentContract>(
    contracts.map((c) => [c.name, c]),
  );
  const violations: DSBindingViolation[] = [];

  for (const [, node] of document.nodes) {
    validateNodeAgainstContract(node, contractMap, violations);
  }

  return {
    valid: violations.length === 0,
    violations,
  };
}

function validateNodeAgainstContract(
  node: ComponentInstance,
  contractMap: Map<string, ComponentContract>,
  violations: DSBindingViolation[],
): void {
  const contract = contractMap.get(node.contractName);

  if (!contract) {
    violations.push({
      nodeId: node.id,
      contractName: node.contractName,
      kind: 'contract-not-found',
      message: `Component "${node.contractName}" has no registered contract in the design system.`,
    });
    return;
  }

  const propDefs = new Map<string, ComponentProp>(
    contract.props.map((p) => [p.name, p]),
  );

  // Check required props
  for (const propDef of contract.props) {
    if (propDef.required && !(propDef.name in node.props)) {
      violations.push({
        nodeId: node.id,
        contractName: node.contractName,
        kind: 'required-prop-missing',
        message: `Required prop "${propDef.name}" is missing on ${node.contractName}.`,
        propName: propDef.name,
      });
    }
  }

  // Check prop types and unknown props
  for (const [propName, propValue] of Object.entries(node.props)) {
    const propDef = propDefs.get(propName);

    if (!propDef) {
      violations.push({
        nodeId: node.id,
        contractName: node.contractName,
        kind: 'unknown-prop',
        message: `Unknown prop "${propName}" on ${node.contractName} — not in contract.`,
        propName,
      });
      continue;
    }

    const typeViolation = checkPropType(propValue, propDef);
    if (typeViolation) {
      violations.push({
        nodeId: node.id,
        contractName: node.contractName,
        kind: 'prop-type-mismatch',
        message: `Prop "${propName}" on ${node.contractName}: ${typeViolation}`,
        propName,
      });
    }
  }
}

function checkPropType(value: unknown, propDef: ComponentProp): string | null {
  if (value === null || value === undefined) return null;

  switch (propDef.type) {
    case 'string':
      if (typeof value !== 'string') return `expected string, got ${typeof value}`;
      break;
    case 'number':
      if (typeof value !== 'number') return `expected number, got ${typeof value}`;
      break;
    case 'boolean':
      if (typeof value !== 'boolean') return `expected boolean, got ${typeof value}`;
      break;
    case 'array':
      if (!Array.isArray(value)) return `expected array, got ${typeof value}`;
      break;
    case 'object':
      if (typeof value !== 'object' || Array.isArray(value))
        return `expected object, got ${Array.isArray(value) ? 'array' : typeof value}`;
      break;
    case 'enum': {
      const enumValues = Array.isArray(propDef.typeDetails)
        ? (propDef.typeDetails as unknown[])
        : propDef.validation?.enum;
      if (enumValues && !enumValues.includes(value)) {
        return `expected one of [${enumValues.join(', ')}], got "${String(value)}"`;
      }
      break;
    }
    case 'function':
    case 'node':
    case 'ref':
      break;
    default:
      break;
  }
  return null;
}

// ============================================================================
// ValidationResult adapter
// ============================================================================

/**
 * Converts DSBindingValidationResult violations into the standard
 * ValidationResult used by the builder document validator.
 */
export function dsViolationsToValidationResult(
  result: DSBindingValidationResult,
): ValidationResult {
  const errors: ValidationError[] = result.violations
    .filter((v) =>
      v.kind === 'contract-not-found' ||
      v.kind === 'required-prop-missing' ||
      v.kind === 'prop-type-mismatch',
    )
    .map((v) => ({
      code: v.kind,
      message: v.message,
      nodeId: v.nodeId,
      path: v.propName,
    }));

  const warnings: ValidationWarning[] = result.violations
    .filter((v) => v.kind === 'unknown-prop' || v.kind === 'deprecated-prop')
    .map((v) => ({
      code: v.kind,
      message: v.message,
      nodeId: v.nodeId,
      path: v.propName,
    }));

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

// ============================================================================
// Story/Contract Parity Check
// ============================================================================

export interface StoryParityReport {
  readonly contractName: string;
  readonly missingStories: readonly string[];
  readonly undocumentedVariants: readonly string[];
  readonly isComplete: boolean;
}

/**
 * Checks that every required prop variant defined in the contract has a
 * corresponding story.  `storyPropCombinations` is the set of prop combinations
 * covered by the component's Storybook stories.
 */
export function checkStoryContractParity(
  contract: ComponentContract,
  storyPropCombinations: ReadonlyArray<Record<string, unknown>>,
): StoryParityReport {
  const missingStories: string[] = [];
  const undocumentedVariants: string[] = [];

  // Find enum props — each value should appear in at least one story
  for (const prop of contract.props) {
    const enumValues: unknown[] | undefined = Array.isArray(prop.typeDetails)
      ? (prop.typeDetails as unknown[])
      : prop.validation?.enum as unknown[] | undefined;
    if (prop.type === 'enum' && enumValues && enumValues.length > 0) {
      for (const enumValue of enumValues) {
        const covered = storyPropCombinations.some(
          (combo) => combo[prop.name] === enumValue,
        );
        if (!covered) {
          missingStories.push(`${prop.name}="${enumValue}"`);
        }
      }
    }
  }

  // Find story prop combinations that reference unknown props
  const knownProps = new Set(contract.props.map((p) => p.name));
  for (const combo of storyPropCombinations) {
    for (const key of Object.keys(combo)) {
      if (!knownProps.has(key)) {
        undocumentedVariants.push(key);
      }
    }
  }

  return {
    contractName: contract.name,
    missingStories,
    undocumentedVariants: [...new Set(undocumentedVariants)],
    isComplete: missingStories.length === 0 && undocumentedVariants.length === 0,
  };
}
