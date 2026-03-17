/**
 * ExecuteStep - UI for the Execute step of the workflow
 *
 * @doc.type component
 * @doc.purpose Execute step workspace
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React from 'react';
import { Box, Typography, TextField, IconButton, Button, InteractiveList as List, ListItem, ListItemIcon, ListItemText, ListItemText as ListItemSecondaryAction, Divider, FormControl, InputLabel, Select, MenuItem, Chip, LinearProgress, Switch, FormControlLabel, Alert } from '@ghatana/ui';
import { Plus as AddIcon, Trash2 as DeleteIcon, Code as CodeIcon, Settings as ConfigIcon, HardDrive as DataIcon, Cloud as InfraIcon, Bot as AIIcon } from 'lucide-react';
import { useAtomValue, useSetAtom } from 'jotai';

import {
    currentWorkflowAtom,
    draftStepDataAtom,
    updateDraftStepDataAtom,
} from '../../../stores/workflow.store';

import type { ExecuteStepData, ChangeRecord } from '@ghatana/yappc-types';

// ============================================================================
// CONSTANTS
// ============================================================================

const CHANGE_TYPES: { value: ChangeRecord['type']; label: string; icon: React.ElementType }[] = [
    { value: 'CODE', label: 'Code', icon: CodeIcon },
    { value: 'CONFIG', label: 'Configuration', icon: ConfigIcon },
    { value: 'TEST', label: 'Test', icon: CodeIcon },
    { value: 'DOCS', label: 'Documentation', icon: DataIcon },
    { value: 'INFRASTRUCTURE', label: 'Infrastructure', icon: InfraIcon },
];

const CHANGE_STATUSES: { value: ChangeRecord['status']; label: string; color: 'default' | 'info' | 'success' | 'warning' }[] = [
    { value: 'PENDING', label: 'Pending', color: 'default' },
    { value: 'IN_REVIEW', label: 'In Review', color: 'info' },
    { value: 'COMPLETED', label: 'Completed', color: 'success' },
    { value: 'ROLLED_BACK', label: 'Rolled Back', color: 'warning' },
];

// ============================================================================
// COMPONENT
// ============================================================================

export function ExecuteStep() {
    const workflow = useAtomValue(currentWorkflowAtom);
    const draftData = useAtomValue(draftStepDataAtom) as ExecuteStepData | null;
    const updateDraft = useSetAtom(updateDraftStepDataAtom);

    const [newChangeType, setNewChangeType] = React.useState<ChangeRecord['type']>('CODE');
    const [newChangeDesc, setNewChangeDesc] = React.useState('');
    const [newChangePath, setNewChangePath] = React.useState('');
    const [aiAssistEnabled, setAiAssistEnabled] = React.useState(true);

    // Get current data (draft or saved)
    const baseData = draftData ?? workflow?.steps.execute.data;
    const currentData: ExecuteStepData = {
        changes: baseData?.changes ?? [],
        progress: baseData?.progress ?? 0,
    };

    const handleChange = (field: keyof ExecuteStepData, value: unknown) => {
        updateDraft({ ...currentData, [field]: value });
    };

    // Calculate progress
    const changes = currentData.changes ?? [];
    const completedChanges = changes.filter((c) => c.status === 'COMPLETED').length;
    const totalChanges = changes.length;
    const progress = totalChanges > 0 ? (completedChanges / totalChanges) * 100 : 0;

    // Change records
    const handleAddChange = () => {
        if (newChangeDesc.trim()) {
            const newChange: ChangeRecord = {
                id: `change-${Date.now()}`,
                type: newChangeType,
                description: newChangeDesc.trim(),
                path: newChangePath.trim() || undefined,
                status: 'PENDING',
            };
            handleChange('changes', [...changes, newChange]);
            setNewChangeDesc('');
            setNewChangePath('');
        }
    };

    const handleRemoveChange = (id: string) => {
        handleChange(
            'changes',
            changes.filter((c) => c.id !== id)
        );
    };

    const handleUpdateChangeStatus = (id: string, status: ChangeRecord['status']) => {
        handleChange(
            'changes',
            changes.map((c) => (c.id === id ? { ...c, status } : c))
        );
    };

    return (
        <Box className="max-w-[800px] mx-auto">
            {/* Auto-track toggle */}
            <Box className="flex items-center justify-end mb-6">
                <FormControlLabel
                    control={
                        <Switch
                            checked={aiAssistEnabled}
                            onChange={(e) => setAiAssistEnabled(e.target.checked)}
                        />
                    }
                    label={
                        <Box className="flex items-center gap-1">
                            <AIIcon size={16} />
                            <Typography as="p" className="text-sm">AI Assist</Typography>
                        </Box>
                    }
                />
            </Box>

            {/* Progress */}
            {totalChanges > 0 && (
                <Box className="mb-8">
                    <Box className="flex justify-between mb-2">
                        <Typography as="p" className="text-sm" fontWeight={500}>
                            Execution Progress
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary">
                            {completedChanges} / {totalChanges} changes completed
                        </Typography>
                    </Box>
                    <LinearProgress
                        variant="determinate"
                        value={progress}
                        className="h-[8px] rounded-2xl"
                    />
                </Box>
            )}

            {/* Add Change */}
            <Box className="mb-8">
                <Typography as="h6" gutterBottom fontWeight={600}>
                    Add Change
                </Typography>

                <Box className="flex gap-2 mb-4 flex-wrap">
                    <FormControl size="sm" className="min-w-[140px]">
                        <InputLabel>Type</InputLabel>
                        <Select
                            value={newChangeType}
                            label="Type"
                            onChange={(e) => setNewChangeType(e.target.value as ChangeRecord['type'])}
                        >
                            {CHANGE_TYPES.map((type) => (
                                <MenuItem key={type.value} value={type.value}>
                                    {type.label}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <TextField
                        size="sm"
                        placeholder="Description"
                        value={newChangeDesc}
                        onChange={(e) => setNewChangeDesc(e.target.value)}
                        className="grow min-w-[200px]"
                    />
                    <TextField
                        size="sm"
                        placeholder="File path (optional)"
                        value={newChangePath}
                        onChange={(e) => setNewChangePath(e.target.value)}
                        className="grow min-w-[200px]"
                    />
                    <Button
                        variant="solid"
                        onClick={handleAddChange}
                        disabled={!newChangeDesc.trim()}
                        startIcon={<AddIcon />}
                    >
                        Add
                    </Button>
                </Box>
            </Box>

            <Divider className="my-8" />

            {/* Changes List */}
            <Box>
                <Typography as="h6" gutterBottom>
                    Changes ({changes.length})
                </Typography>

                {changes.length === 0 ? (
                    <Alert severity="info">No changes tracked yet. Add changes as you execute the plan.</Alert>
                ) : (
                    <List>
                        {changes.map((change, index) => {
                            const changeType = CHANGE_TYPES.find((t) => t.value === change.type);
                            const ChangeIcon = changeType?.icon || CodeIcon;

                            return (
                                <React.Fragment key={change.id}>
                                    <ListItem className="px-0 items-start flex-wrap">
                                        <ListItemIcon className="mt-2 min-w-[40px]">
                                            <ChangeIcon color="action" />
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={
                                                <Box className="flex items-center gap-2">
                                                    <Typography as="p" className="text-sm" fontWeight={500}>
                                                        {change.description}
                                                    </Typography>
                                                    <Chip
                                                        label={changeType?.label}
                                                        size="sm"
                                                        variant="outlined"
                                                        className="h-[20px] text-[10px]"
                                                    />
                                                </Box>
                                            }
                                            secondary={
                                                change.path && (
                                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary" component="span" className="font-mono">
                                                        {change.path}
                                                    </Typography>
                                                )
                                            }
                                        />
                                        <FormControl size="sm" className="min-w-[120px] ml-auto">
                                            <Select
                                                value={change.status || 'PENDING'}
                                                size="sm"
                                                onChange={(e) => handleUpdateChangeStatus(change.id, e.target.value as ChangeRecord['status'])}
                                                className="h-[28px] text-xs"
                                            >
                                                {CHANGE_STATUSES.map((status) => (
                                                    <MenuItem key={status.value} value={status.value}>
                                                        <Chip
                                                            label={status.label}
                                                            size="sm"
                                                            color={status.color}
                                                            className="h-[18px] text-[10px]"
                                                        />
                                                    </MenuItem>
                                                ))}
                                            </Select>
                                        </FormControl>
                                        <ListItemSecondaryAction>
                                            <IconButton
                                                edge="end"
                                                size="sm"
                                                onClick={() => handleRemoveChange(change.id)}
                                            >
                                                <DeleteIcon size={16} />
                                            </IconButton>
                                        </ListItemSecondaryAction>
                                    </ListItem>
                                    {index < changes.length - 1 && <Divider />}
                                </React.Fragment>
                            );
                        })}
                    </List>
                )}
            </Box>
        </Box>
    );
}

export default ExecuteStep;
