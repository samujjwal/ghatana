/**
 * YAPPC Canvas AI Adapter
 *
 * @doc.type adapter
 * @doc.purpose Implements the platform CanvasAIAdapter for YAPPC's Canvas AI service
 * @doc.layer product
 * @doc.pattern Adapter
 *
 * Bridges the platform `@ghatana/canvas` AI contract to the YAPPC Canvas AI HTTP API
 * (proxied through the Node.js BFF at `/api/canvas/*` → Java service on port 8083).
 *
 * The adapter implements:
 * - `getSuggestions` → `/api/canvas/suggest` (GET-style with POST body)
 * - `acceptSuggestion` → `/api/canvas/suggestion/accept`
 * - `dismissSuggestion` → `/api/canvas/suggestion/dismiss`
 * - `query` → generic LLM relay via `/api/canvas/query`
 * - `autoLayout` → `/api/canvas/layout`
 * - `generateElements` → `/api/canvas/generate-elements`
 *
 * Endpoints that don't yet exist on the backend return graceful no-op results
 * rather than throwing, so the frontend degrades cleanly if a feature is not
 * yet implemented on the service side.
 */

import type {
  CanvasAIAdapter,
  CanvasAIContext,
  AISuggestion,
  AISuggestionKind,
  AIResult,
  AILayoutResult,
  AIGenerateElementResult,
} from "@ghatana/canvas";

// ---------------------------------------------------------------------------
// API base — resolves to the BFF proxy which forwards to the Java service
// ---------------------------------------------------------------------------

const API_BASE =
  typeof import.meta !== "undefined" && (import.meta as { env?: { DEV?: boolean; VITE_API_ORIGIN?: string } }).env?.DEV
    ? `${(import.meta as { env?: { DEV?: boolean; VITE_API_ORIGIN?: string } }).env?.VITE_API_ORIGIN ?? "http://localhost:7002"}`
    : "";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`Canvas AI API error [${res.status}]: ${text}`);
  }

  return res.json() as Promise<T>;
}

/** Maps raw service suggestion to the platform AISuggestion shape */
function mapSuggestion(raw: Record<string, unknown>): AISuggestion {
  return {
    id: String(raw["id"] ?? crypto.randomUUID()),
    kind: (raw["kind"] as AISuggestionKind | undefined) ?? "content",
    title: String(raw["title"] ?? raw["label"] ?? "AI Suggestion"),
    description: typeof raw["description"] === "string" ? raw["description"] : undefined,
    confidence: typeof raw["confidence"] === "number" ? raw["confidence"] : 0.8,
    targetElementIds: Array.isArray(raw["targetElementIds"])
      ? (raw["targetElementIds"] as string[])
      : Array.isArray(raw["affectedElementIds"])
        ? (raw["affectedElementIds"] as string[])
        : undefined,
    payload: (raw["payload"] as Record<string, unknown> | undefined) ?? {},
  };
}

// ---------------------------------------------------------------------------
// YAPPC Canvas AI Adapter implementation
// ---------------------------------------------------------------------------

export class YappcCanvasAIAdapter implements CanvasAIAdapter {
  async getSuggestions(context: CanvasAIContext): Promise<AISuggestion[]> {
    try {
      const result = await postJson<{ suggestions: Record<string, unknown>[] }>(
        "/api/canvas/suggest",
        { context: serializeContext(context) },
      );
      return (result.suggestions ?? []).map(mapSuggestion);
    } catch (e) {
      console.warn("[YappcCanvasAIAdapter] getSuggestions unavailable:", e);
      return [];
    }
  }

  async acceptSuggestion(
    suggestion: AISuggestion,
    context: CanvasAIContext,
  ): Promise<AIResult> {
    try {
      const result = await postJson<AIResult>(
        "/api/canvas/suggestion/accept",
        { suggestionId: suggestion.id, suggestion, context: serializeContext(context) },
      );
      return result;
    } catch (e) {
      return {
        kind: "error",
        message: e instanceof Error ? e.message : "acceptSuggestion failed",
        retryable: false,
      };
    }
  }

  async dismissSuggestion(suggestionId: string): Promise<void> {
    try {
      await postJson("/api/canvas/suggestion/dismiss", { suggestionId });
    } catch (e) {
      // Non-critical — log and continue
      console.warn("[YappcCanvasAIAdapter] dismissSuggestion failed:", e);
    }
  }

  async query(context: CanvasAIContext): Promise<AIResult> {
    try {
      const result = await postJson<AIResult>("/api/canvas/query", {
        prompt: context.userQuery ?? "",
        context: serializeContext(context),
      });
      return result;
    } catch (e) {
      return {
        kind: "error",
        message: e instanceof Error ? e.message : "query failed",
        retryable: true,
      };
    }
  }

  async autoLayout(context: CanvasAIContext): Promise<AILayoutResult> {
    try {
      const result = await postJson<AILayoutResult>(
        "/api/canvas/layout",
        { context: serializeContext(context) },
      );
      return result;
    } catch (e) {
      console.warn("[YappcCanvasAIAdapter] autoLayout unavailable:", e);
      return { positions: {} };
    }
  }

  async generateElements(
    description: string,
    context: CanvasAIContext,
  ): Promise<AIGenerateElementResult[]> {
    try {
      const result = await postJson<{ elements: AIGenerateElementResult[] }>(
        "/api/canvas/generate-elements",
        { description, context: serializeContext(context) },
      );
      return result.elements ?? [];
    } catch (e) {
      console.warn("[YappcCanvasAIAdapter] generateElements unavailable:", e);
      return [];
    }
  }
}

// ---------------------------------------------------------------------------
// Context serializer — strips non-serializable fields (functions, DOM refs)
// ---------------------------------------------------------------------------

function serializeContext(ctx: CanvasAIContext): Record<string, unknown> {
  return {
    selectedElementIds: ctx.selectedElementIds,
    activeLayer: ctx.activeLayer,
    visibleElementIds: ctx.visibleElementIds,
    userQuery: ctx.userQuery ?? "",
    productMetadata: ctx.productMetadata ?? {},
  };
}

// ---------------------------------------------------------------------------
// Singleton
// ---------------------------------------------------------------------------

let _adapter: YappcCanvasAIAdapter | null = null;

export function getYappcCanvasAIAdapter(): YappcCanvasAIAdapter {
  if (!_adapter) _adapter = new YappcCanvasAIAdapter();
  return _adapter;
}
