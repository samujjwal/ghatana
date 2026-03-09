/**
 * API Key Authentication Middleware
 *
 * Authenticates requests using API keys (Bearer token with `fli_` prefix).
 * API keys are available for Teams tier users only.
 * Keys are hashed with SHA-256 and validated against the database.
 *
 * Usage:
 *   preHandler: [requireApiKeyOrAuth]
 *
 * The middleware checks for either:
 *   1. A valid JWT token (standard auth)
 *   2. An API key in the Authorization header (Bearer fli_xxxxxxxxxxxx)
 */

import { FastifyRequest, FastifyReply } from 'fastify';
import { createHash } from 'crypto';
import { prisma } from '../lib/prisma';

/**
 * Hash an API key using SHA-256.
 */
export function hashApiKey(key: string): string {
  return createHash('sha256').update(key).digest('hex');
}

/**
 * Generate a new API key with the standard prefix.
 * Returns the plaintext key (only shown once) and its hash.
 */
export function generateApiKey(): { plaintext: string; hash: string; prefix: string } {
  const randomBytes = Array.from({ length: 32 }, () =>
    Math.floor(Math.random() * 16).toString(16)
  ).join('');
  const plaintext = `fli_${randomBytes}`;
  const hash = hashApiKey(plaintext);
  const prefix = plaintext.slice(0, 11); // "fli_" + first 7 chars
  return { plaintext, hash, prefix };
}

/**
 * Middleware that accepts either JWT or API key authentication.
 * Sets request.user with userId and scopes if API key is used.
 */
export async function requireApiKeyOrAuth(
  request: FastifyRequest,
  reply: FastifyReply
): Promise<void> {
  const authHeader = request.headers.authorization;

  if (!authHeader) {
    return reply.code(401).send({ error: 'Authentication required' });
  }

  const token = authHeader.replace(/^Bearer\s+/i, '');

  // Check if it's an API key (starts with fli_)
  if (token.startsWith('fli_')) {
    return authenticateWithApiKey(request, reply, token);
  }

  // Otherwise try JWT
  try {
    await request.jwtVerify();
  } catch {
    return reply.code(401).send({ error: 'Invalid token' });
  }
}

async function authenticateWithApiKey(
  request: FastifyRequest,
  reply: FastifyReply,
  key: string
): Promise<void> {
  const keyHash = hashApiKey(key);

  const apiKey = await prisma.apiKey.findUnique({
    where: { keyHash },
    include: {
      user: {
        select: {
          id: true,
          email: true,
          role: true,
          subscriptionTier: true,
          deletedAt: true,
        },
      },
    },
  });

  if (!apiKey) {
    return reply.code(401).send({ error: 'Invalid API key' });
  }

  // Check if key is revoked
  if (apiKey.revokedAt) {
    return reply.code(401).send({ error: 'API key has been revoked' });
  }

  // Check if key is expired
  if (apiKey.expiresAt && new Date() > apiKey.expiresAt) {
    return reply.code(401).send({ error: 'API key has expired' });
  }

  // Check user is active
  if (apiKey.user.deletedAt) {
    return reply.code(401).send({ error: 'Account is deactivated' });
  }

  // Check Teams tier (API keys are Teams-only feature)
  if (apiKey.user.subscriptionTier !== 'teams') {
    return reply.code(403).send({
      error: 'API key access requires Teams tier subscription',
    });
  }

  // Update last used timestamp (fire-and-forget)
  prisma.apiKey
    .update({
      where: { id: apiKey.id },
      data: { lastUsedAt: new Date() },
    })
    .catch(() => {}); // Non-blocking

  // Attach user info to request (compatible with JWT user shape)
  (request as any).user = {
    userId: apiKey.user.id,
    email: apiKey.user.email,
    role: apiKey.user.role,
    apiKeyId: apiKey.id,
    scopes: apiKey.scopes,
  };
}

/**
 * Middleware to check if the request has a specific API key scope.
 * Must be used after requireApiKeyOrAuth.
 */
export function requireScope(scope: string) {
  return async (request: FastifyRequest, reply: FastifyReply): Promise<void> => {
    const user = (request as any).user;

    // JWT-authenticated users have all scopes implicitly
    if (!user?.apiKeyId) return;

    // API key users need explicit scope
    const scopes: string[] = user.scopes || [];
    if (!scopes.includes(scope) && !scopes.includes('*')) {
      return reply.code(403).send({
        error: `API key missing required scope: ${scope}`,
      });
    }
  };
}
