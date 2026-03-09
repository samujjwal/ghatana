/**
 * Types for useAIInsights
 */
import type { PerformanceMetric } from '../../performance/usePerformanceMonitoring';

/**
 * PredictionModel
 *
 * Represents a trained machine learning model for predictions.
 */
export interface PredictionModel {
  id: string;
  name: string;
  description: string;
  accuracy: number; // 0-1 scale
  lastTrained: Date;
  features: string[];
  type: 'regression' | 'classification' | 'time-series';
}

/**
 * BuildTimePrediction
 *
 * Predicted build time with confidence and contributing factors.
 */
export interface BuildTimePrediction {
  predictedTime: number;
  confidence: number;
  factors: {
    name: string;
    impact: number; // -1 to 1 scale
    description: string;
  }[];
  historicalAccuracy: number;
  range: {
    min: number;
    max: number;
  };
}

/**
 * DeploymentRiskAssessment
 *
 * Comprehensive risk assessment for a proposed deployment with mitigations.
 */
export interface DeploymentRiskAssessment {
  riskLevel: 'low' | 'medium' | 'high' | 'critical';
  riskScore: number; // 0-100 scale
  riskFactors: {
    factor: string;
    weight: number;
    description: string;
    mitigation?: string;
  }[];
  recommendations: string[];
  confidence: number;
}

/**
 * OptimizationRecommendation
 *
 * A specific recommendation for optimizing build, deployment, or system performance.
 */
export interface OptimizationRecommendation {
  id: string;
  type: 'performance' | 'resource' | 'process' | 'configuration';
  priority: 'low' | 'medium' | 'high' | 'critical';
  title: string;
  description: string;
  expectedImpact: {
    metric: string;
    improvement: number; // percentage
    timeframe: string;
  };
  implementation: {
    effort: 'low' | 'medium' | 'high';
    steps: string[];
    prerequisites?: string[];
  };
  confidence: number;
}

/**
 * AIInsightAnalysis
 *
 * Complete results of a comprehensive AI analysis run (predictions, risks, and recommendations).
 */
export interface AIInsightAnalysis {
  buildTimePrediction: BuildTimePrediction | null;
  deploymentRisk: DeploymentRiskAssessment | null;
  recommendations: OptimizationRecommendation[];
  patterns: {
    detected: string[];
    anomalies: string[];
    trends: string[];
  };
  lastAnalysis: Date;
}

/** Type guard for AIInsightAnalysis */
export function isAIInsightAnalysis(obj: unknown): obj is AIInsightAnalysis {
  return (
    obj !== null &&
    typeof obj === 'object' &&
    'buildTimePrediction' in obj &&
    'deploymentRisk' in obj &&
    'recommendations' in obj &&
    'patterns' in obj &&
    'lastAnalysis' in obj &&
    Array.isArray(obj.recommendations) &&
    typeof obj.patterns === 'object' &&
    obj.patterns !== null &&
    Array.isArray(obj.patterns.detected) &&
    Array.isArray(obj.patterns.anomalies) &&
    Array.isArray(obj.patterns.trends) &&
    obj.lastAnalysis instanceof Date
  );
}

/**
 * AIInsightsOptions
 *
 * Configuration options for the useAIInsights hook.
 */
export interface AIInsightsOptions {
  enablePredictions?: boolean;
  enableRiskAssessment?: boolean;
  enableRecommendations?: boolean;
  analysisInterval?: number; // milliseconds
  historicalDays?: number;
  confidenceThreshold?: number;
  maxRecommendations?: number;
}

/**
 * UseAIInsightsResult
 *
 * Return value of the useAIInsights hook with all methods and state.
 */
export interface UseAIInsightsResult {
  insights: AIInsightAnalysis | null;
  models: PredictionModel[];
  predictBuildTime: (
    context: Record<string, unknown>
  ) => Promise<BuildTimePrediction>;
  assessDeploymentRisk: (
    context: Record<string, unknown>
  ) => Promise<DeploymentRiskAssessment>;
  getRecommendations: (
    type?: OptimizationRecommendation['type']
  ) => OptimizationRecommendation[];
  implementRecommendation: (id: string) => Promise<void>;
  dismissRecommendation: (id: string) => void;
  runAnalysis: () => Promise<void>;
  trainModel: (modelId: string, data: PerformanceMetric[]) => Promise<void>;
  isAnalyzing: boolean;
  isTraining: boolean;
  error: string | null;
  recommendationsByType: Record<string, OptimizationRecommendation[]>;
}
