/**
 * Compression Service Tests
 * 
 * Unit tests for CompressionService including compression ratio,
 * decompression accuracy, and performance.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { CompressionService } from '../storage/CompressionService';
import type { CanvasState } from '../../../components/canvas/workspace/canvasAtoms';

describe('CompressionService', () => {
    let service: CompressionService;

    const mockCanvasState: CanvasState = {
        elements: [
            {
                id: 'node-1',
                type: 'component',
                position: { x: 100, y: 100 },
                data: { label: 'Test Node 1' },
            },
            {
                id: 'node-2',
                type: 'api',
                position: { x: 300, y: 100 },
                data: { label: 'Test Node 2' },
            },
            {
                id: 'node-3',
                type: 'data',
                position: { x: 200, y: 300 },
                data: { label: 'Test Node 3' },
            },
        ],
        connections: [
            { id: 'edge-1', source: 'node-1', target: 'node-2', type: 'default' },
            { id: 'edge-2', source: 'node-2', target: 'node-3', type: 'default' },
        ],
    };

    beforeEach(() => {
        service = new CompressionService();
    });

    describe('compressState', () => {
        it('should compress canvas state successfully', async () => {
            const { data, stats } = await service.compressState(mockCanvasState);

            expect(data).toBeInstanceOf(Uint8Array);
            expect(data.byteLength).toBeGreaterThan(0);
            expect(stats.originalSize).toBeGreaterThan(0);
            expect(stats.compressedSize).toBeGreaterThan(0);
        });

        it('should achieve compression ratio > 1', async () => {
            const { stats } = await service.compressState(mockCanvasState);

            // Compression ratio should be greater than 1 (meaning smaller size)
            expect(stats.compressionRatio).toBeGreaterThanOrEqual(1);
        });

        it('should track compression duration', async () => {
            const { stats } = await service.compressState(mockCanvasState);

            expect(stats.duration).toBeGreaterThanOrEqual(0);
        });
    });

    describe('decompressState', () => {
        it('should decompress to original state', async () => {
            const { data } = await service.compressState(mockCanvasState);
            const decompressed = await service.decompressState(data);

            expect(decompressed).toEqual(mockCanvasState);
        });

        it('should handle large canvas states', async () => {
            // Create large canvas with many nodes
            const largeState: CanvasState = {
                elements: Array.from({ length: 1000 }, (_, i) => ({
                    id: `node-${i}`,
                    type: 'component',
                    position: { x: Math.random() * 1000, y: Math.random() * 1000 },
                    data: { label: `Node ${i}`, description: 'A'.repeat(100) },
                })),
                connections: [],
            };

            const { data, stats } = await service.compressState(largeState);
            const decompressed = await service.decompressState(data);

            expect(decompressed).toEqual(largeState);
            expect(stats.compressionRatio).toBeGreaterThan(1);
        });
    });

    describe('compressSnapshot', () => {
        it('should compress snapshot with metadata', async () => {
            const snapshot = {
                id: 'snap-1',
                projectId: 'proj-1',
                canvasId: 'canvas-1',
                version: 1,
                timestamp: Date.now(),
                data: mockCanvasState,
                metadata: {
                    author: 'test-user',
                    description: 'Test snapshot',
                },
            };

            const { compressed, stats } = await service.compressSnapshot(snapshot);

            expect(compressed.id).toBe('snap-1');
            expect(compressed.metadata?.compressed).toBe(true);
            expect(compressed.metadata?.originalSize).toBe(stats.originalSize);
            expect(compressed.metadata?.compressedSize).toBe(stats.compressedSize);
        });
    });

    describe('decompressSnapshot', () => {
        it('should decompress snapshot correctly', async () => {
            const snapshot = {
                id: 'snap-1',
                projectId: 'proj-1',
                canvasId: 'canvas-1',
                version: 1,
                timestamp: Date.now(),
                data: mockCanvasState,
                metadata: {
                    author: 'test-user',
                },
            };

            const { compressed } = await service.compressSnapshot(snapshot);
            const decompressed = await service.decompressSnapshot(compressed);

            expect(decompressed.data).toEqual(mockCanvasState);
            expect(decompressed.id).toBe('snap-1');
            expect(decompressed.metadata?.compressed).toBe(false);
        });

        it('should return uncompressed snapshot as-is', async () => {
            const snapshot = {
                id: 'snap-1',
                projectId: 'proj-1',
                canvasId: 'canvas-1',
                version: 1,
                timestamp: Date.now(),
                data: mockCanvasState,
                metadata: {
                    compressed: false,
                },
            };

            const decompressed = await service.decompressSnapshot(snapshot);

            expect(decompressed).toEqual(snapshot);
        });
    });

    describe('compression algorithms', () => {
        it('should work with gzip algorithm', async () => {
            const gzipService = new CompressionService({ algorithm: 'gzip' });
            const { data } = await gzipService.compressState(mockCanvasState);
            const decompressed = await gzipService.decompressState(data);

            expect(decompressed).toEqual(mockCanvasState);
        });

        it('should work with deflate algorithm', async () => {
            const deflateService = new CompressionService({ algorithm: 'deflate' });
            const { data } = await deflateService.compressState(mockCanvasState);
            const decompressed = await deflateService.decompressState(data);

            expect(decompressed).toEqual(mockCanvasState);
        });
    });

    describe('getCompressionStats', () => {
        it('should return accurate statistics', async () => {
            const stats = await service.getCompressionStats(mockCanvasState);

            expect(stats.originalSize).toBeGreaterThan(0);
            expect(stats.compressedSize).toBeGreaterThan(0);
            expect(stats.compressionRatio).toBeGreaterThanOrEqual(1);
            expect(stats.duration).toBeGreaterThanOrEqual(0);
        });
    });

    describe('performance', () => {
        it('should compress within reasonable time', async () => {
            const start = performance.now();
            await service.compressState(mockCanvasState);
            const duration = performance.now() - start;

            // Should complete within 100ms for small canvas
            expect(duration).toBeLessThan(100);
        });

        it('should decompress within reasonable time', async () => {
            const { data } = await service.compressState(mockCanvasState);

            const start = performance.now();
            await service.decompressState(data);
            const duration = performance.now() - start;

            // Should complete within 50ms for small canvas
            expect(duration).toBeLessThan(50);
        });
    });
});
