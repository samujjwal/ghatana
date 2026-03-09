import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  exportUsageEventsToPDF,
  exportBlockEventsToPDF,
  exportPoliciesToPDF,
  exportAnalyticsSummaryToPDF,
} from '../utils/pdfExport';
import type { UsageEvent, BlockEvent } from '../services/websocket.service';
import type { Policy } from '../components/PolicyManagement';

// Mock jsPDF
vi.mock('jspdf', () => {
  const mockAutoTable = vi.fn();
  class MockJsPDF {
    text = vi.fn();
    autoTable = mockAutoTable;
    save = vi.fn();
    setFontSize = vi.fn();
    lastAutoTable = { finalY: 100 };
  }
  return {
    default: MockJsPDF,
  };
});

// Mock jspdf-autotable
vi.mock('jspdf-autotable', () => ({
  default: vi.fn(),
}));

describe('PDF Export Utilities', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('exportUsageEventsToPDF', () => {
    it('should create PDF with usage events table', () => {
      const events: UsageEvent[] = [
        {
          usageSession: {
            id: '1',
            device_id: 'device-1',
            item_name: 'Test App',
            session_type: 'application',
            duration_seconds: 120,
            timestamp: '2024-01-15T10:30:00Z',
          },
          device: {
            id: 'device-1',
            name: 'Test Device',
            type: 'desktop',
          },
        },
      ];

      // Should not throw
      expect(() => exportUsageEventsToPDF(events)).not.toThrow();
    });

    it('should handle empty events array', () => {
      expect(() => exportUsageEventsToPDF([])).not.toThrow();
    });
  });

  describe('exportBlockEventsToPDF', () => {
    it('should create PDF with block events table', () => {
      const events: BlockEvent[] = [
        {
          blockEvent: {
            id: '1',
            device_id: 'device-1',
            blocked_item: 'Blocked App',
            event_type: 'application',
            reason: 'Policy violation',
            timestamp: '2024-01-15T10:30:00Z',
          },
          device: {
            id: 'device-1',
            name: 'Test Device',
            type: 'desktop',
          },
        },
      ];

      expect(() => exportBlockEventsToPDF(events)).not.toThrow();
    });

    it('should handle empty events array', () => {
      expect(() => exportBlockEventsToPDF([])).not.toThrow();
    });
  });

  describe('exportPoliciesToPDF', () => {
    it('should create PDF with policies table', () => {
      const policies: Policy[] = [
        {
          id: '1',
          name: 'Work Policy',
          type: 'time-limit',
          restrictions: {
            maxUsageMinutes: 120,
            blockedCategories: ['social'],
            allowedHours: {
              start: '09:00',
              end: '17:00',
            },
          },
          deviceIds: ['device-1', 'device-2'],
          createdAt: '2024-01-01',
          updatedAt: '2024-01-15',
        },
      ];

      expect(() => exportPoliciesToPDF(policies)).not.toThrow();
    });

    it('should handle empty policies array', () => {
      expect(() => exportPoliciesToPDF([])).not.toThrow();
    });
  });

  describe('exportAnalyticsSummaryToPDF', () => {
    it('should create multi-section summary PDF', () => {
      const usageStats = {
        totalMinutes: 120,
        uniqueDevices: 2,
        topApps: [
          { app: 'App 1', minutes: 60 },
          { app: 'App 2', minutes: 40 },
        ],
      };

      const blockStats = {
        totalBlocks: 15,
        uniqueDevices: 2,
        topBlockedItems: [
          { item: 'Blocked App', count: 8 },
        ],
      };

      expect(() => exportAnalyticsSummaryToPDF(usageStats, blockStats, '7d')).not.toThrow();
    });

    it('should handle custom time range', () => {
      const usageStats = {
        totalMinutes: 0,
        uniqueDevices: 0,
        topApps: [],
      };

      const blockStats = {
        totalBlocks: 0,
        uniqueDevices: 0,
        topBlockedItems: [],
      };

      expect(() => exportAnalyticsSummaryToPDF(usageStats, blockStats, 'custom')).not.toThrow();
    });
  });
});
