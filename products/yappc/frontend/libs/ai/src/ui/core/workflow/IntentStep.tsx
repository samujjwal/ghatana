/**
 * Intent Capture Step Component
 *
 * First step in the AI-powered workflow wizard.
 * Captures user's intent/objective for the workflow.
 *
 * Features:
 * - Natural language input
 * - AI-powered intent parsing
 * - Suggested intents
 * - Intent validation
 *
 * @doc.type component
 * @doc.purpose Intent capture workflow step
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useEffect } from 'react';
import { Box, Surface as Paper, Typography, TextField, Button, Chip, InteractiveList as List, ListItemButton, ListItemIcon, ListItemText, Alert, Spinner as CircularProgress, Fade } from '@ghatana/ui';
import { Lightbulb as IdeaIcon, Hammer as BuildIcon, Bug as BugIcon, Gauge as SpeedIcon, FlaskConical as TestIcon, Rocket as DeployIcon, Sparkles as AIIcon, Check as CheckIcon } from 'lucide-react';

// Hook would be imported from the hooks directory in production
// import { useAICopilot } from '../hooks';

export interface IntentStepProps {
    /** Current intent value */
    value: string;
    /** Callback when intent changes */
    onChange: (intent: string) => void;
    /** Callback when step is complete */
    onComplete: (data: IntentStepData) => void;
    /** Whether step is loading */
    isLoading?: boolean;
    /** Error message */
    error?: string | null;
}

export interface IntentStepData {
    intent: string;
    parsedIntent: ParsedIntent | null;
    confidence: number;
}

export interface ParsedIntent {
    type: 'create' | 'fix' | 'improve' | 'test' | 'deploy' | 'other';
    target: string;
    details: string[];
    suggestedWorkflowType: string;
}

const SUGGESTED_INTENTS = [
    {
        icon: <BuildIcon />,
        label: 'Create a new feature',
        description: 'Build a new component, page, or functionality',
        type: 'create' as const,
    },
    {
        icon: <BugIcon />,
        label: 'Fix a bug',
        description: 'Identify and resolve an issue in the code',
        type: 'fix' as const,
    },
    {
        icon: <SpeedIcon />,
        label: 'Improve performance',
        description: 'Optimize code, reduce load times, or improve efficiency',
        type: 'improve' as const,
    },
    {
        icon: <TestIcon />,
        label: 'Add tests',
        description: 'Write unit tests, integration tests, or E2E tests',
        type: 'test' as const,
    },
    {
        icon: <DeployIcon />,
        label: 'Deploy changes',
        description: 'Prepare and deploy code to production',
        type: 'deploy' as const,
    },
];

/**
 * IntentStep Component
 */
export const IntentStep: React.FC<IntentStepProps> = ({
    value,
    onChange,
    onComplete,
    isLoading = false,
    error = null,
}) => {
    const [localValue, setLocalValue] = useState(value);
    const [parsedIntent, setParsedIntent] = useState<ParsedIntent | null>(null);
    const [isParsing, setIsParsing] = useState(false);
    const [showSuggestions, setShowSuggestions] = useState(true);

    // In production, this would use the useAICopilot hook
    // const { sendMessage } = useAICopilot();

    // Parse intent using AI when user stops typing
    useEffect(() => {
        if (!localValue || localValue.length < 10) {
            setParsedIntent(null);
            return;
        }

        const timeout = setTimeout(async () => {
            setIsParsing(true);
            try {
                // In production, this would call the AI copilot
                // For now, simulate parsing
                const parsed = parseIntentLocally(localValue);
                setParsedIntent(parsed);
            } finally {
                setIsParsing(false);
            }
        }, 500);

        return () => clearTimeout(timeout);
    }, [localValue]);

    const handleInputChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        const newValue = e.target.value;
        setLocalValue(newValue);
        onChange(newValue);
        setShowSuggestions(false);
    }, [onChange]);

    const handleSuggestionClick = useCallback((suggestion: typeof SUGGESTED_INTENTS[0]) => {
        setLocalValue(suggestion.label);
        onChange(suggestion.label);
        setShowSuggestions(false);
        setParsedIntent({
            type: suggestion.type,
            target: '',
            details: [],
            suggestedWorkflowType: mapTypeToWorkflow(suggestion.type),
        });
    }, [onChange]);

    const handleContinue = useCallback(() => {
        onComplete({
            intent: localValue,
            parsedIntent,
            confidence: parsedIntent ? 0.85 : 0.5,
        });
    }, [localValue, parsedIntent, onComplete]);

    const canContinue = localValue.length >= 10 && !isParsing;

    return (
        <Box className="p-6">
            <Typography as="h5" gutterBottom className="flex items-center gap-2">
                <AIIcon tone="primary" />
                What would you like to accomplish?
            </Typography>

            <Typography as="p" className="text-sm" color="text.secondary" className="mb-6">
                Describe your goal in natural language. Our AI will help create a step-by-step plan.
            </Typography>

            {error && (
                <Alert severity="error" className="mb-4">
                    {error}
                </Alert>
            )}

            <TextField
                fullWidth
                multiline
                rows={3}
                value={localValue}
                onChange={handleInputChange}
                placeholder="e.g., Create a user authentication system with email verification..."
                variant="outlined"
                disabled={isLoading}
                className="mb-4"
                InputProps={{
                    endAdornment: isParsing ? (
                        <CircularProgress size={20} className="mr-2" />
                    ) : null,
                }}
            />

            {/* AI-parsed intent feedback */}
            {parsedIntent && (
                <Fade in>
                    <Paper variant="outlined" className="p-4 mb-4 bg-gray-100" >
                        <Typography as="p" className="text-sm font-medium" className="flex items-center gap-2 mb-2">
                            <AIIcon size={16} tone="primary" />
                            AI Understanding
                        </Typography>
                        <Box className="flex flex-wrap gap-2">
                            <Chip
                                size="sm"
                                label={`Type: ${parsedIntent.type}`}
                                tone="primary"
                                variant="outlined"
                            />
                            {parsedIntent.target && (
                                <Chip
                                    size="sm"
                                    label={`Target: ${parsedIntent.target}`}
                                    variant="outlined"
                                />
                            )}
                            <Chip
                                size="sm"
                                label={`Workflow: ${parsedIntent.suggestedWorkflowType}`}
                                tone="secondary"
                                variant="outlined"
                            />
                        </Box>
                    </Paper>
                </Fade>
            )}

            {/* Quick suggestions */}
            {showSuggestions && !localValue && (
                <Box className="mt-4">
                    <Typography as="p" className="text-sm font-medium" gutterBottom color="text.secondary">
                        <IdeaIcon size={16} className="mr-1 align-middle" />
                        Quick Start
                    </Typography>
                    <List dense>
                        {SUGGESTED_INTENTS.map((suggestion, index) => (
                            <ListItemButton
                                key={index}
                                onClick={() => handleSuggestionClick(suggestion)}
                                className="rounded"
                            >
                                <ListItemIcon className="min-w-[40px]">
                                    {suggestion.icon}
                                </ListItemIcon>
                                <ListItemText
                                    primary={suggestion.label}
                                    secondary={suggestion.description}
                                />
                            </ListItemButton>
                        ))}
                    </List>
                </Box>
            )}

            <Box className="mt-6 flex justify-end">
                <Button
                    variant="solid"
                    onClick={handleContinue}
                    disabled={!canContinue || isLoading}
                    endIcon={isLoading ? <CircularProgress size={20} /> : <CheckIcon />}
                >
                    {isLoading ? 'Processing...' : 'Continue'}
                </Button>
            </Box>
        </Box>
    );
};

// Helper functions
function parseIntentLocally(text: string): ParsedIntent {
    const lowerText = text.toLowerCase();

    let type: ParsedIntent['type'] = 'other';
    if (lowerText.includes('create') || lowerText.includes('add') || lowerText.includes('build')) {
        type = 'create';
    } else if (lowerText.includes('fix') || lowerText.includes('bug') || lowerText.includes('error')) {
        type = 'fix';
    } else if (lowerText.includes('improve') || lowerText.includes('optimize') || lowerText.includes('performance')) {
        type = 'improve';
    } else if (lowerText.includes('test') || lowerText.includes('coverage')) {
        type = 'test';
    } else if (lowerText.includes('deploy') || lowerText.includes('release')) {
        type = 'deploy';
    }

    return {
        type,
        target: extractTarget(text),
        details: extractDetails(text),
        suggestedWorkflowType: mapTypeToWorkflow(type),
    };
}

function extractTarget(text: string): string {
    // Simple extraction - in production use NLP
    const words = text.split(' ');
    const targetKeywords = ['for', 'to', 'the', 'a', 'an'];
    for (let i = 0; i < words.length; i++) {
        if (targetKeywords.includes(words[i].toLowerCase()) && words[i + 1]) {
            return words.slice(i + 1, i + 4).join(' ');
        }
    }
    return '';
}

function extractDetails(text: string): string[] {
    const details: string[] = [];
    if (text.includes('with')) {
        const parts = text.split('with');
        if (parts[1]) {
            details.push('with' + parts[1].substring(0, 50));
        }
    }
    return details;
}

function mapTypeToWorkflow(type: ParsedIntent['type']): string {
    const mapping: Record<ParsedIntent['type'], string> = {
        create: 'FEATURE_DEVELOPMENT',
        fix: 'BUG_FIX',
        improve: 'REFACTORING',
        test: 'TESTING',
        deploy: 'DEPLOYMENT',
        other: 'CUSTOM',
    };
    return mapping[type];
}

export default IntentStep;
