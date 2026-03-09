import { useState, useCallback } from 'react';
import { ActionQueue } from './components/ActionQueue';
import { ActionDetailDrawer } from './components/ActionDetailDrawer';
import { useHitlShortcuts } from '@/hooks/useKeyboardShortcuts';
import { useDecisionAudit } from '@/hooks/useDecisionAudit';
import { useDebounce } from '@/hooks/useDebounce';

/**
 * HITL Console page for human-in-the-loop AI action approvals.
 *
 * <p><b>Purpose</b><br>
 * Displays prioritized queue of AI agent actions requiring human review before
 * execution. Shows confidence scores, reasoning, and impact predictions. Users
 * can approve, defer, reject, or modify actions with keyboard shortcuts.
 *
 * <p><b>Features</b><br>
 * - Hero stats (pending count, avg response time, open incidents, SLA breaches)
 * - Two-column layout: action queue + detail drawer
 * - Virtualized table for 1000s of actions
 * - Priority-based coloring (P0 red, P1 yellow, P2 green)
 * - Keyboard shortcuts: A=Approve, D=Defer, R=Reject
 * - Filters: priority, type, department, search
 * - Real-time updates via WebSocket
 *
 * <p><b>State Management</b><br>
 * - TanStack Query: useHitlActions hook (fetch action queue)
 * - Local: selectedActionId, filters, sortBy
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <Route path="/hitl" element={<HitlConsole />} />
 * // Shows pending actions requiring approval
 * // User clicks action to see details
 * // Keyboard shortcut (A/D/R) to make decision
 * // Real-time stats update
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose HITL action approval console
 * @doc.layer product
 * @doc.pattern Page
 * @see ActionQueue - Virtualized action list
 * @see ActionDetailDrawer - Action detail panel
 */
export function HitlConsole() {
    // GIVEN: HITL Console page loading
    // WHEN: User needs to review AI agent actions
    // THEN: Display prioritized queue with filtering and approval workflow

    const [selectedActionId, setSelectedActionId] = useState<string | null>(null);
    const [showDetails, setShowDetails] = useState(false);
    const [priorityFilter, setPriorityFilter] = useState('all');
    const [typeFilter, setTypeFilter] = useState('all');
    const [deptFilter, setDeptFilter] = useState('all');
    const [searchQuery, setSearchQuery] = useState('');
    const debouncedSearch = useDebounce(searchQuery, 500); // Debounce search after 500ms
    const { recordDecision } = useDecisionAudit();

    // Mock stats - replace with useHitlStats hook
    const stats = {
        pending: 12,
        avgResponse: '8m 23s',
        openIncidents: 3,
        slaBreaches: 0,
        trendResponse: -45,
        trendIncidents: 'on-track',
    };

    // Handle action decision (Approve/Defer/Reject)
    const handleApprove = useCallback(async () => {
        if (!selectedActionId) return;
        try {
            await recordDecision({
                entityType: 'action',
                entityId: selectedActionId,
                decision: 'approve',
                reason: 'Approved via keyboard shortcut',
            });
            console.log('[HITL] Action approved:', selectedActionId);
            setShowDetails(false);
            setSelectedActionId(null);
        } catch (err) {
            console.error('[HITL] Approve failed:', err);
        }
    }, [selectedActionId, recordDecision]);

    const handleDefer = useCallback(async () => {
        if (!selectedActionId) return;
        try {
            await recordDecision({
                entityType: 'action',
                entityId: selectedActionId,
                decision: 'defer',
                reason: 'Deferred for later review',
            });
            console.log('[HITL] Action deferred:', selectedActionId);
            setShowDetails(false);
            setSelectedActionId(null);
        } catch (err) {
            console.error('[HITL] Defer failed:', err);
        }
    }, [selectedActionId, recordDecision]);

    const handleReject = useCallback(async () => {
        if (!selectedActionId) return;
        try {
            await recordDecision({
                entityType: 'action',
                entityId: selectedActionId,
                decision: 'reject',
                reason: 'Rejected by reviewer',
            });
            console.log('[HITL] Action rejected:', selectedActionId);
            setShowDetails(false);
            setSelectedActionId(null);
        } catch (err) {
            console.error('[HITL] Reject failed:', err);
        }
    }, [selectedActionId, recordDecision]);

    // Register keyboard shortcuts
    useHitlShortcuts(!!selectedActionId && showDetails, handleApprove, handleDefer, handleReject);

    return (
        <div className="h-screen flex flex-col bg-slate-50 dark:bg-slate-900">
            {/* Hero Stats */}
            <div className="grid grid-cols-4 gap-4 p-6 border-b border-slate-200 dark:border-neutral-600">
                {/* Pending */}
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="text-sm text-slate-600 dark:text-neutral-400">Pending</div>
                    <div className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mt-2">{stats.pending}</div>
                    <div className="text-xs text-red-600 dark:text-rose-400 mt-1">↑ 2 from 1h</div>
                </div>

                {/* Avg Response */}
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="text-sm text-slate-600 dark:text-neutral-400">Avg Response</div>
                    <div className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mt-2">{stats.avgResponse}</div>
                    <div className="text-xs text-green-600 dark:text-green-400 mt-1">↓ 45% (7d)</div>
                </div>

                {/* Open Incidents */}
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="text-sm text-slate-600 dark:text-neutral-400">Open Incidents</div>
                    <div className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mt-2">{stats.openIncidents}</div>
                    <div className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">⚠️ 1 P1</div>
                </div>

                {/* SLA Breaches */}
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="text-sm text-slate-600 dark:text-neutral-400">SLA Breaches</div>
                    <div className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mt-2">{stats.slaBreaches}</div>
                    <div className="text-xs text-green-600 dark:text-green-400 mt-1">✓ On track</div>
                </div>
            </div>

            {/* Main Content */}
            <div className="flex-1 flex overflow-hidden">
                {/* Action Queue (left 2/3) */}
                <div className="flex-1 border-r border-slate-200 dark:border-neutral-600 flex flex-col">
                    {/* Filters & Search */}
                    <div className="border-b border-slate-200 dark:border-neutral-600 p-4 bg-white dark:bg-neutral-800">
                        <div className="flex gap-3 mb-4">
                            <select
                                value={priorityFilter}
                                onChange={(e) => setPriorityFilter(e.target.value)}
                                className="px-3 py-2 bg-slate-100 dark:bg-neutral-700 text-slate-900 dark:text-slate-200 rounded border border-slate-200 dark:border-neutral-600 text-sm"
                            >
                                <option value="all">Priority: All</option>
                                <option value="p0">P0 Only</option>
                                <option value="p1">P1 Only</option>
                                <option value="p2">P2 Only</option>
                            </select>

                            <select
                                value={typeFilter}
                                onChange={(e) => setTypeFilter(e.target.value)}
                                className="px-3 py-2 bg-slate-100 dark:bg-neutral-700 text-slate-900 dark:text-slate-200 rounded border border-slate-200 dark:border-neutral-600 text-sm"
                            >
                                <option value="all">Type: All</option>
                                <option value="remediate">Auto-remediate</option>
                                <option value="quarantine">Quarantine</option>
                                <option value="refactor">Refactor</option>
                            </select>

                            <select
                                value={deptFilter}
                                onChange={(e) => setDeptFilter(e.target.value)}
                                className="px-3 py-2 bg-slate-100 dark:bg-neutral-700 text-slate-900 dark:text-slate-200 rounded border border-slate-200 dark:border-neutral-600 text-sm"
                            >
                                <option value="all">Dept: All</option>
                                <option value="eng">Engineering</option>
                                <option value="qa">QA</option>
                                <option value="devops">DevOps</option>
                            </select>

                            <input
                                type="text"
                                placeholder="Search actions..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                className="flex-1 px-3 py-2 bg-slate-100 dark:bg-neutral-700 text-slate-900 dark:text-slate-200 rounded border border-slate-200 dark:border-neutral-600 text-sm placeholder-slate-400 dark:placeholder-slate-500"
                            />
                        </div>

                        <div className="text-xs text-slate-500 dark:text-slate-500">
                            Keyboard shortcuts: <strong>A</strong>=Approve | <strong>D</strong>=Defer | <strong>R</strong>=Reject
                        </div>
                    </div>

                    {/* Action Queue Table */}
                    <ActionQueue
                        priorityFilter={priorityFilter}
                        typeFilter={typeFilter}
                        deptFilter={deptFilter}
                        searchQuery={debouncedSearch}
                        selectedActionId={selectedActionId}
                        onSelectAction={(id: string) => {
                            setSelectedActionId(id);
                            setShowDetails(true);
                        }}
                    />
                </div>

                {/* Detail Panel (right 1/3) */}
                <div className="w-96 border-l border-slate-200 dark:border-neutral-600 flex flex-col bg-white dark:bg-neutral-800">
                    {showDetails && selectedActionId ? (
                        <ActionDetailDrawer
                            actionId={selectedActionId}
                            onClose={() => {
                                setShowDetails(false);
                                setSelectedActionId(null);
                            }}
                            onApprove={handleApprove}
                            onDefer={handleDefer}
                            onReject={handleReject}
                        />
                    ) : (
                        <div className="flex-1 flex items-center justify-center text-slate-500 dark:text-slate-500">
                            <div className="text-center">
                                <div className="text-6xl mb-2">👈</div>
                                <p>Select an action from the queue to view details</p>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export default HitlConsole;
