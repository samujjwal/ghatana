/**
 * Change Impact Analyzer - Analyze downstream effects of changes
 * 
 * Provides comprehensive analysis of how changes affect downstream
 * phases and components with mitigation strategies.
 */

import React, { useState } from 'react';
import { Box, Card, CardContent, Typography, Button, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Chip, Alert, Divider, Accordion, AccordionSummary, AccordionDetails } from '@ghatana/ui';
import { ChevronDown as ExpandMore, AlertTriangle as Warning, AlertCircle as Error, Info, CheckCircle, ArrowLeft as ArrowBack, RefreshCw as Refresh } from 'lucide-react';

interface ChangeImpactAnalyzerProps {
    projectId: string;
    onBack: () => void;
    onApplyChanges: () => void;
}

export function ChangeImpactAnalyzer({
    projectId,
    onBack,
    onApplyChanges
}: ChangeImpactAnalyzerProps) {
    const [selectedChanges, setSelectedChanges] = useState<string[]>([]);

    // Mock impact analysis data
    const impactData = {
        changes: [
            {
                id: 'ui-component-update',
                title: 'UI Component Structure Update',
                severity: 'high',
                description: 'Changes to component hierarchy will affect multiple views',
                affectedPhases: ['design', 'implement', 'test'],
                impact: {
                    breaking: true,
                    effort: 'medium',
                    risk: 'high'
                }
            },
            {
                id: 'api-endpoint-change',
                title: 'API Endpoint Modification',
                severity: 'medium',
                description: 'Updated response format for user data endpoint',
                affectedPhases: ['implement', 'test'],
                impact: {
                    breaking: false,
                    effort: 'low',
                    risk: 'medium'
                }
            },
            {
                id: 'data-model-update',
                title: 'Data Model Enhancement',
                severity: 'low',
                description: 'Added new fields to user profile model',
                affectedPhases: ['implement'],
                impact: {
                    breaking: false,
                    effort: 'low',
                    risk: 'low'
                }
            }
        ],
        recommendations: [
            'Update component tests to reflect new structure',
            'Regenerate API client with updated schema',
            'Review and update documentation',
            'Communicate changes to team members'
        ]
    };

    const getSeverityIcon = (severity: string) => {
        switch (severity) {
            case 'high': return <Error tone="danger" />;
            case 'medium': return <Warning tone="warning" />;
            case 'low': return <Info tone="info" />;
            default: return <Info />;
        }
    };

    const getSeverityColor = (severity: string) => {
        switch (severity) {
            case 'high': return 'error';
            case 'medium': return 'warning';
            case 'low': return 'info';
            default: return 'default';
        }
    };

    const handleToggleChange = (changeId: string) => {
        setSelectedChanges(prev =>
            prev.includes(changeId)
                ? prev.filter(id => id !== changeId)
                : [...prev, changeId]
        );
    };

    return (
        <Box className="h-full overflow-auto bg-bg-default p-6">
            {/* Header */}
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                <Box>
                    <Typography as="h4" fontWeight="bold" mb={1}>
                        Change Impact Analysis
                    </Typography>
                    <Typography as="p" color="text.secondary">
                        Review downstream effects of recent changes
                    </Typography>
                </Box>
                <Box display="flex" gap={2}>
                    <Button
                        variant="outlined"
                        startIcon={<Refresh />}
                        onClick={() => {/* Refresh analysis */ }}
                    >
                        Refresh
                    </Button>
                    <Button
                        variant="outlined"
                        startIcon={<ArrowBack />}
                        onClick={onBack}
                    >
                        Back
                    </Button>
                </Box>
            </Box>

            {/* Summary Alert */}
            <Alert severity="warning" className="mb-8">
                <Typography as="p" className="text-sm">
                    Found {impactData.changes.length} changes that may affect downstream phases.
                    Review and apply changes carefully to avoid breaking existing functionality.
                </Typography>
            </Alert>

            {/* Impact Analysis */}
            <Card variant="flat" className="mb-8 border border-solid border-gray-200 dark:border-gray-700">
                <CardContent>
                    <Typography as="h6" fontWeight="bold" mb={3}>
                        Detected Changes
                    </Typography>

                    <List>
                        {impactData.changes.map((change, index) => (
                            <React.Fragment key={change.id}>
                                <ListItem
                                    className={`flex-col items-start py-4 rounded cursor-pointer ${selectedChanges.includes(change.id) ? 'bg-gray-50 dark:bg-gray-800' : 'bg-transparent'}`}
                                    onClick={() => handleToggleChange(change.id)}
                                >
                                    <Box display="flex" alignItems="center" gap={2} width="100%">
                                        <ListItemIcon className="min-w-[32px]">
                                            {getSeverityIcon(change.severity)}
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={
                                                <Box display="flex" alignItems="center" gap={2}>
                                                    <Typography as="h6" fontWeight="medium">
                                                        {change.title}
                                                    </Typography>
                                                    <Chip
                                                        label={change.severity}
                                                        size="sm"
                                                        color={getSeverityColor(change.severity) as unknown}
                                                        variant="outlined"
                                                    />
                                                </Box>
                                            }
                                            secondary={change.description}
                                        />
                                    </Box>

                                    <Box display="flex" gap={1} mt={1} ml={8}>
                                        {change.affectedPhases.map(phase => (
                                            <Chip
                                                key={phase}
                                                label={phase}
                                                size="sm"
                                                variant="outlined"
                                                className="text-[0.7rem]"
                                            />
                                        ))}
                                    </Box>
                                </ListItem>
                                {index < impactData.changes.length - 1 && (
                                    <Divider variant="middle" component="li" />
                                )}
                            </React.Fragment>
                        ))}
                    </List>
                </CardContent>
            </Card>

            {/* Recommendations */}
            <Card variant="flat" className="mb-8 border border-solid border-gray-200 dark:border-gray-700">
                <CardContent>
                    <Typography as="h6" fontWeight="bold" mb={3}>
                        Recommendations
                    </Typography>

                    <List dense>
                        {impactData.recommendations.map((rec, index) => (
                            <ListItem key={index}>
                                <ListItemIcon className="min-w-[32px]">
                                    <CheckCircle tone="success" size={16} />
                                </ListItemIcon>
                                <ListItemText primary={rec} />
                            </ListItem>
                        ))}
                    </List>
                </CardContent>
            </Card>

            {/* Affected Phases Details */}
            <Card variant="flat" className="mb-8 border border-solid border-gray-200 dark:border-gray-700">
                <CardContent>
                    <Typography as="h6" fontWeight="bold" mb={3}>
                        Affected Phases Details
                    </Typography>

                    {['design', 'implement', 'test'].map(phase => {
                        const phaseChanges = impactData.changes.filter(change =>
                            change.affectedPhases.includes(phase)
                        );

                        if (phaseChanges.length === 0) return null;

                        return (
                            <Accordion key={phase} className="mb-2">
                                <AccordionSummary expandIcon={<ExpandMore />}>
                                    <Box display="flex" alignItems="center" gap={2}>
                                        <Typography as="h6" fontWeight="medium">
                                            {phase.charAt(0).toUpperCase() + phase.slice(1)} Phase
                                        </Typography>
                                        <Chip
                                            label={`${phaseChanges.length} changes`}
                                            size="sm"
                                            tone="primary"
                                            variant="outlined"
                                        />
                                    </Box>
                                </AccordionSummary>
                                <AccordionDetails>
                                    <List dense>
                                        {phaseChanges.map(change => (
                                            <ListItem key={change.id}>
                                                <ListItemIcon className="min-w-[32px]">
                                                    {getSeverityIcon(change.severity)}
                                                </ListItemIcon>
                                                <ListItemText
                                                    primary={change.title}
                                                    secondary={`Impact: ${change.impact.breaking ? 'Breaking' : 'Non-breaking'} | Effort: ${change.impact.effort} | Risk: ${change.impact.risk}`}
                                                />
                                            </ListItem>
                                        ))}
                                    </List>
                                </AccordionDetails>
                            </Accordion>
                        );
                    })}
                </CardContent>
            </Card>

            {/* Actions */}
            <Box display="flex" gap={2} justifyContent="flex-end">
                <Button
                    variant="outlined"
                    onClick={onBack}
                >
                    Cancel
                </Button>
                <Button
                    variant="solid"
                    onClick={onApplyChanges}
                    disabled={selectedChanges.length === 0}
                >
                    Apply {selectedChanges.length > 0 && `(${selectedChanges.length})`} Changes
                </Button>
            </Box>
        </Box>
    );
}
