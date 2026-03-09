/**
 * Team Detail - View and manage team information
 *
 * Shows team details, members, owned services, and linked workflows.
 * Connected to real Admin APIs via useAdminApi hooks.
 *
 * @doc.type route
 * @doc.section ADMIN
 */

import { useState } from 'react';
import { useParams, Link } from 'react-router';
import { MainLayout } from '@/app/Layout';
import {
    ArrowLeft,
    Users,
    Settings,
    Loader2,
    AlertCircle,
    Package,
    Plus,
    ChevronRight,
    RefreshCw,
} from 'lucide-react';
import { useTeam, useTeamServices, useUpdateServiceOwnership, useTeams, type ServiceResponse } from '@/hooks';
import { Drawer, FormField, Select, Checkbox } from '@/components/admin';

interface LoadingStateProps {
    message?: string;
}

function LoadingState({ message = 'Loading...' }: LoadingStateProps) {
    return (
        <div className="flex items-center justify-center py-12">
            <Loader2 className="h-6 w-6 animate-spin text-blue-600 mr-2" />
            <span className="text-gray-600 dark:text-gray-400">{message}</span>
        </div>
    );
}

interface ErrorStateProps {
    message: string;
    onRetry?: () => void;
}

function ErrorState({ message, onRetry }: ErrorStateProps) {
    return (
        <div className="flex flex-col items-center justify-center py-12">
            <AlertCircle className="h-8 w-8 text-red-500 mb-2" />
            <p className="text-gray-600 dark:text-gray-400 mb-4">{message}</p>
            {onRetry && (
                <button
                    onClick={onRetry}
                    className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"
                >
                    Retry
                </button>
            )}
        </div>
    );
}

interface ServiceCardProps {
    service: ServiceResponse;
    selected?: boolean;
    onToggleSelect?: (serviceId: string) => void;
}

function ServiceCard({ service, selected, onToggleSelect }: ServiceCardProps) {
    return (
        <div className="flex items-center justify-between p-4 bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700">
            <div className="flex items-center gap-3">
                {onToggleSelect && (
                    <input
                        type="checkbox"
                        checked={selected}
                        onChange={() => onToggleSelect(service.id)}
                        className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                    />
                )}
                <div className="p-2 bg-purple-100 dark:bg-purple-900/30 rounded-lg">
                    <Package className="h-5 w-5 text-purple-600 dark:text-purple-400" />
                </div>
                <div>
                    <h4 className="font-medium text-gray-900 dark:text-white">{service.name}</h4>
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                        {service.key} • {service.status}
                    </p>
                </div>
            </div>
            <ChevronRight className="h-5 w-5 text-gray-400" />
        </div>
    );
}

export default function TeamDetailPage() {
    const { id } = useParams();
    const [isReassignDrawerOpen, setIsReassignDrawerOpen] = useState(false);
    const [selectedServiceIds, setSelectedServiceIds] = useState<string[]>([]);
    const [targetTeamId, setTargetTeamId] = useState('');

    const {
        data: team,
        isLoading: isTeamLoading,
        error: teamError,
        refetch: refetchTeam,
    } = useTeam(id || '');

    const {
        data: servicesData,
        isLoading: isServicesLoading,
        error: servicesError,
        refetch: refetchServices,
    } = useTeamServices(id || '');

    const { data: teamsData } = useTeams();
    const teams = teamsData?.data ?? [];

    const services = servicesData?.data ?? [];
    const updateOwnershipMutation = useUpdateServiceOwnership();

    const handleToggleService = (serviceId: string) => {
        setSelectedServiceIds(prev =>
            prev.includes(serviceId)
                ? prev.filter(id => id !== serviceId)
                : [...prev, serviceId]
        );
    };

    const handleSelectAll = () => {
        if (selectedServiceIds.length === services.length) {
            setSelectedServiceIds([]);
        } else {
            setSelectedServiceIds(services.map(s => s.id));
        }
    };

    const handleReassign = async () => {
        if (!targetTeamId || selectedServiceIds.length === 0) return;
        try {
            await updateOwnershipMutation.mutateAsync({
                serviceIds: selectedServiceIds,
                newOwnerTeamId: targetTeamId,
                tenantIdToUpdate: team?.tenantId || '',
            });
            setIsReassignDrawerOpen(false);
            setSelectedServiceIds([]);
            setTargetTeamId('');
            refetchServices();
        } catch (error) {
            console.error('Failed to reassign services:', error);
        }
    };

    if (isTeamLoading) {
        return (
            <MainLayout>
                <LoadingState message="Loading team details..." />
            </MainLayout>
        );
    }

    if (teamError || !team) {
        return (
            <MainLayout>
                <div className="space-y-6">
                    <div className="flex items-center gap-4">
                        <Link
                            to="/admin/organization"
                            className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-slate-800 transition-colors"
                        >
                            <ArrowLeft className="h-5 w-5 text-gray-500" />
                        </Link>
                        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Team Not Found</h1>
                    </div>
                    <ErrorState
                        message="Failed to load team details"
                        onRetry={() => refetchTeam()}
                    />
                </div>
            </MainLayout>
        );
    }

    return (
        <MainLayout>
            <div className="space-y-6">
                {/* Header */}
                <div className="flex items-center gap-4">
                    <Link
                        to="/admin/organization"
                        className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-slate-800 transition-colors"
                    >
                        <ArrowLeft className="h-5 w-5 text-gray-500" />
                    </Link>
                    <div className="flex-1">
                        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
                            {team.name}
                        </h1>
                        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                            {team.description || 'No description'}
                        </p>
                    </div>
                    <button className="inline-flex items-center gap-2 px-4 py-2 bg-white dark:bg-slate-800 border border-gray-300 dark:border-slate-600 text-gray-700 dark:text-gray-300 rounded-lg text-sm font-medium hover:bg-gray-50 dark:hover:bg-slate-700 transition-colors">
                        <Settings className="h-4 w-4" />
                        Edit
                    </button>
                </div>

                {/* Stats Cards */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 p-4">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-green-100 dark:bg-green-900/30 rounded-lg">
                                <Users className="h-5 w-5 text-green-600 dark:text-green-400" />
                            </div>
                            <div>
                                <p className="text-2xl font-bold text-gray-900 dark:text-white">
                                    {team.memberCount ?? 0}
                                </p>
                                <p className="text-sm text-gray-500 dark:text-gray-400">Members</p>
                            </div>
                        </div>
                    </div>
                    <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 p-4">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-purple-100 dark:bg-purple-900/30 rounded-lg">
                                <Package className="h-5 w-5 text-purple-600 dark:text-purple-400" />
                            </div>
                            <div>
                                <p className="text-2xl font-bold text-gray-900 dark:text-white">
                                    {services.length}
                                </p>
                                <p className="text-sm text-gray-500 dark:text-gray-400">Owned Services</p>
                            </div>
                        </div>
                    </div>
                    <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 p-4">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
                                <Settings className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                            </div>
                            <div>
                                <p className="text-2xl font-bold text-gray-900 dark:text-white">
                                    {team.serviceCount ?? 0}
                                </p>
                                <p className="text-sm text-gray-500 dark:text-gray-400">Workflows</p>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Owned Services */}
                <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 overflow-hidden">
                    <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-slate-700">
                        <div className="flex items-center gap-3">
                            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                                Owned Services
                            </h2>
                            {selectedServiceIds.length > 0 && (
                                <span className="px-2 py-1 text-xs font-medium bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300 rounded">
                                    {selectedServiceIds.length} selected
                                </span>
                            )}
                        </div>
                        <div className="flex items-center gap-2">
                            {services.length > 0 && (
                                <>
                                    <button
                                        onClick={handleSelectAll}
                                        className="px-3 py-1.5 text-sm font-medium text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                                    >
                                        {selectedServiceIds.length === services.length ? 'Deselect All' : 'Select All'}
                                    </button>
                                    <button
                                        onClick={() => setIsReassignDrawerOpen(true)}
                                        disabled={selectedServiceIds.length === 0}
                                        className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed"
                                    >
                                        <RefreshCw className="h-4 w-4" />
                                        Reassign
                                    </button>
                                </>
                            )}
                        </div>
                    </div>
                    <div className="p-4">{isServicesLoading ? (
                            <LoadingState message="Loading services..." />
                        ) : servicesError ? (
                            <ErrorState message="Failed to load services" />
                        ) : services.length === 0 ? (
                            <div className="text-center py-8">
                                <Package className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                                    No services owned
                                </h3>
                                <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
                                    This team doesn't own any services yet.
                                </p>
                                <button className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700">
                                    <Plus className="h-4 w-4" />
                                    Assign Service
                                </button>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {services.map((service) => (
                                    <ServiceCard
                                        key={service.id}
                                        service={service}
                                        selected={selectedServiceIds.includes(service.id)}
                                        onToggleSelect={handleToggleService}
                                    />
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* Bulk Reassignment Drawer */}
            <Drawer
                isOpen={isReassignDrawerOpen}
                onClose={() => setIsReassignDrawerOpen(false)}
                title="Reassign Services"
                size="md"
            >
                <div className="space-y-4">
                    <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-3">
                        <p className="text-sm text-blue-700 dark:text-blue-300">
                            {selectedServiceIds.length} service{selectedServiceIds.length !== 1 ? 's' : ''} will be reassigned to the selected team.
                        </p>
                    </div>

                    <FormField label="Target Team" name="targetTeam" required>
                        <Select
                            value={targetTeamId}
                            onChange={(e) => setTargetTeamId(e.target.value)}
                        >
                            <option value="">Select a team...</option>
                            {teams.filter(t => t.id !== id).map(team => (
                                <option key={team.id} value={team.id}>
                                    {team.name}
                                </option>
                            ))}
                        </Select>
                    </FormField>

                    <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                        <button
                            onClick={() => setIsReassignDrawerOpen(false)}
                            className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleReassign}
                            disabled={!targetTeamId || updateOwnershipMutation.isPending}
                            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg"
                        >
                            {updateOwnershipMutation.isPending ? 'Reassigning...' : 'Reassign Services'}
                        </button>
                    </div>
                </div>
            </Drawer>
        </MainLayout>
    );
}
