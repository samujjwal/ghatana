/**
 * Risk Scoring Service
 *
 * Computes risk scores for children based on their digital activity patterns.
 * Provides multi-dimensional risk assessment including:
 * - Digital overuse risk (excessive screen time)
 * - Schedule compliance risk (usage during blocked hours)
 * - Content risk (attempts to access blocked/risky content)
 * - Social risk (patterns indicating concerning social behavior)
 *
 * @doc.type service
 * @doc.purpose Risk scoring and insights for parental monitoring
 * @doc.layer backend
 * @doc.pattern Service
 */

import { query } from '../db';
import { logger } from '../utils/logger';

/**
 * Risk dimension scores
 */
export interface RiskDimensions {
    /** Excessive screen time risk (0-100) */
    digitalOveruse: number;
    /** Usage during blocked hours risk (0-100) */
    scheduleCompliance: number;
    /** Attempts to access risky content (0-100) */
    contentRisk: number;
    /** Concerning social patterns (0-100) */
    socialRisk: number;
}

/**
 * Risk factor contributing to a score
 */
export interface RiskFactor {
    dimension: keyof RiskDimensions;
    factor: string;
    severity: 'low' | 'medium' | 'high';
    description: string;
    value?: number;
}

/**
 * Complete risk assessment for a child
 */
export interface ChildRiskAssessment {
    childId: string;
    childName: string;
    /** Overall risk score (0-100) */
    overallScore: number;
    /** Risk bucket for quick categorization */
    riskBucket: 'low' | 'medium' | 'high' | 'critical';
    /** Individual dimension scores */
    dimensions: RiskDimensions;
    /** Contributing factors */
    factors: RiskFactor[];
    /** AI-generated insights (if available) */
    insights: string[];
    /** Recommended actions */
    recommendations: string[];
    /** Assessment timestamp */
    assessedAt: string;
    /** Data window used for assessment */
    dataWindow: {
        start: string;
        end: string;
    };
}

/**
 * Risk thresholds for bucketing
 */
const RISK_THRESHOLDS = {
    low: 25,
    medium: 50,
    high: 75,
};

/**
 * Calculate risk bucket from score
 */
function getRiskBucket(score: number): 'low' | 'medium' | 'high' | 'critical' {
    if (score < RISK_THRESHOLDS.low) return 'low';
    if (score < RISK_THRESHOLDS.medium) return 'medium';
    if (score < RISK_THRESHOLDS.high) return 'high';
    return 'critical';
}

/**
 * Get usage statistics for a child over a time window
 */
async function getUsageStats(childId: string, startDate: Date, endDate: Date): Promise<{
    totalMinutes: number;
    avgDailyMinutes: number;
    peakHour: number;
    sessionCount: number;
}> {
    const result = await query<{
        total_seconds: string;
        session_count: string;
        peak_hour: number;
    }>(
        `SELECT 
       COALESCE(SUM(duration_seconds), 0) as total_seconds,
       COUNT(*) as session_count,
       EXTRACT(HOUR FROM start_time) as peak_hour
     FROM usage_sessions us
     JOIN devices d ON us.device_id = d.id
     WHERE d.child_id = $1
       AND us.start_time >= $2
       AND us.start_time < $3
     GROUP BY EXTRACT(HOUR FROM start_time)
     ORDER BY SUM(duration_seconds) DESC
     LIMIT 1`,
        [childId, startDate.toISOString(), endDate.toISOString()]
    );

    const totalResult = await query<{ total_seconds: string; session_count: string }>(
        `SELECT 
       COALESCE(SUM(duration_seconds), 0) as total_seconds,
       COUNT(*) as session_count
     FROM usage_sessions us
     JOIN devices d ON us.device_id = d.id
     WHERE d.child_id = $1
       AND us.start_time >= $2
       AND us.start_time < $3`,
        [childId, startDate.toISOString(), endDate.toISOString()]
    );

    const totalSeconds = parseInt(totalResult[0]?.total_seconds || '0', 10);
    const sessionCount = parseInt(totalResult[0]?.session_count || '0', 10);
    const days = Math.max(1, Math.ceil((endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24)));

    return {
        totalMinutes: Math.round(totalSeconds / 60),
        avgDailyMinutes: Math.round(totalSeconds / 60 / days),
        peakHour: result[0]?.peak_hour ?? 12,
        sessionCount,
    };
}

/**
 * Get block event statistics for a child
 */
async function getBlockStats(childId: string, startDate: Date, endDate: Date): Promise<{
    totalBlocks: number;
    uniqueDomains: number;
    categoryCounts: Record<string, number>;
}> {
    const result = await query<{
        total_blocks: string;
        unique_domains: string;
    }>(
        `SELECT 
       COUNT(*) as total_blocks,
       COUNT(DISTINCT blocked_item) as unique_domains
     FROM block_events be
     JOIN devices d ON be.device_id = d.id
     WHERE d.child_id = $1
       AND be.timestamp >= $2
       AND be.timestamp < $3`,
        [childId, startDate.toISOString(), endDate.toISOString()]
    );

    const categoryResult = await query<{ category: string; count: string }>(
        `SELECT 
       COALESCE(category, 'unknown') as category,
       COUNT(*) as count
     FROM block_events be
     JOIN devices d ON be.device_id = d.id
     WHERE d.child_id = $1
       AND be.timestamp >= $2
       AND be.timestamp < $3
     GROUP BY category`,
        [childId, startDate.toISOString(), endDate.toISOString()]
    );

    const categoryCounts: Record<string, number> = {};
    for (const row of categoryResult) {
        categoryCounts[row.category] = parseInt(row.count, 10);
    }

    return {
        totalBlocks: parseInt(result[0]?.total_blocks || '0', 10),
        uniqueDomains: parseInt(result[0]?.unique_domains || '0', 10),
        categoryCounts,
    };
}

/**
 * Calculate digital overuse risk score
 */
function calculateDigitalOveruseRisk(
    avgDailyMinutes: number,
    ageYears: number
): { score: number; factors: RiskFactor[] } {
    const factors: RiskFactor[] = [];

    // Age-appropriate screen time limits (AAP guidelines approximation)
    const recommendedMaxMinutes = ageYears < 6 ? 60 : ageYears < 12 ? 120 : 180;

    const ratio = avgDailyMinutes / recommendedMaxMinutes;
    let score = Math.min(100, Math.round(ratio * 50));

    if (ratio > 2) {
        factors.push({
            dimension: 'digitalOveruse',
            factor: 'excessive_screen_time',
            severity: 'high',
            description: `Average daily usage (${avgDailyMinutes} min) is more than double the recommended limit`,
            value: avgDailyMinutes,
        });
        score = Math.min(100, score + 25);
    } else if (ratio > 1.5) {
        factors.push({
            dimension: 'digitalOveruse',
            factor: 'high_screen_time',
            severity: 'medium',
            description: `Average daily usage (${avgDailyMinutes} min) exceeds recommended limit by 50%`,
            value: avgDailyMinutes,
        });
        score = Math.min(100, score + 15);
    } else if (ratio > 1) {
        factors.push({
            dimension: 'digitalOveruse',
            factor: 'above_limit_screen_time',
            severity: 'low',
            description: `Average daily usage (${avgDailyMinutes} min) slightly exceeds recommended limit`,
            value: avgDailyMinutes,
        });
    }

    return { score, factors };
}

/**
 * Calculate content risk score based on block events
 */
function calculateContentRisk(
    blockStats: { totalBlocks: number; uniqueDomains: number; categoryCounts: Record<string, number> }
): { score: number; factors: RiskFactor[] } {
    const factors: RiskFactor[] = [];
    let score = 0;

    // High-risk categories
    const highRiskCategories = ['adult', 'gambling', 'violence', 'drugs'];
    const mediumRiskCategories = ['social', 'gaming'];

    for (const category of highRiskCategories) {
        const count = blockStats.categoryCounts[category] || 0;
        if (count > 0) {
            score += Math.min(30, count * 10);
            factors.push({
                dimension: 'contentRisk',
                factor: `${category}_attempts`,
                severity: count > 5 ? 'high' : count > 2 ? 'medium' : 'low',
                description: `${count} attempt(s) to access ${category} content`,
                value: count,
            });
        }
    }

    for (const category of mediumRiskCategories) {
        const count = blockStats.categoryCounts[category] || 0;
        if (count > 10) {
            score += Math.min(15, Math.floor(count / 5) * 5);
            factors.push({
                dimension: 'contentRisk',
                factor: `frequent_${category}_blocks`,
                severity: 'low',
                description: `${count} blocks for ${category} content`,
                value: count,
            });
        }
    }

    // Overall block frequency
    if (blockStats.totalBlocks > 50) {
        score += 15;
        factors.push({
            dimension: 'contentRisk',
            factor: 'high_block_frequency',
            severity: 'medium',
            description: `High number of blocked access attempts (${blockStats.totalBlocks})`,
            value: blockStats.totalBlocks,
        });
    }

    return { score: Math.min(100, score), factors };
}

/**
 * Calculate schedule compliance risk
 */
async function calculateScheduleComplianceRisk(
    childId: string,
    startDate: Date,
    endDate: Date
): Promise<{ score: number; factors: RiskFactor[] }> {
    const factors: RiskFactor[] = [];

    // Check for late-night usage (after 10 PM, before 6 AM)
    const lateNightResult = await query<{ count: string }>(
        `SELECT COUNT(*) as count
     FROM usage_sessions us
     JOIN devices d ON us.device_id = d.id
     WHERE d.child_id = $1
       AND us.start_time >= $2
       AND us.start_time < $3
       AND (EXTRACT(HOUR FROM us.start_time) >= 22 OR EXTRACT(HOUR FROM us.start_time) < 6)`,
        [childId, startDate.toISOString(), endDate.toISOString()]
    );

    const lateNightSessions = parseInt(lateNightResult[0]?.count || '0', 10);
    let score = 0;

    if (lateNightSessions > 10) {
        score += 40;
        factors.push({
            dimension: 'scheduleCompliance',
            factor: 'frequent_late_night_usage',
            severity: 'high',
            description: `${lateNightSessions} sessions during late night hours`,
            value: lateNightSessions,
        });
    } else if (lateNightSessions > 5) {
        score += 25;
        factors.push({
            dimension: 'scheduleCompliance',
            factor: 'some_late_night_usage',
            severity: 'medium',
            description: `${lateNightSessions} sessions during late night hours`,
            value: lateNightSessions,
        });
    } else if (lateNightSessions > 0) {
        score += 10;
        factors.push({
            dimension: 'scheduleCompliance',
            factor: 'occasional_late_night_usage',
            severity: 'low',
            description: `${lateNightSessions} session(s) during late night hours`,
            value: lateNightSessions,
        });
    }

    return { score: Math.min(100, score), factors };
}

/**
 * Generate insights based on risk assessment
 */
function generateInsights(dimensions: RiskDimensions, factors: RiskFactor[]): string[] {
    const insights: string[] = [];

    if (dimensions.digitalOveruse > 50) {
        insights.push('Screen time is significantly above recommended levels. Consider setting stricter time limits.');
    }

    if (dimensions.contentRisk > 50) {
        insights.push('Multiple attempts to access blocked content detected. Review and discuss online safety rules.');
    }

    if (dimensions.scheduleCompliance > 50) {
        insights.push('Device usage during restricted hours is concerning. Ensure devices are stored away at bedtime.');
    }

    const highSeverityFactors = factors.filter(f => f.severity === 'high');
    if (highSeverityFactors.length > 0) {
        insights.push(`${highSeverityFactors.length} high-severity concern(s) require immediate attention.`);
    }

    if (Object.values(dimensions).every(d => d < 25)) {
        insights.push('Digital habits are within healthy ranges. Keep up the good work!');
    }

    return insights;
}

/**
 * Generate recommendations based on risk assessment
 */
function generateRecommendations(dimensions: RiskDimensions, factors: RiskFactor[]): string[] {
    const recommendations: string[] = [];

    if (dimensions.digitalOveruse > 50) {
        recommendations.push('Set daily screen time limits appropriate for age');
        recommendations.push('Schedule regular device-free activities');
    }

    if (dimensions.contentRisk > 30) {
        recommendations.push('Review and update content filtering policies');
        recommendations.push('Have a conversation about online safety');
    }

    if (dimensions.scheduleCompliance > 30) {
        recommendations.push('Enable bedtime mode on devices');
        recommendations.push('Create a device charging station outside bedrooms');
    }

    if (dimensions.socialRisk > 30) {
        recommendations.push('Monitor social media activity more closely');
        recommendations.push('Discuss healthy online relationships');
    }

    return recommendations;
}

/**
 * Get risk assessment for a specific child
 */
export async function getChildRiskAssessment(
    childId: string,
    daysBack: number = 7
): Promise<ChildRiskAssessment | null> {
    try {
        // Get child info
        const childResult = await query<{ id: string; name: string; birth_date: Date }>(
            'SELECT id, name, birth_date FROM children WHERE id = $1',
            [childId]
        );

        if (childResult.length === 0) {
            return null;
        }

        const child = childResult[0];
        const ageYears = Math.floor(
            (Date.now() - new Date(child.birth_date).getTime()) / (1000 * 60 * 60 * 24 * 365)
        );

        const endDate = new Date();
        const startDate = new Date(endDate.getTime() - daysBack * 24 * 60 * 60 * 1000);

        // Gather statistics
        const usageStats = await getUsageStats(childId, startDate, endDate);
        const blockStats = await getBlockStats(childId, startDate, endDate);

        // Calculate dimension scores
        const overuseResult = calculateDigitalOveruseRisk(usageStats.avgDailyMinutes, ageYears);
        const contentResult = calculateContentRisk(blockStats);
        const scheduleResult = await calculateScheduleComplianceRisk(childId, startDate, endDate);

        // Combine all factors
        const allFactors = [
            ...overuseResult.factors,
            ...contentResult.factors,
            ...scheduleResult.factors,
        ];

        const dimensions: RiskDimensions = {
            digitalOveruse: overuseResult.score,
            scheduleCompliance: scheduleResult.score,
            contentRisk: contentResult.score,
            socialRisk: 0, // TODO: Implement social risk scoring
        };

        // Calculate overall score (weighted average)
        const overallScore = Math.round(
            dimensions.digitalOveruse * 0.3 +
            dimensions.scheduleCompliance * 0.25 +
            dimensions.contentRisk * 0.35 +
            dimensions.socialRisk * 0.1
        );

        const assessment: ChildRiskAssessment = {
            childId,
            childName: child.name,
            overallScore,
            riskBucket: getRiskBucket(overallScore),
            dimensions,
            factors: allFactors,
            insights: generateInsights(dimensions, allFactors),
            recommendations: generateRecommendations(dimensions, allFactors),
            assessedAt: new Date().toISOString(),
            dataWindow: {
                start: startDate.toISOString(),
                end: endDate.toISOString(),
            },
        };

        logger.info('Risk assessment completed', {
            childId,
            overallScore,
            riskBucket: assessment.riskBucket,
            factorCount: allFactors.length,
        });

        return assessment;
    } catch (error) {
        logger.error('Failed to compute risk assessment', {
            childId,
            error: error instanceof Error ? error.message : String(error),
        });
        throw error;
    }
}

/**
 * Get risk assessments for all children of a parent
 */
export async function getParentRiskOverview(
    userId: string,
    daysBack: number = 7
): Promise<ChildRiskAssessment[]> {
    const children = await query<{ id: string }>(
        'SELECT id FROM children WHERE user_id = $1 AND is_active = true',
        [userId]
    );

    const assessments: ChildRiskAssessment[] = [];

    for (const child of children) {
        const assessment = await getChildRiskAssessment(child.id, daysBack);
        if (assessment) {
            assessments.push(assessment);
        }
    }

    // Sort by risk score descending (highest risk first)
    assessments.sort((a, b) => b.overallScore - a.overallScore);

    return assessments;
}
