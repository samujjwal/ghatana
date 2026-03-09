/**
 * Plan Generation Step Component
 *
 * Third step in the AI-powered workflow wizard.
 * Displays and allows editing of the AI-generated plan.
 *
 * Features:
 * - AI plan visualization
 * - Step reordering
 * - Step editing
 * - Approval workflow
 *
 * @doc.type component
 * @doc.purpose Plan review workflow step
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Surface as Paper, Typography, Button, IconButton, InteractiveList as List, ListItem, ListItemIcon, ListItemText, ListItemText as ListItemSecondaryAction, Chip, TextField, Dialog, DialogTitle, DialogContent, DialogActions, Stepper, Step, StepLabel, StepLabel as StepContent, Alert, LinearProgress, Collapse, Tooltip } from '@ghatana/ui';
import { GripVertical as DragIcon, Pencil as EditIcon, Trash2 as DeleteIcon, Plus as AddIcon, Check as CheckIcon, X as CloseIcon, Clock as TimeIcon, AlertTriangle as WarningIcon, Sparkles as AIIcon, RefreshCw as RegenerateIcon, ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon } from 'lucide-react';

export interface PlanStepProps {
    /** Context data from previous step */
    contextData: {
        selectedFiles: { path: string }[];
        additionalContext: string;
    };
    /** Intent data from first step */
    intentData: {
        intent: string;
    };
    /** Current plan value */
    value: PlanStepData;
    /** Callback when plan changes */
    onChange: (plan: PlanStepData) => void;
    /** Callback when step is complete */
    onComplete: (data: PlanStepData) => void;
    /** Callback to go back */
    onBack?: () => void;
    /** Whether step is loading */
    isLoading?: boolean;
    /** Error message */
    error?: string | null;
}

export interface PlanStepData {
    id: string;
    steps: PlanStep[];
    status: 'generating' | 'pending_review' | 'approved' | 'rejected' | 'modified';
    confidence: number;
    estimatedDuration: { min: number; max: number };
}

export interface PlanStep {
    id: string;
    name: string;
    description: string;
    type: string;
    estimatedMinutes: number;
    requiresReview: boolean;
    aiInstructions?: string;
}

const STEP_TYPE_COLORS: Record<string, 'default' | 'primary' | 'secondary' | 'success' | 'warning' | 'error'> = {
    INTENT_CAPTURE: 'primary',
    CONTEXT_GATHERING: 'primary',
    CODE_GENERATION: 'secondary',
    TEST_GENERATION: 'success',
    PREVIEW: 'warning',
    DEPLOYMENT: 'error',
    VERIFICATION: 'success',
};

/**
 * PlanStep Component
 */
export const PlanStep: React.FC<PlanStepProps> = ({
    contextData,
    intentData,
    value,
    onChange,
    onComplete,
    onBack,
    isLoading = false,
    error = null,
}) => {
    const [expandedSteps, setExpandedSteps] = useState<Set<string>>(new Set());
    const [editingStep, setEditingStep] = useState<PlanStep | null>(null);
    const [isRegenerating, setIsRegenerating] = useState(false);

    const toggleStepExpand = useCallback((stepId: string) => {
        setExpandedSteps((prev) => {
            const next = new Set(prev);
            if (next.has(stepId)) {
                next.delete(stepId);
            } else {
                next.add(stepId);
            }
            return next;
        });
    }, []);

    const handleEditStep = useCallback((step: PlanStep) => {
        setEditingStep(step);
    }, []);

    const handleSaveEdit = useCallback((editedStep: PlanStep) => {
        const newSteps = value.steps.map((s) =>
            s.id === editedStep.id ? editedStep : s
        );
        onChange({
            ...value,
            steps: newSteps,
            status: 'modified',
        });
        setEditingStep(null);
    }, [value, onChange]);

    const handleDeleteStep = useCallback((stepId: string) => {
        const newSteps = value.steps.filter((s) => s.id !== stepId);
        onChange({
            ...value,
            steps: newSteps,
            status: 'modified',
        });
    }, [value, onChange]);

    const handleAddStep = useCallback(() => {
        const newStep: PlanStep = {
            id: `step-${Date.now()}`,
            name: 'New Step',
            description: 'Describe what this step should accomplish',
            type: 'CUSTOM',
            estimatedMinutes: 15,
            requiresReview: true,
        };
        onChange({
            ...value,
            steps: [...value.steps, newStep],
            status: 'modified',
        });
        setEditingStep(newStep);
    }, [value, onChange]);

    const handleRegenerate = useCallback(async () => {
        setIsRegenerating(true);
        try {
            // Simulate regeneration
            await new Promise((resolve) => setTimeout(resolve, 2000));
            // In production, call the AI service
        } finally {
            setIsRegenerating(false);
        }
    }, []);

    const handleApprove = useCallback(() => {
        onChange({ ...value, status: 'approved' });
        onComplete({ ...value, status: 'approved' });
    }, [value, onChange, onComplete]);

    const totalMinutes = value.steps.reduce((sum, s) => sum + s.estimatedMinutes, 0);
    const reviewSteps = value.steps.filter((s) => s.requiresReview).length;

    return (
        <Box className="p-6">
            <Typography as="h5" gutterBottom className="flex items-center gap-2">
                <AIIcon tone="primary" />
                AI-Generated Plan
            </Typography>

            <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                Review the execution plan for: <strong>{intentData.intent}</strong>
            </Typography>

            {error && (
                <Alert severity="error" className="mb-4">
                    {error}
                </Alert>
            )}

            {(isLoading || isRegenerating) && (
                <Box className="mb-6">
                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-2">
                        <AIIcon size={16} className="mr-1 align-middle" />
                        {isRegenerating ? 'Regenerating plan...' : 'Generating plan...'}
                    </Typography>
                    <LinearProgress />
                </Box>
            )}

            {/* Plan Summary */}
            <Paper variant="outlined" className="p-4 mb-4">
                <Box className="flex flex-wrap gap-4">
                    <Box>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Steps</Typography>
                        <Typography as="h6">{value.steps.length}</Typography>
                    </Box>
                    <Box>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Est. Time</Typography>
                        <Typography as="h6">{totalMinutes} min</Typography>
                    </Box>
                    <Box>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Review Points</Typography>
                        <Typography as="h6">{reviewSteps}</Typography>
                    </Box>
                    <Box>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Confidence</Typography>
                        <Typography as="h6">{Math.round(value.confidence * 100)}%</Typography>
                    </Box>
                    <Box className="grow" />
                    <Chip
                        label={value.status.replace('_', ' ').toUpperCase()}
                        color={value.status === 'approved' ? 'success' : value.status === 'modified' ? 'warning' : 'default'}
                        size="sm"
                    />
                </Box>
            </Paper>

            {/* Plan Steps */}
            <Paper variant="outlined" className="mb-4">
                <List>
                    {value.steps.map((step, index) => (
                        <React.Fragment key={step.id}>
                            <ListItem
                                className="hover:bg-black/5"
                            >
                                <ListItemIcon className="min-w-[36px]">
                                    <Typography as="p" className="text-sm" color="text.secondary">
                                        {index + 1}
                                    </Typography>
                                </ListItemIcon>
                                <ListItemText
                                    primary={
                                        <Box className="flex items-center gap-2">
                                            {step.name}
                                            <Chip
                                                size="sm"
                                                label={step.type}
                                                color={STEP_TYPE_COLORS[step.type] || 'default'}
                                                className="h-[20px]"
                                            />
                                            {step.requiresReview && (
                                                <Tooltip title="Requires your review">
                                                    <WarningIcon size={16} tone="warning" />
                                                </Tooltip>
                                            )}
                                        </Box>
                                    }
                                    secondary={
                                        <Box className="flex items-center gap-2 mt-1">
                                            <TimeIcon size={16} />
                                            <Typography as="span" className="text-xs text-gray-500">
                                                ~{step.estimatedMinutes} min
                                            </Typography>
                                        </Box>
                                    }
                                />
                                <ListItemSecondaryAction>
                                    <IconButton
                                        size="sm"
                                        onClick={() => toggleStepExpand(step.id)}
                                    >
                                        {expandedSteps.has(step.id) ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                                    </IconButton>
                                    <IconButton
                                        size="sm"
                                        onClick={() => handleEditStep(step)}
                                    >
                                        <EditIcon size={16} />
                                    </IconButton>
                                    <IconButton
                                        size="sm"
                                        onClick={() => handleDeleteStep(step.id)}
                                    >
                                        <DeleteIcon size={16} />
                                    </IconButton>
                                </ListItemSecondaryAction>
                            </ListItem>
                            <Collapse in={expandedSteps.has(step.id)}>
                                <Box className="pl-14 pr-4 pb-4">
                                    <Typography as="p" className="text-sm" color="text.secondary">
                                        {step.description}
                                    </Typography>
                                    {step.aiInstructions && (
                                        <Alert severity="info" className="mt-2" icon={<AIIcon />}>
                                            <Typography as="span" className="text-xs text-gray-500">
                                                AI Instructions: {step.aiInstructions}
                                            </Typography>
                                        </Alert>
                                    )}
                                </Box>
                            </Collapse>
                        </React.Fragment>
                    ))}
                </List>
                <Box className="p-2 border-gray-200 dark:border-gray-700 border-t" >
                    <Button size="sm" startIcon={<AddIcon />} onClick={handleAddStep}>
                        Add Step
                    </Button>
                </Box>
            </Paper>

            {/* Actions */}
            <Box className="flex justify-between gap-4">
                {onBack && (
                    <Button onClick={onBack} disabled={isLoading || isRegenerating}>
                        Back
                    </Button>
                )}
                <Box className="grow" />
                <Button
                    startIcon={<RegenerateIcon />}
                    onClick={handleRegenerate}
                    disabled={isLoading || isRegenerating}
                >
                    Regenerate
                </Button>
                <Button
                    variant="solid"
                    tone="success"
                    onClick={handleApprove}
                    disabled={isLoading || isRegenerating || value.steps.length === 0}
                    endIcon={<CheckIcon />}
                >
                    Approve Plan
                </Button>
            </Box>

            {/* Edit Dialog */}
            <StepEditDialog
                step={editingStep}
                onSave={handleSaveEdit}
                onClose={() => setEditingStep(null)}
            />
        </Box>
    );
};

interface StepEditDialogProps {
    step: PlanStep | null;
    onSave: (step: PlanStep) => void;
    onClose: () => void;
}

const StepEditDialog: React.FC<StepEditDialogProps> = ({ step, onSave, onClose }) => {
    const [editedStep, setEditedStep] = useState<PlanStep | null>(null);

    React.useEffect(() => {
        setEditedStep(step ? { ...step } : null);
    }, [step]);

    if (!step || !editedStep) return null;

    return (
        <Dialog open={!!step} onClose={onClose} size="sm" fullWidth>
            <DialogTitle>Edit Step</DialogTitle>
            <DialogContent>
                <TextField
                    fullWidth
                    label="Name"
                    value={editedStep.name}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setEditedStep({ ...editedStep, name: e.target.value })}
                    margin="normal"
                />
                <TextField
                    fullWidth
                    label="Description"
                    value={editedStep.description}
                    onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setEditedStep({ ...editedStep, description: e.target.value })}
                    multiline
                    rows={3}
                    margin="normal"
                />
                <TextField
                    fullWidth
                    label="Estimated Minutes"
                    type="number"
                    value={editedStep.estimatedMinutes}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setEditedStep({ ...editedStep, estimatedMinutes: parseInt(e.target.value) || 0 })}
                    margin="normal"
                />
                <TextField
                    fullWidth
                    label="AI Instructions (Optional)"
                    value={editedStep.aiInstructions || ''}
                    onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setEditedStep({ ...editedStep, aiInstructions: e.target.value })}
                    multiline
                    rows={2}
                    margin="normal"
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button onClick={() => onSave(editedStep)} variant="solid" tone="primary">
                    Save
                </Button>
            </DialogActions>
        </Dialog>
    );
};

export default PlanStep;
