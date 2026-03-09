import { memo, useState } from 'react';

/**
 * Side drawer showing detailed event information and AI insights.
 *
 * <p><b>Purpose</b><br>
 * Displays full event payload, AI reasoning, upstream/downstream events, and
 * suggested actions (quarantine, postmortem, escalate, simulate impact).
 *
 * <p><b>Features</b><br>
 * - Event metadata (type, ID, timestamp, tenant)
 * - JSON payload viewer (copy/download)
 * - AI reasoning section (expandable)
 * - Upstream/downstream event links
 * - Action buttons (simulate impact, create incident)
 *
 * <p><b>Props</b><br>
 * @param onClose - Callback when drawer closes
 *
 * <p><b>State</b><br>
 * - showReasoning: AI reasoning section expanded
 *
 * @doc.type component
 * @doc.purpose Event detail inspection drawer
 * @doc.layer product
 * @doc.pattern Drawer
 */
interface FlowInspectorDrawerProps {
    onClose: () => void;
}

interface EventDetail {
    id: string;
    type: string;
    time: string;
    tenant: string;
    confidence: number;
    payload: Record<string, unknown>;
    reasoning: string;
    upstreamEvents: Array<{ id: string; type: string; time: string }>;
    downstreamEvents: Array<{ id: string; type: string; time: string; status: string }>;
}

// Event details - static fallback data
// TODO: Integrate with useEventDetails hook when API structure aligns with component expectations
// Note: useEventDetails exists (returns timestamp, departmentId, sourceAgent, status, payload)
//       Component expects different structure (time, tenant, confidence, reasoning, upstreamEvents, downstreamEvents)
//       Requires mapping layer or component refactoring in future sessions
const eventDetails: EventDetail = {
    id: 'test-suite-abc123',
    type: 'TestSuiteStarted',
    time: '14:15:23',
    tenant: 'acme-corp',
    confidence: 0.88,
    payload: {
        suiteId: 'test-123',
        commitSha: 'abc123def456',
        testType: 'INTEGRATION',
        totalTests: 142,
        environment: 'staging',
    },
    reasoning:
        'Based on analysis of 19 similar incidents: Pattern detected in auth module test flakiness. ' +
        'Historical success rate: 94.7%. Model: incident-remediation-v2.3',
    upstreamEvents: [
        { id: 'task-refined', type: 'TaskRefined', time: '14:10:05' },
        { id: 'commit-analyzed', type: 'CommitAnalyzed', time: '14:12:33' },
        { id: 'build-succeeded', type: 'BuildSucceeded', time: '14:14:12' },
    ],
    downstreamEvents: [
        { id: 'quality-gate', type: 'QualityGateEvaluation', time: '14:45:01', status: 'Pending' },
        { id: 'deployment', type: 'DeploymentInitiated', time: 'Est. 15:00', status: 'Estimated' },
    ],
};

export const FlowInspectorDrawer = memo(function FlowInspectorDrawer({
    onClose,
}: FlowInspectorDrawerProps) {
    // GIVEN: Event ID from workflow canvas click
    // WHEN: Drawer opens with event details
    // THEN: Display full event context with AI insights and actions

    const [showReasoning, setShowReasoning] = useState(false);
    const [showJson, setShowJson] = useState(false);

    // Replace with useEventDetails query
    const event = eventDetails;

    const getConfidenceColor = (confidence: number) => {
        if (confidence >= 0.9) return 'bg-green-500';
        if (confidence >= 0.75) return 'bg-yellow-500';
        return 'bg-orange-500';
    };

    return (
        <div className="fixed right-0 top-0 bottom-0 w-96 bg-slate-900 border-l border-slate-700 shadow-xl flex flex-col z-50">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-slate-700">
                <h2 className="text-lg font-semibold text-white">Event Details</h2>
                <button
                    onClick={onClose}
                    className="text-slate-400 hover:text-slate-200 transition-colors"
                    title="Close drawer"
                    type="button">
                    ✕
                </button>
            </div>

            {/* Scrollable content */}
            <div className="flex-1 overflow-y-auto">
                <div className="space-y-6 p-4">
                    {/* Event metadata */}
                    <div className="space-y-3">
                        <h3 className="text-sm font-semibold text-slate-300 uppercase tracking-wider">Metadata</h3>

                        <div>
                            <div className="text-xs text-slate-500">Type</div>
                            <span className="text-slate-200 font-mono">{event.type}</span>
                        </div>

                        <div>
                            <div className="text-xs text-slate-500">Event ID</div>
                            <span className="text-slate-200 font-mono text-xs">{event.id}</span>
                        </div>

                        <div>
                            <div className="text-xs text-slate-500">Time</div>
                            <span className="text-slate-200">{event.time}</span>
                        </div>

                        <div>
                            <div className="text-xs text-slate-500">Tenant</div>
                            <span className="text-slate-200">{event.tenant}</span>
                        </div>
                    </div>

                    {/* JSON payload */}
                    <div className="space-y-2">
                        <button
                            onClick={() => setShowJson(!showJson)}
                            className="text-xs font-semibold text-slate-400 hover:text-slate-300 uppercase tracking-wider"
                            type="button">
                            {showJson ? '▼' : '▶'} Payload ({Object.keys(event.payload).length} fields)
                        </button>
                        {showJson && (
                            <pre className="bg-slate-950 rounded p-2 text-xs text-slate-400 overflow-x-auto max-h-40">
                                {JSON.stringify(event.payload, null, 2)}
                            </pre>
                        )}
                    </div>

                    {/* Confidence score */}
                    <div className="space-y-2">
                        <h3 className="text-sm font-semibold text-slate-300 uppercase tracking-wider">AI Confidence</h3>

                        <div>
                            <div className="flex items-center justify-between mb-2">
                                <span className="text-sm text-slate-400">Match Confidence</span>
                                <span className={`px-2 py-1 rounded text-xs font-medium ${getConfidenceColor(event.confidence)} bg-opacity-20`}>
                                    {(event.confidence * 100).toFixed(0)}%
                                </span>
                            </div>

                            <div className="w-full bg-slate-800 rounded-full h-2">
                                <div
                                    className={`h-full ${getConfidenceColor(event.confidence)} transition-all`}
                                    style={{ width: `${event.confidence * 100}%` }}
                                />
                            </div>
                            <div className="text-xs text-slate-500 dark:text-neutral-400 mt-1 text-right">
                                <span className="font-mono">{(event.confidence * 100).toFixed(0)}%</span>
                            </div>
                        </div>
                    </div>

                    {/* AI Reasoning */}
                    <div className="space-y-2">
                        <button
                            onClick={() => setShowReasoning(!showReasoning)}
                            className="text-xs font-semibold text-slate-400 hover:text-slate-300 uppercase tracking-wider"
                            type="button">
                            {showReasoning ? '▼' : '▶'} AI Reasoning
                        </button>
                        {showReasoning && (
                            <div className="bg-slate-950 rounded p-3 text-sm text-slate-300 leading-relaxed">
                                <p>{event.reasoning}</p>
                            </div>
                        )}
                    </div>

                    {/* Upstream events */}
                    <div className="space-y-2">
                        <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Upstream Events</h3>
                        <div className="space-y-1">
                            {event.upstreamEvents.map((evt: any) => (
                                <div
                                    key={evt.id}
                                    className="bg-slate-800 rounded p-2 text-xs flex items-center justify-between hover:bg-slate-700 cursor-pointer transition-colors">
                                    <div className="flex flex-col">
                                        <span className="text-slate-300 font-medium">{evt.type}</span>
                                        <span className="text-slate-500">{evt.time}</span>
                                    </div>
                                    <span className="text-slate-400">→</span>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Downstream events */}
                    <div className="space-y-2">
                        <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Downstream Events</h3>
                        <div className="space-y-1">
                            {event.downstreamEvents.map((evt: any) => (
                                <div
                                    key={evt.id}
                                    className="bg-slate-800 rounded p-2 text-xs flex items-center justify-between hover:bg-slate-700 cursor-pointer transition-colors">
                                    <span className="text-slate-400">→</span>
                                    <div className="flex flex-col text-right flex-1 mr-2">
                                        <span className="text-slate-300 font-medium">{evt.type}</span>
                                        <span className="text-slate-500">{evt.status}</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </div>

            {/* Action buttons */}
            <div className="border-t border-slate-700 p-4 flex gap-2">
                <button
                    className="flex-1 bg-blue-600 hover:bg-blue-700 text-white py-2 rounded font-medium transition-colors"
                    type="button">
                    Create Incident
                </button>
                <button
                    className="flex-1 bg-slate-700 hover:bg-slate-600 text-white py-2 rounded font-medium transition-colors"
                    onClick={onClose}
                    type="button">
                    Close
                </button>
            </div>
        </div>
    );
});

export default FlowInspectorDrawer;
