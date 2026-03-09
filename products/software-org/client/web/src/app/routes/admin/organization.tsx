/**
 * Organization - Consolidated Org Management
 *
 * Single page for managing departments, teams, personas, and roles.
 * Connected to real Admin APIs via useAdminApi hooks.
 *
 * @doc.type route
 * @doc.section ADMIN
 */

import { useState } from 'react';
import { Link } from 'react-router';
import { MainLayout } from '@/app/Layout';
import {
    Building2,
    Users,
    UserCircle,
    Shield,
    Plus,
    Search,
    ChevronRight,
    Settings,
    Loader2,
    AlertCircle,
    UserPlus,
    X,
    MoreVertical,
    AlertTriangle,
} from 'lucide-react';
import {
    useDepartments,
    useTeams,
    usePersonas,
    useRoles,
    useCreateDepartment,
    useCreateTeam,
    useCreatePersona,
    useCreateRole,
    useUpdatePersonaMembers,
    useTenants,
    useTenantDeactivationCheck,
    useDeactivateTenant,
    type DepartmentResponse,
    type TeamResponse,
    type PersonaResponse,
    type RoleResponse,
    type TenantResponse,
} from '@/hooks';
import { Drawer, FormField, Input, Textarea, Select, ConfirmDialog } from '@/components/admin';

type TabType = 'tenants' | 'departments' | 'teams' | 'personas' | 'roles';

const tabs: { id: TabType; label: string; icon: React.ElementType }[] = [
    { id: 'tenants', label: 'Tenants', icon: Building2 },
    { id: 'departments', label: 'Departments', icon: Building2 },
    { id: 'teams', label: 'Teams', icon: Users },
    { id: 'personas', label: 'Personas', icon: UserCircle },
    { id: 'roles', label: 'Roles', icon: Shield },
];

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

interface EmptyStateProps {
    entityType: string;
    onAdd?: () => void;
}

function EmptyState({ entityType, onAdd }: EmptyStateProps) {
    return (
        <div className="flex flex-col items-center justify-center py-12 bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700">
            <Building2 className="h-12 w-12 text-gray-400 mb-4" />
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                No {entityType} found
            </h3>
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
                Get started by creating your first {entityType.toLowerCase().slice(0, -1)}.
            </p>
            {onAdd && (
                <button
                    onClick={onAdd}
                    className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"
                >
                    <Plus className="h-4 w-4" />
                    Add {entityType.slice(0, -1)}
                </button>
            )}
        </div>
    );
}

interface TenantsListProps {
    tenants: TenantResponse[];
    searchQuery: string;
    onDeactivate?: (tenant: TenantResponse) => void;
}

function TenantsList({ tenants, searchQuery, onDeactivate }: TenantsListProps) {
    const [showInactive, setShowInactive] = useState(false);

    const filtered = tenants.filter((tenant) => {
        const matchesSearch =
            tenant.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
            (tenant.slug ?? tenant.key ?? '').toLowerCase().includes(searchQuery.toLowerCase());
        const matchesStatus = showInactive || tenant.status === 'active';
        return matchesSearch && matchesStatus;
    });

    if (filtered.length === 0) {
        return <EmptyState entityType="Tenants" />;
    }

    return (
        <div className="space-y-4">
            {/* Show Inactive Toggle */}
            <div className="flex justify-end">
                <label className="flex items-center gap-2 cursor-pointer">
                    <input
                        type="checkbox"
                        checked={showInactive}
                        onChange={(e) => setShowInactive(e.target.checked)}
                        className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                    />
                    <span className="text-sm text-gray-600 dark:text-gray-400">Show inactive tenants</span>
                </label>
            </div>

            {/* Tenants Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {filtered.map((tenant) => (
                    <div
                        key={tenant.id}
                        className={`p-4 bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 ${tenant.status === 'inactive' ? 'opacity-60' : ''
                            }`}
                    >
                        <div className="flex items-start justify-between mb-3">
                            <div className="flex items-center gap-3">
                                <div className="p-2 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
                                    <Building2 className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                                </div>
                                <div>
                                    <h3 className="font-semibold text-gray-900 dark:text-white">{tenant.name}</h3>
                                    <p className="text-sm text-gray-500 dark:text-gray-400">{tenant.slug}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className={`px-2 py-0.5 text-xs rounded ${tenant.status === 'active'
                                        ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
                                        : 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
                                    }`}>
                                    {tenant.status}
                                </span>
                                {tenant.status === 'active' && onDeactivate && (
                                    <button
                                        onClick={() => onDeactivate(tenant)}
                                        className="p-1.5 hover:bg-gray-100 dark:hover:bg-slate-700 rounded"
                                        title="Deactivate tenant"
                                    >
                                        <MoreVertical className="h-4 w-4 text-gray-400" />
                                    </button>
                                )}
                            </div>
                        </div>
                        <div className="flex gap-4 text-sm text-gray-600 dark:text-gray-400">
                            <span>{tenant.environmentCount ?? 0} environments</span>
                            <span>{tenant.contactEmail}</span>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

interface DepartmentsListProps {
    departments: DepartmentResponse[];
    searchQuery: string;
}

function DepartmentsList({ departments, searchQuery }: DepartmentsListProps) {
    const filtered = departments.filter((dept) =>
        dept.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        (dept.description?.toLowerCase().includes(searchQuery.toLowerCase()) ?? false)
    );

    if (filtered.length === 0) {
        return <EmptyState entityType="Departments" />;
    }

    return (
        <div className="space-y-3">
            {filtered.map((dept) => (
                <Link
                    key={dept.id}
                    to={`/admin/organization/departments/${dept.id}`}
                    className="flex items-center justify-between p-4 bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 hover:shadow-md hover:border-gray-300 dark:hover:border-slate-600 transition-all"
                >
                    <div className="flex items-center gap-4">
                        <div className="p-3 bg-blue-100 dark:bg-blue-900/30 rounded-xl">
                            <Building2 className="h-6 w-6 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div>
                            <h3 className="font-semibold text-gray-900 dark:text-white">{dept.name}</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400">
                                {dept.description || dept.type}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-6">
                        <div className="text-right">
                            <p className="text-lg font-bold text-gray-900 dark:text-white">
                                {dept.memberCount ?? 0}
                            </p>
                            <p className="text-xs text-gray-500 dark:text-gray-500">members</p>
                        </div>
                        <div className="text-right">
                            <p className="text-lg font-bold text-gray-900 dark:text-white">
                                {dept.teamCount ?? 0}
                            </p>
                            <p className="text-xs text-gray-500 dark:text-gray-500">teams</p>
                        </div>
                        <ChevronRight className="h-5 w-5 text-gray-400" />
                    </div>
                </Link>
            ))}
        </div>
    );
}

interface TeamsListProps {
    teams: TeamResponse[];
    searchQuery: string;
}

function TeamsList({ teams, searchQuery }: TeamsListProps) {
    const filtered = teams.filter((team) =>
        team.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        (team.description?.toLowerCase().includes(searchQuery.toLowerCase()) ?? false)
    );

    if (filtered.length === 0) {
        return <EmptyState entityType="Teams" />;
    }

    return (
        <div className="space-y-3">
            {filtered.map((team) => (
                <Link
                    key={team.id}
                    to={`/admin/organization/teams/${team.id}`}
                    className="flex items-center justify-between p-4 bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 hover:shadow-md hover:border-gray-300 dark:hover:border-slate-600 transition-all"
                >
                    <div className="flex items-center gap-4">
                        <div className="p-3 bg-green-100 dark:bg-green-900/30 rounded-xl">
                            <Users className="h-6 w-6 text-green-600 dark:text-green-400" />
                        </div>
                        <div>
                            <h3 className="font-semibold text-gray-900 dark:text-white">{team.name}</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400">
                                {team.description || `Team ID: ${team.id.slice(0, 8)}...`}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-4">
                        <div className="text-right">
                            <p className="text-lg font-bold text-gray-900 dark:text-white">
                                {team.memberCount ?? 0}
                            </p>
                            <p className="text-xs text-gray-500 dark:text-gray-500">members</p>
                        </div>
                        <div className="text-right">
                            <p className="text-lg font-bold text-gray-900 dark:text-white">
                                {team.serviceCount ?? 0}
                            </p>
                            <p className="text-xs text-gray-500 dark:text-gray-500">services</p>
                        </div>
                        <ChevronRight className="h-5 w-5 text-gray-400" />
                    </div>
                </Link>
            ))}
        </div>
    );
}

interface PersonasListProps {
    personas: PersonaResponse[];
    searchQuery: string;
    onAssignMembers?: (persona: PersonaResponse) => void;
}

function PersonasList({ personas, searchQuery, onAssignMembers }: PersonasListProps) {
    const filtered = personas.filter((persona) =>
        persona.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        (persona.description?.toLowerCase().includes(searchQuery.toLowerCase()) ?? false)
    );

    if (filtered.length === 0) {
        return <EmptyState entityType="Personas" />;
    }

    return (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {filtered.map((persona) => (
                <div
                    key={persona.id}
                    className="p-4 bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700"
                >
                    <div className="flex items-start justify-between">
                        <div className="flex items-center gap-3">
                            <div className={`p-2 rounded-lg ${persona.type === 'AGENT'
                                    ? 'bg-cyan-100 dark:bg-cyan-900/30'
                                    : 'bg-purple-100 dark:bg-purple-900/30'
                                }`}>
                                <UserCircle className={`h-5 w-5 ${persona.type === 'AGENT'
                                        ? 'text-cyan-600 dark:text-cyan-400'
                                        : 'text-purple-600 dark:text-purple-400'
                                    }`} />
                            </div>
                            <div>
                                <div className="flex items-center gap-2">
                                    <h3 className="font-semibold text-gray-900 dark:text-white">
                                        {persona.name}
                                    </h3>
                                    <span className={`px-1.5 py-0.5 text-xs rounded ${persona.type === 'AGENT'
                                            ? 'bg-cyan-100 text-cyan-700 dark:bg-cyan-900/30 dark:text-cyan-300'
                                            : 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300'
                                        }`}>
                                        {persona.type}
                                    </span>
                                </div>
                                <p className="text-sm text-gray-500 dark:text-gray-400">
                                    {persona.description || persona.slug}
                                </p>
                            </div>
                        </div>
                        <button className="p-1 hover:bg-gray-100 dark:hover:bg-slate-700 rounded">
                            <Settings className="h-4 w-4 text-gray-400" />
                        </button>
                    </div>
                    <div className="mt-3 pt-3 border-t border-gray-100 dark:border-slate-700 flex justify-between">
                        <p className="text-sm text-gray-600 dark:text-gray-400">
                            <span className="font-medium text-gray-900 dark:text-white">
                                {persona.roleCount ?? 0}
                            </span> roles
                        </p>
                        {persona.primaryTeamName && (
                            <p className="text-sm text-gray-500 dark:text-gray-400">
                                Team: {persona.primaryTeamName}
                            </p>
                        )}
                        <span className={`px-2 py-0.5 text-xs rounded ${persona.active
                                ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
                                : 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
                            }`}>
                            {persona.active ? 'Active' : 'Inactive'}
                        </span>
                    </div>
                </div>
            ))}
        </div>
    );
}

interface RolesListProps {
    roles: RoleResponse[];
    searchQuery: string;
}

function RolesList({ roles, searchQuery }: RolesListProps) {
    const filtered = roles.filter((role) =>
        role.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        (role.description?.toLowerCase().includes(searchQuery.toLowerCase()) ?? false)
    );

    if (filtered.length === 0) {
        return <EmptyState entityType="Roles" />;
    }

    return (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {filtered.map((role) => (
                <div
                    key={role.id}
                    className="p-4 bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700"
                >
                    <div className="flex items-start justify-between">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-amber-100 dark:bg-amber-900/30 rounded-lg">
                                <Shield className="h-5 w-5 text-amber-600 dark:text-amber-400" />
                            </div>
                            <div>
                                <h3 className="font-semibold text-gray-900 dark:text-white">{role.name}</h3>
                                <p className="text-sm text-gray-500 dark:text-gray-400">
                                    {role.description || `${role.permissions.length} permissions`}
                                </p>
                            </div>
                        </div>
                        <button className="p-1 hover:bg-gray-100 dark:hover:bg-slate-700 rounded">
                            <Settings className="h-4 w-4 text-gray-400" />
                        </button>
                    </div>
                    <div className="mt-3 pt-3 border-t border-gray-100 dark:border-slate-700 flex justify-between">
                        <p className="text-sm text-gray-600 dark:text-gray-400">
                            <span className="font-medium text-gray-900 dark:text-white">
                                {role.userCount ?? 0}
                            </span> users
                        </p>
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                            {role.permissions.length} permissions
                        </p>
                    </div>
                </div>
            ))}
        </div>
    );
}

export default function OrganizationPage() {
    const [activeTab, setActiveTab] = useState<TabType>('departments');
    const [searchQuery, setSearchQuery] = useState('');

    // Drawer state
    const [isDepartmentDrawerOpen, setIsDepartmentDrawerOpen] = useState(false);
    const [isTeamDrawerOpen, setIsTeamDrawerOpen] = useState(false);
    const [isPersonaDrawerOpen, setIsPersonaDrawerOpen] = useState(false);
    const [isRoleDrawerOpen, setIsRoleDrawerOpen] = useState(false);
    const [isMemberDrawerOpen, setIsMemberDrawerOpen] = useState(false);
    const [selectedPersona, setSelectedPersona] = useState<PersonaResponse | null>(null);
    const [isDeactivateDialogOpen, setIsDeactivateDialogOpen] = useState(false);
    const [tenantToDeactivate, setTenantToDeactivate] = useState<TenantResponse | null>(null);

    // Form state
    const [deptForm, setDeptForm] = useState({ name: '', description: '', slug: '' });
    const [teamForm, setTeamForm] = useState({ name: '', description: '', slug: '', departmentId: '' });
    const [personaForm, setPersonaForm] = useState({
        name: '',
        description: '',
        type: 'HUMAN' as 'HUMAN' | 'AGENT',
        primaryTeamId: ''
    });
    const [roleForm, setRoleForm] = useState({
        name: '',
        description: '',
        scope: 'TENANT' as 'TENANT' | 'ENVIRONMENT' | 'GLOBAL',
        permissions: [] as string[]
    });
    const [memberForm, setMemberForm] = useState<{
        userIds: string[];
        teamIds: string[];
        agentIds: string[];
        searchQuery: string;
    }>({
        userIds: [],
        teamIds: [],
        agentIds: [],
        searchQuery: '',
    });

    // Fetch data using Admin API hooks
    const {
        data: departmentsData,
        isLoading: isDepartmentsLoading,
        error: departmentsError,
        refetch: refetchDepartments,
    } = useDepartments();

    const {
        data: tenantsData,
        isLoading: isTenantsLoading,
        error: tenantsError,
        refetch: refetchTenants,
    } = useTenants();

    // Create mutations
    const createDepartment = useCreateDepartment();
    const createTeam = useCreateTeam();
    const createPersona = useCreatePersona();
    const createRole = useCreateRole();
    const updatePersonaMembers = useUpdatePersonaMembers();
    const deactivateTenantMutation = useDeactivateTenant();

    // Deactivation check (only fetch when dialog is open)
    const { data: deactivationCheck, isLoading: isCheckingDeactivation } = useTenantDeactivationCheck(
        tenantToDeactivate?.id || ''
    );

    // Handler functions
    const handleCreateDepartment = async () => {
        try {
            await createDepartment.mutateAsync({
                name: deptForm.name,
                description: deptForm.description,
                slug: deptForm.slug,
            });
            setIsDepartmentDrawerOpen(false);
            setDeptForm({ name: '', description: '', slug: '' });
            refetchDepartments();
        } catch (error) {
            console.error('Failed to create department:', error);
        }
    };

    const handleOpenMemberDrawer = (persona: PersonaResponse) => {
        setSelectedPersona(persona);
        setMemberForm({
            userIds: [],
            teamIds: [],
            agentIds: [],
            searchQuery: '',
        });
        setIsMemberDrawerOpen(true);
    };

    const handleAssignMembers = async () => {
        if (!selectedPersona) return;
        try {
            await updatePersonaMembers.mutateAsync({
                personaId: selectedPersona.id,
                userIds: memberForm.userIds,
                teamIds: memberForm.teamIds,
                agentIds: memberForm.agentIds,
            });
            setIsMemberDrawerOpen(false);
            setSelectedPersona(null);
            refetchPersonas();
        } catch (error) {
            console.error('Failed to assign members:', error);
        }
    };

    const handleToggleUserId = (userId: string) => {
        setMemberForm(prev => ({
            ...prev,
            userIds: prev.userIds.includes(userId)
                ? prev.userIds.filter(id => id !== userId)
                : [...prev.userIds, userId]
        }));
    };

    const handleToggleTeamId = (teamId: string) => {
        setMemberForm(prev => ({
            ...prev,
            teamIds: prev.teamIds.includes(teamId)
                ? prev.teamIds.filter(id => id !== teamId)
                : [...prev.teamIds, teamId]
        }));
    };

    const handleOpenDeactivateDialog = (tenant: TenantResponse) => {
        setTenantToDeactivate(tenant);
        setIsDeactivateDialogOpen(true);
    };

    const handleDeactivateTenant = async () => {
        if (!tenantToDeactivate) return;
        try {
            await deactivateTenantMutation.mutateAsync(tenantToDeactivate.id);
            setIsDeactivateDialogOpen(false);
            setTenantToDeactivate(null);
            refetchTenants();
        } catch (error) {
            console.error('Failed to deactivate tenant:', error);
        }
    };

    const handleCreateTeam = async () => {
        try {
            await createTeam.mutateAsync({
                name: teamForm.name,
                description: teamForm.description,
                slug: teamForm.slug,
                departmentId: teamForm.departmentId,
            });
            setIsTeamDrawerOpen(false);
            setTeamForm({ name: '', description: '', slug: '', departmentId: '' });
            refetchTeams();
        } catch (error) {
            console.error('Failed to create team:', error);
        }
    };

    const handleCreatePersona = async () => {
        try {
            await createPersona.mutateAsync({
                name: personaForm.name,
                description: personaForm.description,
                type: personaForm.type,
                primaryTeamId: personaForm.primaryTeamId,
            });
            setIsPersonaDrawerOpen(false);
            setPersonaForm({ name: '', description: '', type: 'HUMAN', primaryTeamId: '' });
            refetchPersonas();
        } catch (error) {
            console.error('Failed to create persona:', error);
        }
    };

    // Member drawer handlers moved to earlier in file (lines 540-560)

    const handleCreateRole = async () => {
        try {
            await createRole.mutateAsync({
                name: roleForm.name,
                description: roleForm.description,
                scope: roleForm.scope,
                permissions: roleForm.permissions,
            });
            setIsRoleDrawerOpen(false);
            setRoleForm({ name: '', description: '', scope: 'TENANT', permissions: [] });
            refetchRoles();
        } catch (error) {
            console.error('Failed to create role:', error);
        }
    };

    const {
        data: teamsData,
        isLoading: isTeamsLoading,
        error: teamsError,
        refetch: refetchTeams,
    } = useTeams();

    const {
        data: personasData,
        isLoading: isPersonasLoading,
        error: personasError,
        refetch: refetchPersonas,
    } = usePersonas();

    const {
        data: rolesData,
        isLoading: isRolesLoading,
        error: rolesError,
        refetch: refetchRoles,
    } = useRoles();

    const departments = departmentsData?.data ?? [];
    const teams = teamsData?.data ?? [];
    const personas = personasData?.data ?? [];
    const roles = rolesData?.data ?? [];
    const tenants = tenantsData?.data ?? [];

    const getAddButtonLabel = () => {
        switch (activeTab) {
            case 'tenants':
                return 'Add Tenant';
            case 'departments':
                return 'Add Department';
            case 'teams':
                return 'Add Team';
            case 'personas':
                return 'Add Persona';
            case 'roles':
                return 'Add Role';
            default:
                return 'Add New';
        }
    };

    const renderContent = () => {
        switch (activeTab) {
            case 'tenants':
                if (isTenantsLoading) return <LoadingState message="Loading tenants..." />;
                if (tenantsError) {
                    return (
                        <ErrorState
                            message="Failed to load tenants"
                            onRetry={() => refetchTenants()}
                        />
                    );
                }
                return <TenantsList tenants={tenants} searchQuery={searchQuery} onDeactivate={handleOpenDeactivateDialog} />;

            case 'departments':
                if (isDepartmentsLoading) return <LoadingState message="Loading departments..." />;
                if (departmentsError) {
                    return (
                        <ErrorState
                            message="Failed to load departments"
                            onRetry={() => refetchDepartments()}
                        />
                    );
                }
                return <DepartmentsList departments={departments} searchQuery={searchQuery} />;

            case 'teams':
                if (isTeamsLoading) return <LoadingState message="Loading teams..." />;
                if (teamsError) {
                    return (
                        <ErrorState
                            message="Failed to load teams"
                            onRetry={() => refetchTeams()}
                        />
                    );
                }
                return <TeamsList teams={teams} searchQuery={searchQuery} />;

            case 'personas':
                if (isPersonasLoading) return <LoadingState message="Loading personas..." />;
                if (personasError) {
                    return (
                        <ErrorState
                            message="Failed to load personas"
                            onRetry={() => refetchPersonas()}
                        />
                    );
                }
                return <PersonasList personas={personas} searchQuery={searchQuery} onAssignMembers={handleOpenMemberDrawer} />;

            case 'roles':
                if (isRolesLoading) return <LoadingState message="Loading roles..." />;
                if (rolesError) {
                    return (
                        <ErrorState
                            message="Failed to load roles"
                            onRetry={() => refetchRoles()}
                        />
                    );
                }
                return <RolesList roles={roles} searchQuery={searchQuery} />;

            default:
                return null;
        }
    };

    return (
        <MainLayout>
            <div className="space-y-6">
                {/* Header */}
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Organization</h1>
                        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                            Manage departments, teams, personas, and roles
                        </p>
                    </div>
                    <button
                        onClick={() => {
                            switch (activeTab) {
                                case 'departments':
                                    setIsDepartmentDrawerOpen(true);
                                    break;
                                case 'teams':
                                    setIsTeamDrawerOpen(true);
                                    break;
                                case 'personas':
                                    setIsPersonaDrawerOpen(true);
                                    break;
                                case 'roles':
                                    setIsRoleDrawerOpen(true);
                                    break;
                            }
                        }}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
                    >
                        <Plus className="h-4 w-4" />
                        {getAddButtonLabel()}
                    </button>
                </div>

                {/* Tabs */}
                <div className="border-b border-gray-200 dark:border-slate-700">
                    <nav className="flex gap-8">
                        {tabs.map((tab) => {
                            const Icon = tab.icon;
                            return (
                                <button
                                    key={tab.id}
                                    onClick={() => setActiveTab(tab.id)}
                                    className={`flex items-center gap-2 py-3 border-b-2 text-sm font-medium transition-colors ${activeTab === tab.id
                                            ? 'border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400'
                                            : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
                                        }`}
                                >
                                    <Icon className="h-4 w-4" />
                                    {tab.label}
                                </button>
                            );
                        })}
                    </nav>
                </div>

                {/* Search */}
                <div className="relative max-w-md">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                    <input
                        type="text"
                        placeholder={`Search ${activeTab}...`}
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                </div>

                {/* Content */}
                {renderContent()}
            </div>

            {/* Department Drawer */}
            <Drawer
                isOpen={isDepartmentDrawerOpen}
                onClose={() => setIsDepartmentDrawerOpen(false)}
                title="Create Department"
                size="md"
            >
                <div className="space-y-4">
                    <FormField label="Department Name" name="name" required>
                        <Input
                            value={deptForm.name}
                            onChange={(e) => setDeptForm({ ...deptForm, name: e.target.value })}
                            placeholder="Engineering, Sales, Marketing..."
                        />
                    </FormField>

                    <FormField label="Slug" name="slug" required helpText="URL-friendly identifier">
                        <Input
                            value={deptForm.slug}
                            onChange={(e) => setDeptForm({ ...deptForm, slug: e.target.value })}
                            placeholder="engineering"
                        />
                    </FormField>

                    <FormField label="Description" name="description">
                        <Textarea
                            value={deptForm.description}
                            onChange={(e) => setDeptForm({ ...deptForm, description: e.target.value })}
                            placeholder="Brief description of the department..."
                            rows={3}
                        />
                    </FormField>

                    <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                        <button
                            onClick={() => setIsDepartmentDrawerOpen(false)}
                            className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleCreateDepartment}
                            disabled={!deptForm.name || !deptForm.slug || createDepartment.isPending}
                            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg"
                        >
                            {createDepartment.isPending ? 'Creating...' : 'Create Department'}
                        </button>
                    </div>
                </div>
            </Drawer>

            {/* Team Drawer */}
            <Drawer
                isOpen={isTeamDrawerOpen}
                onClose={() => setIsTeamDrawerOpen(false)}
                title="Create Team"
                size="md"
            >
                <div className="space-y-4">
                    <FormField label="Department" name="departmentId" required>
                        <Select
                            value={teamForm.departmentId}
                            onChange={(e) => setTeamForm({ ...teamForm, departmentId: e.target.value })}
                        >
                            <option value="">Select Department</option>
                            {departments.map((dept) => (
                                <option key={dept.id} value={dept.id}>
                                    {dept.name}
                                </option>
                            ))}
                        </Select>
                    </FormField>

                    <FormField label="Team Name" name="name" required>
                        <Input
                            value={teamForm.name}
                            onChange={(e) => setTeamForm({ ...teamForm, name: e.target.value })}
                            placeholder="Platform Team, Mobile Team..."
                        />
                    </FormField>

                    <FormField label="Slug" name="slug" required helpText="URL-friendly identifier">
                        <Input
                            value={teamForm.slug}
                            onChange={(e) => setTeamForm({ ...teamForm, slug: e.target.value })}
                            placeholder="platform-team"
                        />
                    </FormField>

                    <FormField label="Description" name="description">
                        <Textarea
                            value={teamForm.description}
                            onChange={(e) => setTeamForm({ ...teamForm, description: e.target.value })}
                            placeholder="Brief description of the team..."
                            rows={3}
                        />
                    </FormField>

                    <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                        <button
                            onClick={() => setIsTeamDrawerOpen(false)}
                            className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleCreateTeam}
                            disabled={!teamForm.name || !teamForm.slug || !teamForm.departmentId || createTeam.isPending}
                            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg"
                        >
                            {createTeam.isPending ? 'Creating...' : 'Create Team'}
                        </button>
                    </div>
                </div>
            </Drawer>

            {/* Persona Drawer */}
            <Drawer
                isOpen={isPersonaDrawerOpen}
                onClose={() => setIsPersonaDrawerOpen(false)}
                title="Create Persona"
                size="md"
            >
                <div className="space-y-4">
                    <FormField label="Persona Type" name="type" required>
                        <Select
                            value={personaForm.type}
                            onChange={(e) => setPersonaForm({ ...personaForm, type: e.target.value as 'HUMAN' | 'AGENT' })}
                        >
                            <option value="HUMAN">Human</option>
                            <option value="AGENT">AI Agent</option>
                        </Select>
                    </FormField>

                    <FormField label="Persona Name" name="name" required>
                        <Input
                            value={personaForm.name}
                            onChange={(e) => setPersonaForm({ ...personaForm, name: e.target.value })}
                            placeholder="Senior Developer, QA Lead, DevOps Agent..."
                        />
                    </FormField>

                    <FormField label="Primary Team" name="primaryTeamId" required>
                        <Select
                            value={personaForm.primaryTeamId}
                            onChange={(e) => setPersonaForm({ ...personaForm, primaryTeamId: e.target.value })}
                        >
                            <option value="">Select Team</option>
                            {teams.map((team) => (
                                <option key={team.id} value={team.id}>
                                    {team.name} ({team.department?.name ?? team.departmentName ?? team.departmentId})
                                </option>
                            ))}
                        </Select>
                    </FormField>

                    <FormField label="Description" name="description">
                        <Textarea
                            value={personaForm.description}
                            onChange={(e) => setPersonaForm({ ...personaForm, description: e.target.value })}
                            placeholder="Brief description of the persona..."
                            rows={3}
                        />
                    </FormField>

                    <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                        <button
                            onClick={() => setIsPersonaDrawerOpen(false)}
                            className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleCreatePersona}
                            disabled={!personaForm.name || !personaForm.primaryTeamId || createPersona.isPending}
                            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg"
                        >
                            {createPersona.isPending ? 'Creating...' : 'Create Persona'}
                        </button>
                    </div>
                </div>
            </Drawer>

            {/* Role Drawer */}
            <Drawer
                isOpen={isRoleDrawerOpen}
                onClose={() => setIsRoleDrawerOpen(false)}
                title="Create Role"
                size="md"
            >
                <div className="space-y-4">
                    <FormField label="Role Name" name="name" required>
                        <Input
                            value={roleForm.name}
                            onChange={(e) => setRoleForm({ ...roleForm, name: e.target.value })}
                            placeholder="Admin, Developer, Viewer..."
                        />
                    </FormField>

                    <FormField label="Scope" name="scope" required>
                        <Select
                            value={roleForm.scope}
                            onChange={(e) => setRoleForm({ ...roleForm, scope: e.target.value as 'TENANT' | 'ENVIRONMENT' | 'GLOBAL' })}
                        >
                            <option value="TENANT">Tenant</option>
                            <option value="ENVIRONMENT">Environment</option>
                            <option value="GLOBAL">Global</option>
                        </Select>
                    </FormField>

                    <FormField label="Description" name="description">
                        <Textarea
                            value={roleForm.description}
                            onChange={(e) => setRoleForm({ ...roleForm, description: e.target.value })}
                            placeholder="Brief description of the role..."
                            rows={3}
                        />
                    </FormField>

                    <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                        <button
                            onClick={() => setIsRoleDrawerOpen(false)}
                            className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleCreateRole}
                            disabled={!roleForm.name || createRole.isPending}
                            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg"
                        >
                            {createRole.isPending ? 'Creating...' : 'Create Role'}
                        </button>
                    </div>
                </div>
            </Drawer>

            {/* Member Assignment Drawer */}
            <Drawer
                isOpen={isMemberDrawerOpen}
                onClose={() => setIsMemberDrawerOpen(false)}
                title={`Assign Members to ${selectedPersona?.name || 'Persona'}`}
                size="md"
            >
                <div className="space-y-4">
                    {/* Search */}
                    <FormField label="Search" name="search">
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                            <Input
                                value={memberForm.searchQuery}
                                onChange={(e) => setMemberForm({ ...memberForm, searchQuery: e.target.value })}
                                placeholder="Search users, teams, or agents..."
                                className="pl-10"
                            />
                        </div>
                    </FormField>

                    {/* User Selection */}
                    {selectedPersona?.type === 'HUMAN' && (
                        <div>
                            <h3 className="text-sm font-medium text-gray-900 dark:text-white mb-2">Users</h3>
                            <div className="space-y-2 max-h-48 overflow-y-auto border border-gray-200 dark:border-slate-700 rounded-lg p-3">
                                {['user1@example.com', 'user2@example.com', 'user3@example.com'].map(userId => (
                                    <label key={userId} className="flex items-center gap-2 cursor-pointer hover:bg-gray-50 dark:hover:bg-slate-700 p-2 rounded">
                                        <input
                                            type="checkbox"
                                            checked={memberForm.userIds.includes(userId)}
                                            onChange={() => handleToggleUserId(userId)}
                                            className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                                        />
                                        <span className="text-sm text-gray-700 dark:text-gray-300">{userId}</span>
                                    </label>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Team Selection */}
                    <div>
                        <h3 className="text-sm font-medium text-gray-900 dark:text-white mb-2">Teams</h3>
                        <div className="space-y-2 max-h-48 overflow-y-auto border border-gray-200 dark:border-slate-700 rounded-lg p-3">
                            {teams.map(team => (
                                <label key={team.id} className="flex items-center gap-2 cursor-pointer hover:bg-gray-50 dark:hover:bg-slate-700 p-2 rounded">
                                    <input
                                        type="checkbox"
                                        checked={memberForm.teamIds.includes(team.id)}
                                        onChange={() => handleToggleTeamId(team.id)}
                                        className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                                    />
                                    <span className="text-sm text-gray-700 dark:text-gray-300">{team.name}</span>
                                </label>
                            ))}
                        </div>
                    </div>

                    {/* Selected Count */}
                    <div className="bg-gray-50 dark:bg-slate-700/50 p-3 rounded-lg">
                        <p className="text-sm text-gray-600 dark:text-gray-400">
                            Selected: <span className="font-medium text-gray-900 dark:text-white">
                                {memberForm.userIds.length + memberForm.teamIds.length + memberForm.agentIds.length}
                            </span> members
                        </p>
                    </div>

                    <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                        <button
                            onClick={() => setIsMemberDrawerOpen(false)}
                            className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleAssignMembers}
                            disabled={updatePersonaMembers.isPending}
                            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg"
                        >
                            {updatePersonaMembers.isPending ? 'Assigning...' : 'Assign Members'}
                        </button>
                    </div>
                </div>
            </Drawer>

            {/* Deactivation Confirmation Dialog */}
            <ConfirmDialog
                isOpen={isDeactivateDialogOpen}
                onClose={() => {
                    setIsDeactivateDialogOpen(false);
                    setTenantToDeactivate(null);
                }}
                onConfirm={handleDeactivateTenant}
                title="Deactivate Tenant"
                message={
                    <div className="space-y-3">
                        <p>Are you sure you want to deactivate <strong>{tenantToDeactivate?.name}</strong>?</p>

                        {isCheckingDeactivation && (
                            <div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
                                <Loader2 className="h-4 w-4 animate-spin" />
                                Checking dependencies...
                            </div>
                        )}

                        {deactivationCheck && (
                            <div className="space-y-2">
                                {deactivationCheck.warnings && deactivationCheck.warnings.length > 0 && (
                                    <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-3">
                                        <div className="flex items-start gap-2">
                                            <AlertTriangle className="h-5 w-5 text-yellow-600 dark:text-yellow-400 flex-shrink-0 mt-0.5" />
                                            <div className="space-y-1">
                                                <p className="text-sm font-medium text-yellow-800 dark:text-yellow-200">Warnings:</p>
                                                {deactivationCheck.warnings.map((warning, i) => (
                                                    <p key={i} className="text-sm text-yellow-700 dark:text-yellow-300">{warning}</p>
                                                ))}
                                            </div>
                                        </div>
                                    </div>
                                )}

                                {deactivationCheck.blockers && deactivationCheck.blockers.length > 0 && (
                                    <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-3">
                                        <div className="flex items-start gap-2">
                                            <AlertCircle className="h-5 w-5 text-red-600 dark:text-red-400 flex-shrink-0 mt-0.5" />
                                            <div className="space-y-1">
                                                <p className="text-sm font-medium text-red-800 dark:text-red-200">Blockers:</p>
                                                {deactivationCheck.blockers.map((blocker, i) => (
                                                    <p key={i} className="text-sm text-red-700 dark:text-red-300">{blocker}</p>
                                                ))}
                                            </div>
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                }
                confirmText="Deactivate"
                confirmVariant="danger"
                isLoading={deactivateTenantMutation.isPending}
                disabled={deactivationCheck?.blocked || isCheckingDeactivation}
            />
        </MainLayout>
    );
}
