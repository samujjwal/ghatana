/**
 * Preview Step Component
 *
 * Sixth step in the AI-powered workflow wizard.
 * Provides live preview of changes before deployment.
 *
 * @doc.type component
 * @doc.purpose Preview workflow step
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useEffect } from 'react';
import { Box, Surface as Paper, Typography, Button, LinearProgress, Alert, IconButton, Chip, Tooltip } from '@ghatana/ui';
import { Eye as PreviewIcon, DesktopWindows as DesktopIcon, TabletMac as TabletIcon, Smartphone as PhoneIcon, RefreshCw as RefreshIcon, ExternalLink as OpenInNewIcon, Check as CheckIcon, Sparkles as AIIcon } from 'lucide-react';

export interface PreviewStepProps {
    codeData: { files: { path: string }[] };
    value: PreviewStepData;
    onChange: (data: PreviewStepData) => void;
    onComplete: (data: PreviewStepData) => void;
    onBack?: () => void;
    isLoading?: boolean;
    error?: string | null;
}

export interface PreviewStepData {
    previewUrl: string | null;
    status: 'building' | 'ready' | 'error';
    buildLog: string[];
    viewport: 'desktop' | 'tablet' | 'mobile';
    aiSummary?: string;
}

const VIEWPORT_SIZES = {
    desktop: { width: '100%', height: '600px' },
    tablet: { width: '768px', height: '600px' },
    mobile: { width: '375px', height: '667px' },
};

export const PreviewStep: React.FC<PreviewStepProps> = ({
    codeData,
    value,
    onChange,
    onComplete,
    onBack,
    isLoading = false,
    error = null,
}) => {
    const [isBuilding, setIsBuilding] = useState(false);

    useEffect(() => {
        if (value.status === 'building' && !value.previewUrl) {
            buildPreview();
        }
    }, []);

    const buildPreview = useCallback(async () => {
        setIsBuilding(true);
        onChange({ ...value, status: 'building', buildLog: [] });

        try {
            const logs: string[] = [];
            const addLog = (msg: string) => {
                logs.push(msg);
                onChange({ ...value, buildLog: [...logs], status: 'building' });
            };

            addLog('Installing dependencies...');
            await new Promise((r) => setTimeout(r, 500));

            addLog('Compiling TypeScript...');
            await new Promise((r) => setTimeout(r, 700));

            addLog('Bundling assets...');
            await new Promise((r) => setTimeout(r, 600));

            addLog('Starting preview server...');
            await new Promise((r) => setTimeout(r, 500));

            addLog('✓ Preview ready!');

            onChange({
                ...value,
                status: 'ready',
                previewUrl: 'http://localhost:3000',
                buildLog: logs,
                aiSummary: 'Your changes include a new Login component and authentication hook. The preview shows the login form with email/password inputs.',
            });
        } catch (err) {
            onChange({
                ...value,
                status: 'error',
                buildLog: [...value.buildLog, `Error: ${err}`],
            });
        } finally {
            setIsBuilding(false);
        }
    }, [value, onChange]);

    const handleViewportChange = useCallback((viewport: 'desktop' | 'tablet' | 'mobile') => {
        onChange({ ...value, viewport });
    }, [value, onChange]);

    return (
        <Box className="p-6">
            <Typography as="h5" gutterBottom className="flex items-center gap-2">
                <PreviewIcon tone="primary" />
                Preview Changes
            </Typography>

            <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                Review your changes in a live preview before deploying.
            </Typography>

            {error && <Alert severity="error" className="mb-4">{error}</Alert>}

            {isBuilding && (
                <Box className="mb-6">
                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-2">
                        Building preview...
                    </Typography>
                    <LinearProgress />
                    <Paper variant="outlined" className="mt-4 p-4 overflow-auto max-h-[150px]">
                        {value.buildLog.map((log, i) => (
                            <Typography
                                key={i}
                                as="span" className="text-xs text-gray-500"
                                component="div"
                                className="font-mono"
                            >
                                {log}
                            </Typography>
                        ))}
                    </Paper>
                </Box>
            )}

            {value.status === 'ready' && (
                <>
                    {value.aiSummary && (
                        <Alert severity="info" icon={<AIIcon />} className="mb-4">
                            {value.aiSummary}
                        </Alert>
                    )}

                    {/* Viewport controls */}
                    <Paper variant="outlined" className="mb-4">
                        <Box className="p-2 flex items-center gap-2 border-gray-200 dark:border-gray-700 border-b" >
                            <Tooltip title="Desktop">
                                <IconButton
                                    color={value.viewport === 'desktop' ? 'primary' : 'default'}
                                    onClick={() => handleViewportChange('desktop')}
                                >
                                    <DesktopIcon />
                                </IconButton>
                            </Tooltip>
                            <Tooltip title="Tablet">
                                <IconButton
                                    color={value.viewport === 'tablet' ? 'primary' : 'default'}
                                    onClick={() => handleViewportChange('tablet')}
                                >
                                    <TabletIcon />
                                </IconButton>
                            </Tooltip>
                            <Tooltip title="Mobile">
                                <IconButton
                                    color={value.viewport === 'mobile' ? 'primary' : 'default'}
                                    onClick={() => handleViewportChange('mobile')}
                                >
                                    <PhoneIcon />
                                </IconButton>
                            </Tooltip>
                            <Box className="grow" />
                            <Chip
                                size="sm"
                                label={value.previewUrl}
                                className="font-mono"
                            />
                            <Tooltip title="Refresh">
                                <IconButton onClick={buildPreview}>
                                    <RefreshIcon />
                                </IconButton>
                            </Tooltip>
                            <Tooltip title="Open in new tab">
                                <IconButton onClick={() => window.open(value.previewUrl!, '_blank')}>
                                    <OpenInNewIcon />
                                </IconButton>
                            </Tooltip>
                        </Box>

                        {/* Preview iframe */}
                        <Box
                            className="flex justify-center p-4 bg-gray-100" >
                            <Box
                                className="max-w-full rounded overflow-hidden flex items-center justify-center bg-white border border-gray-300" style={{ width: VIEWPORT_SIZES[value.viewport].width, height: VIEWPORT_SIZES[value.viewport].height }} >
                                {/* In production, this would be an actual iframe */}
                                <Typography as="p" className="text-sm" color="text.secondary">
                                    Preview would render here
                                    <br />
                                    <small>{value.viewport} view</small>
                                </Typography>
                            </Box>
                        </Box>
                    </Paper>
                </>
            )}

            {value.status === 'error' && (
                <Alert severity="error" className="mb-4">
                    Build failed. Check the logs above for details.
                    <Button size="sm" onClick={buildPreview} className="ml-4">
                        Retry
                    </Button>
                </Alert>
            )}

            <Box className="flex justify-between gap-4">
                {onBack && <Button onClick={onBack} disabled={isLoading || isBuilding}>Back</Button>}
                <Box className="grow" />
                <Button
                    variant="solid"
                    onClick={() => onComplete(value)}
                    disabled={isLoading || isBuilding || value.status !== 'ready'}
                    endIcon={<CheckIcon />}
                >
                    Approve & Continue
                </Button>
            </Box>
        </Box>
    );
};

export default PreviewStep;
