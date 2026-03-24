/**
 * A/B Test Framework
 *
 * Manages experiments, variant assignment, and statistical analysis
 * for data-driven decision making.
 */

/**
 * Experiment variant
 */
export interface Variant {
  id: string;
  name: string;
  description?: string;
  weight: number; // 0-1, must sum to 1 across all variants
  config: Record<string, unknown>; // Variant-specific configuration
}

/**
 * Experiment definition
 */
export interface Experiment {
  id: string;
  name: string;
  description?: string;
  variants: Variant[];
  status: 'draft' | 'running' | 'paused' | 'completed';
  startDate?: Date;
  endDate?: Date;
  targetAudience?: {
    userIds?: string[];
    segments?: string[];
    percentage?: number; // 0-100
  };
  metrics: ExperimentMetric[];
  metadata?: Record<string, unknown>;
}

/**
 * Metric to track in experiment
 */
export interface ExperimentMetric {
  id: string;
  name: string;
  type: 'conversion' | 'revenue' | 'engagement' | 'custom';
  goal: 'maximize' | 'minimize';
  isPrimary?: boolean;
}

/**
 * User assignment to variant
 */
export interface VariantAssignment {
  experimentId: string;
  userId: string;
  variantId: string;
  assignedAt: Date;
  metadata?: Record<string, unknown>;
}

/**
 * Metric event (conversion, revenue, etc.)
 */
export interface MetricEvent {
  experimentId: string;
  variantId: string;
  userId: string;
  metricId: string;
  value: number;
  timestamp: Date;
  metadata?: Record<string, unknown>;
}

/**
 * Experiment results
 */
export interface ExperimentResults {
  experimentId: string;
  startDate: Date;
  endDate?: Date;
  status: Experiment['status'];
  variants: VariantResults[];
  winner?: string; // Variant ID
  confidence: number; // 0-1
  recommendations: string[];
}

/**
 * Results for a single variant
 */
export interface VariantResults {
  variantId: string;
  variantName: string;
  sampleSize: number;
  metrics: Array<{
    metricId: string;
    metricName: string;
    value: number; // Mean
    standardDeviation: number;
    confidenceInterval: [number, number]; // 95% CI
  }>;
}

/**
 * Statistical test result
 */
export interface StatisticalTest {
  testType: 'ttest' | 'chi-square' | 'mann-whitney';
  pValue: number;
  isSignificant: boolean;
  confidence: number;
  effectSize: number;
}

/**
 * Storage adapter for A/B testing data
 */
export interface ABTestStorageAdapter {
  saveExperiment(experiment: Experiment): Promise<void>;
  getExperiment(experimentId: string): Promise<Experiment | null>;
  saveAssignment(assignment: VariantAssignment): Promise<void>;
  getAssignment(
    experimentId: string,
    userId: string
  ): Promise<VariantAssignment | null>;
  saveMetricEvent(event: MetricEvent): Promise<void>;
  getMetricEvents(experimentId: string): Promise<MetricEvent[]>;
}

/**
 * In-memory storage adapter (default)
 */
class InMemoryABTestStorage implements ABTestStorageAdapter {
  private experiments: Map<string, Experiment> = new Map();
  private assignments: Map<string, VariantAssignment> = new Map();
  private events: MetricEvent[] = [];

  /**
  /**
   * Save experiment to storage adapter.
   */
  async saveExperiment(experiment: Experiment): Promise<void> {
    this.experiments.set(experiment.id, experiment);
  }

  /**
  /**
   * Retrieve an experiment by id from storage.
   */
  async getExperiment(experimentId: string): Promise<Experiment | null> {
    return this.experiments.get(experimentId) || null;
  }

  /**
  /**
   * Persist a user's variant assignment.
   */
  async saveAssignment(assignment: VariantAssignment): Promise<void> {
    const key = `${assignment.experimentId}:${assignment.userId}`;
    this.assignments.set(key, assignment);
  }

  /**
  /**
   * Retrieve a user's assignment for an experiment, if any.
   */
  async getAssignment(
    experimentId: string,
    userId: string
  ): Promise<VariantAssignment | null> {
    const key = `${experimentId}:${userId}`;
    return this.assignments.get(key) || null;
  }

  /**
  /**
   * Persist a metric event for analysis.
   */
  async saveMetricEvent(event: MetricEvent): Promise<void> {
    this.events.push(event);
  }

  /**
   * Retrieve recorded metric events for an experiment.
   */
  async getMetricEvents(experimentId: string): Promise<MetricEvent[]> {
    return this.events.filter((e) => e.experimentId === experimentId);
  }
}

/**
 * A/B Test Framework implementation
 */
export class ABTestFramework {
  private storage: ABTestStorageAdapter;

  /**
   * Construct the ABTestFramework with an optional storage adapter.
   */
  constructor(storage?: ABTestStorageAdapter) {
    this.storage = storage || new InMemoryABTestStorage();
  }

  /**
   * Create a new experiment
   */
  async createExperiment(
    experiment: Omit<Experiment, 'status'>
  ): Promise<Experiment> {
    // Validate variants
    this.validateVariants(experiment.variants);

    const fullExperiment: Experiment = {
      ...experiment,
      status: 'draft',
    };

    await this.storage.saveExperiment(fullExperiment);
    return fullExperiment;
  }

  /**
   * Start an experiment
   */
  async startExperiment(experimentId: string): Promise<void> {
    const experiment = await this.storage.getExperiment(experimentId);
    if (!experiment) {
      throw new Error(`Experiment ${experimentId} not found`);
    }

    experiment.status = 'running';
    experiment.startDate = new Date();

    await this.storage.saveExperiment(experiment);
  }

  /**
   * Stop an experiment
   */
  async stopExperiment(experimentId: string): Promise<void> {
    const experiment = await this.storage.getExperiment(experimentId);
    if (!experiment) {
      throw new Error(`Experiment ${experimentId} not found`);
    }

    experiment.status = 'completed';
    experiment.endDate = new Date();

    await this.storage.saveExperiment(experiment);
  }

  /**
   * Assign a user to a variant
   */
  async assignVariant(
    experimentId: string,
    userId: string
  ): Promise<VariantAssignment> {
    // Check for existing assignment
    const existing = await this.storage.getAssignment(experimentId, userId);
    if (existing) {
      return existing;
    }

    const experiment = await this.storage.getExperiment(experimentId);
    if (!experiment) {
      throw new Error(`Experiment ${experimentId} not found`);
    }

    if (experiment.status !== 'running') {
      throw new Error(`Experiment ${experimentId} is not running`);
    }

    // Check if user is in target audience
    if (!this.isInTargetAudience(userId, experiment)) {
      throw new Error(`User ${userId} is not in target audience`);
    }

    // Select variant using consistent hashing
    const variant = this.selectVariant(
      experimentId,
      userId,
      experiment.variants
    );

    const assignment: VariantAssignment = {
      experimentId,
      userId,
      variantId: variant.id,
      assignedAt: new Date(),
    };

    await this.storage.saveAssignment(assignment);
    return assignment;
  }

  /**
   * Get user's assigned variant
   */
  async getVariant(
    experimentId: string,
    userId: string
  ): Promise<Variant | null> {
    const assignment = await this.storage.getAssignment(experimentId, userId);
    if (!assignment) return null;

    const experiment = await this.storage.getExperiment(experimentId);
    if (!experiment) return null;

    return (
      experiment.variants.find((v) => v.id === assignment.variantId) || null
    );
  }

  /**
   * Track a metric event
   */
  async trackMetric(
    experimentId: string,
    userId: string,
    metricId: string,
    value: number,
    metadata?: Record<string, unknown>
  ): Promise<void> {
    const assignment = await this.storage.getAssignment(experimentId, userId);
    if (!assignment) {
      throw new Error(
        `User ${userId} not assigned to experiment ${experimentId}`
      );
    }

    const event: MetricEvent = {
      experimentId,
      variantId: assignment.variantId,
      userId,
      metricId,
      value,
      timestamp: new Date(),
      metadata,
    };

    await this.storage.saveMetricEvent(event);
  }

  /**
   * Track a conversion
   */
  async trackConversion(
    experimentId: string,
    userId: string,
    metricId: string
  ): Promise<void> {
    await this.trackMetric(experimentId, userId, metricId, 1);
  }

  /**
   * Get experiment results
   */
  async getResults(experimentId: string): Promise<ExperimentResults> {
    const experiment = await this.storage.getExperiment(experimentId);
    if (!experiment) {
      throw new Error(`Experiment ${experimentId} not found`);
    }

    const events = await this.storage.getMetricEvents(experimentId);

    // Calculate results for each variant
    const variantResults: VariantResults[] = experiment.variants.map(
      (variant) => {
        const variantEvents = events.filter((e) => e.variantId === variant.id);
        const userIds = new Set(variantEvents.map((e) => e.userId));

        return {
          variantId: variant.id,
          variantName: variant.name,
          sampleSize: userIds.size,
          metrics: experiment.metrics.map((metric) => {
            const metricEvents = variantEvents.filter(
              (e) => e.metricId === metric.id
            );
            const values = metricEvents.map((e) => e.value);

            const mean = this.calculateMean(values);
            const stdDev = this.calculateStandardDeviation(values);
            const ci = this.calculateConfidenceInterval(values, 0.95);

            return {
              metricId: metric.id,
              metricName: metric.name,
              value: mean,
              standardDeviation: stdDev,
              confidenceInterval: ci,
            };
          }),
        };
      }
    );

    // Determine winner
    const { winner, confidence, recommendations } = this.determineWinner(
      experiment,
      variantResults
    );

    return {
      experimentId,
      startDate: experiment.startDate || new Date(),
      endDate: experiment.endDate,
      status: experiment.status,
      variants: variantResults,
      winner,
      confidence,
      recommendations,
    };
  }

  /**
   * Run statistical test between two variants
   */
  async runStatisticalTest(
    experimentId: string,
    variantId1: string,
    variantId2: string,
    metricId: string
  ): Promise<StatisticalTest> {
    const events = await this.storage.getMetricEvents(experimentId);

    const variant1Events = events
      .filter((e) => e.variantId === variantId1 && e.metricId === metricId)
      .map((e) => e.value);

    const variant2Events = events
      .filter((e) => e.variantId === variantId2 && e.metricId === metricId)
      .map((e) => e.value);

    return this.tTest(variant1Events, variant2Events);
  }

  /**
   * Validate variant configuration
   */
  private validateVariants(variants: Variant[]): void {
    if (variants.length < 2) {
      throw new Error('At least 2 variants are required');
    }

    const totalWeight = variants.reduce((sum, v) => sum + v.weight, 0);
    if (Math.abs(totalWeight - 1) > 0.001) {
      throw new Error(`Variant weights must sum to 1, got ${totalWeight}`);
    }

    const ids = new Set(variants.map((v) => v.id));
    if (ids.size !== variants.length) {
      throw new Error('Variant IDs must be unique');
    }
  }

  /**
   * Check if user is in target audience
   */
  private isInTargetAudience(userId: string, experiment: Experiment): boolean {
    if (!experiment.targetAudience) return true;

    const { userIds, percentage } = experiment.targetAudience;

    // Check specific user IDs
    // If userIds is provided but empty, treat it as "no restriction".
    if (userIds && userIds.length > 0 && !userIds.includes(userId)) {
      return false;
    }

    // Check percentage rollout
    if (percentage !== undefined) {
      const hash = this.hashString(`${experiment.id}:${userId}`);
      const userPercentage = (hash % 100) / 100;
      if (userPercentage > percentage / 100) {
        return false;
      }
    }

    return true;
  }

  /**
   * Select variant using consistent hashing
   */
  private selectVariant(
    experimentId: string,
    userId: string,
    variants: Variant[]
  ): Variant {
    const hash = this.hashString(`${experimentId}:${userId}`);
    const normalized = (hash % 10000) / 10000; // 0-1

    let cumulative = 0;
    for (const variant of variants) {
      cumulative += variant.weight;
      if (normalized <= cumulative) {
        return variant;
      }
    }

    // Fallback to first variant
    return variants[0];
  }

  /**
   * Hash a string to a number (simple hash for demonstration)
   */
  private hashString(str: string): number {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash = hash & hash; // Convert to 32-bit integer
    }
    return Math.abs(hash);
  }

  /**
   * Determine experiment winner
   */
  private determineWinner(
    experiment: Experiment,
    results: VariantResults[]
  ): {
    winner?: string;
    confidence: number;
    recommendations: string[];
  } {
    const recommendations: string[] = [];

    // Find primary metric
    const primaryMetric = experiment.metrics.find((m) => m.isPrimary);
    if (!primaryMetric) {
      return {
        confidence: 0,
        recommendations: ['No primary metric defined'],
      };
    }

    // Check sample sizes
    const minSampleSize = 100; // Minimum for statistical significance
    const insufficientSamples = results.filter(
      (r) => r.sampleSize < minSampleSize
    );

    if (insufficientSamples.length > 0) {
      recommendations.push(
        `Insufficient sample size for ${insufficientSamples.map((r) => r.variantName).join(', ')}`
      );
      return { confidence: 0, recommendations };
    }

    // Find best performing variant for primary metric
    const variantScores = results.map((variant) => {
      const metric = variant.metrics.find(
        (m) => m.metricId === primaryMetric.id
      );
      return {
        variantId: variant.variantId,
        variantName: variant.variantName,
        score: metric?.value || 0,
      };
    });

    variantScores.sort((a, b) =>
      primaryMetric.goal === 'maximize' ? b.score - a.score : a.score - b.score
    );

    const winner = variantScores[0];
    const runnerUp = variantScores[1];

    // Calculate confidence (simplified)
    const improvement =
      Math.abs(winner.score - runnerUp.score) / runnerUp.score;
    const confidence = Math.min(0.99, improvement * 2); // Simplified confidence

    if (confidence >= 0.95) {
      recommendations.push(
        `Strong winner: ${winner.variantName} with ${(improvement * 100).toFixed(1)}% improvement`
      );
    } else if (confidence >= 0.8) {
      recommendations.push(
        `Moderate confidence in ${winner.variantName}. Consider running longer.`
      );
    } else {
      recommendations.push('No clear winner yet. Continue experiment.');
    }

    return {
      winner: confidence >= 0.8 ? winner.variantId : undefined,
      confidence,
      recommendations,
    };
  }

  /**
   * Perform t-test
   */
  private tTest(sample1: number[], sample2: number[]): StatisticalTest {
    if (sample1.length === 0 || sample2.length === 0) {
      return {
        testType: 'ttest',
        pValue: 1,
        isSignificant: false,
        confidence: 0,
        effectSize: 0,
      };
    }

    const mean1 = this.calculateMean(sample1);
    const mean2 = this.calculateMean(sample2);
    const variance1 = this.calculateVariance(sample1);
    const variance2 = this.calculateVariance(sample2);

    const n1 = sample1.length;
    const n2 = sample2.length;

    // Welch's t-test (unequal variances)
    const tStatistic =
      (mean1 - mean2) / Math.sqrt(variance1 / n1 + variance2 / n2);

    // Degrees of freedom (Welch-Satterthwaite equation)
    const df =
      Math.pow(variance1 / n1 + variance2 / n2, 2) /
      (Math.pow(variance1 / n1, 2) / (n1 - 1) +
        Math.pow(variance2 / n2, 2) / (n2 - 1));

    // Simplified p-value calculation (rough approximation)
    const pValue = this.approximatePValue(Math.abs(tStatistic), df);

    // Effect size (Cohen's d)
    const pooledStd = Math.sqrt((variance1 + variance2) / 2);
    const effectSize = (mean1 - mean2) / pooledStd;

    return {
      testType: 'ttest',
      pValue,
      isSignificant: pValue < 0.05,
      confidence: 1 - pValue,
      effectSize,
    };
  }

  /**
   * Calculate mean
   */
  private calculateMean(values: number[]): number {
    if (values.length === 0) return 0;
    return values.reduce((sum, v) => sum + v, 0) / values.length;
  }

  /**
   * Calculate variance
   */
  private calculateVariance(values: number[]): number {
    if (values.length === 0) return 0;
    const mean = this.calculateMean(values);
    return (
      values.reduce((sum, v) => sum + Math.pow(v - mean, 2), 0) / values.length
    );
  }

  /**
   * Calculate standard deviation
   */
  private calculateStandardDeviation(values: number[]): number {
    return Math.sqrt(this.calculateVariance(values));
  }

  /**
   * Calculate 95% confidence interval
   */
  private calculateConfidenceInterval(
    values: number[],
    confidence: number
  ): [number, number] {
    if (values.length === 0) return [0, 0];

    const mean = this.calculateMean(values);
    const stdDev = this.calculateStandardDeviation(values);
    const n = values.length;

    // z-score for 95% confidence
    const z = confidence === 0.95 ? 1.96 : 1.645;
    const margin = z * (stdDev / Math.sqrt(n));

    return [mean - margin, mean + margin];
  }

  /**
   * Approximate p-value from t-statistic (simplified)
   */
  private approximatePValue(t: number, _df: number): number {
    // Very rough approximation for demonstration
    // In production, use a proper statistical library
    if (t < 1.96) return 1 - (t / 1.96) * 0.5;
    if (t < 2.58) return (0.05 * (2.58 - t)) / 0.62;
    return 0.01;
  }
}
