/**
 * useYappcCanvasAI — React hook wiring the YAPPC Canvas AI adapter to the
 * platform AICanvasProvider.
 *
 * @doc.type hook
 * @doc.purpose Convenience hook: pre-wired YAPPC adapter for @ghatana/canvas AICanvasProvider
 * @doc.layer product
 * @doc.pattern Hook
 *
 * Usage:
 * ```tsx
 * import { useYappcCanvasAI } from "@/lib/canvas-ai/use-yappc-canvas-ai";
 * import { AICanvasProvider } from "@ghatana/canvas";
 *
 * function YappcCanvasRoot() {
 *   const adapter = useYappcCanvasAI();
 *   return (
 *     <AICanvasProvider adapter={adapter}>
 *       <YappcCanvas />
 *     </AICanvasProvider>
 *   );
 * }
 * ```
 */

import { useMemo } from "react";
import { getYappcCanvasAIAdapter, YappcCanvasAIAdapter } from "./yappc-ai-adapter.js";
import type { CanvasAIAdapter } from "@ghatana/canvas/ai";

/**
 * Returns a stable singleton YappcCanvasAIAdapter instance.
 *
 * The adapter is a stable singleton, so it won't cause unstable dependencies
 * in React trees and can be passed directly to AICanvasProvider as a prop.
 */
export function useYappcCanvasAI(): CanvasAIAdapter {
  return useMemo<YappcCanvasAIAdapter>(() => getYappcCanvasAIAdapter(), []);
}
