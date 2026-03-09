/**
 * Storybook Stories for SmartSuggestions Component
 */

import { Box, Typography, TextField } from '@ghatana/ui';
import { useState } from 'react';

import { SmartSuggestions } from './SmartSuggestions';

import type { Meta, StoryObj } from '@storybook/react';
import type { IAIService, CompletionResponse, Suggestion } from '@ghatana/yappc-ai/core';

// Mock AI Service
const createMockAIService = (delay = 800): IAIService => ({
    complete: async ({ messages }) => {
        await new Promise((resolve) => setTimeout(resolve, delay));

        const prompts = {
            completion: '1. and make it production-ready\n2. with comprehensive error handling\n3. using best practices',
            edit: '1. Refactor for better readability\n2. Add type safety improvements\n3. Optimize performance',
            explain: '1. This code handles user authentication\n2. It validates credentials securely\n3. Returns JWT tokens on success',
            improve: '1. Add input validation\n2. Implement error boundaries\n3. Enhance accessibility features',
        };

        const content = messages[0].content.toLowerCase();
        let response = prompts.completion;

        if (content.includes('edit')) response = prompts.edit;
        else if (content.includes('explain')) response = prompts.explain;
        else if (content.includes('improve')) response = prompts.improve;

        return {
            content: response,
            model: 'mock-model',
            finishReason: 'stop',
            usage: { promptTokens: 20, completionTokens: 30, totalTokens: 50 },
        } as CompletionResponse;
    },
    async *stream() { },
    embed: async () => ({ embedding: [], model: 'mock', usage: { promptTokens: 0, totalTokens: 0 } }),
    getTokenCount: () => 20,
    healthCheck: async () => true,
});

const meta: Meta<typeof SmartSuggestions> = {
    title: 'AI/SmartSuggestions',
    component: SmartSuggestions,
    parameters: {
        layout: 'centered',
        docs: {
            description: {
                component: 'Context-aware AI suggestions with keyboard navigation. Use arrow keys to navigate, Enter to select.',
            },
        },
    },
    tags: ['autodocs'],
    argTypes: {
        aiService: { control: false },
        context: { control: 'text' },
        selection: { control: 'text' },
        onSelect: { action: 'selected' },
        onDismiss: { action: 'dismissed' },
        suggestionTypes: { control: 'check', options: ['completion', 'edit', 'explain', 'improve'] },
        maxSuggestionsPerType: { control: { type: 'number', min: 1, max: 10 } },
        showConfidence: { control: 'boolean' },
        autoGenerate: { control: 'boolean' },
    },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof SmartSuggestions>;

export const Default: Story = {
    args: {
        aiService: createMockAIService(),
        context: 'User is writing a React component',
        onSelect: (suggestion: Suggestion) => console.log('Selected:', suggestion),
        autoGenerate: true,
    },
};

export const AllSuggestionTypes: Story = {
    args: {
        aiService: createMockAIService(),
        context: 'Building a user authentication system',
        suggestionTypes: ['completion', 'edit', 'explain', 'improve'],
        onSelect: (suggestion: Suggestion) => console.log('Selected:', suggestion),
        autoGenerate: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Shows all four types of suggestions: completion, edit, explain, and improve.',
            },
        },
    },
};

export const CompletionOnly: Story = {
    args: {
        aiService: createMockAIService(),
        context: 'const handleSubmit = async (data) => {',
        suggestionTypes: ['completion'],
        onSelect: (suggestion: Suggestion) => console.log('Selected:', suggestion),
        autoGenerate: true,
    },
};

export const EditSuggestions: Story = {
    args: {
        aiService: createMockAIService(),
        context: 'Refactoring legacy code',
        selection: 'function getUserData() { return fetch("/api/user").then(r => r.json()) }',
        suggestionTypes: ['edit'],
        onSelect: (suggestion: Suggestion) => console.log('Selected:', suggestion),
        autoGenerate: true,
    },
};

export const ExplainCode: Story = {
    args: {
        aiService: createMockAIService(),
        context: 'Complex algorithm',
        selection: 'const memoized = useMemo(() => expensiveComputation(data), [data]);',
        suggestionTypes: ['explain'],
        onSelect: (suggestion: Suggestion) => console.log('Selected:', suggestion),
        autoGenerate: true,
    },
};

export const WithDismiss: Story = {
    args: {
        aiService: createMockAIService(),
        context: 'Writing documentation',
        onSelect: (suggestion: Suggestion) => console.log('Selected:', suggestion),
        onDismiss: () => console.log('Dismissed'),
        autoGenerate: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Press Escape or click the X button to dismiss.',
            },
        },
    },
};

export const NoConfidenceScores: Story = {
    args: {
        aiService: createMockAIService(),
        context: 'Clean UI without confidence indicators',
        showConfidence: false,
        onSelect: (suggestion: Suggestion) => console.log('Selected:', suggestion),
        autoGenerate: true,
    },
};

export const HighConfidenceOnly: Story = {
    args: {
        aiService: createMockAIService(),
        context: 'Only show high-confidence suggestions',
        minConfidence: 0.8,
        onSelect: (suggestion: Suggestion) => console.log('Selected:', suggestion),
        autoGenerate: true,
    },
};

export const LimitedSuggestions: Story = {
    args: {
        aiService: createMockAIService(),
        context: 'Show fewer suggestions per category',
        maxSuggestionsPerType: 2,
        suggestionTypes: ['completion', 'improve'],
        onSelect: (suggestion: Suggestion) => console.log('Selected:', suggestion),
        autoGenerate: true,
    },
};

export const ManualTrigger: Story = {
    render: () => {
        const [showSuggestions, setShowSuggestions] = useState(false);

        return (
            <Box className="w-[500px]">
                <Typography as="h6" gutterBottom>
                    Manual Trigger Demo
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Click the button to manually generate suggestions.
                </Typography>

                {!showSuggestions ? (
                    <Box
                        onClick={() => setShowSuggestions(true)}
                        className="p-6 border-[2px] border-blue-600 rounded-lg cursor-pointer text-center hover:bg-gray-100 hover:dark:bg-gray-800"
                    >
                        <Typography>Click to Generate Suggestions</Typography>
                    </Box>
                ) : (
                    <SmartSuggestions
                        aiService={createMockAIService()}
                        context="Building a feature"
                        onSelect={(s) => {
                            console.log('Selected:', s);
                            setShowSuggestions(false);
                        }}
                        onDismiss={() => setShowSuggestions(false)}
                        autoGenerate={true}
                    />
                )}
            </Box>
        );
    },
};

export const InteractiveDemo: Story = {
    render: () => {
        const [context, setContext] = useState('I want to build a');
        const [selectedSuggestion, setSelectedSuggestion] = useState<Suggestion | null>(null);
        const [showSuggestions, setShowSuggestions] = useState(false);

        return (
            <Box className="p-6 rounded-lg w-[700px] bg-gray-50 dark:bg-gray-800">
                <Typography as="h6" gutterBottom>
                    Interactive Smart Suggestions
                </Typography>

                <Box className="mb-6">
                    <TextField
                        fullWidth
                        label="Context"
                        value={context}
                        onChange={(e) => setContext(e.target.value)}
                        helperText="Type your context and click 'Get Suggestions'"
                        className="mb-4 bg-white"
                    />

                    <Box
                        onClick={() => setShowSuggestions(true)}
                        className="p-4 bg-blue-600 text-white rounded text-center cursor-pointer hover:bg-blue-800"
                    >
                        Get AI Suggestions
                    </Box>
                </Box>

                {showSuggestions && (
                    <Box className="mb-6">
                        <SmartSuggestions
                            aiService={createMockAIService()}
                            context={context}
                            onSelect={(suggestion) => {
                                setSelectedSuggestion(suggestion);
                                setShowSuggestions(false);
                            }}
                            onDismiss={() => setShowSuggestions(false)}
                            suggestionTypes={['completion', 'improve']}
                            autoGenerate={true}
                        />
                    </Box>
                )}

                {selectedSuggestion && (
                    <Box className="p-4 rounded bg-green-100 dark:bg-green-900/30">
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            Selected Suggestion ({selectedSuggestion.type}):
                        </Typography>
                        <Typography as="p" className="text-sm">
                            {selectedSuggestion.text}
                        </Typography>
                        {selectedSuggestion.confidence && (
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-2 block">
                                Confidence: {Math.round(selectedSuggestion.confidence * 100)}%
                            </Typography>
                        )}
                    </Box>
                )}
            </Box>
        );
    },
    parameters: {
        docs: {
            description: {
                story: 'Full interactive demo with context input and selection feedback.',
            },
        },
    },
};

export const FastResponse: Story = {
    args: {
        aiService: createMockAIService(200),
        context: 'Fast AI response (200ms)',
        onSelect: (suggestion: Suggestion) => console.log('Selected:', suggestion),
        autoGenerate: true,
    },
};

export const SlowResponse: Story = {
    args: {
        aiService: createMockAIService(3000),
        context: 'Slow AI response (3s) - shows loading state',
        onSelect: (suggestion: Suggestion) => console.log('Selected:', suggestion),
        autoGenerate: true,
    },
};

export const ErrorState: Story = {
    render: () => {
        const errorService: IAIService = {
            complete: async () => {
                throw new Error('AI service unavailable');
            },
            async *stream() { },
            embed: async () => ({ embedding: [], model: '', usage: { promptTokens: 0, totalTokens: 0 } }),
            getTokenCount: () => 0,
            healthCheck: async () => false,
        };

        return (
            <SmartSuggestions
                aiService={errorService}
                context="Trigger error"
                onSelect={(s) => console.log('Selected:', s)}
                autoGenerate={true}
            />
        );
    },
    parameters: {
        docs: {
            description: {
                story: 'Shows error state when AI service fails.',
            },
        },
    },
};

export const EmptyContext: Story = {
    args: {
        aiService: createMockAIService(),
        context: '',
        selection: '',
        onSelect: (suggestion: Suggestion) => console.log('Selected:', suggestion),
        autoGenerate: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Shows error message when context is empty.',
            },
        },
    },
};

export const CustomPosition: Story = {
    render: () => (
        <Box className="flex gap-4 flex-wrap">
            <SmartSuggestions
                aiService={createMockAIService()}
                context="Above position"
                position="above"
                onSelect={(s) => console.log('Selected:', s)}
                autoGenerate={true}
            />
            <SmartSuggestions
                aiService={createMockAIService()}
                context="Below position (default)"
                position="below"
                onSelect={(s) => console.log('Selected:', s)}
                autoGenerate={true}
            />
        </Box>
    ),
};
