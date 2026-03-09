/**
 * Storybook Stories for AITextCompletion Component
 */


import { Box, Typography } from '@ghatana/ui';
import { useState } from 'react';

import { AITextCompletion } from './AITextCompletion';

import type { Meta, StoryObj } from '@storybook/react';
import type { IAIService, CompletionResponse } from '@ghatana/yappc-ai/core';

// Mock AI Service for Storybook
const createMockAIService = (delay = 1000): IAIService => ({
    complete: async ({ messages }) => {
        await new Promise((resolve) => setTimeout(resolve, delay));

        const _userMessage = messages[messages.length - 1].content;
        const completions = [
            'and create amazing experiences for users.',
            'with powerful AI-driven features.',
            'that adapts to user behavior.',
            'using cutting-edge technology.',
        ];

        const completion = completions[Math.floor(Math.random() * completions.length)];

        return {
            content: completion,
            model: 'mock-model',
            finishReason: 'stop',
            usage: { promptTokens: 10, completionTokens: 5, totalTokens: 15 },
        } as CompletionResponse;
    },
    async *stream({ messages }) {
        const completion = 'and deliver exceptional results for everyone involved.';
        const words = completion.split(' ');

        for (const word of words) {
            await new Promise((resolve) => setTimeout(resolve, 100));
            yield { content: `${word} `, model: 'mock-model', done: false };
        }
    },
    embed: async () => ({ embedding: [], model: 'mock-model', usage: { promptTokens: 0, totalTokens: 0 } }),
    getTokenCount: () => 10,
    healthCheck: async () => true,
});

const meta: Meta<typeof AITextCompletion> = {
    title: 'AI/AITextCompletion',
    component: AITextCompletion,
    parameters: {
        layout: 'centered',
        docs: {
            description: {
                component: 'AI-powered text input with inline suggestions. Press Tab to accept, Escape to reject.',
            },
        },
    },
    tags: ['autodocs'],
    argTypes: {
        aiService: { control: false },
        value: { control: 'text' },
        onChange: { action: 'changed' },
        minLength: { control: { type: 'number', min: 1, max: 50 } },
        debounceMs: { control: { type: 'number', min: 0, max: 2000, step: 100 } },
        stream: { control: 'boolean' },
        label: { control: 'text' },
        placeholder: { control: 'text' },
        multiline: { control: 'boolean' },
        disabled: { control: 'boolean' },
    },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof AITextCompletion>;

// Wrapper component to manage state
const AITextCompletionWrapper = (props: Partial<React.ComponentProps<typeof AITextCompletion>>) => {
    const [value, setValue] = useState('');

    return (
        <Box className="w-[600px]">
            <AITextCompletion
                aiService={createMockAIService()}
                value={value}
                onChange={setValue}
                {...props}
            />
            <Box className="mt-4 p-4 rounded bg-gray-100 dark:bg-gray-800">
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                    Current value: {value || '(empty)'}
                </Typography>
            </Box>
        </Box>
    );
};

export const Default: Story = {
    render: () => <AITextCompletionWrapper label="Start typing..." />,
};

export const WithPlaceholder: Story = {
    render: () => (
        <AITextCompletionWrapper
            label="AI-Powered Editor"
            placeholder="Type at least 10 characters to trigger AI suggestions..."
        />
    ),
};

export const Multiline: Story = {
    render: () => (
        <AITextCompletionWrapper
            label="Long-form Content"
            placeholder="Write a story or article..."
            multiline
            rows={8}
        />
    ),
};

export const Streaming: Story = {
    render: () => (
        <AITextCompletionWrapper
            label="Streaming Mode"
            placeholder="Watch the AI stream completions in real-time..."
            stream={true}
        />
    ),
    parameters: {
        docs: {
            description: {
                story: 'Streaming mode shows AI completions as they are generated, word by word.',
            },
        },
    },
};

export const FastResponse: Story = {
    render: () => {
        const [value, setValue] = useState('');
        const fastService = createMockAIService(200); // Very fast response

        return (
            <Box className="w-[600px]">
                <AITextCompletion
                    aiService={fastService}
                    value={value}
                    onChange={setValue}
                    label="Fast AI (200ms)"
                    placeholder="Type to see near-instant suggestions..."
                    debounceMs={100}
                    minLength={5}
                />
            </Box>
        );
    },
};

export const SlowResponse: Story = {
    render: () => {
        const [value, setValue] = useState('');
        const slowService = createMockAIService(3000); // Slow response

        return (
            <Box className="w-[600px]">
                <AITextCompletion
                    aiService={slowService}
                    value={value}
                    onChange={setValue}
                    label="Slow AI (3s)"
                    placeholder="Type to see loading state..."
                    minLength={5}
                />
            </Box>
        );
    },
};

export const ShortDebounce: Story = {
    render: () => (
        <AITextCompletionWrapper
            label="Short Debounce (200ms)"
            placeholder="Suggestions appear quickly..."
            debounceMs={200}
            minLength={5}
        />
    ),
};

export const LongDebounce: Story = {
    render: () => (
        <AITextCompletionWrapper
            label="Long Debounce (1500ms)"
            placeholder="Wait longer for suggestions..."
            debounceMs={1500}
            minLength={5}
        />
    ),
};

export const LowMinLength: Story = {
    render: () => (
        <AITextCompletionWrapper
            label="Triggers Early (3 chars)"
            placeholder="Start typing..."
            minLength={3}
        />
    ),
};

export const HighMinLength: Story = {
    render: () => (
        <AITextCompletionWrapper
            label="Requires More Text (20 chars)"
            placeholder="Type at least 20 characters..."
            minLength={20}
        />
    ),
};

export const CustomPrompt: Story = {
    render: () => (
        <AITextCompletionWrapper
            label="Custom Prompt Prefix"
            placeholder="Write a technical description..."
            promptPrefix="Complete this technical documentation: "
            minLength={10}
        />
    ),
};

export const Disabled: Story = {
    render: () => {
        const [value, setValue] = useState('This field is disabled');

        return (
            <Box className="w-[600px]">
                <AITextCompletion
                    aiService={createMockAIService()}
                    value={value}
                    onChange={setValue}
                    label="Disabled State"
                    disabled
                />
            </Box>
        );
    },
};

export const WithInitialValue: Story = {
    render: () => {
        const [value, setValue] = useState('I am building an application ');

        return (
            <Box className="w-[600px]">
                <AITextCompletion
                    aiService={createMockAIService()}
                    value={value}
                    onChange={setValue}
                    label="With Initial Content"
                    placeholder="Continue writing..."
                    minLength={5}
                />
                <Box className="mt-4">
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        💡 Tip: Add a few more words to trigger AI suggestions
                    </Typography>
                </Box>
            </Box>
        );
    },
};

export const MultipleInstances: Story = {
    render: () => {
        const [value1, setValue1] = useState('');
        const [value2, setValue2] = useState('');
        const [value3, setValue3] = useState('');

        return (
            <Box className="flex flex-col gap-4 w-[600px]">
                <AITextCompletion
                    aiService={createMockAIService()}
                    value={value1}
                    onChange={setValue1}
                    label="First Editor"
                    placeholder="Independent instance 1..."
                    minLength={5}
                />
                <AITextCompletion
                    aiService={createMockAIService()}
                    value={value2}
                    onChange={setValue2}
                    label="Second Editor"
                    placeholder="Independent instance 2..."
                    minLength={5}
                />
                <AITextCompletion
                    aiService={createMockAIService()}
                    value={value3}
                    onChange={setValue3}
                    label="Third Editor"
                    placeholder="Independent instance 3..."
                    minLength={5}
                    multiline
                    rows={3}
                />
            </Box>
        );
    },
    parameters: {
        docs: {
            description: {
                story: 'Multiple independent AI text completion instances on the same page.',
            },
        },
    },
};

export const ErrorState: Story = {
    render: () => {
        const [value, setValue] = useState('');
        const errorService: IAIService = {
            complete: async () => {
                throw new Error('API connection failed');
            },
            async *stream() { throw new Error('Streaming failed'); },
            embed: async () => ({ embedding: [], model: 'mock', usage: { promptTokens: 0, totalTokens: 0 } }),
            getTokenCount: () => 0,
            healthCheck: async () => false,
        };

        return (
            <Box className="w-[600px]">
                <AITextCompletion
                    aiService={errorService}
                    value={value}
                    onChange={setValue}
                    label="Error Simulation"
                    placeholder="Type to trigger an error..."
                    minLength={5}
                />
                <Box className="mt-4">
                    <Typography as="span" className="text-xs text-gray-500" tone="danger">
                        💡 This demo simulates an API error
                    </Typography>
                </Box>
            </Box>
        );
    },
};

export const FullFeature: Story = {
    render: () => {
        const [value, setValue] = useState('');
        const [acceptedCount, setAcceptedCount] = useState(0);

        return (
            <Box className="p-6 rounded-lg w-[700px] bg-gray-50 dark:bg-gray-800">
                <Typography as="h6" gutterBottom>
                    Full-Featured AI Editor
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Type at least 10 characters, wait 500ms, then press Tab to accept suggestions.
                </Typography>

                <AITextCompletion
                    aiService={createMockAIService()}
                    value={value}
                    onChange={(newValue) => {
                        if (newValue.length > value.length + 10) {
                            setAcceptedCount(acceptedCount + 1);
                        }
                        setValue(newValue);
                    }}
                    label="AI-Powered Text Editor"
                    placeholder="Start typing your content here..."
                    multiline
                    rows={6}
                    minLength={10}
                    debounceMs={500}
                    inlineSuggestion
                />

                <Box className="mt-4 p-4 rounded bg-white">
                    <Typography as="p" className="text-sm font-medium" gutterBottom>
                        Statistics
                    </Typography>
                    <Typography as="p" className="text-sm">
                        Characters: {value.length}
                    </Typography>
                    <Typography as="p" className="text-sm">
                        Words: {value.split(/\s+/).filter(Boolean).length}
                    </Typography>
                    <Typography as="p" className="text-sm">
                        Suggestions Accepted: {acceptedCount}
                    </Typography>
                </Box>
            </Box>
        );
    },
    parameters: {
        docs: {
            description: {
                story: 'Complete example with statistics and full feature set.',
            },
        },
    },
};
