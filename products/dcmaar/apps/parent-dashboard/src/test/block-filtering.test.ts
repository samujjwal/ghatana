import { describe, it, expect } from 'vitest';
import type { BlockEvent } from '../services/websocket.service';

describe('Block Filtering Logic', () => {
  const mockEvents: BlockEvent[] = [
    {
      blockEvent: {
        id: '1',
        device_id: 'policy-1',
        blocked_item: 'facebook.com',
        event_type: 'website',
        reason: 'Inappropriate content',
        timestamp: '2025-11-03T10:00:00Z',
      },
      device: {
        id: 'device-1',
        name: 'iPhone',
        type: 'mobile',
      },
    },
    {
      blockEvent: {
        id: '2',
        device_id: 'policy-2',
        blocked_item: 'TikTok',
        event_type: 'app',
        reason: 'Time limit exceeded',
        timestamp: '2025-11-03T11:00:00Z',
      },
      device: {
        id: 'device-2',
        name: 'iPad',
        type: 'tablet',
      },
    },
    {
      blockEvent: {
        id: '3',
        device_id: 'policy-1',
        blocked_item: 'instagram.com',
        event_type: 'website',
        reason: 'Inappropriate content',
        timestamp: '2025-11-03T12:00:00Z',
      },
      device: {
        id: 'device-1',
        name: 'iPhone',
        type: 'mobile',
      },
    },
  ];

  it('should filter by policy ID (device_id)', () => {
    const filtered = mockEvents.filter((event) => event.blockEvent.device_id === 'policy-1');
    expect(filtered).toHaveLength(2);
    expect(filtered[0].blockEvent.blocked_item).toBe('facebook.com');
    expect(filtered[1].blockEvent.blocked_item).toBe('instagram.com');
  });

  it('should filter by device ID', () => {
    const filtered = mockEvents.filter((event) => event.device.id === 'device-2');
    expect(filtered).toHaveLength(1);
    expect(filtered[0].blockEvent.blocked_item).toBe('TikTok');
  });

  it('should filter by reason (case-insensitive)', () => {
    const searchTerm = 'time limit';
    const filtered = mockEvents.filter((event) => 
      event.blockEvent.reason.toLowerCase().includes(searchTerm.toLowerCase())
    );
    expect(filtered).toHaveLength(1);
    expect(filtered[0].blockEvent.blocked_item).toBe('TikTok');
  });

  it('should count unique devices correctly', () => {
    const deviceCounts = mockEvents.reduce((acc, event) => {
      const deviceId = event.device.id;
      acc[deviceId] = (acc[deviceId] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);
    
    expect(Object.keys(deviceCounts).length).toBe(2);
    expect(deviceCounts['device-1']).toBe(2);
    expect(deviceCounts['device-2']).toBe(1);
  });
});
