/**
 * AI Insights Dashboard Component
 * 
 * Interactive dashboard displaying AI-powered predictions, risk assessments,
 * and optimization recommendations with actionable insights.
 */

import { Alert, Badge, Box, Tab, Tabs, Typography } from '@ghatana/ui';
import React, { useCallback, useMemo, useState } from 'react';

import { Header } from './Header';
import { SummaryCards } from './SummaryCards';
import { PatternAnalysisTab } from './tabs/PatternAnalysisTab';
import { PredictionsTab } from './tabs/PredictionsTab';
import { RecommendationsTab } from './tabs/RecommendationsTab';
import { RiskAssessmentTab } from './tabs/RiskAssessmentTab';
import { useAIInsights } from '../../hooks/ai/useAIInsights';

import type { AIInsightsDashboardProps, RecommendationsByType } from './types';

/**
 * AIInsightsDashboard Component
 * 
 * @component
 *
 * @param props - Component props
 * @param props.title - Dashboard title
 * @param props.refreshInterval - Auto-refresh interval in milliseconds
 * @param props.className - Custom styling
 *
 * @returns Rendered dashboard component
 */
export const AIInsightsDashboard: React.FC<AIInsightsDashboardProps> = ({
    title = 'AI Insights & Recommendations',
    refreshInterval = 60000, // 1 minute
    className
}) => {
    const [selectedTab, setSelectedTab] = useState(0);
    const [implementingIds, setImplementingIds] = useState<Set<string>>(new Set());

    const {
        insights,
        models = [],
        implementRecommendation,
        dismissRecommendation,
        runAnalysis,
        isAnalyzing,
        error
    } = useAIInsights({
        analysisInterval: refreshInterval,
        enablePredictions: true,
        enableRiskAssessment: true,
        enableRecommendations: true
    });

    // Handle tab change
    const handleTabChange = useCallback((_: React.SyntheticEvent, newValue: number) => {
        setSelectedTab(newValue);
    }, []);

    const handleDismissRecommendation = useCallback((id: string) => {
        dismissRecommendation(id);
        setImplementingIds(prev => {
            const newSet = new Set(prev);
            newSet.delete(id);
            return newSet;
        });
    }, [dismissRecommendation]);

    // Handle recommendation implementation
    const handleImplementRecommendation = useCallback(async (id: string) => {
        setImplementingIds(prev => new Set(prev).add(id));
        try {
            await implementRecommendation(id);
        } finally {
            setImplementingIds(prev => {
                const newSet = new Set(prev);
                newSet.delete(id);
                return newSet;
            });
        }
    }, [implementRecommendation]);

    const buildTimeSummary = useMemo(() => {
        if (!insights?.buildTimePrediction) {
            return undefined;
        }

        const { predictedTime, confidence, range, factors } = insights.buildTimePrediction;
        return {
            predictedTime,
            confidence,
            range,
            factors: factors.map(factor => ({
                name: factor.name,
                description: factor.description,
                impact: factor.impact
            }))
        };
    }, [insights?.buildTimePrediction]);

    const deploymentRiskSummary = useMemo(() => {
        if (!insights?.deploymentRisk) {
            return undefined;
        }

        const { riskLevel, riskScore, riskFactors, recommendations } = insights.deploymentRisk;
        return {
            riskLevel,
            riskScore,
            riskFactors: riskFactors.map(factor => ({
                factor: factor.factor,
                impact: factor.weight,
                description: factor.description
            })),
            recommendations
        };
    }, [insights?.deploymentRisk]);

    const summaryModels = useMemo(
        () => models.map(model => ({ accuracy: model.accuracy })),
        [models]
    );

    const hasSummaryData = useMemo(
        () =>
            Boolean(
                buildTimeSummary ||
                deploymentRiskSummary ||
                summaryModels.length > 0
            ),
        [buildTimeSummary, deploymentRiskSummary, summaryModels.length]
    );

    const recommendationsByType = useMemo<RecommendationsByType>(() => {
        if (!insights?.recommendations) return {};
        
        return insights.recommendations.reduce<RecommendationsByType>((acc, rec) => {
            const type = rec.type || 'other';
            if (!acc[type]) {
                acc[type] = [];
            }
            acc[type]!.push(rec);
            return acc;
        }, {});
    }, [insights?.recommendations]);

    const recommendationCounts = useMemo(() => {
        const counts = { critical: 0, high: 0, medium: 0, low: 0, total: 0 };
        
        if (!insights?.recommendations) return counts;

        insights.recommendations.forEach(rec => {
            if (rec.priority === 'critical') counts.critical++;
            else if (rec.priority === 'high') counts.high++;
            else if (rec.priority === 'medium') counts.medium++;
            else if (rec.priority === 'low') counts.low++;
            counts.total++;
        });

        return counts;
    }, [insights?.recommendations]);

    return (
        <Box className={className}>
            <Header title={title} isAnalyzing={isAnalyzing} onRefresh={runAnalysis} />

            {/* Error Alert */}
            {error && (
                <Alert severity="error" className="mb-6">
                    Analysis Error: {error}
                </Alert>
            )}

            {hasSummaryData && (
                <SummaryCards
                    buildTimePrediction={buildTimeSummary}
                    deploymentRisk={deploymentRiskSummary}
                    models={summaryModels}
                    recommendationCounts={recommendationCounts}
                />
            )}

            {/* Tabs */}
            <Box className="mb-6 border-gray-200 dark:border-gray-700 border-b" >
                <Tabs value={selectedTab} onChange={handleTabChange}>
                    <Tab
                        label={
                            <Badge badgeContent={recommendationCounts.total} tone="warning">
                                Recommendations
                            </Badge>
                        }
                    />
                    <Tab label="Predictions" />
                    <Tab label="Risk Assessment" />
                    <Tab label="Pattern Analysis" />
                </Tabs>
            </Box>

            {/* Tab Content */}
            {selectedTab === 0 && (
                <RecommendationsTab
                    recommendationsByType={recommendationsByType}
                    recommendationCounts={recommendationCounts}
                    implementingIds={implementingIds}
                    onImplement={handleImplementRecommendation}
                    onDismiss={handleDismissRecommendation}
                />
            )}

            {selectedTab === 1 && (
                <SummaryCards
                    buildTimePrediction={buildTimeSummary}
                    deploymentRisk={deploymentRiskSummary}
                    models={summaryModels}
                    recommendationCounts={recommendationCounts}
                />
            )}

            {selectedTab === 2 && insights?.buildTimePrediction && (
                <PredictionsTab insights={{ buildTimePrediction: insights.buildTimePrediction }} />
            )}

            {selectedTab === 3 && insights?.deploymentRisk && (
                <RiskAssessmentTab insights={{ 
                    deploymentRisk: insights.deploymentRisk
                }} />
            )}

            {selectedTab === 4 && insights?.recommendations && (
                <RecommendationsTab
                    recommendationsByType={recommendationsByType}
                    recommendationCounts={recommendationCounts}
                    implementingIds={implementingIds}
                    onImplement={handleImplementRecommendation}
                    onDismiss={handleDismissRecommendation}
                />
            )}

            {selectedTab === 5 && insights?.patterns && (
                <PatternAnalysisTab insights={{
                    detected: insights.patterns.detected || [],
                    anomalies: insights.patterns.anomalies || [],
                    trends: insights.patterns.trends || []
                }} />
            )}

            {/* Analysis Status */}
            {insights?.lastAnalysis && (
                <Box className="mt-6 text-center">
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        Last analysis: {insights.lastAnalysis.toLocaleString()}
                    </Typography>
                </Box>
            )}
        </Box>
    );
};