/**
 * VerifyStep - UI for the Verify step of the workflow
 *
 * @doc.type component
 * @doc.purpose Verify step workspace
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React from 'react';
import { Box, Typography, TextField, IconButton, Button, InteractiveList as List, ListItem, ListItemIcon, ListItemText, ListItemText as ListItemSecondaryAction, Divider, Checkbox, Chip, Alert, ToggleButtonGroup, ToggleButton } from '@ghatana/design-system';
import { Plus as AddIcon, Trash2 as DeleteIcon, CheckCircle as PassIcon, XCircle as FailIcon, FlaskConical as TestIcon, Camera as ScreenshotIcon, FileText as LogIcon, Pointer as ManualIcon } from 'lucide-react';
import { useAtomValue, useSetAtom } from 'jotai';

import {
    currentWorkflowAtom,
    draftStepDataAtom,
    updateDraftStepDataAtom,
} from '../../../stores/workflow.store';

interface VerificationEvidence {
    id: string;
    type: 'TEST_RESULT' | 'SCREENSHOT' | 'LOG' | 'MANUAL_CHECK';
    name: string;
    status: 'PASS' | 'FAIL' | 'SKIPPED';
}

interface ChecklistItem {
    id: string;
    label: string;
    checked: boolean;
}

interface VerifyStepData {
    verificationStatus: 'PENDING' | 'PASS' | 'FAIL';
    acceptanceChecklist: ChecklistItem[];
    evidence: VerificationEvidence[];
}

// ============================================================================
// CONSTANTS
// ============================================================================

const EVIDENCE_TYPES: { value: VerificationEvidence['type']; label: string; icon: React.ElementType }[] = [
    { value: 'TEST_RESULT', label: 'Test Result', icon: TestIcon },
    { value: 'SCREENSHOT', label: 'Screenshot', icon: ScreenshotIcon },
    { value: 'LOG', label: 'Log', icon: LogIcon },
    { value: 'MANUAL_CHECK', label: 'Manual Check', icon: ManualIcon },
];

// ============================================================================
// COMPONENT
// ============================================================================

export function VerifyStep() {
    const workflow = useAtomValue(currentWorkflowAtom);
    const draftData = useAtomValue(draftStepDataAtom) as VerifyStepData | null;
    const updateDraft = useSetAtom(updateDraftStepDataAtom);

    const [newChecklistItem, setNewChecklistItem] = React.useState('');
    const [newEvidenceType, setNewEvidenceType] = React.useState<VerificationEvidence['type']>('TEST_RESULT');
    const [newEvidenceName, setNewEvidenceName] = React.useState('');
    const [newEvidenceStatus, setNewEvidenceStatus] = React.useState<VerificationEvidence['status']>('PASS');

    // Get current data (draft or saved)
    const baseData = (draftData ?? workflow?.steps.verify.data) as Partial<VerifyStepData> | null;
    const currentData: VerifyStepData = {
        verificationStatus: baseData?.verificationStatus ?? 'PENDING',
        acceptanceChecklist: baseData?.acceptanceChecklist ?? [],
        evidence: baseData?.evidence ?? [],
    };

    const handleChange = (field: keyof VerifyStepData, value: unknown) => {
        updateDraft({ ...currentData, [field]: value });
    };

    // Calculate overall status
    const evidence = currentData.evidence ?? [];
    const passCount = evidence.filter((e: VerificationEvidence) => e.status === 'PASS').length;
    const failCount = evidence.filter((e: VerificationEvidence) => e.status === 'FAIL').length;
    const checklist = currentData.acceptanceChecklist ?? [];
    const checkedCount = checklist.filter((c: ChecklistItem) => c.checked).length;

    // Checklist
    const handleAddChecklistItem = () => {
        if (newChecklistItem.trim()) {
            const item: ChecklistItem = {
                id: `check-${Date.now()}`,
                label: newChecklistItem.trim(),
                checked: false,
            };
            handleChange('acceptanceChecklist', [...checklist, item]);
            setNewChecklistItem('');
        }
    };

    const handleToggleChecklistItem = (id: string) => {
        handleChange(
            'acceptanceChecklist',
            checklist.map((item: ChecklistItem) =>
                item.id === id ? { ...item, checked: !item.checked } : item
            )
        );
    };

    const handleRemoveChecklistItem = (id: string) => {
        handleChange(
            'acceptanceChecklist',
            checklist.filter((item: ChecklistItem) => item.id !== id)
        );
    };

    // Evidence
    const handleAddEvidence = () => {
        if (newEvidenceName.trim()) {
            const ev: VerificationEvidence = {
                id: `evidence-${Date.now()}`,
                type: newEvidenceType,
                name: newEvidenceName.trim(),
                status: newEvidenceStatus,
            };
            handleChange('evidence', [...evidence, ev]);
            setNewEvidenceName('');
        }
    };

    const handleRemoveEvidence = (id: string) => {
        handleChange(
            'evidence',
            evidence.filter((e: VerificationEvidence) => e.id !== id)
        );
    };

    return (
        <Box className="max-w-[800px] mx-auto">
            {/* Acceptance Checklist */}
            <Box className="mb-8">
                <Box className="flex items-center justify-between mb-4">
                    <Typography className="text-lg font-semibold">
                        Acceptance Checklist
                    </Typography>
                    <Box className="flex gap-2">
                        <Chip
                            icon={<PassIcon />}
                            label={`${passCount} Passed`}
                            tone="success"
                            variant="outlined"
                            size="sm"
                        />
                        <Chip
                            icon={<FailIcon />}
                            label={`${failCount} Failed`}
                            color={failCount > 0 ? 'error' : 'default'}
                            variant="outlined"
                            size="sm"
                        />
                        <Chip
                            label={`${checkedCount}/${checklist.length}`}
                            color={checkedCount === checklist.length && checklist.length > 0 ? 'success' : 'default'}
                            variant="outlined"
                            size="sm"
                        />
                    </Box>
                </Box>

                <Box className="flex gap-2 mb-4">
                    <TextField
                        fullWidth
                        size="sm"
                        placeholder="Add acceptance criterion..."
                        value={newChecklistItem}
                        onChange={(e) => setNewChecklistItem(e.target.value)}
                        onKeyPress={(e) => e.key === 'Enter' && handleAddChecklistItem()}
                    />
                    <Button
                        variant="outlined"
                        onClick={handleAddChecklistItem}
                        disabled={!newChecklistItem.trim()}
                        startIcon={<AddIcon />}
                    >
                        Add
                    </Button>
                </Box>

                {checklist.length === 0 ? (
                    <Alert severity="info">No acceptance criteria defined. Add items to verify.</Alert>
                ) : (
                    <List>
                        {checklist.map((item, index) => (
                            <React.Fragment key={item.id}>
                                <ListItem className="px-0">
                                    <ListItemIcon className="min-w-[40px]">
                                        <Checkbox
                                            checked={item.checked}
                                            onChange={() => handleToggleChecklistItem(item.id)}
                                        />
                                    </ListItemIcon>
                                    <ListItemText
                                        primary={
                                            <Typography className={item.checked ? 'text-fg-muted line-through' : ''}>
                                                {item.label}
                                            </Typography>
                                        }
                                    />
                                    <ListItemSecondaryAction>
                                        <IconButton
                                            edge="end"
                                            size="small"
                                            onClick={() => handleRemoveChecklistItem(item.id)}
                                        >
                                            <DeleteIcon size={16} />
                                        </IconButton>
                                    </ListItemSecondaryAction>
                                </ListItem>
                                {index < checklist.length - 1 && <Divider />}
                            </React.Fragment>
                        ))}
                    </List>
                )}
            </Box>

            <Divider className="my-8" />

            {/* Evidence */}
            <Box className="mb-8">
                <Typography gutterBottom className="text-lg font-semibold">
                    Verification Evidence
                </Typography>

                <Box className="flex gap-2 mb-4 flex-wrap items-center">
                    <ToggleButtonGroup
                        value={newEvidenceType}
                        exclusive
                            onChange={(value) => {
                                if (typeof value === 'string') {
                                    setNewEvidenceType(value as VerificationEvidence['type']);
                                }
                            }}
                        size="sm"
                    >
                        {EVIDENCE_TYPES.map((type) => (
                            <ToggleButton key={type.value} value={type.value}>
                                <type.icon size={16} className="mr-1" />
                                {type.label}
                            </ToggleButton>
                        ))}
                    </ToggleButtonGroup>
                </Box>

                <Box className="flex gap-2 mb-4">
                    <TextField
                        fullWidth
                        size="sm"
                        placeholder="Evidence name"
                        value={newEvidenceName}
                        onChange={(e) => setNewEvidenceName(e.target.value)}
                    />
                    <ToggleButtonGroup
                        value={newEvidenceStatus}
                        exclusive
                        onChange={(value) => {
                            if (typeof value === 'string') {
                                setNewEvidenceStatus(value as VerificationEvidence['status']);
                            }
                        }}
                        size="sm"
                    >
                        <ToggleButton value="PASS">
                            Pass
                        </ToggleButton>
                        <ToggleButton value="FAIL">
                            Fail
                        </ToggleButton>
                        <ToggleButton value="SKIPPED">
                            Skip
                        </ToggleButton>
                    </ToggleButtonGroup>
                    <Button
                        variant="solid"
                        onClick={handleAddEvidence}
                        disabled={!newEvidenceName.trim()}
                        startIcon={<AddIcon />}
                    >
                        Add
                    </Button>
                </Box>

                {evidence.length > 0 && (
                    <List>
                        {evidence.map((ev: VerificationEvidence, index: number) => {
                            const evidenceType = EVIDENCE_TYPES.find((t) => t.value === ev.type);
                            const EvidenceIcon = evidenceType?.icon || TestIcon;

                            return (
                                <React.Fragment key={ev.id}>
                                    <ListItem className="px-0">
                                        <ListItemIcon className="min-w-[40px]">
                                            <EvidenceIcon color="action" />
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={ev.name}
                                            secondary={evidenceType?.label}
                                        />
                                        <Chip
                                            label={ev.status}
                                            size="sm"
                                            color={ev.status === 'PASS' ? 'success' : ev.status === 'FAIL' ? 'error' : 'default'}
                                            className="mr-2"
                                        />
                                        <IconButton
                                            size="small"
                                            onClick={() => handleRemoveEvidence(ev.id)}
                                        >
                                            <DeleteIcon size={16} />
                                        </IconButton>
                                    </ListItem>
                                    {index < evidence.length - 1 && <Divider />}
                                </React.Fragment>
                            );
                        })}
                    </List>
                )}
            </Box>
        </Box>
    );
}

export default VerifyStep;
