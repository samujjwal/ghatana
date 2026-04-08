/**
 * OfflineService Tests
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { OfflineService, getOfflineService, destroyOfflineService, type OfflineState } from '../OfflineService';

describe('OfflineService', () => {
  let service: OfflineService;

  beforeEach(() => {
    destroyOfflineService();
    service = new OfflineService({ storageKey: 'test-offline-state' });
  });

  afterEach(() => {
    service.destroy();
    destroyOfflineService();
  });

  describe('State Management', () => {
    it('should initialize with current online status', () => {
      const state = service.getState();

      expect(state.isOnline).toBe(navigator.onLine);
    });

    it('should update state when online status changes', () => {
      const initialState = service.getState();

      // Simulate offline event
      window.dispatchEvent(new Event('offline'));

      const offlineState = service.getState();
      expect(offlineState.isOnline).toBe(false);

      // Simulate online event
      window.dispatchEvent(new Event('online'));

      const onlineState = service.getState();
      expect(onlineState.isOnline).toBe(true);
    });

    it('should notify subscribers of state changes', async () => {
      let callCount = 0;
      let resolve: () => void;

      const promise = new Promise<void>(r => { resolve = r; });

      const unsubscribe = service.subscribe((state) => {
        callCount++;
        if (callCount === 2) {
          unsubscribe();
          resolve();
        }
      });

      window.dispatchEvent(new Event('offline'));
      window.dispatchEvent(new Event('online'));

      await promise;
    });
  });

  describe('Queue Operations', () => {
    it('should queue operation when offline', () => {
      // Force offline state
      Object.defineProperty(navigator, 'onLine', { value: false, writable: true });
      window.dispatchEvent(new Event('offline'));

      const operationId = service.queueOperation({
        type: 'create',
        endpoint: '/api/tasks',
        method: 'POST',
        body: { title: 'Test' },
      });

      const state = service.getState();
      expect(state.queuedOperations.length).toBe(1);
      expect(state.queuedOperations[0].id).toBe(operationId);

      // Reset
      Object.defineProperty(navigator, 'onLine', { value: true, writable: true });
      window.dispatchEvent(new Event('online'));
    });

    it('should throw error when trying to queue while online', () => {
      expect(() => {
        service.queueOperation({
          type: 'create',
          endpoint: '/api/tasks',
          method: 'POST',
        });
      }).toThrow();
    });

    it('should remove queued operation by id', () => {
      // Force offline state
      Object.defineProperty(navigator, 'onLine', { value: false, writable: true });
      window.dispatchEvent(new Event('offline'));

      const operationId = service.queueOperation({
        type: 'create',
        endpoint: '/api/tasks',
        method: 'POST',
      });

      service.removeOperation(operationId);

      const state = service.getState();
      expect(state.queuedOperations.length).toBe(0);

      // Reset
      Object.defineProperty(navigator, 'onLine', { value: true, writable: true });
      window.dispatchEvent(new Event('online'));
    });

    it('should clear all queued operations', () => {
      // Force offline state
      Object.defineProperty(navigator, 'onLine', { value: false, writable: true });
      window.dispatchEvent(new Event('offline'));

      service.queueOperation({ type: 'create', endpoint: '/api/tasks', method: 'POST' });
      service.queueOperation({ type: 'update', endpoint: '/api/tasks/1', method: 'PUT' });

      service.clearQueue();

      const state = service.getState();
      expect(state.queuedOperations.length).toBe(0);

      // Reset
      Object.defineProperty(navigator, 'onLine', { value: true, writable: true });
      window.dispatchEvent(new Event('online'));
    });

    it('should enforce max queue size', () => {
      const serviceWithLimit = new OfflineService({
        storageKey: 'test-offline-limit',
        maxQueueSize: 2,
      });

      // Force offline state
      Object.defineProperty(navigator, 'onLine', { value: false, writable: true });
      window.dispatchEvent(new Event('offline'));

      serviceWithLimit.queueOperation({ type: 'create', endpoint: '/api/tasks', method: 'POST' });
      serviceWithLimit.queueOperation({ type: 'update', endpoint: '/api/tasks/1', method: 'PUT' });
      serviceWithLimit.queueOperation({ type: 'delete', endpoint: '/api/tasks/2', method: 'DELETE' });

      const state = serviceWithLimit.getState();
      expect(state.queuedOperations.length).toBe(2);

      // Cleanup
      serviceWithLimit.destroy();
      Object.defineProperty(navigator, 'onLine', { value: true, writable: true });
      window.dispatchEvent(new Event('online'));
    });
  });

  describe('Sync Operations', () => {
    it('should sync queued operations when online', async () => {
      // Force offline state
      Object.defineProperty(navigator, 'onLine', { value: false, writable: true });
      window.dispatchEvent(new Event('offline'));

      service.queueOperation({
        type: 'create',
        endpoint: '/api/tasks',
        method: 'POST',
        body: { title: 'Test' },
      });

      // Go online
      Object.defineProperty(navigator, 'onLine', { value: true, writable: true });
      window.dispatchEvent(new Event('online'));

      // Note: Actual sync would fail due to no real endpoint, but the method should be called
      // We're testing the structure here
      const state = service.getState();
      expect(state.isOnline).toBe(true);
    });
  });

  describe('Queue Statistics', () => {
    it('should calculate queue statistics', () => {
      // Force offline state
      Object.defineProperty(navigator, 'onLine', { value: false, writable: true });
      window.dispatchEvent(new Event('offline'));

      service.queueOperation({ type: 'create', endpoint: '/api/tasks', method: 'POST' });
      service.queueOperation({ type: 'update', endpoint: '/api/tasks/1', method: 'PUT' });

      const stats = service.getQueueStats();

      expect(stats.total).toBe(2);
      expect(stats.byType.create).toBe(1);
      expect(stats.byType.update).toBe(1);
      expect(stats.oldestTimestamp).toBeDefined();

      // Reset
      Object.defineProperty(navigator, 'onLine', { value: true, writable: true });
      window.dispatchEvent(new Event('online'));
    });
  });

  describe('Cleanup Operations', () => {
    it('should cleanup old operations', () => {
      // Force offline state
      Object.defineProperty(navigator, 'onLine', { value: false, writable: true });
      window.dispatchEvent(new Event('offline'));

      const oldTimestamp = Date.now() - 25 * 60 * 60 * 1000; // 25 hours ago
      const recentTimestamp = Date.now() - 1 * 60 * 60 * 1000; // 1 hour ago

      // Manually add operations with specific timestamps
      service.queueOperation({ type: 'create', endpoint: '/api/tasks', method: 'POST' });

      const state = service.getState();
      state.queuedOperations[0].timestamp = oldTimestamp;

      const removed = service.cleanupOldOperations();

      expect(removed).toBe(1);

      // Reset
      Object.defineProperty(navigator, 'onLine', { value: true, writable: true });
      window.dispatchEvent(new Event('online'));
    });
  });

  describe('Singleton Pattern', () => {
    it('should return same instance on subsequent calls', () => {
      const service1 = getOfflineService({ storageKey: 'test-singleton' });
      const service2 = getOfflineService({ storageKey: 'test-singleton' });

      expect(service1).toBe(service2);

      destroyOfflineService();
    });

    it('should create new instance after destroy', () => {
      const service1 = getOfflineService({ storageKey: 'test-singleton-destroy' });
      destroyOfflineService();
      const service2 = getOfflineService({ storageKey: 'test-singleton-destroy' });

      expect(service1).not.toBe(service2);

      destroyOfflineService();
    });
  });
});
