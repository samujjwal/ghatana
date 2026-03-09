/**
 * API Deprecation Middleware
 * 
 * Adds Sunset headers and deprecation notices to legacy endpoints.
 * Follows RFC 8594 ( Sunset HTTP Header Field ) for deprecation signaling.
 * 
 * @doc.type middleware
 * @doc.purpose Mark legacy API endpoints for deprecation
 * @doc.layer product
 * @doc.pattern Middleware
 */

import { FastifyRequest, FastifyReply, HookHandlerDoneFunction } from 'fastify';

// Sunset date for deprecated endpoints (90 days from now)
const DEPRECATION_SUNSET_DATE = new Date();
DEPRECATION_SUNSET_DATE.setDate(DEPRECATION_SUNSET_DATE.getDate() + 90);
const SUNSET_DATE_ISO = DEPRECATION_SUNSET_DATE.toISOString();

// Target URL for migration
const MIGRATION_TARGET = '/api';

/**
 * Add deprecation headers to response
 * 
 * Headers added:
 * - Sunset: Date when endpoint will be removed
 * - Deprecation: true
 * - Link: Link to migration guide/alternative
 */
export function addDeprecationHeaders(
  request: FastifyRequest,
  reply: FastifyReply,
  done: HookHandlerDoneFunction
): void {
  // Add RFC 8594 Sunset header
  reply.header('Sunset', SUNSET_DATE_ISO);
  
  // Add deprecation flag
  reply.header('Deprecation', 'true');
  
  // Add link to alternative (same path, but will be served by Java backend)
  const alternativeUrl = `${MIGRATION_TARGET}${request.url.replace(/^\/(api|v1)/, '')}`;
  reply.header('Link', `<${alternativeUrl}>; rel="successor-version"`);
  
  // Add warning header (RFC 7234)
  reply.header('Warning', '299 - "Legacy API endpoint. Migrate to Java backend."');
  
  done();
}

/**
 * Log deprecation warning for monitoring
 */
export function logDeprecation(
  request: FastifyRequest,
  reply: FastifyReply,
  done: HookHandlerDoneFunction
): void {
  console.warn(
    `[DEPRECATION] ${request.method} ${request.url} - ` +
    `Client: ${request.headers['user-agent'] || 'unknown'} - ` +
    `Sunset: ${SUNSET_DATE_ISO}`
  );
  
  done();
}

/**
 * Combined deprecation middleware
 * Adds headers and logs the deprecation
 */
export function deprecationMiddleware(
  request: FastifyRequest,
  reply: FastifyReply,
  done: HookHandlerDoneFunction
): void {
  addDeprecationHeaders(request, reply, () => {
    logDeprecation(request, reply, done);
  });
}

/**
 * Add deprecation headers and log warning
 * Call this at the start of deprecated route handlers
 */
export function markDeprecated(
  request: FastifyRequest,
  reply: FastifyReply
): void {
  // Add RFC 8594 Sunset header
  reply.header('Sunset', SUNSET_DATE_ISO);
  
  // Add deprecation flag
  reply.header('Deprecation', 'true');
  
  // Add link to alternative (same path, but will be served by Java backend)
  const alternativeUrl = `${MIGRATION_TARGET}${request.url.replace(/^\/(api|v1)/, '')}`;
  reply.header('Link', `<${alternativeUrl}>; rel="successor-version"`);
  
  // Add warning header (RFC 7234)
  reply.header('Warning', '299 - "Legacy API endpoint. Migrate to Java backend."');
  
  // Log the deprecation
  console.warn(
    `[DEPRECATION] ${request.method} ${request.url} accessed - ` +
    `Sunset date: ${SUNSET_DATE_ISO}`
  );
}
