/**
 * Data Quality Dashboard Component
 * 
 * AI-powered data quality monitoring and assessment.
 * Displays quality scores, anomalies, and recommendations.
 * 
 * @doc.type component
 * @doc.purpose Data quality visualization
 * @doc.layer frontend
 * @doc.pattern Container Component
 */

import React, { useState, useEffect } from 'react';
import {
    Shield,
    AlertTriangle,
    CheckCircle2,
    TrendingUp,
    TrendingDown,
    Minus,
    RefreshCw,
    Info,
    Zap,
} from 'lucide-react';
import { cn, textStyles, cardStyles, badgeStyles } from '../../lib/theme';

/**
 * Quality dimension scores
 */
interface QualityDimensions {
    completeness: number;
    accuracy: number;
    consistency: number;
    timeliness: number;
    uniqueness: number;
}

/**
 * Quality issue
 */
interface QualityIssue {
    field: string;
    issue: string;
    severity: 'high' | 'medium' | 'low';
    affectedRows: number;
    suggestion: string;
}

/**
 * Quality trend
 */
interface QualityTrend {
    metric: string;
    direction: 'improving' | 'stable' | 'declining';
    change: number;
}

/**
 * Data quality assessment
 */
interface DataQualityAssessment {
    collectionName: string;
    overallScore: number;
    dimensions: QualityDimensions;
    issues: QualityIssue[];
    trends: QualityTrend[];
    lastAssessed: string;
}

interface DataQualityDashboardProps {
    collectionName: string;
    className?: string;
    onIssueClick?: (issue: QualityIssue) => void;
}

/**
 * Mock data quality assessment
 */
async function assessQuality(collectionName: string): Promise<DataQualityAssessment> {
    await new Promise((resolve) => setTimeout(resolve, 500));

    return {
        collectionName,
        overallScore: 87,
        dimensions: {
            completeness: 92,
            accuracy: 88,
            consistency: 85,
            timeliness: 90,
            uniqueness: 80,
        },
        issues: [
            {
                field: 'email',
                issue: 'Invalid email format detected',
                severity: 'high',
                affectedRows: 234,
                suggestion: 'Apply email validation regex and clean invalid entries',
            },
            {
                field: 'phone',
                issue: 'Missing values',
                severity: 'medium',
                affectedRows: 1205,
                suggestion: 'Consider making field optional or implement data enrichment',
            },
            {
                field: 'created_at',
                issue: 'Future dates detected',
                severity: 'low',
                affectedRows: 12,
                suggestion: 'Add constraint to prevent future dates',
            },
        ],
        trends: [
            { metric: 'completeness', direction: 'improving', change: 2.3 },
            { metric: 'accuracy', direction: 'stable', change: 0.1 },
            { metric: 'consistency', direction: 'declining', change: -1.5 },
        ],
        lastAssessed: new Date().toISOString(),
    };
}

/**
 * Quality score ring component
 */
function QualityScoreRing({ score, size = 120 }: { score: number; size?: number }): React.ReactElement {
    const radius = (size - 12) / 2;
    const circumference = 2 * Math.PI * radius;
    const strokeDashoffset = circumference - (score / 100) * circumference;

    const getScoreColor = (s: number): string => {
        if (s >= 90) return 'text-green-500';
        if (s >= 70) return 'text-yellow-500';
        return 'text-red-500';
    };

    const getStrokeColor = (s: number): string => {
        if (s >= 90) return '#22c55e';
        if (s >= 70) return '#eab308';
        return '#ef4444';
    };

    return (
        <div className="relative" style={{ width: size, height: size }}>
            <svg className="transform -rotate-90" width={size} height={size}>
                {/* Background circle */}
                <circle
                    cx={size / 2}
                    cy={size / 2}
                    r={radius}
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="8"
                    className="text-gray-200 dark:text-gray-700"
                />
                {/* Progress circle */}
                <circle
                    cx={size / 2}
                    cy={size / 2}
                    r={radius}
                    fill="none"
                    stroke={getStrokeColor(score)}
                    strokeWidth="8"
                    strokeLinecap="round"
                    strokeDasharray={circumference}
                    strokeDashoffset={strokeDashoffset}
                    className="transition-all duration-1000 ease-out"
                />
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
                <span className={cn('text-3xl font-bold', getScoreColor(score))}>
                    {score}
                </span>
                <span className={textStyles.xs}>Quality Score</span>
            </div>
        </div>
    );
}

/**
 * Dimension bar component
 */
function DimensionBar({
    label,
    score,
    icon
}: {
    label: string;
    score: number;
    icon: React.ReactNode;
}): React.ReactElement {
    const getBarColor = (s: number): string => {
        if (s >= 90) return 'bg-green-500';
        if (s >= 70) return 'bg-yellow-500';
        return 'bg-red-500';
    };

    return (
        <div className="space-y-1">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <span className="text-gray-500">{icon}</span>
                    <span className={textStyles.small}>{label}</span>
                </div>
                <span className={cn(textStyles.small, 'font-medium')}>{score}%</span>
            </div>
            <div className="h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                <div
                    className={cn('h-full rounded-full transition-all duration-500', getBarColor(score))}
                    style={{ width: `${score}%` }}
                />
            </div>
        </div>
    );
}

/**
 * Data Quality Dashboard Component
 */
export function DataQualityDashboard({
    collectionName,
    className,
    onIssueClick,
}: DataQualityDashboardProps): React.ReactElement {
    const [assessment, setAssessment] = useState<DataQualityAssessment | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        setIsLoading(true);
        assessQuality(collectionName)
            .then(setAssessment)
            .finally(() => setIsLoading(false));
    }, [collectionName]);

    const getTrendIcon = (direction: string): React.ReactNode => {
        switch (direction) {
            case 'improving':
                return <TrendingUp className="h-4 w-4 text-green-500" />;
            case 'declining':
                return <TrendingDown className="h-4 w-4 text-red-500" />;
            default:
                return <Minus className="h-4 w-4 text-gray-500" />;
        }
    };

    const getSeverityBadge = (severity: string): string => {
        switch (severity) {
            case 'high':
                return badgeStyles.danger;
            case 'medium':
                return badgeStyles.warning;
            default:
                return badgeStyles.info;
        }
    };

    if (isLoading) {
        return (
            <div className={cn(cardStyles.base, cardStyles.padded, 'flex items-center justify-center h-64', className)}>
                <RefreshCw className="h-8 w-8 animate-spin text-blue-500" />
            </div>
        );
    }

    if (!assessment) {
        return (
            <div className={cn(cardStyles.base, cardStyles.padded, className)}>
                <p className={textStyles.muted}>Failed to load quality assessment</p>
            </div>
        );
    }

    return (
        <div className={cn('space-y-6', className)}>
            {/* Header */}
            <div className={cn(cardStyles.base, cardStyles.padded)}>
                <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                        <Shield className="h-5 w-5 text-blue-500" />
                        <h3 className={textStyles.h3}>Data Quality</h3>
                    </div>
                    <span className={textStyles.xs}>
                        Last assessed: {new Date(assessment.lastAssessed).toLocaleString()}
                    </span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    {/* Overall Score */}
                    <div className="flex justify-center">
                        <QualityScoreRing score={assessment.overallScore} />
                    </div>

                    {/* Dimensions */}
                    <div className="space-y-3">
                        <DimensionBar
                            label="Completeness"
                            score={assessment.dimensions.completeness}
                            icon={<CheckCircle2 className="h-4 w-4" />}
                        />
                        <DimensionBar
                            label="Accuracy"
                            score={assessment.dimensions.accuracy}
                            icon={<Zap className="h-4 w-4" />}
                        />
                        <DimensionBar
                            label="Consistency"
                            score={assessment.dimensions.consistency}
                            icon={<Shield className="h-4 w-4" />}
                        />
                        <DimensionBar
                            label="Timeliness"
                            score={assessment.dimensions.timeliness}
                            icon={<TrendingUp className="h-4 w-4" />}
                        />
                        <DimensionBar
                            label="Uniqueness"
                            score={assessment.dimensions.uniqueness}
                            icon={<Info className="h-4 w-4" />}
                        />
                    </div>
                </div>
            </div>

            {/* Issues */}
            {assessment.issues.length > 0 && (
                <div className={cn(cardStyles.base, cardStyles.padded)}>
                    <div className="flex items-center gap-2 mb-4">
                        <AlertTriangle className="h-5 w-5 text-yellow-500" />
                        <h3 className={textStyles.h4}>Quality Issues ({assessment.issues.length})</h3>
                    </div>

                    <div className="space-y-3">
                        {assessment.issues.map((issue, i) => (
                            <div
                                key={i}
                                onClick={() => onIssueClick?.(issue)}
                                className={cn(
                                    'p-3 rounded-lg border cursor-pointer transition-colors',
                                    'border-gray-200 dark:border-gray-700',
                                    'hover:bg-gray-50 dark:hover:bg-gray-800'
                                )}
                            >
                                <div className="flex items-start justify-between gap-2">
                                    <div className="flex-1">
                                        <div className="flex items-center gap-2 mb-1">
                                            <span className={cn(textStyles.mono, 'text-sm font-medium')}>
                                                {issue.field}
                                            </span>
                                            <span className={getSeverityBadge(issue.severity)}>
                                                {issue.severity}
                                            </span>
                                        </div>
                                        <p className={textStyles.small}>{issue.issue}</p>
                                        <p className={cn(textStyles.xs, 'mt-1')}>
                                            Affected rows: {issue.affectedRows.toLocaleString()}
                                        </p>
                                    </div>
                                </div>
                                <div className="mt-2 p-2 rounded bg-blue-50 dark:bg-blue-900/20">
                                    <p className={cn(textStyles.xs, 'text-blue-700 dark:text-blue-300')}>
                                        <strong>Suggestion:</strong> {issue.suggestion}
                                    </p>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Trends */}
            {assessment.trends.length > 0 && (
                <div className={cn(cardStyles.base, cardStyles.padded)}>
                    <h3 className={cn(textStyles.h4, 'mb-4')}>Quality Trends</h3>
                    <div className="grid grid-cols-3 gap-4">
                        {assessment.trends.map((trend, i) => (
                            <div
                                key={i}
                                className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800 text-center"
                            >
                                <div className="flex items-center justify-center gap-1 mb-1">
                                    {getTrendIcon(trend.direction)}
                                    <span className={cn(
                                        'text-sm font-medium',
                                        trend.direction === 'improving' && 'text-green-600',
                                        trend.direction === 'declining' && 'text-red-600',
                                        trend.direction === 'stable' && 'text-gray-600'
                                    )}>
                                        {trend.change > 0 ? '+' : ''}{trend.change}%
                                    </span>
                                </div>
                                <p className={textStyles.xs}>{trend.metric}</p>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

export default DataQualityDashboard;
