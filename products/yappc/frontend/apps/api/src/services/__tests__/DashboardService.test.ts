/**
 * @fileoverview DashboardService tests
 *
 * Tests for the Node-to-Java dashboard proxy gateway.
 * Verifies that the service correctly delegates to the Java backend
 * and handles error cases appropriately.
 *
 * @doc.type test
 * @doc.purpose Test DashboardService Node-to-Java proxy
 * @doc.layer product
 * @doc.pattern Integration Test
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { DashboardService } from '../DashboardService';
import type { Dashboard } from '../DashboardService';

describe('DashboardService', () => {
  let service: DashboardService;
  let originalEnv: NodeJS.ProcessEnv;

  beforeEach(() => {
    originalEnv = process.env;
    process.env = { ...originalEnv, JAVA_BACKEND_URL: 'http://test-backend:7003' };
    service = DashboardService.getInstance();
  });

  afterEach(() => {
    process.env = originalEnv;
  });

  describe('getInstance', () => {
    it('should return a singleton instance', () => {
      const instance1 = DashboardService.getInstance();
      const instance2 = DashboardService.getInstance();
      expect(instance1).toBe(instance2);
    });

    it('should use JAVA_BACKEND_URL from environment', () => {
      process.env.JAVA_BACKEND_URL = 'http://custom-backend:8080';
      const newInstance = new (DashboardService as any)();
      expect((newInstance as any).javaBackendUrl).toBe('http://custom-backend:8080');
    });

    it('should default to localhost:7003 if JAVA_BACKEND_URL is not set', () => {
      delete process.env.JAVA_BACKEND_URL;
      const newInstance = new (DashboardService as any)();
      expect((newInstance as any).javaBackendUrl).toBe('http://localhost:7003');
    });
  });

  describe('getDashboards', () => {
    it('should fetch dashboards from Java backend', async () => {
      const mockDashboards: Dashboard[] = [
        {
          id: 'dash-1',
          domainId: 'domain-1',
          title: 'Dashboard 1',
          layout: { kind: 'grid', columns: 3, items: [] },
          widgets: [],
          actions: [],
          audience: { levels: [1], personas: [] },
        },
        {
          id: 'dash-2',
          domainId: 'domain-1',
          title: 'Dashboard 2',
          layout: { kind: 'grid', columns: 2, items: [] },
          widgets: [],
          actions: [],
          audience: { levels: [2], personas: [] },
        },
      ];

      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        text: async () => JSON.stringify({ dashboards: mockDashboards }),
      } as Response);

      const result = await service.getDashboards();
      expect(result).toEqual(mockDashboards);
      expect(global.fetch).toHaveBeenCalledWith('http://test-backend:7003/api/dashboards');
    });

    it('should handle empty dashboards array', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        text: async () => JSON.stringify({ dashboards: [] }),
      } as Response);

      const result = await service.getDashboards();
      expect(result).toEqual([]);
    });

    it('should handle missing dashboards field', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        text: async () => JSON.stringify({}),
      } as Response);

      const result = await service.getDashboards();
      expect(result).toEqual([]);
    });

    it('should throw error on non-OK response', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        text: async () => 'Server error',
      } as Response);

      await expect(service.getDashboards()).rejects.toThrow('Server error');
    });

    it('should throw error on empty response', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        text: async () => '',
      } as Response);

      await expect(service.getDashboards()).rejects.toThrow('empty response');
    });

    it('should throw error on invalid JSON', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        text: async () => 'invalid json',
      } as Response);

      await expect(service.getDashboards()).rejects.toThrow('invalid JSON');
    });
  });

  describe('getDashboardsByDomain', () => {
    it('should fetch dashboards by domain from Java backend', async () => {
      const mockDashboards: Dashboard[] = [
        {
          id: 'dash-1',
          domainId: 'domain-1',
          title: 'Dashboard 1',
          layout: { kind: 'grid', columns: 3, items: [] },
          widgets: [],
          actions: [],
          audience: { levels: [1], personas: [] },
        },
      ];

      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        text: async () => JSON.stringify({ dashboards: mockDashboards }),
      } as Response);

      const result = await service.getDashboardsByDomain('domain-1');
      expect(result).toEqual(mockDashboards);
      expect(global.fetch).toHaveBeenCalledWith('http://test-backend:7003/api/dashboards/domain/domain-1');
    });

    it('should handle domain-specific empty results', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        text: async () => JSON.stringify({ dashboards: [] }),
      } as Response);

      const result = await service.getDashboardsByDomain('nonexistent-domain');
      expect(result).toEqual([]);
    });
  });

  describe('getDashboard', () => {
    it('should fetch single dashboard by ID from Java backend', async () => {
      const mockDashboard: Dashboard = {
        id: 'dash-1',
        domainId: 'domain-1',
        title: 'Dashboard 1',
        layout: { kind: 'grid', columns: 3, items: [] },
        widgets: [],
        actions: [],
        audience: { levels: [1], personas: [] },
      };

      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        text: async () => JSON.stringify({ dashboard: mockDashboard }),
      } as Response);

      const result = await service.getDashboard('dash-1');
      expect(result).toEqual(mockDashboard);
      expect(global.fetch).toHaveBeenCalledWith('http://test-backend:7003/api/dashboards/dash-1');
    });

    it('should return null for non-existent dashboard', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
        text: async () => 'Dashboard not found',
      } as Response);

      const result = await service.getDashboard('nonexistent');
      expect(result).toBeNull();
    });

    it('should handle missing dashboard field', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        text: async () => JSON.stringify({}),
      } as Response);

      const result = await service.getDashboard('dash-1');
      expect(result).toBeNull();
    });

    it('should handle network errors gracefully', async () => {
      global.fetch = vi.fn().mockRejectedValue(new Error('Network error'));

      const result = await service.getDashboard('dash-1');
      expect(result).toBeNull();
    });
  });

  describe('Error Handling', () => {
    it('should extract error message from JSON response', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        text: async () => JSON.stringify({ message: 'Invalid request' }),
      } as Response);

      await expect(service.getDashboards()).rejects.toThrow('Invalid request');
    });

    it('should extract error field from JSON response', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        text: async () => JSON.stringify({ error: 'Server error' }),
      } as Response);

      await expect(service.getDashboards()).rejects.toThrow('Server error');
    });

    it('should use raw text if JSON parsing fails', async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        text: async () => 'Plain text error message',
      } as Response);

      await expect(service.getDashboards()).rejects.toThrow('Plain text error message');
    });

    it('should handle response with data envelope', async () => {
      const mockDashboards: Dashboard[] = [
        {
          id: 'dash-1',
          domainId: 'domain-1',
          title: 'Dashboard 1',
          layout: { kind: 'grid', columns: 3, items: [] },
          widgets: [],
          actions: [],
          audience: { levels: [1], personas: [] },
        },
      ];

      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        text: async () => JSON.stringify({ data: { dashboards: mockDashboards } }),
      } as Response);

      const result = await service.getDashboards();
      expect(result).toEqual({ dashboards: mockDashboards });
    });
  });
});
