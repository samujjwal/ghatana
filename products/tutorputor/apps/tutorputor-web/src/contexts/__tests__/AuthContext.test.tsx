/**
 * AuthContext Tests
 *
 * Tests for authentication context and hooks.
 *
 * @doc.type test
 * @doc.purpose Test authentication context functionality
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { AuthProvider, useAuth, useTenantId } from '../AuthContext';

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  describe('AuthProvider', () => {
    it('should initialize with no user when no token in localStorage', () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useAuth(), { wrapper });
      
      expect(result.current.user).toBeNull();
      expect(result.current.token).toBeNull();
      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.isLoading).toBe(false);
    });

    it('should parse valid JWT token from localStorage', async () => {
      const validToken = btoa(JSON.stringify({
        sub: 'user-123',
        email: 'test@example.com',
        name: 'Test User',
        role: 'student',
        tenantId: 'tenant-abc',
      })) + '.' + btoa('{}') + '.signature';
      
      localStorage.setItem('auth_token', validToken);
      
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useAuth(), { wrapper });
      
      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });
      
      expect(result.current.user).toEqual({
        id: 'user-123',
        email: 'test@example.com',
        displayName: 'Test User',
        role: 'student',
        tenantId: 'tenant-abc',
      });
      expect(result.current.isAuthenticated).toBe(true);
    });

    it('should clear invalid token from localStorage', async () => {
      localStorage.setItem('auth_token', 'invalid-token');
      
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useAuth(), { wrapper });
      
      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });
      
      expect(result.current.user).toBeNull();
      expect(result.current.isAuthenticated).toBe(false);
      expect(localStorage.getItem('auth_token')).toBeNull();
    });
  });

  describe('login', () => {
    it('should set user and token on successful login', async () => {
      const validToken = btoa(JSON.stringify({
        sub: 'user-123',
        email: 'test@example.com',
        name: 'Test User',
        role: 'student',
        tenantId: 'tenant-abc',
      })) + '.' + btoa('{}') + '.signature';
      
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useAuth(), { wrapper });
      
      await act(async () => {
        await result.current.login(validToken);
      });
      
      expect(result.current.user).toEqual({
        id: 'user-123',
        email: 'test@example.com',
        displayName: 'Test User',
        role: 'student',
        tenantId: 'tenant-abc',
      });
      expect(result.current.token).toBe(validToken);
      expect(result.current.isAuthenticated).toBe(true);
      expect(localStorage.getItem('auth_token')).toBe(validToken);
      expect(localStorage.getItem('tenant_id')).toBe('tenant-abc');
    });

    it('should throw error on invalid token', async () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useAuth(), { wrapper });
      
      await expect(async () => {
        await act(async () => {
          await result.current.login('invalid-token');
        });
      }).rejects.toThrow('Invalid token');
    });
  });

  describe('logout', () => {
    it('should clear user and token on logout', async () => {
      const validToken = btoa(JSON.stringify({
        sub: 'user-123',
        email: 'test@example.com',
        name: 'Test User',
        role: 'student',
        tenantId: 'tenant-abc',
      })) + '.' + btoa('{}') + '.signature';
      
      localStorage.setItem('auth_token', validToken);
      
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useAuth(), { wrapper });
      
      await waitFor(() => {
        expect(result.current.isAuthenticated).toBe(true);
      });
      
      act(() => {
        result.current.logout();
      });
      
      expect(result.current.user).toBeNull();
      expect(result.current.token).toBeNull();
      expect(result.current.isAuthenticated).toBe(false);
      expect(localStorage.getItem('auth_token')).toBeNull();
      expect(localStorage.getItem('tenant_id')).toBeNull();
    });
  });

  describe('useTenantId', () => {
    it('should return tenant ID when authenticated', async () => {
      const validToken = btoa(JSON.stringify({
        sub: 'user-123',
        email: 'test@example.com',
        name: 'Test User',
        role: 'student',
        tenantId: 'tenant-abc',
      })) + '.' + btoa('{}') + '.signature';
      
      localStorage.setItem('auth_token', validToken);
      
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      const { result } = renderHook(() => useTenantId(), { wrapper });
      
      await waitFor(() => {
        expect(result.current).toBe('tenant-abc');
      });
    });

    it('should throw error when not authenticated', () => {
      const wrapper = ({ children }: { children: React.ReactNode }) => (
        <AuthProvider>{children}</AuthProvider>
      );
      
      expect(() => {
        renderHook(() => useTenantId(), { wrapper });
      }).toThrow('Authentication required: No valid tenant context');
    });
  });
});
