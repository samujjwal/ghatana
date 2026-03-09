/**
 * @doc.type component
 * @doc.purpose Requirement wireframer for Journey 21.1 (Product Designer - Requirement to Wireframe)
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback } from 'react';
import { Surface as Paper, Box, Typography, TextField, Button, IconButton, Chip, Alert, AlertTitle, InteractiveList as List, ListItem, ListItemText, ListItemIcon, Divider, Card, CardContent, CardActions, Dialog, DialogTitle, DialogContent, DialogActions, Stepper, Step, StepLabel, Tooltip, ToggleButton, ToggleButtonGroup, Stack } from '@ghatana/ui';
import { X as CloseIcon, Play as PlayIcon, Pause as PauseIcon, SkipNext as NextIcon, SkipPrevious as PrevIcon, RotateCcw as ResetIcon, Plus as AddIcon, Trash2 as DeleteIcon, Link as LinkIcon, Download as ExportIcon, Gauge as SpeedIcon, CheckCircle as CheckIcon, AlertTriangle as WarningIcon, AlertCircle as ErrorIcon, LayoutGrid as ScreenIcon, Component as ComponentIcon, Rule as RuleIcon, Activity as FlowIcon, FileText as DescriptionIcon } from 'lucide-react';
import { useRequirementWireframer, type WireframeElement, type BusinessRule, type FlowStep } from '../hooks/useRequirementWireframer';

/**
 * Component props
 */
export interface RequirementWireframerProps {
    /**
     * Initial user story (optional)
     */
    initialStory?: string;

    /**
     * Close handler
     */
    onClose?: () => void;

    /**
     * Export handler
     */
    onExport?: (data: { elements: WireframeElement[]; rules: BusinessRule[]; flow: FlowStep[] }) => void;
}

/**
 * Requirement Wireframer Component
 * 
 * Transforms user stories into visual wireframes with business rules
 * and interactive flow simulation.
 * 
 * @example
 * ```tsx
 * <RequirementWireframer
 *   initialStory="As a user, I want to filter products by price"
 *   onClose={handleClose}
 *   onExport={handleExport}
 * />
 * ```
 */
export const RequirementWireframer: React.FC<RequirementWireframerProps> = ({
    initialStory = '',
    onClose,
    onExport,
}) => {
    const [userStory, setUserStory] = useState(initialStory);
    const [activeTab, setActiveTab] = useState<'elements' | 'rules' | 'flow'>('elements');
    const [showExportDialog, setShowExportDialog] = useState(false);
    const [exportFormat, setExportFormat] = useState<'json' | 'markdown'>('json');

    const {
        parseStory,
        parsedStory,
        isValid,
        errors,
        warnings,
        elements,
        addElement,
        updateElement,
        deleteElement,
        rules,
        addRule,
        updateRule,
        deleteRule,
        linkRuleToElement,
        flow,
        addFlowStep,
        deleteFlowStep,
        reorderFlow,
        simulation,
        startSimulation,
        pauseSimulation,
        resetSimulation,
        nextStep,
        previousStep,
        setSimulationSpeed,
        generateAcceptanceCriteria,
        estimateComplexity,
        exportAsJSON,
        exportAsMarkdown,
    } = useRequirementWireframer({ autoSimulate: false, defaultSpeed: 2000 });

    // Handle parse
    const handleParse = useCallback(() => {
        parseStory(userStory);
    }, [userStory, parseStory]);

    // Handle export
    const handleExport = useCallback(() => {
        const data = {
            elements,
            rules,
            flow,
        };

        if (onExport) {
            onExport(data);
        }

        setShowExportDialog(true);
    }, [elements, rules, flow, onExport]);

    // Get export content
    const getExportContent = useCallback(() => {
        return exportFormat === 'json' ? exportAsJSON() : exportAsMarkdown();
    }, [exportFormat, exportAsJSON, exportAsMarkdown]);

    // Copy to clipboard
    const handleCopyExport = useCallback(() => {
        navigator.clipboard.writeText(getExportContent());
    }, [getExportContent]);

    // Download export
    const handleDownloadExport = useCallback(() => {
        const content = getExportContent();
        const extension = exportFormat === 'json' ? 'json' : 'md';
        const blob = new Blob([content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `wireframe-${Date.now()}.${extension}`;
        a.click();
        URL.revokeObjectURL(url);
    }, [getExportContent, exportFormat]);

    // Complexity estimation
    const complexity = estimateComplexity();

    return (
        <Paper
            className="w-full h-full flex flex-col overflow-hidden"
        >
            {/* Header */}
            <Box className="p-4 flex justify-between items-center border-gray-200 dark:border-gray-700 border-b" >
                <Box className="flex gap-4 items-center">
                    <Typography as="h6">Requirement Wireframer</Typography>
                    {parsedStory && (
                        <Chip
                            label={`Complexity: ${complexity.level}`}
                            color={
                                complexity.level === 'low'
                                    ? 'success'
                                    : complexity.level === 'medium'
                                        ? 'info'
                                        : complexity.level === 'high'
                                            ? 'warning'
                                            : 'error'
                            }
                            size="sm"
                        />
                    )}
                </Box>

                <Box className="flex gap-2">
                    <Button startIcon={<ExportIcon />} onClick={handleExport} variant="outlined" size="sm" disabled={!parsedStory}>
                        Export
                    </Button>
                    {onClose && (
                        <IconButton onClick={onClose} size="sm">
                            <CloseIcon />
                        </IconButton>
                    )}
                </Box>
            </Box>

            {/* Main Content */}
            <Box className="flex-1 flex overflow-hidden">
                {/* Left Panel: Input & Validation */}
                <Box className="flex flex-col w-[400px] border-r border-gray-200 dark:border-gray-700">
                    <Box className="p-4 flex-1 overflow-auto">
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            User Story
                        </Typography>
                        <TextField
                            fullWidth
                            multiline
                            rows={8}
                            value={userStory}
                            onChange={e => setUserStory(e.target.value)}
                            placeholder={'As a [actor], I want to [goal], so that [benefit]\n\nExample:\nAs a user, I want to filter products by price, so that I can find affordable items.'}
                            className="mb-4"
                        />

                        <Button fullWidth variant="solid" startIcon={<PlayIcon />} onClick={handleParse} disabled={!userStory.trim()}>
                            Parse Story
                        </Button>

                        {/* Validation Messages */}
                        {errors.length > 0 && (
                            <Alert severity="error" className="mt-4">
                                <AlertTitle>Errors</AlertTitle>
                                {errors.map((error, index) => (
                                    <Typography key={index} as="p" className="text-sm">
                                        • {error}
                                    </Typography>
                                ))}
                            </Alert>
                        )}

                        {warnings.length > 0 && (
                            <Alert severity="warning" className="mt-4">
                                <AlertTitle>Warnings</AlertTitle>
                                {warnings.map((warning, index) => (
                                    <Typography key={index} as="p" className="text-sm">
                                        • {warning}
                                    </Typography>
                                ))}
                            </Alert>
                        )}

                        {/* Parsed Story Info */}
                        {parsedStory && (
                            <Box className="mt-4">
                                <Card variant="outlined">
                                    <CardContent>
                                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                                            Parsed Information
                                        </Typography>
                                        <Typography as="p" className="text-sm" color="text.secondary">
                                            <strong>Actor:</strong> {parsedStory.actor}
                                        </Typography>
                                        <Typography as="p" className="text-sm" color="text.secondary">
                                            <strong>Goal:</strong> {parsedStory.goal}
                                        </Typography>
                                        <Typography as="p" className="text-sm" color="text.secondary" className="mt-2">
                                            <strong>Elements:</strong> {elements.length}
                                        </Typography>
                                        <Typography as="p" className="text-sm" color="text.secondary">
                                            <strong>Rules:</strong> {rules.length}
                                        </Typography>
                                        <Typography as="p" className="text-sm" color="text.secondary">
                                            <strong>Flow Steps:</strong> {flow.length}
                                        </Typography>
                                    </CardContent>
                                </Card>

                                {/* Acceptance Criteria */}
                                <Card variant="outlined" className="mt-4">
                                    <CardContent>
                                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                                            Acceptance Criteria
                                        </Typography>
                                        <List dense>
                                            {generateAcceptanceCriteria().map((criterion, index) => (
                                                <ListItem key={index} disablePadding>
                                                    <ListItemIcon className="min-w-[32px]">
                                                        <CheckIcon size={16} tone="success" />
                                                    </ListItemIcon>
                                                    <ListItemText primary={criterion} primaryTypographyProps={{ variant: 'body2' }} />
                                                </ListItem>
                                            ))}
                                        </List>
                                    </CardContent>
                                </Card>
                            </Box>
                        )}
                    </Box>
                </Box>

                {/* Right Panel: Wireframe Details */}
                <Box className="flex-1 flex flex-col overflow-hidden">
                    {parsedStory ? (
                        <>
                            {/* Tab Navigation */}
                            <Box className="flex gap-0 border-gray-200 dark:border-gray-700 border-b" >
                                <Button
                                    startIcon={<ScreenIcon />}
                                    onClick={() => setActiveTab('elements')}
                                    className="rounded-none border-blue-600 px-6" style={{ borderBottom: activeTab === 'elements' ? 2 : 0, gridTemplateColumns: 'repeat(auto-fill }}
                                    color={activeTab === 'elements' ? 'primary' : 'inherit'}
                                >
                                    Elements ({elements.length})
                                </Button>
                                <Button
                                    startIcon={<RuleIcon />}
                                    onClick={() => setActiveTab('rules')}
                                    className="rounded-none border-blue-600 px-6" style={{ borderBottom: activeTab === 'rules' ? 2 : 0, alignItems: 'start' }}
                                    color={activeTab === 'rules' ? 'primary' : 'inherit'}
                                >
                                    Rules ({rules.length})
                                </Button>
                                <Button
                                    startIcon={<FlowIcon />}
                                    onClick={() => setActiveTab('flow')}
                                    className="rounded-none border-blue-600 px-6" style={{ borderBottom: activeTab === 'flow' ? 2 : 0 }}
                                    color={activeTab === 'flow' ? 'primary' : 'inherit'}
                                >
                                    Flow ({flow.length})
                                </Button>
                            </Box>

                            {/* Tab Content */}
                            <Box className="flex-1 overflow-auto p-4">
                                {/* Elements Tab */}
                                {activeTab === 'elements' && (
                                    <Box cl'repeat(auto-fill */>
                                        {elements.map(element => (
                                            <Card variant="outlined" key={element.id}>
                                                <CardContent>
                                                    <Box className="flex justify-between mb-2" >
                                                        <Chip
                                                            label={element.type}
                                                            size="sm"
                                                            icon={element.type === 'screen' ? <ScreenIcon /> : <ComponentIcon />}
                                                            color={element.type === 'screen' ? 'primary' : 'default'}
                                                        />
                                                        <IconButton size="sm" onClick={() => deleteElement(element.id)} aria-label="delete">
                                                            <DeleteIcon size={16} />
                                                        </IconButton>
                                                    </Box>
                                                    <Typography as="p" className="text-sm font-medium" gutterBottom>
                                                        {element.label}
                                                    </Typography>
                                                    {element.description && (
                                                        <Typography as="p" className="text-sm" color="text.secondary">
                                                            {element.description}
                                                        </Typography>
                                                    )}
                                                </CardContent>
                                            </Card>
                                        ))}

                                        {elements.length === 0 && (
                                            <Box style={{ gridColumn: '1 / -1' }}>
                                                <Alert severity="info">
                                                    No UI elements yet. Parse a user story to generate wireframe elements.
                                                </Alert>
                                            </Box>
                                        )}
                                    </Box>
                                )}

                                {/* Rules Tab */}
                                {activeTab === 'rules' && (
                                    <List>
                                        {rules.map((rule, index) => (
                                            <React.Fragment key={rule.id}>
                                                <ListItem>
                                                    <Box className="flex-1">
                                                        <Box className="flex justify-between mb-2" style={{ alignItems: 'start' }} >
                                                            <Typography as="p" className="text-sm font-medium">Rule {index + 1}</Typography>
                                                            <IconButton size="sm" onClick={() => deleteRule(rule.id)}>
                                                                <DeleteIcon size={16} />
                                                            </IconButton>
                                                        </Box>
                                                        <Typography as="p" className="text-sm" gutterBottom>
                                                            {rule.description}
                                                        </Typography>
                                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block">
                                                            <strong>Condition:</strong> {rule.condition}
                                                        </Typography>
                                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block">
                                                            <strong>Action:</strong> {rule.action}
                                                        </Typography>
                                                        {rule.appliesTo.length > 0 && (
                                                            <Box className="mt-2 flex flex-wrap gap-1">
                                                                {rule.appliesTo.map(elId => {
                                                                    const element = elements.find(el => el.id === elId);
                                                                    return element ? (
                                                                        <Chip key={elId} label={element.label} size="sm" variant="outlined" />
                                                                    ) : null;
                                                                })}
                                                            </Box>
                                                        )}
                                                    </Box>
                                                </ListItem>
                                                {index < rules.length - 1 && <Divider />}
                                            </React.Fragment>
                                        ))}

                                        {rules.length === 0 && (
                                            <Alert severity="info">
                                                No business rules found. Add conditional statements to your user story to generate rules.
                                            </Alert>
                                        )}
                                    </List>
                                )}

                                {/* Flow Tab */}
                                {activeTab === 'flow' && (
                                    <Box>
                                        {/* Flow Simulation Controls */}
                                        <Card variant="outlined" className="mb-4">
                                            <CardContent>
                                                <Box className="flex justify-between items-center mb-4">
                                                    <Typography as="p" className="text-sm font-medium">Flow Simulation</Typography>
                                                    <Box className="flex gap-2">
                                                        <Tooltip title="Previous Step">
                                                            <IconButton size="sm" onClick={previousStep} disabled={simulation.currentStep === 0}>
                                                                <PrevIcon />
                                                            </IconButton>
                                                        </Tooltip>
                                                        {simulation.isPlaying ? (
                                                            <Tooltip title="Pause">
                                                                <IconButton size="sm" onClick={pauseSimulation}>
                                                                    <PauseIcon />
                                                                </IconButton>
                                                            </Tooltip>
                                                        ) : (
                                                            <Tooltip title="Play">
                                                                <IconButton size="sm" onClick={startSimulation}>
                                                                    <PlayIcon />
                                                                </IconButton>
                                                            </Tooltip>
                                                        )}
                                                        <Tooltip title="Next Step">
                                                            <IconButton size="sm" onClick={nextStep} disabled={simulation.currentStep >= flow.length - 1}>
                                                                <NextIcon />
                                                            </IconButton>
                                                        </Tooltip>
                                                        <Tooltip title="Reset">
                                                            <IconButton size="sm" onClick={resetSimulation}>
                                                                <ResetIcon />
                                                            </IconButton>
                                                        </Tooltip>
                                                    </Box>
                                                </Box>

                                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block" className="mb-2">
                                                    Step {simulation.currentStep + 1} of {flow.length}
                                                </Typography>

                                                <Stepper activeStep={simulation.currentStep} orientation="vertical">
                                                    {flow.map((step, index) => {
                                                        const element = elements.find(el => el.id === step.elementId);
                                                        return (
                                                            <Step key={step.id} completed={simulation.completed.has(step.elementId)}>
                                                                <StepLabel>
                                                                    <Typography as="p" className="text-sm">{step.description}</Typography>
                                                                    {element && (
                                                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                                            {element.label} ({element.type})
                                                                        </Typography>
                                                                    )}
                                                                </StepLabel>
                                                            </Step>
                                                        );
                                                    })}
                                                </Stepper>
                                            </CardContent>
                                        </Card>
                                    </Box>
                                )}
                            </Box>
                        </>
                    ) : (
                        <Box className="flex-1 flex items-center justify-center p-8">
                            <Alert severity="info" icon={<DescriptionIcon />}>
                                <AlertTitle>No Story Parsed</AlertTitle>
                                Enter a user story in the left panel and click "Parse Story" to begin.
                            </Alert>
                        </Box>
                    )}
                </Box>
            </Box>

            {/* Export Dialog */}
            <Dialog open={showExportDialog} onClose={() => setShowExportDialog(false)} size="md" fullWidth>
                <DialogTitle>Export Wireframe</DialogTitle>
                <DialogContent>
                    <Box className="mb-4">
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            Format
                        </Typography>
                        <ToggleButtonGroup
                            value={exportFormat}
                            exclusive
                            onChange={(e, value) => value && setExportFormat(value)}
                            size="sm"
                        >
                            <ToggleButton value="json">JSON</ToggleButton>
                            <ToggleButton value="markdown">Markdown</ToggleButton>
                        </ToggleButtonGroup>
                    </Box>

                    <TextField
                        fullWidth
                        multiline
                        rows={15}
                        value={getExportContent()}
                        InputProps={{
                            readOnly: true,
                            sx: { fontFamily: 'monospace', fontSize: 12 },
                        }}
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleCopyExport}>Copy to Clipboard</Button>
                    <Button onClick={handleDownloadExport} variant="solid">
                        Download
                    </Button>
                    <Button onClick={() => setShowExportDialog(false)}>Close</Button>
                </DialogActions>
            </Dialog>
        </Paper>
    );
};
