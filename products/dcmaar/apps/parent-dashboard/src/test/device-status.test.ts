import { describe, it, expect } from 'vitest';

describe('Device Status Logic', () => {
  it('should filter devices by search term', () => {
    const devices = [
      { id: '1', name: 'Johns iPhone', type: 'mobile' as const, status: 'online' as const, lastHeartbeat: '', registeredAt: '', policies: [] },
      { id: '2', name: 'iPad Pro', type: 'tablet' as const, status: 'offline' as const, lastHeartbeat: '', registeredAt: '', policies: [] },
    ];

    const searchTerm = 'iphone';
    const filtered = devices.filter(d => d.name.toLowerCase().includes(searchTerm.toLowerCase()));
    
    expect(filtered).toHaveLength(1);
    expect(filtered[0].name).toBe('Johns iPhone');
  });

  it('should filter devices by status', () => {
    const devices = [
      { id: '1', name: 'Device 1', type: 'mobile' as const, status: 'online' as const, lastHeartbeat: '', registeredAt: '', policies: [] },
      { id: '2', name: 'Device 2', type: 'mobile' as const, status: 'offline' as const, lastHeartbeat: '', registeredAt: '', policies: [] },
      { id: '3', name: 'Device 3', type: 'mobile' as const, status: 'online' as const, lastHeartbeat: '', registeredAt: '', policies: [] },
    ];

    const filterStatus = 'online';
    const filtered = devices.filter(d => d.status === filterStatus);
    
    expect(filtered).toHaveLength(2);
    expect(filtered[0].status).toBe('online');
    expect(filtered[1].status).toBe('online');
  });

  it('should filter devices by type', () => {
    const devices = [
      { id: '1', name: 'Device 1', type: 'mobile' as const, status: 'online' as const, lastHeartbeat: '', registeredAt: '', policies: [] },
      { id: '2', name: 'Device 2', type: 'tablet' as const, status: 'online' as const, lastHeartbeat: '', registeredAt: '', policies: [] },
      { id: '3', name: 'Device 3', type: 'mobile' as const, status: 'online' as const, lastHeartbeat: '', registeredAt: '', policies: [] },
    ];

    const filterType = 'mobile';
    const filtered = devices.filter(d => d.type === filterType);
    
    expect(filtered).toHaveLength(2);
    expect(filtered[0].type).toBe('mobile');
    expect(filtered[1].type).toBe('mobile');
  });

  it('should count online and offline devices correctly', () => {
    const devices = [
      { id: '1', name: 'Device 1', type: 'mobile' as const, status: 'online' as const, lastHeartbeat: '', registeredAt: '', policies: [] },
      { id: '2', name: 'Device 2', type: 'mobile' as const, status: 'offline' as const, lastHeartbeat: '', registeredAt: '', policies: [] },
      { id: '3', name: 'Device 3', type: 'mobile' as const, status: 'online' as const, lastHeartbeat: '', registeredAt: '', policies: [] },
      { id: '4', name: 'Device 4', type: 'mobile' as const, status: 'offline' as const, lastHeartbeat: '', registeredAt: '', policies: [] },
    ];

    const online = devices.filter(d => d.status === 'online').length;
    const offline = devices.filter(d => d.status === 'offline').length;
    
    expect(online).toBe(2);
    expect(offline).toBe(2);
  });
});
