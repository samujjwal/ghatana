/**
 * Auth Routes Tests
 *
 * Tests the profile endpoints exposed by DCMAAR backend after platform migration.
 * All auth token issuance (register, login, refresh) now belongs to auth-gateway.
 * Tests simulate requests using locally-signed JWT tokens (the backward-compatible
 * fallback path in the auth middleware).
 *
 * Covered endpoints:
 * - GET  /api/auth/me      — Return authenticated user profile
 * - PUT  /api/auth/profile — Update authenticated user profile
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import jwt from 'jsonwebtoken';
import { FastifyInstance } from 'fastify';
import { request } from '../helpers/request.helper';
import { createTestApp } from '../helpers/app.helper';
import { randomEmail } from '../setup';
import { query } from '../../db';

const JWT_SECRET = process.env.JWT_SECRET ?? 'development-secret-key';

/** Create a locally-signed access token (mirrors the fallback path in auth.middleware.ts) */
function makeAccessToken(userId: string): string {
  return jwt.sign({ userId, type: 'access' }, JWT_SECRET, { expiresIn: '15m' });
}

let app: FastifyInstance;

describe('Auth Routes', () => {
  beforeAll(async () => {
    app = await createTestApp();
  });

  afterAll(async () => {
    await app.close();
  });

  describe('GET /api/auth/me', () => {
    it('should return the current user profile with a valid token', async () => {
      // GIVEN: A user in the database and a valid local access token
      const email = randomEmail();
      const result = await query<{ id: string }>(
        'INSERT INTO users (email, password_hash, display_name) VALUES ($1, $2, $3) RETURNING id',
        [email, 'placeholder_hash', 'Test User']
      );
      const userId = result[0].id;
      const accessToken = makeAccessToken(userId);

      try {
        // WHEN
        const response = await request(app)
          .get('/api/auth/me')
          .set('Authorization', 'Bearer ' + accessToken)
          .expect(200);

        // THEN
        expect(response.body.user).toBeDefined();
        expect(response.body.user.email).toBe(email);
        expect(response.body.user.display_name).toBe('Test User');
      } finally {
        await query('DELETE FROM users WHERE id = $1', [userId]);
      }
    });

    it('should return 401 when no token is provided', async () => {
      const response = await request(app)
        .get('/api/auth/me')
        .expect(401);

      expect(response.body.error).toBeDefined();
    });

    it('should return 401 for an invalid token', async () => {
      const response = await request(app)
        .get('/api/auth/me')
        .set('Authorization', 'Bearer invalid-token')
        .expect(401);

      expect(response.body.error).toBeDefined();
    });
  });

  describe('PUT /api/auth/profile', () => {
    let userId: string;
    let accessToken: string;

    beforeAll(async () => {
      const result = await query<{ id: string }>(
        'INSERT INTO users (email, password_hash) VALUES ($1, $2) RETURNING id',
        [randomEmail(), 'placeholder_hash']
      );
      userId = result[0].id;
      accessToken = makeAccessToken(userId);
    });

    afterAll(async () => {
      if (userId) {
        await query('DELETE FROM users WHERE id = $1', [userId]);
      }
    });

    it('should update the display name', async () => {
      const response = await request(app)
        .put('/api/auth/profile')
        .set('Authorization', 'Bearer ' + accessToken)
        .send({ displayName: 'Updated Name' })
        .expect(200);

      expect(response.body.user.display_name).toBe('Updated Name');
    });

    it('should update the photo URL', async () => {
      const response = await request(app)
        .put('/api/auth/profile')
        .set('Authorization', 'Bearer ' + accessToken)
        .send({ photoUrl: 'https://example.com/photo.jpg' })
        .expect(200);

      expect(response.body.user.photo_url).toBe('https://example.com/photo.jpg');
    });

    it('should return 400 for an invalid photo URL', async () => {
      const response = await request(app)
        .put('/api/auth/profile')
        .set('Authorization', 'Bearer ' + accessToken)
        .send({ photoUrl: 'not-a-url' })
        .expect(400);

      expect(response.body.error).toContain('URL');
    });

    it('should return 401 for an unauthenticated request', async () => {
      const response = await request(app)
        .put('/api/auth/profile')
        .send({ displayName: 'Test' })
        .expect(401);

      expect(response.body.error).toBeDefined();
    });
  });
});
