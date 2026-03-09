/**
 * Deploy Step Component
 *
 * Seventh step in the AI-powered workflow wizard.
 * Handles deployment to selected environment.
 *
 * @doc.type component
 * @doc.purpose Deploy workflow step
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Surface as Paper, Typography, Button, LinearProgress, Alert, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Radio, RadioGroup, FormControlLabel, FormControl, FormLabel, Chip, Collapse, IconButton, Divider } from '@ghatana/ui';
import { CloudUpload as DeployIcon, CheckCircle as SuccessIcon, AlertCircle as ErrorIcon, Clock as PendingIcon, ChevronDown as ExpandIcon, ChevronUp as CollapseIcon, Gauge as SpeedIcon, Shield as SecurityIcon, Settings as ConfigIcon } from 'lucide-react';

export interface DeployStepProps {
    previewData: { previewUrl: string | null };
    value: DeployStepData;
    onChange: (data: DeployStepData) => void;
    onComplete: (data: DeployStepData) => void;
    onBack?: () => void;
    isLoading?: boolean;
    error?: string | null;
}

export interface DeployStepData {
    environment: 'development' | 'staging' | 'production';
    status: 'pending' | 'deploying' | 'success' | 'failed';
    steps: DeploymentStep[];
    deploymentUrl?: string;
    startTime?: Date;
    endTime?: Date;
}

interface DeploymentStep {
    id: string;
    name: string;
    status: 'pending' | 'running' | 'success' | 'failed' | 'skipped';
    duration?: number;
    message?: string;
}

const ENVIRONMENTS = [
    { value: 'development', label: 'Development', description: 'Local development environment' },
    { value: 'staging', label: 'Staging', description: 'Pre-production testing environment' },
    { value: 'production', label: 'Production', description: 'Live production environment', warning: true },
];

const INITIAL_STEPS: DeploymentStep[] = [
    { id: 'lint', name: 'Linting & Code Quality', status: 'pending' },
    { id: 'test', name: 'Running Tests', status: 'pending' },
    { id: 'build', name: 'Building Application', status: 'pending' },
    { id: 'security', name: 'Security Scan', status: 'pending' },
    { id: 'deploy', name: 'Deploying to Environment', status: 'pending' },
    { id: 'verify', name: 'Health Check', status: 'pending' },
];

export const DeployStep: React.FC<DeployStepProps> = ({
    previewData,
    value,
    onChange,
    onComplete,
    onBack,
    isLoading = false,
    error = null,
}) => {
    const [showLogs, setShowLogs] = useState(false);

    const handleEnvironmentChange = useCallback((env: 'development' | 'staging' | 'production') => {
        onChange({ ...value, environment: env });
    }, [value, onChange]);

    const startDeployment = useCallback(async () => {
        const steps = INITIAL_STEPS.map(s => ({ ...s }));
        onChange({ ...value, status: 'deploying', steps, startTime: new Date() });

        for (let i = 0; i < steps.length; i++) {
            steps[i].status = 'running';
            onChange({ ...value, status: 'deploying', steps: [...steps] });

            await new Promise((r) => setTimeout(r, 500 + Math.random() * 500));

            const success = Math.random() > 0.1; // 90% success rate
            steps[i].status = success ? 'success' : 'failed';
            steps[i].duration = Math.floor(Math.random() * 5000) + 1000;

            if (!success) {
                steps[i].message = 'Unexpected error occurred';
                for (let j = i + 1; j < steps.length; j++) {
                    steps[j].status = 'skipped';
                }
                onChange({ ...value, status: 'failed', steps: [...steps], endTime: new Date() });
                return;
            }

            onChange({ ...value, status: 'deploying', steps: [...steps] });
        }

        onChange({
            ...value,
            status: 'success',
            steps,
            endTime: new Date(),
            deploymentUrl: `https://${value.environment}.example.com`,
        });
    }, [value, onChange]);

    const getStatusIcon = (status: DeploymentStep['status']) => {
        switch (status) {
            case 'success': return <SuccessIcon tone="success" />;
            case 'failed': return <ErrorIcon tone="danger" />;
            case 'running': return <SpeedIcon tone="primary" className="spin" />;
            case 'skipped': return <PendingIcon color="disabled" />;
            default: return <PendingIcon color="action" />;
        }
    };

    const progress = value.steps.filter(s => s.status === 'success').length / value.steps.length * 100;

    return (
        <Box className="p-6">
            <Typography as="h5" gutterBottom className="flex items-center gap-2">
                <DeployIcon tone="primary" />
                Deploy Changes
            </Typography>

            <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                Deploy your changes to the selected environment.
            </Typography>

            {error && <Alert severity="error" className="mb-4">{error}</Alert>}

            {/* Environment Selection */}
            {value.status === 'pending' && (
                <Paper variant="outlined" className="p-6 mb-6">
                    <FormControl component="fieldset">
                        <FormLabel component="legend" className="mb-4">
                            Select Target Environment
                        </FormLabel>
                        <RadioGroup
                            value={value.environment}
                            onChange={(e) => handleEnvironmentChange(e.target.value as unknown)}
                        >
                            {ENVIRONMENTS.map((env) => (
                                <Paper
                                    key={env.value}
                                    variant="outlined"
                                    className="p-4 mb-2 cursor-pointer" style={{ border: value.environment === env.value ? 2 : 1, borderColor: value.environment === env.value ? 'primary.main' : 'divider' }}
                                    onClick={() => handleEnvironmentChange(env.value as unknown)}
                                >
                                    <FormControlLabel
                                        value={env.value}
                                        control={<Radio />}
                                        label={
                                            <Box>
                                                <Typography as="p" className="text-lg font-medium">
                                                    {env.label}
                                                    {env.warning && (
                                                        <Chip
                                                            size="sm"
                                                            label="Requires Approval"
                                                            tone="warning"
                                                            className="ml-2"
                                                        />
                                                    )}
                                                </Typography>
                                                <Typography as="p" className="text-sm" color="text.secondary">
                                                    {env.description}
                                                </Typography>
                                            </Box>
                                        }
                                        className="w-full m-0"
                                    />
                                </Paper>
                            ))}
                        </RadioGroup>
                    </FormControl>
                </Paper>
            )}

            {/* Deployment Progress */}
            {(value.status === 'deploying' || value.status === 'success' || value.status === 'failed') && (
                <Paper variant="outlined" className="mb-6">
                    <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                        <Box className="flex items-center gap-2">
                            <Typography as="p" className="text-lg font-medium">
                                Deploying to {value.environment}
                            </Typography>
                            <Chip
                                size="sm"
                                label={value.status}
                                color={value.status === 'success' ? 'success' : value.status === 'failed' ? 'error' : 'primary'}
                            />
                        </Box>
                        {value.status === 'deploying' && (
                            <LinearProgress variant="determinate" value={progress} className="mt-2" />
                        )}
                    </Box>

                    <List dense>
                        {value.steps.map((step) => (
                            <ListItem key={step.id}>
                                <ListItemIcon>
                                    {getStatusIcon(step.status)}
                                </ListItemIcon>
                                <ListItemText
                                    primary={step.name}
                                    secondary={step.duration ? `${(step.duration / 1000).toFixed(1)}s` : step.message}
                                />
                            </ListItem>
                        ))}
                    </List>

                    {value.deploymentUrl && (
                        <Box className="p-4 border-gray-200 dark:border-gray-700 border-t" >
                            <Alert severity="success">
                                Deployed successfully to{' '}
                                <a href={value.deploymentUrl} target="_blank" rel="noopener noreferrer">
                                    {value.deploymentUrl}
                                </a>
                            </Alert>
                        </Box>
                    )}
                </Paper>
            )}

            {value.status === 'failed' && (
                <Alert severity="error" className="mb-4">
                    Deployment failed. Please check the logs and try again.
                    <Button size="sm" onClick={startDeployment} className="ml-4">
                        Retry
                    </Button>
                </Alert>
            )}

            <Box className="flex justify-between gap-4">
                {onBack && <Button onClick={onBack} disabled={isLoading || value.status === 'deploying'}>Back</Button>}
                <Box className="grow" />
                {value.status === 'pending' && (
                    <Button
                        variant="solid"
                        onClick={startDeployment}
                        disabled={isLoading}
                        startIcon={<DeployIcon />}
                    >
                        Deploy to {value.environment}
                    </Button>
                )}
                {value.status === 'success' && (
                    <Button
                        variant="solid"
                        tone="success"
                        onClick={() => onComplete(value)}
                        endIcon={<SuccessIcon />}
                    >
                        Continue
                    </Button>
                )}
            </Box>
        </Box>
    );
};

export default DeployStep;
