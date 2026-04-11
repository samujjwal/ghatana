/**
 * @ghatana/canvas-react — DEPRECATED
 *
 * This package is superseded by `@ghatana/canvas`. React canvas components
 * and hooks are available via `@ghatana/canvas` and `@ghatana/canvas/react`.
 *
 * @deprecated Import from '@ghatana/canvas' or '@ghatana/canvas/react' instead.
 *
 * Migration:
 *   Before: import { HybridCanvas, useHybridCanvas } from '@ghatana/canvas-react';
 *   After:  import { HybridCanvas, useHybridCanvas } from '@ghatana/canvas';
 *
 *   Before: import { BaseTopologyNode, useTopology } from '@ghatana/canvas-react';
 *   After:  import { BaseTopologyNode, useTopology } from '@ghatana/canvas/topology';
 *
 * @doc.type module
 * @doc.purpose Deprecated facade — re-exports from canonical @ghatana/canvas
 * @doc.layer platform
 * @doc.pattern Facade
 */
export * from '@ghatana/canvas';
export * from '@ghatana/canvas/react';
export * from '@ghatana/canvas/topology';
