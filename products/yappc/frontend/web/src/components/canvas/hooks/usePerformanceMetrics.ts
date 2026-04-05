/**
 * Canvas Performance Monitor Hook
 * 
 * Real-time FPS measurement using requestAnimationFrame. Replaces
 * the previously hardcoded "FPS: 60, Render: 12ms" static strings.
 * 
 * @doc.type hook
 * @doc.purpose Real-time canvas performance measurement
 * @doc.layer product
 * @doc.pattern Observer
 */

import { useState, useEffect, useRef, useCallback } from 'react';

export interface PerformanceMetrics {
    fps: number;
    renderTimeMs: number;
    nodeCount: number;
}

/**
 * Measures real FPS via requestAnimationFrame counter.
 * Updates metrics once per second to avoid excessive re-renders.
 */
export function usePerformanceMetrics(nodeCount: number): PerformanceMetrics {
    const [metrics, setMetrics] = useState<PerformanceMetrics>({
        fps: 0,
        renderTimeMs: 0,
        nodeCount: 0,
    });

    const frameCount = useRef(0);
    const lastTimestamp = useRef(performance.now());
    const rafId = useRef<number>(0);

    const measure = useCallback((timestamp: number) => {
        frameCount.current++;

        const elapsed = timestamp - lastTimestamp.current;
        if (elapsed >= 1000) {
            const fps = Math.round((frameCount.current * 1000) / elapsed);
            const renderTimeMs = Math.round(elapsed / frameCount.current * 100) / 100;

            setMetrics({
                fps,
                renderTimeMs,
                nodeCount,
            });

            frameCount.current = 0;
            lastTimestamp.current = timestamp;
        }

        rafId.current = requestAnimationFrame(measure);
    }, [nodeCount]);

    useEffect(() => {
        rafId.current = requestAnimationFrame(measure);
        return () => cancelAnimationFrame(rafId.current);
    }, [measure]);

    return metrics;
}
