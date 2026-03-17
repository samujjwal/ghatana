/**
 * PlanStep - UI for the Plan step of the workflow
 *
 * @doc.type component
 * @doc.purpose Plan step workspace
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React from 'react';
import { Box, Card, CardContent, Typography, TextField, IconButton, Button, InteractiveList as List, ListItem, ListItemIcon, ListItemText, ListItemText as ListItemSecondaryAction, Divider, FormControl, InputLabel, Select, MenuItem, Accordion, AccordionSummary, AccordionDetails, Chip, Alert } from '@ghatana/ui';
import { Plus as AddIcon, Trash2 as DeleteIcon, GripVertical as DragIcon, ChevronDown as ExpandMoreIcon, AlertTriangle as WarningIcon } from 'lucide-react';
import { useAtomValue, useSetAtom } from 'jotai';

import {
    currentWorkflowAtom,
    draftStepDataAtom,
    updateDraftStepDataAtom,
} from '../../../stores/workflow.store';

import type { PlanStepData, RiskAssessment } from '@ghatana/yappc-types';

// ============================================================================
// CONSTANTS
// ============================================================================

const RISK_LEVELS: { value: RiskAssessment['level']; label: string; color: 'success' | 'info' | 'warning' | 'error' }[] = [
    { value: 'LOW', label: 'Low', color: 'success' },
    { value: 'MEDIUM', label: 'Medium', color: 'info' },
    { value: 'HIGH', label: 'High', color: 'warning' },
    { value: 'CRITICAL', label: 'Critical', color: 'error' },
];

// ============================================================================
// COMPONENT
// ============================================================================

export function PlanStep() {
    const workflow = useAtomValue(currentWorkflowAtom);
    const draftData = useAtomValue(draftStepDataAtom) as PlanStepData | null;
    const updateDraft = useSetAtom(updateDraftStepDataAtom);

    const [newTaskTitle, setNewTaskTitle] = React.useState('');
    const [newTaskDesc, setNewTaskDesc] = React.useState('');
    const [newRiskFactor, setNewRiskFactor] = React.useState('');
    const [newMitigation, setNewMitigation] = React.useState('');

    // Get current data (draft or saved)
    const baseData = draftData ?? workflow?.steps.plan.data;
    const currentData: PlanStepData = {
        tasks: baseData?.tasks ?? [],
        riskAssessment: {
            level: baseData?.riskAssessment?.level ?? 'MEDIUM',
            factors: baseData?.riskAssessment?.factors ?? [],
            mitigations: baseData?.riskAssessment?.mitigations ?? [],
            hasRollbackPlan: baseData?.riskAssessment?.hasRollbackPlan ?? false,
        },
    };

    const handleChange = (field: keyof PlanStepData, value: unknown) => {
        updateDraft({ ...currentData, [field]: value });
    };

    const riskAssessment = currentData.riskAssessment || {
        level: 'MEDIUM',
        factors: [],
        mitigations: [],
        hasRollbackPlan: false,
    };

    const handleRiskChange = (field: keyof RiskAssessment, value: unknown) => {
        handleChange('riskAssessment', { ...riskAssessment, [field]: value });
    };

    // Plan items
    const handleAddTask = () => {
        if (newTaskTitle.trim()) {
            const newTask = {
                id: `task-${Date.now()}`,
                title: newTaskTitle.trim(),
                status: 'TODO' as const,
                assignee: undefined,
            };
            handleChange('tasks', [...(currentData.tasks ?? []), newTask]);
            setNewTaskTitle('');
            setNewTaskDesc('');
        }
    };

    const handleRemoveTask = (id: string) => {
        handleChange(
            'tasks',
            (currentData.tasks ?? []).filter((t) => t.id !== id)
        );
    };

    // Risk factors
    const handleAddRiskFactor = () => {
        if (newRiskFactor.trim()) {
            handleRiskChange('factors', [...riskAssessment.factors, newRiskFactor.trim()]);
            setNewRiskFactor('');
        }
    };

    const handleRemoveRiskFactor = (index: number) => {
        handleRiskChange(
            'factors',
            riskAssessment.factors.filter((_: string, i: number) => i !== index)
        );
    };

    // Mitigations
    const handleAddMitigation = () => {
        if (newMitigation.trim()) {
            handleRiskChange('mitigations', [...riskAssessment.mitigations, newMitigation.trim()]);
            setNewMitigation('');
        }
    };

    const handleRemoveMitigation = (index: number) => {
        handleRiskChange(
            'mitigations',
            riskAssessment.mitigations.filter((_: string, i: number) => i !== index)
        );
    };

    return (
        <Box className="max-w-[800px] mx-auto">

            {/* Execution Plan */}
            <Card className="mb-6">
                <CardContent>
                    <Typography as="p" className="text-lg font-medium" gutterBottom fontWeight={600}>
                        Execution Plan
                    </Typography>
                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                        Break down the work into ordered tasks.
                    </Typography>

                    <Box className="flex gap-2 mb-4 flex-col">
                        <Box className="flex gap-2">
                            <TextField
                                fullWidth
                                size="sm"
                                placeholder="Task title"
                                value={newTaskTitle}
                                onChange={(e) => setNewTaskTitle(e.target.value)}
                            />
                            <Button
                                variant="outlined"
                                onClick={handleAddTask}
                                disabled={!newTaskTitle.trim()}
                                startIcon={<AddIcon />}
                            >
                                Add
                            </Button>
                        </Box>
                        <TextField
                            fullWidth
                            size="sm"
                            placeholder="Task description (optional)"
                            value={newTaskDesc}
                            onChange={(e) => setNewTaskDesc(e.target.value)}
                        />
                    </Box>

                    {(currentData.tasks ?? []).length === 0 ? (
                        <Alert severity="info">No tasks defined yet. Add tasks to build your execution plan.</Alert>
                    ) : (
                        <List>
                            {(currentData.tasks ?? []).map((task, index) => (
                                <React.Fragment key={task.id}>
                                    <ListItem className="px-0">
                                        <ListItemIcon className="min-w-[32px]">
                                            <DragIcon color="action" className="cursor-grab" />
                                        </ListItemIcon>
                                        <Chip
                                            label={index + 1}
                                            size="sm"
                                            tone="primary"
                                            className="mr-4"
                                        />
                                        <ListItemText
                                            primary={task.title}
                                            secondary={task.status}
                                        />
                                        <ListItemSecondaryAction>
                                            <IconButton
                                                edge="end"
                                                size="sm"
                                                onClick={() => handleRemoveTask(task.id)}
                                            >
                                                <DeleteIcon size={16} />
                                            </IconButton>
                                        </ListItemSecondaryAction>
                                    </ListItem>
                                    {index < (currentData.tasks ?? []).length - 1 && <Divider />}
                                </React.Fragment>
                            ))}
                        </List>
                    )}
                </CardContent>
            </Card>

            {/* Risk Assessment */}
            <Card>
                <CardContent>
                    <Box className="flex items-center gap-2 mb-4">
                        <WarningIcon tone="warning" />
                        <Typography as="p" className="text-lg font-medium" fontWeight={600}>
                            Risk Assessment
                        </Typography>
                    </Box>

                    {/* Risk Level */}
                    <FormControl fullWidth size="sm" className="mb-6">
                        <InputLabel>Risk Level</InputLabel>
                        <Select
                            value={riskAssessment.level}
                            label="Risk Level"
                            onChange={(e) => handleRiskChange('level', e.target.value)}
                        >
                            {RISK_LEVELS.map((level) => (
                                <MenuItem key={level.value} value={level.value}>
                                    <Box className="flex items-center gap-2">
                                        <Chip
                                            label={level.label}
                                            size="sm"
                                            color={level.color}
                                        />
                                    </Box>
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>

                    {/* Risk Factors */}
                    <Accordion defaultExpanded>
                        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Typography as="p" className="text-sm" fontWeight={500}>
                                Risk Factors ({riskAssessment.factors.length})
                            </Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <Box className="flex gap-2 mb-4">
                                <TextField
                                    fullWidth
                                    size="sm"
                                    placeholder="Add a risk factor..."
                                    value={newRiskFactor}
                                    onChange={(e) => setNewRiskFactor(e.target.value)}
                                    onKeyPress={(e) => e.key === 'Enter' && handleAddRiskFactor()}
                                />
                                <Button
                                    variant="outlined"
                                    size="sm"
                                    onClick={handleAddRiskFactor}
                                    disabled={!newRiskFactor.trim()}
                                >
                                    Add
                                </Button>
                            </Box>
                            <Box className="flex flex-wrap gap-2">
                                {riskAssessment.factors.map((factor: string, index: number) => (
                                    <Chip
                                        key={index}
                                        label={factor}
                                        onDelete={() => handleRemoveRiskFactor(index)}
                                        tone="warning"
                                        variant="outlined"
                                        size="sm"
                                    />
                                ))}
                            </Box>
                        </AccordionDetails>
                    </Accordion>

                    {/* Mitigations */}
                    <Accordion>
                        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Typography as="p" className="text-sm" fontWeight={500}>
                                Mitigations ({riskAssessment.mitigations.length})
                            </Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <Box className="flex gap-2 mb-4">
                                <TextField
                                    fullWidth
                                    size="sm"
                                    placeholder="Add a mitigation..."
                                    value={newMitigation}
                                    onChange={(e) => setNewMitigation(e.target.value)}
                                    onKeyPress={(e) => e.key === 'Enter' && handleAddMitigation()}
                                />
                                <Button
                                    variant="outlined"
                                    size="sm"
                                    onClick={handleAddMitigation}
                                    disabled={!newMitigation.trim()}
                                >
                                    Add
                                </Button>
                            </Box>
                            <Box className="flex flex-wrap gap-2">
                                {riskAssessment.mitigations.map((mitigation: string, index: number) => (
                                    <Chip
                                        key={index}
                                        label={mitigation}
                                        onDelete={() => handleRemoveMitigation(index)}
                                        tone="success"
                                        variant="outlined"
                                        size="sm"
                                    />
                                ))}
                            </Box>
                        </AccordionDetails>
                    </Accordion>

                    {/* Rollback Plan */}
                    <Accordion>
                        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Typography as="p" className="text-sm" fontWeight={500}>
                                Rollback Plan
                            </Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <TextField
                                fullWidth
                                multiline
                                rows={3}
                                placeholder="Describe the rollback strategy if something goes wrong..."
                                value={riskAssessment.rollbackPlan ?? ''}
                                onChange={(e) => handleRiskChange('rollbackPlan', e.target.value)}
                            />
                        </AccordionDetails>
                    </Accordion>
                </CardContent>
            </Card>
        </Box>
    );
}

export default PlanStep;
