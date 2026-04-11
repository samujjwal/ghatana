/**
 * @ghatana/sso-client — Security sub-module
 *
 * Platform-level security utilities: CSP config, helmet config, nonce
 * generation, and HTML injection helpers.
 *
 * Fastify-specific plugin lives in the `@ghatana/sso-client/security/fastify`
 * subpath to avoid importing server-only code into client bundles.
 *
 * @doc.type module
 * @doc.purpose Content Security Policy and HTTP security header utilities
 * @doc.layer platform
 * @doc.pattern Barrel Export
 */

export type { CSPConfig } from './csp.js';
export {
  defaultCSPConfig,
  buildCSPHeader,
  generateNonce,
  helmetConfig,
  injectCSPNonce,
} from './csp.js';
