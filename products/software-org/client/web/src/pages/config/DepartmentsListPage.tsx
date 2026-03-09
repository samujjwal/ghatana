/**
 * Departments List Page
 *
 * @doc.type page
 * @doc.purpose Departments list and visualization
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
import { CARD_STYLES, getCategoryStyle } from '@/components/layouts/page/theme';
import { Building2, Users, Grid3x3, List, ArrowRight } from 'lucide-react';
import { useConfigDepartments } from '@/hooks/useConfig';
import type { DepartmentConfig } from '@/services/api/configApi';

interface DepartmentCardProps {
    department: DepartmentConfig;
    viewMode: 'grid' | 'list';
}

function DepartmentCard({ department, viewMode }: DepartmentCardProps) {
    const categoryStyle = getCategoryStyle(department.type?.toLowerCase() || 'default');
    const agentCount = department.agents?.length || 0;

    if (viewMode === 'list') {
        return (
            <Link
                to={`/config/departments/${department.id || department.name}`}
                className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover}`}
            >
                <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-4">
                        <div className={`flex-shrink-0 rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                            <Building2 className="h-6 w-6" />
                        </div>
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                                {department.name}
                            </h3>
                            <p className="text-sm text-slate-600 dark:text-slate-400">{department.type}</p>
                        </div>
                    </div>
                    <div className="flex items-center space-x-4">
                        <span className="flex items-center text-sm text-slate-500">
                            <Users className="h-4 w-4 mr-1" />
                            {agentCount} agents
                        </span>
                        <ArrowRight className="h-5 w-5 text-slate-400" />
                    </div>
                </div>
                {department.description && (
                    <p className="mt-2 text-sm text-slate-500 dark:text-slate-400 ml-16">
                        {department.description}
                    </p>
                )}
            </Link>
        );
    }

    return (
        <Link
            to={`/config/departments/${department.id || department.name}`}
            className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover} p-6`}
        >
            <div className="flex items-start justify-between mb-4">
                <div className={`rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                    <Building2 className="h-6 w-6" />
                </div>
                <span className={`px-2 py-1 text-xs font-medium rounded ${categoryStyle.bg} ${categoryStyle.text}`}>
                    {department.type}
                </span>
            </div>
            <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-2">
                {department.name}
            </h3>
            <p className="text-sm text-slate-500 dark:text-slate-400 line-clamp-2 mb-4">
                {department.description || 'No description'}
            </p>
            <div className="flex items-center text-sm text-slate-600 dark:text-slate-400">
                <Users className="h-4 w-4 mr-2" />
                {agentCount} agents
            </div>
        </Link>
    );
}

export function DepartmentsListPage() {
    const { data: departments, isLoading } = useConfigDepartments();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedType, setSelectedType] = useState<string>('all');
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

    const types = useMemo(() => {
        if (!departments) return [];
        const typeSet = new Set(departments.map((d) => d.type).filter(Boolean));
        return Array.from(typeSet).sort();
    }, [departments]);

    const categoryList = useMemo(() => [
        { id: 'all', label: 'All Types' },
        ...types.map(type => ({ id: type, label: type }))
    ], [types]);

    const filteredDepartments = useMemo(() => {
        if (!departments) return [];
        return departments.filter((dept) => {
            const matchesSearch = !searchQuery ||
                dept.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                dept.description?.toLowerCase().includes(searchQuery.toLowerCase());
            const matchesType = selectedType === 'all' || dept.type === selectedType;
            return matchesSearch && matchesType;
        });
    }, [departments, searchQuery, selectedType]);

    if (isLoading) {
        return (
            <PageLayout title="Departments" subtitle="Organization structure">
                <LoadingState message="Loading departments..." />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Departments" subtitle="Organization structure">
            <PageHeader
                title="Departments"
                subtitle="View and manage organization departments"
                backLink="/config"
                actions={
                    <div className="flex items-center space-x-2">
                        <button
                            onClick={() => setViewMode('grid')}
                            className={`p-2 rounded ${viewMode === 'grid' ? 'bg-primary-100 text-primary-600' : 'text-slate-400 hover:text-slate-600'}`}
                        >
                            <Grid3x3 className="h-5 w-5" />
                        </button>
                        <button
                            onClick={() => setViewMode('list')}
                            className={`p-2 rounded ${viewMode === 'list' ? 'bg-primary-100 text-primary-600' : 'text-slate-400 hover:text-slate-600'}`}
                        >
                            <List className="h-5 w-5" />
                        </button>
                    </div>
                }
            />

            <PageFilters
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                searchPlaceholder="Search departments..."
                categories={categoryList}
                selectedCategory={selectedType}
                onCategoryChange={setSelectedType}
            />

            <PageSection title={`${filteredDepartments.length} Departments`}>
                {filteredDepartments.length === 0 ? (
                    <EmptyState
                        title="No departments found"
                        description={searchQuery ? 'Try adjusting your search.' : 'No departments configured.'}
                    />
                ) : (
                    <PageGrid cols={viewMode === 'grid' ? { sm: 1, md: 2, lg: 3 } : { sm: 1, md: 1, lg: 1 }}>
                        {filteredDepartments.map((dept) => (
                            <DepartmentCard key={dept.id || dept.name} department={dept} viewMode={viewMode} />
                        ))}
                    </PageGrid>
                )}
            </PageSection>
        </PageLayout>
    );
}

export default DepartmentsListPage;
