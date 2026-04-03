/**
 * @doc.type module
 * @doc.purpose Public API exports for Security (CSP, headers, etc.) utilities
 * @doc.layer platform
 */

export type { CSPConfig } from './csp';
export { 
  buildCSPHeader,
  generateNonce,
  cspMiddleware,
  helmetConfig,
} from './csp';

// Default export for Fastify plugin
export { default } from './csp';
