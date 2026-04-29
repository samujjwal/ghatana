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
import crypto from 'crypto';

const logger = createStandaloneLogger({ component: 'LtiAuthMiddleware' });
const NONCE_TTL_SECONDS = 300; // 5 minutes

interface NonceStore {
  exists(key: string): Promise<number>;
  setex(key: string, seconds: number, value: string): Promise<string | void>;
}

interface FastifyInstanceWithRedis {
  redis?: NonceStore;
}

interface JwkKey {
  kty: string;
  kid: string;
  use?: string;
  n?: string;
  e?: string;
  x5c?: string[];
  [key: string]: unknown;
}

interface JwksResponse {
  keys: JwkKey[];
}

async function fetchJwks(jwksUrl: string): Promise<JwksResponse> {
  const response = await fetch(jwksUrl, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`JWKS fetch failed: ${response.status} ${response.statusText}`);
  }
  return (await response.json()) as JwksResponse;
}

function getKeyFromJwks(jwks: JwksResponse, kid: string): crypto.KeyObject {
  const key = jwks.keys.find((k) => k.kid === kid);
  if (!key) {
    throw new Error(`Key with kid "${kid}" not found in JWKS`);
  }

  if (key.x5c && key.x5c.length > 0) {
    const cert = `-----BEGIN CERTIFICATE-----\n${key.x5c[0]}\n-----END CERTIFICATE-----`;
    return crypto.createPublicKey(cert);
  }

  if (key.n && key.e) {
    const jwk = {
      kty: 'RSA',
      n: key.n,
      e: key.e,
    };
    return crypto.createPublicKey({ key: jwk, format: 'jwk' });
  }

  throw new Error(`Unsupported JWK format for kid "${kid}"`);
}

function verifyJwtSignature(token: string, publicKey: crypto.KeyObject): boolean {
  const [headerB64, payloadB64, signatureB64] = token.split('.');
  if (!headerB64 || !payloadB64 || !signatureB64) {
    return false;
  }

  const signingInput = `${headerB64}.${payloadB64}`;
  const signature = Buffer.from(signatureB64, 'base64url');

  const header = JSON.parse(Buffer.from(headerB64, 'base64url').toString()) as { alg?: string };
  const algorithm = (header.alg ?? 'RS256') as string;

  const algoMap: Record<string, string> = {
    RS256: 'SHA256',
    RS384: 'SHA384',
    RS512: 'SHA512',
    ES256: 'SHA256',
    ES384: 'SHA384',
    ES512: 'SHA512',
  };

  const digest = algoMap[algorithm];
  if (!digest) {
    throw new Error(`Unsupported JWT algorithm: ${algorithm}`);
  }

  return crypto.verify(digest, Buffer.from(signingInput), publicKey, signature);
}

async function resolvePublicKey(token: string, publicKeyOrJwksUrl: string): Promise<crypto.KeyObject> {
  // If it's a PEM public key directly
  if (publicKeyOrJwksUrl.includes('-----BEGIN') || publicKeyOrJwksUrl.includes('-----BEGIN PUBLIC KEY')) {
    return crypto.createPublicKey(publicKeyOrJwksUrl);
  }

  // Otherwise, treat it as a JWKS URL
  const header = JSON.parse(
    Buffer.from(token.split('.')[0]!, 'base64url').toString(),
  ) as { kid?: string };

  if (!header.kid) {
    throw new Error('JWT header missing "kid" for JWKS lookup');
  }

  const jwks = await fetchJwks(publicKeyOrJwksUrl);
  return getKeyFromJwks(jwks, header.kid);
}

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

interface LtiUser {
  id: string;
  roles: string[];
  context?: {
    id: string;
    label?: string;
    title?: string;
    type?: string[];
  };
}

declare module 'fastify' {
  interface FastifyRequest {
    ltiClaims?: LtiClaims;
    ltiUser?: LtiUser;
  }
}

interface LtiValidationResult {
  valid: boolean;
  claims?: LtiClaims;
  error?: string;
}

/**
 * Validates LTI JWT token with real signature verification.
 * Supports PEM public keys and JWKS endpoints.
 */
async function validateLtiToken(
  token: string,
  publicKeyOrJwksUrl: string,
): Promise<LtiValidationResult> {
  try {
    // 1. Decode JWT header and payload
    const parts = token.split('.');
    if (parts.length !== 3) {
      return { valid: false, error: 'Invalid JWT format' };
    }

    const payload = JSON.parse(Buffer.from(parts[1]!, 'base64url').toString()) as Record<string, unknown>;
    const header = JSON.parse(Buffer.from(parts[0]!, 'base64url').toString()) as Record<string, unknown>;

    // 2. Verify signature with public key or JWKS
    try {
      const publicKey = await resolvePublicKey(token, publicKeyOrJwksUrl);
      const verified = verifyJwtSignature(token, publicKey);
      if (!verified) {
        logger.warn({
          message: 'LTI JWT signature verification failed',
          alg: header.alg,
          kid: header.kid,
        });
        return { valid: false, error: 'Invalid signature' };
      }
    } catch (sigError) {
      logger.error({
        message: 'LTI signature resolution/verification failed',
        error: sigError instanceof Error ? sigError.message : String(sigError),
      });
      return { valid: false, error: 'Signature verification failed' };
    }

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
      'https://purl.imsglobal.org/spec/lti/claim/deployment_id',
      'https://purl.imsglobal.org/spec/lti/claim/target_link_uri',
    ];

    for (const claim of requiredClaims) {
      if (!payload[claim]) {
        return { valid: false, error: `Missing required claim: ${claim}` };
      }
    }

    // 4. Validate expiration
    const now = Math.floor(Date.now() / 1000);
    if (typeof payload.exp === 'number' && payload.exp < now) {
      return { valid: false, error: 'Token expired' };
    }

    // 5. Validate issued at (not too far in the future)
    if (typeof payload.iat === 'number' && payload.iat > now + 300) {
      return { valid: false, error: 'Token issued in the future' };
    }

    // 6. Validate LTI version
    const ltiVersion = payload['https://purl.imsglobal.org/spec/lti/claim/version'];
    if (ltiVersion !== '1.3.0') {
      return { valid: false, error: `Unsupported LTI version: ${String(ltiVersion)}` };
    }

    return { valid: true, claims: payload as unknown as LtiClaims };
  } catch (error) {
    logger.error({
      message: 'LTI token validation error',
      error: error instanceof Error ? error.message : String(error),
    });
    return { valid: false, error: 'Token validation failed' };
  }
}

/**
 * Validates nonce to prevent replay attacks using a Redis-backed nonce store.
 * Nonces are stored with a TTL matching the token acceptance window.
 */
async function validateNonce(
  nonce: string,
  iss: string,
  store?: NonceStore,
): Promise<boolean> {
  if (!store) {
    logger.error({
      message: 'NonceStore not available; nonce replay protection unavailable',
      iss,
    });
    return false;
  }

  const key = `lti:nonce:${iss}:${nonce}`;
  const exists = await store.exists(key);
  if (exists) {
    logger.warn({ key, iss, nonce }, 'LTI nonce replay detected');
    return false;
  }

  await store.setex(key, NONCE_TTL_SECONDS, '1');
  return true;
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

    if (!validation.claims) {
      reply.code(401).send({
        error: 'Unauthorized',
        message: 'LTI token claims missing',
      });
      return;
    }

    // 4. Validate nonce to prevent replay attacks
    const server = request.server as unknown as FastifyInstanceWithRedis;
    const nonceStore = server.redis;
    const nonceValid = await validateNonce(
      validation.claims.nonce,
      validation.claims.iss,
      nonceStore,
    );

    if (!nonceValid) {
      logger.warn({
        message: 'LTI nonce validation failed (replay attack)',
        nonce: validation.claims.nonce,
        issuer: validation.claims.iss,
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
      validation.claims['https://purl.imsglobal.org/spec/lti/claim/roles'] ?? [];
    const contextClaims =
      validation.claims['https://purl.imsglobal.org/spec/lti/claim/context'];

    request.ltiClaims = validation.claims;
    request.ltiUser = {
      id: validation.claims.sub,
      roles: roleClaims,
      context: contextClaims,
    };

    logger.info({
      message: 'LTI authentication successful',
      userId: validation.claims.sub,
      issuer: validation.claims.iss,
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
  const ltiUser = request.ltiUser;
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
        userRoles: request.ltiUser?.roles,
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
  const claims = request.ltiClaims;
  if (!claims) {
    return false;
  }

  const deploymentId = claims['https://purl.imsglobal.org/spec/lti/claim/deployment_id'];
  return allowedDeployments.includes(deploymentId);
}
