/**
 * IC Work Items Page
 *
 * <p><b>Purpose</b><br>
 * Comprehensive work item management for Individual Contributors.
 * Kanban board view organized by DevSecOps phases with navigation
 * to detailed execution context.
 *
 * <p><b>Features</b><br>
 * - Kanban board by status or DevSecOps phase
 * - Search and filter capabilities
 * - Click-through to work item detail with execution panel
 * - Real-time data from unified persona hooks
 *
 * @doc.type page
 * @doc.purpose Work item management for ICs
 * @doc.layer product
 * @doc.pattern Kanban Board
 */

import { useState, useCallback, useMemo, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { Box, Card, Button, Chip, Grid } from '@ghatana/design-system';
import { KpiCard } from '@/shared/components/org';
import { usePersonaWorkItems, useWorkSession } from '@/hooks/useUnifiedPersona';
import { PersonaFlowStrip } from '@/shared/components/PersonaFlowStrip';
import type { DevSecOpsPhaseId } from '@/shared/types/org';
import type { PersonaWorkItem } from '@/state/jotai/atoms';

/**
 * Work item statuses for Kanban columns
 */
type WorkItemStatus = 'ready' | 'in-progress' | 'blocked' | 'done';

/**
 * Phase labels for display
 */
const PHASE_LABELS: Record<DevSecOpsPhaseId, string> = {
    intake: 'Intake',
    plan: 'Plan',
    build: 'Build',
    verify: 'Verify',
    review: 'Review',
    staging: 'Staging',
    deploy: 'Deploy',
    operate: 'Operate',
    learn: 'Learn',
};

/**
 * Status labels for display
 */
const STATUS_LABELS: Record<WorkItemStatus, string> = {
    ready: 'Ready',
    'in-progress': 'In Progress',
    blocked: 'Blocked',
    done: 'Done',
};

export function ICWorkItemsPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const { workItems, isLoading } = usePersonaWorkItems();
    const { isActive: isSessionActive } = useWorkSession();
    
    const [searchQuery, setSearchQuery] = useState('');
    const [viewMode, setViewMode] = useState<'status' | 'phase'>('status');
    const [selectedPhase, setSelectedPhase] = useState<DevSecOpsPhaseId | null>(null);

    // Handle phase from URL query parameter
    useEffect(() => {
        const phaseParam = searchParams.get('phase');
        if (phaseParam && Object.keys(PHASE_LABELS).includes(phaseParam)) {
            setSelectedPhase(phaseParam as DevSecOpsPhaseId);
            setViewMode('phase');
        }
    }, [searchParams]);

    // Filter work items based on search
    const filteredItems = useMemo(() => {
        if (!workItems) return [];
        return workItems.filter(
            (item) =>
                searchQuery === '' ||
                item.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                item.description?.toLowerCase().includes(searchQuery.toLowerCase())
        );
    }, [workItems, searchQuery]);

    // Group items by status
    const itemsByStatus = useMemo(() => {
        const groups: Record<WorkItemStatus, PersonaWorkItem[]> = {
            ready: [],
            'in-progress': [],
            blocked: [],
            done: [],
        };
        filteredItems.forEach((item) => {
            const status = item.status as WorkItemStatus;
            if (groups[status]) {
                groups[status].push(item);
            }
        });
        return groups;
    }, [filteredItems]);

    // Group items by phase
    const itemsByPhase = useMemo(() => {
        const groups: Partial<Record<DevSecOpsPhaseId, PersonaWorkItem[]>> = {};
        filteredItems.forEach((item) => {
            if (!groups[item.phase]) {
                groups[item.phase] = [];
            }
            groups[item.phase]!.push(item);
        });
        return groups;
    }, [filteredItems]);

    // Navigate to work item detail
    const handleItemClick = useCallback((itemId: string) => {
        navigate(`/ic/work-items/${itemId}`);
    }, [navigate]);

    // Handle phase click from flow strip
    const handlePhaseClick = useCallback((phase: DevSecOpsPhaseId) => {
        setSelectedPhase(phase === selectedPhase ? null : phase);
    }, [selectedPhase]);

    const getStatusColor = (status: string): 'primary' | 'default' | 'success' | 'warning' | 'danger' => {
        switch (status) {
            case 'in-progress':
                return 'primary';
            case 'ready':
                return 'warning';
            case 'done':
                return 'success';
            case 'blocked':
                return 'danger';
            default:
                return 'default';
        }
    };

    const getPriorityColor = (priority: string): 'primary' | 'default' | 'success' | 'warning' | 'danger' => {
        switch (priority) {
            case 'p0':
                return 'danger';
            case 'p1':
                return 'warning';
            case 'p2':
                return 'primary';
            default:
                return 'default';
        }
    };

    // Loading state
    if (isLoading) {
        return (
            <Box className="p-6 flex items-center justify-center min-h-[400px]">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4" />
                    <p className="text-slate-600 dark:text-neutral-400">Loading work items...</p>
                </div>
            </Box>
        );
    }

    const columns: WorkItemStatus[] = ['ready', 'in-progress', 'blocked', 'done'];

    return (
        <Box className="p-6 space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
                        My Work Items
                    </h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">
                        {isSessionActive && (
                            <span className="inline-flex items-center gap-2 mr-2">
                                <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
                                Session active
                            </span>
                        )}
                        {filteredItems.length} items total
                    </p>
                </div>

                <div className="flex items-center gap-2">
                    <Button
                        variant={viewMode === 'status' ? 'solid' : 'outline'}
                        size="sm"
                        onClick={() => setViewMode('status')}
                    >
                        By Status
                    </Button>
                    <Button
                        variant={viewMode === 'phase' ? 'solid' : 'outline'}
                        size="sm"
                        onClick={() => setViewMode('phase')}
                    >
                        By Phase
                    </Button>
                </div>
            </div>

            {/* DevSecOps Flow Strip */}
            <PersonaFlowStrip
                personaId="engineer"
                currentPhaseId={selectedPhase}
                onPhaseClick={handlePhaseClick}
            />

            {/* Search */}
            <Card>
                <Box className="p-4">
                    <input
                        type="text"
                        placeholder="Search work items..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full px-4 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-neutral-800 text-slate-900 dark:text-neutral-200"
                    />
                </Box>
            </Card>

            {/* Stats */}
            <Grid columns={4} gap={4}>
                {columns.map((status) => (
                    <KpiCard
                        key={status}
                        label={STATUS_LABELS[status]}
                        value={itemsByStatus[status].length}
                        description={`${status === 'in-progress' ? 'Currently working' : status === 'blocked' ? 'Needs attention' : ''}`}
                        status={status === 'blocked' && itemsByStatus[status].length > 0 ? 'critical' : 'healthy'}
                    />
                ))}
            </Grid>

            {/* Kanban Board by Status */}
            {viewMode === 'status' && (
                <div className="grid grid-cols-4 gap-4">
                    {columns.map((status) => (
                        <div key={status} className="space-y-3">
                            {/* Column Header */}
                            <div className={`p-3 rounded-lg ${
                                status === 'in-progress' ? 'bg-blue-100 dark:bg-blue-900/30' :
                                status === 'blocked' ? 'bg-red-100 dark:bg-red-900/30' :
                                status === 'done' ? 'bg-green-100 dark:bg-green-900/30' :
                                'bg-slate-100 dark:bg-neutral-800'
                            }`}>
                                <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                                    {STATUS_LABELS[status]}
                                </h3>
                                <p className="text-xs text-slate-600 dark:text-neutral-400">
                                    {itemsByStatus[status].length} items
                                </p>
                            </div>

                            {/* Work Items */}
                            <div className="space-y-2">
                                {itemsByStatus[status].map((item) => (
                                    <Card
                                        key={item.id}
                                        className="cursor-pointer hover:shadow-md hover:border-blue-400 transition-all"
                                        onClick={() => handleItemClick(item.id)}
                                    >
                                        <Box className="p-3">
                                            <div className="flex items-start justify-between mb-2">
                                                <h4 className="text-sm font-semibold text-slate-900 dark:text-neutral-200 line-clamp-2">
                                                    {item.title}
                                                </h4>
                                            </div>

                                            <div className="flex flex-wrap gap-1 mb-2">
                                                <Chip
                                                    label={PHASE_LABELS[item.phase]}
                                                    tone="primary"
                                                    size="sm"
                                                />
                                                <Chip
                                                    label={item.priority.toUpperCase()}
                                                    tone={getPriorityColor(item.priority)}
                                                    size="sm"
                                                />
                                            </div>

                                            {item.dueDate && (
                                                <p className="text-xs text-slate-500 dark:text-neutral-500">
                                                    Due: {item.dueDate}
                                                </p>
                                            )}
                                        </Box>
                                    </Card>
                                ))}

                                {itemsByStatus[status].length === 0 && (
                                    <div className="p-4 text-center text-slate-400 dark:text-neutral-600 text-sm">
                                        No items
                                    </div>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {/* Kanban Board by Phase */}
            {viewMode === 'phase' && (
                <div className="space-y-4">
                    {Object.entries(itemsByPhase)
                        .filter(([phase]) => !selectedPhase || phase === selectedPhase)
                        .map(([phase, items]) => (
                            <Card key={phase} title={PHASE_LABELS[phase as DevSecOpsPhaseId]} subtitle={`${items.length} items`}>
                                <Box className="p-4">
                                    <div className="grid grid-cols-3 gap-3">
                                        {items.map((item) => (
                                            <div
                                                key={item.id}
                                                onClick={() => handleItemClick(item.id)}
                                                className="p-3 border border-slate-200 dark:border-neutral-700 rounded-lg cursor-pointer hover:border-blue-400 hover:shadow-md transition-all"
                                            >
                                                <div className="flex items-start justify-between mb-2">
                                                    <h4 className="text-sm font-semibold text-slate-900 dark:text-neutral-200">
                                                        {item.title}
                                                    </h4>
                                                    <Chip
                                                        label={item.status.replace('-', ' ')}
                                                        tone={getStatusColor(item.status)}
                                                        size="sm"
                                                    />
                                                </div>
                                                <p className="text-xs text-slate-500 dark:text-neutral-500 line-clamp-2">
                                                    {item.description}
                                                </p>
                                                {item.dueDate && (
                                                    <p className="text-xs text-slate-400 dark:text-neutral-600 mt-2">
                                                        Due: {item.dueDate}
                                                    </p>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                </Box>
                            </Card>
                        ))}

                    {Object.keys(itemsByPhase).length === 0 && (
                        <Card>
                            <Box className="p-8 text-center">
                                <p className="text-slate-500 dark:text-neutral-500">
                                    No work items found
                                </p>
                            </Box>
                        </Card>
                    )}
                </div>
            )}
        </Box>
    );
}
