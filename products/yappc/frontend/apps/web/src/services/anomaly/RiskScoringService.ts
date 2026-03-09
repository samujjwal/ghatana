/**
 * Risk Scoring Service
 *
 * Calculates and manages risk scores for detected security anomalies.
 * Integrates anomaly data with baseline metrics to produce risk assessments.
 *
 * Features:
 * - Risk score calculation from anomaly patterns
 * - Baseline comparison and deviation analysis
 * - Risk trend tracking
 * - Risk metric aggregation
 * - Real-time risk updates
 *
 * @see AnomalyDetectionService for anomaly data
 * @see AnomalyBaseline for baseline comparison
 */

import {
  Injectable,
  Inject,
  Logger,
} from '@nestjs/common';
import { Observable, BehaviorSubject, interval } from 'rxjs';
import { map, switchMap, tap, catchError } from 'rxjs/operators';

/**
 * Risk score calculation input.
 */
export interface RiskScoringInput {
  anomalyCount: number;
  maxAnomaltySeverity: number; // 1-10 scale
  frequencyPerHour: number;
  impactedSystems: number;
  dataExposureRisk: number; // 0-100
  complianceViolations: number;
}

/**
 * Risk score output.
 */
export interface RiskScore {
  id: string;
  tenantId: string;
  timestamp: Date;
  score: number; // 0-100
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  category: string;
  factors: RiskFactor[];
  trend: 'INCREASING' | 'DECREASING' | 'STABLE';
  recommendations: string[];
}

/**
 * Individual risk factor.
 */
export interface RiskFactor {
  name: string;
  weight: number; // 0-1
  value: number; // 0-100
  contribution: number; // 0-100 (contribution to total score)
}

/**
 * Risk scoring baseline for comparison.
 */
export interface RiskBaseline {
  normalRiskScore: number;
  acceptableThreshold: number;
  warningThreshold: number;
  criticalThreshold: number;
  historicalAverage: number;
  historicalStdDev: number;
}

/**
 * Risk metrics aggregate.
 */
export interface RiskMetrics {
  avgRiskScore: number;
  maxRiskScore: number;
  minRiskScore: number;
  standardDeviation: number;
  trend: 'UP' | 'DOWN' | 'STABLE';
  anomalyCount: number;
  riskEvents: number;
  criticalEvents: number;
}

/**
 * Service for calculating and managing risk scores.
 */
@Injectable()
export class RiskScoringService {
  private readonly logger = new Logger(RiskScoringService.name);
  
  private riskScores = new Map<string, RiskScore>();
  private riskHistory = new Map<string, RiskScore[]>();
  private riskMetrics = new Map<string, RiskMetrics>();
  private riskBaselines = new Map<string, RiskBaseline>();
  private riskScoreSubject = new BehaviorSubject<RiskScore | null>(null);

  constructor(
    @Inject('ANOMALY_DETECTION_SERVICE') private anomalyService: unknown,
  ) {
    this.initializeRiskBaselines();
  }

  /**
   * Initialize default risk baselines.
   * Can be overridden per tenant for customization.
   */
  private initializeRiskBaselines(): void {
    const defaultBaseline: RiskBaseline = {
      normalRiskScore: 20,
      acceptableThreshold: 35,
      warningThreshold: 60,
      criticalThreshold: 80,
      historicalAverage: 25,
      historicalStdDev: 10,
    };

    // Store for default tenant
    this.riskBaselines.set('default', defaultBaseline);
  }

  /**
   * Calculate risk score from anomaly input.
   *
   * Algorithm:
   * 1. Weight individual risk factors
   * 2. Apply multipliers for severity and frequency
   * 3. Normalize to 0-100 scale
   * 4. Compare against baseline
   * 5. Determine trend
   *
   * @param tenantId - Tenant identifier
   * @param category - Risk category (security, performance, compliance)
   * @param input - Risk scoring input parameters
   * @returns Calculated risk score object
   */
  calculateRiskScore(
    tenantId: string,
    category: string,
    input: RiskScoringInput,
  ): RiskScore {
    this.logger.debug(`Calculating risk score for tenant ${tenantId}, category ${category}`);

    // Define risk factors with weights
    const factors: RiskFactor[] = [
      {
        name: 'Anomaly Count',
        weight: 0.2,
        value: Math.min(input.anomalyCount * 10, 100),
        contribution: 0,
      },
      {
        name: 'Severity Level',
        weight: 0.25,
        value: input.maxAnomaltySeverity * 10,
        contribution: 0,
      },
      {
        name: 'Frequency',
        weight: 0.2,
        value: Math.min(input.frequencyPerHour * 5, 100),
        contribution: 0,
      },
      {
        name: 'System Impact',
        weight: 0.15,
        value: Math.min(input.impactedSystems * 10, 100),
        contribution: 0,
      },
      {
        name: 'Data Exposure',
        weight: 0.1,
        value: input.dataExposureRisk,
        contribution: 0,
      },
      {
        name: 'Compliance Violations',
        weight: 0.1,
        value: Math.min(input.complianceViolations * 15, 100),
        contribution: 0,
      },
    ];

    // Calculate weighted score
    let totalScore = 0;
    for (const factor of factors) {
      factor.contribution = factor.value * factor.weight * 100;
      totalScore += factor.contribution;
    }

    // Normalize to 0-100 scale
    const normalizedScore = Math.min(Math.round(totalScore / 100), 100);

    // Determine severity
    const severity = this.determineSeverity(normalizedScore);

    // Determine trend
    const trend = this.determineTrend(tenantId, normalizedScore);

    // Get recommendations
    const recommendations = this.generateRecommendations(
      normalizedScore,
      factors,
      category,
    );

    const riskScore: RiskScore = {
      id: `risk-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      tenantId,
      timestamp: new Date(),
      score: normalizedScore,
      severity,
      category,
      factors,
      trend,
      recommendations,
    };

    // Store and update history
    this.storeRiskScore(tenantId, riskScore);
    this.updateRiskMetrics(tenantId);

    // Emit update
    this.riskScoreSubject.next(riskScore);

    this.logger.debug(
      `Risk score calculated: ${normalizedScore} (${severity}) for ${category}`,
    );

    return riskScore;
  }

  /**
   * Determine severity level from score.
   */
  private determineSeverity(score: number): 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' {
    if (score >= 80) return 'CRITICAL';
    if (score >= 60) return 'HIGH';
    if (score >= 35) return 'MEDIUM';
    return 'LOW';
  }

  /**
   * Determine trend compared to historical data.
   */
  private determineTrend(
    tenantId: string,
    currentScore: number,
  ): 'INCREASING' | 'DECREASING' | 'STABLE' {
    const history = this.riskHistory.get(tenantId) || [];
    if (history.length < 2) return 'STABLE';

    const recentScores = history.slice(-10).map((r) => r.score);
    const avgRecent = recentScores.reduce((a, b) => a + b, 0) / recentScores.length;

    const diff = currentScore - avgRecent;
    const threshold = 5; // 5 point threshold for trend detection

    if (diff > threshold) return 'INCREASING';
    if (diff < -threshold) return 'DECREASING';
    return 'STABLE';
  }

  /**
   * Generate recommendations based on risk factors.
   */
  private generateRecommendations(
    score: number,
    factors: RiskFactor[],
    category: string,
  ): string[] {
    const recommendations: string[] = [];

    if (score >= 80) {
      recommendations.push('CRITICAL: Immediate investigation and remediation required');
      recommendations.push('Escalate to security incident response team');
    }

    if (score >= 60) {
      recommendations.push('HIGH: Priority investigation recommended');
      recommendations.push('Review affected systems and access logs');
    }

    // Factor-specific recommendations
    const topFactors = factors.sort((a, b) => b.contribution - a.contribution).slice(0, 3);
    for (const factor of topFactors) {
      if (factor.name === 'Severity Level' && factor.value > 70) {
        recommendations.push(`High severity anomalies detected: ${factor.name}`);
      }
      if (factor.name === 'Frequency' && factor.value > 70) {
        recommendations.push('Anomaly frequency is above normal threshold - enable enhanced monitoring');
      }
      if (factor.name === 'Data Exposure' && factor.value > 70) {
        recommendations.push('Data exposure risk detected - review access controls');
      }
    }

    return recommendations;
  }

  /**
   * Store risk score in history.
   */
  private storeRiskScore(tenantId: string, riskScore: RiskScore): void {
    // Store in current scores
    this.riskScores.set(riskScore.id, riskScore);

    // Store in history (keep last 1000)
    const history = this.riskHistory.get(tenantId) || [];
    history.push(riskScore);
    if (history.length > 1000) {
      history.shift(); // Remove oldest
    }
    this.riskHistory.set(tenantId, history);
  }

  /**
   * Update aggregated risk metrics.
   */
  private updateRiskMetrics(tenantId: string): void {
    const history = this.riskHistory.get(tenantId) || [];
    if (history.length === 0) return;

    const scores = history.map((r) => r.score);
    const sum = scores.reduce((a, b) => a + b, 0);
    const avg = sum / scores.length;
    const variance =
      scores.reduce((a, b) => a + Math.pow(b - avg, 2), 0) / scores.length;
    const stdDev = Math.sqrt(variance);

    const metrics: RiskMetrics = {
      avgRiskScore: Math.round(avg),
      maxRiskScore: Math.max(...scores),
      minRiskScore: Math.min(...scores),
      standardDeviation: Math.round(stdDev),
      trend: this.calculateMetricsTrend(history),
      anomalyCount: history.length,
      riskEvents: history.filter((r) => r.score >= 60).length,
      criticalEvents: history.filter((r) => r.severity === 'CRITICAL').length,
    };

    this.riskMetrics.set(tenantId, metrics);
  }

  /**
   * Calculate metrics trend.
   */
  private calculateMetricsTrend(history: RiskScore[]): 'UP' | 'DOWN' | 'STABLE' {
    if (history.length < 2) return 'STABLE';

    const recent = history.slice(-20);
    const avgFirst = recent.slice(0, 10).reduce((a, b) => a + b.score, 0) / 10;
    const avgLast = recent.slice(-10).reduce((a, b) => a + b.score, 0) / 10;

    const diff = avgLast - avgFirst;
    if (diff > 5) return 'UP';
    if (diff < -5) return 'DOWN';
    return 'STABLE';
  }

  /**
   * Get current risk score by ID.
   */
  getRiskScoreById(id: string): RiskScore | undefined {
    return this.riskScores.get(id);
  }

  /**
   * Get all risk scores for tenant.
   */
  getRiskScoresForTenant(tenantId: string): RiskScore[] {
    return this.riskHistory.get(tenantId) || [];
  }

  /**
   * Get latest risk scores for tenant by category.
   */
  getLatestRiskScoreByCategory(
    tenantId: string,
    category: string,
  ): RiskScore | undefined {
    const history = this.riskHistory.get(tenantId) || [];
    return history.reverse().find((r) => r.category === category);
  }

  /**
   * Get aggregated risk metrics for tenant.
   */
  getRiskMetrics(tenantId: string): RiskMetrics | undefined {
    return this.riskMetrics.get(tenantId);
  }

  /**
   * Get risk baseline for tenant.
   */
  getRiskBaseline(tenantId: string): RiskBaseline {
    return (
      this.riskBaselines.get(tenantId) || this.riskBaselines.get('default')!
    );
  }

  /**
   * Update risk baseline for tenant.
   */
  updateRiskBaseline(tenantId: string, baseline: Partial<RiskBaseline>): void {
    const existing = this.getRiskBaseline(tenantId);
    this.riskBaselines.set(tenantId, { ...existing, ...baseline });
    this.logger.debug(`Risk baseline updated for tenant ${tenantId}`);
  }

  /**
   * Get risk score stream for real-time updates.
   */
  getRiskScoreStream(): Observable<RiskScore> {
    return this.riskScoreSubject.asObservable().pipe(
      switchMap((score) => {
        // Emit updates every minute or on new score
        return interval(60000).pipe(
          map(() => score),
          catchError((error) => {
            this.logger.error('Error in risk score stream', error);
            return [];
          }),
        );
      }),
    );
  }

  /**
   * Batch update multiple risk scores.
   */
  updateRiskScores(
    tenantId: string,
    scores: Array<{ category: string; input: RiskScoringInput }>,
  ): RiskScore[] {
    const results: RiskScore[] = [];
    for (const item of scores) {
      const score = this.calculateRiskScore(tenantId, item.category, item.input);
      results.push(score);
    }
    return results;
  }

  /**
   * Get risk score history for date range.
   */
  getRiskScoreHistory(
    tenantId: string,
    startDate: Date,
    endDate: Date,
  ): RiskScore[] {
    const history = this.riskHistory.get(tenantId) || [];
    return history.filter(
      (r) => r.timestamp >= startDate && r.timestamp <= endDate,
    );
  }

  /**
   * Clear old risk scores (older than days).
   */
  clearOldRiskScores(tenantId: string, days: number): number {
    const history = this.riskHistory.get(tenantId) || [];
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - days);

    const original = history.length;
    const filtered = history.filter((r) => r.timestamp > cutoffDate);

    this.riskHistory.set(tenantId, filtered);
    return original - filtered.length;
  }
}
