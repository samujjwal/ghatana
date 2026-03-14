/**
 * WorkItemExecutionPanel Component
 *
 * <p><b>Purpose</b><br>
 * Provides embedded tools and integrations for completing work items
 * directly from the dashboard. This is the core of the embedded task
 * execution model - tasks are not just tracked, they are executed.
 *
 * <p><b>Features</b><br>
 * - Canvas for visual work (diagrams, architecture)
 * - Terminal for command execution
 * - Code editor for inline editing
 * - VCS integration (PRs, commits)
 * - CI/CD pipeline status
 * - Observability metrics
 * - Security scan results
 * - AI assistant for guidance
 *
 * <p><b>Usage</b><br>
 * ```tsx
 * <WorkItemExecutionPanel
 *   workItemId="WI-1001"
 *   onActionComplete={(action) => console.log('Action completed:', action)}
 * />
 * ```
 *
 * @doc.type component
 * @doc.purpose Embedded task execution panel with tools
 * @doc.layer product
 * @doc.pattern Execution Context
 */

import { useState, useCallback } from 'react';
import { Card, Box, Button, Chip, Grid } from '@ghatana/design-system';
import { useAtom } from 'jotai';
import { useExecutionContext } from '@/hooks/useUnifiedPersona';
import { selectedExecutionToolAtom, type ExecutionToolTab } from '@/state/jotai/atoms';
import type { WorkItemAction } from '@/shared/types/org';

// ============================================================================
// TYPES
// ============================================================================

export interface WorkItemExecutionPanelProps {
    /** Work item ID to load execution context for */
    workItemId: string;
    /** Callback when an action is completed */
    onActionComplete?: (action: WorkItemAction) => void;
    /** Callback when moving to next step */
    onNextStep?: (stepId: string) => void;
    /** Additional CSS classes */
    className?: string;
}

// ============================================================================
// TOOL TAB COMPONENTS
// ============================================================================

/**
 * Canvas Tool Tab - For visual work like diagrams
 */
function CanvasToolTab({ context }: { context: ReturnType<typeof useExecutionContext>['context'] }) {
    if (!context?.canvas?.enabled) {
        return (
            <div className="p-8 text-center text-slate-500 dark:text-neutral-500">
                Canvas is not available for this work item phase.
            </div>
        );
    }

    return (
        <div className="p-4">
            <div className="mb-4 flex items-center justify-between">
                <h4 className="font-medium text-slate-900 dark:text-neutral-200">
                    Canvas
                </h4>
                {context.canvas.template && (
                    <Chip label={`Template: ${context.canvas.template}`} tone="primary" size="sm" />
                )}
            </div>
            
            {/* Canvas placeholder - would integrate with actual canvas component */}
            <div className="border-2 border-dashed border-slate-300 dark:border-neutral-600 rounded-lg p-8 min-h-[400px] flex items-center justify-center bg-slate-50 dark:bg-neutral-800">
                <div className="text-center">
                    <div className="text-4xl mb-4">🎨</div>
                    <p className="text-slate-600 dark:text-neutral-400 mb-4">
                        Canvas workspace for diagrams and visual work
                    </p>
                    <Button variant="outline" size="sm">
                        Open Canvas Editor
                    </Button>
                </div>
            </div>

            {context.canvas.artifacts.length > 0 && (
                <div className="mt-4">
                    <h5 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                        Artifacts ({context.canvas.artifacts.length})
                    </h5>
                    <div className="space-y-2">
                        {context.canvas.artifacts.map((artifact) => (
                            <div
                                key={artifact.id}
                                className="p-2 bg-slate-100 dark:bg-neutral-700 rounded flex items-center justify-between"
                            >
                                <span className="text-sm text-slate-700 dark:text-neutral-300">
                                    {artifact.title}
                                </span>
                                <Chip label={artifact.type} size="sm" />
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

/**
 * Terminal Tool Tab - For command execution
 */
function TerminalToolTab({ context }: { context: ReturnType<typeof useExecutionContext>['context'] }) {
    const [command, setCommand] = useState('');
    const [output, setOutput] = useState<string[]>([
        '$ Welcome to the embedded terminal',
        '$ Working directory: ' + (context?.terminal?.workingDirectory || '/workspace'),
        '',
    ]);

    const handleRunCommand = useCallback(() => {
        if (!command.trim()) return;
        setOutput((prev) => [...prev, `$ ${command}`, 'Command execution simulated...', '']);
        setCommand('');
    }, [command]);

    if (!context?.terminal?.enabled) {
        return (
            <div className="p-8 text-center text-slate-500 dark:text-neutral-500">
                Terminal is not available for this work item phase.
            </div>
        );
    }

    return (
        <div className="p-4">
            <div className="mb-4 flex items-center justify-between">
                <h4 className="font-medium text-slate-900 dark:text-neutral-200">
                    Terminal
                </h4>
                <span className="text-sm text-slate-500 dark:text-neutral-500">
                    {context.terminal.workingDirectory}
                </span>
            </div>

            {/* Terminal output */}
            <div className="bg-slate-900 dark:bg-black rounded-lg p-4 font-mono text-sm text-green-400 min-h-[300px] max-h-[400px] overflow-y-auto">
                {output.map((line, i) => (
                    <div key={i} className={line.startsWith('$') ? 'text-green-400' : 'text-slate-400'}>
                        {line || '\u00A0'}
                    </div>
                ))}
            </div>

            {/* Command input */}
            <div className="mt-4 flex gap-2">
                <input
                    type="text"
                    value={command}
                    onChange={(e) => setCommand(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleRunCommand()}
                    placeholder="Enter command..."
                    className="flex-1 px-3 py-2 bg-slate-100 dark:bg-neutral-800 border border-slate-300 dark:border-neutral-600 rounded-lg text-sm font-mono"
                />
                <Button variant="solid" size="sm" onClick={handleRunCommand}>
                    Run
                </Button>
            </div>

            {context.terminal.allowedCommands && (
                <div className="mt-4">
                    <p className="text-xs text-slate-500 dark:text-neutral-500 mb-2">
                        Allowed commands:
                    </p>
                    <div className="flex flex-wrap gap-1">
                        {context.terminal.allowedCommands.map((cmd) => (
                            <Chip key={cmd} label={cmd} size="sm" />
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

/**
 * Editor Tool Tab - For code editing
 */
function EditorToolTab({ context }: { context: ReturnType<typeof useExecutionContext>['context'] }) {
    if (!context?.editor?.enabled) {
        return (
            <div className="p-8 text-center text-slate-500 dark:text-neutral-500">
                Editor is not available for this work item phase.
            </div>
        );
    }

    return (
        <div className="p-4">
            <div className="mb-4 flex items-center justify-between">
                <h4 className="font-medium text-slate-900 dark:text-neutral-200">
                    Code Editor
                </h4>
                <span className="text-sm text-slate-500 dark:text-neutral-500">
                    {context.editor.files.length} files
                </span>
            </div>

            {/* File list */}
            <div className="space-y-2 mb-4">
                {context.editor.files.map((file) => (
                    <div
                        key={file.path}
                        className="p-3 bg-slate-100 dark:bg-neutral-800 rounded-lg flex items-center justify-between cursor-pointer hover:bg-slate-200 dark:hover:bg-neutral-700 transition-colors"
                    >
                        <div className="flex items-center gap-2">
                            <span className="text-lg">📄</span>
                            <span className="text-sm font-mono text-slate-700 dark:text-neutral-300">
                                {file.path}
                            </span>
                        </div>
                        <Chip label={file.language} size="sm" />
                    </div>
                ))}
            </div>

            {/* Editor placeholder */}
            <div className="border border-slate-300 dark:border-neutral-600 rounded-lg overflow-hidden">
                <div className="bg-slate-200 dark:bg-neutral-700 px-4 py-2 flex items-center justify-between">
                    <span className="text-sm font-mono text-slate-600 dark:text-neutral-400">
                        {context.editor.files[0]?.path || 'No file selected'}
                    </span>
                    <Button variant="ghost" size="sm">
                        Open in IDE
                    </Button>
                </div>
                <div className="bg-slate-900 dark:bg-black p-4 font-mono text-sm text-slate-300 min-h-[300px]">
                    <pre className="text-green-400">// Code editor placeholder</pre>
                    <pre className="text-slate-500">// Select a file to view/edit</pre>
                </div>
            </div>
        </div>
    );
}

/**
 * VCS Tool Tab - Version control integration
 */
function VCSToolTab({ context }: { context: ReturnType<typeof useExecutionContext>['context'] }) {
    if (!context?.vcs) {
        return (
            <div className="p-8 text-center text-slate-500 dark:text-neutral-500">
                VCS integration is not configured for this work item.
            </div>
        );
    }

    return (
        <div className="p-4">
            <div className="mb-4">
                <h4 className="font-medium text-slate-900 dark:text-neutral-200 mb-2">
                    Version Control
                </h4>
                <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-neutral-400">
                    <span>Branch:</span>
                    <Chip label={context.vcs.branch} tone="primary" size="sm" />
                </div>
            </div>

            {/* Pull Requests */}
            {context.vcs.pullRequests.length > 0 && (
                <div className="mb-4">
                    <h5 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                        Pull Requests
                    </h5>
                    <div className="space-y-2">
                        {context.vcs.pullRequests.map((pr) => (
                            <a
                                key={pr.id}
                                href={pr.url}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="block p-3 bg-slate-100 dark:bg-neutral-800 rounded-lg hover:bg-slate-200 dark:hover:bg-neutral-700 transition-colors"
                            >
                                <div className="flex items-center justify-between mb-1">
                                    <span className="text-sm font-medium text-slate-900 dark:text-neutral-200">
                                        {pr.title}
                                    </span>
                                    <Chip
                                        label={pr.status}
                                        tone={pr.status === 'merged' ? 'success' : pr.status === 'open' ? 'primary' : 'default'}
                                        size="sm"
                                    />
                                </div>
                                <div className="flex items-center gap-2 text-xs text-slate-500 dark:text-neutral-500">
                                    <span>Review: {pr.reviewStatus}</span>
                                </div>
                            </a>
                        ))}
                    </div>
                </div>
            )}

            {/* Recent Commits */}
            {context.vcs.recentCommits.length > 0 && (
                <div>
                    <h5 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                        Recent Commits
                    </h5>
                    <div className="space-y-2">
                        {context.vcs.recentCommits.map((commit) => (
                            <div
                                key={commit.sha}
                                className="p-2 bg-slate-50 dark:bg-neutral-800/50 rounded"
                            >
                                <div className="flex items-center gap-2 mb-1">
                                    <code className="text-xs text-blue-600 dark:text-blue-400">
                                        {commit.sha.slice(0, 7)}
                                    </code>
                                    <span className="text-sm text-slate-700 dark:text-neutral-300">
                                        {commit.message}
                                    </span>
                                </div>
                                <div className="text-xs text-slate-500 dark:text-neutral-500">
                                    {commit.author} • {new Date(commit.timestamp).toLocaleDateString()}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

/**
 * CI Tool Tab - CI/CD pipeline status
 */
function CIToolTab({ context }: { context: ReturnType<typeof useExecutionContext>['context'] }) {
    if (!context?.ci) {
        return (
            <div className="p-8 text-center text-slate-500 dark:text-neutral-500">
                CI/CD integration is not configured for this work item.
            </div>
        );
    }

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'passed':
                return 'success';
            case 'failed':
                return 'danger';
            case 'running':
                return 'warning';
            default:
                return 'default';
        }
    };

    return (
        <div className="p-4">
            <div className="mb-4 flex items-center justify-between">
                <h4 className="font-medium text-slate-900 dark:text-neutral-200">
                    {context.ci.name}
                </h4>
                <Chip
                    label={context.ci.status}
                    tone={getStatusColor(context.ci.status) as 'success' | 'danger' | 'warning' | 'default'}
                    size="sm"
                />
            </div>

            {/* Pipeline stages */}
            <div className="space-y-2">
                {context.ci.stages.map((stage) => (
                    <div
                        key={stage.id}
                        className="p-3 bg-slate-100 dark:bg-neutral-800 rounded-lg flex items-center justify-between"
                    >
                        <div className="flex items-center gap-3">
                            <span className={`w-3 h-3 rounded-full ${
                                stage.status === 'passed' ? 'bg-green-500' :
                                stage.status === 'failed' ? 'bg-red-500' :
                                stage.status === 'running' ? 'bg-yellow-500 animate-pulse' :
                                'bg-gray-400'
                            }`} />
                            <span className="text-sm text-slate-700 dark:text-neutral-300">
                                {stage.name}
                            </span>
                        </div>
                        {stage.duration && (
                            <span className="text-xs text-slate-500 dark:text-neutral-500">
                                {Math.round(stage.duration / 60)}m
                            </span>
                        )}
                    </div>
                ))}
            </div>

            {context.ci.url && (
                <div className="mt-4">
                    <a
                        href={context.ci.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-sm text-blue-600 dark:text-blue-400 hover:underline"
                    >
                        View full pipeline →
                    </a>
                </div>
            )}
        </div>
    );
}

/**
 * Observability Tool Tab - Metrics and monitoring
 */
function ObservabilityToolTab({ context }: { context: ReturnType<typeof useExecutionContext>['context'] }) {
    if (!context?.observability) {
        return (
            <div className="p-8 text-center text-slate-500 dark:text-neutral-500">
                Observability integration is not configured.
            </div>
        );
    }

    const getTrendIcon = (trend: string) => {
        switch (trend) {
            case 'up':
                return '↑';
            case 'down':
                return '↓';
            default:
                return '→';
        }
    };

    const getTrendColor = (trend: string, name: string) => {
        // For error rate, down is good; for others, up might be good
        if (name.toLowerCase().includes('error')) {
            return trend === 'down' ? 'text-green-600' : trend === 'up' ? 'text-red-600' : 'text-slate-600';
        }
        return trend === 'up' ? 'text-green-600' : trend === 'down' ? 'text-red-600' : 'text-slate-600';
    };

    return (
        <div className="p-4">
            <div className="mb-4 flex items-center justify-between">
                <h4 className="font-medium text-slate-900 dark:text-neutral-200">
                    Observability
                </h4>
                {context.observability.dashboardUrl && (
                    <a
                        href={context.observability.dashboardUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-sm text-blue-600 dark:text-blue-400 hover:underline"
                    >
                        Open Dashboard
                    </a>
                )}
            </div>

            {/* Metrics */}
            <Grid columns={3} gap={3}>
                {context.observability.metrics.map((metric) => (
                    <div
                        key={metric.name}
                        className="p-3 bg-slate-100 dark:bg-neutral-800 rounded-lg"
                    >
                        <p className="text-xs text-slate-500 dark:text-neutral-500 mb-1">
                            {metric.name}
                        </p>
                        <div className="flex items-baseline gap-2">
                            <span className="text-xl font-bold text-slate-900 dark:text-neutral-200">
                                {metric.value}
                            </span>
                            <span className="text-sm text-slate-600 dark:text-neutral-400">
                                {metric.unit}
                            </span>
                            <span className={`text-sm ${getTrendColor(metric.trend, metric.name)}`}>
                                {getTrendIcon(metric.trend)}
                            </span>
                        </div>
                    </div>
                ))}
            </Grid>

            {/* Alerts */}
            {context.observability.alerts.length > 0 && (
                <div className="mt-4">
                    <h5 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                        Active Alerts
                    </h5>
                    <div className="space-y-2">
                        {context.observability.alerts.map((alert) => (
                            <div
                                key={alert.id}
                                className={`p-3 rounded-lg border-l-4 ${
                                    alert.severity === 'critical' ? 'bg-red-50 dark:bg-red-900/20 border-red-500' :
                                    alert.severity === 'warning' ? 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-500' :
                                    'bg-blue-50 dark:bg-blue-900/20 border-blue-500'
                                }`}
                            >
                                <p className="text-sm text-slate-900 dark:text-neutral-200">
                                    {alert.message}
                                </p>
                                <p className="text-xs text-slate-500 dark:text-neutral-500 mt-1">
                                    {new Date(alert.timestamp).toLocaleString()}
                                </p>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

/**
 * Security Tool Tab - Security scan results
 */
function SecurityToolTab({ context }: { context: ReturnType<typeof useExecutionContext>['context'] }) {
    if (!context?.security) {
        return (
            <div className="p-8 text-center text-slate-500 dark:text-neutral-500">
                Security integration is not configured.
            </div>
        );
    }

    const { vulnerabilities, complianceStatus, lastScanAt } = context.security;
    const totalVulns = vulnerabilities.critical + vulnerabilities.high + vulnerabilities.medium + vulnerabilities.low;

    return (
        <div className="p-4">
            <div className="mb-4 flex items-center justify-between">
                <h4 className="font-medium text-slate-900 dark:text-neutral-200">
                    Security
                </h4>
                <Chip
                    label={complianceStatus}
                    tone={complianceStatus === 'compliant' ? 'success' : complianceStatus === 'non-compliant' ? 'danger' : 'default'}
                    size="sm"
                />
            </div>

            {/* Vulnerability summary */}
            <div className="mb-4 p-4 bg-slate-100 dark:bg-neutral-800 rounded-lg">
                <p className="text-sm text-slate-600 dark:text-neutral-400 mb-2">
                    Vulnerabilities ({totalVulns} total)
                </p>
                <Grid columns={4} gap={2}>
                    <div className="text-center">
                        <p className="text-2xl font-bold text-red-600">{vulnerabilities.critical}</p>
                        <p className="text-xs text-slate-500">Critical</p>
                    </div>
                    <div className="text-center">
                        <p className="text-2xl font-bold text-orange-600">{vulnerabilities.high}</p>
                        <p className="text-xs text-slate-500">High</p>
                    </div>
                    <div className="text-center">
                        <p className="text-2xl font-bold text-yellow-600">{vulnerabilities.medium}</p>
                        <p className="text-xs text-slate-500">Medium</p>
                    </div>
                    <div className="text-center">
                        <p className="text-2xl font-bold text-slate-600">{vulnerabilities.low}</p>
                        <p className="text-xs text-slate-500">Low</p>
                    </div>
                </Grid>
            </div>

            {lastScanAt && (
                <p className="text-xs text-slate-500 dark:text-neutral-500">
                    Last scan: {new Date(lastScanAt).toLocaleString()}
                </p>
            )}

            {context.security.scanResultsUrl && (
                <div className="mt-4">
                    <a
                        href={context.security.scanResultsUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-sm text-blue-600 dark:text-blue-400 hover:underline"
                    >
                        View full scan results →
                    </a>
                </div>
            )}
        </div>
    );
}

/**
 * AI Assistant Tool Tab
 */
function AIToolTab({ context }: { context: ReturnType<typeof useExecutionContext>['context'] }) {
    const [message, setMessage] = useState('');

    if (!context?.aiAssistant?.enabled) {
        return (
            <div className="p-8 text-center text-slate-500 dark:text-neutral-500">
                AI Assistant is not available.
            </div>
        );
    }

    return (
        <div className="p-4">
            <div className="mb-4">
                <h4 className="font-medium text-slate-900 dark:text-neutral-200">
                    AI Assistant
                </h4>
                <p className="text-sm text-slate-500 dark:text-neutral-500">
                    Context: {context.aiAssistant.context}
                </p>
            </div>

            {/* Suggested actions */}
            {context.aiAssistant.suggestedActions.length > 0 && (
                <div className="mb-4">
                    <h5 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                        Suggested Actions
                    </h5>
                    <div className="space-y-2">
                        {context.aiAssistant.suggestedActions.map((suggestion) => (
                            <div
                                key={suggestion.id}
                                className="p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg cursor-pointer hover:bg-blue-100 dark:hover:bg-blue-900/30 transition-colors"
                            >
                                <div className="flex items-center justify-between mb-1">
                                    <span className="text-sm font-medium text-slate-900 dark:text-neutral-200">
                                        {suggestion.action}
                                    </span>
                                    <span className="text-xs text-slate-500 dark:text-neutral-500">
                                        {Math.round(suggestion.confidence * 100)}% confidence
                                    </span>
                                </div>
                                <p className="text-xs text-slate-600 dark:text-neutral-400">
                                    {suggestion.description}
                                </p>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Chat input */}
            <div className="flex gap-2">
                <input
                    type="text"
                    value={message}
                    onChange={(e) => setMessage(e.target.value)}
                    placeholder="Ask AI for help..."
                    className="flex-1 px-3 py-2 bg-slate-100 dark:bg-neutral-800 border border-slate-300 dark:border-neutral-600 rounded-lg text-sm"
                />
                <Button variant="solid" size="sm">
                    Ask
                </Button>
            </div>
        </div>
    );
}

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const TOOL_TABS: { id: ExecutionToolTab; label: string; icon: string }[] = [
    { id: 'canvas', label: 'Canvas', icon: '🎨' },
    { id: 'terminal', label: 'Terminal', icon: '💻' },
    { id: 'editor', label: 'Editor', icon: '📝' },
    { id: 'vcs', label: 'VCS', icon: '🔀' },
    { id: 'ci', label: 'CI/CD', icon: '🔄' },
    { id: 'observability', label: 'Metrics', icon: '📊' },
    { id: 'security', label: 'Security', icon: '🔒' },
    { id: 'ai', label: 'AI', icon: '🤖' },
];

export function WorkItemExecutionPanel({
    workItemId,
    onActionComplete,
    onNextStep,
    className = '',
}: WorkItemExecutionPanelProps) {
    const { context, isLoading, isError } = useExecutionContext(workItemId);
    const [selectedTool, setSelectedTool] = useAtom(selectedExecutionToolAtom);

    const handleActionClick = useCallback((action: WorkItemAction) => {
        if (!action.enabled) return;
        // Execute action
        console.log('Executing action:', action);
        onActionComplete?.(action);
    }, [onActionComplete]);

    const handleNextStep = useCallback(() => {
        if (context?.nextStepId) {
            onNextStep?.(context.nextStepId);
        }
    }, [context?.nextStepId, onNextStep]);

    if (isLoading) {
        return (
            <Card className={className}>
                <Box className="p-8 text-center">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto mb-4" />
                    <p className="text-slate-600 dark:text-neutral-400">Loading execution context...</p>
                </Box>
            </Card>
        );
    }

    if (isError || !context) {
        return (
            <Card className={className}>
                <Box className="p-8 text-center">
                    <p className="text-red-600 dark:text-red-400">Failed to load execution context</p>
                </Box>
            </Card>
        );
    }

    const renderToolContent = () => {
        switch (selectedTool) {
            case 'canvas':
                return <CanvasToolTab context={context} />;
            case 'terminal':
                return <TerminalToolTab context={context} />;
            case 'editor':
                return <EditorToolTab context={context} />;
            case 'vcs':
                return <VCSToolTab context={context} />;
            case 'ci':
                return <CIToolTab context={context} />;
            case 'observability':
                return <ObservabilityToolTab context={context} />;
            case 'security':
                return <SecurityToolTab context={context} />;
            case 'ai':
                return <AIToolTab context={context} />;
            default:
                return null;
        }
    };

    return (
        <Card className={className}>
            {/* Tool tabs */}
            <Box className="border-b border-slate-200 dark:border-neutral-700">
                <div className="flex overflow-x-auto">
                    {TOOL_TABS.map((tab) => (
                        <button
                            key={tab.id}
                            onClick={() => setSelectedTool(tab.id)}
                            className={`px-4 py-3 text-sm font-medium whitespace-nowrap border-b-2 transition-colors ${
                                selectedTool === tab.id
                                    ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                                    : 'border-transparent text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-neutral-200'
                            }`}
                        >
                            <span className="mr-2">{tab.icon}</span>
                            {tab.label}
                        </button>
                    ))}
                </div>
            </Box>

            {/* Tool content */}
            <Box className="min-h-[400px]">
                {renderToolContent()}
            </Box>

            {/* Actions footer */}
            <Box className="p-4 border-t border-slate-200 dark:border-neutral-700 bg-slate-50 dark:bg-neutral-800/50">
                <div className="flex items-center justify-between">
                    <div className="flex gap-2">
                        {context.availableActions.map((action) => (
                            <Button
                                key={action.id}
                                variant={action.primary ? 'solid' : 'outline'}
                                size="sm"
                                onClick={() => handleActionClick(action)}
                                disabled={!action.enabled}
                                title={action.disabledReason}
                            >
                                {action.icon && <span className="mr-1">{action.icon}</span>}
                                {action.label}
                            </Button>
                        ))}
                    </div>

                    {context.nextStepId && (
                        <Button variant="solid" size="sm" onClick={handleNextStep}>
                            Next Step →
                        </Button>
                    )}
                </div>
            </Box>
        </Card>
    );
}

export default WorkItemExecutionPanel;
