/**
 * Agent Components Integration Tests
 *
 * Comprehensive test suite for AgentConfiguration, AgentAnalytics, and AgentConversations components.
 * Tests rendering, interactions, filtering, callbacks, and edge cases.
 *
 * @package @ghatana/software-org-web
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
    AgentConfiguration,
    mockAgentConfigurationData,
    type AgentConfigurationProps,
} from '../AgentConfiguration';
import {
    AgentAnalytics,
    mockAgentAnalyticsData,
    type AgentAnalyticsProps,
} from '../AgentAnalytics';
import {
    AgentConversations,
    mockAgentConversationsData,
    type AgentConversationsProps,
} from '../AgentConversations';

// ========================================
// AgentConfiguration Tests
// ========================================

describe('AgentConfiguration Integration Tests', () => {
    let mockProps: AgentConfigurationProps;
    let user: ReturnType<typeof userEvent.setup>;

    beforeEach(() => {
        user = userEvent.setup();
        mockProps = {
            ...mockAgentConfigurationData,
            onAgentClick: vi.fn(),
            onRuleClick: vi.fn(),
            onKnowledgeClick: vi.fn(),
            onIntegrationClick: vi.fn(),
            onCreateAgent: vi.fn(),
            onCreateRule: vi.fn(),
            onUploadKnowledge: vi.fn(),
            onToggle: vi.fn(),
        };
    });

    describe('Component Rendering', () => {
        it('should render all metric KPI cards with correct values', () => {
            render(<AgentConfiguration {...mockProps} />);

            // Total Agents metric
            expect(screen.getByText('Total Agents')).toBeInTheDocument();
            expect(screen.getByText('5')).toBeInTheDocument();
            expect(screen.getByText('3 active')).toBeInTheDocument();

            // Conversations metric
            expect(screen.getByText('Conversations')).toBeInTheDocument();
            expect(screen.getByText('12,450')).toBeInTheDocument();
            expect(screen.getByText('87.5% success rate')).toBeInTheDocument();

            // Avg Response Time metric
            expect(screen.getByText('Avg Response Time')).toBeInTheDocument();
            expect(screen.getByText('320ms')).toBeInTheDocument();

            // Knowledge Base metric
            expect(screen.getByText('Knowledge Base')).toBeInTheDocument();
            expect(screen.getByText('245 MB')).toBeInTheDocument();
            expect(screen.getByText('3 documents')).toBeInTheDocument();
        });

        it('should render all 4 tabs with correct labels', () => {
            render(<AgentConfiguration {...mockProps} />);

            expect(screen.getByRole('tab', { name: /agents \(3\)/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /rules \(3\/3\)/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /knowledge \(3\/3\)/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /integrations \(2\/3\)/i })).toBeInTheDocument();
        });

        it('should render agent cards with correct information', () => {
            render(<AgentConfiguration {...mockProps} />);

            // Customer Support agent
            expect(screen.getByText('Customer Support Agent')).toBeInTheDocument();
            expect(screen.getByText(/AI-powered customer support/i)).toBeInTheDocument();
            expect(screen.getByText('gpt-4')).toBeInTheDocument();
            expect(screen.getByText('active')).toBeInTheDocument();

            // Sales Assistant
            expect(screen.getByText('Sales Assistant')).toBeInTheDocument();
            expect(screen.getByText('claude-3')).toBeInTheDocument();

            // Technical Support
            expect(screen.getByText('Technical Support Agent')).toBeInTheDocument();
            expect(screen.getByText('gpt-3.5')).toBeInTheDocument();
            expect(screen.getByText('inactive')).toBeInTheDocument();
        });
    });

    describe('Tab Navigation', () => {
        it('should switch to Rules tab and display behavior rules', async () => {
            render(<AgentConfiguration {...mockProps} />);

            const rulesTab = screen.getByRole('tab', { name: /rules/i });
            await user.click(rulesTab);

            // Verify rules are displayed
            expect(screen.getByText('Content Safety Filter')).toBeInTheDocument();
            expect(screen.getByText('Professional Tone Enforcement')).toBeInTheDocument();
            expect(screen.getByText('Data Privacy Protection')).toBeInTheDocument();

            // Verify Create Rule button appears
            expect(screen.getByRole('button', { name: /create rule/i })).toBeInTheDocument();
        });

        it('should switch to Knowledge tab and display knowledge items', async () => {
            render(<AgentConfiguration {...mockProps} />);

            const knowledgeTab = screen.getByRole('tab', { name: /knowledge/i });
            await user.click(knowledgeTab);

            // Verify knowledge items are displayed
            expect(screen.getByText('Product Documentation')).toBeInTheDocument();
            expect(screen.getByText('Common FAQs')).toBeInTheDocument();
            expect(screen.getByText('Company Policies')).toBeInTheDocument();

            // Verify Upload Knowledge button appears
            expect(screen.getByRole('button', { name: /upload knowledge/i })).toBeInTheDocument();
        });

        it('should switch to Integrations tab and display integration settings', async () => {
            render(<AgentConfiguration {...mockProps} />);

            const integrationsTab = screen.getByRole('tab', { name: /integrations/i });
            await user.click(integrationsTab);

            // Verify integrations are displayed
            expect(screen.getByText('CRM Integration')).toBeInTheDocument();
            expect(screen.getByText('Knowledge Database')).toBeInTheDocument();
            expect(screen.getByText('Slack Notifications')).toBeInTheDocument();

            // Verify status chips
            expect(screen.getAllByText('connected')).toHaveLength(2);
            expect(screen.getByText('disconnected')).toBeInTheDocument();
        });
    });

    describe('Filtering', () => {
        it('should filter agents by status when clicking filter chips', async () => {
            render(<AgentConfiguration {...mockProps} />);

            // Initially shows all agents (3)
            expect(screen.getByText('Customer Support Agent')).toBeInTheDocument();
            expect(screen.getByText('Sales Assistant')).toBeInTheDocument();
            expect(screen.getByText('Technical Support Agent')).toBeInTheDocument();

            // Click Active filter
            const activeFilter = screen.getByRole('button', { name: /active \(2\)/i });
            await user.click(activeFilter);

            // Should show only active agents
            expect(screen.getByText('Customer Support Agent')).toBeInTheDocument();
            expect(screen.getByText('Sales Assistant')).toBeInTheDocument();
            expect(screen.queryByText('Technical Support Agent')).not.toBeInTheDocument();

            // Click Inactive filter
            const inactiveFilter = screen.getByRole('button', { name: /inactive \(1\)/i });
            await user.click(inactiveFilter);

            // Should show only inactive agent
            expect(screen.queryByText('Customer Support Agent')).not.toBeInTheDocument();
            expect(screen.queryByText('Sales Assistant')).not.toBeInTheDocument();
            expect(screen.getByText('Technical Support Agent')).toBeInTheDocument();
        });
    });

    describe('User Interactions', () => {
        it('should call onAgentClick when agent card is clicked', async () => {
            render(<AgentConfiguration {...mockProps} />);

            const agentCard = screen.getByText('Customer Support Agent').closest('div[class*="cursor-pointer"]');
            expect(agentCard).toBeInTheDocument();

            if (agentCard) {
                await user.click(agentCard);
                expect(mockProps.onAgentClick).toHaveBeenCalledWith('agent-1');
            }
        });

        it('should call onToggle when agent toggle is switched', async () => {
            render(<AgentConfiguration {...mockProps} />);

            // Find toggle switches (there are 3 agents, find the first one)
            const switches = screen.getAllByRole('checkbox');
            await user.click(switches[0]);

            expect(mockProps.onToggle).toHaveBeenCalledWith('agent', 'agent-1', false);
        });

        it('should call onCreateAgent when Create Agent button is clicked', async () => {
            render(<AgentConfiguration {...mockProps} />);

            const createButton = screen.getByRole('button', { name: /create agent/i });
            await user.click(createButton);

            expect(mockProps.onCreateAgent).toHaveBeenCalled();
        });

        it('should call onRuleClick when rule card is clicked in Rules tab', async () => {
            render(<AgentConfiguration {...mockProps} />);

            // Switch to Rules tab
            const rulesTab = screen.getByRole('tab', { name: /rules/i });
            await user.click(rulesTab);

            // Click rule card
            const ruleCard = screen.getByText('Content Safety Filter').closest('div[class*="cursor-pointer"]');
            if (ruleCard) {
                await user.click(ruleCard);
                expect(mockProps.onRuleClick).toHaveBeenCalledWith('rule-1');
            }
        });

        it('should call onCreateRule when Create Rule button is clicked', async () => {
            render(<AgentConfiguration {...mockProps} />);

            // Switch to Rules tab
            const rulesTab = screen.getByRole('tab', { name: /rules/i });
            await user.click(rulesTab);

            const createButton = screen.getByRole('button', { name: /create rule/i });
            await user.click(createButton);

            expect(mockProps.onCreateRule).toHaveBeenCalled();
        });
    });

    describe('Edge Cases', () => {
        it('should handle empty agents array', () => {
            const emptyProps = { ...mockProps, agents: [] };
            render(<AgentConfiguration {...emptyProps} />);

            expect(screen.getByText('Agent Configuration')).toBeInTheDocument();
            // Should not crash
        });

        it('should display correct counts when all agents are active', () => {
            const allActiveProps = {
                ...mockProps,
                agents: mockProps.agents.map((a) => ({ ...a, status: 'active' as const })),
            };
            render(<AgentConfiguration {...allActiveProps} />);

            expect(screen.getByRole('button', { name: /active \(3\)/i })).toBeInTheDocument();
            expect(screen.getByRole('button', { name: /inactive \(0\)/i })).toBeInTheDocument();
        });
    });
});

// ========================================
// AgentAnalytics Tests
// ========================================

describe('AgentAnalytics Integration Tests', () => {
    let mockProps: AgentAnalyticsProps;
    let user: ReturnType<typeof userEvent.setup>;

    beforeEach(() => {
        user = userEvent.setup();
        mockProps = {
            ...mockAgentAnalyticsData,
            onTrendClick: vi.fn(),
            onAgentAnalyticsClick: vi.fn(),
            onMetricClick: vi.fn(),
            onRecommendationClick: vi.fn(),
            onExportReport: vi.fn(),
        };
    });

    describe('Component Rendering', () => {
        it('should render all metric KPI cards with correct values', () => {
            render(<AgentAnalytics {...mockProps} />);

            // Total Conversations
            expect(screen.getByText('Total Conversations')).toBeInTheDocument();
            expect(screen.getByText('12,450')).toBeInTheDocument();

            // Success Rate
            expect(screen.getByText('Success Rate')).toBeInTheDocument();
            expect(screen.getByText('87.5%')).toBeInTheDocument();

            // Avg Response Time
            expect(screen.getByText('Avg Response Time')).toBeInTheDocument();
            expect(screen.getByText('320ms')).toBeInTheDocument();

            // Satisfaction Score
            expect(screen.getByText('Satisfaction Score')).toBeInTheDocument();
            expect(screen.getByText('4.2/5.0')).toBeInTheDocument();
        });

        it('should render all 4 tabs with correct labels', () => {
            render(<AgentAnalytics {...mockProps} />);

            expect(screen.getByRole('tab', { name: /usage \(3\)/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /conversations \(2\)/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /performance \(3\)/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /recommendations \(2\)/i })).toBeInTheDocument();
        });

        it('should render Export Report button', () => {
            render(<AgentAnalytics {...mockProps} />);

            expect(screen.getByRole('button', { name: /export report/i })).toBeInTheDocument();
        });

        it('should render usage trends with correct information', () => {
            render(<AgentAnalytics {...mockProps} />);

            // Usage tab is default
            expect(screen.getByText('Last 7 Days')).toBeInTheDocument();
            expect(screen.getByText('Last 30 Days')).toBeInTheDocument();
            expect(screen.getByText('Last 90 Days')).toBeInTheDocument();

            // Verify trend indicators
            expect(screen.getByText('↑ up')).toBeInTheDocument();
            expect(screen.getByText('→ stable')).toBeInTheDocument();
            expect(screen.getByText('↓ down')).toBeInTheDocument();
        });
    });

    describe('Tab Navigation', () => {
        it('should switch to Conversations tab and display agent analytics', async () => {
            render(<AgentAnalytics {...mockProps} />);

            const conversationsTab = screen.getByRole('tab', { name: /conversations/i });
            await user.click(conversationsTab);

            // Verify agent analytics are displayed
            expect(screen.getByText('Customer Support Agent')).toBeInTheDocument();
            expect(screen.getByText('Sales Assistant')).toBeInTheDocument();

            // Verify metrics
            expect(screen.getByText(/92%/i)).toBeInTheDocument(); // Completion rate
            expect(screen.getByText(/5.2%/i)).toBeInTheDocument(); // Escalation rate
        });

        it('should switch to Performance tab and display metrics table', async () => {
            render(<AgentAnalytics {...mockProps} />);

            const performanceTab = screen.getByRole('tab', { name: /performance/i });
            await user.click(performanceTab);

            // Verify table headers
            expect(screen.getByText('Metric')).toBeInTheDocument();
            expect(screen.getByText('Category')).toBeInTheDocument();
            expect(screen.getByText('Current')).toBeInTheDocument();
            expect(screen.getByText('Target')).toBeInTheDocument();
            expect(screen.getByText('Status')).toBeInTheDocument();

            // Verify metrics
            expect(screen.getByText('Response Time')).toBeInTheDocument();
            expect(screen.getByText('First Contact Resolution')).toBeInTheDocument();
            expect(screen.getByText('Customer Satisfaction')).toBeInTheDocument();
        });

        it('should switch to Recommendations tab and display improvement suggestions', async () => {
            render(<AgentAnalytics {...mockProps} />);

            const recommendationsTab = screen.getByRole('tab', { name: /recommendations/i });
            await user.click(recommendationsTab);

            // Verify recommendations
            expect(screen.getByText('Improve Response Accuracy')).toBeInTheDocument();
            expect(screen.getByText('Optimize Response Speed')).toBeInTheDocument();

            // Verify priority and category chips
            expect(screen.getByText('high')).toBeInTheDocument();
            expect(screen.getByText('medium')).toBeInTheDocument();
        });
    });

    describe('User Interactions', () => {
        it('should call onTrendClick when trend card is clicked', async () => {
            render(<AgentAnalytics {...mockProps} />);

            const trendCard = screen.getByText('Last 7 Days').closest('div[class*="cursor-pointer"]');
            if (trendCard) {
                await user.click(trendCard);
                expect(mockProps.onTrendClick).toHaveBeenCalledWith('trend-1');
            }
        });

        it('should call onAgentAnalyticsClick when agent card is clicked', async () => {
            render(<AgentAnalytics {...mockProps} />);

            // Switch to Conversations tab
            const conversationsTab = screen.getByRole('tab', { name: /conversations/i });
            await user.click(conversationsTab);

            const agentCard = screen.getByText('Customer Support Agent').closest('div[class*="cursor-pointer"]');
            if (agentCard) {
                await user.click(agentCard);
                expect(mockProps.onAgentAnalyticsClick).toHaveBeenCalledWith('analytics-1');
            }
        });

        it('should call onMetricClick when performance metric row is clicked', async () => {
            render(<AgentAnalytics {...mockProps} />);

            // Switch to Performance tab
            const performanceTab = screen.getByRole('tab', { name: /performance/i });
            await user.click(performanceTab);

            // Click on a table row
            const metricRow = screen.getByText('Response Time').closest('tr');
            if (metricRow) {
                await user.click(metricRow);
                expect(mockProps.onMetricClick).toHaveBeenCalledWith('metric-1');
            }
        });

        it('should call onRecommendationClick when recommendation card is clicked', async () => {
            render(<AgentAnalytics {...mockProps} />);

            // Switch to Recommendations tab
            const recommendationsTab = screen.getByRole('tab', { name: /recommendations/i });
            await user.click(recommendationsTab);

            const recommendationCard = screen.getByText('Improve Response Accuracy').closest('div[class*="cursor-pointer"]');
            if (recommendationCard) {
                await user.click(recommendationCard);
                expect(mockProps.onRecommendationClick).toHaveBeenCalledWith('rec-1');
            }
        });

        it('should call onExportReport when Export Report button is clicked', async () => {
            render(<AgentAnalytics {...mockProps} />);

            const exportButton = screen.getByRole('button', { name: /export report/i });
            await user.click(exportButton);

            expect(mockProps.onExportReport).toHaveBeenCalled();
        });
    });

    describe('Edge Cases', () => {
        it('should handle empty trends array', () => {
            const emptyProps = { ...mockProps, usageTrends: [] };
            render(<AgentAnalytics {...emptyProps} />);

            expect(screen.getByText('Agent Analytics')).toBeInTheDocument();
            // Should not crash
        });

        it('should display trend icons correctly for all trend types', () => {
            render(<AgentAnalytics {...mockProps} />);

            expect(screen.getByText('↑ up')).toBeInTheDocument();
            expect(screen.getByText('→ stable')).toBeInTheDocument();
            expect(screen.getByText('↓ down')).toBeInTheDocument();
        });
    });
});

// ========================================
// AgentConversations Tests
// ========================================

describe('AgentConversations Integration Tests', () => {
    let mockProps: AgentConversationsProps;
    let user: ReturnType<typeof userEvent.setup>;

    beforeEach(() => {
        user = userEvent.setup();
        mockProps = {
            ...mockAgentConversationsData,
            onConversationClick: vi.fn(),
            onMessageClick: vi.fn(),
            onSentimentClick: vi.fn(),
            onQualityClick: vi.fn(),
            onExportConversations: vi.fn(),
        };
    });

    describe('Component Rendering', () => {
        it('should render all metric KPI cards with correct values', () => {
            render(<AgentConversations {...mockProps} />);

            // Total Conversations
            expect(screen.getByText('Total Conversations')).toBeInTheDocument();
            expect(screen.getByText('1,245')).toBeInTheDocument();

            // Avg Quality Score
            expect(screen.getByText('Avg Quality Score')).toBeInTheDocument();
            expect(screen.getByText('82')).toBeInTheDocument();

            // Positive Sentiment
            expect(screen.getByText('Positive Sentiment')).toBeInTheDocument();
            expect(screen.getByText('68%')).toBeInTheDocument();

            // Completion Rate
            expect(screen.getByText('Completion Rate')).toBeInTheDocument();
        });

        it('should render all 4 tabs with correct labels', () => {
            render(<AgentConversations {...mockProps} />);

            expect(screen.getByRole('tab', { name: /conversations \(3\)/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /messages \(3\)/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /sentiment \(2\)/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /quality \(3\)/i })).toBeInTheDocument();
        });

        it('should render Export Conversations button', () => {
            render(<AgentConversations {...mockProps} />);

            expect(screen.getByRole('button', { name: /export conversations/i })).toBeInTheDocument();
        });

        it('should render conversation cards with correct information', () => {
            render(<AgentConversations {...mockProps} />);

            // Conversation cards
            expect(screen.getByText(/John Smith ↔ Customer Support Agent/i)).toBeInTheDocument();
            expect(screen.getByText(/Jane Doe ↔ Sales Assistant/i)).toBeInTheDocument();
            expect(screen.getByText(/Bob Wilson ↔ Technical Support/i)).toBeInTheDocument();

            // Status chips
            expect(screen.getAllByText('completed')).toHaveLength(2);
            expect(screen.getByText('escalated')).toBeInTheDocument();

            // Sentiment chips
            expect(screen.getAllByText('positive')).toHaveLength(2);
            expect(screen.getByText('negative')).toBeInTheDocument();
        });
    });

    describe('Tab Navigation', () => {
        it('should switch to Messages tab and display message history', async () => {
            render(<AgentConversations {...mockProps} />);

            const messagesTab = screen.getByRole('tab', { name: /messages/i });
            await user.click(messagesTab);

            // Verify table headers
            expect(screen.getByText('Timestamp')).toBeInTheDocument();
            expect(screen.getByText('Sender')).toBeInTheDocument();
            expect(screen.getByText('Message')).toBeInTheDocument();
            expect(screen.getByText('Sentiment')).toBeInTheDocument();
            expect(screen.getByText('Confidence')).toBeInTheDocument();

            // Verify messages
            expect(screen.getByText(/I need help with my billing issue/i)).toBeInTheDocument();
            expect(screen.getByText(/I'd be happy to help you/i)).toBeInTheDocument();
        });

        it('should switch to Sentiment tab and display sentiment analysis', async () => {
            render(<AgentConversations {...mockProps} />);

            const sentimentTab = screen.getByRole('tab', { name: /sentiment/i });
            await user.click(sentimentTab);

            // Verify sentiment cards
            expect(screen.getByText('Last 7 Days')).toBeInTheDocument();
            expect(screen.getByText('Last 30 Days')).toBeInTheDocument();

            // Verify sentiment breakdowns
            expect(screen.getAllByText('Positive')).toHaveLength(2);
            expect(screen.getAllByText('Neutral')).toHaveLength(2);
            expect(screen.getAllByText('Negative')).toHaveLength(2);
        });

        it('should switch to Quality tab and display quality metrics', async () => {
            render(<AgentConversations {...mockProps} />);

            const qualityTab = screen.getByRole('tab', { name: /quality/i });
            await user.click(qualityTab);

            // Verify table headers
            expect(screen.getByText('User')).toBeInTheDocument();
            expect(screen.getByText('Agent')).toBeInTheDocument();
            expect(screen.getByText('Overall Score')).toBeInTheDocument();
            expect(screen.getByText('Accuracy')).toBeInTheDocument();
            expect(screen.getByText('Satisfaction')).toBeInTheDocument();

            // Verify quality data
            expect(screen.getByText('John Smith')).toBeInTheDocument();
            expect(screen.getByText('Customer Support Agent')).toBeInTheDocument();
            expect(screen.getByText('excellent')).toBeInTheDocument();
            expect(screen.getByText('poor')).toBeInTheDocument();
        });
    });

    describe('Filtering', () => {
        it('should filter conversations by status when clicking filter chips', async () => {
            render(<AgentConversations {...mockProps} />);

            // Initially shows all conversations (3)
            expect(screen.getByText(/John Smith ↔ Customer Support Agent/i)).toBeInTheDocument();
            expect(screen.getByText(/Jane Doe ↔ Sales Assistant/i)).toBeInTheDocument();
            expect(screen.getByText(/Bob Wilson ↔ Technical Support/i)).toBeInTheDocument();

            // Click Completed filter
            const completedFilter = screen.getByRole('button', { name: /completed \(2\)/i });
            await user.click(completedFilter);

            // Should show only completed conversations
            expect(screen.getByText(/John Smith ↔ Customer Support Agent/i)).toBeInTheDocument();
            expect(screen.getByText(/Jane Doe ↔ Sales Assistant/i)).toBeInTheDocument();
            expect(screen.queryByText(/Bob Wilson ↔ Technical Support/i)).not.toBeInTheDocument();

            // Click Escalated filter
            const escalatedFilter = screen.getByRole('button', { name: /escalated \(1\)/i });
            await user.click(escalatedFilter);

            // Should show only escalated conversation
            expect(screen.queryByText(/John Smith ↔ Customer Support Agent/i)).not.toBeInTheDocument();
            expect(screen.queryByText(/Jane Doe ↔ Sales Assistant/i)).not.toBeInTheDocument();
            expect(screen.getByText(/Bob Wilson ↔ Technical Support/i)).toBeInTheDocument();
        });
    });

    describe('User Interactions', () => {
        it('should call onConversationClick when conversation card is clicked', async () => {
            render(<AgentConversations {...mockProps} />);

            const conversationCard = screen.getByText(/John Smith ↔ Customer Support Agent/i).closest('div[class*="cursor-pointer"]');
            if (conversationCard) {
                await user.click(conversationCard);
                expect(mockProps.onConversationClick).toHaveBeenCalledWith('conv-1');
            }
        });

        it('should call onMessageClick when message row is clicked', async () => {
            render(<AgentConversations {...mockProps} />);

            // Switch to Messages tab
            const messagesTab = screen.getByRole('tab', { name: /messages/i });
            await user.click(messagesTab);

            // Click on a message row
            const messageRow = screen.getByText(/I need help with my billing issue/i).closest('tr');
            if (messageRow) {
                await user.click(messageRow);
                expect(mockProps.onMessageClick).toHaveBeenCalledWith('msg-1');
            }
        });

        it('should call onSentimentClick when sentiment card is clicked', async () => {
            render(<AgentConversations {...mockProps} />);

            // Switch to Sentiment tab
            const sentimentTab = screen.getByRole('tab', { name: /sentiment/i });
            await user.click(sentimentTab);

            const sentimentCard = screen.getByText('Last 7 Days').closest('div[class*="cursor-pointer"]');
            if (sentimentCard) {
                await user.click(sentimentCard);
                expect(mockProps.onSentimentClick).toHaveBeenCalledWith('sent-1');
            }
        });

        it('should call onQualityClick when quality metric row is clicked', async () => {
            render(<AgentConversations {...mockProps} />);

            // Switch to Quality tab
            const qualityTab = screen.getByRole('tab', { name: /quality/i });
            await user.click(qualityTab);

            // Click on a quality row
            const qualityRow = screen.getByText('John Smith').closest('tr');
            if (qualityRow) {
                await user.click(qualityRow);
                expect(mockProps.onQualityClick).toHaveBeenCalledWith('qual-1');
            }
        });

        it('should call onExportConversations when Export button is clicked', async () => {
            render(<AgentConversations {...mockProps} />);

            const exportButton = screen.getByRole('button', { name: /export conversations/i });
            await user.click(exportButton);

            expect(mockProps.onExportConversations).toHaveBeenCalled();
        });
    });

    describe('Edge Cases', () => {
        it('should handle empty conversations array', () => {
            const emptyProps = { ...mockProps, conversations: [] };
            render(<AgentConversations {...emptyProps} />);

            expect(screen.getByText('Agent Conversations')).toBeInTheDocument();
            // Should not crash and completion rate should handle division by zero
        });

        it('should display quality scores with correct color coding', () => {
            render(<AgentConversations {...mockProps} />);

            // Quality scores in conversation cards
            const highScore = screen.getByText('92');
            const mediumScore = screen.getByText('88');
            const lowScore = screen.getByText('45');

            expect(highScore).toHaveClass('text-green-600');
            expect(mediumScore).toHaveClass('text-green-600');
            expect(lowScore).toHaveClass('text-red-600');
        });

        it('should handle conversations without topics', () => {
            const noTopicsProps = {
                ...mockProps,
                conversations: mockProps.conversations.map((c) => ({ ...c, topics: [] })),
            };
            render(<AgentConversations {...noTopicsProps} />);

            expect(screen.getByText('Agent Conversations')).toBeInTheDocument();
            // Should not display topics section
        });
    });
});
