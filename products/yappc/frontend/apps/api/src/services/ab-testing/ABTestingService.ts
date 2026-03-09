/**
 * A/B Testing Infrastructure for AI Models
 * 
 * Compares prediction accuracy across models (GPT-4, Claude, Ollama),
 * tracks user satisfaction, and provides automatic model fallback.
 * 
 * @doc.type class
 * @doc.purpose AI model A/B testing and automatic fallback system
 * @doc.layer product
 * @doc.pattern Service
 */

import { EventEmitter } from 'events';

// ============================================================================
// Types & Interfaces
// ============================================================================

/**
 * Supported AI model providers
 */
export type AIModelProvider = 'gpt-4' | 'gpt-4-turbo' | 'claude-3-opus' | 'claude-3-sonnet' | 'ollama-llama3' | 'ollama-mixtral';

/**
 * Experiment status
 */
export type ExperimentStatus = 'draft' | 'running' | 'paused' | 'completed' | 'archived';

/**
 * Traffic allocation strategy
 */
export type AllocationStrategy =
    | 'random'           // Pure random assignment
    | 'weighted'         // Weighted random based on percentages
    | 'sticky'           // User always gets same variant
    | 'multi-armed-bandit'; // Dynamic allocation based on performance

/**
 * Experiment variant configuration
 */
export interface ExperimentVariant {
    id: string;
    name: string;
    model: AIModelProvider;
    trafficPercentage: number;
    config?: Record<string, unknown>;
}

/**
 * A/B test experiment definition
 */
export interface Experiment {
    id: string;
    name: string;
    description: string;
    status: ExperimentStatus;
    variants: ExperimentVariant[];
    allocationStrategy: AllocationStrategy;
    targetMetrics: string[];
    startDate?: Date;
    endDate?: Date;
    minSampleSize: number;
    statisticalSignificance: number; // e.g., 0.95 for 95% confidence
    createdAt: Date;
    updatedAt: Date;
    createdBy: string;
}

/**
 * User assignment to experiment variant
 */
export interface UserAssignment {
    experimentId: string;
    userId: string;
    variantId: string;
    assignedAt: Date;
    isSticky: boolean;
}

/**
 * Interaction event for tracking
 */
export interface InteractionEvent {
    id: string;
    experimentId: string;
    variantId: string;
    userId: string;
    sessionId: string;
    eventType: 'request' | 'response' | 'feedback' | 'error';
    model: AIModelProvider;
    promptTokens?: number;
    completionTokens?: number;
    latencyMs?: number;
    cost?: number;
    satisfactionScore?: number; // 1-5 scale
    feedbackText?: string;
    isSuccess: boolean;
    errorCode?: string;
    metadata?: Record<string, unknown>;
    timestamp: Date;
}

/**
 * Aggregated metrics for a variant
 */
export interface VariantMetrics {
    variantId: string;
    model: AIModelProvider;
    totalRequests: number;
    successfulRequests: number;
    failedRequests: number;
    avgLatencyMs: number;
    p50LatencyMs: number;
    p95LatencyMs: number;
    p99LatencyMs: number;
    avgSatisfactionScore: number;
    satisfactionCount: number;
    totalTokens: number;
    totalCost: number;
    avgCostPerRequest: number;
    errorRate: number;
    conversionRate?: number;
}

/**
 * Experiment results with statistical analysis
 */
export interface ExperimentResults {
    experimentId: string;
    status: ExperimentStatus;
    variants: VariantMetrics[];
    winner?: string;
    confidence: number;
    isStatisticallySignificant: boolean;
    recommendation: string;
    analysisDate: Date;
}

/**
 * Model health status for fallback decisions
 */
export interface ModelHealth {
    model: AIModelProvider;
    isHealthy: boolean;
    errorRate: number;
    avgLatencyMs: number;
    lastError?: {
        code: string;
        message: string;
        timestamp: Date;
    };
    consecutiveFailures: number;
    lastSuccessAt?: Date;
    degradedSince?: Date;
}

/**
 * Fallback configuration
 */
export interface FallbackConfig {
    enabled: boolean;
    errorThreshold: number;        // Error rate to trigger fallback (e.g., 0.1 = 10%)
    latencyThreshold: number;      // Latency in ms to trigger fallback
    consecutiveFailures: number;   // Number of failures to trigger immediate fallback
    recoveryCheckInterval: number; // Seconds between health checks
    fallbackChain: AIModelProvider[];
}

// ============================================================================
// A/B Testing Service
// ============================================================================

/**
 * Core A/B Testing Service
 * 
 * Manages experiments, assigns users to variants, tracks metrics,
 * and provides automatic fallback on degraded performance.
 */
export class ABTestingService extends EventEmitter {
    private experiments: Map<string, Experiment> = new Map();
    private userAssignments: Map<string, UserAssignment[]> = new Map();
    private interactions: InteractionEvent[] = [];
    private modelHealth: Map<AIModelProvider, ModelHealth> = new Map();
    private fallbackConfig: FallbackConfig;

    // Multi-armed bandit state
    private banditState: Map<string, { successes: number; failures: number }[]> = new Map();

    constructor(fallbackConfig?: Partial<FallbackConfig>) {
        super();

        this.fallbackConfig = {
            enabled: true,
            errorThreshold: 0.1,
            latencyThreshold: 5000,
            consecutiveFailures: 3,
            recoveryCheckInterval: 60,
            fallbackChain: ['gpt-4-turbo', 'claude-3-sonnet', 'ollama-llama3'],
            ...fallbackConfig,
        };

        // Initialize model health
        this.initializeModelHealth();

        // Start health monitoring
        this.startHealthMonitoring();
    }

    // ============================================================================
    // Experiment Management
    // ============================================================================

    /**
     * Create a new A/B test experiment
     */
    createExperiment(config: Omit<Experiment, 'id' | 'createdAt' | 'updatedAt'>): Experiment {
        const experiment: Experiment = {
            ...config,
            id: this.generateId('exp'),
            createdAt: new Date(),
            updatedAt: new Date(),
        };

        // Validate traffic allocation
        const totalTraffic = experiment.variants.reduce((sum, v) => sum + v.trafficPercentage, 0);
        if (Math.abs(totalTraffic - 100) > 0.01) {
            throw new Error(`Traffic allocation must sum to 100%, got ${totalTraffic}%`);
        }

        this.experiments.set(experiment.id, experiment);
        this.emit('experiment:created', experiment);

        // Initialize bandit state if using multi-armed bandit
        if (experiment.allocationStrategy === 'multi-armed-bandit') {
            this.banditState.set(
                experiment.id,
                experiment.variants.map(() => ({ successes: 1, failures: 1 })) // Prior
            );
        }

        return experiment;
    }

    /**
     * Start an experiment
     */
    startExperiment(experimentId: string): Experiment {
        const experiment = this.getExperiment(experimentId);
        if (!experiment) {
            throw new Error(`Experiment ${experimentId} not found`);
        }

        experiment.status = 'running';
        experiment.startDate = new Date();
        experiment.updatedAt = new Date();

        this.emit('experiment:started', experiment);
        return experiment;
    }

    /**
     * Pause an experiment
     */
    pauseExperiment(experimentId: string): Experiment {
        const experiment = this.getExperiment(experimentId);
        if (!experiment) {
            throw new Error(`Experiment ${experimentId} not found`);
        }

        experiment.status = 'paused';
        experiment.updatedAt = new Date();

        this.emit('experiment:paused', experiment);
        return experiment;
    }

    /**
     * Complete an experiment and declare a winner
     */
    completeExperiment(experimentId: string, winnerId?: string): ExperimentResults {
        const experiment = this.getExperiment(experimentId);
        if (!experiment) {
            throw new Error(`Experiment ${experimentId} not found`);
        }

        experiment.status = 'completed';
        experiment.endDate = new Date();
        experiment.updatedAt = new Date();

        const results = this.analyzeExperiment(experimentId);

        if (winnerId) {
            results.winner = winnerId;
        }

        this.emit('experiment:completed', { experiment, results });
        return results;
    }

    /**
     * Get experiment by ID
     */
    getExperiment(experimentId: string): Experiment | undefined {
        return this.experiments.get(experimentId);
    }

    /**
     * List all experiments
     */
    listExperiments(status?: ExperimentStatus): Experiment[] {
        const experiments = Array.from(this.experiments.values());
        return status ? experiments.filter(e => e.status === status) : experiments;
    }

    // ============================================================================
    // User Assignment
    // ============================================================================

    /**
     * Assign a user to an experiment variant
     */
    assignUser(experimentId: string, userId: string): ExperimentVariant | null {
        const experiment = this.getExperiment(experimentId);
        if (!experiment || experiment.status !== 'running') {
            return null;
        }

        // Check for existing sticky assignment
        const existingAssignment = this.getUserAssignment(experimentId, userId);
        if (existingAssignment && experiment.allocationStrategy === 'sticky') {
            const variant = experiment.variants.find(v => v.id === existingAssignment.variantId);
            return variant || null;
        }

        // Select variant based on allocation strategy
        const variant = this.selectVariant(experiment, userId);

        if (variant) {
            const assignment: UserAssignment = {
                experimentId,
                userId,
                variantId: variant.id,
                assignedAt: new Date(),
                isSticky: experiment.allocationStrategy === 'sticky',
            };

            const userAssignments = this.userAssignments.get(userId) || [];
            userAssignments.push(assignment);
            this.userAssignments.set(userId, userAssignments);

            this.emit('user:assigned', { userId, experiment, variant });
        }

        return variant;
    }

    /**
     * Get user's assignment for an experiment
     */
    getUserAssignment(experimentId: string, userId: string): UserAssignment | undefined {
        const assignments = this.userAssignments.get(userId) || [];
        return assignments.find(a => a.experimentId === experimentId);
    }

    /**
     * Select variant based on allocation strategy
     */
    private selectVariant(experiment: Experiment, userId: string): ExperimentVariant | null {
        switch (experiment.allocationStrategy) {
            case 'random':
                return this.selectRandomVariant(experiment);

            case 'weighted':
                return this.selectWeightedVariant(experiment);

            case 'sticky':
                return this.selectStickyVariant(experiment, userId);

            case 'multi-armed-bandit':
                return this.selectBanditVariant(experiment);

            default:
                return this.selectWeightedVariant(experiment);
        }
    }

    private selectRandomVariant(experiment: Experiment): ExperimentVariant {
        const index = Math.floor(Math.random() * experiment.variants.length);
        return experiment.variants[index];
    }

    private selectWeightedVariant(experiment: Experiment): ExperimentVariant {
        const random = Math.random() * 100;
        let cumulative = 0;

        for (const variant of experiment.variants) {
            cumulative += variant.trafficPercentage;
            if (random <= cumulative) {
                return variant;
            }
        }

        return experiment.variants[experiment.variants.length - 1];
    }

    private selectStickyVariant(experiment: Experiment, userId: string): ExperimentVariant {
        // Hash user ID to get consistent assignment
        const hash = this.hashString(userId + experiment.id);
        const index = hash % experiment.variants.length;
        return experiment.variants[index];
    }

    private selectBanditVariant(experiment: Experiment): ExperimentVariant {
        const state = this.banditState.get(experiment.id);
        if (!state) {
            return this.selectWeightedVariant(experiment);
        }

        // Thompson Sampling
        const samples = state.map(({ successes, failures }) => {
            return this.sampleBeta(successes, failures);
        });

        const bestIndex = samples.indexOf(Math.max(...samples));
        return experiment.variants[bestIndex];
    }

    // Beta distribution sampling for Thompson Sampling
    private sampleBeta(alpha: number, beta: number): number {
        const x = this.sampleGamma(alpha, 1);
        const y = this.sampleGamma(beta, 1);
        return x / (x + y);
    }

    private sampleGamma(shape: number, scale: number): number {
        // Marsaglia and Tsang's method
        if (shape < 1) {
            return this.sampleGamma(shape + 1, scale) * Math.pow(Math.random(), 1 / shape);
        }

        const d = shape - 1 / 3;
        const c = 1 / Math.sqrt(9 * d);

        while (true) {
            let x: number;
            let v: number;

            do {
                x = this.randomNormal();
                v = 1 + c * x;
            } while (v <= 0);

            v = v * v * v;
            const u = Math.random();

            if (u < 1 - 0.0331 * x * x * x * x) {
                return d * v * scale;
            }

            if (Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) {
                return d * v * scale;
            }
        }
    }

    private randomNormal(): number {
        const u1 = Math.random();
        const u2 = Math.random();
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }

    // ============================================================================
    // Interaction Tracking
    // ============================================================================

    /**
     * Track an AI interaction
     */
    trackInteraction(event: Omit<InteractionEvent, 'id' | 'timestamp'>): InteractionEvent {
        const interaction: InteractionEvent = {
            ...event,
            id: this.generateId('int'),
            timestamp: new Date(),
        };

        this.interactions.push(interaction);

        // Update model health
        this.updateModelHealth(interaction);

        // Update bandit state if applicable
        if (event.isSuccess) {
            this.updateBanditSuccess(event.experimentId, event.variantId);
        } else {
            this.updateBanditFailure(event.experimentId, event.variantId);
        }

        this.emit('interaction:tracked', interaction);
        return interaction;
    }

    /**
     * Record user satisfaction score
     */
    recordSatisfaction(
        experimentId: string,
        variantId: string,
        userId: string,
        score: number,
        feedbackText?: string
    ): InteractionEvent {
        if (score < 1 || score > 5) {
            throw new Error('Satisfaction score must be between 1 and 5');
        }

        return this.trackInteraction({
            experimentId,
            variantId,
            userId,
            sessionId: this.generateId('ses'),
            eventType: 'feedback',
            model: this.getVariantModel(experimentId, variantId),
            satisfactionScore: score,
            feedbackText,
            isSuccess: true,
        });
    }

    private getVariantModel(experimentId: string, variantId: string): AIModelProvider {
        const experiment = this.getExperiment(experimentId);
        const variant = experiment?.variants.find(v => v.id === variantId);
        return variant?.model || 'gpt-4-turbo';
    }

    private updateBanditSuccess(experimentId: string, variantId: string): void {
        const state = this.banditState.get(experimentId);
        const experiment = this.getExperiment(experimentId);

        if (state && experiment) {
            const index = experiment.variants.findIndex(v => v.id === variantId);
            if (index >= 0) {
                state[index].successes++;
            }
        }
    }

    private updateBanditFailure(experimentId: string, variantId: string): void {
        const state = this.banditState.get(experimentId);
        const experiment = this.getExperiment(experimentId);

        if (state && experiment) {
            const index = experiment.variants.findIndex(v => v.id === variantId);
            if (index >= 0) {
                state[index].failures++;
            }
        }
    }

    // ============================================================================
    // Analysis & Results
    // ============================================================================

    /**
     * Analyze experiment results
     */
    analyzeExperiment(experimentId: string): ExperimentResults {
        const experiment = this.getExperiment(experimentId);
        if (!experiment) {
            throw new Error(`Experiment ${experimentId} not found`);
        }

        const variantMetrics = experiment.variants.map(variant =>
            this.calculateVariantMetrics(experimentId, variant.id)
        );

        // Find potential winner
        const { winner, confidence, isSignificant } = this.determineWinner(variantMetrics, experiment);

        const recommendation = this.generateRecommendation(variantMetrics, winner, isSignificant);

        return {
            experimentId,
            status: experiment.status,
            variants: variantMetrics,
            winner,
            confidence,
            isStatisticallySignificant: isSignificant,
            recommendation,
            analysisDate: new Date(),
        };
    }

    /**
     * Calculate metrics for a variant
     */
    private calculateVariantMetrics(experimentId: string, variantId: string): VariantMetrics {
        const interactions = this.interactions.filter(
            i => i.experimentId === experimentId && i.variantId === variantId
        );

        const requests = interactions.filter(i => i.eventType === 'request' || i.eventType === 'response');
        const successful = requests.filter(i => i.isSuccess);
        const failed = requests.filter(i => !i.isSuccess);
        const feedbacks = interactions.filter(i => i.eventType === 'feedback' && i.satisfactionScore);

        const latencies = requests
            .filter(i => i.latencyMs !== undefined)
            .map(i => i.latencyMs!)
            .sort((a, b) => a - b);

        const variant = this.getVariantById(experimentId, variantId);

        return {
            variantId,
            model: variant?.model || 'gpt-4-turbo',
            totalRequests: requests.length,
            successfulRequests: successful.length,
            failedRequests: failed.length,
            avgLatencyMs: latencies.length > 0
                ? latencies.reduce((a, b) => a + b, 0) / latencies.length
                : 0,
            p50LatencyMs: this.percentile(latencies, 50),
            p95LatencyMs: this.percentile(latencies, 95),
            p99LatencyMs: this.percentile(latencies, 99),
            avgSatisfactionScore: feedbacks.length > 0
                ? feedbacks.reduce((sum, f) => sum + f.satisfactionScore!, 0) / feedbacks.length
                : 0,
            satisfactionCount: feedbacks.length,
            totalTokens: requests.reduce((sum, r) => sum + (r.promptTokens || 0) + (r.completionTokens || 0), 0),
            totalCost: requests.reduce((sum, r) => sum + (r.cost || 0), 0),
            avgCostPerRequest: requests.length > 0
                ? requests.reduce((sum, r) => sum + (r.cost || 0), 0) / requests.length
                : 0,
            errorRate: requests.length > 0 ? failed.length / requests.length : 0,
        };
    }

    private getVariantById(experimentId: string, variantId: string): ExperimentVariant | undefined {
        const experiment = this.getExperiment(experimentId);
        return experiment?.variants.find(v => v.id === variantId);
    }

    private percentile(arr: number[], p: number): number {
        if (arr.length === 0) return 0;
        const index = Math.ceil((p / 100) * arr.length) - 1;
        return arr[Math.max(0, index)];
    }

    /**
     * Determine winner using statistical significance
     */
    private determineWinner(
        metrics: VariantMetrics[],
        experiment: Experiment
    ): { winner?: string; confidence: number; isSignificant: boolean } {
        if (metrics.length < 2) {
            return { confidence: 0, isSignificant: false };
        }

        // Sort by satisfaction score (primary metric)
        const sorted = [...metrics].sort((a, b) => b.avgSatisfactionScore - a.avgSatisfactionScore);

        const best = sorted[0];
        const secondBest = sorted[1];

        // Check minimum sample size
        const totalSamples = metrics.reduce((sum, m) => sum + m.totalRequests, 0);
        if (totalSamples < experiment.minSampleSize) {
            return {
                winner: best.variantId,
                confidence: 0,
                isSignificant: false
            };
        }

        // Calculate z-score for proportion test
        const p1 = best.avgSatisfactionScore / 5; // Normalize to 0-1
        const p2 = secondBest.avgSatisfactionScore / 5;
        const n1 = best.satisfactionCount || 1;
        const n2 = secondBest.satisfactionCount || 1;

        const pooledP = (p1 * n1 + p2 * n2) / (n1 + n2);
        const se = Math.sqrt(pooledP * (1 - pooledP) * (1 / n1 + 1 / n2));
        const zScore = se > 0 ? (p1 - p2) / se : 0;

        // Convert z-score to confidence
        const confidence = this.zScoreToConfidence(zScore);
        const isSignificant = confidence >= experiment.statisticalSignificance;

        return {
            winner: best.variantId,
            confidence,
            isSignificant,
        };
    }

    private zScoreToConfidence(z: number): number {
        // Approximation of cumulative normal distribution
        const a1 = 0.254829592;
        const a2 = -0.284496736;
        const a3 = 1.421413741;
        const a4 = -1.453152027;
        const a5 = 1.061405429;
        const p = 0.3275911;

        const sign = z < 0 ? -1 : 1;
        z = Math.abs(z) / Math.sqrt(2);

        const t = 1.0 / (1.0 + p * z);
        const y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-z * z);

        return 0.5 * (1.0 + sign * y);
    }

    private generateRecommendation(
        metrics: VariantMetrics[],
        winner: string | undefined,
        isSignificant: boolean
    ): string {
        if (!winner) {
            return 'Insufficient data to determine a winner. Continue collecting samples.';
        }

        const winnerMetrics = metrics.find(m => m.variantId === winner);
        if (!winnerMetrics) {
            return 'Unable to analyze winner metrics.';
        }

        if (!isSignificant) {
            return `Preliminary leader is ${winnerMetrics.model} with ${winnerMetrics.avgSatisfactionScore.toFixed(2)}/5 satisfaction, but results are not yet statistically significant. Continue experiment.`;
        }

        const costComparison = metrics
            .filter(m => m.variantId !== winner)
            .map(m => {
                const costDiff = ((winnerMetrics.avgCostPerRequest - m.avgCostPerRequest) / m.avgCostPerRequest * 100).toFixed(1);
                return `${costDiff}% vs ${m.model}`;
            })
            .join(', ');

        return `Recommend ${winnerMetrics.model} as the winner with ${winnerMetrics.avgSatisfactionScore.toFixed(2)}/5 satisfaction score. Cost comparison: ${costComparison}. Error rate: ${(winnerMetrics.errorRate * 100).toFixed(2)}%.`;
    }

    // ============================================================================
    // Model Health & Fallback
    // ============================================================================

    /**
     * Get current model health status
     */
    getModelHealth(model: AIModelProvider): ModelHealth | undefined {
        return this.modelHealth.get(model);
    }

    /**
     * Get all model health statuses
     */
    getAllModelHealth(): ModelHealth[] {
        return Array.from(this.modelHealth.values());
    }

    /**
     * Get fallback model for a degraded primary model
     */
    getFallbackModel(primaryModel: AIModelProvider): AIModelProvider | null {
        if (!this.fallbackConfig.enabled) {
            return null;
        }

        const health = this.modelHealth.get(primaryModel);
        if (health?.isHealthy) {
            return null; // No fallback needed
        }

        // Find first healthy model in fallback chain
        for (const fallbackModel of this.fallbackConfig.fallbackChain) {
            if (fallbackModel === primaryModel) continue;

            const fallbackHealth = this.modelHealth.get(fallbackModel);
            if (fallbackHealth?.isHealthy) {
                this.emit('model:fallback', { from: primaryModel, to: fallbackModel });
                return fallbackModel;
            }
        }

        return null;
    }

    /**
     * Check if model should be used or fallback triggered
     */
    shouldUseFallback(model: AIModelProvider): boolean {
        const health = this.modelHealth.get(model);
        if (!health) return false;

        return (
            health.errorRate > this.fallbackConfig.errorThreshold ||
            health.avgLatencyMs > this.fallbackConfig.latencyThreshold ||
            health.consecutiveFailures >= this.fallbackConfig.consecutiveFailures
        );
    }

    private initializeModelHealth(): void {
        const models: AIModelProvider[] = [
            'gpt-4', 'gpt-4-turbo',
            'claude-3-opus', 'claude-3-sonnet',
            'ollama-llama3', 'ollama-mixtral'
        ];

        for (const model of models) {
            this.modelHealth.set(model, {
                model,
                isHealthy: true,
                errorRate: 0,
                avgLatencyMs: 0,
                consecutiveFailures: 0,
            });
        }
    }

    private updateModelHealth(interaction: InteractionEvent): void {
        const health = this.modelHealth.get(interaction.model);
        if (!health) return;

        // Get recent interactions for this model (last 100)
        const recentInteractions = this.interactions
            .filter(i => i.model === interaction.model)
            .slice(-100);

        // Calculate error rate
        const failures = recentInteractions.filter(i => !i.isSuccess).length;
        health.errorRate = recentInteractions.length > 0
            ? failures / recentInteractions.length
            : 0;

        // Calculate average latency
        const latencies = recentInteractions
            .filter(i => i.latencyMs !== undefined)
            .map(i => i.latencyMs!);
        health.avgLatencyMs = latencies.length > 0
            ? latencies.reduce((a, b) => a + b, 0) / latencies.length
            : 0;

        // Update consecutive failures
        if (!interaction.isSuccess) {
            health.consecutiveFailures++;
            health.lastError = {
                code: interaction.errorCode || 'UNKNOWN',
                message: 'Error recorded',
                timestamp: interaction.timestamp,
            };
        } else {
            health.consecutiveFailures = 0;
            health.lastSuccessAt = interaction.timestamp;
        }

        // Determine health status
        const wasHealthy = health.isHealthy;
        health.isHealthy = !this.shouldUseFallback(interaction.model);

        if (wasHealthy && !health.isHealthy) {
            health.degradedSince = new Date();
            this.emit('model:degraded', { model: interaction.model, health });
        } else if (!wasHealthy && health.isHealthy) {
            health.degradedSince = undefined;
            this.emit('model:recovered', { model: interaction.model, health });
        }
    }

    private startHealthMonitoring(): void {
        // Periodic health check could go here
        // In production, this would check external endpoints
        setInterval(() => {
            for (const [model, health] of this.modelHealth) {
                // Check for recovery if degraded
                if (!health.isHealthy && health.degradedSince) {
                    const degradedMs = Date.now() - health.degradedSince.getTime();
                    // Auto-recover after 5 minutes if no recent failures
                    if (degradedMs > 5 * 60 * 1000 && health.consecutiveFailures === 0) {
                        health.isHealthy = true;
                        health.degradedSince = undefined;
                        this.emit('model:recovered', { model, health });
                    }
                }
            }
        }, this.fallbackConfig.recoveryCheckInterval * 1000);
    }

    // ============================================================================
    // Utilities
    // ============================================================================

    private generateId(prefix: string): string {
        return `${prefix}_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
    }

    private hashString(str: string): number {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash;
        }
        return Math.abs(hash);
    }

    /**
     * Export experiment data for analysis
     */
    exportExperimentData(experimentId: string): {
        experiment: Experiment;
        interactions: InteractionEvent[];
        metrics: VariantMetrics[];
    } {
        const experiment = this.getExperiment(experimentId);
        if (!experiment) {
            throw new Error(`Experiment ${experimentId} not found`);
        }

        const interactions = this.interactions.filter(i => i.experimentId === experimentId);
        const metrics = experiment.variants.map(v =>
            this.calculateVariantMetrics(experimentId, v.id)
        );

        return { experiment, interactions, metrics };
    }
}

// ============================================================================
// Singleton Export
// ============================================================================

export const abTestingService = new ABTestingService();

export default ABTestingService;
