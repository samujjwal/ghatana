/**
 * @fileoverview Vanilla DOM/HTML/TS rendering target for UI Builder.
 *
 * Provides framework-agnostic HTML serialization and live DOM mounting from
 * a BuilderDocument, suitable for SSR, static generation, email, and
 * non-React product surfaces.
 */

export type { WebRendererConfig } from './renderer.js';
export { serializeToHtml, mountToDOM } from './renderer.js';
