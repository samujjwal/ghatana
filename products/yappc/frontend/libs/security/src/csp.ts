/**
 * @file CSP (Content Security Policy) Middleware
 * Security headers implementation for YAPPC
 * 
 * @doc.type middleware
 * @doc.purpose Protect against XSS, data injection, and other attacks
 * @doc.layer infrastructure
 * @doc.pattern Security
 */

import { type FastifyPluginAsync } from 'fastify';
import fp from 'fastify-plugin';

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
const defaultCSPConfig: CSPConfig = {
  defaultSrc: ["'self'"],
  scriptSrc: [
    "'self'",
    "'unsafe-inline'", // Required for BlockSuite/Monaco
    "'unsafe-eval'",     // Required for Monaco editor
    "blob:",             // Required for worker scripts
  ],
  styleSrc: [
    "'self'",
    "'unsafe-inline'",   // Required for dynamic styles
    "https://fonts.googleapis.com",
  ],
  imgSrc: [
    "'self'",
    "data:",             // For base64 encoded images
    "blob:",             // For canvas exports
    "https:",            // For external images
  ],
  fontSrc: [
    "'self'",
    "https://fonts.gstatic.com",
    "data:",
  ],
  connectSrc: [
    "'self'",
    "wss:",              // WebSocket connections
    "https:",            // API calls
  ],
  mediaSrc: [
    "'self'",
    "blob:",
    "https:",
  ],
  objectSrc: ["'none'"],  // Disable plugins
  frameSrc: [
    "'self'",
    "https:",            // For embeds
  ],
  workerSrc: [
    "'self'",
    "blob:",             // For canvas/web workers
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
function buildCSPHeader(config: CSPConfig): string {
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
function generateNonce(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

// ============================================================================
// Security Headers Plugin
// ============================================================================

/**
 * Fastify plugin for CSP and security headers
 * @doc.purpose Apply security headers to all responses
 */
const securityHeadersPlugin: FastifyPluginAsync<{
  csp?: CSPConfig;
  reportOnly?: boolean;
}> = async (fastify, options) => {
  const cspConfig = { ...defaultCSPConfig, ...options.csp };
  const headerName = options.reportOnly 
    ? 'Content-Security-Policy-Report-Only' 
    : 'Content-Security-Policy';
  
  // Add hook to set headers on all responses
  fastify.addHook('onSend', async (request, reply, payload) => {
    // Generate nonce for this request
    const nonce = generateNonce();
    
    // Store nonce for use in templates
    request.cspNonce = nonce;
    
    // Build CSP with nonce if enabled
    let cspHeader = buildCSPHeader(cspConfig);
    if (cspConfig.generateNonce) {
      cspHeader = cspHeader.replace(/'unsafe-inline'/g, `'nonce-${nonce}'`);
    }
    
    // Set CSP header
    reply.header(headerName, cspHeader);
    
    // Additional security headers
    reply.header('X-Content-Type-Options', 'nosniff');
    reply.header('X-Frame-Options', 'DENY');
    reply.header('X-XSS-Protection', '1; mode=block');
    reply.header('Referrer-Policy', 'strict-origin-when-cross-origin');
    reply.header('Permissions-Policy', 
      'accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), payment=(), usb=()');
    
    // HSTS (HTTPS Strict Transport Security)
    reply.header('Strict-Transport-Security', 'max-age=31536000; includeSubDomains; preload');
    
    return payload;
  });
};

// Extend Fastify request type
declare module 'fastify' {
  interface FastifyRequest {
    cspNonce: string;
  }
}

export default fp(securityHeadersPlugin, {
  name: 'security-headers',
  fastify: '4.x',
});

// ============================================================================
// Express/Connect Middleware (for legacy support)
// ============================================================================

import { type Request, type Response, type NextFunction } from 'express';

/**
 * Express middleware for CSP headers
 * @doc.purpose Apply CSP for Express-based services
 */
export function cspMiddleware(config: CSPConfig = defaultCSPConfig) {
  return (req: Request, res: Response, next: NextFunction) => {
    const nonce = generateNonce();
    (req as any).cspNonce = nonce;
    
    let cspHeader = buildCSPHeader(config);
    if (config.generateNonce) {
      cspHeader = cspHeader.replace(/'unsafe-inline'/g, `'nonce-${nonce}'`);
    }
    
    res.setHeader('Content-Security-Policy', cspHeader);
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('X-XSS-Protection', '1; mode=block');
    res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
    res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains; preload');
    
    next();
  };
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
      scriptSrc: [
        "'self'",
        "'unsafe-inline'",
        "'unsafe-eval'",
        "blob:",
      ],
      styleSrc: [
        "'self'",
        "'unsafe-inline'",
        "https://fonts.googleapis.com",
      ],
      imgSrc: [
        "'self'",
        "data:",
        "blob:",
        "https:",
      ],
      fontSrc: [
        "'self'",
        "https://fonts.gstatic.com",
        "data:",
      ],
      connectSrc: [
        "'self'",
        "wss:",
        "https:",
      ],
      mediaSrc: [
        "'self'",
        "blob:",
        "https:",
      ],
      objectSrc: ["'none'"],
      frameSrc: ["'self'"],
      workerSrc: [
        "'self'",
        "blob:",
      ],
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
