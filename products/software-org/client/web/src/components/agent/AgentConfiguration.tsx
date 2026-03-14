/**
 * Agent Configuration Component
 *
 * Component for configuring AI agents with settings, behavior rules, knowledge base,
 * and integration options.
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
    Switch,
} from '@ghatana/design-system';
import { KpiCard } from '@/shared/components/org';

/**
 * Agent configuration metrics
 */
export interface AgentMetrics {
    totalAgents: number;
    activeAgents: number;
    totalConversations: number;
    averageResponseTime: number; // ms
    successRate: number; // Percentage
    knowledgeBaseSize: number; // MB
}

/**
 * Agent configuration
 */
export interface AgentConfig {
    id: string;
    name: string;
    description: string;
    status: 'active' | 'inactive' | 'training';
    model: 'gpt-4' | 'gpt-3.5' | 'claude-3' | 'claude-2' | 'custom';
    temperature: number; // 0-1
    maxTokens: number;
    systemPrompt: string;
    tags: string[];
    createdAt: string;
    lastModified: string;
}

/**
 * Behavior rule
 */
export interface BehaviorRule {
    id: string;
    name: string;
    category: 'safety' | 'tone' | 'content' | 'compliance';
    enabled: boolean;
    priority: 'high' | 'medium' | 'low';
    description: string;
    condition: string;
    action: string;
}

/**
 * Knowledge base item
 */
export interface KnowledgeBaseItem {
    id: string;
    title: string;
    category: 'documentation' | 'faq' | 'policy' | 'training';
    content: string;
    size: number; // KB
    lastUpdated: string;
    usageCount: number;
    enabled: boolean;
}

/**
 * Integration setting
 */
export interface IntegrationSetting {
    id: string;
    name: string;
    type: 'api' | 'database' | 'webhook' | 'plugin';
    status: 'connected' | 'disconnected' | 'error';
    endpoint: string;
    lastSync: string;
    enabled: boolean;
}

/**
 * Agent Configuration Props
 */
export interface AgentConfigurationProps {
    /** Agent metrics */
    metrics: AgentMetrics;
    /** Agent configurations */
    agents: AgentConfig[];
    /** Behavior rules */
    behaviorRules: BehaviorRule[];
    /** Knowledge base items */
    knowledgeBase: KnowledgeBaseItem[];
    /** Integration settings */
    integrations: IntegrationSetting[];
    /** Callback when agent is clicked */
    onAgentClick?: (agentId: string) => void;
    /** Callback when rule is clicked */
    onRuleClick?: (ruleId: string) => void;
    /** Callback when knowledge item is clicked */
    onKnowledgeClick?: (itemId: string) => void;
    /** Callback when integration is clicked */
    onIntegrationClick?: (integrationId: string) => void;
    /** Callback when create agent is clicked */
    onCreateAgent?: () => void;
    /** Callback when create rule is clicked */
    onCreateRule?: () => void;
    /** Callback when upload knowledge is clicked */
    onUploadKnowledge?: () => void;
    /** Callback when toggle is changed */
    onToggle?: (type: 'agent' | 'rule' | 'knowledge' | 'integration', id: string, enabled: boolean) => void;
}

/**
 * Agent Configuration Component
 *
 * Provides comprehensive agent configuration with:
 * - Agent setup (model, parameters, prompts)
 * - Behavior rules (safety, tone, content, compliance)
 * - Knowledge base management
 * - Integration settings
 * - Tab-based navigation (Agents, Rules, Knowledge, Integrations)
 *
 * Reuses @ghatana/design-system components and shared org KPI cards:
 * - KpiCard (metrics)
 * - Grid (responsive layouts)
 * - Card (agent cards, rule cards, knowledge cards)
 * - Chip (status, model, category indicators)
 * - Tabs (navigation)
 * - Switch (enable/disable toggles)
 *
 * @example
 * ```tsx
 * <AgentConfiguration
 *   metrics={agentMetrics}
 *   agents={agentList}
 *   behaviorRules={rules}
 *   knowledgeBase={knowledge}
 *   integrations={integrations}
 *   onAgentClick={(id) => navigate(`/agents/${id}`)}
 * />
 * ```
 */
export const AgentConfiguration: React.FC<AgentConfigurationProps> = ({
    metrics,
    agents,
    behaviorRules,
    knowledgeBase,
    integrations,
    onAgentClick,
    onRuleClick,
    onKnowledgeClick,
    onIntegrationClick,
    onCreateAgent,
    onCreateRule,
    onUploadKnowledge,
    onToggle,
}) => {
    const [selectedTab, setSelectedTab] = useState<'agents' | 'rules' | 'knowledge' | 'integrations'>('agents');
    const [agentFilter, setAgentFilter] = useState<'all' | 'active' | 'inactive' | 'training'>('all');

    // Get status color
    const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'active':
            case 'connected':
                return 'success';
            case 'inactive':
            case 'disconnected':
            case 'training':
                return 'warning';
            case 'error':
                return 'error';
            default:
                return 'default';
        }
    };

    // Get model color
    const getModelColor = (model: string): 'error' | 'warning' | 'default' => {
        switch (model) {
            case 'gpt-4':
            case 'claude-3':
                return 'error';
            case 'gpt-3.5':
            case 'claude-2':
                return 'warning';
            case 'custom':
                return 'default';
            default:
                return 'default';
        }
    };

    // Get category color
    const getCategoryColor = (category: string): 'error' | 'warning' | 'default' => {
        switch (category) {
            case 'safety':
            case 'compliance':
                return 'error';
            case 'tone':
            case 'content':
            case 'policy':
                return 'warning';
            case 'documentation':
            case 'faq':
            case 'training':
                return 'default';
            default:
                return 'default';
        }
    };

    // Get priority color
    const getPriorityColor = (priority: 'high' | 'medium' | 'low'): 'error' | 'warning' | 'default' => {
        switch (priority) {
            case 'high':
                return 'error';
            case 'medium':
                return 'warning';
            case 'low':
                return 'default';
        }
    };

    // Get type color
    const getTypeColor = (type: string): 'error' | 'warning' | 'default' => {
        switch (type) {
            case 'api':
                return 'error';
            case 'database':
            case 'webhook':
                return 'warning';
            case 'plugin':
                return 'default';
            default:
                return 'default';
        }
    };

    // Format date
    const formatDate = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
    };

    // Filter agents
    const filteredAgents = agentFilter === 'all' ? agents : agents.filter((a) => a.status === agentFilter);

    // Calculate enabled counts
    const enabledRules = behaviorRules.filter((r) => r.enabled).length;
    const enabledKnowledge = knowledgeBase.filter((k) => k.enabled).length;
    const connectedIntegrations = integrations.filter((i) => i.status === 'connected').length;

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Agent Configuration
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        AI agent setup, behavior rules, and knowledge management
                    </Typography>
                </Box>
                <Stack direction="row" spacing={2}>
                    {onCreateAgent && selectedTab === 'agents' && (
                        <Button variant="primary" size="md" onClick={onCreateAgent}>
                            Create Agent
                        </Button>
                    )}
                    {onCreateRule && selectedTab === 'rules' && (
                        <Button variant="primary" size="md" onClick={onCreateRule}>
                            Create Rule
                        </Button>
                    )}
                    {onUploadKnowledge && selectedTab === 'knowledge' && (
                        <Button variant="primary" size="md" onClick={onUploadKnowledge}>
                            Upload Knowledge
                        </Button>
                    )}
                </Stack>
            </Box>

            {/* Metrics */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="Total Agents"
                    value={metrics.totalAgents}
                    description={`${metrics.activeAgents} active`}
                    status={metrics.activeAgents > 0 ? 'healthy' : 'warning'}
                />

                <KpiCard
                    label="Conversations"
                    value={metrics.totalConversations.toLocaleString()}
                    description={`${metrics.successRate}% success rate`}
                    status={metrics.successRate >= 80 ? 'healthy' : metrics.successRate >= 60 ? 'warning' : 'error'}
                />

                <KpiCard
                    label="Avg Response Time"
                    value={`${metrics.averageResponseTime}ms`}
                    description="Last 24 hours"
                    status={metrics.averageResponseTime < 500 ? 'healthy' : metrics.averageResponseTime < 1000 ? 'warning' : 'error'}
                />

                <KpiCard
                    label="Knowledge Base"
                    value={`${metrics.knowledgeBaseSize.toLocaleString()} MB`}
                    description={`${knowledgeBase.length} documents`}
                    status="healthy"
                />
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`Agents (${agents.length})`} value="agents" />
                    <Tab label={`Rules (${enabledRules}/${behaviorRules.length})`} value="rules" />
                    <Tab label={`Knowledge (${enabledKnowledge}/${knowledgeBase.length})`} value="knowledge" />
                    <Tab label={`Integrations (${connectedIntegrations}/${integrations.length})`} value="integrations" />
                </Tabs>

                {/* Agents Tab */}
                {selectedTab === 'agents' && (
                    <Box className="p-4">
                        {/* Agent Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label={`All (${agents.length})`} color={agentFilter === 'all' ? 'error' : 'default'} onClick={() => setAgentFilter('all')} />
                            <Chip
                                label={`Active (${agents.filter((a) => a.status === 'active').length})`}
                                color={agentFilter === 'active' ? 'success' : 'default'}
                                onClick={() => setAgentFilter('active')}
                            />
                            <Chip
                                label={`Inactive (${agents.filter((a) => a.status === 'inactive').length})`}
                                color={agentFilter === 'inactive' ? 'warning' : 'default'}
                                onClick={() => setAgentFilter('inactive')}
                            />
                            <Chip
                                label={`Training (${agents.filter((a) => a.status === 'training').length})`}
                                color={agentFilter === 'training' ? 'warning' : 'default'}
                                onClick={() => setAgentFilter('training')}
                            />
                        </Stack>

                        {/* Agent Grid */}
                        <Grid columns={2} gap={4}>
                            {filteredAgents.map((agent) => (
                                <Card key={agent.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onAgentClick?.(agent.id)}>
                                    <Box className="p-4">
                                        {/* Agent Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {agent.name}
                                                    </Typography>
                                                    <Chip label={agent.status} color={getStatusColor(agent.status)} size="small" />
                                                    <Chip label={agent.model} color={getModelColor(agent.model)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {agent.description}
                                                </Typography>
                                            </Box>
                                            <Switch
                                                checked={agent.status === 'active'}
                                                onChange={(e) => {
                                                    e.stopPropagation();
                                                    onToggle?.('agent', agent.id, e.target.checked);
                                                }}
                                            />
                                        </Box>

                                        {/* Agent Parameters */}
                                        <Grid columns={3} gap={2} className="mb-3">
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Temperature
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {agent.temperature.toFixed(1)}
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Max Tokens
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {agent.maxTokens.toLocaleString()}
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Tags
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {agent.tags.length}
                                                </Typography>
                                            </Box>
                                        </Grid>

                                        {/* Agent Tags */}
                                        {agent.tags.length > 0 && (
                                            <Box className="mb-3">
                                                <Stack direction="row" spacing={1} className="flex-wrap">
                                                    {agent.tags.slice(0, 3).map((tag, i) => (
                                                        <Chip key={i} label={tag} size="small" />
                                                    ))}
                                                    {agent.tags.length > 3 && <Chip label={`+${agent.tags.length - 3} more`} size="small" />}
                                                </Stack>
                                            </Box>
                                        )}

                                        {/* Agent Footer */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={2} gap={2}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Created
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {formatDate(agent.createdAt)}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Last Modified
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {formatDate(agent.lastModified)}
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

                {/* Rules Tab */}
                {selectedTab === 'rules' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Behavior Rules
                        </Typography>

                        <Stack spacing={3}>
                            {behaviorRules.map((rule) => (
                                <Card key={rule.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onRuleClick?.(rule.id)}>
                                    <Box className="p-4">
                                        {/* Rule Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {rule.name}
                                                    </Typography>
                                                    <Chip label={rule.category} color={getCategoryColor(rule.category)} size="small" />
                                                    <Chip label={rule.priority} color={getPriorityColor(rule.priority)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {rule.description}
                                                </Typography>
                                            </Box>
                                            <Switch
                                                checked={rule.enabled}
                                                onChange={(e) => {
                                                    e.stopPropagation();
                                                    onToggle?.('rule', rule.id, e.target.checked);
                                                }}
                                            />
                                        </Box>

                                        {/* Rule Details */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={2} gap={3}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Condition
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {rule.condition}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Action
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {rule.action}
                                                    </Typography>
                                                </Box>
                                            </Grid>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}

                {/* Knowledge Tab */}
                {selectedTab === 'knowledge' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Knowledge Base
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {knowledgeBase.map((item) => (
                                <Card key={item.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onKnowledgeClick?.(item.id)}>
                                    <Box className="p-4">
                                        {/* Knowledge Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {item.title}
                                                    </Typography>
                                                    <Chip label={item.category} color={getCategoryColor(item.category)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 line-clamp-2">
                                                    {item.content}
                                                </Typography>
                                            </Box>
                                            <Switch
                                                checked={item.enabled}
                                                onChange={(e) => {
                                                    e.stopPropagation();
                                                    onToggle?.('knowledge', item.id, e.target.checked);
                                                }}
                                            />
                                        </Box>

                                        {/* Knowledge Details */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={3} gap={2}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Size
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {item.size} KB
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Usage Count
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {item.usageCount}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Last Updated
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {formatDate(item.lastUpdated)}
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

                {/* Integrations Tab */}
                {selectedTab === 'integrations' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Integration Settings
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {integrations.map((integration) => (
                                <Card
                                    key={integration.id}
                                    className="cursor-pointer hover:shadow-md transition-shadow"
                                    onClick={() => onIntegrationClick?.(integration.id)}
                                >
                                    <Box className="p-4">
                                        {/* Integration Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {integration.name}
                                                    </Typography>
                                                    <Chip label={integration.type} color={getTypeColor(integration.type)} size="small" />
                                                    <Chip label={integration.status} color={getStatusColor(integration.status)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {integration.endpoint}
                                                </Typography>
                                            </Box>
                                            <Switch
                                                checked={integration.enabled}
                                                onChange={(e) => {
                                                    e.stopPropagation();
                                                    onToggle?.('integration', integration.id, e.target.checked);
                                                }}
                                            />
                                        </Box>

                                        {/* Integration Details */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Last Sync
                                                </Typography>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {formatDate(integration.lastSync)}
                                                </Typography>
                                            </Box>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockAgentConfigurationData = {
    metrics: {
        totalAgents: 5,
        activeAgents: 3,
        totalConversations: 12450,
        averageResponseTime: 320,
        successRate: 87.5,
        knowledgeBaseSize: 245,
    } as AgentMetrics,

    agents: [
        {
            id: 'agent-1',
            name: 'Customer Support Agent',
            description: 'Handles customer inquiries and support tickets',
            status: 'active',
            model: 'gpt-4',
            temperature: 0.7,
            maxTokens: 2000,
            systemPrompt: 'You are a helpful customer support agent...',
            tags: ['support', 'customer-service', 'tickets'],
            createdAt: '2025-10-15T10:00:00Z',
            lastModified: '2025-12-10T14:30:00Z',
        },
        {
            id: 'agent-2',
            name: 'Sales Assistant',
            description: 'Assists with sales inquiries and product recommendations',
            status: 'active',
            model: 'claude-3',
            temperature: 0.8,
            maxTokens: 1500,
            systemPrompt: 'You are a knowledgeable sales assistant...',
            tags: ['sales', 'product', 'recommendations'],
            createdAt: '2025-11-01T12:00:00Z',
            lastModified: '2025-12-09T16:00:00Z',
        },
        {
            id: 'agent-3',
            name: 'Technical Support',
            description: 'Provides technical troubleshooting and solutions',
            status: 'inactive',
            model: 'gpt-3.5',
            temperature: 0.5,
            maxTokens: 3000,
            systemPrompt: 'You are a technical support specialist...',
            tags: ['technical', 'troubleshooting', 'engineering'],
            createdAt: '2025-09-20T08:00:00Z',
            lastModified: '2025-12-05T10:00:00Z',
        },
    ] as AgentConfig[],

    behaviorRules: [
        {
            id: 'rule-1',
            name: 'Content Safety Filter',
            category: 'safety',
            enabled: true,
            priority: 'high',
            description: 'Filters harmful or inappropriate content',
            condition: 'message contains offensive language',
            action: 'Block message and notify moderator',
        },
        {
            id: 'rule-2',
            name: 'Professional Tone',
            category: 'tone',
            enabled: true,
            priority: 'medium',
            description: 'Ensures professional and courteous responses',
            condition: 'customer interaction',
            action: 'Apply professional language patterns',
        },
        {
            id: 'rule-3',
            name: 'Data Privacy Compliance',
            category: 'compliance',
            enabled: true,
            priority: 'high',
            description: 'Ensures GDPR and privacy compliance',
            condition: 'personal data detected',
            action: 'Redact sensitive information',
        },
    ] as BehaviorRule[],

    knowledgeBase: [
        {
            id: 'kb-1',
            title: 'Product Documentation',
            category: 'documentation',
            content: 'Comprehensive guide to all product features and functionality...',
            size: 1250,
            lastUpdated: '2025-12-08T10:00:00Z',
            usageCount: 3420,
            enabled: true,
        },
        {
            id: 'kb-2',
            title: 'Common FAQs',
            category: 'faq',
            content: 'Frequently asked questions about billing, features, and support...',
            size: 450,
            lastUpdated: '2025-12-10T14:00:00Z',
            usageCount: 5680,
            enabled: true,
        },
        {
            id: 'kb-3',
            title: 'Company Policies',
            category: 'policy',
            content: 'Internal policies regarding returns, refunds, and service level agreements...',
            size: 320,
            lastUpdated: '2025-11-15T09:00:00Z',
            usageCount: 1240,
            enabled: true,
        },
    ] as KnowledgeBaseItem[],

    integrations: [
        {
            id: 'int-1',
            name: 'CRM Integration',
            type: 'api',
            status: 'connected',
            endpoint: 'https://api.crm.example.com/v1',
            lastSync: '2025-12-11T08:00:00Z',
            enabled: true,
        },
        {
            id: 'int-2',
            name: 'Knowledge Database',
            type: 'database',
            status: 'connected',
            endpoint: 'postgres://knowledge.db',
            lastSync: '2025-12-11T06:30:00Z',
            enabled: true,
        },
        {
            id: 'int-3',
            name: 'Slack Notifications',
            type: 'webhook',
            status: 'disconnected',
            endpoint: 'https://hooks.slack.com/services/...',
            lastSync: '2025-12-09T18:00:00Z',
            enabled: false,
        },
    ] as IntegrationSetting[],
};
