/**
 * Auth routes — platform-integrated user profile endpoints.
 *
 * <p><b>Purpose</b><br>
 * Exposes user profile read/update endpoints for the DCMAAR backend.
 * All authentication (login, registration, token issuance, password reset)
 * is handled exclusively by the Ghatana auth-gateway service. Clients must
 * obtain a platform Bearer token from auth-gateway and include it in every
 * request; the {@link authenticate} middleware validates it here.
 *
 * <p><b>Endpoints</b><br>
 * - GET  /me      — Return the authenticated user's profile
 * - PUT  /profile — Update the authenticated user's display name or photo URL
 *
 * <p><b>Removed (delegated to auth-gateway)</b><br>
 * - POST /register, POST /login, POST /refresh, POST /logout
 * - POST /password-reset/request, POST /password-reset/confirm
 *
 * @doc.type route
 * @doc.purpose User profile endpoints backed by platform auth
 * @doc.layer backend
 * @doc.pattern REST API Routes
 */
import { FastifyPluginAsync, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import * as authService from '../services/auth.service';
import { authenticate, AuthRequest } from '../middleware/auth.middleware';

const updateProfileSchema = z.object({
  displayName: z.string().optional(),
  photoUrl: z.string().url('Invalid photo URL').optional(),
});

const authRoutes: FastifyPluginAsync = async (fastify) => {
  /**
   * GET /me
   * Return the authenticated user's profile.
   */
  fastify.get('/me', { preHandler: authenticate }, async (request: AuthRequest, reply: FastifyReply) => {
    try {
      if (!request.userId) {
        return reply.status(401).send({ error: 'Not authenticated' });
      }

      const user = await authService.getUserById(request.userId);

      if (!user) {
        return reply.status(404).send({ error: 'User not found' });
      }

      return reply.send({ user });
    } catch (_error) {
      return reply.status(500).send({ error: 'Failed to get user' });
    }
  });

  /**
   * PUT /profile
   * Update authenticated user's display name and/or photo URL.
   */
  fastify.put('/profile', { preHandler: authenticate }, async (request: AuthRequest, reply: FastifyReply) => {
    try {
      if (!request.userId) {
        return reply.status(401).send({ error: 'Not authenticated' });
      }

      const updates = updateProfileSchema.parse(request.body);
      const user = await authService.updateProfile(request.userId, updates);

      return reply.send({ user });
    } catch (error) {
      if (error instanceof z.ZodError) {
        return reply.status(400).send({ error: error.issues[0].message });
      }

      if (error instanceof Error) {
        return reply.status(400).send({ error: error.message });
      }

      return reply.status(500).send({ error: 'Profile update failed' });
    }
  });
};

export default authRoutes;
