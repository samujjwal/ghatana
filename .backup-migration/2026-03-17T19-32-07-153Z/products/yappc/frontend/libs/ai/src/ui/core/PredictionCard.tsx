/**
 * Prediction Card Component
 *
 * Displays AI predictions with confidence indicators and contributing factors.
 * Used to show timeline estimates, risk scores, and effort predictions.
 *
 * Features:
 * - Visual confidence indicator
 * - Prediction range display
 * - Contributing factors breakdown
 * - Expandable details
 * - Refresh action
 *
 * @doc.type component
 * @doc.purpose AI prediction display card
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState } from 'react';
import { Box, Card, CardContent, CardActions, Typography, IconButton, Chip, LinearProgress, Collapse, Tooltip, Divider } from '@ghatana/ui';
import { ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon, RefreshCw as RefreshIcon, TrendingUp as TrendingUpIcon, TrendingDown as TrendingDownIcon, Clock as ScheduleIcon, AlertTriangle as WarningIcon, Gauge as EffortIcon, Info as InfoIcon } from 'lucide-react';

import type { Prediction, PredictionType, ContributingFactor } from '@ghatana/yappc-types';

/**
 * PredictionCard props
 */
export interface PredictionCardProps {
    /** Prediction data */
    prediction: Prediction;
    /** Callback to refresh prediction */
    onRefresh?: () => void;
    /** Loading state */
    loading?: boolean;
    /** Compact display mode */
    compact?: boolean;
    /** Show contributing factors */
    showFactors?: boolean;
}

/**
 * Get icon for prediction type
 */
const getTypeIcon = (type: PredictionType) => {
    switch (type) {
        case 'COMPLETION_DATE':
            return <ScheduleIcon />;
        case 'RISK_SCORE':
            return <WarningIcon />;
        case 'EFFORT_ESTIMATE':
            return <EffortIcon />;
        case 'SUCCESS_PROBABILITY':
            return <TrendingUpIcon />;
        case 'PRIORITY_SCORE':
            return <TrendingDownIcon />;
        default:
            return <InfoIcon />;
    }
};

/**
 * Get color based on confidence
 */
const getConfidenceColor = (confidence: number): 'success' | 'warning' | 'error' => {
    if (confidence >= 0.8) return 'success';
    if (confidence >= 0.6) return 'warning';
    return 'error';
};

/**
 * Get label for prediction type
 */
const getTypeLabel = (type: PredictionType): string => {
    switch (type) {
        case 'COMPLETION_DATE':
            return 'Estimated Completion';
        case 'RISK_SCORE':
            return 'Risk Score';
        case 'EFFORT_ESTIMATE':
            return 'Effort Estimate';
        case 'SUCCESS_PROBABILITY':
            return 'Success Probability';
        case 'PRIORITY_SCORE':
            return 'Priority Score';
        case 'BLOCKERS':
            return 'Potential Blockers';
        default:
            return type;
    }
};

/**
 * Contributing factor row component
 */
const FactorRow: React.FC<{ factor: ContributingFactor }> = ({ factor }) => {
    const impactColor = factor.impact > 0 ? 'success.main' : factor.impact < 0 ? 'error.main' : 'grey.500';
    const impactSign = factor.impact > 0 ? '+' : '';

    return (
        <Box
            className="flex items-center justify-between py-1"
        >
            <Typography as="p" className="text-sm" color="text.secondary">
                {factor.name}
            </Typography>
            <Box className="flex items-center gap-2">
                <LinearProgress
                    variant="determinate"
                    value={Math.min(Math.abs(factor.impact) * 100, 100)}
                    className="w-[60px] h-[6px] rounded-xl bg-gray-200" />
                <Typography
                    as="span" className="text-xs text-gray-500"
                    className="text-right min-w-[40px]" style={{ color: 'impactColor' }} >
                    {impactSign}{(factor.impact * 100).toFixed(0)}%
                </Typography>
            </Box>
        </Box>
    );
};

/**
 * PredictionCard Component
 */
export const PredictionCard: React.FC<PredictionCardProps> = ({
    prediction,
    onRefresh,
    loading = false,
    compact = false,
    showFactors = true,
}) => {
    const [expanded, setExpanded] = useState(false);

    const confidencePercent = Math.round(prediction.confidence * 100);
    const confidenceColor = getConfidenceColor(prediction.confidence);

    // Format predicted value
    const formatValue = (value: string | number): string => {
        if (typeof value === 'number') {
            if (prediction.type === 'RISK_SCORE' || prediction.type === 'SUCCESS_PROBABILITY') {
                return `${Math.round(value * 100)}%`;
            }
            return value.toFixed(1);
        }
        return String(value);
    };

    return (
        <Card
            variant="outlined"
            className="relative overflow-visible"
        >
            {/* Confidence indicator bar */}
            <LinearProgress
                variant="determinate"
                value={confidencePercent}
                color={confidenceColor}
                className="h-[4px]" style={{ borderTopLeftRadius: 4, borderTopRightRadius: 4 }} />

            <CardContent style={{ paddingBottom: compact ? 8 : 16 }}>
                {/* Header */}
                <Box className="flex items-center gap-2 mb-2">
                    <Box className="text-blue-600">{getTypeIcon(prediction.type)}</Box>
                    <Typography as="p" className="text-sm font-medium" color="text.secondary">
                        {getTypeLabel(prediction.type)}
                    </Typography>
                    <Box className="grow" />
                    <Tooltip title={`${confidencePercent}% confidence`}>
                        <Chip
                            size="sm"
                            label={`${confidencePercent}%`}
                            color={confidenceColor}
                            variant="outlined"
                        />
                    </Tooltip>
                </Box>

                {/* Predicted value */}
                <Typography as="h5" className="mb-2">
                    {formatValue(prediction.predictedValue)}
                </Typography>

                {/* Range indicator */}
                {prediction.range && (
                    <Typography as="p" className="text-sm" color="text.secondary">
                        Range: {formatValue(prediction.range.low)} - {formatValue(prediction.range.high)}
                    </Typography>
                )}

                {/* Model info */}
                {!compact && (
                    <Box className="flex gap-2 mt-2">
                        <Chip
                            size="sm"
                            label={prediction.modelVersion}
                            variant="outlined"
                            className="text-[0.7rem]"
                        />
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            Updated: {prediction.createdAt.toLocaleString()}
                        </Typography>
                    </Box>
                )}
            </CardContent>

            {/* Contributing factors */}
            {showFactors && prediction.contributingFactors.length > 0 && (
                <>
                    <Divider />
                    <CardActions className="px-4 py-2">
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            Contributing Factors
                        </Typography>
                        <Box className="grow" />
                        <IconButton size="sm" onClick={() => setExpanded(!expanded)}>
                            {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                        </IconButton>
                        {onRefresh && (
                            <IconButton size="sm" onClick={onRefresh} disabled={loading}>
                                <RefreshIcon size={16} />
                            </IconButton>
                        )}
                    </CardActions>

                    <Collapse in={expanded}>
                        <Box className="px-4 pb-4">
                            {prediction.contributingFactors.slice(0, 5).map((factor, idx) => (
                                <FactorRow key={idx} factor={factor} />
                            ))}
                            {prediction.contributingFactors.length > 5 && (
                                <Typography
                                    as="span" className="text-xs text-gray-500"
                                    color="text.secondary"
                                    className="block mt-2"
                                >
                                    +{prediction.contributingFactors.length - 5} more factors
                                </Typography>
                            )}
                        </Box>
                    </Collapse>
                </>
            )}
        </Card>
    );
};

export default PredictionCard;
