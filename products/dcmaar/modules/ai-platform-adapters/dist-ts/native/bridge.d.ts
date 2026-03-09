export interface BridgeStats {
    batchesProcessed: number;
    eventsProcessed: number;
    usingRealClient: boolean;
    startedAt: string;
    uptimeMs: number;
    lastError?: string | null;
}
export interface NativeBridge {
    submitBatch(batchJson: string): Promise<number>;
    submitEvent(eventJson: string): Promise<void>;
    getStats(): Promise<string> | string;
    healthCheck(): Promise<boolean>;
    getVersion(): string;
}
export declare function createNativeBridge(): NativeBridge | null;
export declare function getLastBridgeError(): unknown | null;
export declare function parseBridgeStats(bridge: NativeBridge): Promise<BridgeStats | null>;
//# sourceMappingURL=bridge.d.ts.map