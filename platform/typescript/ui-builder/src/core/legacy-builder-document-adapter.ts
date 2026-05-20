/**
 * @fileoverview Legacy BuilderDocument compatibility adapter.
 *
 * This adapter provides an explicit, bounded migration path for code that was
 * written against the old Map-based BuilderDocument format. New code MUST NOT
 * use this adapter — it exists only to support in-progress migration of legacy
 * consumers.
 *
 * @deprecated All new code must use the canonical BuilderDocument from
 * `./builder-document.ts` directly. The compatibility normalization in this
 * file will be removed when all legacy consumers have migrated.
 *
 * Migration guide:
 * - Replace `normalizeBuilderDocument(legacy)` with `createBuilderDocument(owner, options)`
 * - Replace `attachBuilderDocumentCompatibility(doc)` — the canonical document
 *   returned by `createBuilderDocument` already satisfies all required invariants.
 *
 * @doc.type module
 * @doc.purpose Legacy BuilderDocument compatibility bridge (migration only)
 * @doc.layer platform
 * @doc.pattern Adapter
 */

export {
  /**
   * @deprecated Migration-only: converts old Map-based BuilderDocument shapes
   * into the canonical format. Do not use for new documents — use
   * `createBuilderDocument()` instead.
   */
  normalizeBuilderDocument,
  /**
   * @deprecated Migration-only: attaches Map-compatible accessors to a
   * canonical BuilderDocument for legacy consumers. Remove when callers
   * are migrated to canonical operations.
   */
  attachBuilderDocumentCompatibility,
} from "./builder-document.js";

export type {
  BuilderDocument,
} from "./builder-document.js";
