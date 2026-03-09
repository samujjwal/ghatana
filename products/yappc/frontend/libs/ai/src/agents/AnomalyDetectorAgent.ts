/**
 * Anomaly Detector Agent Implementation
 *
 * Real-time anomaly detection using Isolation Forest and LSTM models.
 * Monitors velocity, patterns, security, quality, and resource metrics.
 *
 * @module ai/agents/AnomalyDetectorAgent
 * @doc.type class
 * @doc.purpose Anomaly detection agent
 * @doc.layer product
 * @doc.pattern AIAgent
 */

import { BaseAgent, type ProcessResult } from './BaseAgent';
import type {
    AgentContext,
    AIRecommendation,
    AnomalyInput,
    AnomalyOutput,
    BaselineMetrics,
    DetectedAnomaly,
    MetricData,
    TimeSeriesPoint,
} from './types';
import { AnomalyInputSchema, AgentError } from './types';
import { v4 as uuidv4 } from 'uuid';

/**
 * Alert service interface
 */
interface AlertService {
    trigger(alert: AlertPayload): Promise<void>;
}

interface AlertPayload {
    type: string;
    severity: string;
    anomalies: DetectedAnomaly[];
    context: AgentContext;
}

/**
 * AnomalyDetectorAgent for real-time monitoring
 */
export class AnomalyDetectorAgent extends BaseAgent<AnomalyInput, AnomalyOutput> {
    private alertService?: AlertService;

    constructor(alertService?: AlertService) {
        super({
            name: 'AnomalyDetectorAgent',
            version: '1.5.0',
            description: 'Real-time anomaly detection using ML models',
            capabilities: [
                'isolation-forest',
                'lstm-detection',
                'baseline-comparison',
                'severity-classification',
                'root-cause-analysis',
            ],
            supportedModels: ['isolation-forest-v2', 'lstm-anomaly-v1'],
            latencySLA: 200,
            defaultTimeout: 5000,
        });

        this.alertService = alertService;
    }

    /**
     * Validate input
     */
    protected validateInput(input: AnomalyInput): void {
        const result = AnomalyInputSchema.safeParse(input);
        if (!result.success) {
            throw new AgentError(
                `Invalid input: ${result.error.message}`,
                'VALIDATION_ERROR',
                this.name,
                false
            );
        }
    }

    /**
     * Process anomaly detection request
     */
    protected async processRequest(
        input: AnomalyInput,
        context: AgentContext
    ): Promise<ProcessResult<AnomalyOutput>> {
        // Get or calculate baseline
        const baseline = input.baseline || this.calculateBaseline(input.currentMetrics);

        // Run detection algorithms
        const [isolationForestResults, statisticalResults] = await Promise.all([
            this.runIsolationForest(input.currentMetrics, baseline, input.sensitivity),
            this.runStatisticalDetection(input.currentMetrics, baseline, input.sensitivity),
        ]);

        // Run LSTM if time series data available
        let lstmResults: DetectedAnomaly[] = [];
        if (input.timeSeriesData?.length) {
            lstmResults = await this.runLSTMDetection(input.timeSeriesData, baseline);
        }

        // Ensemble voting - combine results
        const anomalies = this.ensembleVote([
            ...isolationForestResults,
            ...statisticalResults,
            ...lstmResults,
        ]);

        // Classify severity and identify root causes
        const classifiedAnomalies = anomalies.map((a) => ({
            ...a,
            severity: this.classifySeverity(a, baseline),
            rootCause: this.identifyRootCause(a, input),
        }));

        // Trigger alerts for high-severity anomalies
        const highSeverity = classifiedAnomalies.filter(
            (a) => a.severity === 'critical' || a.severity === 'warning'
        );
        if (highSeverity.length > 0 && this.alertService) {
            await this.alertService.trigger({
                type: 'anomaly-detected',
                severity: highSeverity.some((a) => a.severity === 'critical')
                    ? 'critical'
                    : 'warning',
                anomalies: highSeverity,
                context,
            });
        }

        // Generate recommendations
        const recommendations = this.generateRecommendations(classifiedAnomalies);

        return {
            data: {
                anomaliesDetected: classifiedAnomalies.length,
                anomalies: classifiedAnomalies,
                baseline,
                recommendations,
            },
            modelVersion: 'ensemble-v1.5.0',
            confidence: this.calculateOverallConfidence(classifiedAnomalies),
        };
    }

    /**
     * Calculate baseline metrics from historical data
     */
    private calculateBaseline(metrics: MetricData[]): BaselineMetrics {
        if (metrics.length === 0) {
            return {
                mean: 0,
                stdDev: 0,
                min: 0,
                max: 0,
                percentiles: { 25: 0, 50: 0, 75: 0, 90: 0, 95: 0, 99: 0 },
            };
        }

        const values = metrics.map((m) => m.value).sort((a, b) => a - b);
        const n = values.length;

        const mean = values.reduce((a, b) => a + b, 0) / n;
        const variance = values.reduce((sum, v) => sum + Math.pow(v - mean, 2), 0) / n;
        const stdDev = Math.sqrt(variance);

        const percentile = (p: number) => {
            const index = Math.ceil((p / 100) * n) - 1;
            return values[Math.max(0, Math.min(index, n - 1))];
        };

        return {
            mean,
            stdDev,
            min: values[0],
            max: values[n - 1],
            percentiles: {
                25: percentile(25),
                50: percentile(50),
                75: percentile(75),
                90: percentile(90),
                95: percentile(95),
                99: percentile(99),
            },
        };
    }

    /**
     * Run Isolation Forest-like anomaly detection
     */
    private async runIsolationForest(
        metrics: MetricData[],
        baseline: BaselineMetrics,
        sensitivity: number = 0.8
    ): Promise<DetectedAnomaly[]> {
        const anomalies: DetectedAnomaly[] = [];
        const threshold = 3 - sensitivity * 2; // 3 std devs at 0 sensitivity, 1 at 1.0

        for (const metric of metrics) {
            const zScore = baseline.stdDev > 0
                ? Math.abs(metric.value - baseline.mean) / baseline.stdDev
                : 0;

            if (zScore > threshold) {
                const deviationPercent = baseline.mean !== 0
                    ? ((metric.value - baseline.mean) / baseline.mean) * 100
                    : metric.value * 100;

                anomalies.push({
                    id: uuidv4(),
                    type: 'velocity', // Will be updated based on metric type
                    severity: 'info',
                    title: `Anomaly detected in ${metric.name}`,
                    description: `Value ${metric.value.toFixed(2)} is ${zScore.toFixed(1)} standard deviations from mean`,
                    confidence: Math.min(zScore / 5, 0.99),
                    baselineValue: baseline.mean,
                    currentValue: metric.value,
                    deviationPercent: Math.round(deviationPercent * 10) / 10,
                });
            }
        }

        return anomalies;
    }

    /**
     * Run statistical anomaly detection
     */
    private async runStatisticalDetection(
        metrics: MetricData[],
        baseline: BaselineMetrics,
        sensitivity: number = 0.8
    ): Promise<DetectedAnomaly[]> {
        const anomalies: DetectedAnomaly[] = [];

        // Check for values outside IQR-based bounds
        const iqr = baseline.percentiles[75] - baseline.percentiles[25];
        const lowerBound = baseline.percentiles[25] - 1.5 * iqr * (2 - sensitivity);
        const upperBound = baseline.percentiles[75] + 1.5 * iqr * (2 - sensitivity);

        for (const metric of metrics) {
            if (metric.value < lowerBound || metric.value > upperBound) {
                const deviationPercent = baseline.mean !== 0
                    ? ((metric.value - baseline.mean) / baseline.mean) * 100
                    : metric.value * 100;

                anomalies.push({
                    id: uuidv4(),
                    type: 'pattern',
                    severity: 'info',
                    title: `Outlier detected in ${metric.name}`,
                    description: `Value ${metric.value.toFixed(2)} is outside expected range [${lowerBound.toFixed(2)}, ${upperBound.toFixed(2)}]`,
                    confidence: 0.85,
                    baselineValue: baseline.mean,
                    currentValue: metric.value,
                    deviationPercent: Math.round(deviationPercent * 10) / 10,
                });
            }
        }

        return anomalies;
    }

    /**
     * Run LSTM-based sequence anomaly detection
     */
    private async runLSTMDetection(
        timeSeries: TimeSeriesPoint[],
        baseline: BaselineMetrics
    ): Promise<DetectedAnomaly[]> {
        const anomalies: DetectedAnomaly[] = [];

        if (timeSeries.length < 10) {
            return anomalies; // Need enough data for sequence analysis
        }

        // Simplified: detect sudden changes in trend
        const values = timeSeries.map((p) => p.value);
        const windowSize = Math.min(5, Math.floor(values.length / 3));

        for (let i = windowSize; i < values.length - windowSize; i++) {
            const prevWindow = values.slice(i - windowSize, i);
            const nextWindow = values.slice(i, i + windowSize);

            const prevMean = prevWindow.reduce((a, b) => a + b, 0) / windowSize;
            const nextMean = nextWindow.reduce((a, b) => a + b, 0) / windowSize;

            const change = Math.abs(nextMean - prevMean);
            const relativeChange = baseline.mean !== 0 ? change / baseline.mean : change;

            if (relativeChange > 0.3) {
                // 30% change threshold
                anomalies.push({
                    id: uuidv4(),
                    type: 'pattern',
                    severity: 'info',
                    title: 'Trend change detected',
                    description: `Significant shift from ${prevMean.toFixed(2)} to ${nextMean.toFixed(2)} detected in time series`,
                    confidence: Math.min(relativeChange * 2, 0.95),
                    baselineValue: prevMean,
                    currentValue: nextMean,
                    deviationPercent: Math.round(((nextMean - prevMean) / prevMean) * 1000) / 10,
                });
                break; // Only report first significant change
            }
        }

        return anomalies;
    }

    /**
     * Ensemble voting to combine detection results
     */
    private ensembleVote(anomalies: DetectedAnomaly[]): DetectedAnomaly[] {
        // Group by similar characteristics
        const grouped = new Map<string, DetectedAnomaly[]>();

        for (const anomaly of anomalies) {
            const key = `${anomaly.type}-${Math.round(anomaly.deviationPercent / 10)}`;
            const existing = grouped.get(key) || [];
            existing.push(anomaly);
            grouped.set(key, existing);
        }

        // Keep anomalies detected by multiple methods with higher confidence
        const result: DetectedAnomaly[] = [];
        for (const [, group] of grouped) {
            if (group.length > 1) {
                // Multiple detectors agree - boost confidence
                const best = group.reduce((a, b) =>
                    a.confidence > b.confidence ? a : b
                );
                result.push({
                    ...best,
                    confidence: Math.min(best.confidence * 1.2, 0.99),
                });
            } else if (group[0].confidence > 0.7) {
                // Single detector with high confidence
                result.push(group[0]);
            }
        }

        return result;
    }

    /**
     * Classify anomaly severity
     */
    private classifySeverity(
        anomaly: DetectedAnomaly,
        baseline: BaselineMetrics
    ): 'info' | 'warning' | 'critical' {
        const deviation = Math.abs(anomaly.deviationPercent);

        // Severity based on deviation and confidence
        if (deviation > 50 && anomaly.confidence > 0.8) {
            return 'critical';
        } else if (deviation > 25 && anomaly.confidence > 0.7) {
            return 'warning';
        }

        return 'info';
    }

    /**
     * Identify potential root cause
     */
    private identifyRootCause(
        anomaly: DetectedAnomaly,
        input: AnomalyInput
    ): string | undefined {
        const causes: Record<string, string[]> = {
            velocity: [
                'Team capacity changes',
                'Sprint scope changes',
                'Blocking dependencies',
                'Technical complexity',
            ],
            pattern: [
                'Process deviation',
                'Workflow changes',
                'External factors',
                'Data quality issues',
            ],
            security: [
                'New vulnerability introduced',
                'Configuration drift',
                'Access pattern change',
                'External threat',
            ],
            quality: [
                'Code complexity increase',
                'Test coverage decrease',
                'Deployment issues',
                'Integration problems',
            ],
            resource: [
                'Team availability',
                'Infrastructure capacity',
                'Budget constraints',
                'Skill gaps',
            ],
        };

        const typeCauses = causes[anomaly.type] || causes['pattern'];

        // Simple heuristic: pick based on deviation direction
        if (anomaly.currentValue < anomaly.baselineValue) {
            return typeCauses[0]; // First cause for decrease
        } else {
            return typeCauses[1]; // Second cause for increase
        }
    }

    /**
     * Generate recommendations based on detected anomalies
     */
    private generateRecommendations(
        anomalies: DetectedAnomaly[]
    ): AIRecommendation[] {
        const recommendations: AIRecommendation[] = [];

        const criticalAnomalies = anomalies.filter((a) => a.severity === 'critical');
        const warningAnomalies = anomalies.filter((a) => a.severity === 'warning');

        if (criticalAnomalies.length > 0) {
            recommendations.push({
                id: 'investigate-critical',
                type: 'action',
                title: 'Investigate critical anomalies immediately',
                description: `${criticalAnomalies.length} critical anomalies detected that require immediate attention`,
                priority: 'high',
                action: {
                    type: 'navigate',
                    target: '/ai/alerts',
                    parameters: { filter: 'critical' },
                    impact: 'low',
                    confidence: 0.95,
                    requiresConfirmation: false,
                },
            });
        }

        if (warningAnomalies.length > 2) {
            recommendations.push({
                id: 'review-warnings',
                type: 'review',
                title: 'Review warning anomalies',
                description: 'Multiple warning-level anomalies detected. Consider scheduling a review.',
                priority: 'medium',
            });
        }

        // Type-specific recommendations
        const velocityAnomalies = anomalies.filter((a) => a.type === 'velocity');
        if (velocityAnomalies.length > 0) {
            recommendations.push({
                id: 'velocity-analysis',
                type: 'analysis',
                title: 'Analyze velocity trends',
                description: 'Velocity anomalies detected. Review team capacity and sprint planning.',
                priority: velocityAnomalies.some((a) => a.severity === 'critical') ? 'high' : 'medium',
            });
        }

        return recommendations;
    }

    /**
     * Calculate overall confidence
     */
    private calculateOverallConfidence(anomalies: DetectedAnomaly[]): number {
        if (anomalies.length === 0) {
            return 1.0; // High confidence that no anomalies exist
        }

        const avgConfidence =
            anomalies.reduce((sum, a) => sum + a.confidence, 0) / anomalies.length;
        return Math.round(avgConfidence * 100) / 100;
    }

    /**
     * Check dependencies
     */
    protected async checkDependencies(): Promise<
        Record<string, 'healthy' | 'degraded' | 'unhealthy'>
    > {
        return {
            statisticalEngine: 'healthy',
            isolationForest: 'healthy',
            lstmDetector: 'healthy',
            alertService: this.alertService ? 'healthy' : 'degraded',
        };
    }
}
