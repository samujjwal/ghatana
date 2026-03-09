/**
 * Offline Sync Integration Tests
 * Tests complete offline-to-online sync scenarios
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { renderHook, act } from '@testing-library/react-hooks';
import { useOfflineSync } from '../hooks/useOfflineSync';
import { ApiProvider } from '../contexts/ApiContext';

// Mock NetInfo
jest.mock('@react-native-community/netinfo', () => ({
    fetch: jest.fn(),
    addEventListener: jest.fn(),
}));

// Mock AsyncStorage
jest.mock('@react-native-async-storage/async-storage', () => ({
    getItem: jest.fn(),
    setItem: jest.fn(),
    removeItem: jest.fn(),
    clear: jest.fn(),
}));

describe('Offline Sync Integration', () => {
    const mockApiClient = {
        post: jest.fn(),
        get: jest.fn(),
        put: jest.fn(),
        delete: jest.fn(),
    };

    const wrapper = ({ children }: any) => (
        <ApiProvider client={mockApiClient}>
            {children}
        </ApiProvider>
    );

    beforeEach(() => {
        jest.clearAllMocks();
        AsyncStorage.clear.mockResolvedValue();
    });

    describe('Complete Offline Flow', () => {
        test('should capture moments offline and sync when online', async () => {
            // Start offline
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: false });

            const { result } = renderHook(() => useOfflineSync(), { wrapper });

            // Create moment while offline
            const momentData = {
                content: 'Offline moment',
                sphereId: 'sphere-1',
                signals: {
                    emotions: ['happy'],
                    tags: ['test'],
                },
            };

            await act(async () => {
                await result.current.createMoment(momentData);
            });

            // Verify it's queued for sync
            expect(await result.current.getQueueSize()).toBe(1);

            // Come online
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: true });

            // Mock successful API call
            mockApiClient.post.mockResolvedValue({
                data: { id: 'server-moment-id', ...momentData },
            });

            // Trigger sync
            await act(async () => {
                await result.current.syncNow();
            });

            // Verify moment was synced
            expect(mockApiClient.post).toHaveBeenCalledWith('/api/moments', momentData);
            expect(await result.current.getQueueSize()).toBe(0);
        });

        test('should handle multiple offline operations', async () => {
            // Start offline
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: false });

            const { result } = renderHook(() => useOfflineSync(), { wrapper });

            // Create multiple moments offline
            const moments = [
                { content: 'First moment', sphereId: 'sphere-1' },
                { content: 'Second moment', sphereId: 'sphere-1' },
                { content: 'Third moment', sphereId: 'sphere-2' },
            ];

            for (const moment of moments) {
                await act(async () => {
                    await result.current.createMoment(moment);
                });
            }

            expect(await result.current.getQueueSize()).toBe(3);

            // Come online
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: true });

            // Mock API responses
            mockApiClient.post.mockResolvedValue((data) => ({
                data: { id: `moment-${Math.random()}`, ...data },
            }));

            // Sync all
            await act(async () => {
                await result.current.syncNow();
            });

            expect(mockApiClient.post).toHaveBeenCalledTimes(3);
            expect(await result.current.getQueueSize()).toBe(0);
        });
    });

    describe('Conflict Resolution Integration', () => {
        test('should handle version conflicts during sync', async () => {
            // Create moment offline
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: false });

            const { result } = renderHook(() => useOfflineSync(), { wrapper });

            const momentData = {
                id: 'moment-1',
                content: 'Local version',
                sphereId: 'sphere-1',
                version: 1,
            };

            await act(async () => {
                await result.current.updateMoment(momentData);
            });

            // Come online but server has newer version
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: true });

            mockApiClient.put.mockRejectedValue({
                response: {
                    status: 409,
                    data: {
                        error: 'VERSION_CONFLICT',
                        serverData: {
                            id: 'moment-1',
                            content: 'Server version',
                            version: 2,
                        },
                    },
                },
            });

            // Attempt sync
            await act(async () => {
                await result.current.syncNow();
            });

            // Verify conflict was created
            const conflicts = await result.current.getConflicts();
            expect(conflicts).toHaveLength(1);
            expect(conflicts[0].type).toBe('VERSION_CONFLICT');

            // Resolve conflict with server wins
            mockApiClient.put.mockResolvedValue({
                data: { id: 'moment-1', content: 'Server version', version: 2 },
            });

            await act(async () => {
                await result.current.resolveConflict(conflicts[0].id, 'SERVER_WINS');
            });

            expect(await result.current.getConflicts()).toHaveLength(0);
        });
    });

    describe('Network Interruption Handling', () => {
        test('should handle network dropping during sync', async () => {
            const { result } = renderHook(() => useOfflineSync(), { wrapper });

            // Start with network
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: true });

            // Queue some items
            await act(async () => {
                await result.current.createMoment({ content: 'Test 1', sphereId: 'sphere-1' });
                await result.current.createMoment({ content: 'Test 2', sphereId: 'sphere-1' });
            });

            // Start sync but network drops after first success
            mockApiClient.post
                .mockResolvedValueOnce({ data: { id: 'moment-1' } })
                .mockRejectedValueOnce(new Error('Network dropped'));

            await act(async () => {
                await result.current.syncNow();
            });

            // One should be synced, one should remain in queue
            expect(mockApiClient.post).toHaveBeenCalledTimes(2);
            expect(await result.current.getQueueSize()).toBe(1);
        });

        test('should resume sync when network returns', async () => {
            const { result } = renderHook(() => useOfflineSync(), { wrapper });

            // Start offline
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: false });

            // Create moment offline
            await act(async () => {
                await result.current.createMoment({ content: 'Test', sphereId: 'sphere-1' });
            });

            // Network returns
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: true });

            mockApiClient.post.mockResolvedValue({ data: { id: 'moment-1' } });

            // Auto-sync should trigger
            await act(async () => {
                // Simulate network change event
                const netInfo = require('@react-native-community/netinfo');
                const callback = netInfo.addEventListener.mock.calls[0][0];
                await callback({ isConnected: true });
            });

            expect(await result.current.getQueueSize()).toBe(0);
        });
    });

    describe('Data Persistence', () => {
        test('should persist queue across app restarts', async () => {
            // Simulate app restart by clearing and repopulating AsyncStorage
            const queuedItem = {
                id: 'test-1',
                type: 'CREATE_MOMENT',
                data: { content: 'Persisted moment', sphereId: 'sphere-1' },
                timestamp: Date.now(),
                retries: 0,
            };

            AsyncStorage.getItem.mockResolvedValue(JSON.stringify([queuedItem]));

            const { result } = renderHook(() => useOfflineSync(), { wrapper });

            // Queue should be restored
            expect(await result.current.getQueueSize()).toBe(1);

            // Sync should work with restored queue
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: true });

            mockApiClient.post.mockResolvedValue({ data: { id: 'moment-1' } });

            await act(async () => {
                await result.current.syncNow();
            });

            expect(await result.current.getQueueSize()).toBe(0);
        });

        test('should handle corrupted queue data', async () => {
            // Simulate corrupted data in AsyncStorage
            AsyncStorage.getItem.mockResolvedValue('invalid-json');

            const { result } = renderHook(() => useOfflineSync(), { wrapper });

            // Should handle gracefully and start with empty queue
            expect(await result.current.getQueueSize()).toBe(0);
        });
    });

    describe('Performance Tests', () => {
        test('should handle large queue efficiently', async () => {
            const { result } = renderHook(() => useOfflineSync(), { wrapper });

            // Add 100 items to queue
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: false });

            const startTime = Date.now();

            for (let i = 0; i < 100; i++) {
                await act(async () => {
                    await result.current.createMoment({
                        content: `Moment ${i}`,
                        sphereId: 'sphere-1',
                    });
                });
            }

            const queueTime = Date.now() - startTime;
            expect(queueTime).toBeLessThan(5000); // Should complete within 5 seconds
            expect(await result.current.getQueueSize()).toBe(100);

            // Sync all items
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: true });

            mockApiClient.post.mockResolvedValue({ data: { id: 'moment-id' } });

            const syncStartTime = Date.now();

            await act(async () => {
                await result.current.syncNow();
            });

            const syncTime = Date.now() - syncStartTime;
            expect(syncTime).toBeLessThan(10000); // Should sync within 10 seconds
            expect(await result.current.getQueueSize()).toBe(0);
        });

        test('should not block UI during sync', async () => {
            const { result } = renderHook(() => useOfflineSync(), { wrapper });

            // Start sync in background
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: true });

            // Mock slow API response
            mockApiClient.post.mockImplementation(() =>
                new Promise(resolve => setTimeout(() => resolve({ data: { id: 'moment-id' } }), 100))
            );

            // Add items and start sync
            await act(async () => {
                await result.current.createMoment({ content: 'Test', sphereId: 'sphere-1' });
                result.current.syncNow(); // Don't await
            });

            // Should be able to perform other operations while syncing
            await act(async () => {
                const status = await result.current.getSyncStatus();
                expect(status).toBe('SYNCING');
            });

            // Wait for sync to complete
            await new Promise(resolve => setTimeout(resolve, 200));

            expect(await result.current.getSyncStatus()).toBe('SYNCED');
        });
    });

    describe('Error Recovery', () => {
        test('should recover from API errors during sync', async () => {
            const { result } = renderHook(() => useOfflineSync(), { wrapper });

            // Add item to queue
            await act(async () => {
                await result.current.createMoment({ content: 'Test', sphereId: 'sphere-1' });
            });

            // First sync attempt fails
            require('@react-native-community/netinfo').fetch
                .mockResolvedValue({ isConnected: true });

            mockApiClient.post.mockRejectedValueOnce(new Error('API Error'));

            await act(async () => {
                await result.current.syncNow();
            });

            // Item should still be in queue with increased retry count
            expect(await result.current.getQueueSize()).toBe(1);

            // Second attempt succeeds
            mockApiClient.post.mockResolvedValueOnce({ data: { id: 'moment-id' } });

            await act(async () => {
                await result.current.syncNow();
            });

            expect(await result.current.getQueueSize()).toBe(0);
        });

        test('should handle storage errors gracefully', async () => {
            // Simulate storage error
            AsyncStorage.setItem.mockRejectedValue(new Error('Storage full'));

            const { result } = renderHook(() => useOfflineSync(), { wrapper });

            // Should not crash when storage fails
            await act(async () => {
                await expect(
                    result.current.createMoment({ content: 'Test', sphereId: 'sphere-1' })
                ).rejects.toThrow();
            });
        });
    });
});
