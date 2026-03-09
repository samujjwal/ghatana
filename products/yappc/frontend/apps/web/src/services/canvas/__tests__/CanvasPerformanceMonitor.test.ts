import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { CanvasPerformanceMonitor } from '../CanvasPerformanceMonitor';

describe('CanvasPerformanceMonitor', () => {
    let monitor: CanvasPerformanceMonitor;

    beforeEach(() => {
        // Reset singleton for testing
        monitor = CanvasPerformanceMonitor.getInstance();
        monitor.clear();
        monitor.setEnabled(true);
    });

    it('should measure synchronous operations', () => {
        const result = monitor.measure('test-sync', () => {
            let sum = 0;
            for (let i = 0; i < 1000000; i++) sum += i;
            return sum;
        });

        expect(result).toBeGreaterThan(0);

        const stats = monitor.getStats('test-sync');
        expect(stats).not.toBeNull();
        expect(stats?.count).toBe(1);
        expect(stats?.avg).toBeGreaterThan(0);
    });

    it('should measure asynchronous operations', async () => {
        const result = await monitor.measureAsync('test-async', async () => {
            await new Promise(resolve => setTimeout(resolve, 10));
            return 'done';
        });

        expect(result).toBe('done');

        const stats = monitor.getStats('test-async');
        expect(stats).not.toBeNull();
        expect(stats?.count).toBe(1);
        expect(stats?.avg).toBeGreaterThanOrEqual(10); // Should be at least 10ms
    });

    it('should calculate correct statistics', () => {
        // Record some fake measurements
        monitor.record('test-stats', 10);
        monitor.record('test-stats', 20);
        monitor.record('test-stats', 30);
        monitor.record('test-stats', 40);
        monitor.record('test-stats', 100); // Outlier

        const stats = monitor.getStats('test-stats');

        expect(stats).not.toBeNull();
        expect(stats?.count).toBe(5);
        expect(stats?.min).toBe(10);
        expect(stats?.max).toBe(100);
        expect(stats?.avg).toBe(40); // (10+20+30+40+100)/5 = 40
        expect(stats?.last).toBe(100);
    });

    it('should respect enabled/disabled state', () => {
        monitor.setEnabled(false);

        monitor.measure('test-disabled', () => {
            return 'result';
        });

        const stats = monitor.getStats('test-disabled');
        expect(stats).toBeNull();
    });

    it('should limit sample size', () => {
        for (let i = 0; i < 1100; i++) {
            monitor.record('test-limit', i);
        }

        const stats = monitor.getStats('test-limit');
        expect(stats?.count).toBe(1000);
        // Should have dropped the first 100 samples (0-99)
        // So min should be around 100
        expect(stats?.min).toBeGreaterThanOrEqual(100);
    });
});
