/**
 * @doc.type component
 * @doc.purpose Unified right panel consolidating guidance, suggestions, validation, and generation
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { Box, Tabs, Tab, Surface as Paper, IconButton, Typography, Badge, Divider, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Chip, Button, LinearProgress, Alert, Collapse, Spinner as CircularProgress } from '@ghatana/ui';
import { X as Close, Lightbulb, Sparkles as AutoAwesome, CheckCircle, Code, AlertTriangle as Warning, AlertCircle as ErrorIcon, Info, Play as PlayArrow, RefreshCw as Refresh, ChevronDown as ExpandMore, ChevronUp as ExpandLess } from 'lucide-react';
import { Fade } from '@ghatana/ui';
import { LifecyclePhase } from '../../types/lifecycle';

// Tab panel component
interface TabPanelProps {
    children?: React.ReactNode;
    index: number;
    value: number;
}

function TabPanel({ children, value, index }: TabPanelProps) {
    if (value !== index) return null;
    
    return (
        <Fade in={true} timeout={300}>
            <Box
                role="tabpanel"
                className="p-4 h-full overflow-auto"
            >
                {children}
            </Box>
        </Fade>
    );
}

// Types for panel data
export interface GuidanceItem {
    id: string;
    title: string;
    description: string;
    phase: LifecyclePhase;
    completed?: boolean;
}

export interface Suggestion {
    id: string;
    type: 'improvement' | 'warning' | 'info';
    message: string;
    action?: () => void;
    actionLabel?: string;
}

export interface ValidationIssue {
    id: string;
    severity: 'error' | 'warning' | 'info';
    message: string;
    location?: string;
    autoFix?: () => void;
}

export interface GenerationStatus {
    isGenerating: boolean;
    progress: number;
    currentFile?: string;
    files: Array<{
        name: string;
        status: 'pending' | 'generating' | 'complete' | 'error';
        error?: string;
    }>;
}

interface UnifiedRightPanelProps {
    open: boolean;
    onClose: () => void;
    currentPhase: LifecyclePhase;
    // Guidance
    guidanceItems: GuidanceItem[];
    onGuidanceComplete?: (id: string) => void;
    // Suggestions
    suggestions: Suggestion[];
    onDismissSuggestion?: (id: string) => void;
    // Validation
    validationIssues: ValidationIssue[];
    validationScore?: number;
    onValidate?: () => void;
    isValidating?: boolean;
    // Generation
    generationStatus?: GenerationStatus;
    onGenerate?: () => void;
    onCancelGeneration?: () => void;
}

export function UnifiedRightPanel({
    open,
    onClose,
    currentPhase,
    guidanceItems,
    onGuidanceComplete,
    suggestions,
    onDismissSuggestion,
    validationIssues,
    validationScore,
    onValidate,
    isValidating,
    generationStatus,
    onGenerate,
    onCancelGeneration,
    initialTab = 0,
}: UnifiedRightPanelProps & { initialTab?: number }) {
    const [tabValue, setTabValue] = useState(initialTab);

    // Update tab when initialTab changes (to allow controlling from parent)
    useMemo(() => {
        if (initialTab !== undefined) {
            setTabValue(initialTab);
        }
    }, [initialTab]);

    // Count badges
    const errorCount = validationIssues.filter(i => i.severity === 'error').length;
    const suggestionCount = suggestions.length;
    const incompleteGuidance = guidanceItems.filter(g => !g.completed).length;

    // Filter guidance for current phase
    const phaseGuidance = useMemo(
        () => guidanceItems.filter(g => g.phase === currentPhase),
        [guidanceItems, currentPhase]
    );

    if (!open) return null;

    return (
        <Paper
            elevation={3}
            className="h-full flex flex-col w-[320px] border-gray-200 dark:border-gray-700 border-l" >
            {/* Header */}
            <Box
                className="flex items-center p-2 border-gray-200 dark:border-gray-700 border-b" >
                <Typography as="p" className="text-lg font-medium" fontWeight={600} className="grow pl-2">
                    Assistant
                </Typography>
                <IconButton size="sm" onClick={onClose}>
                    <Close size={16} />
                </IconButton>
            </Box>

            {/* Tabs */}
            <Tabs
                value={tabValue}
                onChange={(_, v) => setTabValue(v)}
                variant="fullWidth"
                className="border-gray-200 dark:border-gray-700 border-b" >
                <Tab
                    icon={
                        <Badge badgeContent={incompleteGuidance} tone="primary" max={9}>
                            <Lightbulb size={16} />
                        </Badge>
                    }
                    label="Guide"
                    className="text-[11px]"
                />
                <Tab
                    icon={
                        <Badge badgeContent={suggestionCount} tone="secondary" max={9}>
                            <AutoAwesome size={16} />
                        </Badge>
                    }
                    label="AI"
                    className="text-[11px]"
                />
                <Tab
                    icon={
                        <Badge badgeContent={errorCount} tone="danger" max={9}>
                            <CheckCircle size={16} />
                        </Badge>
                    }
                    label="Validate"
                    className="text-[11px]"
                />
                <Tab
                    icon={<Code size={16} />}
                    label="Generate"
                    className="text-[11px]"
                />
            </Tabs>

            {/* Tab Panels */}
            <Box className="grow overflow-hidden">
                {/* Guidance Tab */}
                <TabPanel value={tabValue} index={0}>
                    <GuidancePanel
                        items={phaseGuidance}
                        currentPhase={currentPhase}
                        onComplete={onGuidanceComplete}
                    />
                </TabPanel>

                {/* AI Suggestions Tab */}
                <TabPanel value={tabValue} index={1}>
                    <SuggestionsPanel
                        suggestions={suggestions}
                        onDismiss={onDismissSuggestion}
                    />
                </TabPanel>

                {/* Validation Tab */}
                <TabPanel value={tabValue} index={2}>
                    <ValidationPanel
                        issues={validationIssues}
                        score={validationScore}
                        onValidate={onValidate}
                        isValidating={isValidating}
                    />
                </TabPanel>

                {/* Generation Tab */}
                <TabPanel value={tabValue} index={3}>
                    <GenerationPanel
                        status={generationStatus}
                        onGenerate={onGenerate}
                        onCancel={onCancelGeneration}
                    />
                </TabPanel>
            </Box>
        </Paper>
    );
}

// Guidance Panel
function GuidancePanel({
    items,
    currentPhase,
    onComplete,
}: {
    items: GuidanceItem[];
    currentPhase: LifecyclePhase;
    onComplete?: (id: string) => void;
}) {
    const [expandedId, setExpandedId] = useState<string | null>(null);

    return (
        <Box>
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mb-4 block">
                {items.filter(i => !i.completed).length} tips for {currentPhase} phase
            </Typography>

            {items.length === 0 ? (
                <Alert severity="info" className="mt-4">
                    No guidance yet for this phase
                </Alert>
            ) : (
                <List disablePadding>
                    {items.map(item => (
                        <Box key={item.id}>
                            <ListItem
                                className="px-2 py-1 rounded cursor-pointer hover:bg-gray-100" style={{ backgroundColor: item.completed ? 'action.selected' : 'transparent' }}
                                onClick={() => setExpandedId(expandedId === item.id ? null : item.id)}
                            >
                                <ListItemIcon className="min-w-[32px]">
                                    {item.completed ? (
                                        <CheckCircle size={16} tone="success" />
                                    ) : (
                                        <Lightbulb size={16} tone="primary" />
                                    )}
                                </ListItemIcon>
                                <ListItemText
                                    primary={item.title}
                                    primaryTypographyProps={{
                                        variant: 'body2',
                                        fontWeight: item.completed ? 400 : 500,
                                        sx: { textDecoration: item.completed ? 'line-through' : 'none' },
                                    }}
                                />
                                {expandedId === item.id ? <ExpandLess /> : <ExpandMore />}
                            </ListItem>
                            <Collapse in={expandedId === item.id}>
                                <Box className="pl-10 pr-2 pb-2">
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        {item.description}
                                    </Typography>
                                    {!item.completed && onComplete && (
                                        <Button
                                            size="sm"
                                            onClick={() => onComplete(item.id)}
                                            className="mt-2"
                                        >
                                            Mark Complete
                                        </Button>
                                    )}
                                </Box>
                            </Collapse>
                        </Box>
                    ))}
                </List>
            )}
        </Box>
    );
}

// Suggestions Panel
function SuggestionsPanel({
    suggestions,
    onDismiss,
}: {
    suggestions: Suggestion[];
    onDismiss?: (id: string) => void;
}) {
    const getIcon = (type: Suggestion['type']) => {
        switch (type) {
            case 'improvement':
                return <AutoAwesome size={16} tone="primary" />;
            case 'warning':
                return <Warning size={16} tone="warning" />;
            case 'info':
                return <Info size={16} tone="info" />;
        }
    };

    return (
        <Box>
            {suggestions.length === 0 ? (
                <Alert severity="success" className="mt-4">
                    No suggestions yet. Looking good.
                </Alert>
            ) : (
                <List disablePadding>
                    {suggestions.map(suggestion => (
                        <ListItem
                            key={suggestion.id}
                            className="px-2 py-2 mb-2 rounded flex-col items-start bg-gray-100" >
                            <Box className="flex items-start w-full gap-2">
                                {getIcon(suggestion.type)}
                                <Typography as="p" className="text-sm" className="grow">
                                    {suggestion.message}
                                </Typography>
                                {onDismiss && (
                                    <IconButton size="sm" onClick={() => onDismiss(suggestion.id)}>
                                        <Close className="text-sm" />
                                    </IconButton>
                                )}
                            </Box>
                            {suggestion.action && (
                                <Button
                                    size="sm"
                                    onClick={suggestion.action}
                                    className="mt-2 ml-6"
                                >
                                    {suggestion.actionLabel || 'Apply'}
                                </Button>
                            )}
                        </ListItem>
                    ))}
                </List>
            )}
        </Box>
    );
}

// Validation Panel
function ValidationPanel({
    issues,
    score,
    onValidate,
    isValidating,
}: {
    issues: ValidationIssue[];
    score?: number;
    onValidate?: () => void;
    isValidating?: boolean;
}) {
    const getIcon = (severity: ValidationIssue['severity']) => {
        switch (severity) {
            case 'error':
                return <ErrorIcon size={16} tone="danger" />;
            case 'warning':
                return <Warning size={16} tone="warning" />;
            case 'info':
                return <Info size={16} tone="info" />;
        }
    };

    const errorCount = issues.filter(i => i.severity === 'error').length;
    const warningCount = issues.filter(i => i.severity === 'warning').length;

    return (
        <Box>
            {/* Score indicator */}
            {score !== undefined && (
                <Box className="mb-4 text-center">
                    <Typography as="h3" fontWeight={700} color={score >= 80 ? 'success.main' : score >= 50 ? 'warning.main' : 'error.main'}>
                        {score}
                    </Typography>
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        Validation Score
                    </Typography>
                </Box>
            )}

            {/* Summary chips */}
            <Box className="flex gap-2 mb-4">
                <Chip
                    icon={<ErrorIcon />}
                    label={`${errorCount} errors`}
                    size="sm"
                    color={errorCount > 0 ? 'error' : 'default'}
                />
                <Chip
                    icon={<Warning />}
                    label={`${warningCount} warnings`}
                    size="sm"
                    color={warningCount > 0 ? 'warning' : 'default'}
                />
            </Box>

            {/* Validate button */}
            <Button
                fullWidth
                variant="solid"
                onClick={onValidate}
                disabled={isValidating}
                startIcon={isValidating ? <Refresh className="spin" /> : <CheckCircle />}
                className="mb-4"
            >
                {isValidating ? 'Validating...' : 'Run Validation'}
            </Button>

            <Divider className="mb-4" />

            {/* Issues list */}
            {issues.length === 0 ? (
                <Alert severity="success">No validation issues found.</Alert>
            ) : (
                <List disablePadding>
                    {issues.map(issue => (
                        <ListItem
                            key={issue.id}
                            className="px-2 py-2 mb-2 rounded flex-col items-start bg-gray-100" >
                            <Box className="flex items-start w-full gap-2">
                                {getIcon(issue.severity)}
                                <Box className="grow">
                                    <Typography as="p" className="text-sm">{issue.message}</Typography>
                                    {issue.location && (
                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                            {issue.location}
                                        </Typography>
                                    )}
                                </Box>
                            </Box>
                            {issue.autoFix && (
                                <Button size="sm" onClick={issue.autoFix} className="mt-2 ml-6">
                                    Auto-fix
                                </Button>
                            )}
                        </ListItem>
                    ))}
                </List>
            )}
        </Box>
    );
}

// Generation Panel
function GenerationPanel({
    status,
    onGenerate,
    onCancel,
}: {
    status?: GenerationStatus;
    onGenerate?: () => void;
    onCancel?: () => void;
}) {
    const isGenerating = status?.isGenerating ?? false;

    return (
        <Box>
            {/* Generate button */}
            {!isGenerating && (
                <Button
                    fullWidth
                    variant="solid"
                    tone="secondary"
                    onClick={onGenerate}
                    startIcon={<PlayArrow />}
                    className="mb-4"
                >
                    Generate Code
                </Button>
            )}

            {/* Generation progress */}
            {isGenerating && status && (
                <>
                    <Box className="mb-4">
                        <Box className="flex justify-between mb-2">
                            <Typography as="p" className="text-sm">Generating...</Typography>
                            <Typography as="p" className="text-sm">{status.progress}%</Typography>
                        </Box>
                        <LinearProgress variant="determinate" value={status.progress} />
                        {status.currentFile && (
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-1 block">
                                {status.currentFile}
                            </Typography>
                        )}
                    </Box>

                    <Button
                        fullWidth
                        variant="outlined"
                        tone="danger"
                        onClick={onCancel}
                        className="mb-4"
                    >
                        Cancel
                    </Button>
                </>
            )}

            <Divider className="mb-4" />

            {/* Files list */}
            <Typography as="p" className="text-sm font-medium" className="mb-2">
                Generated Files
            </Typography>

            {status?.files?.length === 0 && !isGenerating && (
                <Alert severity="info">No files generated yet</Alert>
            )}

            {status?.files && status.files.length > 0 && (
                <List disablePadding>
                    {status.files.map(file => (
                        <ListItem
                            key={file.name}
                            className="px-2 py-1 rounded"
                        >
                            <ListItemIcon className="min-w-[28px]">
                                {file.status === 'complete' && <CheckCircle size={16} tone="success" />}
                                {file.status === 'generating' && (
                                    <CircularProgress size={16} />
                                )}
                                {file.status === 'pending' && <Code size={16} color="disabled" />}
                                {file.status === 'error' && <ErrorIcon size={16} tone="danger" />}
                            </ListItemIcon>
                            <ListItemText
                                primary={file.name}
                                primaryTypographyProps={{ variant: 'body2' }}
                                secondary={file.error}
                                secondaryTypographyProps={{ color: 'error' }}
                            />
                        </ListItem>
                    ))}
                </List>
            )}
        </Box>
    );
}

export default UnifiedRightPanel;
