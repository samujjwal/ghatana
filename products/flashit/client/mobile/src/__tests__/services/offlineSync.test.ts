/**
 * Offline Sync Service Tests
 * Tests offline queue, conflict resolution, and sync reliability
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { NetInfo } from '@react-native-community/netinfo';
import OfflineSyncService from '../services/OfflineSyncService';
import { SyncQueueItem, SyncConflict, SyncStatus } from '../types/sync';

describe('OfflineSyncService', () => {
    let syncService: OfflineSyncService;
    const mockApiClient = {
        post: jest.fn(),
        get: jest.fn(),
        put: jest.fn(),
        delete: jest.fn(),
    };

    beforeEach(() => {
        jest.clearAllMocks();
        syncService = new OfflineSyncService(mockApiClient);
        AsyncStorage.clear();
    });

    describe('Queue Management', () => {
        test('should add item to offline queue', async () => {
            const item: SyncQueueItem = {
                id: 'test-1',
                type: 'CREATE_MOMENT',
                data: { content: 'Test moment', sphereId: 'sphere-1' },
                timestamp: Date.now(),
                retries: 0,
            };

            await syncService.addToQueue(item);

            const queue = await syncService.getQueue();
            expect(queue).toHaveLength(1);
            expect(queue[0]).toEqual(item);
        });

        test('should maintain queue order', async () => {
            const items: SyncQueueItem[] = [
                {
                    id: 'test-1',
                    type: 'CREATE_MOMENT',
                    data: { content: 'First' },
                    timestamp: Date.now() - 1000,
                    retries: 0,
                },
                {
                    id: 'test-2',
                    type: 'CREATE_MOMENT',
                    data: { content: 'Second' },
                    timestamp: Date.now(),
                    retries: 0,
                },
            ];

            for (const item of items) {
                await syncService.addToQueue(item);
            }

            const queue = await syncService.getQueue();
            expect(queue).toHaveLength(2);
            expect(queue[0].data.content).toBe('First');
            expect(queue[1].data.content).toBe('Second');
        });

        test('should remove item from queue after successful sync', async () => {
            const item: SyncQueueItem = {
                id: 'test-1',
                type: 'CREATE_MOMENT',
                data: { content: 'Test moment' },
                timestamp: Date.now(),
                retries: 0,
            };

            await syncService.addToQueue(item);
            mockApiClient.post.mockResolvedValue({ data: { id: 'server-id' } });

            await syncService.processQueue();

            const queue = await syncService.getQueue();
            expect(queue).toHaveLength(0);
        });
    });

    describe('Network Detection', () => {
        test('should detect online status', async () => {
            NetInfo.fetch.mockResolvedValue({
                isConnected: true,
                isInternetReachable: true,
            });

            const isOnline = await syncService.isOnline();
            expect(isOnline).toBe(true);
        });

        test('should detect offline status', async () => {
            NetInfo.fetch.mockResolvedValue({
                isConnected: false,
                isInternetReachable: false,
            });

            const isOnline = await syncService.isOnline();
            expect(isOnline).toBe(false);
        });

        test('should trigger sync when coming online', async () => {
            const processQueueSpy = jest.spyOn(syncService, 'processQueue');

            // Start offline
            NetInfo.fetch.mockResolvedValue({ isConnected: false });
            await syncService.startNetworkListener();

            // Come online
            NetInfo.fetch.mockResolvedValue({ isConnected: true });

            // Simulate network change event
            const networkChangeCallback = NetInfo.addEventListener.mock.calls[0][0];
            await networkChangeCallback({ isConnected: true });

            expect(processQueueSpy).toHaveBeenCalled();
        });
    });

    describe('Conflict Resolution', () => {
        test('should detect version conflicts', async () => {
            const localItem: SyncQueueItem = {
                id: 'test-1',
                type: 'UPDATE_MOMENT',
                data: {
                    id: 'moment-1',
                    content: 'Local version',
                    version: 1,
                },
                timestamp: Date.now(),
                retries: 0,
            };

            const serverResponse = {
                id: 'moment-1',
                content: 'Server version',
                version: 2,
                updatedAt: new Date().toISOString(),
            };

            mockApiClient.put.mockRejectedValue({
                response: {
                    status: 409,
                    data: {
                        error: 'VERSION_CONFLICT',
                        serverData: serverResponse,
                    },
                },
            });

            await syncService.addToQueue(localItem);
            await syncService.processQueue();

            const conflicts = await syncService.getConflicts();
            expect(conflicts).toHaveLength(1);
            expect(conflicts[0].type).toBe('VERSION_CONFLICT');
        });

        test('should resolve conflicts with server wins', async () => {
            const conflict: SyncConflict = {
                id: 'conflict-1',
                itemId: 'test-1',
                type: 'VERSION_CONFLICT',
                localData: { content: 'Local version', version: 1 },
                serverData: { content: 'Server version', version: 2 },
                timestamp: Date.now(),
            };

            await syncService.addConflict(conflict);
            await syncService.resolveConflict(conflict.id, 'SERVER_WINS');

            const conflicts = await syncService.getConflicts();
            expect(conflicts).toHaveLength(0);
        });

        test('should resolve conflicts with local wins', async () => {
            const conflict: SyncConflict = {
                id: 'conflict-1',
                itemId: 'test-1',
                type: 'VERSION_CONFLICT',
                localData: { content: 'Local version', version: 1 },
                serverData: { content: 'Server version', version: 2 },
                timestamp: Date.now(),
            };

            mockApiClient.put.mockResolvedValue({
                data: { id: 'moment-1', content: 'Local version', version: 3 },
            });

            await syncService.addConflict(conflict);
            await syncService.resolveConflict(conflict.id, 'LOCAL_WINS');

            const conflicts = await syncService.getConflicts();
            expect(conflicts).toHaveLength(0);

            expect(mockApiClient.put).toHaveBeenCalledWith(
                '/api/moments/moment-1',
                expect.objectContaining({ content: 'Local version' })
            );
        });

        test('should merge conflicting data when possible', async () => {
            const conflict: SyncConflict = {
                id: 'conflict-1',
                itemId: 'test-1',
                type: 'VERSION_CONFLICT',
                localData: {
                    content: 'Local content',
                    tags: ['local'],
                    emotions: ['happy'],
                },
                serverData: {
                    content: 'Server content',
                    tags: ['server'],
                    emotions: ['excited'],
                },
                timestamp: Date.now(),
            };

            mockApiClient.put.mockResolvedValue({
                data: {
                    content: 'Local content',
                    tags: ['local', 'server'],
                    emotions: ['happy', 'excited'],
                    version: 3,
                },
            });

            await syncService.addConflict(conflict);
            await syncService.resolveConflict(conflict.id, 'MERGE');

            const conflicts = await syncService.getConflicts();
            expect(conflicts).toHaveLength(0);

            expect(mockApiClient.put).toHaveBeenCalledWith(
                '/api/moments/test-1',
                expect.objectContaining({
                    tags: ['local', 'server'],
                    emotions: ['happy', 'excited'],
                })
            );
        });
    });

    describe('Retry Logic', () => {
        test('should retry failed requests with exponential backoff', async () => {
            const item: SyncQueueItem = {
                id: 'test-1',
                type: 'CREATE_MOMENT',
                data: { content: 'Test moment' },
                timestamp: Date.now(),
                retries: 0,
            };

            mockApiClient.post
                .mockRejectedValueOnce(new Error('Network error'))
                .mockRejectedValueOnce(new Error('Network error'))
                .mockResolvedValueOnce({ data: { id: 'server-id' } });

            await syncService.addToQueue(item);

            // Mock delay to avoid actual waiting
            jest.spyOn(global, 'setTimeout').mockImplementation(cb => cb());

            await syncService.processQueue();

            expect(mockApiClient.post).toHaveBeenCalledTimes(3);

            const queue = await syncService.getQueue();
            expect(queue).toHaveLength(0);
        });

        test('should give up after max retries', async () => {
            const item: SyncQueueItem = {
                id: 'test-1',
                type: 'CREATE_MOMENT',
                data: { content: 'Test moment' },
                timestamp: Date.now(),
                retries: 0,
            };

            mockApiClient.post.mockRejectedValue(new Error('Persistent error'));

            await syncService.addToQueue(item);

            // Mock delay
            jest.spyOn(global, 'setTimeout').mockImplementation(cb => cb());

            await syncService.processQueue();

            expect(mockApiClient.post).toHaveBeenCalledTimes(3); // Max retries = 3

            const queue = await syncService.getQueue();
            expect(queue).toHaveLength(0); // Item removed after max retries
        });

        test('should reset retry count on successful sync', async () => {
            const item: SyncQueueItem = {
                id: 'test-1',
                type: 'CREATE_MOMENT',
                data: { content: 'Test moment' },
                timestamp: Date.now(),
                retries: 2,
            };

            mockApiClient.post.mockResolvedValue({ data: { id: 'server-id' } });

            await syncService.addToQueue(item);
            await syncService.processQueue();

            const queue = await syncService.getQueue();
            expect(queue).toHaveLength(0);
        });
    });

    describe('Data Integrity', () => {
        test('should preserve data structure during sync', async () => {
            const complexItem: SyncQueueItem = {
                id: 'test-1',
                type: 'CREATE_MOMENT',
                data: {
                    content: 'Complex moment',
                    sphereId: 'sphere-1',
                    signals: {
                        emotions: ['happy', 'excited'],
                        tags: ['work', 'meeting'],
                        importance: 4,
                    },
                    media: [
                        {
                            type: 'AUDIO',
                            uri: 'file://audio.mp3',
                            duration: 120,
                        },
                    ],
                },
                timestamp: Date.now(),
                retries: 0,
            };

            mockApiClient.post.mockResolvedValue({ data: { id: 'server-id' } });

            await syncService.addToQueue(complexItem);
            await syncService.processQueue();

            expect(mockApiClient.post).toHaveBeenCalledWith(
                '/api/moments',
                complexItem.data
            );
        });

        test('should handle large data payloads', async () => {
            const largeContent = 'x'.repeat(1000000); // 1MB content
            const item: SyncQueueItem = {
                id: 'test-1',
                type: 'CREATE_MOMENT',
                data: { content: largeContent },
                timestamp: Date.now(),
                retries: 0,
            };

            mockApiClient.post.mockResolvedValue({ data: { id: 'server-id' } });

            await syncService.addToQueue(item);
            await syncService.processQueue();

            expect(mockApiClient.post).toHaveBeenCalledWith(
                '/api/moments',
                { content: largeContent }
            );
        });

        test('should validate data before adding to queue', async () => {
            const invalidItem = {
                id: 'test-1',
                type: 'INVALID_TYPE',
                data: null,
                timestamp: Date.now(),
                retries: 0,
            } as SyncQueueItem;

            await expect(syncService.addToQueue(invalidItem)).rejects.toThrow();
        });
    });

    describe('Storage Limits', () => {
        test('should enforce maximum queue size', async () => {
            const items: SyncQueueItem[] = Array.from({ length: 1001 }, (_, i) => ({
                id: `test-${i}`,
                type: 'CREATE_MOMENT',
                data: { content: `Item ${i}` },
                timestamp: Date.now() + i,
                retries: 0,
            }));

            // Add items one by one
            for (const item of items) {
                await syncService.addToQueue(item);
            }

            const queue = await syncService.getQueue();
            expect(queue.length).toBeLessThanOrEqual(1000); // Max queue size
        });

        test('should prioritize important items', async () => {
            const importantItem: SyncQueueItem = {
                id: 'important',
                type: 'CREATE_MOMENT',
                data: { content: 'Important', importance: 5 },
                timestamp: Date.now(),
                retries: 0,
                priority: 'HIGH',
            };

            const normalItem: SyncQueueItem = {
                id: 'normal',
                type: 'CREATE_MOMENT',
                data: { content: 'Normal', importance: 3 },
                timestamp: Date.now() + 1000,
                retries: 0,
                priority: 'NORMAL',
            };

            await syncService.addToQueue(normalItem);
            await syncService.addToQueue(importantItem);

            const queue = await syncService.getQueue();
            expect(queue[0].priority).toBe('HIGH');
            expect(queue[1].priority).toBe('NORMAL');
        });
    });

    describe('Sync Status', () => {
        test('should report correct sync status', async () => {
            const item: SyncQueueItem = {
                id: 'test-1',
                type: 'CREATE_MOMENT',
                data: { content: 'Test' },
                timestamp: Date.now(),
                retries: 0,
            };

            expect(await syncService.getSyncStatus()).toBe<SyncStatus>('SYNCED');

            await syncService.addToQueue(item);
            expect(await syncService.getSyncStatus()).toBe<SyncStatus>('PENDING');

            mockApiClient.post.mockRejectedValue(new Error('Network error'));
            await syncService.processQueue();
            expect(await syncService.getSyncStatus()).toBe<SyncStatus>('FAILED');

            mockApiClient.post.mockResolvedValue({ data: { id: 'server-id' } });
            await syncService.processQueue();
            expect(await syncService.getSyncStatus()).toBe<SyncStatus>('SYNCED');
        });

        test('should provide sync statistics', async () => {
            const items: SyncQueueItem[] = Array.from({ length: 5 }, (_, i) => ({
                id: `test-${i}`,
                type: 'CREATE_MOMENT',
                data: { content: `Item ${i}` },
                timestamp: Date.now() + i,
                retries: 0,
            }));

            for (const item of items) {
                await syncService.addToQueue(item);
            }

            const stats = await syncService.getSyncStats();
            expect(stats).toEqual({
                queueSize: 5,
                conflictsCount: 0,
                lastSyncTime: null,
                totalSynced: 0,
                totalFailed: 0,
            });
        });
    });

    describe('Background Sync', () => {
        test('should start background sync task', async () => {
            const startSpy = jest.spyOn(syncService, 'startBackgroundSync');

            await syncService.enableBackgroundSync();

            expect(startSpy).toHaveBeenCalled();
        });

        test('should sync in background when network available', async () => {
            NetInfo.fetch.mockResolvedValue({ isConnected: true });

            const processQueueSpy = jest.spyOn(syncService, 'processQueue');

            await syncService.startBackgroundSync();

            // Wait for background task to run
            await new Promise(resolve => setTimeout(resolve, 100));

            expect(processQueueSpy).toHaveBeenCalled();
        });

        test('should handle background sync errors gracefully', async () => {
            mockApiClient.post.mockRejectedValue(new Error('Background sync error'));

            const item: SyncQueueItem = {
                id: 'test-1',
                type: 'CREATE_MOMENT',
                data: { content: 'Test' },
                timestamp: Date.now(),
                retries: 0,
            };

            await syncService.addToQueue(item);
            await syncService.startBackgroundSync();

            // Should not throw unhandled exceptions
            await expect(syncService.processQueue()).resolves.not.toThrow();
        });
    });

    describe('Cleanup', () => {
        test('should clean up old completed items', async () => {
            const oldItem: SyncQueueItem = {
                id: 'old',
                type: 'CREATE_MOMENT',
                data: { content: 'Old' },
                timestamp: Date.now() - (7 * 24 * 60 * 60 * 1000), // 7 days ago
                retries: 0,
                completed: true,
                completedAt: Date.now() - (6 * 24 * 60 * 60 * 1000),
            };

            await syncService.addToQueue(oldItem);
            await syncService.cleanup();

            const queue = await syncService.getQueue();
            expect(queue).toHaveLength(0);
        });

        test('should clean up resolved conflicts', async () => {
            const resolvedConflict: SyncConflict = {
                id: 'resolved',
                itemId: 'test-1',
                type: 'VERSION_CONFLICT',
                localData: { content: 'Local' },
                serverData: { content: 'Server' },
                timestamp: Date.now() - (7 * 24 * 60 * 60 * 1000),
                resolved: true,
                resolvedAt: Date.now() - (6 * 24 * 60 * 60 * 1000),
            };

            await syncService.addConflict(resolvedConflict);
            await syncService.cleanup();

            const conflicts = await syncService.getConflicts();
            expect(conflicts).toHaveLength(0);
        });
    });
});
