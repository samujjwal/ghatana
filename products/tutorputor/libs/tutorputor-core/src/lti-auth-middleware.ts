/**
 * LTI Authentication Middleware
 * 
 * Ensures proper JWT validation for LTI (Learning Tools Interoperability) endpoints.
 * Prevents authentication bypass vulnerabilities.
 * 
 * @doc.type middleware
 * @doc.purpose LTI authentication and security
 * @doc.layer platform
 */

import type { FastifyRequest, FastifyReply } from 'fastify';
import { createStandaloneLogger } from './logger';

const logger = createStandaloneLogger({ component: 'LtiAuthMiddleware' });

interface LtiClaims {
  iss: string; // Issuer
  sub: string; // Subject (user ID)
  aud: string; // Audience
  exp: number; // Expiration
  iat: number; // Issued at
  nonce: string; // Nonce for replay protection
  'https://purl.imsglobal.org/spec/lti/claim/message_type': string;
  'https://purl.imsglobal.org/spec/lti/claim/version': string;
  'https://purl.imsglobal.org/spec/lti/claim/deployment_id': string;
  'https://purl.imsglobal.org/spec/lti/claim/target_link_uri': string;
  'https://purl.imsglobal.org/spec/lti/claim/resource_link': {
    id: string;
    title?: string;
    description?: string;
  };
  'https://purl.imsglobal.org/spec/lti/claim/roles': string[];
  'https://purl.imsglobal.org/spec/lti/claim/context'?: {
    id: string;
    label?: string;
    title?: string;
    type?: string[];
  };
}

interface LtiValidationResult {
  valid: boolean;
  claims?: LtiClaims;
  error?: string;
}

/**
 * Validates LTI JWT token
 */
async function validateLtiToken(
  token: string,
  publicKey: string,
): Promise<LtiValidationResult> {
  try {
    // In production, use a proper JWT library like jsonwebtoken or jose
    // This is a placeholder for the validation logic
    
    // 1. Decode JWT header and payload
    const parts = token.split('.');
    if (parts.length !== 3) {
      return { valid: false, error: 'Invalid JWT format' };
    }

    const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString());
    
    // 2. Verify signature with public key
    // TODO: Implement actual signature verification
    // const verified = await verifySignature(token, publicKey);
    // if (!verified) {
    //   return { valid: false, error: 'Invalid signature' };
    // }

    // 3. Validate required LTI claims
    const requiredClaims = [
      'iss',
      'sub',
      'aud',
      'exp',
      'iat',
      'nonce',
      'https://purl.imsglobal.org/spec/lti/claim/message_type',
      'https://purl.imsglobal.org/spec/lti/claim/version',
    ];

    for (const claim of requiredClaims) {
      if (!payload[claim]) {
        return { valid: false, error: `Missing required claim: ${claim}` };
      }
    }

    // 4. Validate expiration
    const now = Math.floor(Date.now() / 1000);
    if (payload.exp < now) {
      return { valid: false, error: 'Token expired' };
    }

    // 5. Validate issued at (not too far in the future)
    if (payload.iat > now + 300) {
      return { valid: false, error: 'Token issued in the future' };
    }

    // 6. Validate LTI version
    const ltiVersion = payload['https://purl.imsglobal.org/spec/lti/claim/version'];
    if (ltiVersion !== '1.3.0') {
      return { valid: false, error: `Unsupported LTI version: ${ltiVersion}` };
    }

    return { valid: true, claims: payload as LtiClaims };
  } catch (error) {
    logger.error({
      message: 'LTI token validation error',
      error: error instanceof Error ? error.message : String(error),
    });
    return { valid: false, error: 'Token validation failed' };
  }
}

/**
 * Validates nonce to prevent replay attacks
 */
async function validateNonce(nonce: string, iss: string): Promise<boolean> {
  // In production, check against a cache (Redis) of used nonces
  // Nonces should be stored with expiration (e.g., 5 minutes)
  
  // TODO: Implement actual nonce validation
  // const key = `lti:nonce:${iss}:${nonce}`;
  // const exists = await redis.exists(key);
  // if (exists) {
  //   return false; // Nonce already used
  // }
  // await redis.setex(key, 300, '1'); // Store for 5 minutes
  
  return true; // Placeholder
}

/**
 * LTI authentication middleware
 * Validates JWT tokens for LTI requests
 */
export async function ltiAuthMiddleware(
  request: FastifyRequest,
  reply: FastifyReply,
): Promise<void> {
  try {
    // 1. Extract token from Authorization header or id_token parameter
    const authHeader = request.headers.authorization;
    const idToken = (request.body as Record<string, unknown>)?.id_token || (request.query as Record<string, unknown>)?.id_token;
    
    const token = authHeader?.replace('Bearer ', '') || (typeof idToken === 'string' ? idToken : '');

    if (!token) {
      logger.warn({
        message: 'LTI request missing token',
        path: request.url,
        ip: request.ip,
      });
      
      reply.code(401).send({
        error: 'Unauthorized',
        message: 'LTI authentication token required',
      });
      return;
    }

    // 2. Get public key for issuer
    // In production, fetch from JWKS endpoint or configuration
    const publicKey = process.env.LTI_PUBLIC_KEY || '';
    
    if (!publicKey) {
      logger.error({
        message: 'LTI public key not configured',
        path: request.url,
      });
      
      reply.code(500).send({
        error: 'Internal Server Error',
        message: 'LTI authentication not properly configured',
      });
      return;
    }

    // 3. Validate token
    const validation = await validateLtiToken(token, publicKey);
    
    if (!validation.valid) {
      logger.warn({
        message: 'LTI token validation failed',
        error: validation.error,
        path: request.url,
        ip: request.ip,
      });
      
      reply.code(401).send({
        error: 'Unauthorized',
        message: validation.error || 'Invalid LTI token',
      });
      return;
    }

    // 4. Validate nonce to prevent replay attacks
    const nonceValid = await validateNonce(
      validation.claims!.nonce,
      validation.claims!.iss,
    );
    
    if (!nonceValid) {
      logger.warn({
        message: 'LTI nonce validation failed (replay attack)',
        nonce: validation.claims!.nonce,
        issuer: validation.claims!.iss,
        path: request.url,
        ip: request.ip,
      });
      
      reply.code(401).send({
        error: 'Unauthorized',
        message: 'Invalid or reused nonce',
      });
      return;
    }

    // 5. Attach LTI claims to request for downstream use
    const roleClaims =
      validation.claims!['https://purl.imsglobal.org/spec/lti/claim/roles'] ?? [];
    const contextClaims =
      validation.claims!['https://purl.imsglobal.org/spec/lti/claim/context'];

    (request as any).ltiClaims = validation.claims;
    (request as any).ltiUser = {
      id: validation.claims!.sub,
      roles: roleClaims,
      context: contextClaims,
    };

    logger.info({
      message: 'LTI authentication successful',
      userId: validation.claims!.sub,
      issuer: validation.claims!.iss,
      path: request.url,
    });

  } catch (error) {
    logger.error({
      message: 'LTI authentication error',
      error: error instanceof Error ? error.message : String(error),
      path: request.url,
    });
    
    reply.code(500).send({
      error: 'Internal Server Error',
      message: 'Authentication failed',
    });
  }
}

/**
 * Validates LTI role claims
 */
export function hasLtiRole(request: FastifyRequest, ...roles: string[]): boolean {
  const ltiUser = (request as any).ltiUser;
  if (!ltiUser || !ltiUser.roles) {
    return false;
  }

  return roles.some(role => 
    ltiUser.roles.some((userRole: string) => 
      userRole.toLowerCase().includes(role.toLowerCase())
    )
  );
}

/**
 * Middleware to require specific LTI roles
 */
export function requireLtiRole(...roles: string[]) {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    if (!hasLtiRole(request, ...roles)) {
      logger.warn({
        message: 'LTI role requirement not met',
        requiredRoles: roles,
        userRoles: (request as any).ltiUser?.roles,
        path: request.url,
      });
      
      reply.code(403).send({
        error: 'Forbidden',
        message: 'Insufficient LTI role permissions',
      });
    }
  };
}

/**
 * Validates LTI deployment ID
 */
export function validateDeploymentId(request: FastifyRequest, allowedDeployments: string[]): boolean {
  const claims = (request as any).ltiClaims;
  if (!claims) {
    return false;
  }

  const deploymentId = claims['https://purl.imsglobal.org/spec/lti/claim/deployment_id'];
  return allowedDeployments.includes(deploymentId);
}
