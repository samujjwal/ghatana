/**
 * Recommendation Card Component
 * Individual recommendation display card for AI Insights Dashboard
 * @module components/AI/RecommendationCard
 */

import { Hammer as BuildIcon } from 'lucide-react';
import { X as CloseIcon } from 'lucide-react';
import { Lightbulb as LightbulbIcon } from 'lucide-react';
import { Play as PlayIcon } from 'lucide-react';
import { Gauge as SpeedIcon } from 'lucide-react';
import { TrendingUp as TrendingUpIcon } from 'lucide-react';
import { Box, Button, Card, CardActions, CardContent, Chip, LinearProgress, InteractiveList as List, ListItem, ListItemText, Typography } from '@ghatana/ui';
import { resolveMuiColor } from '../../utils/safePalette';
import React from 'react';

import { AIInsightsDashboardUtils } from './utils';

import type { RecommendationCardProps } from './types';

/**
 * Get type-specific icon for recommendation
 *
 * @param type - Recommendation type
 * @returns MUI icon component for the type
 */
function getTypeIcon(
    type: string
): React.ReactElement {
    switch (type) {
        case 'performance':
            return <SpeedIcon />;
        case 'resource':
            return <BuildIcon />;
        case 'process':
            return <TrendingUpIcon />;
        default:
            return <LightbulbIcon />;
    }
}

/**
 * Render expected impact section
 *
 * @param expectedImpact - Impact data
 * @returns JSX element
 */
function renderExpectedImpact(
    expectedImpact: RecommendationCardProps['recommendation']['expectedImpact']
): React.ReactElement {
    return (
        <Box className="mb-4">
            <Typography as="p" className="text-sm font-medium" gutterBottom>
                Expected Impact
            </Typography>
            <Box className="flex items-center gap-2 mb-2">
                <Typography as="p" className="text-sm" color="success.main">
                    +{expectedImpact.improvement}% improvement
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                    in {expectedImpact.metric}
                </Typography>
            </Box>
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                Timeline: {expectedImpact.timeframe}
            </Typography>
        </Box>
    );
}

/**
 * Render implementation steps section
 *
 * @param implementation - Implementation data
 * @returns JSX element
 */
function renderImplementationSteps(
    implementation: RecommendationCardProps['recommendation']['implementation']
): React.ReactElement {
    return (
        <Box className="mb-4">
            <Typography as="p" className="text-sm font-medium" gutterBottom>
                Implementation ({implementation.effort} effort)
            </Typography>
            <List dense>
                {implementation.steps.slice(0, 3).map((step, index) => (
                    <ListItem key={index} className="py-1 px-0">
                        <ListItemText
                            primary={step}
                            className="text-sm"
                        />
                    </ListItem>
                ))}
                {implementation.steps.length > 3 && (
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        ... and {implementation.steps.length - 3} more steps
                    </Typography>
                )}
            </List>
        </Box>
    );
}

/**
 * RecommendationCard - Displays individual optimization recommendation
 *
 * Renders a recommendation with:
 * - Title, description, and type icon
 * - Priority badge (color-coded)
 * - Expected impact and improvement metric
 * - Implementation steps and effort level
 * - Confidence score with progress indicator
 * - Implement and Dismiss action buttons
 *
 * @component
 *
 * @param props - Component props
 * @param props.recommendation - Recommendation data with impact, implementation, and confidence
 * @param props.onImplement - Callback function to handle implement action, receives recommendation ID
 * @param props.onDismiss - Callback function to handle dismiss action, receives recommendation ID
 * @param props.isImplementing - Whether recommendation is currently being implemented
 *
 * @example
 * ```tsx
 * <RecommendationCard
 *   recommendation={recommendation}
 *   isImplementing={isImplementing}
 *   onImplement={(id) => handleImplement(id)}
 *   onDismiss={(id) => handleDismiss(id)}
 * />
 * ```
 *
 * @returns Rendered recommendation card component
 */
export const RecommendationCard: React.FC<RecommendationCardProps> = ({
    recommendation,
    onImplement,
    onDismiss,
    isImplementing = false,
}): React.ReactElement => {
    const priorityColor = AIInsightsDashboardUtils.getPriorityColor(
        recommendation.priority
    );

    const handleImplementClick = (event: React.MouseEvent<HTMLButtonElement>): void => {
        event.preventDefault();
        onImplement(recommendation.id);
    };

    const handleDismissClick = (event: React.MouseEvent<HTMLButtonElement>): void => {
        event.preventDefault();
        onDismiss(recommendation.id);
    };

    const theme = useTheme();

    return (
        <Card className="h-full flex flex-col">
            <CardContent className="grow">
                <Box
                    className="flex items-center justify-between mb-4"
                >
                    <Box className="flex items-center gap-2">
                        {getTypeIcon(recommendation.type)}
                        <Typography as="h6" component="h3">
                            {recommendation.title}
                        </Typography>
                    </Box>
                    <Chip
                        size="sm"
                        label={recommendation.priority}
                        color={resolveMuiColor(theme, String(priorityColor), 'default') as unknown}
                        variant="filled"
                    />
                </Box>

                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    {recommendation.description}
                </Typography>

                {renderExpectedImpact(recommendation.expectedImpact)}
                {renderImplementationSteps(recommendation.implementation)}

                <Box className="flex items-center gap-2">
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        Confidence: {AIInsightsDashboardUtils.confidenceToPercent(recommendation.confidence)}
                    </Typography>
                    <LinearProgress
                        variant="determinate"
                        value={recommendation.confidence * 100}
                        className="grow h-[4px]"
                    />
                </Box>
            </CardContent>

            <CardActions>
                <Button
                    size="sm"
                    startIcon={<PlayIcon />}
                    onClick={handleImplementClick}
                    disabled={isImplementing}
                    variant="solid"
                >
                    {isImplementing ? 'Implementing...' : 'Implement'}
                </Button>
                <Button
                    size="sm"
                    startIcon={<CloseIcon />}
                    onClick={handleDismissClick}
                    disabled={isImplementing}
                >
                    Dismiss
                </Button>
            </CardActions>
        </Card>
    );
};

RecommendationCard.displayName = 'RecommendationCard';
