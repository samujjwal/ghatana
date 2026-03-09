/**
 * EvidencePanel - Right panel for audit trail and AI suggestions
 *
 * Displays workflow audit history and AI assistance.
 *
 * @doc.type component
 * @doc.purpose Evidence and AI panel
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React from 'react';
import { Box, Typography, Tabs, Tab, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Card, CardContent, CardActions, Button, Chip, Avatar, Divider } from '@ghatana/ui';
import { History as HistoryIcon, Bot as AIIcon, Plus as AddIcon, Pencil as EditIcon, Check as CheckIcon, RotateCcw as ReplayIcon, AlertTriangle as WarningIcon, Info as InfoIcon, Lightbulb as SuggestionIcon } from 'lucide-react';
import { useAtomValue, useSetAtom } from 'jotai';

import {
    workflowAuditAtom,
    currentAISuggestionAtom,
    showAISuggestionsAtom,
} from '../../stores/workflow.store';

import type { WorkflowAuditEntry, AuditAction } from '@ghatana/yappc-types';

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
    AI_SUGGESTION_ACCEPTED: 'AI Suggestion Accepted',
    AI_SUGGESTION_REJECTED: 'AI Suggestion Rejected',
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
    const theme = useTheme();
    const [tabValue, setTabValue] = React.useState(0);

    const auditEntries = useAtomValue(workflowAuditAtom);
    const aiSuggestion = useAtomValue(currentAISuggestionAtom);
    const showAISuggestions = useAtomValue(showAISuggestionsAtom);

    const handleTabChange = (_: React.SyntheticEvent, newValue: number) => {
        setTabValue(newValue);
    };

    return (
        <Box className="h-full flex flex-col">
            {/* Tabs */}
            <Tabs
                value={tabValue}
                onChange={handleTabChange}
                variant="fullWidth"
                className="border-gray-200 dark:border-gray-700 border-b" >
                <Tab
                    icon={<HistoryIcon size={16} />}
                    iconPosition="start"
                    label="Audit"
                    className="min-h-[48px]"
                />
                <Tab
                    icon={<AIIcon size={16} />}
                    iconPosition="start"
                    label="AI"
                    className="min-h-[48px]"
                />
            </Tabs>

            {/* Audit Trail Tab */}
            <TabPanel value={tabValue} index={0}>
                {auditEntries.length === 0 ? (
                    <Box className="text-center py-8">
                        <HistoryIcon className="mb-2 text-5xl text-gray-400 dark:text-gray-600" />
                        <Typography color="text.secondary">
                            No audit entries yet
                        </Typography>
                    </Box>
                ) : (
                    <List className="p-0">
                        {auditEntries.map((entry, index) => {
                            const ActionIcon = ACTION_ICONS[entry.action] || InfoIcon;

                            return (
                                <React.Fragment key={entry.id}>
                                    <ListItem
                                        alignItems="flex-start"
                                        className="px-0 py-3"
                                    >
                                        <ListItemIcon className="min-w-[40px]">
                                            <Avatar
                                                className="w-[28px] h-[28px]" style={{ backgroundColor: theme.palette.action.hover }} >
                                                <ActionIcon
                                                    className="text-gray-500 dark:text-gray-400 text-base"
                                                />
                                            </Avatar>
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={
                                                <Box className="flex items-center gap-2">
                                                    <Typography as="p" className="text-sm" fontWeight={500}>
                                                        {ACTION_LABELS[entry.action]}
                                                    </Typography>
                                                    {entry.step && (
                                                        <Chip
                                                            label={entry.step}
                                                            size="sm"
                                                            className="capitalize h-[18px] text-[10px]"
                                                        />
                                                    )}
                                                </Box>
                                            }
                                            secondary={
                                                <>
                                                    <Typography
                                                        as="span" className="text-xs text-gray-500"
                                                        color="text.secondary"
                                                        component="span"
                                                        className="block mt-1"
                                                    >
                                                        by {entry.userName}
                                                    </Typography>
                                                    <Typography as="span" className="text-xs text-gray-500" color="text.disabled" component="span" className="block">
                                                        {formatTimestamp(entry.timestamp)}
                                                    </Typography>
                                                </>
                                            }
                                        />
                                    </ListItem>
                                    {index < auditEntries.length - 1 && (
                                        <Divider component="li" />
                                    )}
                                </React.Fragment>
                            );
                        })}
                    </List>
                )}
            </TabPanel>

            {/* AI Suggestions Tab */}
            <TabPanel value={tabValue} index={1}>
                {!showAISuggestions ? (
                    <Box className="text-center py-8">
                        <AIIcon className="mb-2 text-5xl text-gray-400 dark:text-gray-600" />
                        <Typography color="text.secondary">
                            AI suggestions are disabled
                        </Typography>
                    </Box>
                ) : aiSuggestion ? (
                    <Card variant="outlined">
                        <CardContent>
                            <Box className="flex items-center gap-2 mb-2">
                                <SuggestionIcon tone="primary" size={16} />
                                <Typography as="p" className="text-sm font-medium" tone="primary">
                                    AI Suggestion
                                </Typography>
                                <Chip
                                    label={`${Math.round(aiSuggestion.confidence * 100)}% confidence`}
                                    size="sm"
                                    variant="outlined"
                                    className="ml-auto"
                                />
                            </Box>
                            <Typography as="p" className="text-sm" color="text.secondary">
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
                    <Box className="text-center py-8">
                        <AIIcon className="mb-2 text-5xl text-gray-400 dark:text-gray-600" />
                        <Typography color="text.secondary" gutterBottom>
                            No suggestions right now
                        </Typography>
                        <Typography as="span" className="text-xs text-gray-500" color="text.disabled">
                            AI will provide suggestions as you work through the workflow
                        </Typography>
                    </Box>
                )}

                {/* AI Capabilities Info */}
                <Box className="mt-6">
                    <Typography as="p" className="text-sm font-medium" gutterBottom>
                        AI Capabilities
                    </Typography>
                    <List dense className="p-0">
                        {[
                            'Intent clarification',
                            'Context retrieval',
                            'Plan generation',
                            'Anomaly detection',
                            'Pattern extraction',
                        ].map((capability) => (
                            <ListItem key={capability} className="px-0 py-1">
                                <ListItemIcon className="min-w-[28px]">
                                    <CheckIcon size={16} tone="success" />
                                </ListItemIcon>
                                <ListItemText
                                    primary={capability}
                                    primaryTypographyProps={{ variant: 'caption' }}
                                />
                            </ListItem>
                        ))}
                    </List>
                </Box>
            </TabPanel>
        </Box>
    );
}

export default EvidencePanel;
