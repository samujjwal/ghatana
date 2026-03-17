/**
 * Agent Panel Component
 * 
 * Displays available agents, their status, and allows manual agent execution.
 * Can be integrated into the workflow board as a side panel or drawer.
 * 
 * @doc.type component
 * @doc.purpose Agent management and execution UI
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState } from 'react';
import { Box, Card, CardContent, Typography, Chip, IconButton, Button, Tooltip, InteractiveList as List, ListItem, ListItemText, ListItemText as ListItemSecondaryAction, Divider, LinearProgress, Dialog, DialogTitle, DialogContent, DialogActions, TextField, Select, MenuItem, FormControl, InputLabel, Alert } from '@ghatana/ui';
import { Bot as AgentIcon, Play as ExecuteIcon, Settings as SettingsIcon, TrendingUp as PerformanceIcon, History as HistoryIcon } from 'lucide-react';

import {
    useAgents,
    useAgentExecution,
    useWorkflowAnalytics,
} from '@ghatana/yappc-canvas';
import type {
    WorkflowAgent,
    WorkflowAgentRole,
    AgentExecutionResult,
    AgentCapability,
} from '@ghatana/yappc-types/devsecops/workflow-automation';
import type { Item } from '@ghatana/yappc-types/devsecops';

// ============================================================================
// TYPES
// ============================================================================

export interface AgentPanelProps {
    /** Current item being processed (optional) */
    currentItem?: Item;
    /** Callback when agent execution completes */
    onExecutionComplete?: (result: unknown) => void;
}

interface AgentExecutionDialogProps {
    agent: WorkflowAgent | null;
    item?: Item;
    open: boolean;
    onClose: () => void;
    onExecute: (agentId: string, input: Record<string, unknown>, priority: 'low' | 'medium' | 'high') => void;
}

// ============================================================================
// AGENT TYPE COLORS
// ============================================================================

const AGENT_ROLE_COLORS: Record<WorkflowAgentRole | string, 'primary' | 'secondary' | 'success' | 'error' | 'info' | 'warning'> = {
    orchestrator: 'primary',
    'product-manager': 'secondary',
    architect: 'info',
    developer: 'success',
    'security-engineer': 'error',
    'devops-engineer': 'warning',
    'qa-engineer': 'info',
    reviewer: 'secondary',
    analyzer: 'primary',
};

// ============================================================================
// AGENT EXECUTION DIALOG
// ============================================================================

const AgentExecutionDialog: React.FC<AgentExecutionDialogProps> = ({
    agent,
    item,
    open,
    onClose,
    onExecute,
}) => {
    const [input, setInput] = useState('');
    const [priority, setPriority] = useState<'low' | 'medium' | 'high'>('medium');

    const handleExecute = () => {
        if (!agent) return;

        const executionInput: Record<string, unknown> = {
            taskId: item?.id,
            taskType: item?.type,
            taskData: item,
            userInput: input || undefined,
        };

        onExecute(agent.id, executionInput, priority);
        onClose();
        setInput('');
    };

    return (
        <Dialog open={open} onClose={onClose} size="sm" fullWidth>
            <DialogTitle>
                <Box className="flex items-center gap-2">
                    <AgentIcon />
                    <Typography as="h6">{agent?.name}</Typography>
                </Box>
            </DialogTitle>
            <DialogContent>
                <Box className="flex flex-col gap-4 mt-2">
                    {agent && (
                        <Alert severity="info">
                            <Typography as="p" className="text-sm">
                                {agent.description}
                            </Typography>
                        </Alert>
                    )}

                    {item && (
                        <Box>
                            <Typography as="p" className="text-sm font-medium" gutterBottom>
                                Processing Item:
                            </Typography>
                            <Typography as="p" className="text-sm" color="text.secondary">
                                {item.title}
                            </Typography>
                        </Box>
                    )}

                    <TextField
                        label="Additional Instructions (Optional)"
                        multiline
                        rows={4}
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        placeholder="Provide any additional context or instructions for the agent..."
                    />

                    <FormControl fullWidth>
                        <InputLabel>Priority</InputLabel>
                        <Select
                            value={priority}
                            onChange={(e) => setPriority(e.target.value as unknown)}
                            label="Priority"
                        >
                            <MenuItem value="low">Low</MenuItem>
                            <MenuItem value="medium">Medium</MenuItem>
                            <MenuItem value="high">High</MenuItem>
                        </Select>
                    </FormControl>

                    {agent?.capabilities && (
                        <Box>
                            <Typography as="p" className="text-sm font-medium" gutterBottom>
                                Agent Capabilities:
                            </Typography>
                            <Box className="flex flex-wrap gap-1">
                                {agent.capabilities.map((cap: AgentCapability) => (
                                    <Chip
                                        key={cap.id}
                                        label={cap.name}
                                        size="sm"
                                        variant="outlined"
                                    />
                                ))}
                            </Box>
                        </Box>
                    )}
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button
                    onClick={handleExecute}
                    variant="solid"
                    startIcon={<ExecuteIcon />}
                    disabled={!agent}
                >
                    Execute Agent
                </Button>
            </DialogActions>
        </Dialog>
    );
};

// ============================================================================
// AGENT CARD
// ============================================================================

interface AgentCardProps {
    agent: WorkflowAgent;
    onExecute: () => void;
    onConfigure: () => void;
}

const AgentCard: React.FC<AgentCardProps> = ({ agent, onExecute, onConfigure }) => {
    const getSuccessRate = () => {
        if (agent.performance && agent.performance.totalExecutions > 0) {
            return agent.performance.successfulExecutions / agent.performance.totalExecutions;
        }
        return 0;
    };

    const getStatusColor = () => {
        if (!agent.enabled) return 'default';
        if (agent.performance) {
            const successRate = getSuccessRate();
            if (successRate >= 0.9) return 'success';
            if (successRate >= 0.7) return 'warning';
            return 'error';
        }
        return 'default';
    };

    return (
        <Card variant="outlined" className="mb-2">
            <CardContent className="p-4 last:pb-4">
                <Box className="flex items-start justify-between">
                    <Box className="flex-1">
                        <Box className="flex items-center gap-2 mb-1">
                            <AgentIcon size={16} />
                            <Typography as="p" className="text-sm font-medium">{agent.name}</Typography>
                            <Chip
                                label={agent.role}
                                size="sm"
                                color={AGENT_ROLE_COLORS[agent.role] || 'default'}
                            />
                        </Box>

                        <Typography as="p" className="text-sm" color="text.secondary" className="mb-2">
                            {agent.description}
                        </Typography>

                        {agent.performance && (
                            <Box className="flex gap-4 mb-2">
                                <Tooltip title="Success Rate">
                                    <Chip
                                        label={`${(getSuccessRate() * 100).toFixed(0)}%`}
                                        size="sm"
                                        color={getStatusColor()}
                                        icon={<PerformanceIcon />}
                                    />
                                </Tooltip>
                                <Tooltip title="Total Executions">
                                    <Chip
                                        label={agent.performance.totalExecutions}
                                        size="sm"
                                        variant="outlined"
                                    />
                                </Tooltip>
                            </Box>
                        )}

                        <Box className="flex flex-wrap gap-1">
                            {agent.capabilities?.slice(0, 3).map((cap: AgentCapability) => (
                                <Chip
                                    key={cap.id}
                                    label={cap.name}
                                    size="sm"
                                    variant="outlined"
                                />
                            ))}
                            {agent.capabilities && agent.capabilities.length > 3 && (
                                <Chip
                                    label={`+${agent.capabilities.length - 3}`}
                                    size="sm"
                                    variant="outlined"
                                />
                            )}
                        </Box>
                    </Box>

                    <Box className="flex flex-col gap-1">
                        <Tooltip title={agent.enabled ? 'Execute' : 'Agent Disabled'}>
                            <span>
                                <IconButton
                                    size="sm"
                                    onClick={onExecute}
                                    disabled={!agent.enabled}
                                    tone="primary"
                                >
                                    <ExecuteIcon />
                                </IconButton>
                            </span>
                        </Tooltip>
                        <Tooltip title="Configure">
                            <IconButton size="sm" onClick={onConfigure}>
                                <SettingsIcon />
                            </IconButton>
                        </Tooltip>
                    </Box>
                </Box>
            </CardContent>
        </Card>
    );
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

export const AgentPanel: React.FC<AgentPanelProps> = ({
    currentItem,
    onExecutionComplete,
}) => {
    const { agents, agentsByRole } = useAgents();
    const { executeAgent, pendingExecutions, history } = useAgentExecution();
    const { stats } = useWorkflowAnalytics();

    const [selectedAgent, setSelectedAgent] = useState<WorkflowAgent | null>(null);
    const [executionDialogOpen, setExecutionDialogOpen] = useState(false);
    const [showHistory, setShowHistory] = useState(false);

    const handleExecuteAgent = (agent: WorkflowAgent) => {
        setSelectedAgent(agent);
        setExecutionDialogOpen(true);
    };

    const handleConfirmExecution = async (
        agentId: string,
        input: Record<string, unknown>,
        priority: 'low' | 'medium' | 'high' = 'medium'
    ) => {
        const request = await executeAgent({
            agentId,
            input,
            itemId: currentItem?.id,
            priority,
        });

        // Monitor execution (in real implementation, use WebSocket or polling)
        if (onExecutionComplete) {
            setTimeout(() => {
                const result = history.find((r: AgentExecutionResult) => r.requestId === request.id);
                if (result) {
                    onExecutionComplete(result);
                }
            }, 3000);
        }
    };

    const handleConfigureAgent = (agent: WorkflowAgent) => {
        // TODO: Open agent configuration dialog
        console.log('Configure agent:', agent);
    };

    return (
        <Box className="h-full flex flex-col">
            {/* Header */}
            <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                <Typography as="h6" gutterBottom>
                    AI Agents
                </Typography>

                {/* Stats */}
                <Box className="flex gap-2 mb-2">
                    <Chip
                        label={`${agents.filter((a: WorkflowAgent) => a.enabled).length} Active`}
                        size="sm"
                        tone="success"
                    />
                    <Chip
                        label={`${pendingExecutions.length} Queued`}
                        size="sm"
                        tone="warning"
                    />
                    <Chip
                        label={`${(stats.successRate * 100).toFixed(0)}% Success`}
                        size="sm"
                        tone="info"
                    />
                </Box>

                {pendingExecutions.length > 0 && (
                    <Box className="mt-2">
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            Processing {pendingExecutions.length} request(s)...
                        </Typography>
                        <LinearProgress className="mt-1" />
                    </Box>
                )}
            </Box>

            {/* Agent List */}
            <Box className="flex-1 overflow-auto p-4">
                {currentItem && (
                    <Alert severity="info" className="mb-4">
                        <Typography as="p" className="text-sm">
                            Select an agent to process: <strong>{currentItem.title}</strong>
                        </Typography>
                    </Alert>
                )}

                {(Object.entries(agentsByRole) as [string, WorkflowAgent[]][]).map(([role, roleAgents]) => (
                    <Box key={role} className="mb-6">
                        <Typography as="p" className="text-sm font-medium" color="text.secondary" className="mb-2">
                            {role.replace('-', ' ').toUpperCase()}
                        </Typography>
                        {roleAgents.map((agent) => (
                            <AgentCard
                                key={agent.id}
                                agent={agent}
                                onExecute={() => handleExecuteAgent(agent)}
                                onConfigure={() => handleConfigureAgent(agent)}
                            />
                        ))}
                    </Box>
                ))}

                {agents.length === 0 && (
                    <Box className="text-center py-8">
                        <AgentIcon className="mb-2 text-5xl text-gray-500 dark:text-gray-400" />
                        <Typography as="p" className="text-sm" color="text.secondary">
                            No agents available
                        </Typography>
                        <Button size="sm" className="mt-2">
                            Register Agent
                        </Button>
                    </Box>
                )}
            </Box>

            {/* Footer with History Toggle */}
            <Box className="p-4 border-gray-200 dark:border-gray-700 border-t" >
                <Button
                    fullWidth
                    startIcon={<HistoryIcon />}
                    onClick={() => setShowHistory(!showHistory)}
                    variant="outlined"
                >
                    {showHistory ? 'Hide' : 'Show'} Execution History
                </Button>

                {showHistory && history.length > 0 && (
                    <List className="mt-2 overflow-auto max-h-[200px]">
                        {history.slice(-10).reverse().map((result: AgentExecutionResult) => (
                            <React.Fragment key={result.id}>
                                <ListItem dense>
                                    <ListItemText
                                        primary={agents.find((a: WorkflowAgent) => a.id === result.agentId)?.name || 'Unknown Agent'}
                                        secondary={`${result.status} - ${new Date(result.completedAt).toLocaleTimeString()}`}
                                    />
                                    <ListItemSecondaryAction>
                                        <Chip
                                            label={`${(result.confidence * 100).toFixed(0)}%`}
                                            size="sm"
                                            color={result.status === 'success' ? 'success' : 'error'}
                                        />
                                    </ListItemSecondaryAction>
                                </ListItem>
                                <Divider />
                            </React.Fragment>
                        ))}
                    </List>
                )}
            </Box>

            {/* Execution Dialog */}
            <AgentExecutionDialog
                agent={selectedAgent}
                item={currentItem}
                open={executionDialogOpen}
                onClose={() => setExecutionDialogOpen(false)}
                onExecute={handleConfirmExecution}
            />
        </Box>
    );
};

export default AgentPanel;
