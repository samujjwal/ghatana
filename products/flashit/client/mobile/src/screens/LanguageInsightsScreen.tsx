/**
 * Language Insights Screen
 * Displays language evolution patterns, key metrics, and topic shifts
 */

import React, { useState, useMemo } from 'react';
import {
    View,
    Text,
    ScrollView,
    StyleSheet,
    Dimensions,
    TouchableOpacity,
} from 'react-native';
import { LineChart, BarChart } from 'react-native-chart-kit';
import { useQuery } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { mobileAtoms } from '../state/localAtoms';

const { width: screenWidth } = Dimensions.get('window');

interface LanguageMetrics {
    date: string;
    wordCount: number;
    uniqueWords: number;
    avgWordLength: number;
    sentimentScore: number;
    topicDistribution: Record<string, number>;
    expressionPatterns: Record<string, number>;
}

interface LanguageInsight {
    id: string;
    title: string;
    description: string;
    type: 'vocabulary_growth' | 'sentiment_shift' | 'topic_evolution' | 'expression_change';
    date: string;
    impact: 'high' | 'medium' | 'low';
}

const fetchLanguageMetrics = async (): Promise<LanguageMetrics[]> => {
    // Mock data - replace with actual API call
    return [
        {
            date: '2025-01-01',
            wordCount: 1250,
            uniqueWords: 450,
            avgWordLength: 4.8,
            sentimentScore: 0.65,
            topicDistribution: { work: 0.4, personal: 0.3, family: 0.2, health: 0.1 },
            expressionPatterns: { formal: 0.3, casual: 0.5, emotional: 0.2 },
        },
        {
            date: '2025-01-15',
            wordCount: 1380,
            uniqueWords: 485,
            avgWordLength: 5.1,
            sentimentScore: 0.72,
            topicDistribution: { work: 0.35, personal: 0.35, family: 0.2, health: 0.1 },
            expressionPatterns: { formal: 0.25, casual: 0.55, emotional: 0.2 },
        },
    ];
};

const fetchLanguageInsights = async (): Promise<LanguageInsight[]> => {
    // Mock data - replace with actual API call
    return [
        {
            id: '1',
            title: 'Vocabulary Growth Detected',
            description: 'Your vocabulary has expanded by 15% over the past month',
            type: 'vocabulary_growth',
            date: '2025-01-15',
            impact: 'high',
        },
        {
            id: '2',
            title: 'Sentiment Shift Towards Positive',
            description: 'Your language sentiment has become more positive recently',
            type: 'sentiment_shift',
            date: '2025-01-10',
            impact: 'medium',
        },
    ];
};

export default function LanguageInsightsScreen() {
    const authToken = useAtomValue(mobileAtoms.authTokenAtom);

    const [selectedMetric, setSelectedMetric] = useState<'vocabulary' | 'sentiment' | 'topics'>('vocabulary');

    const { data: metrics, isLoading: metricsLoading } = useQuery({
        queryKey: ['language-metrics'],
        queryFn: fetchLanguageMetrics,
        enabled: !!authToken,
    });

    const { data: insights, isLoading: insightsLoading } = useQuery({
        queryKey: ['language-insights'],
        queryFn: fetchLanguageInsights,
        enabled: !!authToken,
    });

    const chartData = useMemo(() => {
        if (!metrics) return null;

        switch (selectedMetric) {
            case 'vocabulary':
                return {
                    labels: metrics.map(m => m.date.slice(5)),
                    datasets: [
                        {
                            data: metrics.map(m => m.wordCount),
                            color: (opacity = 1) => `rgba(59, 130, 246, ${opacity})`,
                            strokeWidth: 2,
                        },
                        {
                            data: metrics.map(m => m.uniqueWords),
                            color: (opacity = 1) => `rgba(34, 197, 94, ${opacity})`,
                            strokeWidth: 2,
                        },
                    ],
                };
            case 'sentiment':
                return {
                    labels: metrics.map(m => m.date.slice(5)),
                    datasets: [
                        {
                            data: metrics.map(m => m.sentimentScore * 100),
                            color: (opacity = 1) => `rgba(251, 146, 60, ${opacity})`,
                            strokeWidth: 2,
                        },
                    ],
                };
            case 'topics':
                const latestMetrics = metrics[metrics.length - 1];
                return {
                    labels: Object.keys(latestMetrics.topicDistribution),
                    datasets: [
                        {
                            data: Object.values(latestMetrics.topicDistribution).map(v => v * 100),
                            color: (opacity = 1) => `rgba(147, 51, 234, ${opacity})`,
                        },
                    ],
                };
            default:
                return null;
        }
    }, [metrics, selectedMetric]);

    if (metricsLoading || insightsLoading) {
        return (
            <View style={styles.container}>
                <Text style={styles.title}>Language Insights</Text>
                <Text style={styles.loadingText}>Loading your language patterns...</Text>
            </View>
        );
    }

    return (
        <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
            <Text style={styles.title}>Language Insights</Text>

            {/* Metric Selector */}
            <View style={styles.metricSelector}>
                {(['vocabulary', 'sentiment', 'topics'] as const).map((metric) => (
                    <TouchableOpacity
                        key={metric}
                        style={[
                            styles.metricButton,
                            selectedMetric === metric && styles.metricButtonActive,
                        ]}
                        onPress={() => setSelectedMetric(metric)}
                    >
                        <Text
                            style={[
                                styles.metricButtonText,
                                selectedMetric === metric && styles.metricButtonTextActive,
                            ]}
                        >
                            {metric.charAt(0).toUpperCase() + metric.slice(1)}
                        </Text>
                    </TouchableOpacity>
                ))}
            </View>

            {/* Chart */}
            {chartData && (
                <View style={styles.chartContainer}>
                    <Text style={styles.chartTitle}>
                        {selectedMetric.charAt(0).toUpperCase() + selectedMetric.slice(1)} Trends
                    </Text>
                    {selectedMetric === 'topics' ? (
                        <BarChart
                            data={chartData}
                            width={screenWidth - 40}
                            height={220}
                            chartConfig={{
                                backgroundColor: '#ffffff',
                                backgroundGradientFrom: '#ffffff',
                                backgroundGradientTo: '#ffffff',
                                decimalPlaces: 0,
                                color: (opacity = 1) => `rgba(0, 0, 0, ${opacity})`,
                                labelColor: (opacity = 1) => `rgba(0, 0, 0, ${opacity})`,
                                style: {
                                    borderRadius: 16,
                                },
                            }}
                            style={styles.chart}
                        />
                    ) : (
                        <LineChart
                            data={chartData}
                            width={screenWidth - 40}
                            height={220}
                            chartConfig={{
                                backgroundColor: '#ffffff',
                                backgroundGradientFrom: '#ffffff',
                                backgroundGradientTo: '#ffffff',
                                decimalPlaces: 0,
                                color: (opacity = 1) => `rgba(0, 0, 0, ${opacity})`,
                                labelColor: (opacity = 1) => `rgba(0, 0, 0, ${opacity})`,
                                style: {
                                    borderRadius: 16,
                                },
                                propsForDots: {
                                    r: '4',
                                    strokeWidth: '2',
                                },
                            }}
                            style={styles.chart}
                        />
                    )}
                </View>
            )}

            {/* Key Insights */}
            <View style={styles.insightsContainer}>
                <Text style={styles.sectionTitle}>Key Insights</Text>
                {insights?.map((insight) => (
                    <View key={insight.id} style={styles.insightCard}>
                        <View style={styles.insightHeader}>
                            <Text style={styles.insightTitle}>{insight.title}</Text>
                            <View style={[
                                styles.impactBadge,
                                insight.impact === 'high' && styles.impactHigh,
                                insight.impact === 'medium' && styles.impactMedium,
                                insight.impact === 'low' && styles.impactLow,
                            ]}>
                                <Text style={styles.impactText}>{insight.impact}</Text>
                            </View>
                        </View>
                        <Text style={styles.insightDescription}>{insight.description}</Text>
                        <Text style={styles.insightDate}>{insight.date}</Text>
                    </View>
                ))}
            </View>

            {/* Summary Stats */}
            {metrics && (
                <View style={styles.statsContainer}>
                    <Text style={styles.sectionTitle}>Summary Statistics</Text>
                    <View style={styles.statsGrid}>
                        <View style={styles.statCard}>
                            <Text style={styles.statValue}>
                                {metrics[metrics.length - 1].wordCount.toLocaleString()}
                            </Text>
                            <Text style={styles.statLabel}>Total Words</Text>
                        </View>
                        <View style={styles.statCard}>
                            <Text style={styles.statValue}>
                                {metrics[metrics.length - 1].uniqueWords.toLocaleString()}
                            </Text>
                            <Text style={styles.statLabel}>Unique Words</Text>
                        </View>
                        <View style={styles.statCard}>
                            <Text style={styles.statValue}>
                                {metrics[metrics.length - 1].avgWordLength.toFixed(1)}
                            </Text>
                            <Text style={styles.statLabel}>Avg Word Length</Text>
                        </View>
                        <View style={styles.statCard}>
                            <Text style={styles.statValue}>
                                {Math.round(metrics[metrics.length - 1].sentimentScore * 100)}%
                            </Text>
                            <Text style={styles.statLabel}>Sentiment Score</Text>
                        </View>
                    </View>
                </View>
            )}
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: '#f8fafc',
        padding: 20,
    },
    title: {
        fontSize: 28,
        fontWeight: 'bold',
        color: '#1e293b',
        marginBottom: 20,
    },
    loadingText: {
        fontSize: 16,
        color: '#64748b',
        textAlign: 'center',
        marginTop: 50,
    },
    metricSelector: {
        flexDirection: 'row',
        backgroundColor: '#e2e8f0',
        borderRadius: 12,
        padding: 4,
        marginBottom: 20,
    },
    metricButton: {
        flex: 1,
        paddingVertical: 8,
        paddingHorizontal: 12,
        borderRadius: 8,
        alignItems: 'center',
    },
    metricButtonActive: {
        backgroundColor: '#3b82f6',
    },
    metricButtonText: {
        fontSize: 14,
        fontWeight: '500',
        color: '#64748b',
    },
    metricButtonTextActive: {
        color: '#ffffff',
    },
    chartContainer: {
        backgroundColor: '#ffffff',
        borderRadius: 16,
        padding: 20,
        marginBottom: 20,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
        elevation: 3,
    },
    chartTitle: {
        fontSize: 18,
        fontWeight: '600',
        color: '#1e293b',
        marginBottom: 15,
    },
    chart: {
        borderRadius: 16,
    },
    insightsContainer: {
        marginBottom: 20,
    },
    sectionTitle: {
        fontSize: 20,
        fontWeight: '600',
        color: '#1e293b',
        marginBottom: 15,
    },
    insightCard: {
        backgroundColor: '#ffffff',
        borderRadius: 12,
        padding: 16,
        marginBottom: 12,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
        elevation: 3,
    },
    insightHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 8,
    },
    insightTitle: {
        fontSize: 16,
        fontWeight: '600',
        color: '#1e293b',
        flex: 1,
    },
    impactBadge: {
        paddingHorizontal: 8,
        paddingVertical: 4,
        borderRadius: 6,
    },
    impactHigh: {
        backgroundColor: '#fef2f2',
    },
    impactMedium: {
        backgroundColor: '#fefce8',
    },
    impactLow: {
        backgroundColor: '#f0f9ff',
    },
    impactText: {
        fontSize: 12,
        fontWeight: '500',
        textTransform: 'uppercase',
    },
    insightDescription: {
        fontSize: 14,
        color: '#64748b',
        marginBottom: 8,
    },
    insightDate: {
        fontSize: 12,
        color: '#94a3b8',
    },
    statsContainer: {
        marginBottom: 20,
    },
    statsGrid: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: 12,
    },
    statCard: {
        backgroundColor: '#ffffff',
        borderRadius: 12,
        padding: 16,
        flex: 1,
        minWidth: '45%',
        alignItems: 'center',
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
        elevation: 3,
    },
    statValue: {
        fontSize: 24,
        fontWeight: 'bold',
        color: '#3b82f6',
        marginBottom: 4,
    },
    statLabel: {
        fontSize: 12,
        color: '#64748b',
        textAlign: 'center',
    },
});
