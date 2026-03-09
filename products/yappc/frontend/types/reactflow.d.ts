// Minimal, permissive stub for the legacy `reactflow` module used in tests.
// This avoids importing `@reactflow/*` packages (which pull in different
// @types/react versions) and provides a forgiving shape for tests/mocks.
declare module 'reactflow' {
  /**
   *
   */
  export type Point = { x: number; y: number };
  /**
   *
   */
  export type Viewport = { x: number; y: number; zoom?: number };
  /**
   *
   */
  export type ReactFlowNode<T = unknown> = {
    id: string;
    position: Point;
    data?: T;
  };
  /**
   *
   */
  export type ReactFlowEdge<T = unknown> = {
    id: string;
    source: string;
    target: string;
    data?: T;
  };

  export function project(point: Point): Point;
  export function toObject(nodeOrEdge: unknown): unknown;

  /**
   *
   */
  export interface ReactFlowInstance {
    // Keep the instance permissive so tests and older code can augment it.
    [key: string]: unknown;
  }
}
