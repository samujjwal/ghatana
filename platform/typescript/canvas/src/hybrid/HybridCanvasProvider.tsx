/**
 * @fileoverview HybridCanvasProvider — instance-scoped Jotai store provider.
 *
 * Wraps a subtree with an isolated Jotai store so that multiple independent
 * canvas instances can coexist in the same application without sharing atom
 * state.
 *
 * @example
 * ```tsx
 * // Create an isolated store and controller for one canvas instance.
 * const store = createHybridCanvasStore();
 * const controller = new HybridCanvasController({ store });
 *
 * function MyCanvas() {
 *   return (
 *     <HybridCanvasProvider store={store}>
 *       <HybridCanvas controller={controller} />
 *     </HybridCanvasProvider>
 *   );
 * }
 * ```
 *
 * @doc.type component
 * @doc.purpose Jotai store isolation for multi-canvas rendering
 * @doc.layer core
 * @doc.pattern Provider
 */

import React, { useMemo } from "react";
import { Provider } from "jotai";
import { createCanvasStore } from "./state.js";

/**
 * The type of an isolated Jotai store created by {@link createHybridCanvasStore}.
 */
export type HybridCanvasStore = ReturnType<typeof createCanvasStore>;

export interface HybridCanvasProviderProps {
  /**
   * An isolated Jotai store created by `createHybridCanvasStore()`.
   *
   * When omitted, a new store is created automatically. Pass an explicit store
   * when you also need to hand the same store to a `HybridCanvasController`.
   */
  store?: HybridCanvasStore;

  children?: React.ReactNode;
}

/**
 * Provides an isolated Jotai store to all canvas hooks and atoms within the
 * subtree.  Use one `<HybridCanvasProvider>` per logical canvas instance to
 * prevent cross-canvas state bleed.
 */
export function HybridCanvasProvider({
  store,
  children,
}: HybridCanvasProviderProps): React.ReactElement {
  // Create a stable store if none was passed. `useMemo` with an empty dep
  // array ensures the store is created once per component instance and is
  // never re-created on re-renders.
  const resolvedStore = useMemo(
    () => store ?? createCanvasStore(),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  return <Provider store={resolvedStore}>{children}</Provider>;
}
