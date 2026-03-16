/**
 * Auth Service Tests
 *
 * Tests the remaining auth service methods after platform migration:
 * - verifyAccessToken (legacy JWT fallback verification)
 * - getUserById (user profile lookup)
 * - updateProfile (user profile mutation)
 *
 * Registration, login, token generation, password reset and logout are
 * now handled exclusively by the Ghatana auth-gateway service.
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import jwt from 'jsonwebtoken';
import * as authService from '../../services/auth.service';
import { query } from '../../db';
import { randomEmail } from '../setup';

const JWT_SECRET = process.env.JWT_SECRET ?? 'development-secret-key';

describe('AuthService', () => {
  describe('verifyAccessToken', () => {
    it('should return userId for a valid access token', () => {
      const userId = 'test-user-id';
      const token = jwt.sign({ userId, type: 'access' }, JWT_SECRET, { expiresIn: '15m' });
      const result = authService.verifyAccessToken(token);
      expect(result).not.toBeNull();
      expect(result?.userId).toBe(userId);
    });

    it('should return null for a malformed token string', () => {
      expect(authService.verifyAccessToken('not-a-jwt')).toBeNull();
    });

    it('should return null for a refresh-type token', () => {
      const token = jwt.sign({ userId: 'test-id', type: 'refresh' }, JWT_SECRET, { expiresIn: '7d' });
      expect(authService.verifyAccessToken(token)).toBeNull();
    });

    it('should return null for an expired token', () => {
      const token = jwt.sign({ userId: 'test-id', type: 'access' }, JWT_SECRET, { expiresIn: 0 });
      expect(authService.verifyAccessToken(token)).toBeNull();
    });

    it('should return null for a token signed with the wrong secret', () => {
      const wrongSecret = 'wrong-secret';
      const token = jwt.sign({ userId: 'test-id', type: 'access' }, wrongSecret, { expiresIn: '15m' });
      expect(authService.verifyAccessToken(token)).toBeNull();
    });
  });

  describe('getUserById', () => {
    let testUserId: string;

    beforeAll(async () => {
      const result = await query<{ id: string }>(
        'INSERT INTO users (email, password_hash) VALUES ($1, $2) RETURNING id',
        [randomEmail(), 'placeholder_hash_for_testing_only']
      );
      testUserId = result[0].id;
    });

    afterAll(async () => {
      if (testUserId) {
        await query('DELETE FROM users WHERE id = $1', [testUserId]);
      }
    });

    it('should return the user for an existing ID', async () => {
      const user = await authService.getUserById(testUserId);
      expect(user).not.toBeNull();
      expect(user?.id).toBe(testUserId);
    });

    it('should return null for a non-existent user ID', async () => {
      const user = await authService.getUserById('00000000-0000-0000-0000-000000000000');
      expect(user).toBeNull();
    });
  });

  describe('updateProfile', () => {
    let testUserId: string;

    beforeAll(async () => {
      const result = await query<{ id: string }>(
        'INSERT INTO users (email, password_hash) VALUES ($1, $2) RETURNING id',
        [randomEmail(), 'placeholder_hash_for_testing_only']
      );
      testUserId = result[0].id;
    });

    afterAll(async () => {
      if (testUserId) {
        await query('DELETE FROM users WHERE id = $1', [testUserId]);
      }
    });

    it('should update the display name', async () => {
      const updated = await authService.updateProfile(testUserId, { displayName: 'Updated Name' });
      expect(updated.display_name).toBe('Updated Name');
    });

    it('should update the photo URL', async () => {
      const updated = await authService.updateProfile(testUserId, {
        photoUrl: 'https://example.com/photo.jpg',
      });
      expect(updated.photo_url).toBe('https://example.com/photo.jpg');
    });

    it('should update both fields together', async () => {
      const updated = await authService.updateProfile(testUserId, {
        displayName: 'Combined Update',
        photoUrl: 'https://example.com/combined.jpg',
      });
      expect(updated.display_name).toBe('Combined Update');
      expect(updated.photo_url).toBe('https://example.com/combined.jpg');
    });

    it('should throw when no update fields are provided', async () => {
      await expect(authService.updateProfile(testUserId, {})).rejects.toThrow('No fields to update');
    });

    it('should throw when updating a non-existent user', async () => {
      await expect(
        authService.updateProfile('00000000-0000-0000-0000-000000000000', { displayName: 'Ghost' })
      ).rejects.toThrow('User not found');
    });
  });
});
