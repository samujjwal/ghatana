/**
 * AI Suggestions Panel Component
 * 
 * Displays AI-generated suggestions with accept/dismiss actions
 * 
 * @doc.type component
 * @doc.purpose Display and manage AI suggestions
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import {
  Badge,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  Divider,
  IconButton,
  Stack,
  Tooltip,
  Typography,
} from '@ghatana/ui';
import { Drawer } from '@ghatana/ui';
import { Sparkles as AIIcon, CheckCircle as AcceptIcon, X as DismissIcon, Lightbulb as SuggestionIcon, AlertTriangle as RiskIcon, Gauge as OptimizationIcon, Puzzle as PatternIcon, Link as ConnectionIcon, GitBranch as NodeIcon } from 'lucide-react';
import type { AISuggestion, SuggestionType } from '@/hooks/useAIAssistant';

// ============================================================================
// Suggestion Icon Mapping
// ============================================================================

const getSuggestionIcon = (type: SuggestionType) => {
    switch (type) {
        case 'node':
            return <NodeIcon />;
        case 'connection':
            return <ConnectionIcon />;
        case 'pattern':
            return <PatternIcon />;
        case 'gap':
            return <SuggestionIcon />;
        case 'risk':
            return <RiskIcon />;
        case 'optimization':
            return <OptimizationIcon />;
        default:
            return <SuggestionIcon />;
    }
};

const getSuggestionColor = (priority: string) => {
    switch (priority) {
        case 'critical':
            return 'error';
        case 'high':
            return 'warning';
        case 'medium':
            return 'info';
        case 'low':
            return 'default';
        default:
            return 'default';
    }
};

// ============================================================================
// Suggestion Card Component
// ============================================================================

interface SuggestionCardProps {
    suggestion: AISuggestion;
    onAccept: (id: string) => void;
    onDismiss: (id: string) => void;
}

const SuggestionCard: React.FC<SuggestionCardProps> = ({
    suggestion,
    onAccept,
    onDismiss,
}) => {
    const icon = getSuggestionIcon(suggestion.type);
    const color = getSuggestionColor(suggestion.priority);
    const confidence = Math.round(suggestion.confidence * 100);

    return (
        <Card
            variant="outlined"
            className="mb-2" style={{ borderLeft: `4px solid`, borderLeftColor: `${color }}
        >
            <CardContent className="pb-2">
                <Stack direction="row" alignItems="flex-start" spacing={1}>
                    <Box style={{ color: `${color }}>
                        {icon}
                    </Box>
                    <Box className="flex-1">
                        <Typography variant="subtitle2" fontWeight="bold">
                            {suggestion.title}
                        </Typography>
                        <Typography variant="body2" color="text.secondary" className="mt-1">
                            {suggestion.description}
                        </Typography>
                        <Stack direction="row" spacing={1} className="mt-2">
                            <Chip
                                label={suggestion.type}
                                size="small"
                                variant="outlined"
                            />
                            <Chip
                                label={`${confidence}% confident`}
                                size="small"
                                color={color}
                                variant="filled"
                            />
                        </Stack>
                    </Box>
                </Stack>
            </CardContent>
            <CardActions className="justify-end pt-0">
                <Button
                    size="small"
                    onClick={() => onDismiss(suggestion.id)}
                    startIcon={<DismissIcon />}
                >
                    Dismiss
                </Button>
                <Button
                    size="small"
                    variant="contained"
                    onClick={() => onAccept(suggestion.id)}
                    startIcon={<AcceptIcon />}
                    color={color}
                >
                    Accept
                </Button>
            </CardActions>
        </Card>
    );
};

// ============================================================================
// AI Suggestions Panel
// ============================================================================

export interface AISuggestionsPanelProps {
    suggestions: AISuggestion[];
    onAccept: (id: string) => void;
    onDismiss: (id: string) => void;
    onDismissAll: () => void;
    isAnalyzing?: boolean;
    open?: boolean;
    onClose?: () => void;
}

export const AISuggestionsPanel: React.FC<AISuggestionsPanelProps> = ({
    suggestions,
    onAccept,
    onDismiss,
    onDismissAll,
    isAnalyzing = false,
    open = true,
    onClose,
}) => {
    const criticalCount = suggestions.filter((s) => s.priority === 'critical').length;
    const highCount = suggestions.filter((s) => s.priority === 'high').length;

    return (
        <Drawer
            anchor="right"
            open={open}
            onClose={onClose}
            variant="persistent"
            className="w-[400px] shrink-0 w-[400px] box-border mt-16"
        >
            <Box className="p-4">
                {/* Header */}
                <Stack direction="row" alignItems="center" justifyContent="space-between" mb={2}>
                    <Stack direction="row" alignItems="center" spacing={1}>
                        <AIIcon color="primary" />
                        <Typography variant="h6">AI Suggestions</Typography>
                        {suggestions.length > 0 && (
                            <Badge badgeContent={suggestions.length} color="primary" />
                        )}
                    </Stack>
                    {onClose && (
                        <IconButton size="small" onClick={onClose}>
                            <DismissIcon />
                        </IconButton>
                    )}
                </Stack>

                {/* Status */}
                {isAnalyzing && (
                    <Box className="mb-4">
                        <Typography variant="body2" color="text.secondary">
                            Analyzing canvas...
                        </Typography>
                    </Box>
                )}

                {/* Priority Summary */}
                {(criticalCount > 0 || highCount > 0) && (
                    <Box className="mb-4">
                        <Stack direction="row" spacing={1}>
                            {criticalCount > 0 && (
                                <Chip
                                    label={`${criticalCount} Critical`}
                                    size="small"
                                    color="error"
                                />
                            )}
                            {highCount > 0 && (
                                <Chip
                                    label={`${highCount} High`}
                                    size="small"
                                    color="warning"
                                />
                            )}
                        </Stack>
                    </Box>
                )}

                <Divider className="mb-4" />

                {/* Suggestions List */}
                {suggestions.length === 0 ? (
                    <Box className="text-center py-8">
                        <AIIcon className="mb-4 text-5xl text-gray-400 dark:text-gray-600" />
                        <Typography variant="body2" color="text.secondary">
                            No suggestions at the moment
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            Keep designing and I'll suggest improvements
                        </Typography>
                    </Box>
                ) : (
                    <>
                        <Box className="overflow-y-auto max-h-[calc(100vh - 300px)]">
                            {suggestions.map((suggestion) => (
                                <SuggestionCard
                                    key={suggestion.id}
                                    suggestion={suggestion}
                                    onAccept={onAccept}
                                    onDismiss={onDismiss}
                                />
                            ))}
                        </Box>

                        {/* Actions */}
                        <Box className="mt-4 pt-4 border-gray-200 dark:border-gray-700 border-t" >
                            <Button
                                fullWidth
                                variant="outlined"
                                onClick={onDismissAll}
                                startIcon={<DismissIcon />}
                            >
                                Dismiss All
                            </Button>
                        </Box>
                    </>
                )}
            </Box>
        </Drawer>
    );
};

// ============================================================================
// Compact AI Badge (for toolbar)
// ============================================================================

export interface AIBadgeProps {
    count: number;
    isAnalyzing?: boolean;
    onClick?: () => void;
}

export const AIBadge: React.FC<AIBadgeProps> = ({
    count,
    isAnalyzing = false,
    onClick,
}) => {
    return (
        <Tooltip title={isAnalyzing ? 'AI is analyzing...' : `${count} AI suggestions`}>
            <Badge badgeContent={count} color="primary">
                <IconButton
                    onClick={onClick}
                    className="bg-[rgba(255,_255,_255,_0.9)] hover:bg-[rgba(255,_255,_255,_1)]"
                >
                    <AIIcon color={count > 0 ? 'primary' : 'action'} />
                </IconButton>
            </Badge>
        </Tooltip>
    );
};
