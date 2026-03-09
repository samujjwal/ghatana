import { useState, useMemo, useCallback, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { OrgGraphCanvas } from '@/shared/components/org/OrgGraphCanvas';
import { OrgNodeInspector } from '@/shared/components/org/OrgNodeInspector';
import { AiHintBanner } from '@/shared/components';
import { useOrgGraph, useDepartments } from '@/hooks/useVirtualOrg';
import { MOCK_DEPARTMENTS } from './mockOrgData';
import type { OrgGraphNode, OrgGraphNodeType } from '@/shared/types/org';

/**
 * OrgBuilderPage - Graphical organization configuration tool
 *
 * <p><b>Purpose</b><br>
 * Provides a visual interface for viewing and configuring the virtual
 * software organization structure, including departments, services,
 * workflows, integrations, and persona bindings.
 *
 * <p><b>Features</b><br>
 * - Interactive graph visualization of org structure
 * - Filter by node type (departments, services, workflows, etc.)
 * - Filter by department
 * - Node inspector with quick actions
 * - AI-powered configuration hints
 * - Links to related pages (DevSecOps board, reports, settings)
 *
 * <p><b>Layout</b><br>
 * - Left: Filter controls
 * - Center: OrgGraphCanvas
 * - Right: OrgNodeInspector (contextual)
 *
 * <p><b>TODO</b><br>
 * - Wire to backend virtual-org APIs
 * - Add CRUD operations for org entities
 * - Implement drag-and-drop node positioning
 * - Add edge visualization with SVG paths
 *
 * @doc.type page
 * @doc.purpose Organization configuration and visualization
 * @doc.layer product
 * @doc.pattern Builder Page
 */
export function OrgBuilderPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    // Parse URL query params for initial filter state
    const initialType = searchParams.get('type') as OrgGraphNodeType | null;

    // Filter state
    const [filterType, setFilterType] = useState<OrgGraphNodeType | 'all'>(initialType || 'all');
    const [filterDepartmentId, setFilterDepartmentId] = useState<string | null>(null);

    // Selection state
    const [selectedNode, setSelectedNode] = useState<OrgGraphNode | null>(null);

    // Onboarding state
    const [showOnboarding, setShowOnboarding] = useState(() => {
        if (typeof window === 'undefined') return true;
        return window.localStorage.getItem('softwareOrg.orgBuilder.onboarding.dismissed') !== 'true';
    });

    // Fetch graph data using React Query hook (with mock fallback)
    const { data: graphData, isLoading: isLoadingGraph } = useOrgGraph();
    const { data: departments } = useDepartments();

    // Use fetched departments or fall back to mock
    const departmentList = departments ?? MOCK_DEPARTMENTS;

    // Update filter when URL params change
    useEffect(() => {
        const typeParam = searchParams.get('type') as OrgGraphNodeType | null;
        if (typeParam && ['department', 'service', 'workflow', 'integration', 'persona'].includes(typeParam)) {
            setFilterType(typeParam);
        }
    }, [searchParams]);

    // Handle node click
    const handleNodeClick = useCallback((node: OrgGraphNode) => {
        setSelectedNode(node);
    }, []);

    // Handle node double-click (navigate to detail)
    const handleNodeDoubleClick = useCallback(
        (node: OrgGraphNode) => {
            switch (node.type) {
                case 'department':
                    navigate(`/departments/${node.id}`);
                    break;
                case 'service':
                    navigate(`/realtime-monitor?service=${node.id}`);
                    break;
                case 'workflow':
                    navigate(`/workflows?id=${node.id}`);
                    break;
                case 'integration':
                    navigate('/settings?tab=integrations');
                    break;
                case 'persona':
                    navigate(`/personas/${node.id.replace('persona-', '')}`);
                    break;
            }
        },
        [navigate]
    );

    // Handle inspector close
    const handleInspectorClose = useCallback(() => {
        setSelectedNode(null);
    }, []);

    // Dismiss onboarding
    const dismissOnboarding = useCallback(() => {
        setShowOnboarding(false);
        if (typeof window !== 'undefined') {
            window.localStorage.setItem('softwareOrg.orgBuilder.onboarding.dismissed', 'true');
        }
    }, []);

    // Node type options for filter
    const nodeTypeOptions: { value: OrgGraphNodeType | 'all'; label: string; icon: string }[] = [
        { value: 'all', label: 'All', icon: '🌐' },
        { value: 'department', label: 'Departments', icon: '🏢' },
        { value: 'service', label: 'Services', icon: '⚙️' },
        { value: 'workflow', label: 'Workflows', icon: '🔄' },
        { value: 'integration', label: 'Integrations', icon: '🔌' },
        { value: 'persona', label: 'Personas', icon: '👤' },
    ];

    // Stats for header (derived from graph data)
    const stats = useMemo(() => {
        if (!graphData) {
            return {
                departments: 0,
                services: 0,
                workflows: 0,
                integrations: 0,
            };
        }

        const counts = {
            departments: 0,
            services: 0,
            workflows: 0,
            integrations: 0,
        };

        for (const node of graphData.nodes) {
            switch (node.type) {
                case 'department':
                    counts.departments += 1;
                    break;
                case 'service':
                    counts.services += 1;
                    break;
                case 'workflow':
                    counts.workflows += 1;
                    break;
                case 'integration':
                    counts.integrations += 1;
                    break;
            }
        }

        return counts;
    }, [graphData]);

    return (
        <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800">
            {/* Header */}
            <div className="border-b border-slate-200 dark:border-neutral-600 bg-white dark:bg-slate-900 px-6 py-4">
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 flex items-center gap-2">
                            <span>🏗️</span>
                            <span>Org Builder</span>
                        </h1>
                        <p className="text-sm text-slate-500 dark:text-neutral-400 mt-1">
                            Configure and visualize your virtual software organization
                        </p>
                    </div>

                    {/* Quick stats */}
                    <div className="hidden md:flex items-center gap-6">
                        <div className="text-center">
                            <div className="text-2xl font-bold text-blue-600 dark:text-indigo-400">{stats.departments}</div>
                            <div className="text-xs text-slate-500">Departments</div>
                        </div>
                        <div className="text-center">
                            <div className="text-2xl font-bold text-emerald-600 dark:text-green-400">{stats.services}</div>
                            <div className="text-xs text-slate-500">Services</div>
                        </div>
                        <div className="text-center">
                            <div className="text-2xl font-bold text-purple-600 dark:text-violet-400">{stats.workflows}</div>
                            <div className="text-xs text-slate-500">Workflows</div>
                        </div>
                        <div className="text-center">
                            <div className="text-2xl font-bold text-orange-600 dark:text-orange-400">{stats.integrations}</div>
                            <div className="text-xs text-slate-500">Integrations</div>
                        </div>
                    </div>
                </div>
            </div>

            {/* Main content */}
            <div className="flex h-[calc(100vh-140px)]">
                {/* Left sidebar - Filters */}
                <div className="w-64 border-r border-slate-200 dark:border-neutral-600 bg-white dark:bg-slate-900 p-4 overflow-y-auto">
                    <h2 className="text-sm font-semibold text-slate-900 dark:text-neutral-100 mb-4">Filters</h2>

                    {/* Node type filter */}
                    <div className="mb-6">
                        <label className="block text-xs font-medium text-slate-500 dark:text-neutral-400 mb-2">
                            Node Type
                        </label>
                        <div className="space-y-1">
                            {nodeTypeOptions.map((option) => (
                                <button
                                    key={option.value}
                                    type="button"
                                    onClick={() => setFilterType(option.value)}
                                    className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-colors ${filterType === option.value
                                        ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300'
                                        : 'text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                                        }`}
                                >
                                    <span>{option.icon}</span>
                                    <span>{option.label}</span>
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* Department filter */}
                    <div className="mb-6">
                        <label className="block text-xs font-medium text-slate-500 dark:text-neutral-400 mb-2">
                            Department
                        </label>
                        <select
                            value={filterDepartmentId || ''}
                            onChange={(e) => setFilterDepartmentId(e.target.value || null)}
                            className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-600 bg-white dark:bg-neutral-800 text-sm text-slate-900 dark:text-neutral-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        >
                            <option value="">All Departments</option>
                            {departmentList.map((dept) => (
                                <option key={dept.id} value={dept.id}>
                                    {dept.icon} {dept.name}
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Quick links */}
                    <div>
                        <label className="block text-xs font-medium text-slate-500 dark:text-neutral-400 mb-2">
                            Quick Links
                        </label>
                        <div className="space-y-1">
                            <button
                                type="button"
                                onClick={() => navigate('/departments')}
                                className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                            >
                                <span>🏢</span>
                                <span>Departments Directory</span>
                            </button>
                            <button
                                type="button"
                                onClick={() => navigate('/workflows')}
                                className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                            >
                                <span>🔗</span>
                                <span>Workflow Explorer</span>
                            </button>
                            <button
                                type="button"
                                onClick={() => navigate('/settings?tab=integrations')}
                                className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                            >
                                <span>🔌</span>
                                <span>Manage Integrations</span>
                            </button>
                            <button
                                type="button"
                                onClick={() => navigate('/personas')}
                                className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                            >
                                <span>👤</span>
                                <span>Persona Config</span>
                            </button>
                        </div>
                    </div>
                </div>

                {/* Center - Graph Canvas */}
                <div className="flex-1 overflow-auto">
                    {/* Onboarding hint */}
                    {showOnboarding && (
                        <div className="p-4 pb-0">
                            <AiHintBanner
                                title="Welcome to Org Builder"
                                body="This is your graphical view of the virtual software organization. Click on any node to see details and quick actions. Double-click to navigate to the detail page. Use the filters on the left to focus on specific areas."
                                onDismiss={dismissOnboarding}
                            />
                        </div>
                    )}

                    {/* Loading state */}
                    {isLoadingGraph && (
                        <div className="flex items-center justify-center h-64">
                            <div className="text-center">
                                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto mb-2"></div>
                                <p className="text-sm text-slate-500 dark:text-neutral-400">Loading organization data...</p>
                            </div>
                        </div>
                    )}

                    {/* Graph */}
                    {!isLoadingGraph && graphData && (
                        <OrgGraphCanvas
                            data={graphData}
                            selectedNodeId={selectedNode?.id}
                            onNodeClick={handleNodeClick}
                            onNodeDoubleClick={handleNodeDoubleClick}
                            filterType={filterType}
                            filterDepartmentId={filterDepartmentId}
                            showEdges={true}
                            layout="grid"
                            className="min-h-full"
                        />
                    )}
                </div>

                {/* Right sidebar - Inspector */}
                <div className="w-80 border-l border-slate-200 dark:border-neutral-600 bg-white dark:bg-slate-900 overflow-y-auto">
                    <OrgNodeInspector
                        node={selectedNode}
                        onClose={handleInspectorClose}
                    />
                </div>
            </div>
        </div>
    );
}

export default OrgBuilderPage;
