/**
 * ProjectAIInsights
 *
 * Displays AI-generated insights for a project with dismiss/action capabilities.
 *
 * @doc.type component
 * @doc.purpose Display AI insights panel for a project
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React from 'react';
import {
    Box,
    Typography,
    Alert,
    AlertTitle,
    Chip,
    CircularProgress,
    Button,
    Divider,
    Tooltip,
    IconButton,
} from '@mui/material';
import { X as CloseIcon, BrainCircuit as BrainIcon, Lightbulb as LightbulbIcon } from 'lucide-react';

import type { AIInsight } from '@yappc/state/aiAtoms';

type Severity = 'error' | 'warning' | 'info' | 'success';

const SEVERITY_MAP: Record<string, Severity> = {
    critical: 'error',
    high: 'error',
    warning: 'warning',
    medium: 'warning',
    low: 'info',
    info: 'info',
    success: 'success',
};

export interface ProjectAIInsightsProps {
    insights: AIInsight[];
    isLoading?: boolean;
    onDismiss?: (insightId: string) => void;
    onRefresh?: () => void;
    className?: string;
}

/**
 * Panel that shows AI-generated project insights.
 */
export const ProjectAIInsights: React.FC<ProjectAIInsightsProps> = ({
    insights,
    isLoading = false,
    onDismiss,
    onRefresh,
    className,
}) => {
    if (isLoading) {
        return (
            <Box display="flex" alignItems="center" gap={1} py={2} justifyContent="center">
                <CircularProgress size={20} />
                <Typography variant="body2" color="text.secondary">
                    Analysing project…
                </Typography>
            </Box>
        );
    }

    if (insights.length === 0) {
        return (
            <Box
                className={className}
                display="flex"
                flexDirection="column"
                alignItems="center"
                py={3}
                gap={1}
            >
                <BrainIcon size={32} opacity={0.4} />
                <Typography variant="body2" color="text.secondary">
                    No insights yet.
                </Typography>
                {onRefresh && (
                    <Button size="small" variant="text" onClick={onRefresh}>
                        Analyse now
                    </Button>
                )}
            </Box>
        );
    }

    return (
        <Box className={className} display="flex" flexDirection="column" gap={1}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={0.5}>
                <Box display="flex" alignItems="center" gap={0.5}>
                    <LightbulbIcon size={16} />
                    <Typography variant="subtitle2" fontWeight={600}>
                        AI Insights ({insights.length})
                    </Typography>
                </Box>
                {onRefresh && (
                    <Button size="small" variant="text" onClick={onRefresh}>
                        Refresh
                    </Button>
                )}
            </Box>

            <Divider />

            {insights.map((insight) => (
                <Alert
                    key={insight.id}
                    severity={SEVERITY_MAP[insight.severity?.toLowerCase()] ?? 'info'}
                    sx={{ position: 'relative', pr: onDismiss ? 5 : 2 }}
                >
                    {onDismiss && (
                        <Tooltip title="Dismiss">
                            <IconButton
                                size="small"
                                sx={{ position: 'absolute', top: 4, right: 4 }}
                                onClick={() => onDismiss(insight.id)}
                            >
                                <CloseIcon size={14} />
                            </IconButton>
                        </Tooltip>
                    )}

                    <AlertTitle sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                        {insight.title}
                        <Chip
                            label={insight.category}
                            size="small"
                            sx={{ height: 16, fontSize: 10 }}
                        />
                        <Chip
                            label={`${Math.round(insight.confidence * 100)}%`}
                            size="small"
                            variant="outlined"
                            sx={{ height: 16, fontSize: 10 }}
                        />
                    </AlertTitle>

                    <Typography variant="body2">{insight.description}</Typography>

                    {insight.actionItems.length > 0 && (
                        <Box mt={0.75}>
                            <Typography variant="caption" fontWeight={600}>
                                Action items:
                            </Typography>
                            <ul style={{ margin: '2px 0 0 16px', padding: 0 }}>
                                {insight.actionItems.map((item, i) => (
                                    <li key={i}>
                                        <Typography variant="caption">{item}</Typography>
                                    </li>
                                ))}
                            </ul>
                        </Box>
                    )}
                </Alert>
            ))}
        </Box>
    );
};
