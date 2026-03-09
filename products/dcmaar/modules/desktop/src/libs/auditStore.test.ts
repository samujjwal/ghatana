/// <reference types="jest" />
/* eslint-env jest */
import { loadAuditEntries, saveAuditEntry } from './auditStore';
import { describe, test, expect, beforeEach } from '@jest/globals';

describe('auditStore', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test('saves and loads entries', () => {
    const e = {
      id: 't1',
      timestamp: new Date().toISOString(),
      user: 'u',
      action: 'dry-run',
      summary: 's',
      details: { a: 1 },
    };
    saveAuditEntry(e as any);
    const loaded = loadAuditEntries();
    expect(loaded.length).toBe(1);
    expect(loaded[0].id).toBe('t1');
  });
});
