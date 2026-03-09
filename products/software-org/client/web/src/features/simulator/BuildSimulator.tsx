import { useState } from "react";
import { useSearchParams } from "react-router";
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { useWorkflows, useAgents, useRunSimulation } from '@/hooks/useBuildApi';
import { Badge } from "@/components/ui";

/**
 * Build Simulator
 *
 * <p><b>Purpose</b><br>
 * Test workflows and agents with simulated events. Visualize execution traces,
 * policy evaluations, and agent responses in a safe sandbox environment.
 *
 * <p><b>Features</b><br>
 * - Workflow selector (active workflows only)
 * - Agent selector (active agents only)
 * - Event payload editor with JSON validation
 * - Simulation execution with real API calls
 * - Trace visualization showing step-by-step execution
 * - Policy block detection
 * - Agent response display
 *
 * @doc.type component
 * @doc.purpose Workflow and agent testing sandbox
 * @doc.layer product
 * @doc.pattern Page
 */
export function BuildSimulator() {
    const [searchParams] = useSearchParams();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const tenantId = selectedTenant || 'acme-payments-id';

    // Pre-select from URL params if available
    const urlWorkflowId = searchParams.get('workflowId');
    const urlAgentId = searchParams.get('agentId');

    const [selectedWorkflowId, setSelectedWorkflowId] = useState<string>(urlWorkflowId || '');
    const [selectedAgentId, setSelectedAgentId] = useState<string>(urlAgentId || '');
    const [eventPayload, setEventPayload] = useState<string>(JSON.stringify({
        type: 'incident.created',
        severity: 'high',
        service: 'payment-api',
        message: 'High latency detected in payment processing',
        metadata: {
            latency_p99: 2500,
            threshold: 500,
            timestamp: new Date().toISOString(),
        },
    }, null, 2));
    const [errors, setErrors] = useState<Record<string, string>>({});

    // Fetch workflows and agents
    const { data: workflowsData } = useWorkflows(tenantId);
    const { data: agentsData } = useAgents(tenantId);

    const workflows = workflowsData?.data?.filter(w => w.status === 'active') || [];
    const agents = agentsData?.data?.filter(a => a.status === 'active') || [];

    // Simulation mutation
    const runSimulation = useRunSimulation();

    // Event templates
    const eventTemplates = [
        {
            label: 'Incident Created',
            value: {
                type: 'incident.created',
                severity: 'high',
                service: 'payment-api',
                message: 'High latency detected',
                metadata: { latency_p99: 2500, threshold: 500 },
            },
        },
        {
            label: 'Deployment Completed',
            value: {
                type: 'deployment.completed',
                service: 'checkout-service',
                version: 'v2.3.1',
                environment: 'production',
                status: 'success',
            },
        },
        {
            label: 'Security Alert',
            value: {
                type: 'security.alert',
                severity: 'critical',
                rule: 'Unauthorized access attempt',
                service: 'auth-service',
                count: 5,
            },
        },
        {
            label: 'Performance Degradation',
            value: {
                type: 'performance.degraded',
                metric: 'p99_latency',
                current: 1200,
                threshold: 500,
                service: 'order-api',
            },
        },
    ];

    // Load template
    const loadTemplate = (template: Record<string, unknown>) => {
        setEventPayload(JSON.stringify(template, null, 2));
        setErrors((prev) => ({ ...prev, payload: '' }));
    };

    // Validate
    const validate = (): boolean => {
        const newErrors: Record<string, string> = {};

        if (!selectedWorkflowId && !selectedAgentId) {
            newErrors.selection = 'Select at least one workflow or agent';
        }

        try {
            JSON.parse(eventPayload);
        } catch {
            newErrors.payload = 'Invalid JSON format';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    // Run simulation
    const handleRunSimulation = async () => {
        if (!validate()) {
            return;
        }

        try {
            const payload = JSON.parse(eventPayload);
            await runSimulation.mutateAsync({
                tenantId,
                workflowId: selectedWorkflowId || undefined,
                agentId: selectedAgentId || undefined,
                environment: 'test',
                eventPayload: payload,
            });
        } catch (error) {
            console.error('Simulation failed:', error);
            setErrors((prev) => ({ ...prev, submit: 'Simulation failed. Check console for details.' }));
        }
    };

    return (
        <div className="p-6 space-y-6">
            {/* Header */}
            <div>
                <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Build Simulator</h1>
                <p className="text-slate-600 dark:text-neutral-400 mt-1">
                    Test workflows and agents with simulated events
                </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Configuration Panel */}
                <div className="lg:col-span-1 space-y-6">
                    {/* Event Templates */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Event Templates
                        </h2>
                        <div className="space-y-2">
                            {eventTemplates.map((template, idx) => (
                                <button
                                    key={idx}
                                    onClick={() => loadTemplate(template.value)}
                                    className="w-full text-left px-3 py-2 border border-slate-200 dark:border-slate-700 rounded-md hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                                >
                                    <div className="font-medium text-sm text-slate-900 dark:text-neutral-100">
                                        {template.label}
                                    </div>
                                    <div className="text-xs text-slate-500 dark:text-neutral-500 font-mono mt-1">
                                        {template.value.type}
                                    </div>
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* Workflow Selection */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Workflow
                        </h2>
                        <select
                            value={selectedWorkflowId}
                            onChange={(e) => setSelectedWorkflowId(e.target.value)}
                            className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 text-sm"
                        >
                            <option value="">None (optional)</option>
                            {workflows.map((workflow) => (
                                <option key={workflow.id} value={workflow.id}>
                                    {workflow.name} ({workflow.steps.length} steps)
                                </option>
                            ))}
                        </select>
                        {workflows.length === 0 && (
                            <p className="mt-2 text-xs text-slate-500 dark:text-neutral-500">
                                No active workflows available
                            </p>
                        )}
                    </div>

                    {/* Agent Selection */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Agent
                        </h2>
                        <select
                            value={selectedAgentId}
                            onChange={(e) => setSelectedAgentId(e.target.value)}
                            className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 text-sm"
                        >
                            <option value="">None (optional)</option>
                            {agents.map((agent) => (
                                <option key={agent.id} value={agent.id}>
                                    {agent.name} ({agent.type})
                                </option>
                            ))}
                        </select>
                        {agents.length === 0 && (
                            <p className="mt-2 text-xs text-slate-500 dark:text-neutral-500">
                                No active agents available
                            </p>
                        )}
                    </div>
                </div>

                {/* Main Panel */}
                <div className="lg:col-span-2 space-y-6">
                    {/* Event Payload Editor */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Event Payload (JSON)
                        </h2>
                        <textarea
                            value={eventPayload}
                            onChange={(e) => {
                                setEventPayload(e.target.value);
                                setErrors((prev) => ({ ...prev, payload: '' }));
                            }}
                            rows={12}
                            className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-neutral-100 font-mono text-sm"
                        />
                        {errors.payload && (
                            <p className="mt-2 text-sm text-red-600 dark:text-red-400">{errors.payload}</p>
                        )}
                    </div>

                    {/* Run Button */}
                    <div className="flex items-center justify-between">
                        {errors.selection && (
                            <p className="text-sm text-red-600 dark:text-red-400">{errors.selection}</p>
                        )}
                        {errors.submit && (
                            <p className="text-sm text-red-600 dark:text-red-400">{errors.submit}</p>
                        )}
                        <div className="ml-auto">
                            <button
                                onClick={handleRunSimulation}
                                disabled={runSimulation.isPending}
                                className="px-6 py-3 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
                            >
                                {runSimulation.isPending ? 'Running...' : '▶ Run Simulation'}
                            </button>
                        </div>
                    </div>

                    {/* Simulation Result */}
                    {runSimulation.isSuccess && runSimulation.data && (
                        <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                                Simulation Result
                            </h2>
                            <div className="space-y-4">
                                {/* Status */}
                                <div className="flex items-center gap-2">
                                    <span className="text-sm text-slate-600 dark:text-neutral-400">Status:</span>
                                    <Badge variant={runSimulation.data.status === 'success' ? 'success' : 'danger'}>
                                        {runSimulation.data.status}
                                    </Badge>
                                </div>

                                {/* Execution Trace */}
                                {runSimulation.data.trace && (
                                    <div>
                                        <h3 className="text-sm font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                                            Execution Trace
                                        </h3>
                                        <div className="space-y-2">
                                            {runSimulation.data.trace.map((step: {step: string; timestamp: string; action: string; result: Record<string, unknown>}, idx: number) => (
                                                <div
                                                    key={idx}
                                                    className="flex items-center gap-3 p-3 bg-slate-50 dark:bg-slate-800 rounded-md border border-slate-200 dark:border-slate-700"
                                                >
                                                    <div className="flex-shrink-0 w-6 h-6 rounded-full bg-blue-100 dark:bg-blue-900 text-blue-600 dark:text-blue-400 flex items-center justify-center text-xs font-bold">
                                                        {idx + 1}
                                                    </div>
                                                    <div className="flex-1">
                                                        <div className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                                                            {step.step}
                                                        </div>
                                                        <div className="text-xs text-slate-500 dark:text-neutral-500">
                                                            {step.action}
                                                        </div>
                                                    </div>
                                                    <Badge variant="success">
                                                        {step.timestamp}
                                                    </Badge>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                {/* Policy Blocks */}
                                {runSimulation.data.policyBlocks && runSimulation.data.policyBlocks.length > 0 && (
                                    <div>
                                        <h3 className="text-sm font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                                            Policy Blocks
                                        </h3>
                                        <div className="space-y-2">
                                            {runSimulation.data.policyBlocks.map((block: { policyId: string; policyName: string; reason: string }, idx: number) => (
                                                <div
                                                    key={idx}
                                                    className="p-3 bg-red-50 dark:bg-red-900/20 rounded-md border border-red-200 dark:border-red-800"
                                                >
                                                    <div className="text-sm font-medium text-red-900 dark:text-red-400">
                                                        {block.policyName}
                                                    </div>
                                                    <div className="text-xs text-red-700 dark:text-red-500 mt-1">
                                                        {block.reason}
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                {/* Duration */}
                                <div className="flex items-center gap-2">
                                    <span className="text-sm text-slate-600 dark:text-neutral-400">Duration:</span>
                                    <span className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                                        {runSimulation.data.duration}ms
                                    </span>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Error */}
                    {runSimulation.isError && (
                        <div className="bg-red-50 dark:bg-red-900/20 rounded-lg border border-red-200 dark:border-red-800 p-6">
                            <h2 className="text-lg font-semibold text-red-900 dark:text-red-400 mb-2">
                                Simulation Failed
                            </h2>
                            <p className="text-sm text-red-700 dark:text-red-500">
                                {runSimulation.error?.message || 'Unknown error occurred'}
                            </p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
