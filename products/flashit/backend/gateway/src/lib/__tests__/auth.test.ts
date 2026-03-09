/**
 * Authentication Tests
 * Tests for password hashing, JWT operations, and token validation
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { hashPassword, comparePassword, generateToken, verifyToken } from '../auth';
import type { JwtPayload } from '../auth';

describe('Authentication', () => {
    describe('Password Hashing', () => {
        it('should hash a password', async () => {
            const password = 'testPassword123';
            const hashed = await hashPassword(password);

            expect(hashed).toBeDefined();
            expect(hashed).not.toBe(password);
            expect(hashed.length).toBeGreaterThan(0);
        });

        it('should generate different hashes for same password', async () => {
            const password = 'testPassword123';
            const hash1 = await hashPassword(password);
            const hash2 = await hashPassword(password);

            expect(hash1).not.toBe(hash2);
        });

        it('should handle empty password', async () => {
            const password = '';
            const hashed = await hashPassword(password);

            expect(hashed).toBeDefined();
        });

        it('should handle long passwords', async () => {
            const password = 'a'.repeat(1000);
            const hashed = await hashPassword(password);

            expect(hashed).toBeDefined();
            expect(hashed.length).toBeGreaterThan(0);
        });

        it('should handle special characters', async () => {
            const password = '!@#$%^&*()_+-=[]{}|;:,.<>?';
            const hashed = await hashPassword(password);

            expect(hashed).toBeDefined();
        });
    });

    describe('Password Comparison', () => {
        it('should return true for correct password', async () => {
            const password = 'testPassword123';
            const hashed = await hashPassword(password);
            const isValid = await comparePassword(password, hashed);

            expect(isValid).toBe(true);
        });

        it('should return false for incorrect password', async () => {
            const password = 'testPassword123';
            const wrongPassword = 'wrongPassword456';
            const hashed = await hashPassword(password);
            const isValid = await comparePassword(wrongPassword, hashed);

            expect(isValid).toBe(false);
        });

        it('should return false for empty password', async () => {
            const password = 'testPassword123';
            const hashed = await hashPassword(password);
            const isValid = await comparePassword('', hashed);

            expect(isValid).toBe(false);
        });

        it('should be case sensitive', async () => {
            const password = 'TestPassword123';
            const hashed = await hashPassword(password);
            const isValid = await comparePassword('testpassword123', hashed);

            expect(isValid).toBe(false);
        });

        it('should handle whitespace differences', async () => {
            const password = 'testPassword123';
            const hashed = await hashPassword(password);
            const isValid = await comparePassword(' testPassword123 ', hashed);

            expect(isValid).toBe(false);
        });
    });

    describe('JWT Token Generation', () => {
        const mockPayload: JwtPayload = {
            userId: 'user-123',
            email: 'test@example.com',
        };

        it('should generate a valid JWT token', () => {
            const token = generateToken(mockPayload);

            expect(token).toBeDefined();
            expect(typeof token).toBe('string');
            expect(token.split('.').length).toBe(3); // JWT has 3 parts
        });

        it('should generate different tokens for same payload at different times', async () => {
            const token1 = generateToken(mockPayload);
            // Wait 1 second to ensure different iat timestamp (JWT uses seconds)
            await new Promise(resolve => setTimeout(resolve, 1100));
            const token2 = generateToken(mockPayload);

            // Tokens should be different due to iat (issued at) timestamp
            expect(token1).not.toBe(token2);
        });

        it('should include userId in token', () => {
            const token = generateToken(mockPayload);
            const decoded = verifyToken(token);

            expect(decoded).toBeDefined();
            expect(decoded?.userId).toBe(mockPayload.userId);
        });

        it('should include email in token', () => {
            const token = generateToken(mockPayload);
            const decoded = verifyToken(token);

            expect(decoded).toBeDefined();
            expect(decoded?.email).toBe(mockPayload.email);
        });

        it('should handle minimal payload', () => {
            const minimalPayload: JwtPayload = {
                userId: 'user-123',
                email: 'test@example.com',
            };
            const token = generateToken(minimalPayload);

            expect(token).toBeDefined();
        });
    });

    describe('JWT Token Verification', () => {
        const mockPayload: JwtPayload = {
            userId: 'user-123',
            email: 'test@example.com',
        };

        it('should verify a valid token', () => {
            const token = generateToken(mockPayload);
            const decoded = verifyToken(token);

            expect(decoded).toBeDefined();
            expect(decoded?.userId).toBe(mockPayload.userId);
            expect(decoded?.email).toBe(mockPayload.email);
        });

        it('should return null for invalid token', () => {
            const invalidToken = 'invalid.token.here';
            const decoded = verifyToken(invalidToken);

            expect(decoded).toBeNull();
        });

        it('should return null for empty token', () => {
            const decoded = verifyToken('');

            expect(decoded).toBeNull();
        });

        it('should return null for malformed token', () => {
            const malformedToken = 'not-a-jwt-token';
            const decoded = verifyToken(malformedToken);

            expect(decoded).toBeNull();
        });

        it('should return null for tampered token', () => {
            const token = generateToken(mockPayload);
            const parts = token.split('.');
            const tamperedToken = `${parts[0]}.${parts[1]}.tampered`;
            const decoded = verifyToken(tamperedToken);

            expect(decoded).toBeNull();
        });

        it('should include standard JWT claims', () => {
            const token = generateToken(mockPayload);
            const decoded = verifyToken(token);

            expect(decoded).toBeDefined();
            expect(decoded?.iat).toBeDefined(); // issued at
            expect(decoded?.exp).toBeDefined(); // expiration
        });

        it('should verify token is not expired', () => {
            const token = generateToken(mockPayload);
            const decoded = verifyToken(token);

            expect(decoded).toBeDefined();
            if (decoded?.exp) {
                const now = Math.floor(Date.now() / 1000);
                expect(decoded.exp).toBeGreaterThan(now);
            }
        });
    });

    describe('Token Expiration', () => {
        it('should set expiration time', () => {
            const mockPayload: JwtPayload = {
                userId: 'user-123',
                email: 'test@example.com',
            };
            const token = generateToken(mockPayload);
            const decoded = verifyToken(token);

            expect(decoded?.exp).toBeDefined();
            expect(decoded?.iat).toBeDefined();

            if (decoded?.exp && decoded?.iat) {
                const duration = decoded.exp - decoded.iat;
                // Default expiration should be reasonable (e.g., 7 days = 604800 seconds)
                expect(duration).toBeGreaterThan(0);
                expect(duration).toBeLessThanOrEqual(604800); // 7 days
            }
        });
    });

    describe('Edge Cases', () => {
        it('should handle very long userId', () => {
            const longUserId = 'a'.repeat(1000);
            const payload: JwtPayload = {
                userId: longUserId,
                email: 'test@example.com',
            };
            const token = generateToken(payload);
            const decoded = verifyToken(token);

            expect(decoded?.userId).toBe(longUserId);
        });

        it('should handle special characters in email', () => {
            const payload: JwtPayload = {
                userId: 'user-123',
                email: 'test+tag@example.co.uk',
            };
            const token = generateToken(payload);
            const decoded = verifyToken(token);

            expect(decoded?.email).toBe(payload.email);
        });

        it('should handle unicode characters', () => {
            const payload: JwtPayload = {
                userId: 'user-123',
                email: 'test@例え.jp',
            };
            const token = generateToken(payload);
            const decoded = verifyToken(token);

            expect(decoded?.email).toBe(payload.email);
        });
    });

    describe('Security', () => {
        it('should not include password in token', () => {
            const payload: JwtPayload = {
                userId: 'user-123',
                email: 'test@example.com',
            };
            const token = generateToken(payload);

            // Decode without verification to check payload
            const parts = token.split('.');
            const payloadPart = Buffer.from(parts[1], 'base64').toString();

            expect(payloadPart).not.toContain('password');
            expect(payloadPart).not.toContain('hash');
        });

        it('should generate cryptographically secure hashes', async () => {
            const password = 'testPassword123';
            const hash1 = await hashPassword(password);
            const hash2 = await hashPassword(password);

            // Hashes should be different (salted)
            expect(hash1).not.toBe(hash2);

            // Both should verify correctly
            expect(await comparePassword(password, hash1)).toBe(true);
            expect(await comparePassword(password, hash2)).toBe(true);
        });
    });
});
