/**
 * SmartKanbanBoard Component
 *
 * AI-enhanced Kanban board with intelligent suggestions for item movements,
 * WIP limit optimization, and workflow insights.
 *
 * Features:
 * - AI-powered suggestions for item status transitions
 * - Automatic WIP limit violation warnings
 * - Smart item grouping recommendations
 * - Predictive completion estimates
 * - Workflow bottleneck detection
 *
 * @module DevSecOps/KanbanBoard
 * @doc.type component
 * @doc.purpose AI-enhanced Kanban board
 * @doc.layer product
 * @doc.pattern Component
 */

import { useMemo, useState, useCallback } from 'react';
import { Box, Surface as Paper, Typography, Chip, IconButton, Tooltip, Collapse, Alert, Badge, Fade, Stack } from '@ghatana/ui';
import { Sparkles as AIIcon, Lightbulb as SuggestionIcon, AlertTriangle as WarningIcon, Gauge as BottleneckIcon, CheckCircle as AcceptIcon, X as DismissIcon, ChevronDown as ExpandIcon, ChevronUp as CollapseIcon, LineChart as InsightsIcon } from 'lucide-react';

import { KanbanBoard } from './KanbanBoard';
import type { KanbanBoardProps, KanbanColumn } from './types';
import type { Item, ItemStatus } from '@ghatana/yappc-types/devsecops';

/**
 * AI Suggestion for Kanban operations
 */
export interface KanbanAISuggestion {
    id: string;
    type: 'move' | 'prioritize' | 'bottleneck' | 'workload' | 'completion';
    itemId?: string;
    itemTitle?: string;
    message: string;
    action?: {
        label: string;
        targetStatus?: ItemStatus;
        targetColumnId?: string;
    };
    severity: 'info' | 'warning' | 'success';
    confidence: number;
    reason: string;
}

/**
 * Board insights from AI analysis
 */
export interface BoardInsights {
    totalItems: number;
    blockedCount: number;
    wipViolations: number;
    avgCycleTime?: number;
    predictedCompletions: number;
    bottleneckColumn?: string;
    healthScore: number;
}

/**
 * SmartKanbanBoard props
 */
export interface SmartKanbanBoardProps extends KanbanBoardProps {
    /**
     * Enable AI suggestions
     * @default true
     */
    enableAI?: boolean;

    /**
     * Custom AI suggestions (for external AI integration)
     */
    suggestions?: KanbanAISuggestion[];

    /**
     * Callback when suggestion is accepted
     */
    onSuggestionAccept?: (suggestion: KanbanAISuggestion) => void;

    /**
     * Callback when suggestion is dismissed
     */
    onSuggestionDismiss?: (suggestionId: string) => void;

    /**
     * Show board insights panel
     * @default true
     */
    showInsights?: boolean;

    /**
     * Maximum suggestions to show
     * @default 3
     */
    maxSuggestions?: number;
}

/**
 * Default columns with more context
 */
const DEFAULT_COLUMNS: KanbanColumn[] = [
    { id: 'not-started', title: 'Not Started', status: 'not-started', color: '#9CA3AF', order: 1 },
    { id: 'in-progress', title: 'In Progress', status: 'in-progress', color: '#3B82F6', wipLimit: 5, order: 2 },
    { id: 'in-review', title: 'In Review', status: 'in-review', color: '#F59E0B', wipLimit: 3, order: 3 },
    { id: 'completed', title: 'Completed', status: 'completed', color: '#10B981', order: 4 },
    { id: 'blocked', title: 'Blocked', status: 'blocked', color: '#EF4444', order: 5 },
];

/**
 * Generate AI suggestions based on board state
 */
function generateAISuggestions(
    items: Item[],
    columns: KanbanColumn[],
): KanbanAISuggestion[] {
    const suggestions: KanbanAISuggestion[] = [];

    // Group items by status
    const itemsByStatus: Record<string, Item[]> = {};
    columns.forEach(col => {
        itemsByStatus[col.status] = items.filter(item => item.status === col.status);
    });

    // Check for WIP limit violations
    columns.forEach(col => {
        if (col.wipLimit) {
            const count = itemsByStatus[col.status]?.length || 0;
            if (count > col.wipLimit) {
                const overflow = count - col.wipLimit;
                suggestions.push({
                    id: `wip-${col.id}`,
                    type: 'bottleneck',
                    message: `${col.title} is over WIP limit by ${overflow} item${overflow > 1 ? 's' : ''}`,
                    severity: 'warning',
                    confidence: 1.0,
                    reason: 'WIP limit exceeded - consider moving items or increasing limit',
                });
            }
        }
    });

    // Check for blocked items that could be unblocked
    const blockedItems = itemsByStatus['blocked'] || [];
    blockedItems.forEach(item => {
        // Simulate AI analysis - in production this would call actual AI service
        const daysSinceUpdate = Math.floor(
            (Date.now() - new Date(item.updatedAt || Date.now()).getTime()) / (1000 * 60 * 60 * 24)
        );

        if (daysSinceUpdate > 2) {
            suggestions.push({
                id: `blocked-${item.id}`,
                type: 'move',
                itemId: item.id,
                itemTitle: item.title,
                message: `"${item.title}" has been blocked for ${daysSinceUpdate} days`,
                action: {
                    label: 'Escalate or unblock',
                    targetStatus: 'in-progress',
                },
                severity: 'warning',
                confidence: 0.8,
                reason: 'Long-blocked items may need attention or escalation',
            });
        }
    });

    // Check for items ready to move
    const inReviewItems = itemsByStatus['in-review'] || [];
    inReviewItems.slice(0, 2).forEach(item => {
        // Check if item has been in review for a while (simplified check)
        const daysSinceUpdate = Math.floor(
            (Date.now() - new Date(item.updatedAt || Date.now()).getTime()) / (1000 * 60 * 60 * 24)
        );

        if (daysSinceUpdate > 1) {
            suggestions.push({
                id: `review-${item.id}`,
                type: 'move',
                itemId: item.id,
                itemTitle: item.title,
                message: `"${item.title}" may be ready to complete`,
                action: {
                    label: 'Mark as completed',
                    targetStatus: 'completed',
                },
                severity: 'info',
                confidence: 0.7,
                reason: 'Item has been in review for over a day',
            });
        }
    });

    // Workload balance suggestion
    const inProgressCount = itemsByStatus['in-progress']?.length || 0;
    const notStartedCount = itemsByStatus['not-started']?.length || 0;

    if (inProgressCount < 2 && notStartedCount > 3) {
        suggestions.push({
            id: 'workload-balance',
            type: 'workload',
            message: 'Capacity available - consider starting more items',
            severity: 'success',
            confidence: 0.75,
            reason: `Only ${inProgressCount} items in progress with ${notStartedCount} waiting`,
        });
    }

    return suggestions;
}

/**
 * Calculate board insights
 */
function calculateInsights(items: Item[], columns: KanbanColumn[]): BoardInsights {
    const itemsByStatus: Record<string, Item[]> = {};
    columns.forEach(col => {
        itemsByStatus[col.status] = items.filter(item => item.status === col.status);
    });

    const blockedCount = itemsByStatus['blocked']?.length || 0;

    // Count WIP violations
    let wipViolations = 0;
    let bottleneckColumn: string | undefined;
    let maxOverage = 0;

    columns.forEach(col => {
        if (col.wipLimit) {
            const count = itemsByStatus[col.status]?.length || 0;
            if (count > col.wipLimit) {
                wipViolations++;
                const overage = count - col.wipLimit;
                if (overage > maxOverage) {
                    maxOverage = overage;
                    bottleneckColumn = col.title;
                }
            }
        }
    });

    // Calculate health score (0-100)
    const baseScore = 100;
    const blockedPenalty = blockedCount * 10;
    const wipPenalty = wipViolations * 15;
    const healthScore = Math.max(0, baseScore - blockedPenalty - wipPenalty);

    // Predicted completions (items likely to complete soon)
    const inReviewCount = itemsByStatus['in-review']?.length || 0;
    const predictedCompletions = Math.ceil(inReviewCount * 0.7);

    return {
        totalItems: items.length,
        blockedCount,
        wipViolations,
        predictedCompletions,
        bottleneckColumn,
        healthScore,
    };
}

/**
 * SmartKanbanBoard component
 */
export function SmartKanbanBoard({
    items,
    columns = DEFAULT_COLUMNS,
    enableAI = true,
    suggestions: externalSuggestions,
    onSuggestionAccept,
    onSuggestionDismiss,
    showInsights = true,
    maxSuggestions = 3,
    onItemMove,
    ...boardProps
}: SmartKanbanBoardProps) {
    const [showAIPanel, setShowAIPanel] = useState(true);
    const [dismissedIds, setDismissedIds] = useState<Set<string>>(new Set());

    // Generate or use external suggestions
    const allSuggestions = useMemo(() => {
        if (externalSuggestions) return externalSuggestions;
        if (!enableAI) return [];
        return generateAISuggestions(items, columns);
    }, [items, columns, enableAI, externalSuggestions]);

    // Filter out dismissed suggestions
    const activeSuggestions = useMemo(() => {
        return allSuggestions
            .filter(s => !dismissedIds.has(s.id))
            .slice(0, maxSuggestions);
    }, [allSuggestions, dismissedIds, maxSuggestions]);

    // Calculate insights
    const insights = useMemo(() => {
        return calculateInsights(items, columns);
    }, [items, columns]);

    // Handle suggestion accept
    const handleAccept = useCallback((suggestion: KanbanAISuggestion) => {
        if (onSuggestionAccept) {
            onSuggestionAccept(suggestion);
        } else if (suggestion.action?.targetStatus && suggestion.itemId) {
            // Default behavior: move item to target status
            const item = items.find(i => i.id === suggestion.itemId);
            if (item && onItemMove) {
                const sourceCol = columns.find(c => c.status === item.status);
                const targetCol = columns.find(c => c.status === suggestion.action?.targetStatus);
                if (sourceCol && targetCol) {
                    onItemMove({
                        item,
                        sourceColumnId: sourceCol.id,
                        targetColumnId: targetCol.id,
                        sourceStatus: item.status,
                        targetStatus: suggestion.action.targetStatus,
                    });
                }
            }
        }
        setDismissedIds(prev => new Set([...prev, suggestion.id]));
    }, [onSuggestionAccept, items, columns, onItemMove]);

    // Handle suggestion dismiss
    const handleDismiss = useCallback((suggestionId: string) => {
        setDismissedIds(prev => new Set([...prev, suggestionId]));
        onSuggestionDismiss?.(suggestionId);
    }, [onSuggestionDismiss]);

    const getSeverityColor = (severity: KanbanAISuggestion['severity']) => {
        switch (severity) {
            case 'warning': return 'warning';
            case 'success': return 'success';
            default: return 'info';
        }
    };

    return (
        <Box>
            {/* AI Panel */}
            {enableAI && (activeSuggestions.length > 0 || showInsights) && (
                <Paper
                    className="mb-4 p-4 bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700"
                >
                    {/* Header */}
                    <Box display="flex" alignItems="center" justifyContent="space-between" mb={showAIPanel ? 2 : 0}>
                        <Box display="flex" alignItems="center" gap={1}>
                            <Badge
                                badgeContent={activeSuggestions.length}
                                tone="primary"
                                invisible={activeSuggestions.length === 0}
                            >
                                <AIIcon tone="primary" />
                            </Badge>
                            <Typography as="p" className="text-lg font-medium" fontWeight={600}>
                                AI Insights
                            </Typography>
                            {insights.healthScore < 70 && (
                                <Chip
                                    size="sm"
                                    icon={<WarningIcon />}
                                    label={`Health: ${insights.healthScore}%`}
                                    tone="warning"
                                />
                            )}
                        </Box>
                        <IconButton size="sm" onClick={() => setShowAIPanel(!showAIPanel)}>
                            {showAIPanel ? <CollapseIcon /> : <ExpandIcon />}
                        </IconButton>
                    </Box>

                    <Collapse in={showAIPanel}>
                        <Stack spacing={2}>
                            {/* Insights */}
                            {showInsights && (
                                <Box display="flex" gap={2} flexWrap="wrap">
                                    <Chip
                                        icon={<InsightsIcon />}
                                        label={`${insights.totalItems} items`}
                                        size="sm"
                                        variant="outlined"
                                    />
                                    {insights.blockedCount > 0 && (
                                        <Chip
                                            label={`${insights.blockedCount} blocked`}
                                            size="sm"
                                            tone="danger"
                                            variant="outlined"
                                        />
                                    )}
                                    {insights.wipViolations > 0 && (
                                        <Chip
                                            icon={<BottleneckIcon />}
                                            label={`${insights.wipViolations} WIP violation${insights.wipViolations > 1 ? 's' : ''}`}
                                            size="sm"
                                            tone="warning"
                                            variant="outlined"
                                        />
                                    )}
                                    {insights.predictedCompletions > 0 && (
                                        <Chip
                                            label={`~${insights.predictedCompletions} near completion`}
                                            size="sm"
                                            tone="success"
                                            variant="outlined"
                                        />
                                    )}
                                    {insights.bottleneckColumn && (
                                        <Chip
                                            icon={<WarningIcon />}
                                            label={`Bottleneck: ${insights.bottleneckColumn}`}
                                            size="sm"
                                            tone="warning"
                                        />
                                    )}
                                </Box>
                            )}

                            {/* Suggestions */}
                            {activeSuggestions.map((suggestion) => (
                                <Fade in key={suggestion.id}>
                                    <Alert
                                        severity={getSeverityColor(suggestion.severity)}
                                        icon={<SuggestionIcon />}
                                        action={
                                            <Box>
                                                {suggestion.action && (
                                                    <Tooltip title={suggestion.action.label}>
                                                        <IconButton
                                                            size="sm"
                                                            tone="neutral"
                                                            onClick={() => handleAccept(suggestion)}
                                                        >
                                                            <AcceptIcon />
                                                        </IconButton>
                                                    </Tooltip>
                                                )}
                                                <Tooltip title="Dismiss">
                                                    <IconButton
                                                        size="sm"
                                                        tone="neutral"
                                                        onClick={() => handleDismiss(suggestion.id)}
                                                    >
                                                        <DismissIcon />
                                                    </IconButton>
                                                </Tooltip>
                                            </Box>
                                        }
                                    >
                                        <Typography as="h6" className="text-sm" fontWeight={600}>
                                            {suggestion.message}
                                        </Typography>
                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                            {suggestion.reason}
                                            {suggestion.confidence < 1 && (
                                                <> • {Math.round(suggestion.confidence * 100)}% confident</>
                                            )}
                                        </Typography>
                                    </Alert>
                                </Fade>
                            ))}
                        </Stack>
                    </Collapse>
                </Paper>
            )}

            {/* Kanban Board */}
            <KanbanBoard
                items={items}
                columns={columns}
                onItemMove={onItemMove}
                {...boardProps}
            />
        </Box>
    );
}

export default SmartKanbanBoard;
