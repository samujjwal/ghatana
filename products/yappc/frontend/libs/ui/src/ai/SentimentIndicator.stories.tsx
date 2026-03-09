/**
 * Storybook Stories for SentimentIndicator Component
 */

import { Box, Typography, TextField, Stack } from '@ghatana/ui';
import { SentimentAnalyzer, type SentimentResult } from '@ghatana/yappc-ai/core';
import { useState } from 'react';

import { SentimentIndicator } from './SentimentIndicator';


import type { Meta, StoryObj } from '@storybook/react';

// Mock sentiment analyzer
const createMockAnalyzer = (delay = 500): SentimentAnalyzer => {
    const analyzer = new SentimentAnalyzer();

    // Override analyze method for demo
    const originalAnalyze = analyzer.analyze.bind(analyzer);
    analyzer.analyze = async (text: string): Promise<SentimentResult> => {
        await new Promise((resolve) => setTimeout(resolve, delay));
        return originalAnalyze(text);
    };

    return analyzer;
};

const meta: Meta<typeof SentimentIndicator> = {
    title: 'AI/SentimentIndicator',
    component: SentimentIndicator,
    parameters: {
        layout: 'centered',
        docs: {
            description: {
                component: 'Real-time sentiment analysis with visual feedback. Supports keyword-based and AI-powered analysis.',
            },
        },
    },
    tags: ['autodocs'],
    argTypes: {
        analyzer: { control: false },
        text: { control: 'text' },
        variant: { control: 'select', options: ['minimal', 'compact', 'detailed'] },
        showHistory: { control: 'boolean' },
        autoAnalyze: { control: 'boolean' },
        minLength: { control: { type: 'number', min: 1, max: 50 } },
    },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof SentimentIndicator>;

export const PositiveSentiment: Story = {
    args: {
        analyzer: createMockAnalyzer(),
        text: 'This is absolutely amazing and wonderful! I love it!',
        variant: 'detailed',
        autoAnalyze: true,
    },
};

export const NegativeSentiment: Story = {
    args: {
        analyzer: createMockAnalyzer(),
        text: 'This is terrible and awful. I hate it.',
        variant: 'detailed',
        autoAnalyze: true,
    },
};

export const NeutralSentiment: Story = {
    args: {
        analyzer: createMockAnalyzer(),
        text: 'The weather is normal today.',
        variant: 'detailed',
        autoAnalyze: true,
    },
};

export const MinimalVariant: Story = {
    args: {
        analyzer: createMockAnalyzer(),
        text: 'Great work on this project!',
        variant: 'minimal',
        autoAnalyze: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Compact emoji-only indicator with tooltip.',
            },
        },
    },
};

export const CompactVariant: Story = {
    args: {
        analyzer: createMockAnalyzer(),
        text: 'This is pretty good overall.',
        variant: 'compact',
        autoAnalyze: true,
    },
};

export const DetailedVariant: Story = {
    args: {
        analyzer: createMockAnalyzer(),
        text: 'Excellent performance with minor issues.',
        variant: 'detailed',
        autoAnalyze: true,
    },
};

export const WithHistory: Story = {
    args: {
        analyzer: createMockAnalyzer(),
        text: 'This feature is fantastic!',
        variant: 'detailed',
        showHistory: true,
        maxHistory: 5,
        autoAnalyze: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Shows historical sentiment analysis. Click the expand button to view history.',
            },
        },
    },
};

export const InteractiveLive: Story = {
    render: () => {
        const [text, setText] = useState('');
        const analyzer = createMockAnalyzer(200);

        return (
            <Box className="p-6 rounded-lg w-[600px] bg-gray-50 dark:bg-gray-800">
                <Typography as="h6" gutterBottom>
                    Live Sentiment Analysis
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Type to see real-time sentiment analysis (min 5 characters)
                </Typography>

                <TextField
                    fullWidth
                    multiline
                    rows={4}
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    placeholder="Type something positive, negative, or neutral..."
                    className="mb-4 bg-white"
                />

                {text.length >= 5 && (
                    <SentimentIndicator
                        analyzer={analyzer}
                        text={text}
                        variant="detailed"
                        autoAnalyze={true}
                        minLength={5}
                    />
                )}

                {text.length > 0 && text.length < 5 && (
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        Type at least 5 characters to trigger analysis...
                    </Typography>
                )}
            </Box>
        );
    },
    parameters: {
        docs: {
            description: {
                story: 'Interactive demo with live text input and real-time sentiment feedback.',
            },
        },
    },
};

export const MultipleTexts: Story = {
    render: () => {
        const analyzer = createMockAnalyzer();

        const samples = [
            'This is absolutely brilliant and innovative!',
            'Pretty standard implementation.',
            'Disappointed with the poor quality.',
        ];

        return (
            <Stack spacing={2} className="w-[400px]">
                {samples.map((text, index) => (
                    <Box key={index} className="p-4 rounded bg-gray-50 dark:bg-gray-800">
                        <Typography as="p" className="text-sm" className="mb-2">
                            "{text}"
                        </Typography>
                        <SentimentIndicator
                            analyzer={analyzer}
                            text={text}
                            variant="compact"
                            autoAnalyze={true}
                        />
                    </Box>
                ))}
            </Stack>
        );
    },
    parameters: {
        docs: {
            description: {
                story: 'Multiple sentiment indicators analyzing different texts.',
            },
        },
    },
};

export const AllVariantsSideBySide: Story = {
    render: () => {
        const analyzer = createMockAnalyzer();
        const text = 'This product exceeded my expectations!';

        return (
            <Box className="p-6 rounded-lg bg-gray-50 dark:bg-gray-800">
                <Typography as="h6" gutterBottom>
                    Sentiment: "{text}"
                </Typography>

                <Stack spacing={3} className="mt-4">
                    <Box>
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            Minimal Variant
                        </Typography>
                        <SentimentIndicator
                            analyzer={analyzer}
                            text={text}
                            variant="minimal"
                            autoAnalyze={true}
                        />
                    </Box>

                    <Box>
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            Compact Variant
                        </Typography>
                        <SentimentIndicator
                            analyzer={analyzer}
                            text={text}
                            variant="compact"
                            autoAnalyze={true}
                        />
                    </Box>

                    <Box>
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            Detailed Variant
                        </Typography>
                        <SentimentIndicator
                            analyzer={analyzer}
                            text={text}
                            variant="detailed"
                            autoAnalyze={true}
                        />
                    </Box>
                </Stack>
            </Box>
        );
    },
};

export const WithHistoryDemo: Story = {
    render: () => {
        const [texts, setTexts] = useState([
            'This is amazing!',
            'Pretty decent work.',
            'Needs significant improvement.',
        ]);
        const [currentText, setCurrentText] = useState('');
        const analyzer = createMockAnalyzer(100);

        const combinedText = [...texts, currentText].filter(Boolean).join(' ');

        return (
            <Box className="p-6 rounded-lg w-[600px] bg-gray-50 dark:bg-gray-800">
                <Typography as="h6" gutterBottom>
                    Sentiment History Demo
                </Typography>

                <TextField
                    fullWidth
                    value={currentText}
                    onChange={(e) => setCurrentText(e.target.value)}
                    onKeyDown={(e) => {
                        if (e.key === 'Enter' && currentText.trim()) {
                            setTexts([...texts, currentText]);
                            setCurrentText('');
                        }
                    }}
                    placeholder="Add sentiment (press Enter)..."
                    className="mb-4 bg-white"
                />

                <SentimentIndicator
                    analyzer={analyzer}
                    text={combinedText}
                    variant="detailed"
                    showHistory={true}
                    maxHistory={10}
                    autoAnalyze={true}
                    minLength={3}
                />

                <Box className="mt-4 p-4 rounded bg-white">
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        💡 Type and press Enter to add to history. Click the expand button to view all.
                    </Typography>
                </Box>
            </Box>
        );
    },
};

export const FastAnalysis: Story = {
    render: () => {
        const [text, setText] = useState('');
        const fastAnalyzer = createMockAnalyzer(100);

        return (
            <Box className="p-6 rounded-lg w-[600px] bg-gray-50 dark:bg-gray-800">
                <Typography as="h6" gutterBottom>
                    Fast Analysis (100ms)
                </Typography>

                <TextField
                    fullWidth
                    multiline
                    rows={3}
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    placeholder="Type for instant sentiment feedback..."
                    className="mb-4 bg-white"
                />

                {text.length >= 5 && (
                    <SentimentIndicator
                        analyzer={fastAnalyzer}
                        text={text}
                        variant="detailed"
                        autoAnalyze={true}
                        minLength={5}
                    />
                )}
            </Box>
        );
    },
};

export const SlowAnalysis: Story = {
    render: () => {
        const [text, setText] = useState('');
        const slowAnalyzer = createMockAnalyzer(2000);

        return (
            <Box className="p-6 rounded-lg w-[600px] bg-gray-50 dark:bg-gray-800">
                <Typography as="h6" gutterBottom>
                    Slow Analysis (2s)
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Shows loading state during analysis
                </Typography>

                <TextField
                    fullWidth
                    multiline
                    rows={3}
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    placeholder="Type to see loading state..."
                    className="mb-4 bg-white"
                />

                {text.length >= 5 && (
                    <SentimentIndicator
                        analyzer={slowAnalyzer}
                        text={text}
                        variant="detailed"
                        autoAnalyze={true}
                        minLength={5}
                    />
                )}
            </Box>
        );
    },
};

export const CustomMinLength: Story = {
    render: () => {
        const [text, setText] = useState('');
        const analyzer = createMockAnalyzer(200);

        return (
            <Box className="p-6 rounded-lg w-[600px] bg-gray-50 dark:bg-gray-800">
                <Typography as="h6" gutterBottom>
                    Custom Min Length (15 characters)
                </Typography>

                <TextField
                    fullWidth
                    multiline
                    rows={3}
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    placeholder="Type at least 15 characters..."
                    helperText={`${text.length}/15 characters`}
                    className="mb-4 bg-white"
                />

                {text.length >= 15 && (
                    <SentimentIndicator
                        analyzer={analyzer}
                        text={text}
                        variant="detailed"
                        autoAnalyze={true}
                        minLength={15}
                    />
                )}
            </Box>
        );
    },
};

export const CommentFeedback: Story = {
    render: () => {
        const [comment, setComment] = useState('');
        const analyzer = createMockAnalyzer(300);

        return (
            <Box className="p-6 rounded-lg w-[600px] bg-gray-50 dark:bg-gray-800">
                <Typography as="h6" gutterBottom>
                    Comment Sentiment Feedback
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Real-world use case: Provide sentiment feedback on user comments
                </Typography>

                <Box className="p-4 rounded bg-white">
                    <TextField
                        fullWidth
                        multiline
                        rows={4}
                        value={comment}
                        onChange={(e) => setComment(e.target.value)}
                        placeholder="Write your comment..."
                        variant="outlined"
                        className="mb-4"
                    />

                    <Box className="flex justify-between items-center">
                        <Box>
                            {comment.length >= 5 && (
                                <SentimentIndicator
                                    analyzer={analyzer}
                                    text={comment}
                                    variant="compact"
                                    autoAnalyze={true}
                                    minLength={5}
                                />
                            )}
                        </Box>

                        <Box
                            className="px-6 py-2 rounded cursor-pointer bg-blue-600 text-white"
                        >
                            Post Comment
                        </Box>
                    </Box>
                </Box>
            </Box>
        );
    },
    parameters: {
        docs: {
            description: {
                story: 'Practical example: sentiment feedback on user-generated content.',
            },
        },
    },
};
