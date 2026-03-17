/**
 * ContextStep - UI for the Context step of the workflow
 *
 * @doc.type component
 * @doc.purpose Context step workspace
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React from 'react';
import { Box, Card, CardContent, Typography, TextField, Chip, IconButton, Button, InteractiveList as List, ListItem, ListItemIcon, ListItemText, ListItemText as ListItemSecondaryAction, Divider, FormControl, InputLabel, Select, MenuItem } from '@ghatana/ui';
import { Plus as AddIcon, Trash2 as DeleteIcon, Link as LinkIcon, Code as CodeIcon, Bug as BugIcon, FileText as DocIcon, Folder as FolderIcon } from 'lucide-react';
import { useAtomValue, useSetAtom } from 'jotai';

import {
    currentWorkflowAtom,
    draftStepDataAtom,
    updateDraftStepDataAtom,
} from '../../../stores/workflow.store';

import type { ContextStepData, ContextReference } from '@ghatana/yappc-types';

// ============================================================================
// CONSTANTS
// ============================================================================

const REFERENCE_TYPES: { value: ContextReference['type']; label: string; icon: React.ElementType }[] = [
    { value: 'TICKET', label: 'Ticket', icon: BugIcon },
    { value: 'REPO', label: 'Repository', icon: CodeIcon },
    { value: 'SERVICE', label: 'Service', icon: FolderIcon },
    { value: 'WORKFLOW', label: 'Workflow', icon: LinkIcon },
    { value: 'DOCUMENT', label: 'Document', icon: DocIcon },
];

// ============================================================================
// COMPONENT
// ============================================================================

export function ContextStep() {
    const workflow = useAtomValue(currentWorkflowAtom);
    const draftData = useAtomValue(draftStepDataAtom) as ContextStepData | null;
    const updateDraft = useSetAtom(updateDraftStepDataAtom);

    const [newSystem, setNewSystem] = React.useState('');
    const [newConstraint, setNewConstraint] = React.useState('');
    const [newRefType, setNewRefType] = React.useState<ContextReference['type']>('TICKET');
    const [newRefName, setNewRefName] = React.useState('');
    const [newRefUrl, setNewRefUrl] = React.useState('');

    // Get current data (draft or saved)
    const baseData = draftData ?? workflow?.steps.context.data;
    const currentData: ContextStepData = {
        systemsImpacted: baseData?.systemsImpacted ?? [],
        constraints: baseData?.constraints ?? [],
        references: baseData?.references ?? [],
    };

    const handleChange = (field: keyof ContextStepData, value: unknown) => {
        updateDraft({ ...currentData, [field]: value });
    };

    const handleAddSystem = () => {
        if (newSystem.trim()) {
            handleChange('systemsImpacted', [...(currentData.systemsImpacted ?? []), newSystem.trim()]);
            setNewSystem('');
        }
    };

    const handleRemoveSystem = (index: number) => {
        handleChange(
            'systemsImpacted',
            (currentData.systemsImpacted ?? []).filter((_: string, i: number) => i !== index)
        );
    };

    const handleAddConstraint = () => {
        if (newConstraint.trim()) {
            handleChange('constraints', [...(currentData.constraints ?? []), newConstraint.trim()]);
            setNewConstraint('');
        }
    };

    const handleRemoveConstraint = (index: number) => {
        handleChange(
            'constraints',
            (currentData.constraints ?? []).filter((_: string, i: number) => i !== index)
        );
    };

    const handleAddReference = () => {
        if (newRefName.trim()) {
            const newRef: ContextReference = {
                id: `ref-${Date.now()}`,
                type: newRefType,
                name: newRefName.trim(),
                url: newRefUrl.trim() || undefined,
            };
            handleChange('references', [...(currentData.references ?? []), newRef]);
            setNewRefName('');
            setNewRefUrl('');
        }
    };

    const handleRemoveReference = (id: string) => {
        handleChange(
            'references',
            (currentData.references ?? []).filter((r: ContextReference) => r.id !== id)
        );
    };

    return (
        <Box className="max-w-[800px] mx-auto">
            {/* Systems Impacted */}
            <Card className="mb-6">
                <CardContent>
                    <Typography as="p" className="text-lg font-medium" gutterBottom fontWeight={600}>
                        Systems Impacted
                    </Typography>
                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                        List all systems, services, or components affected by this work.
                    </Typography>

                    <Box className="flex gap-2 mb-4">
                        <TextField
                            fullWidth
                            size="sm"
                            placeholder="Add a system or service..."
                            value={newSystem}
                            onChange={(e) => setNewSystem(e.target.value)}
                            onKeyPress={(e) => e.key === 'Enter' && handleAddSystem()}
                        />
                        <Button
                            variant="outlined"
                            onClick={handleAddSystem}
                            disabled={!newSystem.trim()}
                            startIcon={<AddIcon />}
                        >
                            Add
                        </Button>
                    </Box>

                    <Box className="flex flex-wrap gap-2">
                        {(currentData.systemsImpacted ?? []).map((system: string) => (
                            <Chip
                                key={`system-${system}`}
                                label={system}
                                onDelete={() => handleRemoveSystem(currentData.systemsImpacted?.indexOf(system) ?? -1)}
                                tone="primary"
                                variant="outlined"
                            />
                        ))}
                    </Box>
                </CardContent>
            </Card>

            {/* Constraints & Assumptions */}
            <Card className="mb-6">
                <CardContent>
                    <Typography as="p" className="text-lg font-medium" gutterBottom fontWeight={600}>
                        Constraints & Assumptions
                    </Typography>
                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                        Document any constraints, limitations, or assumptions for this work.
                    </Typography>

                    <Box className="flex gap-2 mb-4">
                        <TextField
                            fullWidth
                            size="sm"
                            placeholder="Add a constraint or assumption..."
                            value={newConstraint}
                            onChange={(e) => setNewConstraint(e.target.value)}
                            onKeyPress={(e) => e.key === 'Enter' && handleAddConstraint()}
                        />
                        <Button
                            variant="outlined"
                            onClick={handleAddConstraint}
                            disabled={!newConstraint.trim()}
                            startIcon={<AddIcon />}
                        >
                            Add
                        </Button>
                    </Box>

                    {(currentData.constraints ?? []).length > 0 && (
                        <List dense>
                            {(currentData.constraints ?? []).map((constraint: string) => (
                                <ListItem key={`constraint-${constraint}`} className="px-0">
                                    <ListItemText
                                        primary={constraint}
                                        primaryTypographyProps={{ variant: 'body2' }}
                                    />
                                    <ListItemSecondaryAction>
                                        <IconButton
                                            edge="end"
                                            size="sm"
                                            onClick={() => handleRemoveConstraint(currentData.constraints?.indexOf(constraint) ?? -1)}
                                        >
                                            <DeleteIcon size={16} />
                                        </IconButton>
                                    </ListItemSecondaryAction>
                                </ListItem>
                            ))}
                        </List>
                    )}
                </CardContent>
            </Card>

            {/* References */}
            <Card>
                <CardContent>
                    <Typography as="p" className="text-lg font-medium" gutterBottom fontWeight={600}>
                        References
                    </Typography>
                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                        Link to tickets, repos, documentation, or other workflows.
                    </Typography>

                    <Box className="flex gap-2 mb-4 flex-wrap">
                        <FormControl size="sm" className="min-w-[120px]">
                            <InputLabel>Type</InputLabel>
                            <Select
                                value={newRefType}
                                label="Type"
                                onChange={(e: unknown) => setNewRefType(e.target.value as ContextReference['type'])}
                            >
                                {REFERENCE_TYPES.map((type) => (
                                    <MenuItem key={type.value} value={type.value}>
                                        {type.label}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <TextField
                            size="sm"
                            placeholder="Name"
                            value={newRefName}
                            onChange={(e) => setNewRefName(e.target.value)}
                            className="grow min-w-[150px]"
                        />
                        <TextField
                            size="sm"
                            placeholder="URL (optional)"
                            value={newRefUrl}
                            onChange={(e) => setNewRefUrl(e.target.value)}
                            className="grow min-w-[200px]"
                        />
                        <Button
                            variant="outlined"
                            onClick={handleAddReference}
                            disabled={!newRefName.trim()}
                            startIcon={<AddIcon />}
                        >
                            Add
                        </Button>
                    </Box>

                    {(currentData.references ?? []).length > 0 && (
                        <List>
                            {(currentData.references ?? []).map((ref: ContextReference, index: number) => {
                                const refType = REFERENCE_TYPES.find((t) => t.value === ref.type);
                                const RefIcon = refType?.icon || LinkIcon;

                                return (
                                    <React.Fragment key={ref.id}>
                                        <ListItem className="px-0">
                                            <ListItemIcon className="min-w-[40px]">
                                                <RefIcon color="action" />
                                            </ListItemIcon>
                                            <ListItemText
                                                primary={ref.name}
                                                secondary={ref.url ? (
                                                    <a href={ref.url} target="_blank" rel="noopener noreferrer">
                                                        {ref.url}
                                                    </a>
                                                ) : refType?.label}
                                            />
                                            <ListItemSecondaryAction>
                                                <IconButton
                                                    edge="end"
                                                    size="sm"
                                                    onClick={() => handleRemoveReference(ref.id)}
                                                >
                                                    <DeleteIcon size={16} />
                                                </IconButton>
                                            </ListItemSecondaryAction>
                                        </ListItem>
                                        {index < (currentData.references ?? []).length - 1 && <Divider />}
                                    </React.Fragment>
                                );
                            })}
                        </List>
                    )}
                </CardContent>
            </Card>
        </Box>
    );
}

export default ContextStep;
