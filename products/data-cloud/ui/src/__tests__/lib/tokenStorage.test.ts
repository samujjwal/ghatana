/**
 * Tests for TokenStorage
 *
 * Covers: FINDING-DC-UI-H1 (secure token storage)
 *
 * @doc.type test
 * @doc.purpose Unit tests for TokenStorage
 * @doc.layer frontend
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TokenStorage } from '../../lib/auth/tokenStorage';

// Helper to advance simulated time
const NOW = Date.now();

describe('TokenStorage', () => {
  beforeEach(() => {
    TokenStorage.clear();
    vi.restoreAllMocks();
  });

  describe('set / get', () => {
    it('stores and retrieves a token', () => {
      TokenStorage.set('my-token');
      expect(TokenStorage.get()).toBe('my-token');
    });

    it('returns null when no token has been set', () => {
      expect(TokenStorage.get()).toBeNull();
    });

    it('returns null after clear()', () => {
      TokenStorage.set('my-token');
      TokenStorage.clear();
      expect(TokenStorage.get()).toBeNull();
    });
  });

  describe('expiry', () => {
    it('returns token when within expiry', () => {
      vi.spyOn(Date, 'now').mockReturnValue(NOW);
      TokenStorage.set('valid-token', 3600); // 1 hour
      // Travel 30 minutes
      vi.spyOn(Date, 'now').mockReturnValue(NOW + 30 * 60 * 1000);
      expect(TokenStorage.get()).toBe('valid-token');
    });

    it('returns null when token has expired', () => {
      vi.spyOn(Date, 'now').mockReturnValue(NOW);
      TokenStorage.set('expired-token', 60); // 1 minute
      // Travel 2 minutes
      vi.spyOn(Date, 'now').mockReturnValue(NOW + 2 * 60 * 1000);
      expect(TokenStorage.get()).toBeNull();
    });

    it('clears expired token from sessionStorage on access', () => {
      const removeSpy = vi.spyOn(Storage.prototype, 'removeItem');
      vi.spyOn(Date, 'now').mockReturnValue(NOW);
      TokenStorage.set('expiring', 10);
      vi.spyOn(Date, 'now').mockReturnValue(NOW + 20_000);
      TokenStorage.get();
      expect(removeSpy).toHaveBeenCalled();
    });

    it('expiresIn returns null when no expiry set', () => {
      TokenStorage.set('no-expiry');
      expect(TokenStorage.expiresIn()).toBeNull();
    });

    it('expiresIn returns positive ms when not expired', () => {
      vi.spyOn(Date, 'now').mockReturnValue(NOW);
      TokenStorage.set('token', 3600);
      vi.spyOn(Date, 'now').mockReturnValue(NOW + 1000);
      const remaining = TokenStorage.expiresIn();
      expect(remaining).toBeGreaterThan(0);
    });
  });

  describe('isAuthenticated', () => {
    it('returns false when no token', () => {
      expect(TokenStorage.isAuthenticated()).toBe(false);
    });

    it('returns true when valid token exists', () => {
      TokenStorage.set('valid-token');
      expect(TokenStorage.isAuthenticated()).toBe(true);
    });
  });

  describe('sessionStorage resilience', () => {
    it('works when sessionStorage throws (e.g. private browsing)', () => {
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('QuotaExceededError');
      });
      expect(() => TokenStorage.set('token')).not.toThrow();
      // Memory cache still available
      expect(TokenStorage.get()).toBe('token');
    });
  });
});
