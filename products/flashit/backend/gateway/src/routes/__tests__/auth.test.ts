/**
 * Authentication Route Tests
 * 
 * Tests for user registration, login, and profile endpoints.
 * Covers all authentication flows including error cases, validation,
 * audit logging, and JWT token generation.
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import jwt from '@fastify/jwt';
import { registerAuthRoutes } from '../auth';
import { prisma } from '../../lib/prisma';
import { hashPassword } from '../../lib/auth';

describe('Auth Routes', () => {
    let app: FastifyInstance;
    const testEmail = `test-${Date.now()}@example.com`;
    const testPassword = 'SecurePassword123!';
    let testUserId: string;
    let authToken: string;

    beforeAll(async () => {
        // Create Fastify instance with JWT
        app = Fastify();
        await app.register(jwt, {
            secret: process.env.JWT_SECRET || 'test-secret-key-change-in-production',
        });

        // Register auth plugin
        app.decorate('authenticate', async (request: any, reply: any) => {
            try {
                await request.jwtVerify();
            } catch (err) {
                reply.code(401).send({ error: 'Unauthorized' });
            }
        });

        await registerAuthRoutes(app);
        await app.ready();
    });

    afterAll(async () => {
        // Cleanup test data
        if (testUserId) {
            await prisma.moment.deleteMany({ where: { userId: testUserId } });
            await prisma.sphereAccess.deleteMany({ where: { userId: testUserId } });
            await prisma.sphere.deleteMany({ where: { userId: testUserId } });
            await prisma.auditEvent.deleteMany({ where: { userId: testUserId } });
            await prisma.user.delete({ where: { id: testUserId } });
        }
        await app.close();
    });

    describe('POST /auth/register', () => {
        it('should register a new user successfully', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: {
                    email: testEmail,
                    password: testPassword,
                    displayName: 'Test User',
                },
            });

            expect(response.statusCode).toBe(201);
            const body = JSON.parse(response.body);
            expect(body).toHaveProperty('user');
            expect(body).toHaveProperty('token');
            expect(body.user.email).toBe(testEmail);
            expect(body.user.displayName).toBe('Test User');
            expect(body.user.id).toBeDefined();

            // Save for cleanup
            testUserId = body.user.id;
            authToken = body.token;

            // Verify user was created in DB
            const user = await prisma.user.findUnique({
                where: { email: testEmail },
            });
            expect(user).toBeDefined();
            expect(user?.email).toBe(testEmail);
        });

        it('should create default Personal sphere on registration', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: {
                    email: `sphere-test-${Date.now()}@example.com`,
                    password: testPassword,
                },
            });

            expect(response.statusCode).toBe(201);
            const body = JSON.parse(response.body);
            const userId = body.user.id;

            // Check Personal sphere was created
            const sphere = await prisma.sphere.findFirst({
                where: {
                    userId,
                    name: 'Personal',
                    type: 'PERSONAL',
                },
                include: {
                    sphereAccess: true,
                },
            });

            expect(sphere).toBeDefined();
            expect(sphere?.visibility).toBe('PRIVATE');
            expect(sphere?.sphereAccess).toHaveLength(1);
            expect(sphere?.sphereAccess[0].role).toBe('OWNER');

            // Cleanup
            await prisma.sphereAccess.deleteMany({ where: { userId } });
            await prisma.sphere.deleteMany({ where: { userId } });
            await prisma.auditEvent.deleteMany({ where: { userId } });
            await prisma.user.delete({ where: { id: userId } });
        });

        it('should create audit event on registration', async () => {
            const email = `audit-test-${Date.now()}@example.com`;
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: {
                    email,
                    password: testPassword,
                },
            });

            expect(response.statusCode).toBe(201);
            const body = JSON.parse(response.body);
            const userId = body.user.id;

            // Check audit event was created
            const auditEvent = await prisma.auditEvent.findFirst({
                where: {
                    userId,
                    eventType: 'USER_REGISTERED',
                    action: 'REGISTER',
                },
            });

            expect(auditEvent).toBeDefined();
            expect(auditEvent?.resourceType).toBe('USER');
            expect(auditEvent?.resourceId).toBe(userId);

            // Cleanup
            await prisma.moment.deleteMany({ where: { userId } });
            await prisma.sphereAccess.deleteMany({ where: { userId } });
            await prisma.sphere.deleteMany({ where: { userId } });
            await prisma.auditEvent.deleteMany({ where: { userId } });
            await prisma.user.delete({ where: { id: userId } });
        });

        it('should reject duplicate email registration', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: {
                    email: testEmail,
                    password: testPassword,
                },
            });

            expect(response.statusCode).toBe(409);
            const body = JSON.parse(response.body);
            expect(body.error).toBe('User already exists');
        });

        it('should reject invalid email format', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: {
                    email: 'not-an-email',
                    password: testPassword,
                },
            });

            expect(response.statusCode).toBe(400);
        });

        it('should reject short password', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: {
                    email: `new-${Date.now()}@example.com`,
                    password: 'short',
                },
            });

            expect(response.statusCode).toBe(400);
        });

        it('should reject password over 100 characters', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: {
                    email: `new-${Date.now()}@example.com`,
                    password: 'a'.repeat(101),
                },
            });

            expect(response.statusCode).toBe(400);
        });

        it('should accept registration without displayName', async () => {
            const email = `no-name-${Date.now()}@example.com`;
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: {
                    email,
                    password: testPassword,
                },
            });

            expect(response.statusCode).toBe(201);
            const body = JSON.parse(response.body);
            expect(body.user.displayName).toBeNull();

            // Cleanup
            const userId = body.user.id;
            await prisma.sphereAccess.deleteMany({ where: { userId } });
            await prisma.sphere.deleteMany({ where: { userId } });
            await prisma.auditEvent.deleteMany({ where: { userId } });
            await prisma.user.delete({ where: { id: userId } });
        });

        it('should hash the password in database', async () => {
            const email = `hash-test-${Date.now()}@example.com`;
            const password = 'TestPassword123!';
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: { email, password },
            });

            expect(response.statusCode).toBe(201);
            const body = JSON.parse(response.body);
            const userId = body.user.id;

            // Check password is hashed in DB
            const user = await prisma.user.findUnique({
                where: { id: userId },
            });

            expect(user?.passwordHash).not.toBe(password);
            expect(user?.passwordHash).toContain('$2b$');

            // Cleanup
            await prisma.sphereAccess.deleteMany({ where: { userId } });
            await prisma.sphere.deleteMany({ where: { userId } });
            await prisma.auditEvent.deleteMany({ where: { userId } });
            await prisma.user.delete({ where: { id: userId } });
        });

        it('should generate valid JWT token', async () => {
            const email = `jwt-test-${Date.now()}@example.com`;
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: {
                    email,
                    password: testPassword,
                },
            });

            expect(response.statusCode).toBe(201);
            const body = JSON.parse(response.body);
            const token = body.token;
            const userId = body.user.id;

            // Verify token
            const decoded = app.jwt.verify(token);
            expect(decoded).toHaveProperty('userId');
            expect(decoded).toHaveProperty('email');
            expect((decoded as any).email).toBe(email);

            // Cleanup
            await prisma.sphereAccess.deleteMany({ where: { userId } });
            await prisma.sphere.deleteMany({ where: { userId } });
            await prisma.auditEvent.deleteMany({ where: { userId } });
            await prisma.user.delete({ where: { id: userId } });
        });
    });

    describe('POST /auth/login', () => {
        it('should login user with valid credentials', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/auth/login',
                payload: {
                    email: testEmail,
                    password: testPassword,
                },
            });

            expect(response.statusCode).toBe(200);
            const body = JSON.parse(response.body);
            expect(body).toHaveProperty('user');
            expect(body).toHaveProperty('token');
            expect(body.user.email).toBe(testEmail);
            expect(body.token).toBeDefined();
        });

        it('should create audit event on login', async () => {
            const beforeLogin = new Date();
            
            const response = await app.inject({
                method: 'POST',
                url: '/auth/login',
                payload: {
                    email: testEmail,
                    password: testPassword,
                },
            });

            expect(response.statusCode).toBe(200);

            // Check audit event
            const auditEvent = await prisma.auditEvent.findFirst({
                where: {
                    userId: testUserId,
                    eventType: 'USER_LOGIN',
                    action: 'LOGIN',
                    createdAt: { gte: beforeLogin },
                },
            });

            expect(auditEvent).toBeDefined();
            expect(auditEvent?.resourceType).toBe('USER');
        });

        it('should reject login with wrong password', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/auth/login',
                payload: {
                    email: testEmail,
                    password: 'WrongPassword123!',
                },
            });

            expect(response.statusCode).toBe(401);
            const body = JSON.parse(response.body);
            expect(body.error).toBe('Invalid credentials');
        });

        it('should reject login with non-existent email', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/auth/login',
                payload: {
                    email: 'nonexistent@example.com',
                    password: testPassword,
                },
            });

            expect(response.statusCode).toBe(401);
            const body = JSON.parse(response.body);
            expect(body.error).toBe('Invalid credentials');
        });

        it('should reject login with invalid email format', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/auth/login',
                payload: {
                    email: 'not-an-email',
                    password: testPassword,
                },
            });

            expect(response.statusCode).toBe(400);
        });

        it('should reject login for deleted users', async () => {
            // Create and then soft-delete a user
            const email = `deleted-${Date.now()}@example.com`;
            const registerResponse = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: {
                    email,
                    password: testPassword,
                },
            });

            const userId = JSON.parse(registerResponse.body).user.id;

            // Soft delete the user
            await prisma.user.update({
                where: { id: userId },
                data: { deletedAt: new Date() },
            });

            // Attempt login
            const loginResponse = await app.inject({
                method: 'POST',
                url: '/auth/login',
                payload: {
                    email,
                    password: testPassword,
                },
            });

            expect(loginResponse.statusCode).toBe(401);
            const body = JSON.parse(loginResponse.body);
            expect(body.error).toBe('Invalid credentials');

            // Cleanup
            await prisma.sphereAccess.deleteMany({ where: { userId } });
            await prisma.sphere.deleteMany({ where: { userId } });
            await prisma.auditEvent.deleteMany({ where: { userId } });
            await prisma.user.delete({ where: { id: userId } });
        });

        it('should generate valid JWT token on login', async () => {
            const response = await app.inject({
                method: 'POST',
                url: '/auth/login',
                payload: {
                    email: testEmail,
                    password: testPassword,
                },
            });

            expect(response.statusCode).toBe(200);
            const body = JSON.parse(response.body);
            const token = body.token;

            // Verify token
            const decoded = app.jwt.verify(token);
            expect(decoded).toHaveProperty('userId');
            expect(decoded).toHaveProperty('email');
            expect((decoded as any).email).toBe(testEmail);
        });

        it('should log IP address and user agent', async () => {
            const beforeLogin = new Date();
            const response = await app.inject({
                method: 'POST',
                url: '/auth/login',
                payload: {
                    email: testEmail,
                    password: testPassword,
                },
                headers: {
                    'user-agent': 'Test-Agent/1.0',
                },
            });

            expect(response.statusCode).toBe(200);

            // Check audit event has IP and user agent
            const auditEvent = await prisma.auditEvent.findFirst({
                where: {
                    userId: testUserId,
                    eventType: 'USER_LOGIN',
                    createdAt: { gte: beforeLogin },
                },
            });

            expect(auditEvent).toBeDefined();
            expect(auditEvent?.userAgent).toBe('Test-Agent/1.0');
            expect(auditEvent?.ipAddress).toBeDefined();
        });
    });

    describe('GET /auth/me', () => {
        it('should return current user with valid token', async () => {
            const response = await app.inject({
                method: 'GET',
                url: '/auth/me',
                headers: {
                    authorization: `Bearer ${authToken}`,
                },
            });

            expect(response.statusCode).toBe(200);
            const body = JSON.parse(response.body);
            expect(body).toHaveProperty('user');
            expect(body.user.id).toBe(testUserId);
            expect(body.user.email).toBe(testEmail);
            expect(body.user.displayName).toBeDefined();
            expect(body.user.createdAt).toBeDefined();
            expect(body.user.updatedAt).toBeDefined();
        });

        it('should reject request without token', async () => {
            const response = await app.inject({
                method: 'GET',
                url: '/auth/me',
            });

            expect(response.statusCode).toBe(401);
        });

        it('should reject request with invalid token', async () => {
            const response = await app.inject({
                method: 'GET',
                url: '/auth/me',
                headers: {
                    authorization: 'Bearer invalid-token',
                },
            });

            expect(response.statusCode).toBe(401);
        });

        it('should reject request with expired token', async () => {
            // Create a token that expires immediately
            const expiredToken = app.jwt.sign(
                { userId: testUserId, email: testEmail },
                { expiresIn: '1ms' }
            );

            // Wait for it to expire
            await new Promise(resolve => setTimeout(resolve, 10));

            const response = await app.inject({
                method: 'GET',
                url: '/auth/me',
                headers: {
                    authorization: `Bearer ${expiredToken}`,
                },
            });

            expect(response.statusCode).toBe(401);
        });

        it('should return 404 if user no longer exists', async () => {
            // Create a token for a non-existent user
            const fakeToken = app.jwt.sign({
                userId: 'non-existent-user-id',
                email: 'fake@example.com',
            });

            const response = await app.inject({
                method: 'GET',
                url: '/auth/me',
                headers: {
                    authorization: `Bearer ${fakeToken}`,
                },
            });

            expect(response.statusCode).toBe(404);
            const body = JSON.parse(response.body);
            expect(body.error).toBe('User not found');
        });

        it('should not return password hash in response', async () => {
            const response = await app.inject({
                method: 'GET',
                url: '/auth/me',
                headers: {
                    authorization: `Bearer ${authToken}`,
                },
            });

            expect(response.statusCode).toBe(200);
            const body = JSON.parse(response.body);
            expect(body.user).not.toHaveProperty('passwordHash');
        });
    });
});
