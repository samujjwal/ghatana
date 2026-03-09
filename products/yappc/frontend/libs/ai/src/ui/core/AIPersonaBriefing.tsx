/**
 * AI Persona Briefing Component
 *
 * Displays AI-generated daily briefing with personalized insights,
 * action items, and risk alerts for each persona.
 *
 * @module ai/components
 * @doc.type component
 * @doc.purpose AI daily briefing
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { Box, Card, CardContent, Typography, Chip, Stack, Button, Divider, Alert, Spinner as CircularProgress, InteractiveList as List, ListItem, ListItemIcon, ListItemText, IconButton, Collapse } from '@ghatana/ui';
import { TrendingUp, TrendingDown, AlertTriangle as Warning, CheckCircle, Info, ChevronDown as KeyboardArrowDown, KeyboardArrowUp, RefreshCw as Refresh } from 'lucide-react';
import { useAIInsightsGraphQL, usePredictionsGraphQL, useAnomaliesGraphQL } from '../hooks/useAI.graphql';

/**
 * Persona type
 */
export type PersonaType =
    | 'developer'
    | 'tech-lead'
    | 'devops-engineer'
    | 'security-engineer'
    | 'product-manager'
    | 'executive';

/**
 * Component props
 */
export interface AIPersonaBriefingProps {
    persona: PersonaType;
    userId?: string;
    compactMode?: boolean;
}

/**
 * Get persona-specific greeting
 */
function getPersonaGreeting(persona: PersonaType): string {
    const greetings: Record<PersonaType, string> = {
        developer: 'Good morning, Developer',
        'tech-lead': 'Good morning, Tech Lead',
        'devops-engineer': 'Good morning, DevOps Engineer',
        'security-engineer': 'Good morning, Security Engineer',
        'product-manager': 'Good morning, Product Manager',
        executive: 'Good morning, Executive',
    };
    return greetings[persona] || 'Good morning';
}

/**
 * Get persona-specific focus areas
 */
function getPersonaFocusAreas(persona: PersonaType): string[] {
    const focusAreas: Record<PersonaType, string[]> = {
        developer: ['Code Quality', 'Bug Fixes', 'Feature Development'],
        'tech-lead': ['Team Performance', 'Architecture', 'Technical Debt'],
        'devops-engineer': ['Deployment Pipeline', 'Infrastructure', 'Monitoring'],
        'security-engineer': ['Vulnerabilities', 'Compliance', 'Security Scanning'],
        'product-manager': ['Roadmap Progress', 'Feature Delivery', 'User Feedback'],
        executive: ['Business Metrics', 'Team Health', 'Strategic Initiatives'],
    };
    return focusAreas[persona] || [];
}

/**
 * AI Persona Briefing Component
 */
export function AIPersonaBriefing({ persona, userId, compactMode = false }: AIPersonaBriefingProps) {
    const [expanded, setExpanded] = React.useState(true);

    // Fetch AI data
    const { data: insights, isLoading: insightsLoading, refetch: refetchInsights } = useAIInsightsGraphQL({
        severity: ['critical', 'high', 'medium'],
        limit: 10,
    });

    const { data: predictions, isLoading: predictionsLoading, refetch: refetchPredictions } = usePredictionsGraphQL({
        minProbability: 0.6,
        limit: 5,
    });

    const { data: anomalies, isLoading: anomaliesLoading, refetch: refetchAnomalies } = useAnomaliesGraphQL({
        acknowledged: false,
        severity: ['critical', 'warning'],
        limit: 5,
    });

    const isLoading = insightsLoading || predictionsLoading || anomaliesLoading;

    const handleRefresh = () => {
        refetchInsights();
        refetchPredictions();
        refetchAnomalies();
    };

    // Generate summary
    const criticalCount = anomalies?.filter((a: unknown) => a.severity === 'critical').length || 0;
    const highPriorityInsights = insights?.filter((i: unknown) => i.severity === 'high' || i.severity === 'critical').length || 0;
    const highRiskPredictions = predictions?.filter((p: unknown) => p.probability > 0.7).length || 0;

    return (
        <Card elevation={2} className="mb-6">
            <CardContent>
                {/* Header */}
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                    <Box display="flex" alignItems="center" gap={2}>
                        <Typography as="h6" fontWeight="bold">
                            🤖 {getPersonaGreeting(persona)}
                        </Typography>
                        <Chip
                            label="AI-Powered"
                            size="sm"
                            tone="primary"
                            variant="outlined"
                        />
                    </Box>
                    <Stack direction="row" spacing={1}>
                        <IconButton size="sm" onClick={handleRefresh} disabled={isLoading}>
                            <Refresh size={16} />
                        </IconButton>
                        <IconButton
                            size="sm"
                            onClick={() => setExpanded(!expanded)}
                        >
                            {expanded ? <KeyboardArrowUp /> : <KeyboardArrowDown />}
                        </IconButton>
                    </Stack>
                </Box>

                <Collapse in={expanded}>
                    {/* Loading State */}
                    {isLoading && (
                        <Box display="flex" justifyContent="center" py={4}>
                            <CircularProgress size={32} />
                        </Box>
                    )}

                    {/* Summary Metrics */}
                    {!isLoading && (
                        <>
                            <Stack direction="row" spacing={2} mb={3}>
                                <Chip
                                    icon={<Warning />}
                                    label={`${criticalCount} Critical Alerts`}
                                    color={criticalCount > 0 ? 'error' : 'default'}
                                    size="sm"
                                />
                                <Chip
                                    icon={<Info />}
                                    label={`${highPriorityInsights} High Priority Insights`}
                                    color={highPriorityInsights > 0 ? 'warning' : 'default'}
                                    size="sm"
                                />
                                <Chip
                                    icon={<TrendingUp />}
                                    label={`${highRiskPredictions} Risk Predictions`}
                                    color={highRiskPredictions > 0 ? 'warning' : 'default'}
                                    size="sm"
                                />
                            </Stack>

                            {/* Critical Alerts */}
                            {criticalCount > 0 && (
                                <Alert severity="error" className="mb-4">
                                    <Typography as="p" className="text-sm font-medium" fontWeight="bold" gutterBottom>
                                        🚨 Critical Attention Required
                                    </Typography>
                                    <Typography as="p" className="text-sm">
                                        {criticalCount} critical {criticalCount === 1 ? 'issue' : 'issues'} detected that need immediate action.
                                    </Typography>
                                </Alert>
                            )}

                            {/* AI-Generated Summary */}
                            <Box mb={3}>
                                <Typography as="p" className="text-sm font-medium" fontWeight="bold" gutterBottom>
                                    Daily Summary
                                </Typography>
                                <Typography as="p" className="text-sm" color="text.secondary" paragraph>
                                    Based on your role as {persona}, here are today's key points:
                                </Typography>

                                <List dense>
                                    {anomalies && anomalies.length > 0 && (
                                        <ListItem>
                                            <ListItemIcon>
                                                <Warning tone="danger" size={16} />
                                            </ListItemIcon>
                                            <ListItemText
                                                primary="Anomalies Detected"
                                                secondary={`${anomalies.length} unusual patterns requiring attention`}
                                            />
                                        </ListItem>
                                    )}

                                    {predictions && predictions.length > 0 && (
                                        <ListItem>
                                            <ListItemIcon>
                                                <TrendingDown tone="warning" size={16} />
                                            </ListItemIcon>
                                            <ListItemText
                                                primary="Risk Predictions"
                                                secondary={`${predictions.length} potential risks forecasted`}
                                            />
                                        </ListItem>
                                    )}

                                    {insights && insights.length > 0 && (
                                        <ListItem>
                                            <ListItemIcon>
                                                <CheckCircle tone="success" size={16} />
                                            </ListItemIcon>
                                            <ListItemText
                                                primary="AI Insights Available"
                                                secondary={`${insights.length} actionable recommendations`}
                                            />
                                        </ListItem>
                                    )}
                                </List>
                            </Box>

                            {!compactMode && (
                                <>
                                    <Divider className="my-4" />

                                    {/* Focus Areas */}
                                    <Box>
                                        <Typography as="p" className="text-sm font-medium" fontWeight="bold" gutterBottom>
                                            Your Focus Areas Today
                                        </Typography>
                                        <Stack direction="row" spacing={1} flexWrap="wrap" gap={1}>
                                            {getPersonaFocusAreas(persona).map((area) => (
                                                <Chip
                                                    key={area}
                                                    label={area}
                                                    size="sm"
                                                    variant="outlined"
                                                />
                                            ))}
                                        </Stack>
                                    </Box>

                                    <Divider className="my-4" />

                                    {/* Quick Actions */}
                                    <Box>
                                        <Typography as="p" className="text-sm font-medium" fontWeight="bold" gutterBottom>
                                            Recommended Actions
                                        </Typography>
                                        <Stack direction="row" spacing={1}>
                                            <Button
                                                size="sm"
                                                variant="outlined"
                                                startIcon={<Warning />}
                                            >
                                                View Alerts ({anomalies?.length || 0})
                                            </Button>
                                            <Button
                                                size="sm"
                                                variant="outlined"
                                                startIcon={<TrendingUp />}
                                            >
                                                Review Predictions ({predictions?.length || 0})
                                            </Button>
                                            <Button
                                                size="sm"
                                                variant="outlined"
                                                startIcon={<Info />}
                                            >
                                                View All Insights
                                            </Button>
                                        </Stack>
                                    </Box>
                                </>
                            )}

                            {/* Timestamp */}
                            <Box mt={2} pt={2} borderTop="1px solid" borderColor="divider">
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    Generated by AI • Last updated: {new Date().toLocaleTimeString()}
                                </Typography>
                            </Box>
                        </>
                    )}
                </Collapse>
            </CardContent>
        </Card>
    );
}

export default AIPersonaBriefing;
