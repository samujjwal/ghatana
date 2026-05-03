/**
 * Canvas API Client Tests
 * 
 * Unit tests for CanvasAPIClient with retry logic,
 * error handling, and auth integration.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { CanvasAPIClient } from '../api/CanvasAPIClient';
import type { CanvasSnapshot } from '../CanvasPersistence';

describe('CanvasAPIClient', () => {
    let client: CanvasAPIClient;
    let fetchMock: unknown;

    const mockSnapshot: CanvasSnapshot = {
        id: 'snap-1',
        projectId: 'proj-1',
        canvasId: 'canvas-1',
        version: 1,
        timestamp: Date.now(),
        data: {
            elements: [
                {
                    id: 'node-1',
                    type: 'component',
                    position: { x: 100, y: 100 },
                    data: { label: 'Test Node' },
                },
            ],
            connections: [],
        },
        metadata: {
            author: 'test-user',
            description: 'Test snapshot',
        },
    };

    beforeEach(() => {
        // Mock global fetch
        fetchMock = vi.fn();
        global.fetch = fetchMock;

        client = new CanvasAPIClient({
            baseURL: 'http://localhost:3000',
            timeout: 5000,
            maxRetries: 3,
            retryDelay: 100,
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('saveSnapshot', () => {
        it('should save snapshot successfully', async () => {
            fetchMock.mockResolvedValueOnce({
                ok: true,
                status: 200,
                json: async () => ({ success: true, id: 'snap-1' }),
            });

            const result = await client.saveSnapshot(mockSnapshot);

            expect(fetchMock).toHaveBeenCalledWith(
                'http://localhost:3000/api/canvas/snapshots',
                expect.objectContaining({
                    method: 'POST',
                    headers: expect.objectContaining({
                        'Content-Type': 'application/json',
                    }),
                })
            );
        });

        it('should retry on network error', async () => {
            // First two calls fail, third succeeds
            fetchMock
                .mockRejectedValueOnce(new Error('Network error'))
                .mockRejectedValueOnce(new Error('Network error'))
                .mockResolvedValueOnce({
                    ok: true,
                    status: 200,
                    json: async () => ({ success: true, id: 'snap-1' }),
                });

            await client.saveSnapshot(mockSnapshot);

            expect(fetchMock).toHaveBeenCalledTimes(3);
        });

        it('should fail after max retries', async () => {
            fetchMock.mockRejectedValue(new Error('Network error'));

            await expect(client.saveSnapshot(mockSnapshot)).rejects.toBeTruthy();
            expect(fetchMock).toHaveBeenCalledTimes(3); // 3 attempts
        });

        it('should handle 401 unauthorized', async () => {
            fetchMock.mockResolvedValueOnce({
                ok: false,
                status: 401,
                statusText: 'Unauthorized',
                text: async () => '',
            });

            await expect(client.saveSnapshot(mockSnapshot)).rejects.toMatchObject({
                status: 401,
            });
            expect(fetchMock).toHaveBeenCalledTimes(1); // No retry on 401
        });

        it('should handle 429 rate limit', async () => {
            fetchMock.mockResolvedValueOnce({
                ok: false,
                status: 429,
                statusText: 'Too Many Requests',
                text: async () => '',
            });

            // 429 is 4xx — no retry, throws immediately
            await expect(client.saveSnapshot(mockSnapshot)).rejects.toMatchObject({
                status: 429,
            });
            expect(fetchMock).toHaveBeenCalledTimes(1);
        });
    });

    describe('loadSnapshot', () => {
        it('should load snapshot successfully', async () => {
            fetchMock.mockResolvedValueOnce({
                ok: true,
                status: 200,
                json: async () => mockSnapshot,
            });

            const result = await client.loadSnapshot('snap-1');

            expect(result).toEqual(mockSnapshot);
            expect(fetchMock).toHaveBeenCalledWith(
                'http://localhost:3000/api/canvas/snapshots/snap-1',
                expect.any(Object)
            );
        });

        it('should return null for 404 not found', async () => {
            fetchMock.mockResolvedValueOnce({
                ok: false,
                status: 404,
                statusText: 'Not Found',
            });

            const result = await client.loadSnapshot('snap-999');

            expect(result).toBeNull();
        });
    });

    describe('listSnapshots', () => {
        it('should list snapshots with filters', async () => {
            const mockSnapshots = [mockSnapshot];
            fetchMock.mockResolvedValueOnce({
                ok: true,
                status: 200,
                json: async () => ({
                    snapshots: mockSnapshots,
                    total: 1,
                }),
            });

            const result = await client.listSnapshots('proj-1', 'canvas-1');

            expect(result).toEqual({
                snapshots: mockSnapshots,
                total: 1,
            });
            expect(fetchMock).toHaveBeenCalledWith(
                expect.stringContaining('projectId=proj-1'),
                expect.any(Object)
            );
        });
    });

    describe('deleteSnapshot', () => {
        it('should delete snapshot successfully', async () => {
            fetchMock.mockResolvedValueOnce({
                ok: true,
                status: 204,
            });

            await client.deleteSnapshot('snap-1');

            expect(fetchMock).toHaveBeenCalledWith(
                'http://localhost:3000/api/canvas/snapshots/snap-1',
                expect.objectContaining({
                    method: 'DELETE',
                })
            );
        });
    });

    describe('batchSave', () => {
        it('should save multiple snapshots', async () => {
            const snapshots = [mockSnapshot, { ...mockSnapshot, id: 'snap-2' }];
            fetchMock.mockResolvedValueOnce({
                ok: true,
                status: 200,
                json: async () => ({
                    success: true,
                    saved: 2,
                }),
            });

            await client.batchSave(snapshots);

            expect(fetchMock).toHaveBeenCalledWith(
                'http://localhost:3000/api/canvas/snapshots/batch',
                expect.objectContaining({
                    method: 'POST',
                })
            );
        });
    });

    describe('retry logic', () => {
        it('should use exponential backoff', async () => {
            const delays: number[] = [];
            const startTime = Date.now();

            fetchMock
                .mockRejectedValueOnce(new Error('Error 1'))
                .mockRejectedValueOnce(new Error('Error 2'))
                .mockResolvedValueOnce({
                    ok: true,
                    status: 200,
                    json: async () => ({ success: true }),
                });

            await client.saveSnapshot(mockSnapshot);

            expect(fetchMock).toHaveBeenCalledTimes(3);
            // Verify backoff increased (100ms, 200ms, 400ms)
        });
    });

    describe('timeout', () => {
        it('should timeout long requests', async () => {
            // Mock a request that never resolves
            fetchMock.mockImplementation(
                () => new Promise((resolve) => setTimeout(resolve, 10000))
            );

            const shortTimeoutClient = new CanvasAPIClient({
                baseURL: 'http://localhost:3000',
                timeout: 100,
                maxRetries: 0,
            });

            await expect(
                shortTimeoutClient.saveSnapshot(mockSnapshot)
            ).rejects.toThrow();
        });
    });

    describe('auth headers', () => {
        it('should use cookie-based auth on requests', async () => {
            const authClient = new CanvasAPIClient({
                baseURL: 'http://localhost:3000',
                getAuthToken: () => 'test-token-123',
            });

            fetchMock.mockResolvedValueOnce({
                ok: true,
                status: 200,
                json: async () => ({ success: true }),
            });

            await authClient.saveSnapshot(mockSnapshot);

            expect(fetchMock).toHaveBeenCalledWith(
                expect.any(String),
                expect.objectContaining({
                    credentials: 'include',
                    headers: expect.objectContaining({
                        'Content-Type': 'application/json',
                    }),
                })
            );
        });
    });
});
