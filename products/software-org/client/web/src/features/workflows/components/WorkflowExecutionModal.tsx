import { memo, useState, useCallback } from 'react';
import { useWorkflowExecution } from '@/hooks/useWorkflowExecution';

/**
 * Workflow Execution Trigger component.
 *
 * <p><b>Purpose</b><br>
 * Allows users to trigger workflow execution with optional parameters,
 * monitor execution status in real-time, and view execution logs.
 *
 * <p><b>Features</b><br>
 * - Trigger workflow with parameters
 * - Status tracking: pending → running → success/failure
 * - Real-time log accumulation
 * - Execution history
 * - Cancel running execution
 * - Retry failed execution
 *
 * <p><b>State Management</b><br>
 * - useWorkflowExecution hook for execution lifecycle
 * - Local state: selected workflow, parameters, execution result
 *
 * @doc.type component
 * @doc.purpose Workflow execution trigger and monitoring
 * @doc.layer product
 * @doc.pattern Modal
 */

interface WorkflowParam {
    name: string;
    value: string;
    type: 'string' | 'number' | 'boolean';
}

interface ExecutionProps {
    workflowId: string;
    workflowName?: string;
    onClose: () => void;
}

export const WorkflowExecutionModal = memo(function WorkflowExecutionModal(props: ExecutionProps) {
    const { workflowId, workflowName = workflowId, onClose } = props;
    const { executeWorkflow, getExecution, cancelExecution } = useWorkflowExecution();

    const [parameters, setParameters] = useState<WorkflowParam[]>([
        { name: 'targetEnvironment', value: 'staging', type: 'string' },
        { name: 'autoRollback', value: 'true', type: 'boolean' },
    ]);
    const [executionId, setExecutionId] = useState<string | null>(null);
    const [status, setStatus] = useState<'idle' | 'executing' | 'success' | 'failed'>('idle');
    const [logs, setLogs] = useState<string[]>([]);
    const [error, setError] = useState<string | null>(null);

    // Handle execution start
    const handleExecute = useCallback(async () => {
        try {
            setStatus('executing');
            setLogs([]);
            setError(null);

            // Build parameter object
            const params = parameters.reduce((acc, p) => {
                acc[p.name] = p.type === 'number' ? Number(p.value) : p.type === 'boolean' ? p.value === 'true' : p.value;
                return acc;
            }, {} as Record<string, unknown>);

            // Execute workflow
            const result = await executeWorkflow(workflowId, params);
            setExecutionId(result.id);

            // Poll for status updates
            const pollInterval = setInterval(async () => {
                const exec = await getExecution(result.id);
                if (exec) {
                    setLogs(exec.logs || []);

                    if (exec.status === 'success' || exec.status === 'failed') {
                        setStatus(exec.status);
                        clearInterval(pollInterval);
                    }
                }
            }, 1000);

            console.log('[Workflow] Execution started:', result.id);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Execution failed');
            setStatus('failed');
            console.error('[Workflow] Execution failed:', err);
        }
    }, [workflowId, parameters, executeWorkflow, getExecution]);

    // Handle cancellation
    const handleCancel = useCallback(async () => {
        if (!executionId) return;
        try {
            await cancelExecution(executionId);
            setStatus('idle');
            setExecutionId(null);
            console.log('[Workflow] Execution cancelled:', executionId);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Cancellation failed');
            console.error('[Workflow] Cancellation failed:', err);
        }
    }, [executionId, cancelExecution]);

    // Handle parameter change
    const handleParamChange = (index: number, value: string) => {
        const updated = [...parameters];
        updated[index].value = value;
        setParameters(updated);
    };

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
            <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg shadow-2xl max-w-2xl w-full mx-4 max-h-[90vh] flex flex-col">
                {/* Header */}
                <div className="border-b border-slate-200 dark:border-neutral-600 p-6 flex items-center justify-between">
                    <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">Execute Workflow</h2>
                    <button
                        onClick={onClose}
                        className="text-slate-400 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-white text-2xl leading-none"
                        aria-label="Close modal"
                    >
                        ×
                    </button>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-auto p-6">
                    <div className="space-y-6">
                        {/* Workflow Info */}
                        <div>
                            <h3 className="text-sm font-semibold text-slate-700 dark:text-neutral-300 mb-2">Workflow</h3>
                            <div className="bg-slate-100 dark:bg-neutral-700 border border-slate-200 dark:border-neutral-600 rounded p-3 text-slate-900 dark:text-slate-200">
                                {workflowName}
                            </div>
                        </div>

                        {/* Status Display */}
                        {status !== 'idle' && (
                            <div className={`p-4 rounded-lg border ${status === 'executing'
                                ? 'bg-blue-500/10 border-blue-500/30 text-blue-600 dark:text-blue-300'
                                : status === 'success'
                                    ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-600 dark:text-emerald-300'
                                    : 'bg-red-500/10 border-red-500/30 text-red-600 dark:text-red-300'
                                }`}>
                                <div className="flex items-center gap-2">
                                    {status === 'executing' && <span className="inline-block animate-spin">⏳</span>}
                                    {status === 'success' && <span>✅</span>}
                                    {status === 'failed' && <span>❌</span>}
                                    <span className="font-medium capitalize">
                                        {status === 'executing' ? 'Executing...' : status}
                                    </span>
                                </div>
                            </div>
                        )}

                        {/* Parameters */}
                        {status === 'idle' && (
                            <div>
                                <h3 className="text-sm font-semibold text-slate-700 dark:text-neutral-300 mb-3">Parameters</h3>
                                <div className="space-y-3">
                                    {parameters.map((param, idx) => (
                                        <div key={idx}>
                                            <label className="text-xs font-medium text-slate-500 dark:text-neutral-400 block mb-1">
                                                {param.name}
                                                <span className="text-slate-400 dark:text-slate-600 ml-1">({param.type})</span>
                                            </label>
                                            {param.type === 'boolean' ? (
                                                <select
                                                    value={param.value}
                                                    onChange={(e) => handleParamChange(idx, e.target.value)}
                                                    className="w-full px-3 py-2 bg-white dark:bg-neutral-700 text-slate-900 dark:text-slate-200 rounded border border-slate-200 dark:border-neutral-600 text-sm"
                                                >
                                                    <option value="true">true</option>
                                                    <option value="false">false</option>
                                                </select>
                                            ) : (
                                                <input
                                                    type={param.type === 'number' ? 'number' : 'text'}
                                                    value={param.value}
                                                    onChange={(e) => handleParamChange(idx, e.target.value)}
                                                    className="w-full px-3 py-2 bg-white dark:bg-neutral-700 text-slate-900 dark:text-slate-200 rounded border border-slate-200 dark:border-neutral-600 text-sm"
                                                />
                                            )}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Execution Logs */}
                        {logs.length > 0 && (
                            <div>
                                <h3 className="text-sm font-semibold text-slate-700 dark:text-neutral-300 mb-2">Logs</h3>
                                <div className="bg-slate-100 dark:bg-slate-950 border border-slate-200 dark:border-neutral-600 rounded p-3 font-mono text-xs text-slate-700 dark:text-neutral-300 max-h-48 overflow-auto">
                                    {logs.map((log, idx) => (
                                        <div key={idx} className="whitespace-pre-wrap break-words">
                                            {log}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Error Display */}
                        {error && (
                            <div className="bg-red-500/10 border border-red-500/30 rounded p-3 text-red-600 dark:text-red-300 text-sm">
                                {error}
                            </div>
                        )}
                    </div>
                </div>

                {/* Footer */}
                <div className="border-t border-slate-200 dark:border-neutral-600 p-6 flex gap-3 justify-end bg-slate-50 dark:bg-slate-950">
                    {status === 'executing' && (
                        <button
                            onClick={handleCancel}
                            className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded text-sm font-medium"
                        >
                            Cancel Execution
                        </button>
                    )}

                    {status === 'idle' && (
                        <>
                            <button
                                onClick={onClose}
                                className="px-4 py-2 bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-700 dark:text-slate-200 rounded text-sm font-medium"
                            >
                                Close
                            </button>
                            <button
                                onClick={handleExecute}
                                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded text-sm font-medium"
                            >
                                Execute
                            </button>
                        </>
                    )}

                    {(status === 'success' || status === 'failed') && (
                        <button
                            onClick={onClose}
                            className="px-4 py-2 bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-700 dark:text-slate-200 rounded text-sm font-medium"
                        >
                            Close
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
});

export default WorkflowExecutionModal;
