/**
 * AI Assistant Modal Component
 * 
 * Keyboard-triggered (⌘K) AI assistant for task guidance, unblocking, and quick actions.
 * 
 * @doc.type component
 * @doc.purpose AI-powered task assistance modal
 * @doc.layer product
 * @doc.pattern Modal
 */

import * as React from 'react';
import { Dialog, DialogTitle, DialogContent, TextField, Box, Typography, Chip, Stack, IconButton, Spinner as CircularProgress, Surface as Paper, Divider } from '@ghatana/ui';
import { X as CloseIcon, Sparkles as AIIcon, Lightbulb as LightbulbIcon, AlertTriangle as WarningIcon, TrendingUp as TrendingUpIcon, CheckCircle as CheckCircleIcon } from 'lucide-react';

export interface AISuggestion {
    id: string;
    type: 'unblock' | 'next-action' | 'insight' | 'optimization';
    title: string;
    description: string;
    priority: 'high' | 'medium' | 'low';
    action?: () => void;
}

export interface AIAssistantModalProps {
    open: boolean;
    onClose: () => void;
    context?: {
        selectedArtifacts?: string[];
        currentPhase?: string;
        persona?: string;
        blockers?: string[];
    };
    onSubmit: (query: string) => Promise<AISuggestion[]>;
}

const SUGGESTION_ICONS = {
    unblock: <WarningIcon size={16} />,
    'next-action': <TrendingUpIcon size={16} />,
    insight: <LightbulbIcon size={16} />,
    optimization: <CheckCircleIcon size={16} />,
};

const SUGGESTION_COLORS = {
    unblock: 'error',
    'next-action': 'primary',
    insight: 'warning',
    optimization: 'success',
} as const;

const QUICK_ACTIONS = [
    { label: 'What should I do next?', query: 'next-action' },
    { label: 'Show blockers', query: 'blockers' },
    { label: 'Suggest optimizations', query: 'optimize' },
    { label: 'Explain dependencies', query: 'explain-deps' },
];

export const AIAssistantModal: React.FC<AIAssistantModalProps> = ({
    open,
    onClose,
    context,
    onSubmit,
}) => {
    const [query, setQuery] = React.useState('');
    const [isLoading, setIsLoading] = React.useState(false);
    const [suggestions, setSuggestions] = React.useState<AISuggestion[]>([]);
    const inputRef = React.useRef<HTMLInputElement>(null);

    // Focus input when modal opens
    React.useEffect(() => {
        if (open && inputRef.current) {
            setTimeout(() => inputRef.current?.focus(), 100);
        }
    }, [open]);

    // Load context-aware suggestions on open
    React.useEffect(() => {
        if (open && context) {
            loadContextSuggestions();
        }
    }, [open, context]);

    const loadContextSuggestions = async () => {
        setIsLoading(true);
        try {
            // Generate context-aware suggestions based on selected artifacts, phase, etc.
            const contextQuery = buildContextQuery(context);
            const results = await onSubmit(contextQuery);
            setSuggestions(results);
        } catch (error) {
            console.error('Failed to load AI suggestions:', error);
        } finally {
            setIsLoading(false);
        }
    };

    const buildContextQuery = (ctx?: AIAssistantModalProps['context']): string => {
        if (!ctx) return 'suggest-next';

        const parts: string[] = [];
        if (ctx.selectedArtifacts?.length) {
            parts.push(`selected:${ctx.selectedArtifacts.join(',')}`);
        }
        if (ctx.currentPhase) {
            parts.push(`phase:${ctx.currentPhase}`);
        }
        if (ctx.persona) {
            parts.push(`persona:${ctx.persona}`);
        }
        if (ctx.blockers?.length) {
            parts.push(`blockers:${ctx.blockers.length}`);
        }

        return parts.length ? parts.join('|') : 'suggest-next';
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!query.trim() || isLoading) return;

        setIsLoading(true);
        try {
            const results = await onSubmit(query);
            setSuggestions(results);
            setQuery('');
        } catch (error) {
            console.error('Failed to get AI response:', error);
        } finally {
            setIsLoading(false);
        }
    };

    const handleQuickAction = async (actionQuery: string) => {
        setIsLoading(true);
        try {
            const results = await onSubmit(actionQuery);
            setSuggestions(results);
        } catch (error) {
            console.error('Failed to execute quick action:', error);
        } finally {
            setIsLoading(false);
        }
    };

    const handleSuggestionClick = (suggestion: AISuggestion) => {
        suggestion.action?.();
        // Keep modal open to show results, or close based on action type
        if (suggestion.type === 'next-action') {
            onClose();
        }
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            size="md"
            fullWidth
            PaperProps={{
                sx: {
                    minHeight: '60vh',
                    maxHeight: '80vh',
                },
            }}
        >
            <DialogTitle>
                <Box className="flex items-center justify-between">
                    <Box className="flex items-center gap-2">
                        <AIIcon tone="primary" />
                        <Typography as="h6">AI Assistant</Typography>
                        <Chip
                            label="⌘K"
                            size="sm"
                            variant="outlined"
                            className="ml-2 text-xs font-mono"
                        />
                    </Box>
                    <IconButton onClick={onClose} size="sm">
                        <CloseIcon />
                    </IconButton>
                </Box>
            </DialogTitle>

            <DialogContent dividers>
                {/* Input Field */}
                <Box component="form" onSubmit={handleSubmit} className="mb-6">
                    <TextField
                        inputRef={inputRef}
                        fullWidth
                        placeholder="Ask anything about your project..."
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        disabled={isLoading}
                        InputProps={{
                            endAdornment: isLoading && <CircularProgress size={20} />,
                            className: 'bg-white dark:bg-gray-900',
                        }}
                    />
                </Box>

                {/* Quick Actions */}
                {!suggestions.length && (
                    <Box className="mb-6">
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mb-2 block">
                            Quick Actions
                        </Typography>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            {QUICK_ACTIONS.map((action) => (
                                <Chip
                                    key={action.query}
                                    label={action.label}
                                    onClick={() => handleQuickAction(action.query)}
                                    disabled={isLoading}
                                    className="cursor-pointer"
                                />
                            ))}
                        </Stack>
                    </Box>
                )}

                {/* Context Info */}
                {context && (
                    <Box className="mb-4 p-3 rounded bg-gray-100 dark:bg-gray-800">
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" gutterBottom>
                            Current Context
                        </Typography>
                        <Stack direction="row" spacing={1} className="mt-1">
                            {context.currentPhase && (
                                <Chip label={`Phase: ${context.currentPhase}`} size="sm" />
                            )}
                            {context.persona && (
                                <Chip label={`Persona: ${context.persona}`} size="sm" />
                            )}
                            {context.selectedArtifacts?.length && (
                                <Chip
                                    label={`${context.selectedArtifacts.length} selected`}
                                    size="sm"
                                    tone="primary"
                                />
                            )}
                            {context.blockers?.length && (
                                <Chip
                                    label={`${context.blockers.length} blockers`}
                                    size="sm"
                                    tone="danger"
                                />
                            )}
                        </Stack>
                    </Box>
                )}

                <Divider className="my-4" />

                {/* Suggestions */}
                {suggestions.length > 0 && (
                    <Box>
                        <Typography as="p" className="text-sm font-medium mb-4" gutterBottom>
                            AI Suggestions
                        </Typography>
                        <Stack spacing={2}>
                            {suggestions.map((suggestion) => (
                                <Paper
                                    key={suggestion.id}
                                    variant="flat"
                                    className="p-4 border border-gray-200 dark:border-gray-700 transition-all duration-200 shadow-sm" style={{ cursor: suggestion.action ? 'pointer' : 'default' }}
                                    onClick={() => suggestion.action && handleSuggestionClick(suggestion)}
                                >
                                    <Box className="flex items-start gap-3">
                                        <Box
                                            style={{ color: SUGGESTION_COLORS[suggestion.type] }}
                                        >
                                            {SUGGESTION_ICONS[suggestion.type]}
                                        </Box>
                                        <Box className="flex-1">
                                            <Box className="flex items-center gap-2 mb-1">
                                                <Typography as="p" className="text-sm font-medium">{suggestion.title}</Typography>
                                                <Chip
                                                    label={suggestion.priority}
                                                    size="sm"
                                                    color={
                                                        suggestion.priority === 'high'
                                                            ? 'error'
                                                            : suggestion.priority === 'medium'
                                                                ? 'warning'
                                                                : 'default'
                                                    }
                                                    className="h-[20px] text-[0.7rem]"
                                                />
                                            </Box>
                                            <Typography as="p" className="text-sm" color="text.secondary">
                                                {suggestion.description}
                                            </Typography>
                                        </Box>
                                    </Box>
                                </Paper>
                            ))}
                        </Stack>
                    </Box>
                )}

                {/* Empty State */}
                {!isLoading && suggestions.length === 0 && query === '' && (
                    <Box className="text-center py-12">
                        <AIIcon className="mb-4 text-5xl text-gray-400 dark:text-gray-600" />
                        <Typography as="p" className="text-sm" color="text.secondary">
                            Ask me anything about your project, or use the quick actions above.
                        </Typography>
                    </Box>
                )}
            </DialogContent>
        </Dialog>
    );
};
