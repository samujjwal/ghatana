import { Page } from '@playwright/test';
/**
 * E2E Test Isolation Utilities
 *
 * Provides utilities to ensure clean state between tests by:
 * - Clearing localStorage and sessionStorage
 * - Resetting Jotai atoms
 * - Clearing MSW handlers
 * - Resetting canvas state
 */
declare global {
    interface Window {
        __JOTAI_STORE__?: Map<unknown, unknown>;
        __TEST_MOCKS__?: Map<string, unknown>;
        __RF_INSTANCE__?: {
            setNodes: (nodes: unknown[]) => void;
            setEdges: (edges: unknown[]) => void;
            fitView: () => void;
        };
        [key: string]: unknown;
    }
}
export interface TestIsolationOptions {
    clearStorage?: boolean;
    resetAtoms?: boolean;
    clearMSW?: boolean;
    resetCanvas?: boolean;
    seedData?: boolean;
}
/**
 * Clean test state including browser storage, Jotai atoms, and MSW handlers
 */
export declare function cleanTestState(page: Page): Promise<void>;
/**
 * Reset Jotai atoms to their initial state
 */
export declare function resetJotaiAtoms(page: Page): Promise<void>;
/**
 * Clear MSW request handlers and reset mocks
 */
export declare function clearMSWHandlers(page: Page): Promise<void>;
/**
 * Reset canvas-specific state
 */
export declare function resetCanvasState(page: Page): Promise<void>;
/**
 * Seed deterministic test data for canvas tests
 */
export declare function seedCanvasTestData(page: Page, scenario?: string): Promise<void>;
/**
 * Wait for canvas to be fully loaded and ready
 */
export declare function waitForCanvasReady(page: Page, timeout?: number): Promise<void>;
/**
 * Comprehensive test setup that ensures clean, predictable state
 */
export declare function setupTest(page: Page, options?: TestIsolationOptions & {
    seedScenario?: string;
    url?: string;
}): Promise<void>;
/**
 * Test teardown - clean up after test
 */
export declare function teardownTest(page: Page): Promise<void>;
//# sourceMappingURL=test-isolation.d.ts.map