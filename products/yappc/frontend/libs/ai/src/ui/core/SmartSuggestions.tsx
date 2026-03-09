/**
 * Smart Suggestions Component
 *
 * Displays AI-powered suggestions for the current context.
 * Shows recommendations for assignees, tags, priorities, and actions.
 *
 * Features:
 * - Context-aware suggestions
 * - Quick accept/dismiss actions
 * - Confidence indicators
 * - Grouped by type
 * - Inline and dropdown modes
 *
 * @doc.type component
 * @doc.purpose AI suggestion display and interaction
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useMemo, useCallback } from 'react';
import { Box, Chip, Surface as Paper, Typography, IconButton, Tooltip, Avatar, InteractiveList as List, ListItem, ListItemIcon as ListItemAvatar, ListItemText, ListItemText as ListItemSecondaryAction, Collapse, Divider, Badge, LinearProgress } from '@ghatana/ui';
import { Check as AcceptIcon, X as DismissIcon, ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon, User as AssigneeIcon, Label as TagIcon, Flag as PriorityIcon, ArrowRight as ActionIcon, Copy as SimilarIcon, Lightbulb as SuggestionIcon, ThumbsUp as FeedbackUpIcon, ThumbsDown as FeedbackDownIcon } from 'lucide-react';

import type { Recommendation, RecommendationType } from '@ghatana/yappc-types';

/**
 * SmartSuggestions props
 */
export interface SmartSuggestionsProps {
    /** Recommendations to display */
    recommendations: Recommendation[];
    /** Callback when recommendation is accepted */
    onAccept?: (recommendation: Recommendation) => void;
    /** Callback when recommendation is dismissed */
    onDismiss?: (recommendationId: string) => void;
    /** Callback when feedback is provided */
    onFeedback?: (recommendationId: string, helpful: boolean) => void;
    /** Display mode */
    mode?: 'inline' | 'list' | 'compact';
    /** Types to show */
    types?: RecommendationType[];
    /** Maximum items to show per type */
    maxPerType?: number;
    /** Show confidence scores */
    showConfidence?: boolean;
    /** Title for the section */
    title?: string;
}

/**
 * Get icon for recommendation type
 */
const getTypeIcon = (type: RecommendationType): React.ReactNode => {
    switch (type) {
        case 'ASSIGNEE':
            return <AssigneeIcon />;
        case 'TAG':
        case 'LABEL':
            return <TagIcon />;
        case 'PRIORITY':
            return <PriorityIcon />;
        case 'NEXT_ACTION':
            return <ActionIcon />;
        case 'SIMILAR_ITEMS':
            return <SimilarIcon />;
        default:
            return <SuggestionIcon />;
    }
};

/**
 * Get label for recommendation type
 */
const getTypeLabel = (type: RecommendationType): string => {
    switch (type) {
        case 'ASSIGNEE':
            return 'Suggested Assignees';
        case 'TAG':
            return 'Suggested Tags';
        case 'LABEL':
            return 'Suggested Labels';
        case 'PRIORITY':
            return 'Priority Suggestion';
        case 'NEXT_ACTION':
            return 'Next Actions';
        case 'SIMILAR_ITEMS':
            return 'Similar Items';
        case 'WORKFLOW':
            return 'Workflow Suggestions';
        case 'TIME_ESTIMATE':
            return 'Time Estimates';
        case 'DEPENDENCY':
            return 'Dependencies';
        default:
            return 'Suggestions';
    }
};

/**
 * Inline chip suggestion
 */
const InlineSuggestion: React.FC<{
    recommendation: Recommendation;
    onAccept?: (recommendation: Recommendation) => void;
    onDismiss?: (recommendationId: string) => void;
    showConfidence?: boolean;
}> = ({ recommendation, onAccept, onDismiss, showConfidence }) => {
    const confidenceColor =
        recommendation.confidence >= 0.8
            ? 'success'
            : recommendation.confidence >= 0.6
                ? 'warning'
                : 'default';

    return (
        <Tooltip
            title={
                <Box>
                    <Typography as="span" className="text-xs text-gray-500">{recommendation.reason}</Typography>
                    {showConfidence && (
                        <Typography as="span" className="text-xs text-gray-500" display="block">
                            Confidence: {Math.round(recommendation.confidence * 100)}%
                        </Typography>
                    )}
                </Box>
            }
        >
            <Chip
                size="sm"
                icon={getTypeIcon(recommendation.type) as React.ReactElement}
                label={recommendation.displayValue}
                color={confidenceColor as 'success' | 'warning' | 'default'}
                variant="outlined"
                onClick={onAccept ? () => onAccept(recommendation) : undefined}
                onDelete={onDismiss ? () => onDismiss(recommendation.id) : undefined}
                className="[&_.MuiChip-deleteIcon]:text-inherit"
            />
        </Tooltip>
    );
};

/**
 * List item suggestion
 */
const ListSuggestion: React.FC<{
    recommendation: Recommendation;
    onAccept?: (recommendation: Recommendation) => void;
    onDismiss?: (recommendationId: string) => void;
    onFeedback?: (recommendationId: string, helpful: boolean) => void;
    showConfidence?: boolean;
}> = ({ recommendation, onAccept, onDismiss, onFeedback, showConfidence }) => {
    return (
        <ListItem
            className="hover:bg-black/5"
        >
            <ListItemAvatar>
                <Avatar
                    className="w-[36px] h-[36px]" style={{ backgroundColor: recommendation.confidence >= 0.8
                                ? '#4caf50'
                                : recommendation.confidence >= 0.6
                                    ? '#ff9800'
                                    : '#e0e0e0' }}
                >
                    {getTypeIcon(recommendation.type)}
                </Avatar>
            </ListItemAvatar>
            <ListItemText
                primary={recommendation.displayValue}
                secondary={
                    <Box>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            {recommendation.reason}
                        </Typography>
                        {showConfidence && (
                            <Box className="flex items-center mt-1">
                                <LinearProgress
                                    variant="determinate"
                                    value={recommendation.confidence * 100}
                                    className="mr-2 rounded-lg w-[60px] h-[4px]"
                                />
                                <Typography as="span" className="text-xs text-gray-500">
                                    {Math.round(recommendation.confidence * 100)}%
                                </Typography>
                            </Box>
                        )}
                    </Box>
                }
            />
            <ListItemSecondaryAction>
                <Box className="flex gap-1">
                    {onFeedback && (
                        <>
                            <IconButton
                                size="sm"
                                onClick={() => onFeedback(recommendation.id, true)}
                            >
                                <FeedbackUpIcon size={16} />
                            </IconButton>
                            <IconButton
                                size="sm"
                                onClick={() => onFeedback(recommendation.id, false)}
                            >
                                <FeedbackDownIcon size={16} />
                            </IconButton>
                        </>
                    )}
                    {onAccept && (
                        <IconButton
                            size="sm"
                            tone="primary"
                            onClick={() => onAccept(recommendation)}
                        >
                            <AcceptIcon size={16} />
                        </IconButton>
                    )}
                    {onDismiss && (
                        <IconButton
                            size="sm"
                            onClick={() => onDismiss(recommendation.id)}
                        >
                            <DismissIcon size={16} />
                        </IconButton>
                    )}
                </Box>
            </ListItemSecondaryAction>
        </ListItem>
    );
};

/**
 * SmartSuggestions Component
 */
export const SmartSuggestions: React.FC<SmartSuggestionsProps> = ({
    recommendations = [],
    onAccept,
    onDismiss,
    onFeedback,
    mode = 'inline',
    types,
    maxPerType = 5,
    showConfidence = true,
    title = 'AI Suggestions',
}) => {
    const [expandedTypes, setExpandedTypes] = useState<Set<RecommendationType>>(new Set());

    // Filter and group recommendations
    const groupedRecommendations = useMemo(() => {
        const filtered = types
            ? recommendations.filter((r) => types.includes(r.type))
            : recommendations;

        const grouped = new Map<RecommendationType, Recommendation[]>();
        for (const rec of filtered) {
            const existing = grouped.get(rec.type) || [];
            grouped.set(rec.type, [...existing, rec]);
        }

        // Sort within each group by confidence
        grouped.forEach((recs, type) => {
            grouped.set(
                type,
                recs.sort((a, b) => b.confidence - a.confidence).slice(0, maxPerType)
            );
        });

        return grouped;
    }, [recommendations, types, maxPerType]);

    const toggleExpand = useCallback((type: RecommendationType) => {
        setExpandedTypes((prev) => {
            const next = new Set(prev);
            if (next.has(type)) {
                next.delete(type);
            } else {
                next.add(type);
            }
            return next;
        });
    }, []);

    if (recommendations.length === 0) {
        return null;
    }

    // Inline mode - just chips
    if (mode === 'inline') {
        return (
            <Box className="flex flex-wrap gap-2">
                {Array.from(groupedRecommendations.values())
                    .flat()
                    .slice(0, 10)
                    .map((rec) => (
                        <InlineSuggestion
                            key={rec.id}
                            recommendation={rec}
                            onAccept={onAccept}
                            onDismiss={onDismiss}
                            showConfidence={showConfidence}
                        />
                    ))}
            </Box>
        );
    }

    // Compact mode - minimal display
    if (mode === 'compact') {
        const topSuggestions = Array.from(groupedRecommendations.values())
            .flat()
            .slice(0, 3);

        return (
            <Box className="flex items-center gap-2">
                <Badge badgeContent={recommendations.length} tone="primary">
                    <SuggestionIcon color="action" />
                </Badge>
                <Box className="flex gap-1">
                    {topSuggestions.map((rec) => (
                        <Chip
                            key={rec.id}
                            size="sm"
                            label={rec.displayValue}
                            onClick={onAccept ? () => onAccept(rec) : undefined}
                            className="max-w-[120px]"
                        />
                    ))}
                </Box>
            </Box>
        );
    }

    // List mode - grouped list with expand/collapse
    return (
        <Paper variant="outlined" className="overflow-hidden">
            <Box
                className="p-3 flex items-center bg-gray-50" >
                <SuggestionIcon className="mr-2 text-blue-600" />
                <Typography as="p" className="text-sm font-medium">{title}</Typography>
                <Badge
                    badgeContent={recommendations.length}
                    tone="primary"
                    className="ml-2"
                />
            </Box>

            {Array.from(groupedRecommendations.entries()).map(([type, recs], idx) => (
                <Box key={type}>
                    {idx > 0 && <Divider />}

                    {/* Type header */}
                    <Box
                        className="flex items-center px-4 py-2 cursor-pointer hover:bg-gray-100"
                        onClick={() => toggleExpand(type)}
                    >
                        {getTypeIcon(type)}
                        <Typography as="p" className="text-sm ml-2 grow">
                            {getTypeLabel(type)}
                        </Typography>
                        <Chip size="sm" label={recs.length} className="mr-2" />
                        {expandedTypes.has(type) ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                    </Box>

                    {/* Recommendations list */}
                    <Collapse in={expandedTypes.has(type)}>
                        <List dense disablePadding>
                            {recs.map((rec) => (
                                <ListSuggestion
                                    key={rec.id}
                                    recommendation={rec}
                                    onAccept={onAccept}
                                    onDismiss={onDismiss}
                                    onFeedback={onFeedback}
                                    showConfidence={showConfidence}
                                />
                            ))}
                        </List>
                    </Collapse>
                </Box>
            ))}
        </Paper>
    );
};

export default SmartSuggestions;
