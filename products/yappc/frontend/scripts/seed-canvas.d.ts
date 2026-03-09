#!/usr/bin/env node
/**
 * Canvas Seed Script - Generate demo canvas data
 */
import { CanvasState } from '../apps/web/src/components/canvas/workspace/canvasAtoms';
interface SeedOptions {
    nodeCount?: number;
    connectionDensity?: number;
    includeShapes?: boolean;
    includeStrokes?: boolean;
    canvasSize?: {
        width: number;
        height: number;
    };
}
export declare function generateSeedData(options?: SeedOptions): CanvasState;
export declare const seedScenarios: {
    small: () => CanvasState;
    medium: () => CanvasState;
    large: () => CanvasState;
    performance: () => CanvasState;
    microservices: () => CanvasState;
};
export default generateSeedData;
//# sourceMappingURL=seed-canvas.d.ts.map