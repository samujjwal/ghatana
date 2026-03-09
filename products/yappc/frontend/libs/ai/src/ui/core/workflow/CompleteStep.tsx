/**
 * Complete Step Component
 *
 * Eighth and final step in the AI-powered workflow wizard.
 * Shows workflow completion summary and next actions.
 *
 * @doc.type component
 * @doc.purpose Complete workflow step
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { Box, Surface as Paper, Typography, Button, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Alert, Chip, Divider, Grid, Card, CardContent, CardActions } from '@ghatana/ui';
import { CheckCircle as CompleteIcon, Celebration as CelebrationIcon, Code as CodeIcon, Bug as TestIcon, CloudCheck as DeployIcon, BarChart3 as StatsIcon, RefreshCw as NewWorkflowIcon, Eye as ViewIcon, Download as DownloadIcon, Share2 as ShareIcon, Activity as TimelineIcon } from 'lucide-react';

export interface CompleteStepProps {
    workflowData: WorkflowSummary;
    onNewWorkflow?: () => void;
    onViewDeployment?: () => void;
    onDownloadReport?: () => void;
    error?: string | null;
}

export interface WorkflowSummary {
    workflowId: string;
    intent: string;
    filesCreated: number;
    filesModified: number;
    testsGenerated: number;
    testsPassed: number;
    testsFailed: number;
    deploymentUrl?: string;
    environment?: string;
    startTime: Date;
    endTime: Date;
    steps: StepSummary[];
}

interface StepSummary {
    name: string;
    status: 'success' | 'failed' | 'skipped';
    duration: number;
}

export const CompleteStep: React.FC<CompleteStepProps> = ({
    workflowData,
    onNewWorkflow,
    onViewDeployment,
    onDownloadReport,
    error = null,
}) => {
    const totalDuration = workflowData.endTime.getTime() - workflowData.startTime.getTime();
    const formatDuration = (ms: number) => {
        if (ms < 1000) return `${ms}ms`;
        if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
        return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`;
    };

    const allPassed = workflowData.testsFailed === 0;

    return (
        <Box className="p-6 text-center">
            <CelebrationIcon className="mb-4 text-[64px] text-green-600" />

            <Typography as="h4" gutterBottom color="success.main">
                Workflow Complete!
            </Typography>

            <Typography as="p" color="text.secondary" className="mb-6">
                Your AI-powered development workflow has completed successfully.
            </Typography>

            {error && <Alert severity="error" className="mb-6">{error}</Alert>}

            {/* Summary Cards */}
            <Grid container spacing={2} className="mb-6">
                <Grid item xs={12} sm={6} md={3}>
                    <Card variant="outlined">
                        <CardContent>
                            <CodeIcon tone="primary" className="text-[40px]" />
                            <Typography as="h4">{workflowData.filesCreated + workflowData.filesModified}</Typography>
                            <Typography as="p" className="text-sm" color="text.secondary">
                                Files Changed
                            </Typography>
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                {workflowData.filesCreated} created, {workflowData.filesModified} modified
                            </Typography>
                        </CardContent>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card variant="outlined">
                        <CardContent>
                            <TestIcon color={allPassed ? 'success' : 'warning'} className="text-[40px]" />
                            <Typography as="h4">
                                {workflowData.testsPassed}/{workflowData.testsGenerated}
                            </Typography>
                            <Typography as="p" className="text-sm" color="text.secondary">
                                Tests Passed
                            </Typography>
                            {workflowData.testsFailed > 0 && (
                                <Chip size="sm" label={`${workflowData.testsFailed} failed`} tone="danger" />
                            )}
                        </CardContent>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card variant="outlined">
                        <CardContent>
                            <DeployIcon tone="success" className="text-[40px]" />
                            <Typography as="h6">{workflowData.environment || 'N/A'}</Typography>
                            <Typography as="p" className="text-sm" color="text.secondary">
                                Deployed To
                            </Typography>
                        </CardContent>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card variant="outlined">
                        <CardContent>
                            <TimelineIcon tone="info" className="text-[40px]" />
                            <Typography as="h6">{formatDuration(totalDuration)}</Typography>
                            <Typography as="p" className="text-sm" color="text.secondary">
                                Total Duration
                            </Typography>
                        </CardContent>
                    </Card>
                </Grid>
            </Grid>

            {/* Intent Summary */}
            <Paper variant="outlined" className="p-4 mb-6 text-left">
                <Typography as="p" className="text-sm font-medium" color="text.secondary" gutterBottom>
                    Original Intent
                </Typography>
                <Typography as="p">{workflowData.intent}</Typography>
            </Paper>

            {/* Step Timeline */}
            <Paper variant="outlined" className="p-4 mb-6 text-left">
                <Typography as="p" className="text-sm font-medium" color="text.secondary" gutterBottom>
                    Workflow Steps
                </Typography>
                <List dense>
                    {workflowData.steps.map((step, index) => (
                        <ListItem key={index}>
                            <ListItemIcon>
                                {step.status === 'success' ? (
                                    <CompleteIcon tone="success" />
                                ) : step.status === 'failed' ? (
                                    <CompleteIcon tone="danger" />
                                ) : (
                                    <CompleteIcon color="disabled" />
                                )}
                            </ListItemIcon>
                            <ListItemText
                                primary={step.name}
                                secondary={formatDuration(step.duration)}
                            />
                            <Chip
                                size="sm"
                                label={step.status}
                                color={step.status === 'success' ? 'success' : step.status === 'failed' ? 'error' : 'default'}
                            />
                        </ListItem>
                    ))}
                </List>
            </Paper>

            {/* Deployment URL */}
            {workflowData.deploymentUrl && (
                <Alert severity="success" className="mb-6">
                    Your application is live at:{' '}
                    <a href={workflowData.deploymentUrl} target="_blank" rel="noopener noreferrer">
                        {workflowData.deploymentUrl}
                    </a>
                </Alert>
            )}

            {/* Actions */}
            <Divider className="my-6" />

            <Box className="flex justify-center gap-4 flex-wrap">
                {onNewWorkflow && (
                    <Button
                        variant="solid"
                        startIcon={<NewWorkflowIcon />}
                        onClick={onNewWorkflow}
                    >
                        Start New Workflow
                    </Button>
                )}

                {onViewDeployment && workflowData.deploymentUrl && (
                    <Button
                        variant="outlined"
                        startIcon={<ViewIcon />}
                        onClick={onViewDeployment}
                    >
                        View Deployment
                    </Button>
                )}

                {onDownloadReport && (
                    <Button
                        variant="outlined"
                        startIcon={<DownloadIcon />}
                        onClick={onDownloadReport}
                    >
                        Download Report
                    </Button>
                )}

                <Button
                    variant="outlined"
                    startIcon={<ShareIcon />}
                    onClick={() => {
                        navigator.clipboard.writeText(
                            `Completed AI workflow: ${workflowData.intent}\n${workflowData.deploymentUrl || ''}`
                        );
                    }}
                >
                    Share
                </Button>
            </Box>

            {/* Workflow ID for reference */}
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-6 block">
                Workflow ID: {workflowData.workflowId}
            </Typography>
        </Box>
    );
};

export default CompleteStep;
