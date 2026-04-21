/**
 * InstitutionalizeStep - UI for the Institutionalize step of the workflow
 *
 * @doc.type component
 * @doc.purpose Institutionalize step workspace
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React from 'react';
import { Box, Card, CardContent, Typography, TextField, Button, InteractiveList as List, ListItem, ListItemIcon, ListItemText, ListItemSecondaryAction, IconButton, Divider, Chip, Alert, Surface as Paper, Select, MenuItem, FormControl, InputLabel } from '@ghatana/design-system';
import { Plus as AddIcon, Trash2 as DeleteIcon, ListChecks as ChecklistIcon, FileText as TemplateIcon, BookOpen as ADRIcon, BookOpen as RunbookIcon, ShieldCheck as PolicyIcon, User as PersonIcon, Gavel as EnforcementIcon } from 'lucide-react';
import { useAtomValue, useSetAtom } from 'jotai';

import {
    currentWorkflowAtom,
    draftStepDataAtom,
    updateDraftStepDataAtom,
} from '../../../stores/workflow.store';

import type { InstitutionalizeStepData, InstitutionalAction } from '@yappc/core/types';

// ============================================================================
// CONSTANTS
// ============================================================================

const ACTION_TYPES: { value: InstitutionalAction['type']; label: string; icon: React.ElementType; desc: string }[] = [
    { value: 'CHECKLIST', label: 'Checklist', icon: ChecklistIcon, desc: 'Add items to existing checklists' },
    { value: 'TEMPLATE', label: 'Template', icon: TemplateIcon, desc: 'Create or update workflow templates' },
    { value: 'ADR', label: 'ADR', icon: ADRIcon, desc: 'Architecture Decision Record' },
    { value: 'RUNBOOK', label: 'Runbook', icon: RunbookIcon, desc: 'Operational runbook update' },
    { value: 'POLICY', label: 'Policy', icon: PolicyIcon, desc: 'Policy or process update' },
];

const ENFORCEMENT_LEVELS = [
    { value: 0, label: 'Optional' },
    { value: 25, label: 'Recommended' },
    { value: 50, label: 'Encouraged' },
    { value: 75, label: 'Required' },
    { value: 100, label: 'Mandatory' },
];

// Mock team members for assignment
const TEAM_MEMBERS = [
    'Alice Johnson',
    'Bob Smith',
    'Carol Williams',
    'David Brown',
    'Eve Davis',
    'Frank Miller',
];

// ============================================================================
// COMPONENT
// ============================================================================

export function InstitutionalizeStep() {
    const workflow = useAtomValue(currentWorkflowAtom);
    const draftData = useAtomValue(draftStepDataAtom) as InstitutionalizeStepData | null;
    const updateDraft = useSetAtom(updateDraftStepDataAtom);

    const [newActionType, setNewActionType] = React.useState<InstitutionalAction['type']>('CHECKLIST');
    const [newActionTitle, setNewActionTitle] = React.useState('');
    const [newActionOwner, setNewActionOwner] = React.useState<string | null>(null);
    const [newActionEnforcement, setNewActionEnforcement] = React.useState(50);
    const [newActionApprovers, setNewActionApprovers] = React.useState<string[]>([]);

    // Get current data (draft or saved)
    const baseData = draftData ?? workflow?.steps.institutionalize.data;
    const currentData: InstitutionalizeStepData = baseData ?? {
        actions: [],
        approvalChain: [],
        effectiveDate: undefined,
    };
    const actions = currentData.actions ?? [];

    const handleChange = (field: keyof InstitutionalizeStepData, value: unknown) => {
        updateDraft({ ...currentData, [field]: value });
    };

    const getEnforcementLabel = (value: number) => {
        const level = ENFORCEMENT_LEVELS.find((l) => l.value === value) || ENFORCEMENT_LEVELS[2];
        return level.label;
    };

    const handleAddAction = () => {
        if (newActionTitle.trim() && newActionOwner) {
            const action: InstitutionalAction = {
                id: `action-${Date.now()}`,
                type: newActionType,
                title: newActionTitle.trim(),
                owner: newActionOwner,
                enforcementLevel: newActionEnforcement,
                status: 'PENDING',
                approvers: newActionApprovers,
            };
            handleChange('actions', [...actions, action]);
            // Reset form
            setNewActionTitle('');
            setNewActionOwner(null);
            setNewActionEnforcement(50);
            setNewActionApprovers([]);
        }
    };

    const handleRemoveAction = (id: string) => {
        handleChange(
            'actions',
            actions.filter((a) => a.id !== id)
        );
    };

    const handleUpdateActionStatus = (id: string, status: InstitutionalAction['status']) => {
        handleChange(
            'actions',
            actions.map((a) => (a.id === id ? { ...a, status } : a))
        );
    };

    // Get action type info
    const getActionTypeInfo = (type: InstitutionalAction['type']) => {
        return ACTION_TYPES.find((t) => t.value === type) || ACTION_TYPES[0];
    };

    // Summary stats
    const pendingCount = actions.filter((a) => a.status === 'PENDING').length;
    const approvedCount = actions.filter((a) => a.status === 'APPROVED').length;
    const rejectedCount = actions.filter((a) => a.status === 'REJECTED').length;

    return (
        <Box className="max-w-[900px] mx-auto">

            {/* Summary Stats */}
            {actions.length > 0 && (
                <Paper className="p-4 mb-6 bg-gray-100 dark:bg-gray-800">
                    <Box className="flex gap-6 justify-center">
                        <Box className="text-center">
                            <Typography variant="h4">{actions.length}</Typography>
                            <Typography component="span" className="text-xs text-gray-500" color="text.secondary">
                                Total Actions
                            </Typography>
                        </Box>
                        <Divider orientation="vertical" />
                        <Box className="text-center">
                            <Typography variant="h4" color="warning.main">
                                {pendingCount}
                            </Typography>
                            <Typography component="span" className="text-xs text-gray-500" color="text.secondary">
                                Pending
                            </Typography>
                        </Box>
                        <Box className="text-center">
                            <Typography variant="h4" color="success.main">
                                {approvedCount}
                            </Typography>
                            <Typography component="span" className="text-xs text-gray-500" color="text.secondary">
                                Approved
                            </Typography>
                        </Box>
                        <Box className="text-center">
                            <Typography variant="h4" color="error.main">
                                {rejectedCount}
                            </Typography>
                            <Typography component="span" className="text-xs text-gray-500" color="text.secondary">
                                Rejected
                            </Typography>
                        </Box>
                    </Box>
                </Paper>
            )}

            {/* New Action Form */}
            <Card className="mb-6">
                <CardContent>
                    <Typography component="p" className="text-lg font-medium" gutterBottom>
                        Create Institutional Action
                    </Typography>

                    {/* Action Type Selection */}
                    <Box className="flex gap-2 mb-6 flex-wrap">
                        {ACTION_TYPES.map((actionType) => (
                            <Paper
                                key={actionType.value}
                                elevation={newActionType === actionType.value ? 3 : 0}
                                onClick={() => setNewActionType(actionType.value)}
                                className={`p-3 cursor-pointer border rounded-lg transition-all duration-200 min-w-[140px] ${newActionType === actionType.value ? 'border-blue-600 bg-blue-50 dark:bg-blue-900/20' : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900'}`}
                                style={{ flex: '1 1 150px' }}
                            >
                                <Box className="flex items-center gap-2">
                                    <actionType.icon className={newActionType === actionType.value ? 'text-blue-600' : 'text-gray-500'} />
                                    <Typography component="p" className="text-sm" fontWeight={newActionType === actionType.value ? 600 : 400}>
                                        {actionType.label}
                                    </Typography>
                                </Box>
                                <Typography component="span" className="text-xs text-gray-500" color="text.secondary">
                                    {actionType.desc}
                                </Typography>
                            </Paper>
                        ))}
                    </Box>

                    {/* Action Details */}
                    <Box className="flex flex-col gap-4">
                        <TextField
                            fullWidth
                            label="Action Title"
                            placeholder="What needs to be created or updated?"
                            value={newActionTitle}
                            onChange={(e) => setNewActionTitle(e.target.value)}
                        />

                        <Box className="flex gap-4">
                            <FormControl fullWidth>
                                <InputLabel id="institutional-action-owner-label">Owner</InputLabel>
                                <Select
                                    labelId="institutional-action-owner-label"
                                    value={newActionOwner ?? ''}
                                    label="Owner"
                                    onChange={(event) => {
                                        const nextOwner = event.target.value;
                                        setNewActionOwner(nextOwner === '' ? null : nextOwner);
                                    }}
                                >
                                    <MenuItem value="">
                                        <em>Assign owner</em>
                                    </MenuItem>
                                    {TEAM_MEMBERS.map((member) => (
                                        <MenuItem key={member} value={member}>
                                            {member}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                            <FormControl fullWidth>
                                <InputLabel id="institutional-action-approvers-label">Approvers</InputLabel>
                                <Select
                                    multiple
                                    labelId="institutional-action-approvers-label"
                                    value={newActionApprovers}
                                    label="Approvers"
                                    onChange={(event) => {
                                        const value = event.target.value;
                                        setNewActionApprovers(typeof value === 'string' ? value.split(',') : value);
                                    }}
                                >
                                    {TEAM_MEMBERS.filter((member) => member !== newActionOwner).map((member) => (
                                        <MenuItem key={member} value={member}>
                                            {member}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                        </Box>

                        {/* Enforcement Level Slider */}
                        <Box className="px-4">
                            <Box className="flex items-center gap-2 mb-2">
                                <EnforcementIcon size={16} className="text-gray-500" />
                                <Typography component="p" className="text-sm" color="text.secondary">
                                    Enforcement Level: <strong>{getEnforcementLabel(newActionEnforcement)}</strong>
                                </Typography>
                            </Box>
                            <input
                                type="range"
                                value={newActionEnforcement}
                                onChange={(event) => {
                                    setNewActionEnforcement(Number(event.target.value));
                                }}
                                step={25}
                                min={0}
                                max={100}
                                className="w-full"
                            />
                            <Box className="mt-2 flex justify-between text-xs text-gray-500">
                                {ENFORCEMENT_LEVELS.map((level) => (
                                    <span key={level.value}>{level.label}</span>
                                ))}
                            </Box>
                        </Box>

                        <Button
                            variant="contained"
                            onClick={handleAddAction}
                            disabled={!newActionTitle.trim() || !newActionOwner}
                            startIcon={<AddIcon />}
                            className="self-start"
                        >
                            Add Action
                        </Button>
                    </Box>
                </CardContent>
            </Card>

            {/* Actions List */}
            <Card>
                <CardContent>
                    <Typography component="p" className="text-lg font-medium" gutterBottom>
                        Institutional Actions
                    </Typography>

                    {actions.length === 0 ? (
                        <Alert severity="info">
                            No actions created yet. Promote your learnings by creating checklists, templates, ADRs, runbooks, or policies.
                        </Alert>
                    ) : (
                        <List>
                            {actions.map((action, index) => {
                                const typeInfo = getActionTypeInfo(action.type);
                                const TypeIcon = typeInfo.icon;

                                return (
                                    <React.Fragment key={action.id}>
                                        <ListItem className="px-0 items-start">
                                            <ListItemIcon className="mt-1 min-w-[40px]">
                                                <TypeIcon tone="secondary" />
                                            </ListItemIcon>
                                            <ListItemText
                                                primary={
                                                    <Box className="flex items-center gap-2 flex-wrap">
                                                        <Typography component="p" fontWeight={500}>
                                                            {action.title}
                                                        </Typography>
                                                        <Chip
                                                            label={typeInfo.label}
                                                            size="small"
                                                            variant="outlined"
                                                            color="secondary"
                                                            className="h-[20px] text-[10px]"
                                                        />
                                                        <Chip
                                                            label={action.status}
                                                            size="small"
                                                            color={
                                                                action.status === 'APPROVED'
                                                                    ? 'success'
                                                                    : action.status === 'REJECTED'
                                                                        ? 'error'
                                                                        : 'warning'
                                                            }
                                                            className="h-[20px] text-[10px]"
                                                        />
                                                        <Chip
                                                            label={getEnforcementLabel(action.enforcementLevel)}
                                                            size="small"
                                                            variant="outlined"
                                                            className="h-[20px] text-[10px]"
                                                        />
                                                    </Box>
                                                }
                                                secondary={
                                                    <>
                                                        <Typography component="span" className="mt-2 block text-xs text-gray-500" color="text.secondary">
                                                            <PersonIcon size={12} className="mr-1 inline-block text-gray-500 dark:text-gray-400 align-middle" />
                                                            Owner: {action.owner}
                                                        </Typography>
                                                        {(action.approvers ?? []).length > 0 && (
                                                            <Typography component="span" className="mt-1 block text-xs text-gray-500" color="text.secondary">
                                                                Approvers:
                                                                {(action.approvers ?? []).map((approver, i) => (
                                                                    <Chip
                                                                        key={i}
                                                                        label={approver}
                                                                        size="small"
                                                                        variant="outlined"
                                                                        className="ml-1 h-[18px] text-[10px]"
                                                                    />
                                                                ))}
                                                            </Typography>
                                                        )}
                                                    </>
                                                }
                                            />
                                            <ListItemSecondaryAction>
                                                <Box className="flex flex-col gap-1">
                                                    {action.status === 'PENDING' && (
                                                        <>
                                                            <Button
                                                                size="small"
                                                                color="success"
                                                                variant="outlined"
                                                                onClick={() => handleUpdateActionStatus(action.id, 'APPROVED')}
                                                                className="min-w-0 px-2"
                                                            >
                                                                ✓
                                                            </Button>
                                                            <Button
                                                                size="small"
                                                                color="error"
                                                                variant="outlined"
                                                                onClick={() => handleUpdateActionStatus(action.id, 'REJECTED')}
                                                                className="min-w-0 px-2"
                                                            >
                                                                ✗
                                                            </Button>
                                                        </>
                                                    )}
                                                    <IconButton size="small" onClick={() => handleRemoveAction(action.id)}>
                                                        <DeleteIcon size={16} />
                                                    </IconButton>
                                                </Box>
                                            </ListItemSecondaryAction>
                                        </ListItem>
                                        {index < actions.length - 1 && <Divider />}
                                    </React.Fragment>
                                );
                            })}
                        </List>
                    )}
                </CardContent>
            </Card>

            {/* Effective Date */}
            <Card className="mt-6">
                <CardContent>
                    <Typography component="p" className="text-lg font-medium" gutterBottom>
                        Effective Date
                    </Typography>
                    <input
                        type="date"
                        value={currentData.effectiveDate ?? ''}
                        onChange={(e) => handleChange('effectiveDate', e.target.value || undefined)}
                        className="min-w-[200px] rounded border border-gray-300 px-3 py-2 text-sm"
                    />
                    <Typography component="p" className="mt-2 text-sm text-gray-500" color="text.secondary">
                        When should these changes take effect?
                    </Typography>
                </CardContent>
            </Card>
        </Box>
    );
}

export default InstitutionalizeStep;
