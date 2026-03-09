/**
 * Services List Page
 *
 * @doc.type page
 * @doc.purpose Services list and visualization
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
import { Server, ExternalLink, Grid3x3, List, ArrowRight, Activity } from 'lucide-react';
import { useConfigServices } from '@/hooks/useConfig';
import type { ServiceConfig } from '@/services/api/configApi';

interface ServiceCardProps {
    service: ServiceConfig;
    viewMode: 'grid' | 'list';
}

function ServiceCard({ service, viewMode }: ServiceCardProps) {
    const categoryStyle = getCategoryStyle('cyan');

    if (viewMode === 'list') {
        return (
            <div className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover}`}>
                <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-4">
                        <div className={`flex-shrink-0 rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                            <Server className="h-6 w-6" />
                        </div>
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                                {service.name || service.id}
                            </h3>
                            <p className="text-sm text-slate-600 dark:text-slate-400">
                                {service.type || 'Microservice'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center space-x-4">
                        {service.url && (
                            <a 
                                href={service.url} 
                                target="_blank" 
                                rel="noopener noreferrer"
                                className="text-blue-600 hover:text-blue-800"
                            >
                                <ExternalLink className="h-5 w-5" />
                            </a>
                        )}
                        <span className={`px-2 py-1 text-xs font-medium rounded ${
                            service.status === 'active' 
                                ? 'bg-green-100 text-green-800' 
                                : 'bg-slate-100 text-slate-600'
                        }`}>
                            {service.status || 'unknown'}
                        </span>
                    </div>
                </div>
                {service.description && (
                    <p className="mt-2 text-sm text-slate-500 dark:text-slate-400 ml-16">
                        {service.description}
                    </p>
                )}
            </div>
        );
    }

    return (
        <div className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover} p-6`}>
            <div className="flex items-start justify-between mb-4">
                <div className={`rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                    <Server className="h-6 w-6" />
                </div>
                <span className={`px-2 py-1 text-xs font-medium rounded ${
                    service.status === 'active' 
                        ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400' 
                        : 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300'
                }`}>
                    {service.status || 'unknown'}
                </span>
            </div>
            <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-1">
                {service.name || service.id}
            </h3>
            <p className="text-sm text-slate-500 dark:text-slate-400 mb-2">
                {service.type || 'Microservice'}
            </p>
            <p className="text-sm text-slate-500 dark:text-slate-400 line-clamp-2 mb-4">
                {service.description || 'No description'}
            </p>

            {service.url && (
                <div className="flex items-center text-sm text-blue-600 dark:text-blue-400">
                    <ExternalLink className="h-4 w-4 mr-2" />
                    <a href={service.url} target="_blank" rel="noopener noreferrer" className="truncate hover:underline">
                        {service.url}
                    </a>
                </div>
            )}

            {service.health_endpoint && (
                <div className="flex items-center text-sm text-slate-500 dark:text-slate-400 mt-2">
                    <Activity className="h-4 w-4 mr-2" />
                    Health: {service.health_endpoint}
                </div>
            )}
        </div>
    );
}

export function ServicesListPage() {
    const { data: services, isLoading } = useConfigServices();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedStatus, setSelectedStatus] = useState<string>('all');
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

    const statuses = useMemo(() => {
        if (!services) return [];
        const statusSet = new Set(services.map((s) => s.status).filter((x): x is string => Boolean(x)));
        return Array.from(statusSet).sort();
    }, [services]);

    const categoryList = useMemo(() => [
        { id: 'all', label: 'All Statuses' },
        ...statuses.map(status => ({ id: status, label: status }))
    ], [statuses]);

    const filteredServices = useMemo(() => {
        if (!services) return [];
        return services.filter((service) => {
            const matchesSearch = !searchQuery ||
                (service.name || service.id).toLowerCase().includes(searchQuery.toLowerCase()) ||
                service.description?.toLowerCase().includes(searchQuery.toLowerCase());
            const matchesStatus = selectedStatus === 'all' || service.status === selectedStatus;
            return matchesSearch && matchesStatus;
        });
    }, [services, searchQuery, selectedStatus]);

    if (isLoading) {
        return (
            <PageLayout title="Services" subtitle="Microservices">
                <LoadingState message="Loading services..." />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Services" subtitle="Microservices">
            <PageHeader
                title="Services"
                subtitle="View and manage microservices"
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
                searchPlaceholder="Search services..."
                categories={categoryList}
                selectedCategory={selectedStatus}
                onCategoryChange={setSelectedStatus}
            />

            <PageSection title={`${filteredServices.length} Services`}>
                {filteredServices.length === 0 ? (
                    <EmptyState
                        title="No services found"
                        description={searchQuery ? 'Try adjusting your search.' : 'No services configured.'}
                    />
                ) : (
                    <PageGrid cols={viewMode === 'grid' ? { sm: 1, md: 2, lg: 3 } : { sm: 1, md: 1, lg: 1 }}>
                        {filteredServices.map((service) => (
                            <ServiceCard key={service.id} service={service} viewMode={viewMode} />
                        ))}
                    </PageGrid>
                )}
            </PageSection>
        </PageLayout>
    );
}

export default ServicesListPage;
