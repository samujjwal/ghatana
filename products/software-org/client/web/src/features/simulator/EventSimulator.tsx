import React, { useState } from "react";

/**
 * Event Simulator
 *
 * <p><b>Purpose</b><br>
 * Developer utility for composing, validating, and emitting simulated events
 * to test event processing pipelines, patterns, and integrations without
 * requiring production data sources.
 *
 * <p><b>Features</b><br>
 * - Event template library for quick event composition
 * - JSON payload editor with syntax validation
 * - Event history log with timestamps
 * - Quick-send for testing workflows
 * - Event type templates (deployment, security, test, performance)
 * - Payload validation before emission
 *
 * <p><b>Mock Data</b><br>
 * All data is currently mocked. Replace with API calls to `/api/v1/events/simulate`
 * using the `apiClient` from `@/services/api/index.ts`.
 *
 * @doc.type component
 * @doc.purpose Event composition and emission tool
 * @doc.layer product
 * @doc.pattern Page
 */
export function EventSimulator() {
    const [payload, setPayload] = useState<string>(JSON.stringify({
        type: 'deployment.completed',
        message: 'Production deploy finished',
        duration: '2m 45s',
    }, null, 2));
    const [history, setHistory] = useState<Array<any>>([]);
    const [error, setError] = useState<string | null>(null);

    // Event templates
    const templates = [
        {
            label: 'Deployment',
            value: { type: 'deployment.completed', service: 'api', version: 'v2.1.0', duration: '2m 45s', status: 'success' },
        },
        {
            label: 'Security Alert',
            value: { type: 'security.alert', severity: 'high', rule: 'SQL Injection Detected', count: 3, service: 'payments' },
        },
        {
            label: 'Test Failed',
            value: { type: 'test.failed', suite: 'payment-integration', test: 'test_transaction_timeout', duration: '12.3s' },
        },
        {
            label: 'Performance Degradation',
            value: { type: 'performance.degraded', metric: 'p99_latency', value: 850, threshold: 500, service: 'checkout' },
        },
    ];

    const send = () => {
        try {
            const parsed = JSON.parse(payload);
            const event = {
                id: `evt-${Date.now()}`,
                payload: parsed,
                ts: new Date().toLocaleTimeString(),
                type: parsed.type || 'unknown',
            };
            setHistory((h) => [event, ...h].slice(0, 50));
            setError(null);
            console.log('Simulated event sent', event);
        } catch (e) {
            setError(`Invalid JSON: ${(e as any).message}`);
        }
    };

    const loadTemplate = (template: any) => {
        setPayload(JSON.stringify(template, null, 2));
        setError(null);
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div>
                <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Event Simulator</h1>
                <p className="text-slate-600 dark:text-neutral-400 mt-1">Compose and emit simulated events for testing pipelines and workflows</p>
            </div>

            {/* Main Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Template Buttons */}
                <div className="space-y-3">
                    <div className="text-sm font-semibold text-slate-700 dark:text-neutral-300 uppercase tracking-wide">Event Templates</div>
                    {templates.map((t, idx) => (
                        <button
                            key={idx}
                            onClick={() => loadTemplate(t.value)}
                            className="w-full px-4 py-3 text-left rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 hover:border-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition"
                        >
                            <div className="font-medium text-slate-900 dark:text-neutral-100">{t.label}</div>
                            <div className="text-xs text-slate-500 dark:text-neutral-400 mt-1 font-mono">{t.value.type}</div>
                        </button>
                    ))}
                </div>

                {/* JSON Editor & Send */}
                <div className="lg:col-span-2 space-y-4">
                    {/* JSON Textarea */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">Event Payload (JSON)</label>
                        <textarea
                            value={payload}
                            onChange={(e) => {
                                setPayload(e.target.value);
                                setError(null);
                            }}
                            className="w-full h-64 p-4 border border-slate-200 dark:border-slate-800 rounded-lg bg-white dark:bg-slate-900 text-sm font-mono text-slate-900 dark:text-neutral-100"
                        />
                        {error && <div className="mt-2 px-3 py-2 bg-red-100 dark:bg-rose-600/30 text-red-800 dark:text-rose-400 text-sm rounded">{error}</div>}
                    </div>

                    {/* Send & Reset Buttons */}
                    <div className="flex gap-2">
                        <button
                            onClick={send}
                            className="flex-1 px-4 py-3 rounded-lg bg-blue-600 text-white font-medium hover:bg-blue-700 transition"
                        >
                            📤 Send Event
                        </button>
                        <button
                            onClick={() => {
                                setPayload(JSON.stringify(templates[0].value, null, 2));
                                setError(null);
                            }}
                            className="px-4 py-3 rounded-lg bg-slate-200 dark:bg-neutral-800 text-slate-900 dark:text-neutral-100 font-medium hover:bg-slate-300 transition"
                        >
                            Reset
                        </button>
                    </div>

                    {/* Stats Footer */}
                    <div className="grid grid-cols-4 gap-2">
                        <StatCard label="Sent" value={history.length} icon="📊" />
                        <StatCard label="Last Type" value={history[0]?.type.split('.')[1] || '-'} icon="📌" />
                        <StatCard label="Templates" value={templates.length} icon="📋" />
                        <StatCard label="Status" value="Ready" icon="✅" />
                    </div>
                </div>
            </div>

            {/* Event History */}
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                <div className="text-sm font-semibold text-slate-700 dark:text-neutral-300 uppercase tracking-wide mb-4">Event History (Last 50)</div>
                <div className="space-y-2 max-h-96 overflow-auto">
                    {history.length === 0 ? (
                        <div className="text-center py-8 text-slate-500">No events sent yet. Select a template or compose a custom event.</div>
                    ) : (
                        history.map((h) => (
                            <div key={h.id} className="p-3 bg-slate-50 dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded">
                                <div className="flex items-start justify-between">
                                    <div>
                                        <div className="font-mono text-xs text-slate-500 dark:text-neutral-400">{h.ts}</div>
                                        <div className="font-semibold text-slate-900 dark:text-neutral-100 mt-1">{h.type}</div>
                                    </div>
                                    <span className="px-2 py-1 text-xs bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-indigo-400 rounded">Sent</span>
                                </div>
                                <pre className="text-xs mt-2 whitespace-pre-wrap break-words text-slate-700 dark:text-neutral-300 font-mono">{JSON.stringify(h.payload, null, 2)}</pre>
                            </div>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
}

function StatCard({ label, value, icon }: { label: string; value: any; icon: string }) {
    return (
        <div className="bg-slate-50 dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded p-3">
            <div className="text-lg mb-1">{icon}</div>
            <div className="text-xs text-slate-600 dark:text-neutral-400">{label}</div>
            <div className="text-lg font-bold text-slate-900 dark:text-neutral-100 mt-1">{value}</div>
        </div>
    );
}

export default EventSimulator;
