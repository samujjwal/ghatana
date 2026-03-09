import { describe, it, expect } from 'vitest';

describe('Analytics Data Processing', () => {
  it('should calculate total usage minutes correctly', () => {
    const events = [
      { duration: 30 },
      { duration: 45 },
      { duration: 60 },
    ];

    const total = events.reduce((sum, e) => sum + e.duration, 0);
    expect(total).toBe(135);
  });

  it('should aggregate app usage correctly', () => {
    const events = [
      { app: 'YouTube', duration: 30 },
      { app: 'Instagram', duration: 20 },
      { app: 'YouTube', duration: 15 },
    ];

    const appUsage = new Map<string, number>();
    events.forEach(event => {
      appUsage.set(event.app, (appUsage.get(event.app) || 0) + event.duration);
    });

    expect(appUsage.get('YouTube')).toBe(45);
    expect(appUsage.get('Instagram')).toBe(20);
  });

  it('should sort top apps by usage', () => {
    const appUsage = new Map([
      ['YouTube', 45],
      ['Instagram', 20],
      ['TikTok', 60],
    ]);

    const sorted = Array.from(appUsage.entries())
      .map(([app, minutes]) => ({ app, minutes }))
      .sort((a, b) => b.minutes - a.minutes);

    expect(sorted[0].app).toBe('TikTok');
    expect(sorted[1].app).toBe('YouTube');
    expect(sorted[2].app).toBe('Instagram');
  });

  it('should count unique devices correctly', () => {
    const events = [
      { deviceId: 'device-1' },
      { deviceId: 'device-2' },
      { deviceId: 'device-1' },
      { deviceId: 'device-3' },
    ];

    const uniqueDevices = new Set(events.map(e => e.deviceId)).size;
    expect(uniqueDevices).toBe(3);
  });

  it('should aggregate blocks by item', () => {
    const blocks = [
      { item: 'facebook.com' },
      { item: 'instagram.com' },
      { item: 'facebook.com' },
      { item: 'facebook.com' },
    ];

    const itemCounts = new Map<string, number>();
    blocks.forEach(block => {
      itemCounts.set(block.item, (itemCounts.get(block.item) || 0) + 1);
    });

    expect(itemCounts.get('facebook.com')).toBe(3);
    expect(itemCounts.get('instagram.com')).toBe(1);
  });

  it('should aggregate blocks by reason', () => {
    const blocks = [
      { reason: 'Time limit exceeded' },
      { reason: 'Inappropriate content' },
      { reason: 'Time limit exceeded' },
    ];

    const reasonCounts = new Map<string, number>();
    blocks.forEach(block => {
      reasonCounts.set(block.reason, (reasonCounts.get(block.reason) || 0) + 1);
    });

    expect(reasonCounts.get('Time limit exceeded')).toBe(2);
    expect(reasonCounts.get('Inappropriate content')).toBe(1);
  });

  it('should calculate average usage per device', () => {
    const totalMinutes = 120;
    const uniqueDevices = 3;

    const average = Math.round(totalMinutes / uniqueDevices);
    expect(average).toBe(40);
  });

  it('should handle zero devices gracefully', () => {
    const totalMinutes = 120;
    const uniqueDevices = 0;

    const average = uniqueDevices > 0 ? Math.round(totalMinutes / uniqueDevices) : 0;
    expect(average).toBe(0);
  });

  it('should filter events by time range', () => {
    const now = new Date('2025-11-03T12:00:00Z');
    const cutoffTime = new Date(now.getTime() - 24 * 60 * 60 * 1000); // 24h ago

    const events = [
      { timestamp: '2025-11-03T11:00:00Z' }, // Within range (1h ago)
      { timestamp: '2025-11-02T13:00:00Z' }, // Within range (23h ago)
      { timestamp: '2025-11-01T11:00:00Z' }, // Outside range (49h ago)
    ];

    const filtered = events.filter(e => new Date(e.timestamp) >= cutoffTime);
    expect(filtered).toHaveLength(2);
  });

  it('should aggregate daily usage', () => {
    const events = [
      { timestamp: '2025-11-03T10:00:00Z', duration: 30 },
      { timestamp: '2025-11-03T14:00:00Z', duration: 20 },
      { timestamp: '2025-11-02T10:00:00Z', duration: 40 },
    ];

    const dailyMap = new Map<string, number>();
    events.forEach(event => {
      const date = new Date(event.timestamp).toLocaleDateString();
      dailyMap.set(date, (dailyMap.get(date) || 0) + event.duration);
    });

    expect(dailyMap.size).toBe(2);
  });
});
