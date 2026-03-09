import { describe, it, expect, beforeEach, vi } from 'vitest';
import { CanvasPerformanceMonitor } from '../CanvasPerformanceMonitor';
import { CanvasPersistence } from '../CanvasPersistence';
import type { CanvasState } from '@/components/canvas/workspace/canvasAtoms';

// Mock localStorage
const localStorageMock = (() => {
    let store: Record<string, string> = {};
    return {
        getItem: (key: string) => store[key] || null,
        setItem: (key: string, value: string) => { store[key] = value.toString(); },
        removeItem: (key: string) => { delete store[key]; },
        clear: () => { store = {}; }
    };
})();

Object.defineProperty(window, 'localStorage', { value: localStorageMock });

describe('Canvas Performance Benchmarks', () => {
    let monitor: CanvasPerformanceMonitor;
    let persistence: CanvasPersistence;

    beforeEach(() => {
        monitor = CanvasPerformanceMonitor.getInstance();
        monitor.clear();
        monitor.setEnabled(true);

        persistence = new CanvasPersistence({
            storage: 'localStorage',
            maxHistory: 50
        });

        localStorage.clear();
    });

    const generateLargeState = (nodeCount: number): CanvasState => {
        const elements = [];
        const connections = [];

        for (let i = 0; i < nodeCount; i++) {
            elements.push({
                id: `node-${i}`,
                type: 'component',
                position: { x: i * 100, y: i * 100 },
                data: { label: `Node ${i}`, someLargeData: 'x'.repeat(100) }
            });

            if (i > 0) {
                connections.push({
                    id: `edge-${i}`,
                    source: `node-${i - 1}`,
                    target: `node-${i}`,
                    type: 'default'
                });
            }
        }

        return {
            elements,
            connections,
            selectedElements: [],
            scale: 1,
            position: { x: 0, y: 0 }
        };
    };

    it('should measure save performance for 100 nodes', async () => {
        const state = generateLargeState(100);

        await monitor.measureAsync('save-100-nodes', async () => {
            await persistence.save('proj-1', 'canvas-1', state);
        });

        const stats = monitor.getStats('save-100-nodes');
        console.log('Save 100 nodes:', stats?.avg.toFixed(2), 'ms');

        expect(stats?.avg).toBeLessThan(100); // Target: < 100ms
    });

    it('should measure save performance for 500 nodes', async () => {
        const state = generateLargeState(500);

        await monitor.measureAsync('save-500-nodes', async () => {
            await persistence.save('proj-1', 'canvas-1', state);
        });

        const stats = monitor.getStats('save-500-nodes');
        console.log('Save 500 nodes:', stats?.avg.toFixed(2), 'ms');

        expect(stats?.avg).toBeLessThan(200); // Target: < 200ms
    });

    it('should measure save performance for 1000 nodes', async () => {
        const state = generateLargeState(1000);

        await monitor.measureAsync('save-1000-nodes', async () => {
            await persistence.save('proj-1', 'canvas-1', state);
        });

        const stats = monitor.getStats('save-1000-nodes');
        console.log('Save 1000 nodes:', stats?.avg.toFixed(2), 'ms');

        // No strict assertion, just logging for baseline
    });

    it('should measure load performance for 500 nodes', async () => {
        const state = generateLargeState(500);
        await persistence.save('proj-1', 'canvas-1', state);

        await monitor.measureAsync('load-500-nodes', async () => {
            await persistence.load('proj-1', 'canvas-1');
        });

        const stats = monitor.getStats('load-500-nodes');
        console.log('Load 500 nodes:', stats?.avg.toFixed(2), 'ms');

        expect(stats?.avg).toBeLessThan(100); // Target: < 100ms
    });
});
