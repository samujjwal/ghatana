/**
 * EvidencePanel - Right panel for audit trail and suggested improvements
 *
 * Displays workflow audit history and guided assistance.
 *
 * @doc.type component
 * @doc.purpose Evidence and suggestions panel
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React from 'react';
import { Box, Typography, Tabs, Tab, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Card, CardContent, CardActions, Button, Chip, Avatar, Divider } from '@ghatana/design-system';
import { History as HistoryIcon, Bot as AIIcon, Plus as AddIcon, Pencil as EditIcon, Check as CheckIcon, RotateCcw as ReplayIcon, AlertTriangle as WarningIcon, Info as InfoIcon, Lightbulb as SuggestionIcon } from 'lucide-react';
import { useAtomValue, useSetAtom } from 'jotai';

import {
    workflowAuditAtom,
    currentAISuggestionAtom,
    showAISuggestionsAtom,
} from '../../stores/workflow.store';

import type { WorkflowAuditEntry } from 'yappc-core/types';
type AuditAction =
    | 'CREATED'
    | 'STEP_STARTED'
    | 'STEP_COMPLETED'
    | 'STEP_REVISITED'
    | 'DATA_UPDATED'
    | 'AI_SUGGESTION_ACCEPTED'
    | 'AI_SUGGESTION_REJECTED'
    | 'STATUS_CHANGED'
    | 'OWNER_CHANGED';


// ============================================================================
// TYPES
// ============================================================================

interface TabPanelProps {
    children?: React.ReactNode;
    index: number;
    value: number;
}

// ============================================================================
// CONSTANTS
// ============================================================================

const ACTION_ICONS: Record<AuditAction, React.ElementType> = {
    CREATED: AddIcon,
    STEP_STARTED: HistoryIcon,
    STEP_COMPLETED: CheckIcon,
    STEP_REVISITED: ReplayIcon,
    DATA_UPDATED: EditIcon,
    AI_SUGGESTION_ACCEPTED: AIIcon,
    AI_SUGGESTION_REJECTED: WarningIcon,
    STATUS_CHANGED: InfoIcon,
    OWNER_CHANGED: InfoIcon,
};

const ACTION_LABELS: Record<AuditAction, string> = {
    CREATED: 'Workflow Created',
    STEP_STARTED: 'Step Started',
    STEP_COMPLETED: 'Step Completed',
    STEP_REVISITED: 'Step Revisited',
    DATA_UPDATED: 'Data Updated',
    AI_SUGGESTION_ACCEPTED: 'Suggestion Accepted',
    AI_SUGGESTION_REJECTED: 'Suggestion Rejected',
    STATUS_CHANGED: 'Status Changed',
    OWNER_CHANGED: 'Owner Changed',
};

// ============================================================================
// HELPER COMPONENTS
// ============================================================================

function TabPanel({ children, value, index }: TabPanelProps) {
    return (
        <Box
            role="tabpanel"
            hidden={value !== index}
            id={`evidence-tabpanel-${index}`}
            aria-labelledby={`evidence-tab-${index}`}
            className="h-full overflow-auto"
        >
            {value === index && <Box className="p-4">{children}</Box>}
        </Box>
    );
}

function formatTimestamp(timestamp: string): string {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString();
}

// ============================================================================
// COMPONENT
// ============================================================================

export function EvidencePanel() {
    const [tabValue, setTabValue] = React.useState<'audit' | 'ai'>('audit');

    const auditEntries = useAtomValue(workflowAuditAtom);
    const aiSuggestion = useAtomValue(currentAISuggestionAtom);
    const showAISuggestions = useAtomValue(showAISuggestionsAtom);

    const handleTabChange = (_: React.SyntheticEvent, newValue: 'audit' | 'ai') => {
        setTabValue(newValue);
    };

    return (
        <Box className="h-full flex flex-col">
            {/* Tabs */}
            <Tabs
                value={tabValue}
                onChange={handleTabChange}
                variant="underline"
                className="border-border dark:border-border border-b" >
                <Tab value="audit" label="Audit" />
                <Tab value="ai" label="Suggestions" />
            </Tabs>

            {/* Audit Trail Tab */}
            {tabValue === 'audit' && (
                auditEntries.length === 0 ? (
                    <Box className="py-8 text-center">
                        <HistoryIcon className="mb-2 text-5xl text-fg-muted dark:text-fg-muted" />
                        <Typography color="text.secondary">
                            No audit entries yet
                        </Typography>
                    </Box>
                ) : (
                    <List className="p-0">
                        {auditEntries.map((entry, index) => {
                            const action = entry.action in ACTION_ICONS
                                ? (entry.action as AuditAction)
                                : 'STATUS_CHANGED';
                            const ActionIcon = ACTION_ICONS[action];

                            return (
                                <React.Fragment key={entry.id}>
                                    <ListItem className="px-0 py-3">
                                        <ListItemIcon className="min-w-[40px]">
                                            <Avatar className="h-[28px] w-[28px] bg-surface-muted dark:bg-surface">
                                                <ActionIcon className="text-base text-fg-muted dark:text-fg-muted" />
                                            </Avatar>
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={
                                                <Box className="flex items-center gap-2">
                                                    <Typography className="text-sm font-medium">
                                                        {ACTION_LABELS[action]}
                                                    </Typography>
                                                </Box>
                                            }
                                            secondary={
                                                <>
                                                    <Typography className="mt-1 block text-xs text-fg-muted" color="text.secondary">
                                                        Audit entry {entry.id}
                                                    </Typography>
                                                    <Typography className="block text-xs text-fg-muted" color="text.disabled">
                                                        {formatTimestamp(entry.at)}
                                                    </Typography>
                                                </>
                                            }
                                        />
                                    </ListItem>
                                    {index < auditEntries.length - 1 && <Divider />}
                                </React.Fragment>
                            );
                        })}
                    </List>
                )
            )}

            {/* AI Suggestions Tab */}
            {tabValue === 'ai' && (
                <>
                    {!showAISuggestions ? (
                        <Box className="py-8 text-center">
                            <AIIcon className="mb-2 text-5xl text-fg-muted dark:text-fg-muted" />
                            <Typography color="text.secondary">
                                Suggestions are disabled
                            </Typography>
                        </Box>
                    ) : aiSuggestion ? (
                        <Card variant="outlined">
                            <CardContent>
                                <Box className="mb-2 flex items-center gap-2">
                                    <SuggestionIcon className="text-info-color" size={16} />
                                    <Typography className="text-sm font-medium text-info-color">
                                        Suggested Improvement
                                    </Typography>
                                    <Chip
                                        label={`${Math.round(aiSuggestion.confidence * 100)}% confidence`}
                                        size="sm"
                                        variant="outlined"
                                        className="ml-auto"
                                    />
                                </Box>
                                <Typography className="text-sm text-fg-muted" color="text.secondary">
                                    {aiSuggestion.content}
                                </Typography>
                            </CardContent>
                            {aiSuggestion.actions && aiSuggestion.actions.length > 0 && (
                                <CardActions>
                                    {aiSuggestion.actions.map((action, index) => (
                                        <Button
                                            key={index}
                                            size="sm"
                                            variant={action.action === 'ACCEPT' ? 'contained' : 'text'}
                                            color={
                                                action.action === 'ACCEPT'
                                                    ? 'primary'
                                                    : action.action === 'REJECT'
                                                        ? 'error'
                                                        : 'inherit'
                                            }
                                        >
                                            {action.label}
                                        </Button>
                                    ))}
                                </CardActions>
                            )}
                        </Card>
                    ) : (
                        <Box className="py-8 text-center">
                            <AIIcon className="mb-2 text-5xl text-fg-muted dark:text-fg-muted" />
                            <Typography color="text.secondary" gutterBottom>
                                No suggestions right now
                            </Typography>
                            <Typography className="text-xs text-fg-muted" color="text.disabled">
                                Suggestions will appear as you progress through the workflow
                            </Typography>
                        </Box>
                    )}

                    <Box className="mt-6">
                        <Typography className="text-sm font-medium" gutterBottom>
                            Suggestion Capabilities
                        </Typography>
                        <List className="p-0">
                            {[
                                'Intent clarification',
                                'Context retrieval',
                                'Plan generation',
                                'Anomaly detection',
                                'Pattern extraction',
                            ].map((capability) => (
                                <ListItem key={capability} className="px-0 py-1">
                                    <ListItemIcon className="min-w-[28px]">
                                        <CheckIcon size={16} className="text-success-color" />
                                    </ListItemIcon>
                                    <ListItemText primary={capability} />
                                </ListItem>
                            ))}
                        </List>
                    </Box>
                </>
            )}
        </Box>
    );
}

export default EvidencePanel;
