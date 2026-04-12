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

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
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
