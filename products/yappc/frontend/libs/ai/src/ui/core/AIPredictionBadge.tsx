/**
 * AI Prediction Badge Component
 *
 * Inline prediction indicator for KPIs, metrics, and phase cards.
 * Shows trend, confidence, and prediction value.
 *
 * @module ai/components
 * @doc.type component
 * @doc.purpose Inline AI predictions
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { Box, Chip, Tooltip, Typography, Stack, LinearProgress } from '@ghatana/ui';
import { TrendingUp, TrendingDown, MoveRight as TrendingFlat, Brain as Psychology, AlertTriangle as Warning } from 'lucide-react';

/**
 * Prediction type
 */
export interface AIPrediction {
    predictedValue: number | string;
    currentValue: number;
    trend: 'up' | 'down' | 'flat';
    confidence: number; // 0-1
    timeline?: string;
    severity?: 'success' | 'warning' | 'error' | 'info';
}

/**
 * Component props
 */
export interface AIPredictionBadgeProps {
    prediction: AIPrediction;
    label?: string;
    size?: 'small' | 'medium' | 'large';
    showConfidence?: boolean;
    showTrend?: boolean;
    variant?: 'badge' | 'inline' | 'full';
}

/**
 * Get trend icon
 */
function getTrendIcon(trend: 'up' | 'down' | 'flat', size: 'small' | 'medium' | 'large') {
    const iconSize = size === 'small' ? 'inherit' : 'small';

    switch (trend) {
        case 'up':
            return <TrendingUp fontSize={iconSize} />;
        case 'down':
            return <TrendingDown fontSize={iconSize} />;
        case 'flat':
            return <TrendingFlat fontSize={iconSize} />;
    }
}

/**
 * Get severity color
 */
function getSeverityColor(severity?: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
    switch (severity) {
        case 'success':
            return 'success';
        case 'warning':
            return 'warning';
        case 'error':
            return 'error';
        case 'info':
            return 'info';
        default:
            return 'default';
    }
}

/**
 * Format confidence percentage
 */
function formatConfidence(confidence: number): string {
    return `${Math.round(confidence * 100)}%`;
}

/**
 * AI Prediction Badge Component
 */
export function AIPredictionBadge({
    prediction,
    label,
    size = 'medium',
    showConfidence = true,
    showTrend = true,
    variant = 'badge',
}: AIPredictionBadgeProps) {
    const {
        predictedValue,
        currentValue,
        trend,
        confidence,
        timeline,
        severity,
    } = prediction;

    // Calculate change percentage
    const changePercent = typeof predictedValue === 'number'
        ? Math.abs(((predictedValue - currentValue) / currentValue) * 100)
        : 0;

    // Badge variant - compact chip
    if (variant === 'badge') {
        return (
            <Tooltip
                title={
                    <Box>
                        <Typography as="span" className="text-xs text-gray-500" display="block">
                            <strong>AI Prediction:</strong> {predictedValue}
                        </Typography>
                        {timeline && (
                            <Typography as="span" className="text-xs text-gray-500" display="block">
                                <strong>Timeline:</strong> {timeline}
                            </Typography>
                        )}
                        <Typography as="span" className="text-xs text-gray-500" display="block">
                            <strong>Confidence:</strong> {formatConfidence(confidence)}
                        </Typography>
                        {changePercent > 0 && (
                            <Typography as="span" className="text-xs text-gray-500" display="block">
                                <strong>Change:</strong> {changePercent.toFixed(1)}%
                            </Typography>
                        )}
                    </Box>
                }
            >
                <Chip
                    icon={<Psychology />}
                    label={label || 'AI Prediction'}
                    size={size}
                    color={getSeverityColor(severity)}
                    variant="outlined"
                    className="cursor-help"
                />
            </Tooltip>
        );
    }

    // Inline variant - compact horizontal
    if (variant === 'inline') {
        return (
            <Tooltip
                title={`AI predicts ${predictedValue} (${formatConfidence(confidence)} confidence)`}
            >
                <Stack
                    direction="row"
                    spacing={0.5}
                    alignItems="center"
                    className="inline-flex px-2 py-1 rounded bg-gray-100 dark:bg-gray-800 cursor-help"
                >
                    <Psychology size={16} tone="primary" />
                    {showTrend && getTrendIcon(trend, size)}
                    <Typography as="span" className="text-xs text-gray-500" fontWeight="medium">
                        {predictedValue}
                    </Typography>
                    {showConfidence && (
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            ({formatConfidence(confidence)})
                        </Typography>
                    )}
                </Stack>
            </Tooltip>
        );
    }

    // Full variant - detailed display
    return (
        <Box
            className="p-3 rounded border border-solid border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900"
        >
            <Stack spacing={1}>
                {/* Header */}
                <Stack direction="row" spacing={1} alignItems="center">
                    <Psychology size={16} tone="primary" />
                    <Typography as="span" className="text-xs text-gray-500" fontWeight="bold" tone="primary">
                        AI PREDICTION
                    </Typography>
                    {severity && severity !== 'info' && (
                        <Warning size={16} color={severity as unknown} />
                    )}
                </Stack>

                {/* Prediction Value */}
                <Box>
                    <Typography as="h6" fontWeight="bold">
                        {predictedValue}
                    </Typography>
                    {timeline && (
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            {timeline}
                        </Typography>
                    )}
                </Box>

                {/* Trend */}
                {showTrend && (
                    <Stack direction="row" spacing={0.5} alignItems="center">
                        {getTrendIcon(trend, size)}
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            {trend === 'up' && 'Trending upward'}
                            {trend === 'down' && 'Trending downward'}
                            {trend === 'flat' && 'Stable trend'}
                        </Typography>
                        {changePercent > 0 && (
                            <Typography as="span" className="text-xs text-gray-500" fontWeight="medium">
                                ({changePercent.toFixed(1)}%)
                            </Typography>
                        )}
                    </Stack>
                )}

                {/* Confidence */}
                {showConfidence && (
                    <Box>
                        <Stack direction="row" justifyContent="space-between" mb={0.5}>
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                Confidence
                            </Typography>
                            <Typography as="span" className="text-xs text-gray-500" fontWeight="medium">
                                {formatConfidence(confidence)}
                            </Typography>
                        </Stack>
                        <LinearProgress
                            variant="determinate"
                            value={confidence * 100}
                            color={confidence > 0.8 ? 'success' : confidence > 0.6 ? 'warning' : 'error'}
                            className="rounded-lg h-[4px]"
                        />
                    </Box>
                )}
            </Stack>
        </Box>
    );
}

/**
 * Hook for fetching phase predictions
 */
export function usePhaseAIPrediction(phaseId: string) {
    // This would be implemented with actual API call
    // For now, return mock data
    return {
        prediction: {
            predictedValue: '2025-01-15',
            currentValue: 85,
            trend: 'down' as const,
            confidence: 0.78,
            timeline: '2 weeks',
            severity: 'warning' as const,
        },
        isLoading: false,
        error: null,
    };
}

/**
 * Hook for fetching item predictions
 */
export function useItemAIPrediction(itemId: string) {
    // This would be implemented with actual API call
    // For now, return mock data
    return {
        prediction: {
            predictedValue: '3 days',
            currentValue: 2,
            trend: 'up' as const,
            confidence: 0.85,
            timeline: 'Expected completion',
            severity: 'success' as const,
        },
        isLoading: false,
        error: null,
    };
}

export default AIPredictionBadge;
