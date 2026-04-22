/**
 * @fileoverview Builder document validation - contract compliance and structure.
 */

import type { ComponentContract } from '@ghatana/ds-schema';
import type {
  BuilderDocument,
  ComponentInstance,
  NodeId,
  ValidationResult,
  ValidationError,
  ValidationWarning,
} from './types';

// ============================================================================
// Document Validation
// ============================================================================

export function validateDocument(
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
): ValidationResult {
  const errors: ValidationError[] = [];
  const warnings: ValidationWarning[] = [];

  // Validate all nodes
  for (const [nodeId, instance] of document.nodes) {
    const contract = contracts.get(instance.contractName);
    
    if (!contract) {
      errors.push({
        code: 'MISSING_CONTRACT',
        message: `Contract "${instance.contractName}" not found for node ${nodeId}`,
        nodeId,
      });
      continue;
    }

    // Validate required props
    for (const prop of contract.props) {
      if (prop.required && !(prop.name in instance.props)) {
        errors.push({
          code: 'MISSING_REQUIRED_PROP',
          message: `Required prop "${prop.name}" missing on ${instance.contractName}`,
          nodeId,
          path: `props.${prop.name}`,
        });
      }
    }

    // Validate slot constraints
    for (const slot of contract.slots) {
      const children = instance.slots[slot.name] ?? [];
      
      if (slot.minChildren !== undefined && children.length < slot.minChildren) {
        errors.push({
          code: 'SLOT_MIN_CHILDREN',
          message: `Slot "${slot.name}" requires at least ${slot.minChildren} children`,
          nodeId,
          path: `slots.${slot.name}`,
        });
      }

      if (slot.maxChildren !== undefined && children.length > slot.maxChildren) {
        errors.push({
          code: 'SLOT_MAX_CHILDREN',
          message: `Slot "${slot.name}" allows at most ${slot.maxChildren} children`,
          nodeId,
          path: `slots.${slot.name}`,
        });
      }

      // Validate allowed/disallowed components
      if (slot.allowedComponents) {
        for (const childId of children) {
          const child = document.nodes.get(childId);
          if (child && !slot.allowedComponents.includes(child.contractName)) {
            errors.push({
              code: 'DISALLOWED_COMPONENT',
              message: `Component "${child.contractName}" not allowed in slot "${slot.name}"`,
              nodeId: childId,
            });
          }
        }
      }
    }
  }

  // Validate root nodes exist
  for (const rootId of document.rootNodes) {
    if (!document.nodes.has(rootId)) {
      errors.push({
        code: 'MISSING_ROOT_NODE',
        message: `Root node ${rootId} not found in nodes map`,
        nodeId: rootId,
      });
    }
  }

  // Validate no orphaned nodes
  const reachable = collectReachableNodes(document);
  for (const nodeId of document.nodes.keys()) {
    if (!reachable.has(nodeId)) {
      warnings.push({
        code: 'ORPHANED_NODE',
        message: `Node ${nodeId} is not reachable from any root`,
        nodeId,
      });
    }
  }

  // Additional contract-aware validations
  validateResponsiveConsistency(document, contracts, warnings);
  validateActionBindings(document, contracts, warnings);
  validatePreviewTrustPolicy(document, contracts, errors, warnings);

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

// ============================================================================
// Responsive Consistency
// ============================================================================

/**
 * Validates that responsive variants on nodes only override props declared as
 * responsive-capable in the component contract.
 */
function validateResponsiveConsistency(
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
  warnings: ValidationWarning[],
): void {
  for (const [nodeId, instance] of document.nodes) {
    const contract = contracts.get(instance.contractName);
    const responsiveMeta = contract?.responsive;
    if (!responsiveMeta || !instance.metadata.responsiveVariants?.length) continue;

    for (const variant of instance.metadata.responsiveVariants) {
      if (!variant.props) continue;
      const overrideKeys = Object.keys(variant.props);
      const allowedProps = responsiveMeta.responsiveProps;

      for (const key of overrideKeys) {
        if (allowedProps.length > 0 && !allowedProps.includes(key)) {
          warnings.push({
            code: 'RESPONSIVE_PROP_NOT_DECLARED',
            message:
              `Node "${nodeId}" overrides prop "${key}" at breakpoint "${variant.breakpoint}" ` +
              `but "${instance.contractName}" does not declare "${key}" as a responsiveProps.`,
            nodeId,
            path: `responsiveVariants[${variant.breakpoint}].props.${key}`,
          });
        }
      }
    }
  }
}

// ============================================================================
// Action / Binding Correctness
// ============================================================================

/**
 * Validates that action trigger events exist in the component's contract events.
 */
function validateActionBindings(
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
  warnings: ValidationWarning[],
): void {
  for (const [nodeId, instance] of document.nodes) {
    const contract = contracts.get(instance.contractName);
    if (!contract || !instance.metadata.actions?.length) continue;

    const contractEventNames = new Set(contract.events.map((e) => e.name));

    for (const action of instance.metadata.actions) {
      if (!contractEventNames.has(action.triggerEvent)) {
        warnings.push({
          code: 'ACTION_EVENT_NOT_IN_CONTRACT',
          message:
            `Node "${nodeId}" has action "${action.id}" triggered by event "${action.triggerEvent}" ` +
            `but "${instance.contractName}" does not declare that event.`,
          nodeId,
          path: `actions[${action.id}].triggerEvent`,
        });
      }
    }
  }
}

// ============================================================================
// Preview Trust Policy
// ============================================================================

const TRUST_RANK: Record<string, number> = {
  'untrusted': 0,
  'semi-trusted': 1,
  'trusted-controlled': 2,
  'trusted-local': 3,
};

/**
 * Validates that the document trust level satisfies the minimum required by
 * every component contract's preview restrictions.
 */
function validatePreviewTrustPolicy(
  document: BuilderDocument,
  contracts: ReadonlyMap<string, ComponentContract>,
  errors: ValidationError[],
  warnings: ValidationWarning[],
): void {
  const docTrust = document.metadata.trustLevel ?? 'untrusted';
  const docRank = TRUST_RANK[String(docTrust)] ?? 0;

  for (const [nodeId, instance] of document.nodes) {
    const contract = contracts.get(instance.contractName);
    const previewRestrictions = contract?.preview;
    if (!previewRestrictions) continue;

    const requiredTrust = previewRestrictions.minimumTrustLevel;
    const requiredRank = TRUST_RANK[requiredTrust] ?? 0;

    if (docRank < requiredRank) {
      errors.push({
        code: 'TRUST_LEVEL_INSUFFICIENT',
        message:
          `Node "${nodeId}" (${instance.contractName}) requires minimumTrustLevel ` +
          `"${requiredTrust}" but the document trust level is "${String(docTrust)}".`,
        nodeId,
        path: 'metadata.trustLevel',
      });
    }

    // Warn if the document has no trust level set but a component requires one
    if (!document.metadata.trustLevel && requiredRank > 0) {
      warnings.push({
        code: 'DOCUMENT_TRUST_LEVEL_UNSET',
        message:
          `Document has no trustLevel set, but node "${nodeId}" (${instance.contractName}) ` +
          `requires minimumTrustLevel "${requiredTrust}".`,
        nodeId,
        path: 'metadata.trustLevel',
      });
    }
  }
}

// ============================================================================
// Utility Functions
// ============================================================================

function collectReachableNodes(document: BuilderDocument): Set<NodeId> {
  const reachable = new Set<NodeId>();

  function visit(nodeId: NodeId): void {
    if (reachable.has(nodeId)) return;
    reachable.add(nodeId);

    const node = document.nodes.get(nodeId);
    if (node) {
      for (const children of Object.values(node.slots)) {
        for (const childId of children) {
          visit(childId);
        }
      }
    }
  }

  for (const rootId of document.rootNodes) {
    visit(rootId);
  }

  return reachable;
}
