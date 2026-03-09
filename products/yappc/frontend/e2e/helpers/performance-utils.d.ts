/**
 * Performance Test Utilities
 * Shared utilities for measuring and validating canvas performance
 */
import { Page } from '@playwright/test';
export interface PerformanceMetrics {
    renderTime: number;
    frameRate: number;
    memoryUsage: number;
    interactionLatency: number;
}
export interface PerformanceBudget {
    maxRenderTime: number;
    minFrameRate: number;
    maxMemoryIncrease: number;
    maxInteractionLatency: number;
}
export declare const DEFAULT_BUDGETS: PerformanceBudget;
/**
 * Measure canvas rendering performance
 */
export declare function measureRenderPerformance(page: Page, action: () => Promise<void>): Promise<{
    renderTime: number;
    memoryIncrease: number;
}>;
/**
 * Measure frame rate during canvas interaction
 */
export declare function measureFrameRate(page: Page, duration?: number): Promise<number>;
/**
 * Generate test canvas data with specified complexity
 */
export declare function generateCanvasData(nodeCount: number, connectionDensity?: number): {
    elements: {
        id: string;
        kind: string;
        type: string;
        position: {
            x: number;
            y: number;
        };
        size: {
            width: number;
            height: number;
        };
        data: {
            label: string;
        };
        style: {};
    }[];
    connections: {
        id: string;
        source: string;
        target: string;
    }[];
    sketches: never[];
};
/**
 * Seed canvas with performance test data
 */
export declare function seedPerformanceData(page: Page, nodeCount: number, connectionDensity?: number): Promise<void>;
/**
 * Wait for canvas rendering to complete
 */
export declare function waitForCanvasRender(page: Page, expectedElements: number, timeout?: number): Promise<void>;
/**
 * Validate performance against budget
 */
export declare function validatePerformance(metrics: Partial<PerformanceMetrics>, budget?: Partial<PerformanceBudget>): {
    passed: boolean;
    violations: string[];
};
//# sourceMappingURL=performance-utils.d.ts.map