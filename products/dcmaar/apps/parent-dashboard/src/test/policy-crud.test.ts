import { describe, it, expect, beforeEach, vi } from 'vitest';

describe('Policy CRUD Operations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should filter policies by search term', () => {
    const policies = [
      { id: '1', name: 'School Time Limit', type: 'time-limit' as const, restrictions: {}, deviceIds: [], createdAt: '', updatedAt: '' },
      { id: '2', name: 'Gaming Block', type: 'app-block' as const, restrictions: {}, deviceIds: [], createdAt: '', updatedAt: '' },
    ];

    const searchTerm = 'school';
    const filtered = policies.filter(p => p.name.toLowerCase().includes(searchTerm.toLowerCase()));
    
    expect(filtered).toHaveLength(1);
    expect(filtered[0].name).toBe('School Time Limit');
  });

  it('should filter policies by type', () => {
    const policies = [
      { id: '1', name: 'School Time Limit', type: 'time-limit' as const, restrictions: {}, deviceIds: [], createdAt: '', updatedAt: '' },
      { id: '2', name: 'Content Filter', type: 'content-filter' as const, restrictions: {}, deviceIds: [], createdAt: '', updatedAt: '' },
      { id: '3', name: 'Gaming Limit', type: 'time-limit' as const, restrictions: {}, deviceIds: [], createdAt: '', updatedAt: '' },
    ];

    const filterType = 'time-limit';
    const filtered = policies.filter(p => p.type === filterType);
    
    expect(filtered).toHaveLength(2);
    expect(filtered[0].type).toBe('time-limit');
    expect(filtered[1].type).toBe('time-limit');
  });

  it('should count policies by type correctly', () => {
    const policies = [
      { id: '1', name: 'Policy 1', type: 'time-limit' as const, restrictions: {}, deviceIds: [], createdAt: '', updatedAt: '' },
      { id: '2', name: 'Policy 2', type: 'content-filter' as const, restrictions: {}, deviceIds: [], createdAt: '', updatedAt: '' },
      { id: '3', name: 'Policy 3', type: 'time-limit' as const, restrictions: {}, deviceIds: [], createdAt: '', updatedAt: '' },
      { id: '4', name: 'Policy 4', type: 'app-block' as const, restrictions: {}, deviceIds: [], createdAt: '', updatedAt: '' },
    ];

    const timeLimits = policies.filter(p => p.type === 'time-limit').length;
    const contentFilters = policies.filter(p => p.type === 'content-filter').length;
    const appBlocks = policies.filter(p => p.type === 'app-block').length;
    
    expect(timeLimits).toBe(2);
    expect(contentFilters).toBe(1);
    expect(appBlocks).toBe(1);
  });
});
