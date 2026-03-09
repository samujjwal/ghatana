/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  arrayToCSV,
  exportUsageEventsToCSV,
  exportBlockEventsToCSV,
  exportPoliciesToCSV,
} from '../utils/csvExport';
import type { UsageEvent, BlockEvent } from '../services/websocket.service';
import type { Policy } from '../components/PolicyManagement';

describe('CSV Export Utilities', () => {
  // Mock URL.createObjectURL and URL.revokeObjectURL
  beforeEach(() => {
    (window.URL.createObjectURL as any) = vi.fn(() => 'blob:mock-url');
    (window.URL.revokeObjectURL as any) = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('arrayToCSV', () => {
    it('should convert array to CSV with proper escaping', () => {
      const headers = ['Name', 'Email', 'Comment'];
      const data = [
        { Name: 'John Doe', Email: 'john@example.com', Comment: 'Test comment' },
        { Name: 'Jane Smith', Email: 'jane@example.com', Comment: 'Comment with "quotes"' },
        { Name: 'Bob Wilson', Email: 'bob@example.com', Comment: 'Comment with, comma' },
      ];

      const csv = arrayToCSV(headers, data);

      expect(csv).toContain('Name,Email,Comment');
      expect(csv).toContain('John Doe,john@example.com,Test comment');
      expect(csv).toContain('"Comment with ""quotes"""'); // Escaped quotes
      expect(csv).toContain('"Comment with, comma"'); // Escaped comma
    });

    it('should handle newlines in data', () => {
      const headers = ['Title', 'Description'];
      const data = [{ Title: 'Test', Description: 'Line 1\nLine 2' }];

      const csv = arrayToCSV(headers, data);

      expect(csv).toContain('"Line 1\nLine 2"'); // Newlines should be escaped
    });

    it('should use custom delimiter', () => {
      const headers = ['A', 'B'];
      const data = [{ A: '1', B: '2' }];

      const csv = arrayToCSV(headers, data, ';');

      expect(csv).toContain('A;B');
      expect(csv).toContain('1;2');
    });
  });

  describe('exportUsageEventsToCSV', () => {
    it('should export usage events with correct headers', () => {
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
        {
          usageSession: {
            id: '2',
            device_id: 'device-2',
            item_name: 'Another App',
            session_type: 'application',
            duration_seconds: 300,
            timestamp: '2024-01-15T11:00:00Z',
          },
          device: {
            id: 'device-2',
            name: 'Device 2',
            type: 'mobile',
          },
        },
      ];

      // Mock document.createElement and link methods
      const mockLink: any = {
        href: '',
        download: '',
        click: vi.fn(),
        setAttribute: vi.fn((attr: string, value: string) => {
          mockLink[attr] = value;
        }),
        style: { visibility: '' },
      };
      vi.spyOn(document, 'createElement').mockReturnValue(mockLink as any);
      vi.spyOn(document.body, 'appendChild').mockImplementation(() => mockLink as any);
      vi.spyOn(document.body, 'removeChild').mockImplementation(() => mockLink as any);

      exportUsageEventsToCSV(events);

      expect(mockLink.click).toHaveBeenCalled();
      expect(mockLink.setAttribute).toHaveBeenCalledWith('download', expect.stringContaining('usage-events'));
    });

    it('should handle empty events array', () => {
      const mockLink: any = {
        href: '',
        download: '',
        click: vi.fn(),
        setAttribute: vi.fn((attr: string, value: string) => {
          mockLink[attr] = value;
        }),
        style: { visibility: '' },
      };
      vi.spyOn(document, 'createElement').mockReturnValue(mockLink as any);
      vi.spyOn(document.body, 'appendChild').mockImplementation(() => mockLink as any);
      vi.spyOn(document.body, 'removeChild').mockImplementation(() => mockLink as any);

      exportUsageEventsToCSV([]);

      expect(mockLink.click).toHaveBeenCalled();
    });
  });

  describe('exportBlockEventsToCSV', () => {
    it('should export block events with correct data', () => {
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

      const mockLink: any = {
        href: '',
        download: '',
        click: vi.fn(),
        setAttribute: vi.fn((attr: string, value: string) => {
          mockLink[attr] = value;
        }),
        style: { visibility: '' },
      };
      vi.spyOn(document, 'createElement').mockReturnValue(mockLink as any);
      vi.spyOn(document.body, 'appendChild').mockImplementation(() => mockLink as any);
      vi.spyOn(document.body, 'removeChild').mockImplementation(() => mockLink as any);

      exportBlockEventsToCSV(events);

      expect(mockLink.click).toHaveBeenCalled();
      expect(mockLink.setAttribute).toHaveBeenCalledWith('download', expect.stringContaining('block-events'));
    });
  });

  describe('exportPoliciesToCSV', () => {
    it('should export policies with restrictions', () => {
      const policies: Policy[] = [
        {
          id: '1',
          name: 'Work Policy',
          type: 'time-limit',
          restrictions: {
            maxUsageMinutes: 120,
            blockedCategories: ['social', 'gaming'],
            blockedApps: ['Facebook', 'Instagram'],
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

      const mockLink: any = {
        href: '',
        download: '',
        click: vi.fn(),
        setAttribute: vi.fn((attr: string, value: string) => {
          mockLink[attr] = value;
        }),
        style: { visibility: '' },
      };
      vi.spyOn(document, 'createElement').mockReturnValue(mockLink as any);
      vi.spyOn(document.body, 'appendChild').mockImplementation(() => mockLink as any);
      vi.spyOn(document.body, 'removeChild').mockImplementation(() => mockLink as any);

      exportPoliciesToCSV(policies);

      expect(mockLink.click).toHaveBeenCalled();
      expect(mockLink.setAttribute).toHaveBeenCalledWith('download', expect.stringContaining('policies'));
    });
  });
});
