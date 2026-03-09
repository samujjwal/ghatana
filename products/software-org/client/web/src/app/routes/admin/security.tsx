/**
 * Security - Access Control, Policies, and Audit
 *
 * Manage security settings, access control, policies, and audit logs.
 * Connected to real Admin APIs via useAdminApi hooks.
 *
 * @doc.type route
 * @doc.section ADMIN
 */

import { useState } from 'react';
import { MainLayout } from '@/app/Layout';
import {
    Shield,
    Key,
    FileText,
    Clock,
    User,
    Plus,
    Search,
    Play,
    Loader2,
    AlertCircle,
    Download,
    Filter,
    CheckCircle,
    XCircle,
    AlertTriangle,
} from 'lucide-react';
import {
    useRoles,
    useUpdateRole,
    useCreateRoleAssignment,
    usePolicies,
    useCreatePolicy,
    useSimulatePolicy,
    useAuditLog,
    useExportAuditLog,
    type RoleResponse,
    type PolicyResponse,
    type AuditEventResponse,
} from '@/hooks';
import { Drawer, FormField, Input, Checkbox } from '@/components/admin';

type TabType = 'access' | 'audit' | 'policies';

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

interface RolesTabProps {
    roles: RoleResponse[];
    isLoading: boolean;
    error: Error | null;
    onRetry: () => void;
    onEditRole: (role: RoleResponse) => void;
    onAssignRole: (role: RoleResponse) => void;
}

function RolesTab({ roles, isLoading, error, onRetry, onEditRole, onAssignRole }: RolesTabProps) {
    const [searchQuery, setSearchQuery] = useState('');

    if (isLoading) return <LoadingState message="Loading roles..." />;
    if (error) return <ErrorState message="Failed to load roles" onRetry={onRetry} />;

    const filteredRoles = roles.filter(
        (role) =>
            role.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
            (role.description?.toLowerCase().includes(searchQuery.toLowerCase()) ?? false)
    );

    return (
        <div className="space-y-4">
            {/* Search */}
            <div className="relative max-w-md">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                <input
                    type="text"
                    placeholder="Search roles..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
            </div>

            {/* Roles Table */}
            <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 overflow-hidden">
                <table className="min-w-full divide-y divide-gray-200 dark:divide-slate-700">
                    <thead className="bg-gray-50 dark:bg-slate-900">
                        <tr>
                            <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase">
                                Role
                            </th>
                            <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase">
                                Description
                            </th>
                            <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase">
                                Permissions
                            </th>
                            <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase">
                                Users
                            </th>
                            <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase">
                                Actions
                            </th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200 dark:divide-slate-700">
                        {filteredRoles.map((role) => (
                            <tr key={role.id} className="hover:bg-gray-50 dark:hover:bg-slate-700/50">
                                <td className="px-6 py-4">
                                    <div className="flex items-center gap-3">
                                        <div className="p-2 bg-amber-100 dark:bg-amber-900/30 rounded-lg">
                                            <Shield className="h-4 w-4 text-amber-600 dark:text-amber-400" />
                                        </div>
                                        <span className="font-medium text-gray-900 dark:text-white">
                                            {role.name}
                                        </span>
                                    </div>
                                </td>
                                <td className="px-6 py-4 text-sm text-gray-600 dark:text-gray-400">
                                    {role.description || '-'}
                                </td>
                                <td className="px-6 py-4">
                                    <span className="px-2 py-1 text-xs font-medium bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300 rounded">
                                        {role.permissions.length} permissions
                                    </span>
                                </td>
                                <td className="px-6 py-4 text-sm text-gray-900 dark:text-white">
                                    {role.userCount ?? 0}
                                </td>
                                <td className="px-6 py-4">
                                    <div className="flex items-center gap-3">
                                        <button
                                            onClick={() => onEditRole(role)}
                                            className="text-blue-600 hover:text-blue-700 dark:text-blue-400 text-sm font-medium"
                                        >
                                            Edit
                                        </button>
                                        <button
                                            onClick={() => onAssignRole(role)}
                                            className="text-green-600 hover:text-green-700 dark:text-green-400 text-sm font-medium"
                                        >
                                            Assign
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

interface PoliciesTabProps {
    policies: PolicyResponse[];
    isLoading: boolean;
    error: Error | null;
    onRetry: () => void;
    onCreatePolicy: () => void;
}

function PoliciesTab({ policies, isLoading, error, onRetry, onCreatePolicy }: PoliciesTabProps) {
    const [searchQuery, setSearchQuery] = useState('');

    if (isLoading) return <LoadingState message="Loading policies..." />;
    if (error) return <ErrorState message="Failed to load policies" onRetry={onRetry} />;

    const filteredPolicies = policies.filter(
        (policy) =>
            policy.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
            (policy.description?.toLowerCase().includes(searchQuery.toLowerCase()) ?? false)
    );

    const getStatusBadge = (status: string) => {
        switch (status) {
            case 'active':
                return (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300">
                        <CheckCircle className="h-3 w-3" /> Active
                    </span>
                );
            case 'inactive':
                return (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300">
                        <XCircle className="h-3 w-3" /> Inactive
                    </span>
                );
            case 'draft':
                return (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300">
                        <AlertTriangle className="h-3 w-3" /> Draft
                    </span>
                );
            default:
                return (
                    <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800">
                        {status}
                    </span>
                );
        }
    };

    return (
        <div className="space-y-4">
            {/* Search */}
            <div className="relative max-w-md">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                <input
                    type="text"
                    placeholder="Search policies..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
            </div>

            {/* Policies List */}
            <div className="space-y-3">
                {filteredPolicies.map((policy) => (
                    <div
                        key={policy.id}
                        className="p-4 bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700"
                    >
                        <div className="flex items-start justify-between">
                            <div className="flex items-start gap-3">
                                <div className="p-2 bg-purple-100 dark:bg-purple-900/30 rounded-lg">
                                    <Shield className="h-5 w-5 text-purple-600 dark:text-purple-400" />
                                </div>
                                <div>
                                    <div className="flex items-center gap-2">
                                        <h3 className="font-semibold text-gray-900 dark:text-white">
                                            {policy.name}
                                        </h3>
                                        {getStatusBadge(policy.status)}
                                    </div>
                                    <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                                        {policy.description || `Category: ${policy.category}`}
                                    </p>
                                    <div className="flex items-center gap-4 mt-2 text-xs text-gray-400">
                                        <span>
                                            Environments: {policy.environments.join(', ') || 'All'}
                                        </span>
                                        <span>•</span>
                                        <span>
                                            Services: {policy.serviceIds.length || 'All'}
                                        </span>
                                    </div>
                                </div>
                            </div>
                            <div className="flex items-center gap-2">
                                <button className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-slate-700 rounded-lg hover:bg-gray-200 dark:hover:bg-slate-600">
                                    <Play className="h-3 w-3" />
                                    Simulate
                                </button>
                                <button className="px-3 py-1.5 text-sm font-medium text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg">
                                    Edit
                                </button>
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {filteredPolicies.length === 0 && (
                <div className="text-center py-12 bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700">
                    <Shield className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                    <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                        No policies found
                    </h3>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
                        Create your first security policy to enforce guardrails.
                    </p>
                    <button
                        onClick={onCreatePolicy}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"
                    >
                        <Plus className="h-4 w-4" />
                        New Policy
                    </button>
                </div>
            )}
        </div>
    );
}

interface AuditLogTabProps {
    auditEvents: AuditEventResponse[];
    isLoading: boolean;
    error: Error | null;
    onRetry: () => void;
    onExport: (format: 'json' | 'csv') => void;
    isExporting: boolean;
    onOpenFilters: () => void;
}

function AuditLogTab({
    auditEvents,
    isLoading,
    error,
    onRetry,
    onExport,
    isExporting,
    onOpenFilters,
}: AuditLogTabProps) {
    const [searchQuery, setSearchQuery] = useState('');

    if (isLoading) return <LoadingState message="Loading audit logs..." />;
    if (error) return <ErrorState message="Failed to load audit logs" onRetry={onRetry} />;

    const filteredEvents = auditEvents.filter(
        (event) =>
            event.action.toLowerCase().includes(searchQuery.toLowerCase()) ||
            event.entityType.toLowerCase().includes(searchQuery.toLowerCase()) ||
            (event.actorName?.toLowerCase().includes(searchQuery.toLowerCase()) ?? false)
    );

    const getStatusBadge = (action: string) => {
        if (action.includes('create') || action.includes('add')) {
            return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300';
        }
        if (action.includes('delete') || action.includes('remove')) {
            return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300';
        }
        if (action.includes('update') || action.includes('modify')) {
            return 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300';
        }
        return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300';
    };

    const formatTimestamp = (timestamp: string) => {
        const date = new Date(timestamp);
        const now = new Date();
        const diff = now.getTime() - date.getTime();

        if (diff < 60000) return 'Just now';
        if (diff < 3600000) return `${Math.floor(diff / 60000)} min ago`;
        if (diff < 86400000) return `${Math.floor(diff / 3600000)} hours ago`;
        return date.toLocaleDateString();
    };

    return (
        <div className="space-y-4">
            {/* Toolbar */}
            <div className="flex items-center justify-between gap-4">
                <div className="relative flex-1 max-w-md">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                    <input
                        type="text"
                        placeholder="Search audit logs..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                </div>
                <div className="flex items-center gap-2">
                    <button
                        onClick={onOpenFilters}
                        className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-slate-800 border border-gray-300 dark:border-slate-600 rounded-lg hover:bg-gray-50 dark:hover:bg-slate-700"
                    >
                        <Filter className="h-4 w-4" />
                        Filters
                    </button>
                    <button
                        onClick={() => onExport('json')}
                        disabled={isExporting}
                        className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-slate-800 border border-gray-300 dark:border-slate-600 rounded-lg hover:bg-gray-50 dark:hover:bg-slate-700 disabled:opacity-50"
                    >
                        {isExporting ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                            <Download className="h-4 w-4" />
                        )}
                        Export
                    </button>
                </div>
            </div>

            {/* Audit Log Table */}
            <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 overflow-hidden">
                <table className="min-w-full divide-y divide-gray-200 dark:divide-slate-700">
                    <thead className="bg-gray-50 dark:bg-slate-900">
                        <tr>
                            <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase">
                                Action
                            </th>
                            <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase">
                                Entity
                            </th>
                            <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase">
                                Actor
                            </th>
                            <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase">
                                Time
                            </th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200 dark:divide-slate-700">
                        {filteredEvents.map((event) => (
                            <tr
                                key={event.id}
                                className="hover:bg-gray-50 dark:hover:bg-slate-700/50 cursor-pointer"
                            >
                                <td className="px-6 py-4">
                                    <span
                                        className={`px-2 py-0.5 rounded-full text-xs font-medium ${getStatusBadge(
                                            event.action
                                        )}`}
                                    >
                                        {event.action}
                                    </span>
                                </td>
                                <td className="px-6 py-4 text-sm text-gray-900 dark:text-white">
                                    <span className="font-medium">{event.entityType}</span>
                                    <span className="text-gray-500 dark:text-gray-400 ml-1">
                                        ({event.entityId.slice(0, 8)}...)
                                    </span>
                                </td>
                                <td className="px-6 py-4 text-sm text-gray-600 dark:text-gray-400">
                                    <div className="flex items-center gap-2">
                                        <User className="h-4 w-4" />
                                        {event.actorName || event.actorUserId || 'System'}
                                    </div>
                                </td>
                                <td className="px-6 py-4 text-sm text-gray-600 dark:text-gray-400">
                                    <div className="flex items-center gap-1">
                                        <Clock className="h-4 w-4" />
                                        {formatTimestamp(event.timestamp)}
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {filteredEvents.length === 0 && (
                <div className="text-center py-12">
                    <FileText className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                    <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                        No audit events found
                    </h3>
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                        Audit events will appear here as changes are made.
                    </p>
                </div>
            )}
        </div>
    );
}

export default function SecurityPage() {
    const [activeTab, setActiveTab] = useState<TabType>('access');

    // Drawer state
    const [isRoleEditDrawerOpen, setIsRoleEditDrawerOpen] = useState(false);
    const [isRoleAssignDrawerOpen, setIsRoleAssignDrawerOpen] = useState(false);
    const [isAuditFiltersDrawerOpen, setIsAuditFiltersDrawerOpen] = useState(false);
    const [isPolicyWizardOpen, setIsPolicyWizardOpen] = useState(false);
    const [policyWizardStep, setPolicyWizardStep] = useState(1);
    const [selectedRole, setSelectedRole] = useState<RoleResponse | null>(null);
    const [simulationResult, setSimulationResult] = useState<Record<string, unknown> | null>(null);

    // Role form state
    const [roleForm, setRoleForm] = useState({
        name: '',
        description: '',
        permissions: [] as string[],
    });

    // Role assignment form state
    const [assignmentForm, setAssignmentForm] = useState({
        userId: '',
        tenantId: '',
    });

    // Policy form state
    const [policyForm, setPolicyForm] = useState({
        name: '',
        description: '',
        category: 'SECURITY' as string,
        scope: 'TENANT' as string,
        tenantId: '',
        environments: [] as string[],
        serviceIds: [] as string[],
        rules: {} as Record<string, unknown>,
    });

    // Audit filter state
    const [auditFilters, setAuditFilters] = useState({
        entityType: '',
        actorId: '',
        startDate: '',
        endDate: '',
    });

    // All available permissions for the matrix
    const availablePermissions = [
        'tenants.read', 'tenants.write', 'tenants.delete',
        'departments.read', 'departments.write', 'departments.delete',
        'teams.read', 'teams.write', 'teams.delete',
        'services.read', 'services.write', 'services.delete',
        'roles.read', 'roles.write', 'roles.delete',
        'personas.read', 'personas.write', 'personas.delete',
        'policies.read', 'policies.write', 'policies.delete',
        'integrations.read', 'integrations.write', 'integrations.delete',
        'audit.read', 'audit.export',
    ];

    // Fetch data using Admin API hooks
    const {
        data: rolesData,
        isLoading: isRolesLoading,
        error: rolesError,
        refetch: refetchRoles,
    } = useRoles();

    const {
        data: policiesData,
        isLoading: isPoliciesLoading,
        error: policiesError,
        refetch: refetchPolicies,
    } = usePolicies();

    const {
        data: auditData,
        isLoading: isAuditLoading,
        error: auditError,
        refetch: refetchAudit,
    } = useAuditLog({ limit: 100 });

    const exportMutation = useExportAuditLog();
    const updateRoleMutation = useUpdateRole();
    const createRoleAssignmentMutation = useCreateRoleAssignment();
    const createPolicyMutation = useCreatePolicy();
    const simulatePolicyMutation = useSimulatePolicy();

    const roles = rolesData?.data ?? [];
    const policies = policiesData?.data ?? [];
    const auditEvents = auditData?.data ?? [];

    const handleOpenPolicyWizard = () => {
        setPolicyForm({
            name: '',
            description: '',
            category: 'SECURITY',
            scope: 'TENANT',
            tenantId: '',
            environments: [],
            serviceIds: [],
            rules: {},
        });
        setPolicyWizardStep(1);
        setSimulationResult(null);
        setIsPolicyWizardOpen(true);
    };

    const handleNextStep = () => {
        setPolicyWizardStep(prev => Math.min(prev + 1, 4));
    };

    const handlePrevStep = () => {
        setPolicyWizardStep(prev => Math.max(prev - 1, 1));
    };

    const handleSimulatePolicy = async () => {
        try {
            const result = await simulatePolicyMutation.mutateAsync({
                policy: {
                    name: policyForm.name || 'Draft Policy',
                    description: policyForm.description,
                    tenantId: policyForm.tenantId || '',
                    category: policyForm.category,
                    environments: policyForm.environments,
                    serviceIds: policyForm.serviceIds,
                    rules: policyForm.rules,
                },
                event: { type: 'policy.simulate', serviceId: 'test-service' },
            });
            setSimulationResult(result as unknown as Record<string, unknown>);
        } catch (error) {
            console.error('Failed to simulate policy:', error);
        }
    };

    const handleCreatePolicy = async () => {
        try {
            await createPolicyMutation.mutateAsync({
                name: policyForm.name,
                description: policyForm.description,
                category: policyForm.category,
                tenantId: policyForm.tenantId || '',
                environments: policyForm.environments,
                serviceIds: policyForm.serviceIds,
                rules: policyForm.rules,
            });
            setIsPolicyWizardOpen(false);
            refetchPolicies();
        } catch (error) {
            console.error('Failed to create policy:', error);
        }
    };

    const toggleEnvironment = (env: string) => {
        setPolicyForm(prev => ({
            ...prev,
            environments: prev.environments.includes(env)
                ? prev.environments.filter(e => e !== env)
                : [...prev.environments, env]
        }));
    };

    const handleOpenRoleEdit = (role: RoleResponse) => {
        setSelectedRole(role);
        setRoleForm({
            name: role.name,
            description: role.description || '',
            permissions: role.permissions,
        });
        setIsRoleEditDrawerOpen(true);
    };

    const handleOpenRoleAssign = (role: RoleResponse) => {
        setSelectedRole(role);
        setAssignmentForm({
            userId: '',
            tenantId: '',
        });
        setIsRoleAssignDrawerOpen(true);
    };

    const handleSaveRole = async () => {
        if (!selectedRole) return;
        try {
            await updateRoleMutation.mutateAsync({
                id: selectedRole.id,
                name: roleForm.name,
                description: roleForm.description,
                permissions: roleForm.permissions,
            });
            setIsRoleEditDrawerOpen(false);
            refetchRoles();
        } catch (error) {
            console.error('Failed to update role:', error);
        }
    };

    const handleAssignRole = async () => {
        if (!selectedRole) return;
        try {
            await createRoleAssignmentMutation.mutateAsync({
                roleId: selectedRole.id,
                userId: assignmentForm.userId,
                tenantId: assignmentForm.tenantId,
            });
            setIsRoleAssignDrawerOpen(false);
            refetchRoles();
        } catch (error) {
            console.error('Failed to assign role:', error);
        }
    };

    const togglePermission = (permission: string) => {
        setRoleForm(prev => ({
            ...prev,
            permissions: prev.permissions.includes(permission)
                ? prev.permissions.filter(p => p !== permission)
                : [...prev.permissions, permission]
        }));
    };

    const handleExport = (format: 'json' | 'csv') => {
        exportMutation.mutate(
            { format },
            {
                onSuccess: (blob) => {
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = `audit-log.${format}`;
                    a.click();
                    URL.revokeObjectURL(url);
                },
            }
        );
    };

    const getAddButtonLabel = () => {
        switch (activeTab) {
            case 'access':
                return 'New Role';
            case 'policies':
                return 'New Policy';
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
                        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Security</h1>
                        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                            Access control, policies, and audit logs
                        </p>
                    </div>
                    {getAddButtonLabel() && (
                        <button
                            onClick={() => {
                                if (activeTab === 'policies') {
                                    handleOpenPolicyWizard();
                                }
                            }}
                            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
                        >
                            <Plus className="h-4 w-4" />
                            {getAddButtonLabel()}
                        </button>
                    )}
                </div>

                {/* Tabs */}
                <div className="border-b border-gray-200 dark:border-slate-700">
                    <nav className="flex gap-8">
                        <button
                            onClick={() => setActiveTab('access')}
                            className={`flex items-center gap-2 py-3 border-b-2 text-sm font-medium transition-colors ${activeTab === 'access'
                                    ? 'border-blue-600 text-blue-600'
                                    : 'border-transparent text-gray-500 hover:text-gray-700'
                                }`}
                        >
                            <Key className="h-4 w-4" />
                            Access Control
                        </button>
                        <button
                            onClick={() => setActiveTab('policies')}
                            className={`flex items-center gap-2 py-3 border-b-2 text-sm font-medium transition-colors ${activeTab === 'policies'
                                    ? 'border-blue-600 text-blue-600'
                                    : 'border-transparent text-gray-500 hover:text-gray-700'
                                }`}
                        >
                            <Shield className="h-4 w-4" />
                            Policies
                        </button>
                        <button
                            onClick={() => setActiveTab('audit')}
                            className={`flex items-center gap-2 py-3 border-b-2 text-sm font-medium transition-colors ${activeTab === 'audit'
                                    ? 'border-blue-600 text-blue-600'
                                    : 'border-transparent text-gray-500 hover:text-gray-700'
                                }`}
                        >
                            <FileText className="h-4 w-4" />
                            Audit Logs
                        </button>
                    </nav>
                </div>

                {/* Content */}
                {activeTab === 'access' && (
                    <RolesTab
                        roles={roles}
                        isLoading={isRolesLoading}
                        error={rolesError as Error | null}
                        onRetry={() => refetchRoles()}
                        onEditRole={handleOpenRoleEdit}
                        onAssignRole={handleOpenRoleAssign}
                    />
                )}

                {activeTab === 'policies' && (
                    <PoliciesTab
                        policies={policies}
                        isLoading={isPoliciesLoading}
                        error={policiesError as Error | null}
                        onRetry={() => refetchPolicies()}
                        onCreatePolicy={handleOpenPolicyWizard}
                    />
                )}

                {activeTab === 'audit' && (
                    <AuditLogTab
                        auditEvents={auditEvents}
                        isLoading={isAuditLoading}
                        error={auditError as Error | null}
                        onRetry={() => refetchAudit()}
                        onExport={handleExport}
                        isExporting={exportMutation.isPending}
                        onOpenFilters={() => setIsAuditFiltersDrawerOpen(true)}
                    />
                )}
            </div>

            {/* Role Edit Drawer - Permission Matrix */}
            <Drawer
                isOpen={isRoleEditDrawerOpen}
                onClose={() => setIsRoleEditDrawerOpen(false)}
                title="Edit Role Permissions"
                size="lg"
            >
                <div className="space-y-4">
                    <FormField label="Role Name" name="name" required>
                        <Input
                            value={roleForm.name}
                            onChange={(e) => setRoleForm({ ...roleForm, name: e.target.value })}
                            placeholder="Admin, Developer, Viewer..."
                        />
                    </FormField>

                    <FormField label="Description" name="description">
                        <Input
                            value={roleForm.description}
                            onChange={(e) => setRoleForm({ ...roleForm, description: e.target.value })}
                            placeholder="Brief description..."
                        />
                    </FormField>

                    <FormField label="Permissions" name="permissions">
                        <div className="space-y-4 p-4 border border-gray-200 dark:border-slate-600 rounded-lg max-h-96 overflow-y-auto">
                            {/* Group permissions by resource */}
                            {['tenants', 'departments', 'teams', 'services', 'roles', 'personas', 'policies', 'integrations', 'audit'].map(resource => (
                                <div key={resource} className="space-y-2">
                                    <h4 className="text-sm font-semibold text-gray-700 dark:text-gray-300 capitalize">
                                        {resource}
                                    </h4>
                                    <div className="grid grid-cols-3 gap-2 ml-4">
                                        {availablePermissions
                                            .filter(p => p.startsWith(resource))
                                            .map(permission => (
                                                <Checkbox
                                                    key={permission}
                                                    id={permission}
                                                    checked={roleForm.permissions.includes(permission)}
                                                    onChange={() => togglePermission(permission)}
                                                    label={permission.split('.')[1]}
                                                />
                                            ))}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </FormField>

                    <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                        <button
                            onClick={() => setIsRoleEditDrawerOpen(false)}
                            className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleSaveRole}
                            disabled={!roleForm.name || updateRoleMutation.isPending}
                            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg"
                        >
                            {updateRoleMutation.isPending ? 'Saving...' : 'Save Changes'}
                        </button>
                    </div>
                </div>
            </Drawer>

            {/* Role Assignment Drawer */}
            <Drawer
                isOpen={isRoleAssignDrawerOpen}
                onClose={() => setIsRoleAssignDrawerOpen(false)}
                title={`Assign Role: ${selectedRole?.name || ''}`}
                size="md"
            >
                <div className="space-y-4">
                    <FormField label="User ID" name="userId" required helpText="Enter the user ID to assign this role">
                        <Input
                            value={assignmentForm.userId}
                            onChange={(e) => setAssignmentForm({ ...assignmentForm, userId: e.target.value })}
                            placeholder="user-123"
                        />
                    </FormField>

                    <FormField label="Tenant ID" name="tenantId" helpText="Optional: Scope assignment to specific tenant">
                        <Input
                            value={assignmentForm.tenantId}
                            onChange={(e) => setAssignmentForm({ ...assignmentForm, tenantId: e.target.value })}
                            placeholder="tenant-456"
                        />
                    </FormField>

                    <div className="p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
                        <p className="text-sm text-blue-800 dark:text-blue-300">
                            <strong>Note:</strong> This will grant the user all permissions associated with the "{selectedRole?.name}" role
                            {assignmentForm.tenantId ? ' within the specified tenant' : ' globally'}.
                        </p>
                    </div>

                    <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                        <button
                            onClick={() => setIsRoleAssignDrawerOpen(false)}
                            className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleAssignRole}
                            disabled={!assignmentForm.userId || createRoleAssignmentMutation.isPending}
                            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg"
                        >
                            {createRoleAssignmentMutation.isPending ? 'Assigning...' : 'Assign Role'}
                        </button>
                    </div>
                </div>
            </Drawer>

            {/* Audit Filters Drawer */}
            <Drawer
                isOpen={isAuditFiltersDrawerOpen}
                onClose={() => setIsAuditFiltersDrawerOpen(false)}
                title="Audit Log Filters"
                size="md"
            >
                <div className="space-y-4">
                    <FormField label="Entity Type" name="entityType">
                        <select
                            value={auditFilters.entityType}
                            onChange={(e) => setAuditFilters({ ...auditFilters, entityType: e.target.value })}
                            className="w-full px-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100"
                        >
                            <option value="">All Types</option>
                            <option value="tenant">Tenant</option>
                            <option value="department">Department</option>
                            <option value="team">Team</option>
                            <option value="service">Service</option>
                            <option value="role">Role</option>
                            <option value="persona">Persona</option>
                            <option value="policy">Policy</option>
                            <option value="integration">Integration</option>
                        </select>
                    </FormField>

                    <FormField label="Actor ID" name="actorId" helpText="Filter by user who performed the action">
                        <Input
                            value={auditFilters.actorId}
                            onChange={(e) => setAuditFilters({ ...auditFilters, actorId: e.target.value })}
                            placeholder="user-123"
                        />
                    </FormField>

                    <FormField label="Start Date" name="startDate">
                        <Input
                            type="date"
                            value={auditFilters.startDate}
                            onChange={(e) => setAuditFilters({ ...auditFilters, startDate: e.target.value })}
                        />
                    </FormField>

                    <FormField label="End Date" name="endDate">
                        <Input
                            type="date"
                            value={auditFilters.endDate}
                            onChange={(e) => setAuditFilters({ ...auditFilters, endDate: e.target.value })}
                        />
                    </FormField>

                    <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                        <button
                            onClick={() => {
                                setAuditFilters({ entityType: '', actorId: '', startDate: '', endDate: '' });
                            }}
                            className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                        >
                            Clear Filters
                        </button>
                        <button
                            onClick={() => {
                                setIsAuditFiltersDrawerOpen(false);
                                refetchAudit();
                            }}
                            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg"
                        >
                            Apply Filters
                        </button>
                    </div>
                </div>
            </Drawer>

            {/* Policy Wizard Drawer */}
            <Drawer
                isOpen={isPolicyWizardOpen}
                onClose={() => setIsPolicyWizardOpen(false)}
                title={`Create Policy - Step ${policyWizardStep} of 4`}
                size="lg"
            >
                <div className="space-y-6">
                    {/* Progress Indicator */}
                    <div className="flex items-center justify-between">
                        {[1, 2, 3, 4].map((step) => (
                            <div key={step} className="flex items-center">
                                <div
                                    className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium ${step === policyWizardStep
                                            ? 'bg-blue-600 text-white'
                                            : step < policyWizardStep
                                                ? 'bg-green-600 text-white'
                                                : 'bg-gray-200 dark:bg-slate-700 text-gray-600 dark:text-gray-400'
                                        }`}
                                >
                                    {step < policyWizardStep ? '✓' : step}
                                </div>
                                {step < 4 && (
                                    <div
                                        className={`w-16 h-1 ${step < policyWizardStep
                                                ? 'bg-green-600'
                                                : 'bg-gray-200 dark:bg-slate-700'
                                            }`}
                                    />
                                )}
                            </div>
                        ))}
                    </div>

                    {/* Step 1: Basic Info */}
                    {policyWizardStep === 1 && (
                        <div className="space-y-4">
                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Basic Information</h3>

                            <FormField label="Policy Name" name="name" required>
                                <Input
                                    value={policyForm.name}
                                    onChange={(e) => setPolicyForm({ ...policyForm, name: e.target.value })}
                                    placeholder="Production Security Policy"
                                />
                            </FormField>

                            <FormField label="Description" name="description">
                                <Input
                                    value={policyForm.description}
                                    onChange={(e) => setPolicyForm({ ...policyForm, description: e.target.value })}
                                    placeholder="Policy description..."
                                />
                            </FormField>

                            <FormField label="Category" name="category" required>
                                <select
                                    value={policyForm.category}
                                    onChange={(e) => setPolicyForm({ ...policyForm, category: e.target.value })}
                                    className="w-full px-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100"
                                >
                                    <option value="SECURITY">Security</option>
                                    <option value="COMPLIANCE">Compliance</option>
                                    <option value="OPERATIONAL">Operational</option>
                                    <option value="CUSTOM">Custom</option>
                                </select>
                            </FormField>
                        </div>
                    )}

                    {/* Step 2: Scope */}
                    {policyWizardStep === 2 && (
                        <div className="space-y-4">
                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Policy Scope</h3>

                            <FormField label="Scope Level" name="scope" required>
                                <select
                                    value={policyForm.scope}
                                    onChange={(e) => setPolicyForm({ ...policyForm, scope: e.target.value })}
                                    className="w-full px-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100"
                                >
                                    <option value="GLOBAL">Global (All Tenants)</option>
                                    <option value="TENANT">Tenant-Specific</option>
                                    <option value="ENVIRONMENT">Environment-Specific</option>
                                    <option value="SERVICE">Service-Specific</option>
                                </select>
                            </FormField>

                            {policyForm.scope === 'TENANT' && (
                                <FormField label="Tenant ID" name="tenantId" required>
                                    <Input
                                        value={policyForm.tenantId}
                                        onChange={(e) => setPolicyForm({ ...policyForm, tenantId: e.target.value })}
                                        placeholder="tenant-123"
                                    />
                                </FormField>
                            )}

                            <FormField label="Environments" name="environments" helpText="Select applicable environments">
                                <div className="space-y-2 p-4 border border-gray-200 dark:border-slate-600 rounded-lg">
                                    {['dev', 'staging', 'production'].map((env) => (
                                        <Checkbox
                                            key={env}
                                            id={env}
                                            checked={policyForm.environments.includes(env)}
                                            onChange={() => toggleEnvironment(env)}
                                            label={env.charAt(0).toUpperCase() + env.slice(1)}
                                        />
                                    ))}
                                </div>
                            </FormField>
                        </div>
                    )}

                    {/* Step 3: Rules */}
                    {policyWizardStep === 3 && (
                        <div className="space-y-4">
                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Policy Rules</h3>

                            <div className="p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
                                <p className="text-sm text-blue-800 dark:text-blue-300">
                                    Define the conditions and actions for this policy. Rules are evaluated in order.
                                </p>
                            </div>

                            <FormField label="Max CPU Usage (%)" name="maxCpu" helpText="Maximum CPU usage threshold">
                                <Input
                                    type="number"
                                    value={(policyForm.rules as Record<string, number>).maxCpu || ''}
                                    onChange={(e) => setPolicyForm({
                                        ...policyForm,
                                        rules: { ...policyForm.rules, maxCpu: parseInt(e.target.value) || 0 }
                                    })}
                                    placeholder="80"
                                />
                            </FormField>

                            <FormField label="Max Memory Usage (%)" name="maxMemory" helpText="Maximum memory usage threshold">
                                <Input
                                    type="number"
                                    value={(policyForm.rules as Record<string, number>).maxMemory || ''}
                                    onChange={(e) => setPolicyForm({
                                        ...policyForm,
                                        rules: { ...policyForm.rules, maxMemory: parseInt(e.target.value) || 0 }
                                    })}
                                    placeholder="80"
                                />
                            </FormField>

                            <FormField label="Require Approval" name="requireApproval">
                                <Checkbox
                                    id="requireApproval"
                                    checked={(policyForm.rules as Record<string, boolean>).requireApproval || false}
                                    onChange={(e) => setPolicyForm({
                                        ...policyForm,
                                        rules: { ...policyForm.rules, requireApproval: e.target.checked }
                                    })}
                                    label="Require human approval for actions"
                                />
                            </FormField>
                        </div>
                    )}

                    {/* Step 4: Review & Simulate */}
                    {policyWizardStep === 4 && (
                        <div className="space-y-4">
                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Review & Test</h3>

                            <div className="p-4 bg-gray-50 dark:bg-slate-900 rounded-lg space-y-3">
                                <div>
                                    <span className="text-sm font-medium text-gray-600 dark:text-gray-400">Name:</span>
                                    <p className="text-gray-900 dark:text-white">{policyForm.name}</p>
                                </div>
                                <div>
                                    <span className="text-sm font-medium text-gray-600 dark:text-gray-400">Category:</span>
                                    <p className="text-gray-900 dark:text-white">{policyForm.category}</p>
                                </div>
                                <div>
                                    <span className="text-sm font-medium text-gray-600 dark:text-gray-400">Scope:</span>
                                    <p className="text-gray-900 dark:text-white">{policyForm.scope}</p>
                                </div>
                                <div>
                                    <span className="text-sm font-medium text-gray-600 dark:text-gray-400">Environments:</span>
                                    <p className="text-gray-900 dark:text-white">
                                        {policyForm.environments.length > 0 ? policyForm.environments.join(', ') : 'All'}
                                    </p>
                                </div>
                            </div>

                            <button
                                onClick={handleSimulatePolicy}
                                disabled={simulatePolicyMutation.isPending}
                                className="w-full inline-flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20 rounded-lg hover:bg-blue-100 dark:hover:bg-blue-900/30 disabled:opacity-50"
                            >
                                {simulatePolicyMutation.isPending ? (
                                    <>
                                        <Loader2 className="h-4 w-4 animate-spin" />
                                        Simulating...
                                    </>
                                ) : (
                                    <>
                                        <Play className="h-4 w-4" />
                                        Simulate Policy
                                    </>
                                )}
                            </button>

                            {simulationResult && (
                                <div className="p-4 bg-green-50 dark:bg-green-900/20 rounded-lg">
                                    <p className="text-sm text-green-800 dark:text-green-300 font-medium">
                                        ✓ Simulation successful
                                    </p>
                                    <p className="text-xs text-green-700 dark:text-green-400 mt-1">
                                        Policy rules are valid and ready to deploy
                                    </p>
                                </div>
                            )}
                        </div>
                    )}

                    {/* Navigation Buttons */}
                    <div className="flex justify-between pt-4 border-t border-gray-200 dark:border-slate-700">
                        <button
                            onClick={handlePrevStep}
                            disabled={policyWizardStep === 1}
                            className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            Previous
                        </button>
                        <div className="flex gap-3">
                            <button
                                onClick={() => setIsPolicyWizardOpen(false)}
                                className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg"
                            >
                                Cancel
                            </button>
                            {policyWizardStep < 4 ? (
                                <button
                                    onClick={handleNextStep}
                                    disabled={!policyForm.name}
                                    className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg"
                                >
                                    Next
                                </button>
                            ) : (
                                <button
                                    onClick={handleCreatePolicy}
                                    disabled={!policyForm.name || createPolicyMutation.isPending}
                                    className="px-4 py-2 text-sm font-medium text-white bg-green-600 hover:bg-green-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg"
                                >
                                    {createPolicyMutation.isPending ? 'Creating...' : 'Create Policy'}
                                </button>
                            )}
                        </div>
                    </div>
                </div>
            </Drawer>
        </MainLayout>
    );
}
