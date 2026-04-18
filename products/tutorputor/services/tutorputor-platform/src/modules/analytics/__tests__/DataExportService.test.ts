/**
 * Data Export Service Tests
 *
 * @doc.type test
 * @doc.purpose Test data export service
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { DataExportService } from '../DataExportService';

describe('DataExportService', () => {
  let service: DataExportService;
  let mockPrisma: any;

  beforeEach(() => {
    mockPrisma = {
      learningEvent: { findMany: vi.fn() },
      enrollment: { findMany: vi.fn() },
      assessmentAttempt: { findMany: vi.fn() },
    };
    service = new DataExportService(mockPrisma as any);
  });

  describe('exportData', () => {
    it('should export data in CSV format', async () => {
      mockPrisma.learningEvent.findMany.mockResolvedValue([
        { eventType: 'module_viewed', userId: 'user-1', timestamp: new Date() },
      ]);
      mockPrisma.enrollment.findMany.mockResolvedValue([]);
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([]);

      const result = await service.exportData({
        tenantId: 'tenant-1',
        format: 'csv',
        scope: 'tenant',
      });

      expect(result.format).toBe('csv');
      expect(result.rowCount).toBeGreaterThan(0);
      expect(result.data).toContain('eventType');
    });

    it('should export data in JSON format', async () => {
      mockPrisma.learningEvent.findMany.mockResolvedValue([]);
      mockPrisma.enrollment.findMany.mockResolvedValue([]);
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([]);

      const result = await service.exportData({
        tenantId: 'tenant-1',
        format: 'json',
        scope: 'tenant',
      });

      expect(result.format).toBe('json');
      expect(() => JSON.parse(result.data)).not.toThrow();
    });

    it('should anonymize data when requested', async () => {
      mockPrisma.learningEvent.findMany.mockResolvedValue([
        { eventType: 'module_viewed', userId: 'user-1', timestamp: new Date() },
      ]);
      mockPrisma.enrollment.findMany.mockResolvedValue([]);
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([]);

      const result = await service.exportData({
        tenantId: 'tenant-1',
        format: 'json',
        scope: 'tenant',
        anonymize: true,
      });

      const data = JSON.parse(result.data);
      expect(data[0].userId).not.toBe('user-1');
      expect(data[0].userId).toMatch(/^user-/);
    });
  });

  describe('getAvailableFormats', () => {
    it('should return available formats', () => {
      const formats = service.getAvailableFormats();
      expect(formats).toEqual(['csv', 'excel', 'json']);
    });
  });
});
