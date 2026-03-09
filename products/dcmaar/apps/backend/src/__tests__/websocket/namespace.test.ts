/**
import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
 * WebSocket Namespace Utility Tests
 */

import { getRoomNames } from '../../websocket/server';

describe('WebSocket room naming helpers', () => {
  it('builds deterministic room names', () => {
    expect(getRoomNames.parent('parent-1')).toBe('parent:parent-1');
    expect(getRoomNames.child('child-1')).toBe('child:child-1');
    expect(getRoomNames.device('device-1')).toBe('device:device-1');
  });
});

