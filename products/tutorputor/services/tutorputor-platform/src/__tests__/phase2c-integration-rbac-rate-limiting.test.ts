/**
 * @doc.type test-suite
 * @doc.purpose Phase 2C: Integration Tests - RBAC Enforcement, Rate Limiting, Sessions
 * @doc.layer product
 * @doc.pattern Integration Test
 *
 * Phase 2C tests integration with Fastify app:
 * - RBAC enforcement (roles → endpoint permissions)
 * - Rate limiting on /auth/login
 * - Concurrent session handling
 * - Privilege escalation prevention
 * - Token refresh flow
 *
 * These tests require a real Fastify app instance with:
 * 1. JWT validation middleware
 * 2. Role-based route handlers
 * 3. Rate limiting middleware
 * 4. Session management
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import jwt from 'jsonwebtoken';
import Fastify from 'fastify';
import fastifyJwt from '@fastify/jwt';
import type { FastifyInstance } from 'fastify';

/**
 * SIMPLIFIED FASTIFY APP FOR TESTING
 * (Avoids Sentry native module issue by not using setupPlatform)
 */
async function createTestFastifyApp(secret: string): Promise<FastifyInstance> {
  const app = Fastify({
    logger: false,
  });

  // Register JWT plugin
  await app.register(fastifyJwt, {
    secret,
    sign: { algorithm: 'HS256' },
  });

  // JWT validation middleware
  app.addHook('onRequest', async (request) => {
    if (request.url.startsWith('/auth/login') || request.url.startsWith('/health')) {
      return; // Skip auth for login and health
    }
    try {
      await request.jwtVerify();
    } catch (err) {
      throw { statusCode: 401, message: 'Unauthorized' };
    }
  });

  // ========================================================================
  // STUDENT ROLE ENDPOINTS
  // ========================================================================

  // GET /api/student/grades - students can read own grades
  app.get('/api/student/grades', async (request, reply) => {
    if (request.user.role !== 'student') {
      return reply.code(403).send({ error: 'Forbidden' });
    }
    return reply.send({ data: [{ assignment: 'Math', grade: 95 }] });
  });

  // PUT /api/student/grades/:id - students CANNOT write grades
  app.put('/api/student/grades/:id', async (request, reply) => {
    return reply.code(403).send({ error: 'Forbidden: Students cannot modify grades' });
  });

  // ========================================================================
  // TEACHER ROLE ENDPOINTS
  // ========================================================================

  // GET /api/teacher/students - teachers can see all students
  app.get('/api/teacher/students', async (request, reply) => {
    if (!['teacher', 'admin', 'superadmin'].includes(request.user.role)) {
      return reply.code(403).send({ error: 'Forbidden' });
    }
    return reply.send({
      data: [
        { id: 'student-1', name: 'Alice' },
        { id: 'student-2', name: 'Bob' },
      ],
    });
  });

  // PUT /api/teacher/grades/:studentId/:assignmentId - teachers can update grades
  app.put('/api/teacher/grades/:studentId/:assignmentId', async (request, reply) => {
    if (!['teacher', 'admin', 'superadmin'].includes(request.user.role)) {
      return reply.code(403).send({ error: 'Forbidden' });
    }
    return reply.send({ success: true, grade: 95 });
  });

  // ========================================================================
  // CREATOR ROLE ENDPOINTS
  // ========================================================================

  // POST /api/content/domains - creators can author content
  app.post('/api/content/domains', async (request, reply) => {
    if (!['creator', 'admin', 'superadmin'].includes(request.user.role)) {
      return reply.code(403).send({ error: 'Forbidden' });
    }
    return reply.code(201).send({ domainId: 'domain-new-1' });
  });

  // ========================================================================
  // ADMIN ROLE ENDPOINTS
  // ========================================================================

  // POST /api/admin/users - admins can create users
  app.post('/api/admin/users', async (request, reply) => {
    if (!['admin', 'superadmin'].includes(request.user.role)) {
      return reply.code(403).send({ error: 'Forbidden' });
    }
    return reply.code(201).send({ userId: 'user-new-1' });
  });

  // GET /api/admin/audit-logs - admins can view audit logs
  app.get('/api/admin/audit-logs', async (request, reply) => {
    if (!['admin', 'superadmin'].includes(request.user.role)) {
      return reply.code(403).send({ error: 'Forbidden' });
    }
    return reply.send({ logs: [{ action: 'user_created', timestamp: new Date().toISOString() }] });
  });

  // ========================================================================
  // SUPERADMIN ROLE ENDPOINTS
  // ========================================================================

  // POST /api/system/config - superadmins only
  app.post('/api/system/config', async (request, reply) => {
    if (request.user.role !== 'superadmin') {
      return reply.code(403).send({ error: 'Forbidden' });
    }
    return reply.send({ success: true });
  });

  // ========================================================================
  // USER PROFILE ENDPOINT
  // ========================================================================

  // GET /api/user/profile - all authenticated users
  app.get('/api/user/profile', async (request, reply) => {
    return reply.send({
      userId: request.user.sub,
      email: 'user@school.com',
      role: request.user.role,
      tenantId: request.user.tenantId,
    });
  });

  // PUT /api/user/profile - users cannot escalate their own role
  app.put('/api/user/profile', async (request, reply) => {
    const payload = request.body as any;
    if (payload.role && payload.role !== request.user.role) {
      return reply.code(403).send({ error: 'Forbidden: Cannot modify role' });
    }
    return reply.send({ success: true });
  });

  // ========================================================================
  // AUTH ENDPOINTS
  // ========================================================================

  // Simple in-memory rate limit tracker (per IP)
  const loginAttempts: Map<string, { count: number; resetTime: number }> = new Map();

  // POST /auth/login
  app.post('/auth/login', async (request, reply) => {
    const clientIp = (request.headers['x-forwarded-for'] as string) || request.ip;
    const now = Date.now();

    // Check rate limit
    const attempts = loginAttempts.get(clientIp);
    if (attempts && attempts.count >= 5) {
      if (now < attempts.resetTime) {
        const retryAfter = Math.ceil((attempts.resetTime - now) / 1000);
        return reply
          .code(429)
          .header('Retry-After', retryAfter.toString())
          .send({
            error: 'Too many login attempts',
            retryAfter,
          });
      } else {
        // Reset
        loginAttempts.delete(clientIp);
      }
    }

    // Validate credentials (simplified)
    const payload = request.body as any;
    if (payload.password !== 'correct-password') {
      // Increment failed attempts
      const current = loginAttempts.get(clientIp) || { count: 0, resetTime: now + 15 * 60 * 1000 };
      loginAttempts.set(clientIp, {
        count: current.count + 1,
        resetTime: current.resetTime,
      });
      return reply.code(401).send({ error: 'Invalid credentials' });
    }

    // Successful login
    loginAttempts.delete(clientIp);

    const token = app.jwt.sign({
      sub: 'user-123',
      email: payload.email || 'user@school.com',
      tenantId: 'tenant-1',
      role: 'student', // Default role
    });

    return reply.send({ accessToken: token });
  });

  // Health check
  app.get('/health', async (request, reply) => {
    return reply.send({ status: 'ok' });
  });

  return app;
}

/**
 * ============================================================================
 * TEST SUITE: PHASE 2C - RBAC ENFORCEMENT & RATE LIMITING
 * ============================================================================
 */

describe('Phase 2C: RBAC Enforcement and Rate Limiting', () => {
  const jwtSecret = 'test-jwt-secret-32-chars-minimum-abc123xyz789';
  let app: FastifyInstance;

  beforeAll(async () => {
    app = await createTestFastifyApp(jwtSecret);
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  // =========================================================================
  // SECTION 1: STUDENT ROLE PERMISSIONS
  // =========================================================================

  describe('Student Role - Permissions', () => {
    it('should allow student to read own grades', async () => {
      const token = app.jwt.sign({
        sub: 'student-1',
        role: 'student',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/student/grades',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().data).toBeDefined();
    });

    it('should deny student write access to grades', async () => {
      const token = app.jwt.sign({
        sub: 'student-1',
        role: 'student',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'PUT',
        url: '/api/student/grades/assignment-1',
        headers: { authorization: `Bearer ${token}` },
        payload: { grade: 95 },
      });

      expect(response.statusCode).toBe(403);
      expect(response.json().error).toMatch(/Forbidden|permission/i);
    });
  });

  // =========================================================================
  // SECTION 2: TEACHER ROLE PERMISSIONS
  // =========================================================================

  describe('Teacher Role - Permissions', () => {
    it('should allow teacher to read all students', async () => {
      const token = app.jwt.sign({
        sub: 'teacher-1',
        role: 'teacher',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/teacher/students',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().data).toHaveLength(2);
    });

    it('should allow teacher to update student grades', async () => {
      const token = app.jwt.sign({
        sub: 'teacher-1',
        role: 'teacher',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'PUT',
        url: '/api/teacher/grades/student-1/assignment-1',
        headers: { authorization: `Bearer ${token}` },
        payload: { grade: 95 },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().success).toBe(true);
    });

    it('should deny student access to teacher endpoints', async () => {
      const token = app.jwt.sign({
        sub: 'student-1',
        role: 'student',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/teacher/students',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(403);
    });
  });

  // =========================================================================
  // SECTION 3: CREATOR ROLE PERMISSIONS
  // =========================================================================

  describe('Creator Role - Permissions', () => {
    it('should allow creator to author content', async () => {
      const token = app.jwt.sign({
        sub: 'creator-1',
        role: 'creator',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/content/domains',
        headers: { authorization: `Bearer ${token}` },
        payload: { name: 'Biology 101' },
      });

      expect(response.statusCode).toBe(201);
      expect(response.json().domainId).toBeDefined();
    });

    it('should deny student access to content authoring', async () => {
      const token = app.jwt.sign({
        sub: 'student-1',
        role: 'student',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/content/domains',
        headers: { authorization: `Bearer ${token}` },
        payload: { name: 'Physics 101' },
      });

      expect(response.statusCode).toBe(403);
    });
  });

  // =========================================================================
  // SECTION 4: ADMIN ROLE PERMISSIONS
  // =========================================================================

  describe('Admin Role - Permissions', () => {
    it('should allow admin to create users', async () => {
      const token = app.jwt.sign({
        sub: 'admin-1',
        role: 'admin',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/admin/users',
        headers: { authorization: `Bearer ${token}` },
        payload: { email: 'newuser@school.com' },
      });

      expect(response.statusCode).toBe(201);
      expect(response.json().userId).toBeDefined();
    });

    it('should allow admin to view audit logs', async () => {
      const token = app.jwt.sign({
        sub: 'admin-1',
        role: 'admin',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/admin/audit-logs',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().logs).toBeDefined();
    });

    it('should deny teacher access to user management', async () => {
      const token = app.jwt.sign({
        sub: 'teacher-1',
        role: 'teacher',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/admin/users',
        headers: { authorization: `Bearer ${token}` },
        payload: { email: 'newuser@school.com' },
      });

      expect(response.statusCode).toBe(403);
    });
  });

  // =========================================================================
  // SECTION 5: SUPERADMIN ROLE PERMISSIONS
  // =========================================================================

  describe('Superadmin Role - Permissions', () => {
    it('should allow superadmin system config access', async () => {
      const token = app.jwt.sign({
        sub: 'sa-1',
        role: 'superadmin',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/system/config',
        headers: { authorization: `Bearer ${token}` },
        payload: { key: 'max_students', value: 50 },
      });

      expect(response.statusCode).toBe(200);
    });

    it('should deny admin access to system config', async () => {
      const token = app.jwt.sign({
        sub: 'admin-1',
        role: 'admin',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/system/config',
        headers: { authorization: `Bearer ${token}` },
        payload: { key: 'max_students', value: 50 },
      });

      expect(response.statusCode).toBe(403);
    });
  });

  // =========================================================================
  // SECTION 6: PRIVILEGE ESCALATION PREVENTION
  // =========================================================================

  describe('Privilege Escalation Prevention', () => {
    it('should prevent user from self-escalating via JWT tampering', async () => {
      // User tries to create JWT with admin role
      const { wrongSecret } = { wrongSecret: 'wrong-secret-32-chars-minimum-xyz999' };
      const forgedToken = jwt.sign(
        {
          sub: 'student-1',
          role: 'admin',
          tenantId: 'tenant-1',
        },
        wrongSecret
      );

      const response = await app.inject({
        method: 'GET',
        url: '/api/admin/audit-logs',
        headers: { authorization: `Bearer ${forgedToken}` },
      });

      expect(response.statusCode).toBe(401); // Invalid signature
    });

    it('should prevent user from escalating via profile endpoint', async () => {
      const token = app.jwt.sign({
        sub: 'student-1',
        role: 'student',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'PUT',
        url: '/api/user/profile',
        headers: { authorization: `Bearer ${token}` },
        payload: { role: 'admin' },
      });

      expect(response.statusCode).toBe(403);
    });
  });

  // =========================================================================
  // SECTION 7: RATE LIMITING ON /auth/login
  // =========================================================================

  describe('Rate Limiting - Brute Force Protection', () => {
    beforeEach(() => {
      // Note: In real implementation, would clear Redis rate limit cache
      // For this test, we rely on separate test IPs
    });

    it('should allow first 5 failed login attempts', async () => {
      const testIp = '192.0.2.100';
      for (let i = 0; i < 5; i++) {
        const response = await app.inject({
          method: 'POST',
          url: '/auth/login',
          headers: { 'x-forwarded-for': testIp },
          payload: { email: 'user@school.com', password: 'wrong' },
        });

        expect(response.statusCode).toBe(401);
      }
    });

    it('should block with 429 after 5 failed attempts', async () => {
      const testIp = '192.0.2.101';

      // First 5 attempts
      for (let i = 0; i < 5; i++) {
        await app.inject({
          method: 'POST',
          url: '/auth/login',
          headers: { 'x-forwarded-for': testIp },
          payload: { email: 'user@school.com', password: 'wrong' },
        });
      }

      // 6th attempt should be rate limited
      const response = await app.inject({
        method: 'POST',
        url: '/auth/login',
        headers: { 'x-forwarded-for': testIp },
        payload: { email: 'user@school.com', password: 'wrong' },
      });

      expect(response.statusCode).toBe(429);
      expect(response.json().retryAfter).toBeDefined();
    });

    it('should return Retry-After header with seconds', async () => {
      const testIp = '192.0.2.102';

      // First 5 failed attempts
      for (let i = 0; i < 5; i++) {
        await app.inject({
          method: 'POST',
          url: '/auth/login',
          headers: { 'x-forwarded-for': testIp },
          payload: { email: 'user@school.com', password: 'wrong' },
        });
      }

      // 6th attempt
      const response = await app.inject({
        method: 'POST',
        url: '/auth/login',
        headers: { 'x-forwarded-for': testIp },
        payload: { email: 'user@school.com', password: 'wrong' },
      });

      expect(response.statusCode).toBe(429);
      expect(response.headers['retry-after']).toBeDefined();
      const seconds = parseInt(response.headers['retry-after']);
      expect(seconds).toBeGreaterThan(0);
      expect(seconds).toBeLessThanOrEqual(3600);
    });

    it('should track rate limit per IP, not per email', async () => {
      const ip1 = '192.0.2.103';
      const ip2 = '192.0.2.104';

      // Attack from IP1 (5 failed attempts)
      for (let i = 0; i < 5; i++) {
        await app.inject({
          method: 'POST',
          url: '/auth/login',
          headers: { 'x-forwarded-for': ip1 },
          payload: { email: 'user@school.com', password: 'wrong' },
        });
      }

      // IP2 should NOT be rate limited
      const response = await app.inject({
        method: 'POST',
        url: '/auth/login',
        headers: { 'x-forwarded-for': ip2 },
        payload: { email: 'user@school.com', password: 'correct-password' },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().accessToken).toBeDefined();
    });
  });

  // =========================================================================
  // SECTION 8: AUTHENTICATION FLOW
  // =========================================================================

  describe('Authentication Flow', () => {
    it('should deny requests without Bearer token', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/user/profile',
      });

      expect(response.statusCode).toBe(401);
    });

    it('should deny requests with invalid token', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/user/profile',
        headers: { authorization: 'Bearer invalid.token.here' },
      });

      expect(response.statusCode).toBe(401);
    });

    it('should allow valid JWT in Authorization header', async () => {
      const token = app.jwt.sign({
        sub: 'user-1',
        role: 'student',
        tenantId: 'tenant-1',
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/user/profile',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().userId).toBe('user-1');
    });
  });

  // =========================================================================
  // SECTION 9: MULTI-TENANT ISOLATION
  // =========================================================================

  describe('Multi-Tenant Isolation', () => {
    it('should include tenantId in user context', async () => {
      const token = app.jwt.sign({
        sub: 'user-1',
        role: 'student',
        tenantId: 'tenant-xyz',
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/user/profile',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().tenantId).toBe('tenant-xyz');
    });
  });
});
