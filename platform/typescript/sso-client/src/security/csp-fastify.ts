/**
 * @file CSP Fastify Plugin
 *
 * Fastify-specific adapter for the Ghatana CSP security module.
 * Only import this from server-side Fastify applications — never from
 * client-side bundles.
 *
 * Usage:
 *   import cspPlugin from '@ghatana/sso-client/security/fastify';
 *   await fastify.register(cspPlugin, { csp: myConfig });
 *
 * @doc.type plugin
 * @doc.purpose Apply CSP and security headers to all Fastify responses
 * @doc.layer infrastructure
 * @doc.pattern Security
 */

import { type FastifyPluginAsync } from 'fastify';
import fp from 'fastify-plugin';

import {
  type CSPConfig,
  buildCSPHeader,
  defaultCSPConfig,
  generateNonce,
} from './csp.js';

// Re-export core types so consumers don't need to import from two files
export type { CSPConfig } from './csp.js';

// Extend Fastify request type with cspNonce
declare module 'fastify' {
  interface FastifyRequest {
    cspNonce: string;
  }
}

/**
 * Fastify plugin for CSP and security headers.
 * Registers an `onSend` hook that sets all standard security headers on
 * every response and generates a per-request CSP nonce.
 */
const securityHeadersPlugin: FastifyPluginAsync<{
  csp?: CSPConfig;
  reportOnly?: boolean;
}> = async (fastify, options) => {
  const cspConfig = { ...defaultCSPConfig, ...options.csp };
  const headerName = options.reportOnly
    ? 'Content-Security-Policy-Report-Only'
    : 'Content-Security-Policy';

  fastify.addHook('onSend', async (request, reply, payload) => {
    const nonce = generateNonce();
    request.cspNonce = nonce;

    let cspHeader = buildCSPHeader(cspConfig);
    if (cspConfig.generateNonce) {
      cspHeader = cspHeader.replace(/'unsafe-inline'/g, `'nonce-${nonce}'`);
    }

    reply.header(headerName, cspHeader);
    reply.header('X-Content-Type-Options', 'nosniff');
    reply.header('X-Frame-Options', 'DENY');
    reply.header('X-XSS-Protection', '1; mode=block');
    reply.header('Referrer-Policy', 'strict-origin-when-cross-origin');
    reply.header(
      'Permissions-Policy',
      'accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), payment=(), usb=()'
    );
    reply.header(
      'Strict-Transport-Security',
      'max-age=31536000; includeSubDomains; preload'
    );

    return payload;
  });
};

export default fp(securityHeadersPlugin, {
  name: 'security-headers',
  fastify: '4.x',
});
