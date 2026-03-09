/**
 * API Key Management Routes
 *
 * CRUD endpoints for managing API keys. Available to Teams tier users only.
 * Keys are generated server-side and the plaintext is returned only at creation.
 *
 * Endpoints:
 *   POST   /api/api-keys          - Create a new API key
 *   GET    /api/api-keys          - List user's API keys
 *   GET    /api/api-keys/:id      - Get API key details
 *   PATCH  /api/api-keys/:id      - Update key name/scopes
 *   DELETE /api/api-keys/:id      - Revoke an API key
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import { prisma } from '../lib/prisma';
import { generateApiKey } from '../middleware/api-key-auth';

// Require standard JWT auth for key management (not API key auth itself)
const requireAuth = async (request: FastifyRequest, reply: FastifyReply) => {
  try {
    await request.jwtVerify();
  } catch {
    return reply.code(401).send({ error: 'Authentication required' });
  }
};

interface JwtPayload {
  userId: string;
  email: string;
}

const VALID_SCOPES = ['read', 'write', 'moments:read', 'moments:write', 'spheres:read', 'spheres:write', 'search', '*'] as const;

const createKeySchema = z.object({
  name: z.string().min(1).max(255),
  scopes: z.array(z.enum(VALID_SCOPES)).min(1).default(['read']),
  expiresInDays: z.number().int().min(1).max(365).optional(),
});

const updateKeySchema = z.object({
  name: z.string().min(1).max(255).optional(),
  scopes: z.array(z.enum(VALID_SCOPES)).min(1).optional(),
});

export default async function apiKeyRoutes(app: FastifyInstance) {
  /**
   * POST /api/api-keys - Create a new API key
   */
  app.post('/', { preHandler: [requireAuth] }, async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user as JwtPayload;

    // Check Teams tier
    const dbUser = await prisma.user.findUnique({
      where: { id: user.userId },
      select: { subscriptionTier: true },
    });

    if (!dbUser || dbUser.subscriptionTier !== 'teams') {
      return reply.code(403).send({
        error: 'API keys are available for Teams tier subscribers only',
        currentTier: dbUser?.subscriptionTier || 'free',
      });
    }

    // Limit keys per user
    const existingCount = await prisma.apiKey.count({
      where: { userId: user.userId, revokedAt: null },
    });

    if (existingCount >= 10) {
      return reply.code(400).send({
        error: 'Maximum of 10 active API keys allowed. Revoke an existing key first.',
      });
    }

    const parsed = createKeySchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({
        error: 'Invalid request body',
        details: parsed.error.issues,
      });
    }

    const { name, scopes, expiresInDays } = parsed.data;
    const { plaintext, hash, prefix } = generateApiKey();

    const expiresAt = expiresInDays
      ? new Date(Date.now() + expiresInDays * 24 * 60 * 60 * 1000)
      : null;

    const apiKey = await prisma.apiKey.create({
      data: {
        userId: user.userId,
        name,
        keyHash: hash,
        keyPrefix: prefix,
        scopes,
        expiresAt,
      },
      select: {
        id: true,
        name: true,
        keyPrefix: true,
        scopes: true,
        expiresAt: true,
        createdAt: true,
      },
    });

    return reply.code(201).send({
      ...apiKey,
      key: plaintext, // ⚠️ Only returned at creation time — cannot be retrieved again
      warning: 'Save this API key now. It will not be shown again.',
    });
  });

  /**
   * GET /api/api-keys - List API keys (without hashes)
   */
  app.get('/', { preHandler: [requireAuth] }, async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user as JwtPayload;

    const keys = await prisma.apiKey.findMany({
      where: { userId: user.userId },
      select: {
        id: true,
        name: true,
        keyPrefix: true,
        scopes: true,
        lastUsedAt: true,
        expiresAt: true,
        revokedAt: true,
        createdAt: true,
      },
      orderBy: { createdAt: 'desc' },
    });

    return reply.send({
      keys,
      total: keys.length,
      active: keys.filter((k) => !k.revokedAt).length,
    });
  });

  /**
   * GET /api/api-keys/:id - Get single key details
   */
  app.get('/:id', { preHandler: [requireAuth] }, async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user as JwtPayload;
    const { id } = request.params as { id: string };

    const key = await prisma.apiKey.findFirst({
      where: { id, userId: user.userId },
      select: {
        id: true,
        name: true,
        keyPrefix: true,
        scopes: true,
        lastUsedAt: true,
        expiresAt: true,
        revokedAt: true,
        createdAt: true,
      },
    });

    if (!key) {
      return reply.code(404).send({ error: 'API key not found' });
    }

    return reply.send(key);
  });

  /**
   * PATCH /api/api-keys/:id - Update key name/scopes
   */
  app.patch('/:id', { preHandler: [requireAuth] }, async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user as JwtPayload;
    const { id } = request.params as { id: string };

    const parsed = updateKeySchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({
        error: 'Invalid request body',
        details: parsed.error.issues,
      });
    }

    // Verify ownership
    const existing = await prisma.apiKey.findFirst({
      where: { id, userId: user.userId },
    });

    if (!existing) {
      return reply.code(404).send({ error: 'API key not found' });
    }

    if (existing.revokedAt) {
      return reply.code(400).send({ error: 'Cannot update a revoked API key' });
    }

    const updated = await prisma.apiKey.update({
      where: { id },
      data: parsed.data,
      select: {
        id: true,
        name: true,
        keyPrefix: true,
        scopes: true,
        lastUsedAt: true,
        expiresAt: true,
        createdAt: true,
      },
    });

    return reply.send(updated);
  });

  /**
   * DELETE /api/api-keys/:id - Revoke an API key (soft delete)
   */
  app.delete('/:id', { preHandler: [requireAuth] }, async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user as JwtPayload;
    const { id } = request.params as { id: string };

    // Verify ownership
    const existing = await prisma.apiKey.findFirst({
      where: { id, userId: user.userId },
    });

    if (!existing) {
      return reply.code(404).send({ error: 'API key not found' });
    }

    if (existing.revokedAt) {
      return reply.code(400).send({ error: 'API key is already revoked' });
    }

    await prisma.apiKey.update({
      where: { id },
      data: { revokedAt: new Date() },
    });

    return reply.send({ success: true, message: 'API key revoked' });
  });
}
