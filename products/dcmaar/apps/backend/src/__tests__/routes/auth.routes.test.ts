/**
 * Auth Routes Tests
 *
 * Tests authentication API endpoints including:
 * - POST /api/auth/register
 * - POST /api/auth/login
 * - POST /api/auth/refresh
 * - POST /api/auth/logout
 * - GET /api/auth/me
 * - PUT /api/auth/profile
 * - POST /api/auth/password-reset/request
 * - POST /api/auth/password-reset/confirm
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import { request } from '../helpers/request.helper';
import { createTestApp } from '../helpers/app.helper';
import { randomEmail } from '../setup';
import * as authService from '../../services/auth.service';

let app: FastifyInstance;

describe('Auth Routes', () => {
  beforeAll(async () => {
    app = await createTestApp();
  });

  afterAll(async () => {
    await app.close();
  });

  describe('POST /api/auth/register', () => {
    it('should register new user with valid data', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
        displayName: 'Test User',
      };

      const response = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      expect(response.body.accessToken).toBeDefined();
      expect(response.body.refreshToken).toBeDefined();
      expect(response.body.user).toBeDefined();
      expect(response.body.user.email).toBe(userData.email.toLowerCase());
      expect(response.body.user.display_name).toBe(userData.displayName);
    });

    it('should set refresh token in HTTP-only cookie', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      const response = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const cookies = response.headers['set-cookie'];
      expect(cookies).toBeDefined();
      // Fastify inject returns set-cookie as array or string
      const cookieStr = Array.isArray(cookies) ? cookies[0] : cookies;
      expect(cookieStr).toContain('refreshToken=');
      expect(cookieStr).toContain('HttpOnly');
    });

    it('should reject duplicate email', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      // Register first time
      await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      // Try to register again
      const response = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(400);

      expect(response.body.error).toContain('already exists');
    });

    it('should reject weak password', async () => {
      const userData = {
        email: randomEmail(),
        password: 'weak',
      };

      const response = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(400);

      expect(response.body.error).toBeDefined();
    });

    it('should reject invalid email', async () => {
      const userData = {
        email: 'invalid-email',
        password: 'ValidPassword123!',
      };

      const response = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(400);

      expect(response.body.error).toContain('email');
    });

    it('should reject password without uppercase', async () => {
      const userData = {
        email: randomEmail(),
        password: 'lowercase123!',
      };

      const response = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(400);

      expect(response.body.error).toContain('uppercase');
    });

    it('should reject password without number', async () => {
      const userData = {
        email: randomEmail(),
        password: 'NoNumbers!',
      };

      const response = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(400);

      expect(response.body.error).toContain('number');
    });

    it('should reject password without special character', async () => {
      const userData = {
        email: randomEmail(),
        password: 'NoSpecial123',
      };

      const response = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(400);

      expect(response.body.error).toContain('special');
    });
  });

  describe('POST /api/auth/login', () => {
    it('should login with valid credentials', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      // Register first
      await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      // Then login
      const response = await request(app)
        .post('/api/auth/login')
        .send(userData)
        .expect(200);

      expect(response.body.accessToken).toBeDefined();
      expect(response.body.refreshToken).toBeDefined();
      expect(response.body.user).toBeDefined();
      expect(response.body.user.email).toBe(userData.email.toLowerCase());
    });

    it('should set refresh token in cookie on login', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const response = await request(app)
        .post('/api/auth/login')
        .send(userData)
        .expect(200);

      const cookies = response.headers['set-cookie'];
      expect(cookies).toBeDefined();
      // Fastify inject returns set-cookie as array or string
      const cookieStr = Array.isArray(cookies) ? cookies[0] : cookies;
      expect(cookieStr).toContain('refreshToken=');
    });

    it('should reject wrong password', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const response = await request(app)
        .post('/api/auth/login')
        .send({
          email: userData.email,
          password: 'WrongPassword123!',
        })
        .expect(401);

      expect(response.body.error).toContain('Invalid');
    });

    it('should reject non-existent email', async () => {
      const response = await request(app)
        .post('/api/auth/login')
        .send({
          email: 'nonexistent@example.com',
          password: 'Password123!',
        })
        .expect(401);

      expect(response.body.error).toContain('Invalid');
    });

    it('should reject missing password', async () => {
      const response = await request(app)
        .post('/api/auth/login')
        .send({
          email: 'test@example.com',
        })
        .expect(400);

      expect(response.body.error).toBeDefined();
    });

    it('should be case-insensitive for email', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const response = await request(app)
        .post('/api/auth/login')
        .send({
          email: userData.email.toUpperCase(),
          password: userData.password,
        })
        .expect(200);

      expect(response.body.user.email).toBe(userData.email.toLowerCase());
    });
  });

  describe('POST /api/auth/refresh', () => {
    it('should refresh access token with valid refresh token', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      const registerResponse = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const refreshToken = registerResponse.body.refreshToken;

      // Wait 1ms to ensure different timestamp for new token
      await new Promise(resolve => setTimeout(resolve, 1));

      const response = await request(app)
        .post('/api/auth/refresh')
        .send({ refreshToken })
        .expect(200);

      expect(response.body.accessToken).toBeDefined();
      // Verify token is valid instead of just checking inequality
      const decoded = authService.verifyAccessToken(response.body.accessToken);
      expect(decoded?.userId).toBe(registerResponse.body.user.id);
    });

    it('should accept refresh token from cookie', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      const registerResponse = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const cookies = registerResponse.headers['set-cookie'];

      const response = await request(app)
        .post('/api/auth/refresh')
        .set('Cookie', cookies)
        .expect(200);

      expect(response.body.accessToken).toBeDefined();
    });

    it('should reject invalid refresh token', async () => {
      const response = await request(app)
        .post('/api/auth/refresh')
        .send({ refreshToken: 'invalid-token' })
        .expect(401);

      expect(response.body.error).toBeDefined();
    });

    it('should reject missing refresh token', async () => {
      const response = await request(app)
        .post('/api/auth/refresh')
        .send({})
        .expect(400);

      expect(response.body.error).toContain('required');
    });

    it('should reject access token as refresh token', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      const registerResponse = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const accessToken = registerResponse.body.accessToken;

      const response = await request(app)
        .post('/api/auth/refresh')
        .send({ refreshToken: accessToken })
        .expect(401);

      expect(response.body.error).toBeDefined();
    });
  });

  describe('POST /api/auth/logout', () => {
    it('should logout successfully', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      const registerResponse = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const refreshToken = registerResponse.body.refreshToken;

      const response = await request(app)
        .post('/api/auth/logout')
        .send({ refreshToken })
        .expect(200);

      expect(response.body.message).toContain('success');
    });

    it('should clear refresh token cookie', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      const registerResponse = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const cookies = registerResponse.headers['set-cookie'];

      const response = await request(app)
        .post('/api/auth/logout')
        .set('Cookie', cookies)
        .expect(200);

      const setCookies = response.headers['set-cookie'];
      expect(setCookies).toBeDefined();
    });

    it('should succeed even with invalid token', async () => {
      const response = await request(app)
        .post('/api/auth/logout')
        .send({ refreshToken: 'invalid-token' })
        .expect(200);

      expect(response.body.message).toBeDefined();
    });

    it('should invalidate refresh token', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      const registerResponse = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const refreshToken = registerResponse.body.refreshToken;

      // Logout
      await request(app)
        .post('/api/auth/logout')
        .send({ refreshToken })
        .expect(200);

      // Try to use refresh token after logout
      const response = await request(app)
        .post('/api/auth/refresh')
        .send({ refreshToken })
        .expect(401);

      expect(response.body.error).toBeDefined();
    });
  });

  describe('GET /api/auth/me', () => {
    it('should return current user with valid token', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
        displayName: 'Test User',
      };

      const registerResponse = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const accessToken = registerResponse.body.accessToken;

      const response = await request(app)
        .get('/api/auth/me')
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      expect(response.body.user).toBeDefined();
      expect(response.body.user.email).toBe(userData.email.toLowerCase());
      expect(response.body.user.display_name).toBe(userData.displayName);
    });

    it('should reject missing token', async () => {
      const response = await request(app)
        .get('/api/auth/me')
        .expect(401);

      expect(response.body.error).toContain('token');
    });

    it('should reject invalid token', async () => {
      const response = await request(app)
        .get('/api/auth/me')
        .set('Authorization', 'Bearer invalid-token')
        .expect(401);

      expect(response.body.error).toBeDefined();
    });
  });

  describe('PUT /api/auth/profile', () => {
    it('should update display name', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      const registerResponse = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const accessToken = registerResponse.body.accessToken;

      const response = await request(app)
        .put('/api/auth/profile')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({ displayName: 'Updated Name' })
        .expect(200);

      expect(response.body.user.display_name).toBe('Updated Name');
    });

    it('should update photo URL', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      const registerResponse = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const accessToken = registerResponse.body.accessToken;

      const response = await request(app)
        .put('/api/auth/profile')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({ photoUrl: 'https://example.com/photo.jpg' })
        .expect(200);

      expect(response.body.user.photo_url).toBe('https://example.com/photo.jpg');
    });

    it('should reject invalid photo URL', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      const registerResponse = await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const accessToken = registerResponse.body.accessToken;

      const response = await request(app)
        .put('/api/auth/profile')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({ photoUrl: 'not-a-url' })
        .expect(400);

      expect(response.body.error).toContain('URL');
    });

    it('should reject unauthenticated request', async () => {
      const response = await request(app)
        .put('/api/auth/profile')
        .send({ displayName: 'Test' })
        .expect(401);

      expect(response.body.error).toBeDefined();
    });
  });

  describe('POST /api/auth/password-reset/request', () => {
    it('should accept password reset request', async () => {
      const userData = {
        email: randomEmail(),
        password: 'ValidPassword123!',
      };

      await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const response = await request(app)
        .post('/api/auth/password-reset/request')
        .send({ email: userData.email })
        .expect(200);

      expect(response.body.message).toBeDefined();
    });

    it('should not reveal if email exists', async () => {
      const response = await request(app)
        .post('/api/auth/password-reset/request')
        .send({ email: 'nonexistent@example.com' })
        .expect(200);

      expect(response.body.message).toBeDefined();
    });

    it('should reject invalid email format', async () => {
      const response = await request(app)
        .post('/api/auth/password-reset/request')
        .send({ email: 'invalid-email' })
        .expect(400);

      expect(response.body.error).toBeDefined();
    });
  });

  describe('POST /api/auth/password-reset/confirm', () => {
    it('should reset password with valid token', async () => {
      const userData = {
        email: randomEmail(),
        password: 'OldPassword123!',
      };

      await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const resetToken = await authService.requestPasswordReset(userData.email);

      const response = await request(app)
        .post('/api/auth/password-reset/confirm')
        .send({
          token: resetToken,
          newPassword: 'NewPassword123!',
        })
        .expect(200);

      expect(response.body.message).toContain('success');

      // Verify can login with new password
      const loginResponse = await request(app)
        .post('/api/auth/login')
        .send({
          email: userData.email,
          password: 'NewPassword123!',
        })
        .expect(200);

      expect(loginResponse.body.accessToken).toBeDefined();
    });

    it('should reject invalid reset token', async () => {
      const response = await request(app)
        .post('/api/auth/password-reset/confirm')
        .send({
          token: 'invalid-token',
          newPassword: 'NewPassword123!',
        })
        .expect(400);

      expect(response.body.error).toBeDefined();
    });

    it('should reject weak new password', async () => {
      const userData = {
        email: randomEmail(),
        password: 'OldPassword123!',
      };

      await request(app)
        .post('/api/auth/register')
        .send(userData)
        .expect(201);

      const resetToken = await authService.requestPasswordReset(userData.email);

      const response = await request(app)
        .post('/api/auth/password-reset/confirm')
        .send({
          token: resetToken,
          newPassword: 'weak',
        })
        .expect(400);

      expect(response.body.error).toBeDefined();
    });
  });
});
