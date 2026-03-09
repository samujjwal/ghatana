/**
 * SentimentIndicator Component
 * 
 * Real-time sentiment indicator with visual feedback (emoji, color, confidence).
 * Displays current sentiment and tracks historical trends.
 * 
 * Features:
 * - Real-time sentiment display
 * - Visual indicators (emoji, color, icon)
 * - Confidence score display
 * - Historical tracking (optional)
 * - Trend analysis
 * - Compact and expanded modes
 * 
 * @example
 * ```tsx
 * <SentimentIndicator
 *   analyzer={sentimentAnalyzer}
 *   text="This is amazing!"
 *   showHistory={true}
 *   variant="detailed"
 * />
 * ```
 */

import { SentimentVerySatisfied as HappyIcon, SentimentNeutral as NeutralIcon, SentimentVeryDissatisfied as SadIcon, ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon } from 'lucide-react';
import { Box, Chip, Typography, LinearProgress, Surface as Paper, Tooltip, Stack, IconButton, Collapse } from '@ghatana/ui';
import type { SentimentAnalyzer } from '@ghatana/yappc-ai/core';
import { type SentimentResult } from '@ghatana/yappc-ai/core';
import { resolveMuiColor } from '../utils/safePalette';
import React, { useState, useEffect } from 'react';

/**
 *
 */
export interface SentimentIndicatorProps {
    /** Sentiment analyzer instance */
    analyzer: SentimentAnalyzer;

    /** Text to analyze */
    text: string;

    /** Whether to show historical data */
    showHistory?: boolean;

    /** Maximum number of history items */
    maxHistory?: number;

    /** Display variant */
    variant?: 'compact' | 'detailed' | 'minimal';

    /** Whether to auto-analyze when text changes */
    autoAnalyze?: boolean;

    /** Callback when sentiment changes */
    onSentimentChange?: (result: SentimentResult) => void;

    /** Minimum text length to trigger analysis */
    minLength?: number;

    /** Custom styling */
    className?: string;
}

/**
 *
 */
interface HistoryItem {
    timestamp: number;
    result: SentimentResult;
    text: string;
}

const SENTIMENT_CONFIG = {
    positive: {
        emoji: '😊',
        icon: <HappyIcon />,
        color: 'success' as const,
        label: 'Positive',
    },
    neutral: {
        emoji: '😐',
        icon: <NeutralIcon />,
        color: 'default' as const,
        label: 'Neutral',
    },
    negative: {
        emoji: '😞',
        icon: <SadIcon />,
        color: 'error' as const,
        label: 'Negative',
    },
};

export const SentimentIndicator: React.FC<SentimentIndicatorProps> = ({
    analyzer,
    text,
    showHistory = false,
    maxHistory = 10,
    variant = 'detailed',
    autoAnalyze = true,
    onSentimentChange,
    minLength = 5,
    className,
}) => {
    const theme = useTheme();
    const [result, setResult] = useState<SentimentResult | null>(null);
    const [history, setHistory] = useState<HistoryItem[]>([]);
    const [isAnalyzing, setIsAnalyzing] = useState(false);
    const [isExpanded, setIsExpanded] = useState(false);

    useEffect(() => {
        if (!autoAnalyze || text.length < minLength) {
            return;
        }

        const analyzeText = async () => {
            setIsAnalyzing(true);
            try {
                const sentimentResult = await analyzer.analyze(text);
                setResult(sentimentResult);

                if (showHistory) {
                    setHistory((prev) => [
                        {
                            timestamp: Date.now(),
                            result: sentimentResult,
                            text: text.slice(0, 50) + (text.length > 50 ? '...' : ''),
                        },
                        ...prev.slice(0, maxHistory - 1),
                    ]);
                }

                onSentimentChange?.(sentimentResult);
            } catch (error) {
                console.error('Sentiment analysis failed:', error);
            } finally {
                setIsAnalyzing(false);
            }
        };

        const debounceTimer = setTimeout(analyzeText, 500);
        return () => clearTimeout(debounceTimer);
    }, [text, analyzer, autoAnalyze, minLength, maxHistory, showHistory, onSentimentChange]);

    if (!result) {
        return null;
    }

    const config = SENTIMENT_CONFIG[result.sentiment];

    const renderMinimal = () => (
        <Tooltip title={`${config.label} (${Math.round(result.confidence * 100)}% confidence)`}>
            <Chip
                icon={config.icon}
                label={config.emoji}
                color={resolveMuiColor(theme, String(config.color), 'default')}
                size="sm"
                className={className}
            />
        </Tooltip>
    );

    const renderCompact = () => (
        <Chip
            icon={config.icon}
            label={`${config.emoji} ${config.label}`}
            color={resolveMuiColor(theme, String(config.color), 'default')}
            size="md"
            className={className}
        />
    );

    const renderDetailed = () => (
        <Paper
            elevation={2}
            className={`p-4 min-w-[280px] ${className || ''}`}
        >
            <Stack spacing={2}>
                {/* Main sentiment display */}
                <Box className="flex items-center gap-2">
                    <Typography as="h4">{config.emoji}</Typography>
                    <Box className="flex-1">
                        <Typography as="h6">{config.label}</Typography>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            {Math.round(result.confidence * 100)}% confident
                        </Typography>
                    </Box>
                    {showHistory && history.length > 0 && (
                        <IconButton
                            size="sm"
                            onClick={() => setIsExpanded(!isExpanded)}
                        >
                            {isExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                        </IconButton>
                    )}
                </Box>

                {/* Confidence bar */}
                {isAnalyzing ? (
                    <LinearProgress />
                ) : (
                    <LinearProgress
                        variant="determinate"
                        value={result.confidence * 100}
                        color={resolveMuiColor(theme, String(config.color), 'default')}
                    />
                )}

                {/* Score breakdown */}
                <Stack spacing={1}>
                    <Box className="flex justify-between">
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            😊 Positive
                        </Typography>
                        <Typography as="span" className="text-xs text-gray-500" className="font-bold">
                            {Math.round(result.scores.positive * 100)}%
                        </Typography>
                    </Box>
                    <LinearProgress
                        variant="determinate"
                        value={result.scores.positive * 100}
                        tone="success"
                        className="h-[4px]"
                    />

                    <Box className="flex justify-between">
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            😐 Neutral
                        </Typography>
                        <Typography as="span" className="text-xs text-gray-500" className="font-bold">
                            {Math.round(result.scores.neutral * 100)}%
                        </Typography>
                    </Box>
                    <LinearProgress
                        variant="determinate"
                        value={result.scores.neutral * 100}
                        className="h-[4px]"
                    />

                    <Box className="flex justify-between">
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            😞 Negative
                        </Typography>
                        <Typography as="span" className="text-xs text-gray-500" className="font-bold">
                            {Math.round(result.scores.negative * 100)}%
                        </Typography>
                    </Box>
                    <LinearProgress
                        variant="determinate"
                        value={result.scores.negative * 100}
                        tone="danger"
                        className="h-[4px]"
                    />
                </Stack>

                {/* Explanation if available */}
                {result.explanation && (
                    <Box
                        className="p-2 rounded bg-gray-100 dark:bg-gray-800"
                    >
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            {result.explanation}
                        </Typography>
                    </Box>
                )}

                {/* History */}
                {showHistory && history.length > 0 && (
                    <Collapse in={isExpanded}>
                        <Box className="pt-2 border-gray-200 dark:border-gray-700 border-t" >
                            <Typography as="p" className="text-sm font-medium" gutterBottom>
                                History
                            </Typography>
                            <Stack spacing={1}>
                                {history.map((item, index) => {
                                    const itemConfig = SENTIMENT_CONFIG[item.result.sentiment];
                                    return (
                                        <Box
                                            key={item.timestamp}
                                            className={`flex items-center gap-2 p-2 rounded ${index === 0 ? 'bg-gray-100 dark:bg-gray-800' : 'bg-transparent'}`}
                                        >
                                            <Typography as="p" className="text-sm">{itemConfig.emoji}</Typography>
                                            <Box className="flex-1 min-w-0">
                                                <Typography
                                                    as="span" className="text-xs text-gray-500"
                                                    color="text.secondary"
                                                    className="overflow-hidden text-ellipsis whitespace-nowrap block"
                                                >
                                                    {item.text}
                                                </Typography>
                                            </Box>
                                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                {Math.round(item.result.confidence * 100)}%
                                            </Typography>
                                        </Box>
                                    );
                                })}
                            </Stack>
                        </Box>
                    </Collapse>
                )}
            </Stack>
        </Paper>
    );

    switch (variant) {
        case 'minimal':
            return renderMinimal();
        case 'compact':
            return renderCompact();
        case 'detailed':
            return renderDetailed();
        default:
            return renderDetailed();
    }
};
