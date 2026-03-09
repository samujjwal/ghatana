import { describe, it, expect } from 'vitest';
import type { UsageEvent } from '../services/websocket.service';

describe('Usage Filtering Logic', () => {
  const mockEvents: UsageEvent[] = [
    {
      usageSession: {
        id: '1',
        device_id: 'child-1',
        item_name: 'YouTube',
        session_type: 'app',
        duration_seconds: 300,
        timestamp: '2025-11-03T10:00:00Z',
      },
      device: {
        id: 'device-1',
        name: 'iPhone',
        type: 'mobile',
      },
    },
    {
      usageSession: {
        id: '2',
        device_id: 'child-2',
        item_name: 'Instagram',
        session_type: 'app',
        duration_seconds: 600,
        timestamp: '2025-11-03T11:00:00Z',
      },
      device: {
        id: 'device-2',
        name: 'iPad',
        type: 'tablet',
      },
    },
    {
      usageSession: {
        id: '3',
        device_id: 'child-1',
        item_name: 'Facebook',
        session_type: 'website',
        duration_seconds: 450,
        timestamp: '2025-11-03T12:00:00Z',
      },
      device: {
        id: 'device-1',
        name: 'iPhone',
        type: 'mobile',
      },
    },
  ];

  it('should filter by child ID', () => {
    const filtered = mockEvents.filter((event) => event.usageSession.device_id === 'child-1');
    expect(filtered).toHaveLength(2);
    expect(filtered[0].usageSession.item_name).toBe('YouTube');
    expect(filtered[1].usageSession.item_name).toBe('Facebook');
  });

  it('should filter by device ID', () => {
    const filtered = mockEvents.filter((event) => event.device.id === 'device-2');
    expect(filtered).toHaveLength(1);
    expect(filtered[0].usageSession.item_name).toBe('Instagram');
  });

  it('should filter by item name (case-insensitive)', () => {
    const searchTerm = 'face';
    const filtered = mockEvents.filter((event) => 
      event.usageSession.item_name.toLowerCase().includes(searchTerm.toLowerCase())
    );
    expect(filtered).toHaveLength(1);
    expect(filtered[0].usageSession.item_name).toBe('Facebook');
  });

  it('should calculate total duration correctly', () => {
    const totalDuration = mockEvents.reduce((sum, event) => sum + event.usageSession.duration_seconds, 0);
    expect(totalDuration).toBe(1350); // 300 + 600 + 450
  });

  it('should calculate average duration correctly', () => {
    const totalDuration = mockEvents.reduce((sum, event) => sum + event.usageSession.duration_seconds, 0);
    const avgDuration = totalDuration / mockEvents.length;
    expect(avgDuration).toBe(450); // 1350 / 3
  });

  it('should return empty array when no events match filter', () => {
    const filtered = mockEvents.filter((event) => event.usageSession.device_id === 'non-existent');
    expect(filtered).toHaveLength(0);
  });
});
