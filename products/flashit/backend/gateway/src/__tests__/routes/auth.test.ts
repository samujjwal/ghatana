
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { buildServer } from '../../server';
import { prisma } from '../../lib/prisma';
import bcrypt from 'bcrypt';

// Mock dependencies
vi.mock('../../lib/prisma', () => ({
  prisma: {
    user: {
      findUnique: vi.fn(),
      create: vi.fn(),
    },
  },
  disconnectPrisma: vi.fn(),
}));

vi.mock('bcrypt', () => ({
  default: {
    hash: vi.fn(),
    compare: vi.fn(),
  },
}));

vi.mock('../../lib/email', () => ({
    sendEmail: vi.fn().mockResolvedValue(true)
}));

describe('Auth Routes', () => {
    let app: any;

    beforeEach(async () => {
        vi.clearAllMocks();
        app = buildServer();
    });

    afterEach(() => {
        app.close();
    });

    describe('POST /auth/register', () => {
        const validUser = {
            email: 'test@example.com',
            password: 'Password123!',
            name: 'Test User'
        };

        it('should register a new user successfully', async () => {
            // Setup
            (prisma.user.findUnique as any).mockResolvedValue(null);
            (prisma.user.create as any).mockResolvedValue({
                id: 'user-123',
                email: validUser.email,
                name: validUser.name
            });
            (bcrypt.hash as any).mockResolvedValue('hashed_password');

            // Execute
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: validUser
            });

            // Assert
            expect(response.statusCode).toBe(201);
            expect(JSON.parse(response.body)).toMatchObject({
                user: {
                    email: validUser.email,
                    name: validUser.name
                }
            });
        });

        it('should fail with weak password', async () => {
            // Execute
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: {
                    ...validUser,
                    password: 'weak'
                }
            });

            // Assert
            expect(response.statusCode).toBe(400);
        });

        it('should fail if email already exists', async () => {
            // Setup
            (prisma.user.findUnique as any).mockResolvedValue({ id: 'existing' });

            // Execute
            const response = await app.inject({
                method: 'POST',
                url: '/auth/register',
                payload: validUser
            });

            // Assert
            expect(response.statusCode).toBe(409);
        });
    });

    describe('POST /auth/login', () => {
        const credentials = {
            email: 'test@example.com',
            password: 'Password123!'
        };

        it('should login successfully with correct credentials', async () => {
            // Setup
            (prisma.user.findUnique as any).mockResolvedValue({
                id: 'user-123',
                email: credentials.email,
                passwordHash: 'hashed_password',
                name: 'Test User'
            });
            (bcrypt.compare as any).mockResolvedValue(true);

            // Execute
            const response = await app.inject({
                method: 'POST',
                url: '/auth/login',
                payload: credentials
            });

            // Assert
            expect(response.statusCode).toBe(200);
            expect(JSON.parse(response.body)).toHaveProperty('token');
        });

        it('should reject invalid password', async () => {
            // Setup
            (prisma.user.findUnique as any).mockResolvedValue({
                id: 'user-123',
                email: credentials.email,
                passwordHash: 'hashed_password'
            });
            (bcrypt.compare as any).mockResolvedValue(false);

            // Execute
            const response = await app.inject({
                method: 'POST',
                url: '/auth/login',
                payload: credentials
            });

            // Assert
            expect(response.statusCode).toBe(401);
        });
    });
});
