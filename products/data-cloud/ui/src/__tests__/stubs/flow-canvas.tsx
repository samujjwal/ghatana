/**
 * Stub for @ghatana/flow-canvas used in vitest (jsdom) environments.
 * ReactFlow requires browser DOM APIs not available in jsdom, so we replace
 * the entire package with lightweight React stubs for unit tests.
 *
 * This file is referenced by the `resolve.alias` in vitest.config.ts; the
 * vi.mock() calls in individual tests can further override specific exports.
 */
import React from 'react';

export const FlowCanvas = ({ children }: { children?: React.ReactNode }) =>
  React.createElement('div', { 'data-testid': 'flow-canvas' }, children);

export const FlowControls = () =>
  React.createElement('div', { 'data-testid': 'flow-controls' });

export const HotTierNode = () => React.createElement('div', { 'data-testid': 'hot-tier-node' });
export const WarmTierNode = () => React.createElement('div', { 'data-testid': 'warm-tier-node' });
export const ColdTierNode = () => React.createElement('div', { 'data-testid': 'cold-tier-node' });
export const ArchiveTierNode = () =>
  React.createElement('div', { 'data-testid': 'archive-tier-node' });
export const AgentNode = () => React.createElement('div', { 'data-testid': 'agent-node' });
export const DataFlowEdge = () => null;

export function useNodesState<T>(initial: T[]) {
  return [initial, () => {}, () => {}] as const;
}

export function useEdgesState<T>(initial: T[]) {
  return [initial, () => {}, () => {}] as const;
}

export function addEdge<T>(connection: unknown, edges: T[]): T[] {
  return edges;
}

export const MarkerType = {
  Arrow: 'arrow',
  ArrowClosed: 'arrowclosed',
};

export const Position = {
  Left: 'left',
  Right: 'right',
  Top: 'top',
  Bottom: 'bottom',
} as const;

// ── Handle component ───────────────────────────────────────────────────────────

export const Handle = ({
  position,
  type,
}: {
  position?: string;
  type?: string;
  [key: string]: unknown;
}) => React.createElement('div', { 'data-testid': `handle-${type}-${position}` });

/** Floating panel overlay (mirrors ReactFlow's Panel position prop). */
export const Panel = ({
  position,
  className,
  children,
}: {
  position?: string;
  className?: string;
  children?: React.ReactNode;
}) => React.createElement('div', { 'data-testid': `panel-${position}`, className }, children);

// ── Type aliases (structural only — erased at runtime) ────────────────────────

/** Structural alias for ReactFlow's Node<Data, Type> generic. */
export type FlowNodeType<
  TData extends Record<string, unknown> = Record<string, unknown>,
  TType extends string = string,
> = {
  id: string;
  data: TData;
  type?: TType;
  position?: { x: number; y: number };
  selected?: boolean;
};

/** Re-export as Node for direct compatibility with import type { Node }. */
export type Node<
  TData extends Record<string, unknown> = Record<string, unknown>,
  TType extends string = string,
> = FlowNodeType<TData, TType>;

/** Structural Edge type. */
export type Edge<TData extends Record<string, unknown> = Record<string, unknown>> = {
  id: string;
  source: string;
  target: string;
  data?: TData;
  label?: string;
  animated?: boolean;
};

/** Connection type. */
export type Connection = {
  source: string | null;
  target: string | null;
  sourceHandle?: string | null;
  targetHandle?: string | null;
};

/** NodeChange type. */
export type NodeChange = { type: string; id: string; position?: { x: number; y: number } };

/**
 * Props received by custom node components.
 * Mirrors ReactFlow's NodeProps<T> but resolves `data` without the Node wrapper.
 */
export type NodeProps<T = Node> = {
  id: string;
  data: T extends Node<infer D> ? D : Record<string, unknown>;
  selected?: boolean;
  type?: string;
  [key: string]: unknown;
};
