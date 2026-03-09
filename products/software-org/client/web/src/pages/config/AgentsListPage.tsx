/**
 * Agents List Page
 *
 * <p><b>Purpose</b><br>
 * Display all AI agents from YAML configuration with filtering, grouping,
 * and detailed visualization. Shows agent capabilities, departments,
 * personality traits, and model configurations.
 *
 * <p><b>Features</b><br>
 * - Grid/list view toggle
 * - Filter by department, role, capabilities
 * - Group by department
 * - Search across all fields
 * - Agent capability badges
 * - Navigate to detail pages
 *
 * @doc.type page
 * @doc.purpose Agents list and visualization
 * @doc.layer product
 * @doc.pattern List Page
 */

import { useState, useMemo } from 'react';
import { Link } from 'react-router';
import {
    PageLayout,
    PageHeader,
    PageFilters,
    PageSection,
    PageGrid,
    LoadingState,
    EmptyState,
} from '@/components/layouts/page';
import { CATEGORY_STYLES, getCategoryStyle, CARD_STYLES } from '@/components/layouts/page/theme';
import {
    Bot,
    Grid3x3,
    List,
    Building2,
    Zap,
    Brain,
    UserCog,
    Sparkles,
} from 'lucide-react';
import { useAgents } from '@/hooks/useConfig';
import type { AgentConfig } from '@/services/api/configApi';

interface AgentCardProps {
    agent: AgentConfig;
    viewMode: 'grid' | 'list';
}

function AgentCard({ agent, viewMode }: AgentCardProps) {
    const capabilities = agent.capabilities || [];
    const department = agent.department || 'Unassigned';
    const role = typeof agent.role === 'string'
        ? agent.role
        : agent.role?.title || agent.role?.name || agent.name;

    const categoryStyle = getCategoryStyle(department.toLowerCase());

    if (viewMode === 'list') {
        return (
            <Link
                to={`/config/agents/${agent.id}`}
                className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover}`}
            >
                <div className="flex items-start justify-between">
                    <div className="flex items-start space-x-4 flex-1">
                        <div className={`flex-shrink-0 rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                            <Bot className="h-6 w-6" />
                        </div>
                        <div className="flex-1 min-w-0">
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-white truncate">
                                {agent.name}
                            </h3>
                            <p className="text-sm text-slate-600 dark:text-slate-400 mt-1">{role}</p>
                            <div className="flex items-center space-x-4 mt-2 text-sm text-slate-500 dark:text-slate-400">
                                <span className="flex items-center">
                                    <Building2 className="h-4 w-4 mr-1" />
                                    {department}
                                </span>
                                {agent.model?.id && (
                                    <span className="flex items-center">
                                        <Brain className="h-4 w-4 mr-1" />
                                        {agent.model.id}
                                    </span>
                                )}
                            </div>
                            {capabilities.length > 0 && (
                                <div className="flex flex-wrap gap-1 mt-3">
                                    {capabilities.slice(0, 5).map((cap: string, idx: number) => (
                                        <span
                                            key={idx}
                                            className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400"
                                        >
                                            {cap}
                                        </span>
                                    ))}
                                    {capabilities.length > 5 && (
                                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-800 dark:bg-slate-700 dark:text-slate-300">
                                            +{capabilities.length - 5} more
                                        </span>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </Link>
        );
    }

    return (
        <Link
            to={`/config/agents/${agent.id}`}
            className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover} p-6`}
        >
            <div className="flex items-start justify-between mb-4">
                <div className="flex items-center space-x-3">
                    <div className={`flex-shrink-0 rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                        <Bot className="h-6 w-6" />
                    </div>
                    <div>
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                            {agent.name}
                        </h3>
                        <p className="text-sm text-slate-600 dark:text-slate-400">{role}</p>
                    </div>
                </div>
                {agent.personality && (
                    <Sparkles className="h-5 w-5 text-yellow-500" />
                )}
            </div>

            <div className="space-y-3">
                <div className="flex items-center text-sm text-slate-600 dark:text-slate-400">
                    <Building2 className="h-4 w-4 mr-2 text-slate-400" />
                    <span>{department}</span>
                </div>

                {agent.model?.id && (
                    <div className="flex items-center text-sm text-slate-600 dark:text-slate-400">
                        <Brain className="h-4 w-4 mr-2 text-slate-400" />
                        <span>{agent.model.id}</span>
                        {agent.model.max_tokens && (
                            <span className="ml-2 text-xs text-slate-500">
                                ({agent.model.max_tokens} tokens)
                            </span>
                        )}
                    </div>
                )}

                {capabilities.length > 0 && (
                    <div>
                        <div className="flex items-center text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                            <Zap className="h-3 w-3 mr-1" />
                            Capabilities
                        </div>
                        <div className="flex flex-wrap gap-1">
                            {capabilities.slice(0, 4).map((cap: string, idx: number) => (
                                <span
                                    key={idx}
                                    className="inline-flex items-center px-2 py-1 rounded text-xs font-medium bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400"
                                >
                                    {cap}
                                </span>
                            ))}
                            {capabilities.length > 4 && (
                                <span className="inline-flex items-center px-2 py-1 rounded text-xs font-medium bg-slate-100 text-slate-800 dark:bg-slate-700 dark:text-slate-300">
                                    +{capabilities.length - 4}
                                </span>
                            )}
                        </div>
                    </div>
                )}
            </div>
        </Link>
    );
}

export function AgentsListPage() {
    const { data: agents, isLoading } = useAgents();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedDepartment, setSelectedDepartment] = useState<string>('all');
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

    // Extract unique departments
    const departments = useMemo(() => {
        if (!agents) return [];
        const deptSet = new Set(agents.map((a) => a.department).filter(Boolean));
        return Array.from(deptSet).sort();
    }, [agents]);

    // Build category list for filter
    const categoryList = useMemo(() => {
        return [
            { id: 'all', label: 'All Departments' },
            ...departments.map(dept => ({ id: dept, label: dept }))
        ];
    }, [departments]);

    // Filter agents
    const filteredAgents = useMemo(() => {
        if (!agents) return [];
        return agents.filter((agent) => {
            const matchesSearch =
                !searchQuery ||
                agent.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                agent.role?.toLowerCase().includes(searchQuery.toLowerCase()) ||
                agent.id.toLowerCase().includes(searchQuery.toLowerCase());

            const matchesDepartment =
                selectedDepartment === 'all' || agent.department === selectedDepartment;

            return matchesSearch && matchesDepartment;
        });
    }, [agents, searchQuery, selectedDepartment]);

    // Group by department
    const groupedAgents = useMemo(() => {
        const groups: Record<string, AgentConfig[]> = {};
        filteredAgents.forEach((agent) => {
            const dept = agent.department || 'Unassigned';
            if (!groups[dept]) groups[dept] = [];
            groups[dept].push(agent);
        });
        return groups;
    }, [filteredAgents]);

    // View toggle actions
    const viewToggleActions = (
        <div className="flex items-center space-x-2 bg-slate-100 dark:bg-slate-800 rounded-lg p-1">
            <button
                onClick={() => setViewMode('grid')}
                className={`p-2 rounded transition-colors ${viewMode === 'grid'
                    ? 'bg-white dark:bg-slate-700 shadow text-blue-600'
                    : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                    }`}
            >
                <Grid3x3 className="h-5 w-5" />
            </button>
            <button
                onClick={() => setViewMode('list')}
                className={`p-2 rounded transition-colors ${viewMode === 'list'
                    ? 'bg-white dark:bg-slate-700 shadow text-blue-600'
                    : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                    }`}
            >
                <List className="h-5 w-5" />
            </button>
        </div>
    );

    if (isLoading) {
        return (
            <PageLayout title="AI Agents">
                <LoadingState message="Loading agents..." />
            </PageLayout>
        );
    }

    if (filteredAgents.length === 0 && !searchQuery && selectedDepartment === 'all') {
        return (
            <PageLayout title="AI Agents">
                <PageHeader
                    title="AI Agents"
                    subtitle={`${agents?.length || 0} agents configured`}
                    icon={Bot}
                    iconBg="bg-blue-100 dark:bg-blue-900/30"
                    iconColor="text-blue-600 dark:text-blue-400"
                    backLink="/config"
                    actions={viewToggleActions}
                />
                <EmptyState
                    icon={UserCog}
                    title="No agents configured"
                    description="Configure AI agents to get started"
                />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="AI Agents">
            <PageHeader
                title="AI Agents"
                subtitle={`${agents?.length || 0} agents configured`}
                icon={Bot}
                iconBg="bg-blue-100 dark:bg-blue-900/30"
                iconColor="text-blue-600 dark:text-blue-400"
                backLink="/config"
                actions={viewToggleActions}
            />

            <PageFilters
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                searchPlaceholder="Search agents by name, role, or capability..."
                categories={categoryList}
                selectedCategory={selectedDepartment}
                onCategoryChange={setSelectedDepartment}
            />

            {/* Agent Groups */}
            <div className="space-y-8">
                {Object.entries(groupedAgents).map(([dept, deptAgents]) => (
                    <PageSection
                        key={dept}
                        title={dept}
                        subtitle={`${deptAgents.length} agent${deptAgents.length !== 1 ? 's' : ''}`}
                    >
                        <PageGrid cols={viewMode === 'grid' ? { sm: 1, md: 2, lg: 3 } : { sm: 1, md: 1, lg: 1 }}>
                            {deptAgents.map((agent) => (
                                <AgentCard
                                    key={agent.id}
                                    agent={agent}
                                    viewMode={viewMode}
                                />
                            ))}
                        </PageGrid>
                    </PageSection>
                ))}
            </div>

            {filteredAgents.length === 0 && (
                <EmptyState
                    icon={UserCog}
                    title="No agents found"
                    description="Try adjusting your search or filter criteria"
                />
            )}
        </PageLayout>
    );
}

export default AgentsListPage;
