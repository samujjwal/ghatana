/**
 * Department Detail - View and manage department information
 *
 * Shows department details, teams, services, and policies.
 * Connected to real Admin APIs via useAdminApi hooks.
 *
 * @doc.type route
 * @doc.section ADMIN
 */

import { useParams, Link } from 'react-router';
import { MainLayout } from '@/app/Layout';
import {
    ArrowLeft,
    Building2,
    Users,
    Settings,
    Loader2,
    AlertCircle,
    Plus,
    ChevronRight,
} from 'lucide-react';
import { useDepartment, useTeams, type TeamResponse } from '@/hooks';

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

interface TeamCardProps {
    team: TeamResponse;
}

function TeamCard({ team }: TeamCardProps) {
    return (
        <Link
            to={`/admin/organization/teams/${team.id}`}
            className="flex items-center justify-between p-4 bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 hover:shadow-md hover:border-gray-300 dark:hover:border-slate-600 transition-all"
        >
            <div className="flex items-center gap-3">
                <div className="p-2 bg-green-100 dark:bg-green-900/30 rounded-lg">
                    <Users className="h-5 w-5 text-green-600 dark:text-green-400" />
                </div>
                <div>
                    <h4 className="font-medium text-gray-900 dark:text-white">{team.name}</h4>
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                        {team.memberCount ?? 0} members • {team.serviceCount ?? 0} services
                    </p>
                </div>
            </div>
            <ChevronRight className="h-5 w-5 text-gray-400" />
        </Link>
    );
}

export default function DepartmentDetailPage() {
    const { id } = useParams();

    const {
        data: department,
        isLoading: isDepartmentLoading,
        error: departmentError,
        refetch: refetchDepartment,
    } = useDepartment(id || '');

    // Fetch teams for this department
    const {
        data: teamsData,
        isLoading: isTeamsLoading,
        error: teamsError,
    } = useTeams({ departmentId: id });

    const teams = teamsData?.data ?? [];

    if (isDepartmentLoading) {
        return (
            <MainLayout>
                <LoadingState message="Loading department details..." />
            </MainLayout>
        );
    }

    if (departmentError || !department) {
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
                        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
                            Department Not Found
                        </h1>
                    </div>
                    <ErrorState
                        message="Failed to load department details"
                        onRetry={() => refetchDepartment()}
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
                        <div className="flex items-center gap-2">
                            <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
                                {department.name}
                            </h1>
                            <span className="px-2 py-0.5 text-xs font-medium bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300 rounded">
                                {department.type}
                            </span>
                        </div>
                        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                            {department.description || 'No description'}
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
                            <div className="p-2 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
                                <Building2 className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                            </div>
                            <div>
                                <p className="text-2xl font-bold text-gray-900 dark:text-white">
                                    {department.teamCount ?? teams.length}
                                </p>
                                <p className="text-sm text-gray-500 dark:text-gray-400">Teams</p>
                            </div>
                        </div>
                    </div>
                    <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 p-4">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-green-100 dark:bg-green-900/30 rounded-lg">
                                <Users className="h-5 w-5 text-green-600 dark:text-green-400" />
                            </div>
                            <div>
                                <p className="text-2xl font-bold text-gray-900 dark:text-white">
                                    {department.memberCount ?? 0}
                                </p>
                                <p className="text-sm text-gray-500 dark:text-gray-400">Members</p>
                            </div>
                        </div>
                    </div>
                    <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 p-4">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-purple-100 dark:bg-purple-900/30 rounded-lg">
                                <Settings className="h-5 w-5 text-purple-600 dark:text-purple-400" />
                            </div>
                            <div>
                                <p className="text-2xl font-bold text-gray-900 dark:text-white">
                                    {teams.reduce((acc, t) => acc + (t.serviceCount ?? 0), 0)}
                                </p>
                                <p className="text-sm text-gray-500 dark:text-gray-400">Services</p>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Teams List */}
                <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 overflow-hidden">
                    <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-slate-700">
                        <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                            Teams
                        </h2>
                        <button className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg">
                            <Plus className="h-4 w-4" />
                            Add Team
                        </button>
                    </div>
                    <div className="p-4">
                        {isTeamsLoading ? (
                            <LoadingState message="Loading teams..." />
                        ) : teamsError ? (
                            <ErrorState message="Failed to load teams" />
                        ) : teams.length === 0 ? (
                            <div className="text-center py-8">
                                <Users className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                                    No teams yet
                                </h3>
                                <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
                                    This department doesn't have any teams.
                                </p>
                                <button className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700">
                                    <Plus className="h-4 w-4" />
                                    Create Team
                                </button>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {teams.map((team) => (
                                    <TeamCard key={team.id} team={team} />
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </MainLayout>
    );
}
