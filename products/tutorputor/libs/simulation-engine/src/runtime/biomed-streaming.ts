/**
 * @doc.type class
 * @doc.purpose Streaming runtime for long-running biology/medicine simulations
 * @doc.layer product
 * @doc.pattern Service
 *
 * Provides real-time streaming capabilities for pharmacokinetics and
 * epidemiology simulations that can run for extended periods.
 */

import { EventEmitter } from 'events';

// ============================================================================
// Types & Interfaces
// ============================================================================

export interface StreamConfig {
    /** Chunk size for streaming (number of frames per chunk) */
    chunkSize: number;
    /** Interval between chunks in ms */
    chunkInterval: number;
    /** Maximum buffer size before backpressure */
    maxBufferSize: number;
    /** Enable compression for large payloads */
    compression: boolean;
    /** Heartbeat interval for connection keep-alive */
    heartbeatInterval: number;
}

export interface StreamFrame {
    /** Frame index */
    index: number;
    /** Simulation time */
    time: number;
    /** Frame state data */
    state: Record<string, unknown>;
    /** Timestamp when frame was generated */
    generatedAt: number;
}

export interface StreamChunk {
    /** Chunk sequence number */
    sequence: number;
    /** Frames in this chunk */
    frames: StreamFrame[];
    /** Is this the final chunk? */
    isFinal: boolean;
    /** Compression applied */
    compressed: boolean;
    /** Checksum for integrity */
    checksum: string;
}

export interface StreamMetrics {
    /** Total frames streamed */
    framesStreamed: number;
    /** Total chunks sent */
    chunksSent: number;
    /** Bytes transmitted */
    bytesTransmitted: number;
    /** Average latency per chunk in ms */
    avgLatencyMs: number;
    /** Backpressure events count */
    backpressureEvents: number;
    /** Dropped frames due to backpressure */
    droppedFrames: number;
    /** Connection uptime in ms */
    uptimeMs: number;
}

export interface PKSimulationParams {
    /** Drug name */
    drugName: string;
    /** Initial dose in mg */
    dose: number;
    /** Dosing interval in hours */
    dosingInterval: number;
    /** Number of doses */
    numberOfDoses: number;
    /** Absorption rate constant (ka) */
    absorptionRate: number;
    /** Elimination rate constant (ke) */
    eliminationRate: number;
    /** Volume of distribution in L */
    volumeOfDistribution: number;
    /** Bioavailability (0-1) */
    bioavailability: number;
    /** Time step in hours */
    timeStep: number;
    /** Total simulation time in hours */
    totalTime: number;
}

export interface EpidemicSimulationParams {
    /** Population size */
    population: number;
    /** Initial infected count */
    initialInfected: number;
    /** Basic reproduction number (R0) */
    r0: number;
    /** Recovery rate (gamma) */
    recoveryRate: number;
    /** Vaccination rate */
    vaccinationRate: number;
    /** Time step in days */
    timeStep: number;
    /** Total simulation time in days */
    totalTime: number;
    /** Include interventions */
    interventions?: InterventionConfig[];
}

export interface InterventionConfig {
    /** Intervention type */
    type: 'lockdown' | 'mask_mandate' | 'vaccination_campaign' | 'social_distancing';
    /** Start time (day) */
    startTime: number;
    /** End time (day) */
    endTime: number;
    /** Effectiveness (0-1) */
    effectiveness: number;
}

export interface CompartmentState {
    /** Susceptible count */
    S: number;
    /** Exposed count (for SEIR) */
    E?: number;
    /** Infected count */
    I: number;
    /** Recovered count */
    R: number;
    /** Deceased count */
    D?: number;
    /** Vaccinated count */
    V?: number;
}

export type StreamEventType =
    | 'start'
    | 'chunk'
    | 'complete'
    | 'error'
    | 'pause'
    | 'resume'
    | 'backpressure'
    | 'heartbeat';

export interface StreamEvent {
    type: StreamEventType;
    timestamp: number;
    data?: unknown;
}

// ============================================================================
// Stream Buffer
// ============================================================================

export class StreamBuffer {
    private buffer: StreamFrame[] = [];
    private maxSize: number;
    private droppedCount = 0;

    constructor(maxSize: number) {
        this.maxSize = maxSize;
    }

    push(frame: StreamFrame): boolean {
        if (this.buffer.length >= this.maxSize) {
            this.droppedCount++;
            return false;
        }
        this.buffer.push(frame);
        return true;
    }

    take(count: number): StreamFrame[] {
        return this.buffer.splice(0, count);
    }

    size(): number {
        return this.buffer.length;
    }

    isFull(): boolean {
        return this.buffer.length >= this.maxSize;
    }

    getDroppedCount(): number {
        return this.droppedCount;
    }

    clear(): void {
        this.buffer = [];
    }
}

// ============================================================================
// PK Simulation Streamer
// ============================================================================

export class PKSimulationStreamer extends EventEmitter {
    private params: PKSimulationParams;
    private config: StreamConfig;
    private buffer: StreamBuffer;
    private metrics: StreamMetrics;
    private isRunning = false;
    private isPaused = false;
    private currentFrame = 0;
    private startTime = 0;
    private chunkSequence = 0;
    private heartbeatTimer?: NodeJS.Timeout;
    private streamTimer?: NodeJS.Timeout;

    constructor(params: PKSimulationParams, config?: Partial<StreamConfig>) {
        super();
        this.params = params;
        this.config = {
            chunkSize: config?.chunkSize ?? 10,
            chunkInterval: config?.chunkInterval ?? 100,
            maxBufferSize: config?.maxBufferSize ?? 1000,
            compression: config?.compression ?? false,
            heartbeatInterval: config?.heartbeatInterval ?? 5000,
        };
        this.buffer = new StreamBuffer(this.config.maxBufferSize);
        this.metrics = this.initMetrics();
    }

    private initMetrics(): StreamMetrics {
        return {
            framesStreamed: 0,
            chunksSent: 0,
            bytesTransmitted: 0,
            avgLatencyMs: 0,
            backpressureEvents: 0,
            droppedFrames: 0,
            uptimeMs: 0,
        };
    }

    /**
     * Calculate drug concentration at time t for a single dose
     */
    private calculateConcentration(t: number, doseTime: number): number {
        const { dose, absorptionRate, eliminationRate, volumeOfDistribution, bioavailability } = this.params;

        const timeSinceDose = t - doseTime;
        if (timeSinceDose < 0) return 0;

        const F = bioavailability;
        const D = dose;
        const ka = absorptionRate;
        const ke = eliminationRate;
        const Vd = volumeOfDistribution;

        // One-compartment oral absorption model
        const concentration = (F * D * ka) / (Vd * (ka - ke)) *
            (Math.exp(-ke * timeSinceDose) - Math.exp(-ka * timeSinceDose));

        return Math.max(0, concentration);
    }

    /**
     * Calculate total concentration from multiple doses
     */
    private calculateTotalConcentration(t: number): number {
        const { dosingInterval, numberOfDoses } = this.params;
        let totalConcentration = 0;

        for (let i = 0; i < numberOfDoses; i++) {
            const doseTime = i * dosingInterval;
            if (doseTime <= t) {
                totalConcentration += this.calculateConcentration(t, doseTime);
            }
        }

        return totalConcentration;
    }

    /**
     * Generate a single frame of PK simulation
     */
    private generateFrame(frameIndex: number): StreamFrame {
        const time = frameIndex * this.params.timeStep;
        const concentration = this.calculateTotalConcentration(time);

        // Calculate derived metrics
        const { eliminationRate, volumeOfDistribution } = this.params;
        const clearance = eliminationRate * volumeOfDistribution;
        const halfLife = Math.log(2) / eliminationRate;

        // Determine PK phase
        let phase: 'absorption' | 'distribution' | 'elimination' | 'steady_state';
        const doseTimes = Array.from(
            { length: this.params.numberOfDoses },
            (_, i) => i * this.params.dosingInterval
        );
        const lastDoseTime = doseTimes.filter(t => t <= time).pop() ?? 0;
        const timeSinceLastDose = time - lastDoseTime;

        if (timeSinceLastDose < 1 / this.params.absorptionRate) {
            phase = 'absorption';
        } else if (timeSinceLastDose < 2 / this.params.absorptionRate) {
            phase = 'distribution';
        } else if (frameIndex > this.params.numberOfDoses * this.params.dosingInterval / this.params.timeStep) {
            phase = 'elimination';
        } else {
            phase = 'steady_state';
        }

        return {
            index: frameIndex,
            time,
            state: {
                concentration,
                clearance,
                halfLife,
                phase,
                cumulativeDose: Math.min(
                    Math.floor(time / this.params.dosingInterval) + 1,
                    this.params.numberOfDoses
                ) * this.params.dose,
                areaUnderCurve: this.calculateAUC(time),
                therapeuticRange: this.isInTherapeuticRange(concentration),
            },
            generatedAt: Date.now(),
        };
    }

    /**
     * Calculate area under the curve (trapezoidal approximation)
     */
    private calculateAUC(upToTime: number): number {
        let auc = 0;
        const step = this.params.timeStep;
        for (let t = 0; t < upToTime; t += step) {
            const c1 = this.calculateTotalConcentration(t);
            const c2 = this.calculateTotalConcentration(t + step);
            auc += (c1 + c2) / 2 * step;
        }
        return auc;
    }

    /**
     * Check if concentration is in therapeutic range
     */
    private isInTherapeuticRange(concentration: number): {
        status: 'subtherapeutic' | 'therapeutic' | 'toxic';
        percentage: number;
    } {
        // Example therapeutic range (would be drug-specific in real implementation)
        const minTherapeutic = 10; // mg/L
        const maxTherapeutic = 20; // mg/L
        const toxicThreshold = 30; // mg/L

        if (concentration < minTherapeutic) {
            return { status: 'subtherapeutic', percentage: (concentration / minTherapeutic) * 100 };
        } else if (concentration > toxicThreshold) {
            return { status: 'toxic', percentage: ((concentration - toxicThreshold) / toxicThreshold) * 100 + 100 };
        } else if (concentration > maxTherapeutic) {
            return {
                status: 'therapeutic',
                percentage: 100 - ((concentration - maxTherapeutic) / (toxicThreshold - maxTherapeutic)) * 50
            };
        } else {
            return { status: 'therapeutic', percentage: 100 };
        }
    }

    /**
     * Create a chunk from buffered frames
     */
    private createChunk(frames: StreamFrame[], isFinal: boolean): StreamChunk {
        const payload = JSON.stringify(frames);
        const checksum = this.calculateChecksum(payload);

        return {
            sequence: this.chunkSequence++,
            frames,
            isFinal,
            compressed: this.config.compression,
            checksum,
        };
    }

    /**
     * Simple checksum for integrity verification
     */
    private calculateChecksum(data: string): string {
        let hash = 0;
        for (let i = 0; i < data.length; i++) {
            const char = data.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash;
        }
        return Math.abs(hash).toString(16);
    }

    /**
     * Start the streaming simulation
     */
    start(): void {
        if (this.isRunning) return;

        this.isRunning = true;
        this.startTime = Date.now();
        this.emit('event', { type: 'start', timestamp: Date.now() } as StreamEvent);

        // Start heartbeat
        this.heartbeatTimer = setInterval(() => {
            this.emit('event', {
                type: 'heartbeat',
                timestamp: Date.now(),
                data: { metrics: this.getMetrics() }
            } as StreamEvent);
        }, this.config.heartbeatInterval);

        // Start frame generation and streaming
        this.streamLoop();
    }

    private streamLoop(): void {
        if (!this.isRunning) return;

        const totalFrames = Math.ceil(this.params.totalTime / this.params.timeStep);

        // Generate frames
        if (this.currentFrame < totalFrames && !this.isPaused) {
            const frame = this.generateFrame(this.currentFrame);

            if (!this.buffer.push(frame)) {
                this.metrics.backpressureEvents++;
                this.metrics.droppedFrames++;
                this.emit('event', {
                    type: 'backpressure',
                    timestamp: Date.now()
                } as StreamEvent);
            }

            this.currentFrame++;
        }

        // Send chunk if buffer has enough frames
        if (this.buffer.size() >= this.config.chunkSize ||
            (this.currentFrame >= totalFrames && this.buffer.size() > 0)) {

            const isFinal = this.currentFrame >= totalFrames &&
                this.buffer.size() <= this.config.chunkSize;

            const frames = this.buffer.take(this.config.chunkSize);
            const chunk = this.createChunk(frames, isFinal);

            const chunkSize = JSON.stringify(chunk).length;
            this.metrics.bytesTransmitted += chunkSize;
            this.metrics.framesStreamed += frames.length;
            this.metrics.chunksSent++;
            this.metrics.uptimeMs = Date.now() - this.startTime;

            this.emit('event', {
                type: 'chunk',
                timestamp: Date.now(),
                data: chunk
            } as StreamEvent);

            if (isFinal) {
                this.complete();
                return;
            }
        }

        // Schedule next iteration
        this.streamTimer = setTimeout(() => this.streamLoop(), this.config.chunkInterval);
    }

    /**
     * Pause streaming
     */
    pause(): void {
        if (!this.isRunning || this.isPaused) return;
        this.isPaused = true;
        this.emit('event', { type: 'pause', timestamp: Date.now() } as StreamEvent);
    }

    /**
     * Resume streaming
     */
    resume(): void {
        if (!this.isRunning || !this.isPaused) return;
        this.isPaused = false;
        this.emit('event', { type: 'resume', timestamp: Date.now() } as StreamEvent);
    }

    /**
     * Stop streaming
     */
    stop(): void {
        this.isRunning = false;
        this.isPaused = false;

        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
        }
        if (this.streamTimer) {
            clearTimeout(this.streamTimer);
        }

        this.buffer.clear();
    }

    private complete(): void {
        this.metrics.uptimeMs = Date.now() - this.startTime;
        this.emit('event', {
            type: 'complete',
            timestamp: Date.now(),
            data: { metrics: this.getMetrics() }
        } as StreamEvent);
        this.stop();
    }

    /**
     * Get current metrics
     */
    getMetrics(): StreamMetrics {
        return {
            ...this.metrics,
            uptimeMs: this.isRunning ? Date.now() - this.startTime : this.metrics.uptimeMs,
            droppedFrames: this.buffer.getDroppedCount(),
        };
    }

    /**
     * Get current progress
     */
    getProgress(): { currentFrame: number; totalFrames: number; percentage: number } {
        const totalFrames = Math.ceil(this.params.totalTime / this.params.timeStep);
        return {
            currentFrame: this.currentFrame,
            totalFrames,
            percentage: (this.currentFrame / totalFrames) * 100,
        };
    }
}

// ============================================================================
// Epidemic Simulation Streamer
// ============================================================================

export class EpidemicSimulationStreamer extends EventEmitter {
    private params: EpidemicSimulationParams;
    private config: StreamConfig;
    private buffer: StreamBuffer;
    private metrics: StreamMetrics;
    private isRunning = false;
    private isPaused = false;
    private currentFrame = 0;
    private startTime = 0;
    private chunkSequence = 0;
    private state: CompartmentState;
    private heartbeatTimer?: NodeJS.Timeout;
    private streamTimer?: NodeJS.Timeout;

    constructor(params: EpidemicSimulationParams, config?: Partial<StreamConfig>) {
        super();
        this.params = params;
        this.config = {
            chunkSize: config?.chunkSize ?? 10,
            chunkInterval: config?.chunkInterval ?? 100,
            maxBufferSize: config?.maxBufferSize ?? 1000,
            compression: config?.compression ?? false,
            heartbeatInterval: config?.heartbeatInterval ?? 5000,
        };
        this.buffer = new StreamBuffer(this.config.maxBufferSize);
        this.metrics = this.initMetrics();

        // Initialize compartment state
        this.state = {
            S: params.population - params.initialInfected,
            I: params.initialInfected,
            R: 0,
            V: 0,
        };
    }

    private initMetrics(): StreamMetrics {
        return {
            framesStreamed: 0,
            chunksSent: 0,
            bytesTransmitted: 0,
            avgLatencyMs: 0,
            backpressureEvents: 0,
            droppedFrames: 0,
            uptimeMs: 0,
        };
    }

    /**
     * Calculate effective R0 based on interventions
     */
    private getEffectiveR0(time: number): number {
        let effectiveR0 = this.params.r0;

        for (const intervention of this.params.interventions ?? []) {
            if (time >= intervention.startTime && time <= intervention.endTime) {
                // Apply intervention effectiveness to reduce R0
                effectiveR0 *= (1 - intervention.effectiveness);
            }
        }

        return effectiveR0;
    }

    /**
     * Calculate transmission rate (beta) from R0
     */
    private getTransmissionRate(time: number): number {
        const effectiveR0 = this.getEffectiveR0(time);
        return effectiveR0 * this.params.recoveryRate;
    }

    /**
     * Simulate one step using Euler method for SIR model
     */
    private stepSimulation(time: number): void {
        const { population, recoveryRate, vaccinationRate, timeStep } = this.params;
        const beta = this.getTransmissionRate(time);
        const gamma = recoveryRate;

        const S = this.state.S;
        const I = this.state.I;
        const R = this.state.R;
        const V = this.state.V ?? 0;

        // SIR differential equations with vaccination
        const dS = (-beta * S * I / population - vaccinationRate * S) * timeStep;
        const dI = (beta * S * I / population - gamma * I) * timeStep;
        const dR = (gamma * I) * timeStep;
        const dV = (vaccinationRate * S) * timeStep;

        // Update state (ensure non-negative values)
        this.state.S = Math.max(0, S + dS);
        this.state.I = Math.max(0, I + dI);
        this.state.R = Math.max(0, R + dR);
        this.state.V = Math.max(0, V + dV);

        // Normalize to maintain population
        const total = this.state.S + this.state.I + this.state.R + this.state.V;
        if (total !== population) {
            const scale = population / total;
            this.state.S *= scale;
            this.state.I *= scale;
            this.state.R *= scale;
            this.state.V = (this.state.V ?? 0) * scale;
        }
    }

    /**
     * Generate a single frame of epidemic simulation
     */
    private generateFrame(frameIndex: number): StreamFrame {
        const time = frameIndex * this.params.timeStep;

        // Step the simulation
        if (frameIndex > 0) {
            this.stepSimulation(time);
        }

        const effectiveR0 = this.getEffectiveR0(time);
        const effectiveRt = effectiveR0 * (this.state.S / this.params.population);

        // Determine epidemic phase
        let phase: 'growth' | 'peak' | 'decline' | 'endemic' | 'contained';
        if (this.state.I < 1) {
            phase = 'contained';
        } else if (effectiveRt > 1.1) {
            phase = 'growth';
        } else if (effectiveRt < 0.9) {
            phase = 'decline';
        } else if (this.state.I < this.params.population * 0.01) {
            phase = 'endemic';
        } else {
            phase = 'peak';
        }

        // Find active interventions
        const activeInterventions = (this.params.interventions ?? [])
            .filter(i => time >= i.startTime && time <= i.endTime)
            .map(i => i.type);

        return {
            index: frameIndex,
            time,
            state: {
                susceptible: Math.round(this.state.S),
                infected: Math.round(this.state.I),
                recovered: Math.round(this.state.R),
                vaccinated: Math.round(this.state.V ?? 0),
                effectiveR0,
                effectiveRt,
                phase,
                activeInterventions,
                prevalence: this.state.I / this.params.population,
                incidenceRate: this.getTransmissionRate(time) * this.state.S * this.state.I /
                    (this.params.population * this.params.population),
                herdImmunityThreshold: 1 - (1 / this.params.r0),
                immunityPercentage: (this.state.R + (this.state.V ?? 0)) / this.params.population,
            },
            generatedAt: Date.now(),
        };
    }

    /**
     * Create a chunk from buffered frames
     */
    private createChunk(frames: StreamFrame[], isFinal: boolean): StreamChunk {
        const payload = JSON.stringify(frames);
        const checksum = this.calculateChecksum(payload);

        return {
            sequence: this.chunkSequence++,
            frames,
            isFinal,
            compressed: this.config.compression,
            checksum,
        };
    }

    private calculateChecksum(data: string): string {
        let hash = 0;
        for (let i = 0; i < data.length; i++) {
            const char = data.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash;
        }
        return Math.abs(hash).toString(16);
    }

    /**
     * Start the streaming simulation
     */
    start(): void {
        if (this.isRunning) return;

        this.isRunning = true;
        this.startTime = Date.now();
        this.emit('event', { type: 'start', timestamp: Date.now() } as StreamEvent);

        // Start heartbeat
        this.heartbeatTimer = setInterval(() => {
            this.emit('event', {
                type: 'heartbeat',
                timestamp: Date.now(),
                data: { metrics: this.getMetrics() }
            } as StreamEvent);
        }, this.config.heartbeatInterval);

        // Start frame generation and streaming
        this.streamLoop();
    }

    private streamLoop(): void {
        if (!this.isRunning) return;

        const totalFrames = Math.ceil(this.params.totalTime / this.params.timeStep);

        // Generate frames
        if (this.currentFrame < totalFrames && !this.isPaused) {
            const frame = this.generateFrame(this.currentFrame);

            if (!this.buffer.push(frame)) {
                this.metrics.backpressureEvents++;
                this.metrics.droppedFrames++;
                this.emit('event', {
                    type: 'backpressure',
                    timestamp: Date.now()
                } as StreamEvent);
            }

            this.currentFrame++;
        }

        // Send chunk if buffer has enough frames
        if (this.buffer.size() >= this.config.chunkSize ||
            (this.currentFrame >= totalFrames && this.buffer.size() > 0)) {

            const isFinal = this.currentFrame >= totalFrames &&
                this.buffer.size() <= this.config.chunkSize;

            const frames = this.buffer.take(this.config.chunkSize);
            const chunk = this.createChunk(frames, isFinal);

            const chunkSize = JSON.stringify(chunk).length;
            this.metrics.bytesTransmitted += chunkSize;
            this.metrics.framesStreamed += frames.length;
            this.metrics.chunksSent++;
            this.metrics.uptimeMs = Date.now() - this.startTime;

            this.emit('event', {
                type: 'chunk',
                timestamp: Date.now(),
                data: chunk
            } as StreamEvent);

            if (isFinal) {
                this.complete();
                return;
            }
        }

        // Schedule next iteration
        this.streamTimer = setTimeout(() => this.streamLoop(), this.config.chunkInterval);
    }

    /**
     * Pause streaming
     */
    pause(): void {
        if (!this.isRunning || this.isPaused) return;
        this.isPaused = true;
        this.emit('event', { type: 'pause', timestamp: Date.now() } as StreamEvent);
    }

    /**
     * Resume streaming
     */
    resume(): void {
        if (!this.isRunning || !this.isPaused) return;
        this.isPaused = false;
        this.emit('event', { type: 'resume', timestamp: Date.now() } as StreamEvent);
    }

    /**
     * Stop streaming
     */
    stop(): void {
        this.isRunning = false;
        this.isPaused = false;

        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
        }
        if (this.streamTimer) {
            clearTimeout(this.streamTimer);
        }

        this.buffer.clear();
    }

    private complete(): void {
        this.metrics.uptimeMs = Date.now() - this.startTime;
        this.emit('event', {
            type: 'complete',
            timestamp: Date.now(),
            data: { metrics: this.getMetrics() }
        } as StreamEvent);
        this.stop();
    }

    /**
     * Get current metrics
     */
    getMetrics(): StreamMetrics {
        return {
            ...this.metrics,
            uptimeMs: this.isRunning ? Date.now() - this.startTime : this.metrics.uptimeMs,
            droppedFrames: this.buffer.getDroppedCount(),
        };
    }

    /**
     * Get current progress
     */
    getProgress(): { currentFrame: number; totalFrames: number; percentage: number } {
        const totalFrames = Math.ceil(this.params.totalTime / this.params.timeStep);
        return {
            currentFrame: this.currentFrame,
            totalFrames,
            percentage: (this.currentFrame / totalFrames) * 100,
        };
    }
}

// ============================================================================
// Stream Manager
// ============================================================================

export interface ActiveStream {
    id: string;
    type: 'pk' | 'epidemic';
    streamer: PKSimulationStreamer | EpidemicSimulationStreamer;
    createdAt: number;
    tenantId: string;
    userId: string;
}

export class BioMedStreamManager {
    private streams: Map<string, ActiveStream> = new Map();
    private maxStreamsPerTenant: number;
    private maxTotalStreams: number;

    constructor(config?: { maxStreamsPerTenant?: number; maxTotalStreams?: number }) {
        this.maxStreamsPerTenant = config?.maxStreamsPerTenant ?? 10;
        this.maxTotalStreams = config?.maxTotalStreams ?? 100;
    }

    /**
     * Create a new PK simulation stream
     */
    createPKStream(
        params: PKSimulationParams,
        tenantId: string,
        userId: string,
        streamConfig?: Partial<StreamConfig>
    ): { streamId: string; streamer: PKSimulationStreamer } | { error: string } {
        // Check limits
        const tenantStreams = Array.from(this.streams.values())
            .filter(s => s.tenantId === tenantId).length;

        if (tenantStreams >= this.maxStreamsPerTenant) {
            return { error: `Maximum streams per tenant (${this.maxStreamsPerTenant}) reached` };
        }

        if (this.streams.size >= this.maxTotalStreams) {
            return { error: `Maximum total streams (${this.maxTotalStreams}) reached` };
        }

        const streamId = this.generateStreamId();
        const streamer = new PKSimulationStreamer(params, streamConfig);

        this.streams.set(streamId, {
            id: streamId,
            type: 'pk',
            streamer,
            createdAt: Date.now(),
            tenantId,
            userId,
        });

        return { streamId, streamer };
    }

    /**
     * Create a new epidemic simulation stream
     */
    createEpidemicStream(
        params: EpidemicSimulationParams,
        tenantId: string,
        userId: string,
        streamConfig?: Partial<StreamConfig>
    ): { streamId: string; streamer: EpidemicSimulationStreamer } | { error: string } {
        // Check limits
        const tenantStreams = Array.from(this.streams.values())
            .filter(s => s.tenantId === tenantId).length;

        if (tenantStreams >= this.maxStreamsPerTenant) {
            return { error: `Maximum streams per tenant (${this.maxStreamsPerTenant}) reached` };
        }

        if (this.streams.size >= this.maxTotalStreams) {
            return { error: `Maximum total streams (${this.maxTotalStreams}) reached` };
        }

        const streamId = this.generateStreamId();
        const streamer = new EpidemicSimulationStreamer(params, streamConfig);

        this.streams.set(streamId, {
            id: streamId,
            type: 'epidemic',
            streamer,
            createdAt: Date.now(),
            tenantId,
            userId,
        });

        return { streamId, streamer };
    }

    /**
     * Get a stream by ID
     */
    getStream(streamId: string): ActiveStream | undefined {
        return this.streams.get(streamId);
    }

    /**
     * Stop and remove a stream
     */
    removeStream(streamId: string): boolean {
        const stream = this.streams.get(streamId);
        if (!stream) return false;

        stream.streamer.stop();
        this.streams.delete(streamId);
        return true;
    }

    /**
     * Get all streams for a tenant
     */
    getTenantStreams(tenantId: string): ActiveStream[] {
        return Array.from(this.streams.values())
            .filter(s => s.tenantId === tenantId);
    }

    /**
     * Get global stream stats
     */
    getStats(): {
        totalStreams: number;
        byType: { pk: number; epidemic: number };
        byTenant: Record<string, number>;
    } {
        const streams = Array.from(this.streams.values());

        return {
            totalStreams: streams.length,
            byType: {
                pk: streams.filter(s => s.type === 'pk').length,
                epidemic: streams.filter(s => s.type === 'epidemic').length,
            },
            byTenant: streams.reduce((acc, s) => {
                acc[s.tenantId] = (acc[s.tenantId] ?? 0) + 1;
                return acc;
            }, {} as Record<string, number>),
        };
    }

    /**
     * Clean up stale streams (older than maxAge ms)
     */
    cleanupStaleStreams(maxAgeMs: number = 3600000): number {
        const now = Date.now();
        let cleaned = 0;

        for (const [id, stream] of this.streams.entries()) {
            if (now - stream.createdAt > maxAgeMs) {
                stream.streamer.stop();
                this.streams.delete(id);
                cleaned++;
            }
        }

        return cleaned;
    }

    private generateStreamId(): string {
        return `stream_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }
}

// ============================================================================
// WebSocket Integration Helper
// ============================================================================

export interface WebSocketStreamHandler {
    onChunk: (chunk: StreamChunk) => void;
    onComplete: (metrics: StreamMetrics) => void;
    onError: (error: Error) => void;
    onHeartbeat: (metrics: StreamMetrics) => void;
}

export function attachWebSocketHandler(
    streamer: PKSimulationStreamer | EpidemicSimulationStreamer,
    handler: WebSocketStreamHandler
): () => void {
    const eventHandler = (event: StreamEvent) => {
        switch (event.type) {
            case 'chunk':
                handler.onChunk(event.data as StreamChunk);
                break;
            case 'complete':
                handler.onComplete((event.data as { metrics: StreamMetrics }).metrics);
                break;
            case 'error':
                handler.onError(event.data as Error);
                break;
            case 'heartbeat':
                handler.onHeartbeat((event.data as { metrics: StreamMetrics }).metrics);
                break;
        }
    };

    streamer.on('event', eventHandler);

    // Return cleanup function
    return () => {
        streamer.off('event', eventHandler);
    };
}

// ============================================================================
// Export convenience functions
// ============================================================================

export function createPKSimulation(
    params: PKSimulationParams,
    config?: Partial<StreamConfig>
): PKSimulationStreamer {
    return new PKSimulationStreamer(params, config);
}

export function createEpidemicSimulation(
    params: EpidemicSimulationParams,
    config?: Partial<StreamConfig>
): EpidemicSimulationStreamer {
    return new EpidemicSimulationStreamer(params, config);
}

export function createStreamManager(
    config?: { maxStreamsPerTenant?: number; maxTotalStreams?: number }
): BioMedStreamManager {
    return new BioMedStreamManager(config);
}
