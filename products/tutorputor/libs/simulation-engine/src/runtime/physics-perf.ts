/**
 * Physics Runtime Performance Utilities
 *
 * Provides cache warming, worker offload, and adaptive sampling
 * for physics simulations.
 *
 * @doc.type module
 * @doc.purpose Performance optimization utilities for physics simulations
 * @doc.layer product
 * @doc.pattern Performance
 */

import type {
    SimulationManifest,
    SimKeyframe,
    SimulationId,
} from '@ghatana/tutorputor-contracts/v1/simulation/types';
import type { TenantId } from '@ghatana/tutorputor-contracts/v1/types';

// =============================================================================
// Types
// =============================================================================

/**
 * Cache entry for simulation keyframes
 */
export interface CacheEntry {
    manifestHash: string;
    keyframes: SimKeyframe[];
    createdAt: Date;
    accessCount: number;
    lastAccessedAt: Date;
    ttlSeconds: number;
}

/**
 * Cache warming configuration
 */
export interface CacheWarmingConfig {
    /** Maximum number of manifests to warm per cycle */
    maxManifestsPerCycle: number;
    /** Minimum access count threshold for warming */
    accessThreshold: number;
    /** TTL for warmed cache entries (seconds) */
    warmCacheTTL: number;
    /** Cycle interval (milliseconds) */
    cycleIntervalMs: number;
}

/**
 * Worker pool configuration
 */
export interface WorkerPoolConfig {
    /** Number of worker threads */
    poolSize: number;
    /** Maximum concurrent simulations per worker */
    maxConcurrentPerWorker: number;
    /** Timeout for individual simulations (ms) */
    simulationTimeoutMs: number;
    /** Use WASM when available */
    preferWasm: boolean;
}

/**
 * Adaptive sampling configuration
 */
export interface AdaptiveSamplingConfig {
    /** Base sampling rate (frames per second) */
    baseSamplingRate: number;
    /** Minimum sampling rate */
    minSamplingRate: number;
    /** Maximum sampling rate */
    maxSamplingRate: number;
    /** Velocity threshold for increased sampling */
    velocityThreshold: number;
    /** Collision detection sampling boost factor */
    collisionSamplingBoost: number;
}

/**
 * Cache statistics
 */
export interface CacheStats {
    totalEntries: number;
    hitCount: number;
    missCount: number;
    hitRate: number;
    warmCount: number;
    evictionCount: number;
    totalSizeBytes: number;
}

/**
 * Worker status
 */
export interface WorkerStatus {
    id: string;
    busy: boolean;
    currentSimulationId?: SimulationId;
    completedCount: number;
    errorCount: number;
    avgExecutionTimeMs: number;
}

// =============================================================================
// Cache Warming Service
// =============================================================================

/**
 * Physics simulation cache warming service
 *
 * @doc.type class
 * @doc.purpose Pre-compute and cache hot physics simulations
 * @doc.layer product
 * @doc.pattern CacheWarming
 */
export class PhysicsCacheWarmingService {
    private cache: Map<string, CacheEntry> = new Map();
    private accessLog: Map<string, number> = new Map();
    private warmingTimer: NodeJS.Timeout | null = null;
    private stats: CacheStats = {
        totalEntries: 0,
        hitCount: 0,
        missCount: 0,
        hitRate: 0,
        warmCount: 0,
        evictionCount: 0,
        totalSizeBytes: 0,
    };

    constructor(private config: CacheWarmingConfig) { }

    /**
     * Start the cache warming cycle
     */
    start(): void {
        if (this.warmingTimer) {
            return;
        }

        this.warmingTimer = setInterval(() => {
            this.runWarmingCycle();
        }, this.config.cycleIntervalMs);
    }

    /**
     * Stop the cache warming cycle
     */
    stop(): void {
        if (this.warmingTimer) {
            clearInterval(this.warmingTimer);
            this.warmingTimer = null;
        }
    }

    /**
     * Get cached keyframes for a manifest
     */
    get(manifestHash: string): SimKeyframe[] | null {
        const entry = this.cache.get(manifestHash);

        if (entry) {
            entry.accessCount++;
            entry.lastAccessedAt = new Date();
            this.stats.hitCount++;
            this.updateHitRate();
            return entry.keyframes;
        }

        // Log access for warming decisions
        const currentCount = this.accessLog.get(manifestHash) || 0;
        this.accessLog.set(manifestHash, currentCount + 1);
        this.stats.missCount++;
        this.updateHitRate();

        return null;
    }

    /**
     * Set cached keyframes for a manifest
     */
    set(manifestHash: string, keyframes: SimKeyframe[], ttlSeconds?: number): void {
        const entry: CacheEntry = {
            manifestHash,
            keyframes,
            createdAt: new Date(),
            accessCount: 1,
            lastAccessedAt: new Date(),
            ttlSeconds: ttlSeconds || this.config.warmCacheTTL,
        };

        this.cache.set(manifestHash, entry);
        this.stats.totalEntries = this.cache.size;
        this.updateSizeEstimate();
    }

    /**
     * Get candidates for cache warming based on access patterns
     */
    getWarmingCandidates(): string[] {
        const candidates: Array<{ hash: string; count: number }> = [];

        for (const [hash, count] of this.accessLog) {
            if (count >= this.config.accessThreshold && !this.cache.has(hash)) {
                candidates.push({ hash, count });
            }
        }

        // Sort by access count descending
        candidates.sort((a, b) => b.count - a.count);

        return candidates.slice(0, this.config.maxManifestsPerCycle).map((c) => c.hash);
    }

    /**
     * Mark a manifest as warmed
     */
    markWarmed(manifestHash: string, keyframes: SimKeyframe[]): void {
        this.set(manifestHash, keyframes, this.config.warmCacheTTL);
        this.stats.warmCount++;
    }

    /**
     * Run a cache warming cycle
     */
    private runWarmingCycle(): void {
        // Evict expired entries
        this.evictExpired();

        // Get warming candidates
        const candidates = this.getWarmingCandidates();

        // Warming is triggered externally by the simulation service
        // This method just prepares the candidates

        // Clean up old access logs
        this.cleanupAccessLog();
    }

    /**
     * Evict expired cache entries
     */
    private evictExpired(): void {
        const now = Date.now();

        for (const [hash, entry] of this.cache) {
            const age = now - entry.createdAt.getTime();
            if (age > entry.ttlSeconds * 1000) {
                this.cache.delete(hash);
                this.stats.evictionCount++;
            }
        }

        this.stats.totalEntries = this.cache.size;
    }

    /**
     * Clean up old access log entries
     */
    private cleanupAccessLog(): void {
        // Keep only top 1000 entries by access count
        if (this.accessLog.size > 1000) {
            const entries = Array.from(this.accessLog.entries());
            entries.sort((a, b) => b[1] - a[1]);
            this.accessLog = new Map(entries.slice(0, 1000));
        }
    }

    /**
     * Update hit rate calculation
     */
    private updateHitRate(): void {
        const total = this.stats.hitCount + this.stats.missCount;
        this.stats.hitRate = total > 0 ? this.stats.hitCount / total : 0;
    }

    /**
     * Update size estimate
     */
    private updateSizeEstimate(): void {
        let totalSize = 0;
        for (const entry of this.cache.values()) {
            // Rough estimate: ~100 bytes per entity per keyframe
            totalSize += entry.keyframes.reduce(
                (sum, kf) => sum + (kf.entities?.length || 0) * 100,
                0
            );
        }
        this.stats.totalSizeBytes = totalSize;
    }

    /**
     * Get cache statistics
     */
    getStats(): CacheStats {
        return { ...this.stats };
    }

    /**
     * Clear the cache
     */
    clear(): void {
        this.cache.clear();
        this.accessLog.clear();
        this.stats = {
            totalEntries: 0,
            hitCount: 0,
            missCount: 0,
            hitRate: 0,
            warmCount: 0,
            evictionCount: 0,
            totalSizeBytes: 0,
        };
    }
}

// =============================================================================
// Worker Pool Service
// =============================================================================

/**
 * Simulation task for worker execution
 */
interface SimulationTask {
    id: string;
    manifest: SimulationManifest;
    seed: number;
    samplingRate: number;
    resolve: (result: SimKeyframe[]) => void;
    reject: (error: Error) => void;
}

/**
 * Worker thread for physics simulation offload
 *
 * @doc.type class
 * @doc.purpose Offload physics computations to worker threads
 * @doc.layer product
 * @doc.pattern WorkerPool
 */
export class PhysicsWorkerPool {
    private workers: WorkerStatus[] = [];
    private taskQueue: SimulationTask[] = [];
    private isRunning = false;

    constructor(private config: WorkerPoolConfig) {
        this.initializeWorkers();
    }

    /**
     * Initialize worker pool
     */
    private initializeWorkers(): void {
        for (let i = 0; i < this.config.poolSize; i++) {
            this.workers.push({
                id: `worker-${i}`,
                busy: false,
                completedCount: 0,
                errorCount: 0,
                avgExecutionTimeMs: 0,
            });
        }
    }

    /**
     * Submit a simulation for execution
     */
    async submit(
        manifest: SimulationManifest,
        seed: number,
        samplingRate: number
    ): Promise<SimKeyframe[]> {
        return new Promise((resolve, reject) => {
            const task: SimulationTask = {
                id: `task-${Date.now()}-${Math.random().toString(36).slice(2)}`,
                manifest,
                seed,
                samplingRate,
                resolve,
                reject,
            };

            this.taskQueue.push(task);
            this.processQueue();
        });
    }

    /**
     * Process the task queue
     */
    private processQueue(): void {
        if (!this.isRunning) {
            return;
        }

        const availableWorker = this.workers.find((w) => !w.busy);
        if (!availableWorker || this.taskQueue.length === 0) {
            return;
        }

        const task = this.taskQueue.shift()!;
        this.executeTask(availableWorker, task);
    }

    /**
     * Execute a task on a worker
     */
    private async executeTask(worker: WorkerStatus, task: SimulationTask): Promise<void> {
        worker.busy = true;
        worker.currentSimulationId = task.manifest.id;

        const startTime = Date.now();

        try {
            // In a real implementation, this would dispatch to a Web Worker
            // or Node.js worker thread. For now, we simulate the execution.
            const keyframes = await this.simulateExecution(task);

            const executionTime = Date.now() - startTime;
            this.updateWorkerStats(worker, executionTime, false);

            task.resolve(keyframes);
        } catch (error) {
            this.updateWorkerStats(worker, 0, true);
            task.reject(error instanceof Error ? error : new Error(String(error)));
        } finally {
            worker.busy = false;
            worker.currentSimulationId = undefined;
            this.processQueue();
        }
    }

    /**
     * Simulate execution (placeholder for actual worker implementation)
     */
    private async simulateExecution(task: SimulationTask): Promise<SimKeyframe[]> {
        // This would be replaced with actual WASM/worker execution
        // For now, return empty keyframes to indicate successful execution
        await new Promise((resolve) => setTimeout(resolve, 10));
        return [];
    }

    /**
     * Update worker statistics
     */
    private updateWorkerStats(worker: WorkerStatus, executionTime: number, isError: boolean): void {
        if (isError) {
            worker.errorCount++;
        } else {
            worker.completedCount++;
            // Rolling average
            worker.avgExecutionTimeMs =
                (worker.avgExecutionTimeMs * (worker.completedCount - 1) + executionTime) /
                worker.completedCount;
        }
    }

    /**
     * Start the worker pool
     */
    start(): void {
        this.isRunning = true;
        this.processQueue();
    }

    /**
     * Stop the worker pool
     */
    stop(): void {
        this.isRunning = false;
    }

    /**
     * Get worker pool status
     */
    getStatus(): WorkerStatus[] {
        return this.workers.map((w) => ({ ...w }));
    }

    /**
     * Get queue length
     */
    getQueueLength(): number {
        return this.taskQueue.length;
    }
}

// =============================================================================
// Adaptive Sampling Service
// =============================================================================

/**
 * Adaptive sampling for physics simulations
 *
 * Dynamically adjusts sampling rate based on simulation characteristics.
 *
 * @doc.type class
 * @doc.purpose Optimize sampling rate for physics simulations
 * @doc.layer product
 * @doc.pattern AdaptiveSampling
 */
export class AdaptiveSamplingService {
    constructor(private config: AdaptiveSamplingConfig) { }

    /**
     * Calculate optimal sampling rate for a keyframe
     */
    calculateSamplingRate(
        currentEntities: Array<{ vx?: number; vy?: number }>,
        hasCollisionPotential: boolean
    ): number {
        let rate = this.config.baseSamplingRate;

        // Check for high-velocity entities
        const maxVelocity = currentEntities.reduce((max, entity) => {
            const vx = entity.vx || 0;
            const vy = entity.vy || 0;
            const v = Math.sqrt(vx * vx + vy * vy);
            return Math.max(max, v);
        }, 0);

        if (maxVelocity > this.config.velocityThreshold) {
            // Increase sampling rate proportionally to velocity
            const boost = Math.min(
                maxVelocity / this.config.velocityThreshold,
                this.config.maxSamplingRate / this.config.baseSamplingRate
            );
            rate *= boost;
        }

        // Boost for collision potential
        if (hasCollisionPotential) {
            rate *= this.config.collisionSamplingBoost;
        }

        // Clamp to configured bounds
        return Math.max(
            this.config.minSamplingRate,
            Math.min(this.config.maxSamplingRate, Math.round(rate))
        );
    }

    /**
     * Detect collision potential between entities
     */
    detectCollisionPotential(
        entities: Array<{ x: number; y: number; vx?: number; vy?: number; radius?: number }>
    ): boolean {
        const timeHorizon = 0.1; // seconds

        for (let i = 0; i < entities.length; i++) {
            for (let j = i + 1; j < entities.length; j++) {
                const a = entities[i];
                const b = entities[j];

                // Predict future positions
                const ax = a.x + (a.vx || 0) * timeHorizon;
                const ay = a.y + (a.vy || 0) * timeHorizon;
                const bx = b.x + (b.vx || 0) * timeHorizon;
                const by = b.y + (b.vy || 0) * timeHorizon;

                // Check distance
                const dx = bx - ax;
                const dy = by - ay;
                const dist = Math.sqrt(dx * dx + dy * dy);
                const minDist = (a.radius || 10) + (b.radius || 10);

                if (dist < minDist * 2) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get interpolated keyframe for smooth playback
     */
    interpolateKeyframe(
        prev: SimKeyframe,
        next: SimKeyframe,
        t: number // 0-1 between prev and next
    ): SimKeyframe {
        const entities = prev.entities.map((prevEntity) => {
            const nextEntity = next.entities.find((e) => e.id === prevEntity.id);
            if (!nextEntity) {
                return prevEntity;
            }

            return {
                ...prevEntity,
                x: prevEntity.x + (nextEntity.x - prevEntity.x) * t,
                y: prevEntity.y + (nextEntity.y - prevEntity.y) * t,
            };
        });

        return {
            stepIndex: prev.stepIndex,
            timestamp: prev.timestamp + (next.timestamp - prev.timestamp) * t,
            entities,
            annotations: t < 0.5 ? prev.annotations : next.annotations,
        };
    }
}

// =============================================================================
// Metrics & Telemetry
// =============================================================================

/**
 * Physics performance metrics
 */
export interface PhysicsMetrics {
    simulationId: SimulationId;
    domain: 'PHYSICS';
    stepCount: number;
    entityCount: number;
    executionTimeMs: number;
    samplingRate: number;
    cacheHit: boolean;
    workerUsed: boolean;
    wasmUsed: boolean;
}

/**
 * Create metrics object for telemetry
 */
export function createPhysicsMetrics(
    manifest: SimulationManifest,
    executionTimeMs: number,
    samplingRate: number,
    cacheHit: boolean,
    workerUsed: boolean,
    wasmUsed: boolean
): PhysicsMetrics {
    return {
        simulationId: manifest.id,
        domain: 'PHYSICS',
        stepCount: manifest.steps.length,
        entityCount: manifest.initialEntities.length,
        executionTimeMs,
        samplingRate,
        cacheHit,
        workerUsed,
        wasmUsed,
    };
}

// =============================================================================
// Default Configurations
// =============================================================================

export const DEFAULT_CACHE_WARMING_CONFIG: CacheWarmingConfig = {
    maxManifestsPerCycle: 10,
    accessThreshold: 5,
    warmCacheTTL: 3600, // 1 hour
    cycleIntervalMs: 60000, // 1 minute
};

export const DEFAULT_WORKER_POOL_CONFIG: WorkerPoolConfig = {
    poolSize: 4,
    maxConcurrentPerWorker: 2,
    simulationTimeoutMs: 30000, // 30 seconds
    preferWasm: true,
};

export const DEFAULT_ADAPTIVE_SAMPLING_CONFIG: AdaptiveSamplingConfig = {
    baseSamplingRate: 60,
    minSamplingRate: 15,
    maxSamplingRate: 120,
    velocityThreshold: 100, // pixels per second
    collisionSamplingBoost: 1.5,
};
