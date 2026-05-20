/**
 * @fileoverview Runtime helpers for validating and branding Builder node IDs.
 */

import type { NodeId } from './types.js';

export interface NodeIdValidationIssue {
  readonly value: unknown;
  readonly reason: 'not-a-string' | 'empty' | 'unknown';
}

export interface ParseNodeIdArrayResult {
  readonly nodeIds: NodeId[];
  readonly issues: NodeIdValidationIssue[];
}

function brandNodeId(value: string): NodeId {
  return value as NodeId;
}

function hasKnownId(knownIds: ReadonlySet<string> | readonly string[] | undefined, value: string): boolean {
  if (knownIds === undefined) return true;
  if ('has' in knownIds) return knownIds.has(value);
  return knownIds.includes(value);
}

export function parseNodeId(
  value: unknown,
  knownIds?: ReadonlySet<string> | readonly string[],
): NodeId | null {
  if (typeof value !== 'string') return null;
  if (value.length === 0) return null;
  if (!hasKnownId(knownIds, value)) return null;
  return brandNodeId(value);
}

export function validateNodeId(
  value: unknown,
  knownIds?: ReadonlySet<string> | readonly string[],
): value is NodeId {
  return parseNodeId(value, knownIds) !== null;
}

export function parseNodeIdArray(
  values: unknown,
  knownIds?: ReadonlySet<string> | readonly string[],
): ParseNodeIdArrayResult {
  if (!Array.isArray(values)) {
    return {
      nodeIds: [],
      issues: [{ value: values, reason: 'not-a-string' }],
    };
  }

  const nodeIds: NodeId[] = [];
  const issues: NodeIdValidationIssue[] = [];

  for (const value of values) {
    if (typeof value !== 'string') {
      issues.push({ value, reason: 'not-a-string' });
      continue;
    }
    if (value.length === 0) {
      issues.push({ value, reason: 'empty' });
      continue;
    }
    if (!hasKnownId(knownIds, value)) {
      issues.push({ value, reason: 'unknown' });
      continue;
    }
    nodeIds.push(brandNodeId(value));
  }

  return { nodeIds, issues };
}
