/**
 * @file CSP (Content Security Policy) Middleware
 * Security headers implementation — framework-agnostic core.
 *
 * Server-framework-specific adapters are in separate files:
 *   - Fastify: import from '@ghatana/sso-client/security/fastify'
 *
 * @doc.type module
 * @doc.purpose Protect against XSS, data injection, and other attacks via CSP
 * @doc.layer infrastructure
 * @doc.pattern Security
 */

// ============================================================================
// CSP Configuration
// ============================================================================

export interface CSPConfig {
  // Directives
  defaultSrc?: string[];
  scriptSrc?: string[];
  styleSrc?: string[];
  imgSrc?: string[];
  fontSrc?: string[];
  connectSrc?: string[];
  mediaSrc?: string[];
  objectSrc?: string[];
  frameSrc?: string[];
  workerSrc?: string[];
  manifestSrc?: string[];

  // Additional options
  reportUri?: string;
  reportTo?: string;
  upgradeInsecureRequests?: boolean;
  blockAllMixedContent?: boolean;

  // Nonce generation for inline scripts/styles
  generateNonce?: boolean;
}

// Default CSP policy for YAPPC
export const defaultCSPConfig: CSPConfig = {
  defaultSrc: ["'self'"],
  scriptSrc: [
    "'self'",
    "'unsafe-inline'", // Required for BlockSuite/Monaco
    "'unsafe-eval'", // Required for Monaco editor
    'blob:', // Required for worker scripts
  ],
  styleSrc: [
    "'self'",
    "'unsafe-inline'", // Required for dynamic styles
    'https://fonts.googleapis.com',
  ],
  imgSrc: [
    "'self'",
    'data:', // For base64 encoded images
    'blob:', // For canvas exports
    'https:', // For external images
  ],
  fontSrc: ["'self'", 'https://fonts.gstatic.com', 'data:'],
  connectSrc: [
    "'self'",
    'wss:', // WebSocket connections
    'https:', // API calls
  ],
  mediaSrc: ["'self'", 'blob:', 'https:'],
  objectSrc: ["'none'"], // Disable plugins
  frameSrc: [
    "'self'",
    'https:', // For embeds
  ],
  workerSrc: [
    "'self'",
    'blob:', // For canvas/web workers
  ],
  manifestSrc: ["'self'"],
  upgradeInsecureRequests: true,
  blockAllMixedContent: true,
};

// ============================================================================
// CSP Builder
// ============================================================================

/**
 * Build CSP header string from configuration
 * @doc.purpose Generate CSP directive string
 */
export function buildCSPHeader(config: CSPConfig): string {
  const directives: string[] = [];

  if (config.defaultSrc) {
    directives.push(`default-src ${config.defaultSrc.join(' ')}`);
  }

  if (config.scriptSrc) {
    directives.push(`script-src ${config.scriptSrc.join(' ')}`);
  }

  if (config.styleSrc) {
    directives.push(`style-src ${config.styleSrc.join(' ')}`);
  }

  if (config.imgSrc) {
    directives.push(`img-src ${config.imgSrc.join(' ')}`);
  }

  if (config.fontSrc) {
    directives.push(`font-src ${config.fontSrc.join(' ')}`);
  }

  if (config.connectSrc) {
    directives.push(`connect-src ${config.connectSrc.join(' ')}`);
  }

  if (config.mediaSrc) {
    directives.push(`media-src ${config.mediaSrc.join(' ')}`);
  }

  if (config.objectSrc) {
    directives.push(`object-src ${config.objectSrc.join(' ')}`);
  }

  if (config.frameSrc) {
    directives.push(`frame-src ${config.frameSrc.join(' ')}`);
  }

  if (config.workerSrc) {
    directives.push(`worker-src ${config.workerSrc.join(' ')}`);
  }

  if (config.manifestSrc) {
    directives.push(`manifest-src ${config.manifestSrc.join(' ')}`);
  }

  if (config.upgradeInsecureRequests) {
    directives.push('upgrade-insecure-requests');
  }

  if (config.blockAllMixedContent) {
    directives.push('block-all-mixed-content');
  }

  if (config.reportUri) {
    directives.push(`report-uri ${config.reportUri}`);
  }

  if (config.reportTo) {
    directives.push(`report-to ${config.reportTo}`);
  }

  return directives.join('; ');
}

// ============================================================================
// Nonce Generation
// ============================================================================

/**
 * Generate cryptographically secure nonce
 * @doc.purpose Create nonce for inline script/style CSP
 */
export function generateNonce(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

// ============================================================================
// Helmet Configuration
// ============================================================================

/**
 * Helmet.js configuration for YAPPC
 * @doc.purpose Secure Express/Fastify apps with helmet
 */
export const helmetConfig = {
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'", "'unsafe-inline'", "'unsafe-eval'", 'blob:'],
      styleSrc: ["'self'", "'unsafe-inline'", 'https://fonts.googleapis.com'],
      imgSrc: ["'self'", 'data:', 'blob:', 'https:'],
      fontSrc: ["'self'", 'https://fonts.gstatic.com', 'data:'],
      connectSrc: ["'self'", 'wss:', 'https:'],
      mediaSrc: ["'self'", 'blob:', 'https:'],
      objectSrc: ["'none'"],
      frameSrc: ["'self'"],
      workerSrc: ["'self'", 'blob:'],
      upgradeInsecureRequests: [],
    },
  },
  crossOriginEmbedderPolicy: false, // Allow canvas blob URLs
  crossOriginResourcePolicy: { policy: 'cross-origin' },
  dnsPrefetchControl: { allow: false },
  frameguard: { action: 'deny' },
  hidePoweredBy: true,
  hsts: {
    maxAge: 31536000,
    includeSubDomains: true,
    preload: true,
  },
  ieNoOpen: true,
  noSniff: true,
  originAgentCluster: true,
  permittedCrossDomainPolicies: { permittedPolicies: 'none' },
  referrerPolicy: { policy: 'strict-origin-when-cross-origin' },
  xssFilter: true,
};

// ============================================================================
// HTML Template Helper
// ============================================================================

/**
 * Inject CSP nonce into HTML templates
 * @doc.purpose Add nonce to inline scripts/styles
 */
export function injectCSPNonce(html: string, nonce: string): string {
  // Replace inline scripts with nonce
  return html
    .replace(/<script(?![^>]*nonce)/g, `<script nonce="${nonce}"`)
    .replace(/<style(?![^>]*nonce)/g, `<style nonce="${nonce}"`);
}
