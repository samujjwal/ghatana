/**
 * IntentStep - UI for the Intent step of the workflow
 *
 * @doc.type component
 * @doc.purpose Intent step workspace
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React from 'react';
import { Box, Typography, TextField, FormControl, InputLabel, Select, MenuItem, Chip, IconButton, Button, InteractiveList as List, ListItem, ListItemText, ListItemText as ListItemSecondaryAction, Divider, Alert, Grid } from '@ghatana/ui';
import { Plus as AddIcon, Trash2 as DeleteIcon } from 'lucide-react';
import { useAtomValue, useSetAtom } from 'jotai';

import {
    currentWorkflowAtom,
    draftStepDataAtom,
    updateDraftStepDataAtom,
} from '../../../stores/workflow.store';
import { CategoryContextPanel } from '../CategoryContextPanel';

import type { IntentStepData, WorkflowType, WorkflowCategory } from '@ghatana/yappc-types';
import { WORKFLOW_TYPE_TO_CATEGORY } from '@ghatana/yappc-types';

// ============================================================================
// CONSTANTS
// ============================================================================

interface WorkflowTypeOption {
    value: WorkflowType;
    label: string;
    description: string;
    category: WorkflowCategory;
}

const WORKFLOW_TYPES_BY_CATEGORY: Record<WorkflowCategory, WorkflowTypeOption[]> = {
    IDEATION: [
        { value: 'NEW_PRODUCT', label: 'New Product', description: 'Launch a new product or service', category: 'IDEATION' },
        { value: 'FEATURE_REQUEST', label: 'Feature Request', description: 'Request for new functionality', category: 'IDEATION' },
        { value: 'REGULATORY_REQ', label: 'Regulatory Requirement', description: 'Compliance-driven requirement', category: 'IDEATION' },
    ],
    ARCHITECTURE: [
        { value: 'ARCHITECTURE_REVIEW', label: 'Architecture Review', description: 'Review system architecture', category: 'ARCHITECTURE' },
        { value: 'THREAT_MODEL', label: 'Threat Model', description: 'Security threat modeling', category: 'ARCHITECTURE' },
        { value: 'DATA_CLASSIFICATION', label: 'Data Classification', description: 'Classify data sensitivity', category: 'ARCHITECTURE' },
    ],
    DEVELOPMENT: [
        { value: 'FEATURE', label: 'Feature', description: 'Implement new functionality', category: 'DEVELOPMENT' },
        { value: 'BUG_FIX', label: 'Bug Fix', description: 'Fix a defect or issue', category: 'DEVELOPMENT' },
        { value: 'REFACTOR', label: 'Refactor', description: 'Improve code structure', category: 'DEVELOPMENT' },
        { value: 'DEPENDENCY_UPDATE', label: 'Dependency Update', description: 'Update dependencies', category: 'DEVELOPMENT' },
        { value: 'DOCUMENTATION', label: 'Documentation', description: 'Create or update docs', category: 'DEVELOPMENT' },
        { value: 'TESTING', label: 'Testing', description: 'Add or improve tests', category: 'DEVELOPMENT' },
    ],
    BUILD: [
        { value: 'CI_PIPELINE', label: 'CI Pipeline', description: 'Configure or update CI', category: 'BUILD' },
        { value: 'SAST_SCAN', label: 'SAST Scan', description: 'Static security analysis', category: 'BUILD' },
        { value: 'IMAGE_BUILD', label: 'Image Build', description: 'Build container images', category: 'BUILD' },
    ],
    RELEASE: [
        { value: 'RELEASE', label: 'Release', description: 'Standard software release', category: 'RELEASE' },
        { value: 'DEPLOYMENT', label: 'Deployment', description: 'Deploy to environment', category: 'RELEASE' },
        { value: 'ROLLBACK', label: 'Rollback', description: 'Rollback a deployment', category: 'RELEASE' },
    ],
    OPERATIONS: [
        { value: 'INCIDENT', label: 'Incident', description: 'Production incident response', category: 'OPERATIONS' },
        { value: 'SCALING_EVENT', label: 'Scaling Event', description: 'Scale infrastructure', category: 'OPERATIONS' },
        { value: 'CAPACITY_PLAN', label: 'Capacity Plan', description: 'Plan for capacity', category: 'OPERATIONS' },
        { value: 'INFRASTRUCTURE', label: 'Infrastructure', description: 'Infrastructure changes', category: 'OPERATIONS' },
    ],
    SECOPS: [
        { value: 'SECURITY_INCIDENT', label: 'Security Incident', description: 'Security breach response', category: 'SECOPS' },
        { value: 'VULNERABILITY_REMEDIATION', label: 'Vulnerability Fix', description: 'Fix security vulnerabilities', category: 'SECOPS' },
        { value: 'ACCESS_REVIEW', label: 'Access Review', description: 'Review access permissions', category: 'SECOPS' },
        { value: 'SECURITY_UPDATE', label: 'Security Update', description: 'General security update', category: 'SECOPS' },
    ],
    GRC: [
        { value: 'AUDIT_PREP', label: 'Audit Preparation', description: 'Prepare for compliance audit', category: 'GRC' },
        { value: 'POLICY_REVIEW', label: 'Policy Review', description: 'Review security policies', category: 'GRC' },
        { value: 'RISK_ASSESSMENT', label: 'Risk Assessment', description: 'Assess organizational risk', category: 'GRC' },
    ],
    OPTIMIZATION: [
        { value: 'POST_MORTEM', label: 'Post-Mortem', description: 'Analyze past incidents', category: 'OPTIMIZATION' },
        { value: 'COST_OPTIMIZATION', label: 'Cost Optimization', description: 'Reduce costs', category: 'OPTIMIZATION' },
    ],
    INSTITUTIONALIZATION: [
        { value: 'TEMPLATE_CREATION', label: 'Template Creation', description: 'Create reusable templates', category: 'INSTITUTIONALIZATION' },
        { value: 'GUARDRAIL_UPDATE', label: 'Guardrail Update', description: 'Update org guardrails', category: 'INSTITUTIONALIZATION' },
    ],
};

const CATEGORY_LABELS: Record<WorkflowCategory, string> = {
    IDEATION: '💡 Ideation',
    ARCHITECTURE: '🏗️ Architecture',
    DEVELOPMENT: '💻 Development',
    BUILD: '🔨 Build',
    RELEASE: '🚀 Release',
    OPERATIONS: '⚙️ Operations',
    SECOPS: '🔒 SecOps',
    GRC: '⚖️ GRC',
    OPTIMIZATION: '📈 Optimization',
    INSTITUTIONALIZATION: '🎓 Institutionalization',
};

// ============================================================================
// COMPONENT
// ============================================================================

export function IntentStep() {
    const workflow = useAtomValue(currentWorkflowAtom);
    const draftData = useAtomValue(draftStepDataAtom) as IntentStepData | null;
    const updateDraft = useSetAtom(updateDraftStepDataAtom);

    const [newCriteria, setNewCriteria] = React.useState('');
    const [contextCollapsed, setContextCollapsed] = React.useState(false);

    // Get current data (draft or saved)
    const currentData: IntentStepData = draftData ?? workflow?.steps.intent.data ?? {
        workflowType: 'FEATURE',
        goalStatement: '',
        successCriteria: [],
    };

    // Derive category from workflow type
    const currentCategory: WorkflowCategory =
        workflow?.category ??
        WORKFLOW_TYPE_TO_CATEGORY[currentData.workflowType as WorkflowType] ??
        'DEVELOPMENT';

    const handleChange = (field: keyof IntentStepData, value: unknown) => {
        updateDraft({ ...currentData, [field]: value });
    };

    const handleAddCriteria = () => {
        if (newCriteria.trim()) {
            handleChange('successCriteria', [...(currentData.successCriteria ?? []), newCriteria.trim()]);
            setNewCriteria('');
        }
    };

    const handleRemoveCriteria = (index: number) => {
        handleChange(
            'successCriteria',
            (currentData.successCriteria ?? []).filter((_, i) => i !== index)
        );
    };

    // Safely access successCriteria with fallback
    const successCriteria = currentData.successCriteria ?? [];

    return (
        <Grid container spacing={3}>
            {/* Main Form Column */}
            <Grid size={{ xs: 12, md: 8 }}>
                <Box className="max-w-[800px]">
                    {/* Workflow Type */}
                    <Box className="mb-8">
                        <Typography as="h6" gutterBottom>
                            Workflow Type
                        </Typography>
                        <FormControl fullWidth size="sm">
                            <InputLabel>Select type</InputLabel>
                            <Select
                                value={currentData.workflowType ?? 'FEATURE'}
                                label="Select type"
                                onChange={(e) => handleChange('workflowType', e.target.value)}
                            >
                                {Object.entries(WORKFLOW_TYPES_BY_CATEGORY).map(([category, types]) => [
                                    <MenuItem
                                        key={`cat-${category}`}
                                        disabled
                                        className="font-bold bg-gray-100 dark:bg-gray-800 [&.Mui-disabled]:opacity-100"
                                    >
                                        {CATEGORY_LABELS[category as WorkflowCategory]}
                                    </MenuItem>,
                                    ...types.map((type) => (
                                        <MenuItem key={type.value} value={type.value} className="pl-8">
                                            <Box>
                                                <Typography as="p" className="text-sm">{type.label}</Typography>
                                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                    {type.description}
                                                </Typography>
                                            </Box>
                                        </MenuItem>
                                    )),
                                ])}
                            </Select>
                        </FormControl>
                    </Box>

                    <Divider className="my-8" />

                    {/* Goal Statement */}
                    <Box className="mb-8">
                        <Typography as="h6" gutterBottom>
                            Goal Statement
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                            Clearly describe what you want to achieve. Be specific and actionable.
                        </Typography>
                        <TextField
                            fullWidth
                            multiline
                            rows={4}
                            placeholder="Example: Implement user authentication with OAuth 2.0 to allow users to sign in with their Google or GitHub accounts..."
                            value={currentData.goalStatement ?? ''}
                            onChange={(e) => handleChange('goalStatement', e.target.value)}
                            variant="outlined"
                        />
                    </Box>

                    <Divider className="my-8" />

                    {/* Success Criteria */}
                    <Box>
                        <Typography as="h6" gutterBottom>
                            Success Criteria
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                            Define measurable criteria that determine when this work is complete.
                        </Typography>

                        {/* Add new criteria */}
                        <Box className="flex gap-2 mb-4">
                            <TextField
                                fullWidth
                                size="sm"
                                placeholder="Add a success criterion..."
                                value={newCriteria}
                                onChange={(e) => setNewCriteria(e.target.value)}
                                onKeyPress={(e) => e.key === 'Enter' && handleAddCriteria()}
                            />
                            <Button
                                variant="outlined"
                                onClick={handleAddCriteria}
                                disabled={!newCriteria.trim()}
                                startIcon={<AddIcon />}
                            >
                                Add
                            </Button>
                        </Box>

                        {/* Criteria list */}
                        {successCriteria.length === 0 ? (
                            <Alert severity="info" className="mt-4">
                                No success criteria defined yet. Add at least one criterion to proceed.
                            </Alert>
                        ) : (
                            <List>
                                {successCriteria.map((criteria, index) => (
                                    <React.Fragment key={index}>
                                        <ListItem className="px-0">
                                            <Chip
                                                label={index + 1}
                                                size="sm"
                                                tone="primary"
                                                className="mr-4"
                                            />
                                            <ListItemText primary={criteria} />
                                            <ListItemSecondaryAction>
                                                <IconButton
                                                    edge="end"
                                                    size="sm"
                                                    onClick={() => handleRemoveCriteria(index)}
                                                >
                                                    <DeleteIcon size={16} />
                                                </IconButton>
                                            </ListItemSecondaryAction>
                                        </ListItem>
                                        {index < successCriteria.length - 1 && <Divider />}
                                    </React.Fragment>
                                ))}
                            </List>
                        )}
                    </Box>
                </Box>
            </Grid>

            {/* Context Panel Column */}
            <Grid size={{ xs: 12, md: 4 }}>
                <Box className="sticky top-[16px]">
                    <CategoryContextPanel
                        category={currentCategory}
                        currentStep="INTENT"
                        collapsed={contextCollapsed}
                        onToggle={() => setContextCollapsed(!contextCollapsed)}
                    />
                </Box>
            </Grid>
        </Grid>
    );
}

export default IntentStep;
