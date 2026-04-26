import { describe, it, expect } from 'vitest';
import {
  STORAGE_REGISTRY,
  getStorageMeta,
  isKnownStorageKey,
  getKeysRequiringMigration,
  getHighSensitivityKeys,
} from '../StorageRegistry';

describe('StorageRegistry', () => {
  it('should contain auth-session as HIGH sensitivity', () => {
    const meta = getStorageMeta('auth-session');
    expect(meta).not.toBeNull();
    expect(meta?.sensitivity).toBe('HIGH');
    expect(meta?.targetBackend).toBe('cookie-secure');
  });

  it('should contain theme as LOW sensitivity', () => {
    const meta = getStorageMeta('theme');
    expect(meta?.sensitivity).toBe('LOW');
    expect(meta?.targetBackend).toBe('localStorage');
  });

  it('should identify unknown keys as ungoverned', () => {
    expect(isKnownStorageKey('unknown-key')).toBe(false);
    expect(getStorageMeta('unknown-key')).toBeNull();
  });

  it('should list HIGH and TEST keys as requiring migration', () => {
    const migrationKeys = getKeysRequiringMigration();
    const keyNames = migrationKeys.map((m) => m.key);
    expect(keyNames).toContain('auth-session');
    expect(keyNames).toContain('auth_token');
    expect(keyNames).toContain('api_key');
    expect(keyNames).toContain('E2E_FORCE_NETWORK_ERROR');
    expect(keyNames).not.toContain('theme');
  });

  it('should return only HIGH sensitivity keys', () => {
    const highKeys = getHighSensitivityKeys();
    expect(highKeys.every((m) => m.sensitivity === 'HIGH')).toBe(true);
    expect(highKeys.map((m) => m.key)).toContain('auth-session');
  });

  it('should have all keys with unique names', () => {
    const keys = Object.keys(STORAGE_REGISTRY);
    const uniqueKeys = new Set(keys);
    expect(uniqueKeys.size).toBe(keys.length);
  });
});
