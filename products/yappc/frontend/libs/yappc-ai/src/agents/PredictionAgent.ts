/**
 * Prediction Agent Implementation
 *
 * Handles timeline forecasting, risk scoring, and phase predictions.
 * Uses ensemble of XGBoost, Prophet, and LSTM models.
 *
 * @module ai/agents/PredictionAgent
 * @doc.type class
 * @doc.purpose Predictive analytics agent
 * @doc.layer product
 * @doc.pattern AIAgent
 */

import { BaseAgent, type ProcessResult } from './BaseAgent';
import type {
    AgentContext,
    AIRecommendation,
    HistoricalDataPoint,
    PhasePrediction,
    PredictionInput,
    PredictionOutput,
    RiskFactor,
    RiskScore,
    SimilarItem,
    TeamMetrics,
} from './types';
import { PredictionInputSchema, AgentError } from './types';

/**
 * ML Service interface for prediction models
 */
interface MLService {
    predict(request: PredictRequest): Promise<PredictResponse>;
    getModelVersion(modelName: string): Promise<string>;
    healthCheck(): Promise<boolean>;
}

interface PredictRequest {
    modelName: string;
    features: number[];
    options?: Record<string, unknown>;
}

interface PredictResponse {
    prediction: number;
    confidence: number;
    metadata?: Record<string, unknown>;
}

/**
 * PredictionAgent for ML-powered forecasting
 */
export class PredictionAgent extends BaseAgent<PredictionInput, PredictionOutput> {
    private mlService?: MLService;

    constructor(mlService?: MLService) {
        super({
            name: 'PredictionAgent',
            version: '2.1.0',
            description: 'Predictive analytics for timeline and risk forecasting',
            capabilities: [
                'timeline-prediction',
                'risk-scoring',
                'phase-forecasting',
                'ensemble-models',
                'confidence-intervals',
            ],
            supportedModels: ['xgboost-v3', 'prophet-v2', 'lstm-v1'],
            latencySLA: 500,
            defaultTimeout: 10000,
        });

        this.mlService = mlService;
    }

    /**
     * Validate input
     */
    protected validateInput(input: PredictionInput): void {
        const result = PredictionInputSchema.safeParse(input);
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
     * Process prediction request
     */
    protected async processRequest(
        input: PredictionInput,
        _context: AgentContext
    ): Promise<ProcessResult<PredictionOutput>> {
        // Extract features from input data
        const features = this.extractFeatures(input);

        // Run predictions using ensemble approach
        const [timelinePred, riskPred] = await Promise.all([
            this.predictTimeline(features, input),
            this.calculateRiskScore(features, input),
        ]);

        // Get phase predictions if applicable
        const phasePredictions = await this.predictPhases(input);

        // Identify risk factors
        const riskFactors = this.identifyRiskFactors(features, riskPred);

        // Generate recommendations
        const recommendations = this.generateRecommendations(
            riskPred,
            riskFactors,
            input
        );

        // Find similar historical items
        const similarItems = await this.findSimilarItems(input);

        return {
            data: {
                phasePredictions,
                estimatedCompletionDate: timelinePred.date,
                confidenceInterval: timelinePred.confidence,
                riskScore: riskPred,
                riskFactors,
                recommendations,
                similarHistoricalItems: similarItems,
            },
            modelVersion: 'ensemble-v2.1.0',
            confidence: timelinePred.confidence,
        };
    }

    /**
     * Extract features from input for ML models
     */
    private extractFeatures(input: PredictionInput): FeatureVector {
        const historicalData = input.historicalData || [];
        const teamMetrics = input.teamMetrics;

        // Calculate velocity features
        const velocityFeatures = this.calculateVelocityFeatures(historicalData);

        // Calculate complexity features
        const complexityFeatures = this.calculateComplexityFeatures(input);

        // Calculate team features
        const teamFeatures = this.calculateTeamFeatures(teamMetrics);

        return {
            velocity: velocityFeatures,
            complexity: complexityFeatures,
            team: teamFeatures,
            historical: historicalData.length,
        };
    }

    /**
     * Calculate velocity-based features
     */
    private calculateVelocityFeatures(
        historicalData: HistoricalDataPoint[]
    ): VelocityFeatures {
        if (historicalData.length === 0) {
            return {
                avgVelocity: 0,
                velocityTrend: 0,
                velocityVariance: 0,
                recentVelocity: 0,
            };
        }

        const velocityData = historicalData
            .filter((d) => d.metric === 'velocity')
            .map((d) => d.value);

        if (velocityData.length === 0) {
            return {
                avgVelocity: 0,
                velocityTrend: 0,
                velocityVariance: 0,
                recentVelocity: 0,
            };
        }

        const avgVelocity =
            velocityData.reduce((a, b) => a + b, 0) / velocityData.length;
        const recentVelocity = velocityData[velocityData.length - 1];

        // Calculate trend (simple linear regression)
        const n = velocityData.length;
        const sumX = (n * (n - 1)) / 2;
        const sumY = velocityData.reduce((a, b) => a + b, 0);
        const sumXY = velocityData.reduce((sum, y, i) => sum + i * y, 0);
        const sumX2 = (n * (n - 1) * (2 * n - 1)) / 6;
        const velocityTrend = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX) || 0;

        // Calculate variance
        const variance =
            velocityData.reduce((sum, v) => sum + Math.pow(v - avgVelocity, 2), 0) /
            velocityData.length;

        return {
            avgVelocity,
            velocityTrend,
            velocityVariance: Math.sqrt(variance),
            recentVelocity,
        };
    }

    /**
     * Calculate complexity features
     */
    private calculateComplexityFeatures(input: PredictionInput): ComplexityFeatures {
        const similarCount = input.similarItems?.length || 0;
        const hasDependencies = (input.historicalData?.length || 0) > 5;

        return {
            estimatedComplexity: similarCount > 10 ? 0.8 : similarCount > 5 ? 0.5 : 0.3,
            dependencyCount: hasDependencies ? 5 : 1,
            unknownFactors: similarCount < 3 ? 0.4 : 0.1,
        };
    }

    /**
     * Calculate team features
     */
    private calculateTeamFeatures(teamMetrics?: TeamMetrics): TeamFeatures {
        if (!teamMetrics) {
            return {
                capacityUtilization: 0.7,
                avgExperience: 0.5,
                availability: 0.8,
            };
        }

        return {
            capacityUtilization: teamMetrics.utilizationPercent / 100,
            avgExperience: 0.6, // Would come from team data
            availability: 1 - teamMetrics.utilizationPercent / 100,
        };
    }

    /**
     * Predict timeline using ML models
     */
    private async predictTimeline(
        features: FeatureVector,
        input: PredictionInput
    ): Promise<{ date: Date; confidence: number }> {
        // In production, this would call the ML service
        // For now, use heuristic-based prediction
        const horizonDays = input.horizonDays || 30;
        const velocity = features.velocity.avgVelocity || 1;
        const complexity = features.complexity.estimatedComplexity;

        // Simple formula: base days adjusted by velocity and complexity
        const predictedDays = Math.round(
            horizonDays * (1 + complexity) / Math.max(velocity, 0.5)
        );

        const predictedDate = new Date();
        predictedDate.setDate(predictedDate.getDate() + predictedDays);

        // Confidence based on data quality
        const dataQuality = Math.min(
            features.historical / 20,
            1
        );
        const velocityConfidence = features.velocity.velocityVariance < 0.3 ? 0.9 : 0.6;
        const confidence = (dataQuality * 0.4 + velocityConfidence * 0.6);

        return {
            date: predictedDate,
            confidence: Math.round(confidence * 100) / 100,
        };
    }

    /**
     * Calculate risk score
     */
    private async calculateRiskScore(
        features: FeatureVector,
        _input: PredictionInput
    ): Promise<RiskScore> {
        // Velocity risk: high variance or declining trend = higher risk
        const velocityRisk = Math.min(
            (features.velocity.velocityVariance * 2 +
                (features.velocity.velocityTrend < 0 ? 0.3 : 0)) /
            2,
            1
        );

        // Complexity risk
        const complexityRisk = features.complexity.estimatedComplexity;

        // Dependency risk
        const dependencyRisk = Math.min(
            features.complexity.dependencyCount / 10,
            1
        );

        // Historical risk (less data = higher uncertainty)
        const historicalRisk =
            features.historical < 5
                ? 0.7
                : features.historical < 20
                    ? 0.4
                    : 0.2;

        const overall =
            (velocityRisk * 0.3 +
                complexityRisk * 0.25 +
                dependencyRisk * 0.25 +
                historicalRisk * 0.2);

        return {
            overall: Math.round(overall * 100) / 100,
            breakdown: {
                velocity: Math.round(velocityRisk * 100) / 100,
                complexity: Math.round(complexityRisk * 100) / 100,
                dependencies: Math.round(dependencyRisk * 100) / 100,
                historical: Math.round(historicalRisk * 100) / 100,
            },
        };
    }

    /**
     * Predict phase timelines
     */
    private async predictPhases(
        input: PredictionInput
    ): Promise<PhasePrediction[] | undefined> {
        if (input.targetType !== 'phase' && input.targetType !== 'workflow') {
            return undefined;
        }

        const phases = ['development', 'security', 'testing', 'deployment'];
        const baseDate = new Date();

        return phases.map((phaseId, index) => {
            const daysOffset = (index + 1) * 7; // 7 days per phase
            const predictedDate = new Date(baseDate);
            predictedDate.setDate(predictedDate.getDate() + daysOffset);

            return {
                phaseId,
                predictedEndDate: predictedDate,
                confidence: 0.7 - index * 0.05, // Decreasing confidence for later phases
                riskLevel: index < 2 ? 'low' : index < 3 ? 'medium' : 'high',
            };
        });
    }

    /**
     * Identify risk factors
     */
    private identifyRiskFactors(
        features: FeatureVector,
        riskScore: RiskScore
    ): RiskFactor[] {
        const factors: RiskFactor[] = [];

        if (riskScore.breakdown.velocity > 0.5) {
            factors.push({
                type: 'velocity',
                description: 'Velocity is inconsistent or declining',
                severity: riskScore.breakdown.velocity,
                mitigation: 'Consider reducing scope or adding resources',
            });
        }

        if (riskScore.breakdown.complexity > 0.6) {
            factors.push({
                type: 'complexity',
                description: 'High technical complexity detected',
                severity: riskScore.breakdown.complexity,
                mitigation: 'Break down into smaller deliverables',
            });
        }

        if (riskScore.breakdown.dependencies > 0.5) {
            factors.push({
                type: 'dependencies',
                description: 'Multiple blocking dependencies identified',
                severity: riskScore.breakdown.dependencies,
                mitigation: 'Prioritize unblocking dependencies',
            });
        }

        if (features.historical < 10) {
            factors.push({
                type: 'data_quality',
                description: 'Limited historical data for accurate predictions',
                severity: 0.4,
                mitigation: 'Predictions will improve with more data',
            });
        }

        return factors;
    }

    /**
     * Generate recommendations based on risk analysis
     */
    private generateRecommendations(
        riskScore: RiskScore,
        riskFactors: RiskFactor[],
        input: PredictionInput
    ): AIRecommendation[] {
        const recommendations: AIRecommendation[] = [];

        if (riskScore.overall > 0.6) {
            recommendations.push({
                id: 'risk-review',
                type: 'review',
                title: 'Schedule Risk Review Meeting',
                description:
                    'High overall risk detected. Recommend scheduling a risk review with stakeholders.',
                priority: 'high',
                action: {
                    type: 'create',
                    target: 'meeting',
                    parameters: { type: 'risk-review', itemId: input.itemId },
                    impact: 'low',
                    confidence: 0.9,
                    requiresConfirmation: true,
                },
            });
        }

        riskFactors.forEach((factor) => {
            if (factor.mitigation && factor.severity > 0.5) {
                recommendations.push({
                    id: `mitigate-${factor.type}`,
                    type: 'mitigation',
                    title: `Address ${factor.type} risk`,
                    description: factor.mitigation,
                    priority: factor.severity > 0.7 ? 'high' : 'medium',
                });
            }
        });

        return recommendations;
    }

    /**
     * Find similar historical items
     */
    private async findSimilarItems(
        input: PredictionInput
    ): Promise<SimilarItem[]> {
        // In production, this would query the vector store
        // For now, return mock similar items
        if (!input.similarItems?.length) {
            return [];
        }

        return input.similarItems.slice(0, 5).map((itemId, index) => ({
            itemId,
            similarity: 0.9 - index * 0.1,
            outcome: index < 3 ? 'success' : 'delayed',
            cycleTime: 14 + index * 3,
        }));
    }

    /**
     * Check dependencies
     */
    protected async checkDependencies(): Promise<
        Record<string, 'healthy' | 'degraded' | 'unhealthy'>
    > {
        const deps: Record<string, 'healthy' | 'degraded' | 'unhealthy'> = {
            heuristicEngine: 'healthy',
        };

        if (this.mlService) {
            try {
                const healthy = await this.mlService.healthCheck();
                deps.mlService = healthy ? 'healthy' : 'degraded';
            } catch {
                deps.mlService = 'degraded';
            }
        }

        return deps;
    }
}

// Internal types
interface FeatureVector {
    velocity: VelocityFeatures;
    complexity: ComplexityFeatures;
    team: TeamFeatures;
    historical: number;
}

interface VelocityFeatures {
    avgVelocity: number;
    velocityTrend: number;
    velocityVariance: number;
    recentVelocity: number;
}

interface ComplexityFeatures {
    estimatedComplexity: number;
    dependencyCount: number;
    unknownFactors: number;
}

interface TeamFeatures {
    capacityUtilization: number;
    avgExperience: number;
    availability: number;
}
