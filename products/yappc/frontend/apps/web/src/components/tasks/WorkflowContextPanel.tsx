/**
 * Workflow Context Panel Component
 *
 * Right-side panel showing workflow context, progress, and artifacts.
 * Provides overview of completed work and available resources.
 *
 * @doc.type component
 * @doc.purpose Workflow context display
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React from 'react';
import { Box, Typography, IconButton, Divider, InteractiveList as List, ListItem, ListItemIcon, ListItemText, ListItemButton, Chip, LinearProgress, Accordion, AccordionSummary, AccordionDetails, Avatar, AvatarGroup, Tooltip, Badge, Tabs, Tab } from '@ghatana/ui';
import { X as CloseIcon, ChevronDown as ExpandIcon, CheckCircle as CompletedIcon, FileText as DocumentIcon, Code as CodeIcon, Image as ImageIcon, Braces as DataIcon, Folder as FolderIcon, Clock as TimeIcon, User as PersonIcon, TrendingUp as ProgressIcon, Package as ArtifactsIcon, ClipboardList as TasksIcon } from 'lucide-react';
import type { LifecycleStage } from '@ghatana/yappc-types/tasks';

// ============================================================================
// Types
// ============================================================================

interface SerializedWorkflowInstance {
    id: string;
    templateId: string;
    status: 'pending' | 'running' | 'paused' | 'completed' | 'cancelled';
    createdAt: string;
    phases: Array<{
        id: string;
        name: string;
        status: string;
        steps: Array<{
            id: string;
            taskId: string;
            status: string;
            completedAt?: string;
            outputs?: Record<string, unknown>;
        }>;
    }>;
    currentPhaseIndex: number;
    lifecycleState?: {
        currentStage: LifecycleStage;
    };
}

interface WorkflowContextPanelProps {
    workflow: SerializedWorkflowInstance | null;
    completedTasks: Array<{
        id: string;
        taskId: string;
        status: string;
        completedAt?: string;
        outputs?: Record<string, unknown>;
    }>;
    onClose?: () => void;
}

interface TabPanelProps {
    children?: React.ReactNode;
    value: number;
    index: number;
}

// ============================================================================
// Constants
// ============================================================================

const ARTIFACT_ICONS: Record<string, React.ElementType> = {
    document: DocumentIcon,
    code: CodeIcon,
    image: ImageIcon,
    data: DataIcon,
    folder: FolderIcon,
    default: DataIcon,
};

// ============================================================================
// Component
// ============================================================================

export function WorkflowContextPanel({
    workflow,
    completedTasks,
    onClose,
}: WorkflowContextPanelProps) {
    const [tabValue, setTabValue] = React.useState(0);

    // Calculate progress
    const progress = React.useMemo(() => {
        if (!workflow) return { total: 0, completed: 0, percentage: 0 };

        let total = 0;
        let completed = 0;

        for (const phase of workflow.phases) {
            for (const step of phase.steps) {
                total++;
                if (step.status === 'completed') completed++;
            }
        }

        return {
            total,
            completed,
            percentage: total > 0 ? Math.round((completed / total) * 100) : 0,
        };
    }, [workflow]);

    // Extract artifacts from completed tasks
    const artifacts = React.useMemo(() => {
        const items: Array<{
            id: string;
            name: string;
            type: string;
            taskId: string;
            createdAt: string;
        }> = [];

        for (const task of completedTasks) {
            if (task.outputs && typeof task.outputs === 'object') {
                // Extract artifacts from outputs
                const outputArtifacts = task.outputs['artifacts'] as Array<{
                    id: string;
                    name?: string;
                    type?: string;
                }> | undefined;

                if (Array.isArray(outputArtifacts)) {
                    outputArtifacts.forEach((artifact) => {
                        items.push({
                            id: artifact.id,
                            name: artifact.name || artifact.id,
                            type: artifact.type || 'data',
                            taskId: task.taskId,
                            createdAt: task.completedAt || new Date().toISOString(),
                        });
                    });
                }
            }
        }

        return items;
    }, [completedTasks]);

    // Group artifacts by type
    const groupedArtifacts = React.useMemo(() => {
        const groups: Record<string, typeof artifacts> = {};
        for (const artifact of artifacts) {
            const type = artifact.type || 'other';
            if (!groups[type]) groups[type] = [];
            groups[type].push(artifact);
        }
        return groups;
    }, [artifacts]);

    if (!workflow) {
        return (
            <Box className="p-6 text-center">
                <Typography color="text.secondary">
                    No workflow selected
                </Typography>
            </Box>
        );
    }

    return (
        <Box className="flex flex-col h-full">
            {/* Header */}
            <Box
                className="p-4 flex items-center border-gray-200 dark:border-gray-700 border-b" >
                <Typography as="h6" className="flex-1">
                    Workflow Context
                </Typography>
                {onClose && (
                    <IconButton size="sm" onClick={onClose}>
                        <CloseIcon />
                    </IconButton>
                )}
            </Box>

            {/* Progress Summary */}
            <Box className="p-4 bg-gray-100 dark:bg-gray-800">
                <Box className="flex items-center mb-2">
                    <ProgressIcon className="mr-2 text-blue-600" />
                    <Typography as="p" className="text-sm font-medium">Progress</Typography>
                    <Chip
                        label={`${progress.percentage}%`}
                        size="sm"
                        tone="primary"
                        className="ml-auto"
                    />
                </Box>
                <LinearProgress
                    variant="determinate"
                    value={progress.percentage}
                    className="rounded h-[8px]"
                />
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-1 block">
                    {progress.completed} of {progress.total} tasks completed
                </Typography>
            </Box>

            {/* Tabs */}
            <Tabs
                value={tabValue}
                onChange={(_, value) => setTabValue(value)}
                variant="fullWidth"
                className="border-gray-200 dark:border-gray-700 border-b" >
                <Tab
                    icon={<Badge badgeContent={completedTasks.length} tone="success"><TasksIcon /></Badge>}
                    iconPosition="start"
                    label="Tasks"
                    className="min-h-[48px]"
                />
                <Tab
                    icon={<Badge badgeContent={artifacts.length} tone="primary"><ArtifactsIcon /></Badge>}
                    iconPosition="start"
                    label="Artifacts"
                    className="min-h-[48px]"
                />
            </Tabs>

            {/* Tab Panels */}
            <Box className="flex-1 overflow-auto">
                <TabPanel value={tabValue} index={0}>
                    <CompletedTasksList tasks={completedTasks} />
                </TabPanel>
                <TabPanel value={tabValue} index={1}>
                    <ArtifactsList groupedArtifacts={groupedArtifacts} />
                </TabPanel>
            </Box>

            {/* Footer */}
            <Box
                className="p-4 border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 border-t" >
                <Box className="flex items-center gap-2">
                    <TimeIcon size={16} color="action" />
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        Started {new Date(workflow.createdAt).toLocaleDateString()}
                    </Typography>
                </Box>
                <Box className="flex items-center gap-2 mt-1">
                    <PersonIcon size={16} color="action" />
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        Current phase: {workflow.phases[workflow.currentPhaseIndex]?.name || 'N/A'}
                    </Typography>
                </Box>
            </Box>
        </Box>
    );
}

// ============================================================================
// Sub-components
// ============================================================================

function TabPanel({ children, value, index }: TabPanelProps) {
    return (
        <Box role="tabpanel" hidden={value !== index} className="p-4">
            {value === index && children}
        </Box>
    );
}

function CompletedTasksList({
    tasks,
}: {
    tasks: Array<{
        id: string;
        taskId: string;
        status: string;
        completedAt?: string;
    }>;
}) {
    if (tasks.length === 0) {
        return (
            <Box className="text-center py-8">
                <Typography color="text.secondary">
                    No tasks completed yet
                </Typography>
            </Box>
        );
    }

    // Group by domain (extracted from taskId)
    const grouped = React.useMemo(() => {
        const groups: Record<string, typeof tasks> = {};
        for (const task of tasks) {
            const domain = task.taskId.split('-')[0] || 'other';
            if (!groups[domain]) groups[domain] = [];
            groups[domain].push(task);
        }
        return groups;
    }, [tasks]);

    return (
        <Box>
            {Object.entries(grouped).map(([domain, domainTasks]) => (
                <Accordion key={domain} defaultExpanded={domainTasks.length <= 5}>
                    <AccordionSummary expandIcon={<ExpandIcon />}>
                        <Typography as="p" className="text-sm font-medium">
                            {formatDomainName(domain)}
                        </Typography>
                        <Chip
                            label={domainTasks.length}
                            size="sm"
                            className="ml-2"
                        />
                    </AccordionSummary>
                    <AccordionDetails className="p-0">
                        <List dense disablePadding>
                            {domainTasks.map((task) => (
                                <ListItem key={task.id} className="py-1">
                                    <ListItemIcon className="min-w-[32px]">
                                        <CompletedIcon tone="success" size={16} />
                                    </ListItemIcon>
                                    <ListItemText
                                        primary={formatTaskName(task.taskId)}
                                        secondary={
                                            task.completedAt
                                                ? new Date(task.completedAt).toLocaleString()
                                                : undefined
                                        }
                                        primaryTypographyProps={{ variant: 'body2' }}
                                        secondaryTypographyProps={{ variant: 'caption' }}
                                    />
                                </ListItem>
                            ))}
                        </List>
                    </AccordionDetails>
                </Accordion>
            ))}
        </Box>
    );
}

function ArtifactsList({
    groupedArtifacts,
}: {
    groupedArtifacts: Record<string, Array<{
        id: string;
        name: string;
        type: string;
        taskId: string;
        createdAt: string;
    }>>;
}) {
    const entries = Object.entries(groupedArtifacts);

    if (entries.length === 0) {
        return (
            <Box className="text-center py-8">
                <Typography color="text.secondary">
                    No artifacts generated yet
                </Typography>
            </Box>
        );
    }

    return (
        <Box>
            {entries.map(([type, typeArtifacts]) => {
                const Icon = ARTIFACT_ICONS[type] || ARTIFACT_ICONS.default;

                return (
                    <Accordion key={type} defaultExpanded>
                        <AccordionSummary expandIcon={<ExpandIcon />}>
                            <Icon className="mr-2" />
                            <Typography as="p" className="text-sm font-medium">
                                {formatTypeName(type)}
                            </Typography>
                            <Chip
                                label={typeArtifacts.length}
                                size="sm"
                                className="ml-2"
                            />
                        </AccordionSummary>
                        <AccordionDetails className="p-0">
                            <List dense disablePadding>
                                {typeArtifacts.map((artifact) => (
                                    <ListItemButton key={artifact.id}>
                                        <ListItemText
                                            primary={artifact.name}
                                            secondary={`From: ${formatTaskName(artifact.taskId)}`}
                                            primaryTypographyProps={{ variant: 'body2' }}
                                            secondaryTypographyProps={{ variant: 'caption' }}
                                        />
                                    </ListItemButton>
                                ))}
                            </List>
                        </AccordionDetails>
                    </Accordion>
                );
            })}
        </Box>
    );
}

// ============================================================================
// Helpers
// ============================================================================

function formatTaskName(taskId: string): string {
    return taskId
        .replace(/-/g, ' ')
        .replace(/\b\w/g, (c) => c.toUpperCase());
}

function formatDomainName(domain: string): string {
    return domain
        .replace(/-/g, ' ')
        .replace(/\b\w/g, (c) => c.toUpperCase());
}

function formatTypeName(type: string): string {
    return type.charAt(0).toUpperCase() + type.slice(1) + 's';
}

export default WorkflowContextPanel;
