/**
 * Agent Conversations Component
 *
 * Component for viewing and monitoring agent conversations with message history,
 * sentiment analysis, and quality scoring.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Grid,
    Card,
    Box,
    Chip,
    Tabs,
    Tab,
    Button,
    Typography,
    Stack,
    Table,
    TableHead,
    TableBody,
    TableRow,
    TableCell,
} from '@ghatana/design-system';
import { KpiCard } from '@/shared/components/org';

/**
 * Conversation metrics
 */
export interface ConversationMetrics {
    totalConversations: number;
    activeConversations: number;
    averageQualityScore: number; // 0-100
    positiveSentiment: number; // Percentage
}

/**
 * Conversation summary
 */
export interface ConversationSummary {
    id: string;
    agentName: string;
    userName: string;
    startTime: string;
    endTime?: string;
    duration: number; // minutes
    messageCount: number;
    status: 'active' | 'completed' | 'escalated' | 'abandoned';
    sentiment: 'positive' | 'neutral' | 'negative';
    qualityScore: number; // 0-100
    topics: string[];
}

/**
 * Message detail
 */
export interface MessageDetail {
    id: string;
    sender: 'agent' | 'user';
    content: string;
    timestamp: string;
    sentiment?: 'positive' | 'neutral' | 'negative';
    confidence?: number; // 0-1
}

/**
 * Sentiment analysis
 */
export interface SentimentAnalysis {
    id: string;
    period: string;
    positive: number;
    neutral: number;
    negative: number;
    totalConversations: number;
    averageScore: number; // -1 to 1
}

/**
 * Quality metric
 */
export interface QualityMetric {
    id: string;
    conversationId: string;
    userName: string;
    agentName: string;
    timestamp: string;
    overallScore: number; // 0-100
    accuracyScore: number; // 0-100
    responseTimeScore: number; // 0-100
    satisfactionScore: number; // 0-100
    status: 'excellent' | 'good' | 'fair' | 'poor';
}

/**
 * Agent Conversations Props
 */
export interface AgentConversationsProps {
    /** Conversation metrics */
    metrics: ConversationMetrics;
    /** Conversation summaries */
    conversations: ConversationSummary[];
    /** Message details */
    messages: MessageDetail[];
    /** Sentiment analysis */
    sentimentAnalysis: SentimentAnalysis[];
    /** Quality metrics */
    qualityMetrics: QualityMetric[];
    /** Callback when conversation is clicked */
    onConversationClick?: (conversationId: string) => void;
    /** Callback when message is clicked */
    onMessageClick?: (messageId: string) => void;
    /** Callback when sentiment is clicked */
    onSentimentClick?: (sentimentId: string) => void;
    /** Callback when quality metric is clicked */
    onQualityClick?: (qualityId: string) => void;
    /** Callback when export is clicked */
    onExportConversations?: () => void;
}

/**
 * Agent Conversations Component
 *
 * Provides comprehensive conversation monitoring with:
 * - Conversation summaries (status, sentiment, quality)
 * - Message threads (agent/user messages)
 * - Sentiment analysis over time
 * - Quality scoring metrics
 * - Tab-based navigation (Conversations, Messages, Sentiment, Quality)
 *
 * Reuses @ghatana/design-system components and shared org KPI cards:
 * - KpiCard (metrics)
 * - Grid (responsive layouts)
 * - Card (conversation cards, sentiment cards)
 * - Table (messages, quality metrics)
 * - Chip (status, sentiment, topic indicators)
 *
 * @example
 * ```tsx
 * <AgentConversations
 *   metrics={conversationMetrics}
 *   conversations={conversationList}
 *   messages={messageList}
 *   sentimentAnalysis={sentiment}
 *   qualityMetrics={quality}
 *   onConversationClick={(id) => navigate(`/conversations/${id}`)}
 * />
 * ```
 */
export const AgentConversations: React.FC<AgentConversationsProps> = ({
    metrics,
    conversations,
    messages,
    sentimentAnalysis,
    qualityMetrics,
    onConversationClick,
    onMessageClick,
    onSentimentClick,
    onQualityClick,
    onExportConversations,
}) => {
    const [selectedTab, setSelectedTab] = useState<'conversations' | 'messages' | 'sentiment' | 'quality'>('conversations');
    const [conversationFilter, setConversationFilter] = useState<'all' | 'active' | 'completed' | 'escalated' | 'abandoned'>('all');

    // Get status color
    const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'active':
            case 'completed':
            case 'excellent':
                return 'success';
            case 'good':
                return 'warning';
            case 'escalated':
            case 'fair':
                return 'warning';
            case 'abandoned':
            case 'poor':
                return 'error';
            default:
                return 'default';
        }
    };

    // Get sentiment color
    const getSentimentColor = (sentiment: 'positive' | 'neutral' | 'negative'): 'success' | 'default' | 'error' => {
        switch (sentiment) {
            case 'positive':
                return 'success';
            case 'neutral':
                return 'default';
            case 'negative':
                return 'error';
        }
    };

    // Format date
    const formatDate = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
    };

    // Format time only
    const formatTime = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleTimeString();
    };

    // Filter conversations
    const filteredConversations = conversationFilter === 'all' ? conversations : conversations.filter((c) => c.status === conversationFilter);

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Agent Conversations
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Conversation history, sentiment analysis, and quality monitoring
                    </Typography>
                </Box>
                {onExportConversations && (
                    <Button variant="primary" size="md" onClick={onExportConversations}>
                        Export Conversations
                    </Button>
                )}
            </Box>

            {/* Metrics */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="Total Conversations"
                    value={metrics.totalConversations.toLocaleString()}
                    description={`${metrics.activeConversations} active`}
                    status="healthy"
                />

                <KpiCard
                    label="Avg Quality Score"
                    value={metrics.averageQualityScore.toFixed(0)}
                    description="Out of 100"
                    status={metrics.averageQualityScore >= 80 ? 'healthy' : metrics.averageQualityScore >= 60 ? 'warning' : 'error'}
                />

                <KpiCard
                    label="Positive Sentiment"
                    value={`${metrics.positiveSentiment}%`}
                    description="Of all conversations"
                    status={metrics.positiveSentiment >= 70 ? 'healthy' : metrics.positiveSentiment >= 50 ? 'warning' : 'error'}
                />

                <KpiCard
                    label="Completion Rate"
                    value={`${Math.round((conversations.filter((c) => c.status === 'completed').length / conversations.length) * 100)}%`}
                    description="Successfully completed"
                    status="healthy"
                />
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`Conversations (${conversations.length})`} value="conversations" />
                    <Tab label={`Messages (${messages.length})`} value="messages" />
                    <Tab label={`Sentiment (${sentimentAnalysis.length})`} value="sentiment" />
                    <Tab label={`Quality (${qualityMetrics.length})`} value="quality" />
                </Tabs>

                {/* Conversations Tab */}
                {selectedTab === 'conversations' && (
                    <Box className="p-4">
                        {/* Conversation Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label={`All (${conversations.length})`} color={conversationFilter === 'all' ? 'error' : 'default'} onClick={() => setConversationFilter('all')} />
                            <Chip
                                label={`Active (${conversations.filter((c) => c.status === 'active').length})`}
                                color={conversationFilter === 'active' ? 'success' : 'default'}
                                onClick={() => setConversationFilter('active')}
                            />
                            <Chip
                                label={`Completed (${conversations.filter((c) => c.status === 'completed').length})`}
                                color={conversationFilter === 'completed' ? 'success' : 'default'}
                                onClick={() => setConversationFilter('completed')}
                            />
                            <Chip
                                label={`Escalated (${conversations.filter((c) => c.status === 'escalated').length})`}
                                color={conversationFilter === 'escalated' ? 'warning' : 'default'}
                                onClick={() => setConversationFilter('escalated')}
                            />
                            <Chip
                                label={`Abandoned (${conversations.filter((c) => c.status === 'abandoned').length})`}
                                color={conversationFilter === 'abandoned' ? 'error' : 'default'}
                                onClick={() => setConversationFilter('abandoned')}
                            />
                        </Stack>

                        {/* Conversation List */}
                        <Stack spacing={3}>
                            {filteredConversations.map((conv) => (
                                <Card key={conv.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onConversationClick?.(conv.id)}>
                                    <Box className="p-4">
                                        {/* Conversation Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {conv.userName} ↔ {conv.agentName}
                                                    </Typography>
                                                    <Chip label={conv.status} color={getStatusColor(conv.status)} size="small" />
                                                    <Chip label={conv.sentiment} color={getSentimentColor(conv.sentiment)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {formatDate(conv.startTime)} • {conv.duration} min • {conv.messageCount} messages
                                                </Typography>
                                            </Box>
                                            <Box className="text-right">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Quality Score
                                                </Typography>
                                                <Typography
                                                    variant="h6"
                                                    className={
                                                        conv.qualityScore >= 80
                                                            ? 'text-green-600'
                                                            : conv.qualityScore >= 60
                                                                ? 'text-orange-600'
                                                                : 'text-red-600'
                                                    }
                                                >
                                                    {conv.qualityScore}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Topics */}
                                        {conv.topics.length > 0 && (
                                            <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 mb-2 block">
                                                    Topics
                                                </Typography>
                                                <Stack direction="row" spacing={1} className="flex-wrap">
                                                    {conv.topics.slice(0, 4).map((topic, i) => (
                                                        <Chip key={i} label={topic} size="small" />
                                                    ))}
                                                    {conv.topics.length > 4 && <Chip label={`+${conv.topics.length - 4} more`} size="small" />}
                                                </Stack>
                                            </Box>
                                        )}
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}

                {/* Messages Tab */}
                {selectedTab === 'messages' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Message History
                        </Typography>

                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Timestamp</TableCell>
                                    <TableCell>Sender</TableCell>
                                    <TableCell>Message</TableCell>
                                    <TableCell>Sentiment</TableCell>
                                    <TableCell>Confidence</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {messages.map((message) => (
                                    <TableRow
                                        key={message.id}
                                        className="cursor-pointer hover:bg-slate-50 dark:hover:bg-neutral-800"
                                        onClick={() => onMessageClick?.(message.id)}
                                    >
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {formatTime(message.timestamp)}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={message.sender} color={message.sender === 'agent' ? 'error' : 'default'} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 line-clamp-2">
                                                {message.content}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            {message.sentiment && <Chip label={message.sentiment} color={getSentimentColor(message.sentiment)} size="small" />}
                                        </TableCell>
                                        <TableCell>
                                            {message.confidence && (
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {(message.confidence * 100).toFixed(0)}%
                                                </Typography>
                                            )}
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </Box>
                )}

                {/* Sentiment Tab */}
                {selectedTab === 'sentiment' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Sentiment Analysis Over Time
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {sentimentAnalysis.map((sentiment) => (
                                <Card key={sentiment.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onSentimentClick?.(sentiment.id)}>
                                    <Box className="p-4">
                                        {/* Sentiment Header */}
                                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-3">
                                            {sentiment.period}
                                        </Typography>

                                        {/* Sentiment Breakdown */}
                                        <Grid columns={3} gap={3} className="mb-3">
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Positive
                                                </Typography>
                                                <Typography variant="h5" className="text-green-600">
                                                    {sentiment.positive}
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Neutral
                                                </Typography>
                                                <Typography variant="h5" className="text-slate-900 dark:text-neutral-100">
                                                    {sentiment.neutral}
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Negative
                                                </Typography>
                                                <Typography variant="h5" className="text-red-600">
                                                    {sentiment.negative}
                                                </Typography>
                                            </Box>
                                        </Grid>

                                        {/* Sentiment Summary */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={2} gap={2}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Total Conversations
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {sentiment.totalConversations.toLocaleString()}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Average Score
                                                    </Typography>
                                                    <Typography
                                                        variant="body2"
                                                        className={sentiment.averageScore >= 0.5 ? 'text-green-600' : sentiment.averageScore >= 0 ? 'text-slate-900 dark:text-neutral-100' : 'text-red-600'}
                                                    >
                                                        {sentiment.averageScore.toFixed(2)}
                                                    </Typography>
                                                </Box>
                                            </Grid>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* Quality Tab */}
                {selectedTab === 'quality' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Quality Metrics
                        </Typography>

                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Timestamp</TableCell>
                                    <TableCell>User</TableCell>
                                    <TableCell>Agent</TableCell>
                                    <TableCell>Overall Score</TableCell>
                                    <TableCell>Accuracy</TableCell>
                                    <TableCell>Response Time</TableCell>
                                    <TableCell>Satisfaction</TableCell>
                                    <TableCell>Status</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {qualityMetrics.map((quality) => (
                                    <TableRow
                                        key={quality.id}
                                        className="cursor-pointer hover:bg-slate-50 dark:hover:bg-neutral-800"
                                        onClick={() => onQualityClick?.(quality.id)}
                                    >
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {formatDate(quality.timestamp)}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                {quality.userName}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {quality.agentName}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography
                                                variant="body2"
                                                className={`font-medium ${quality.overallScore >= 80 ? 'text-green-600' : quality.overallScore >= 60 ? 'text-orange-600' : 'text-red-600'}`}
                                            >
                                                {quality.overallScore}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                {quality.accuracyScore}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                {quality.responseTimeScore}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                {quality.satisfactionScore}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={quality.status} color={getStatusColor(quality.status)} size="small" />
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </Box>
                )}
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockAgentConversationsData = {
    metrics: {
        totalConversations: 1245,
        activeConversations: 23,
        averageQualityScore: 82.5,
        positiveSentiment: 68,
    } as ConversationMetrics,

    conversations: [
        {
            id: 'conv-1',
            agentName: 'Customer Support Agent',
            userName: 'John Smith',
            startTime: '2025-12-11T10:30:00Z',
            endTime: '2025-12-11T10:42:00Z',
            duration: 12,
            messageCount: 8,
            status: 'completed',
            sentiment: 'positive',
            qualityScore: 92,
            topics: ['billing', 'payment-issue', 'account'],
        },
        {
            id: 'conv-2',
            agentName: 'Sales Assistant',
            userName: 'Jane Doe',
            startTime: '2025-12-11T09:15:00Z',
            endTime: '2025-12-11T09:28:00Z',
            duration: 13,
            messageCount: 10,
            status: 'completed',
            sentiment: 'positive',
            qualityScore: 88,
            topics: ['product-demo', 'pricing', 'features'],
        },
        {
            id: 'conv-3',
            agentName: 'Technical Support',
            userName: 'Bob Wilson',
            startTime: '2025-12-11T08:00:00Z',
            duration: 5,
            messageCount: 3,
            status: 'escalated',
            sentiment: 'negative',
            qualityScore: 45,
            topics: ['technical-issue', 'bug-report'],
        },
    ] as ConversationSummary[],

    messages: [
        {
            id: 'msg-1',
            sender: 'user',
            content: 'I need help with my billing issue',
            timestamp: '2025-12-11T10:30:00Z',
            sentiment: 'neutral',
            confidence: 0.82,
        },
        {
            id: 'msg-2',
            sender: 'agent',
            content: "I'd be happy to help you with your billing. Could you provide your account number?",
            timestamp: '2025-12-11T10:30:30Z',
            sentiment: 'positive',
            confidence: 0.91,
        },
        {
            id: 'msg-3',
            sender: 'user',
            content: 'Sure, it\'s ACC-12345',
            timestamp: '2025-12-11T10:31:00Z',
            sentiment: 'neutral',
            confidence: 0.75,
        },
    ] as MessageDetail[],

    sentimentAnalysis: [
        {
            id: 'sent-1',
            period: 'Last 7 Days',
            positive: 420,
            neutral: 180,
            negative: 45,
            totalConversations: 645,
            averageScore: 0.62,
        },
        {
            id: 'sent-2',
            period: 'Last 30 Days',
            positive: 1850,
            neutral: 720,
            negative: 180,
            totalConversations: 2750,
            averageScore: 0.58,
        },
    ] as SentimentAnalysis[],

    qualityMetrics: [
        {
            id: 'qual-1',
            conversationId: 'conv-1',
            userName: 'John Smith',
            agentName: 'Customer Support Agent',
            timestamp: '2025-12-11T10:42:00Z',
            overallScore: 92,
            accuracyScore: 95,
            responseTimeScore: 88,
            satisfactionScore: 93,
            status: 'excellent',
        },
        {
            id: 'qual-2',
            conversationId: 'conv-2',
            userName: 'Jane Doe',
            agentName: 'Sales Assistant',
            timestamp: '2025-12-11T09:28:00Z',
            overallScore: 88,
            accuracyScore: 90,
            responseTimeScore: 85,
            satisfactionScore: 89,
            status: 'excellent',
        },
        {
            id: 'qual-3',
            conversationId: 'conv-3',
            userName: 'Bob Wilson',
            agentName: 'Technical Support',
            timestamp: '2025-12-11T08:05:00Z',
            overallScore: 45,
            accuracyScore: 40,
            responseTimeScore: 50,
            satisfactionScore: 45,
            status: 'poor',
        },
    ] as QualityMetric[],
};
