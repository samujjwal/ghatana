/**
 * Org Chart - Visual Organization Structure
 *
 * Interactive visual representation of the organization's hierarchy.
 * Supports drag-and-drop restructuring and agent assignment.
 * Connected to backend API for real data.
 *
 * @doc.type route
 * @doc.section MANAGE
 */

import React, { useState, useCallback } from 'react';
import { useAtom } from 'jotai';
import { useNavigate } from 'react-router';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { MainLayout } from '@/app/Layout';
import { Card, Button } from '@/components/ui';
import {
    Building2,
    Plus,
    GripVertical,
    ChevronDown,
    ChevronRight,
    Settings,
    UserPlus,
    Loader2,
    RefreshCw,
    AlertCircle,
} from 'lucide-react';
import { useOrgDepartments as useDepartments, useAgents, useAddAgentToDepartment, orgQueryKeys } from '@/hooks';
import { useQueryClient } from '@tanstack/react-query';
import type { Agent } from '@/hooks/useOrganizationApi';
import { AddDepartmentModal } from '@/components/org/AddDepartmentModal';
import { AddAgentToDeptModal } from '@/components/org/AddAgentToDeptModal';

// Status badge colors
const STATUS_COLORS: Record<string, string> = {
    ONLINE: 'bg-green-500',
    ACTIVE: 'bg-green-500',
    active: 'bg-green-500',
    OFFLINE: 'bg-slate-400',
    IDLE: 'bg-yellow-500',
    idle: 'bg-yellow-500',
    BUSY: 'bg-blue-500',
    busy: 'bg-blue-500',
};

// Department interface matching API
interface DepartmentWithAgents {
    id: string;
    organizationId: string;
    name: string;
    type: string;
    description: string | null;
    status: string;
    agents: Agent[];
    children?: DepartmentWithAgents[];
    _count?: {
        agents: number;
        teams: number;
        workflows: number;
    };
}

// Department card component
function DepartmentCard({
    dept,
    agents,
    depth = 0,
    onAddAgent,
    onConfigure,
}: {
    dept: DepartmentWithAgents;
    agents: Agent[];
    depth?: number;
    onAddAgent: (deptId: string, deptName: string) => void;
    onConfigure: (deptId: string) => void;
}) {
    const [expanded, setExpanded] = useState(true);
    const hasChildren = dept.children && dept.children.length > 0;
    const deptAgents = agents.filter(a => a.departmentId === dept.id);

    return (
        <div className={`${depth > 0 ? 'ml-8 mt-3' : ''}`}>
            <Card className="p-0 overflow-hidden">
                {/* Department Header */}
                <div className="flex items-center justify-between p-4 bg-slate-50 dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700">
                    <div className="flex items-center gap-3">
                        <GripVertical className="w-4 h-4 text-slate-400 cursor-grab" />
                        {(hasChildren || deptAgents.length > 0) && (
                            <button onClick={() => setExpanded(!expanded)}>
                                {expanded ? (
                                    <ChevronDown className="w-4 h-4 text-slate-500" />
                                ) : (
                                    <ChevronRight className="w-4 h-4 text-slate-500" />
                                )}
                            </button>
                        )}
                        <Building2 className="w-5 h-5 text-blue-500" />
                        <div>
                            <div className="font-semibold text-slate-900 dark:text-white">
                                {dept.name}
                            </div>
                            <div className="text-xs text-slate-500">
                                {deptAgents.length} agents · {dept.type}
                            </div>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => onAddAgent(dept.id, dept.name)}
                            title="Add agent to department"
                        >
                            <UserPlus className="w-4 h-4" />
                        </Button>
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => onConfigure(dept.id)}
                            title="Configure department"
                        >
                            <Settings className="w-4 h-4" />
                        </Button>
                    </div>
                </div>

                {/* Agents List */}
                {expanded && deptAgents.length > 0 && (
                    <div className="p-3 space-y-2">
                        {deptAgents.map(agent => (
                            <div
                                key={agent.id}
                                className="flex items-center justify-between p-3 rounded-lg bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 hover:shadow-sm transition-shadow cursor-pointer"
                            >
                                <div className="flex items-center gap-3">
                                    <div className={`w-2 h-2 rounded-full ${STATUS_COLORS[agent.status] || 'bg-slate-400'}`} />
                                    <div>
                                        <div className="font-medium text-slate-900 dark:text-white">
                                            {agent.name}
                                        </div>
                                        <div className="text-xs text-slate-500">
                                            {agent.role}
                                        </div>
                                    </div>
                                </div>
                                <span className={`text-xs px-2 py-1 rounded-full ${agent.status === 'ONLINE' ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' :
                                    agent.status === 'BUSY' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' :
                                        'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
                                    }`}>
                                    {agent.status}
                                </span>
                            </div>
                        ))}
                    </div>
                )}

                {/* Empty state for no agents */}
                {expanded && deptAgents.length === 0 && (
                    <div className="p-6 text-center text-slate-500 dark:text-slate-400">
                        <UserPlus className="w-8 h-8 mx-auto mb-2 opacity-50" />
                        <p className="text-sm">No agents assigned</p>
                        <Button
                            variant="ghost"
                            size="sm"
                            className="mt-2"
                            onClick={() => onAddAgent(dept.id, dept.name)}
                        >
                            Add Agent
                        </Button>
                    </div>
                )}
            </Card>

            {/* Child Departments */}
            {expanded && hasChildren && dept.children!.map(child => (
                <DepartmentCard
                    key={child.id}
                    dept={child}
                    agents={agents}
                    depth={depth + 1}
                    onAddAgent={onAddAgent}
                    onConfigure={onConfigure}
                />
            ))}
        </div>
    );
}

export default function OrgChartPage() {
    const [selectedTenant] = useAtom(selectedTenantAtom);
    // Filter out placeholder 'tenant-1' to allow backend to use default organization
    const tenantId = (selectedTenant && selectedTenant !== 'tenant-1') ? selectedTenant : '';
    const queryClient = useQueryClient();
    const navigate = useNavigate();

    // Modal state
    const [showAddDeptModal, setShowAddDeptModal] = useState(false);
    const [showAddAgentModal, setShowAddAgentModal] = useState(false);
    const [selectedDeptForAgent, setSelectedDeptForAgent] = useState<{
        id: string;
        name: string;
    } | null>(null);

    // Fetch departments and agents from API
    const {
        data: departments = [],
        isLoading: depsLoading,
        error: depsError,
        refetch: refetchDeps,
    } = useDepartments(tenantId);

    const {
        data: agents = [],
        isLoading: agentsLoading,
        error: agentsError,
    } = useAgents(tenantId);

    const isLoading = depsLoading || agentsLoading;
    const error = depsError || agentsError;

    // Mutations
    const addAgentMutation = useAddAgentToDepartment();

    const handleAddAgent = useCallback((deptId: string, deptName: string) => {
        setSelectedDeptForAgent({ id: deptId, name: deptName });
        setShowAddAgentModal(true);
    }, []);

    const handleConfigure = useCallback((deptId: string) => {
        // Navigate to department detail/config page
        navigate(`/admin/organization/departments/${deptId}`);
    }, [navigate]);

    const handleAddDepartment = useCallback(() => {
        setShowAddDeptModal(true);
    }, []);

    const handleRefresh = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: orgQueryKeys.departments() });
        queryClient.invalidateQueries({ queryKey: orgQueryKeys.agents() });
        refetchDeps();
    }, [queryClient, refetchDeps]);

    // Calculate totals
    const totalAgents = agents.length;
    const totalDepartments = departments.length;

    // Loading state
    if (isLoading) {
        return (
            <MainLayout title="Organization Structure" subtitle="Visual hierarchy and restructuring">
                <div className="flex items-center justify-center py-20">
                    <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
                    <span className="ml-3 text-slate-600 dark:text-slate-400">Loading organization structure...</span>
                </div>
            </MainLayout>
        );
    }

    // Error state
    if (error) {
        return (
            <MainLayout title="Organization Structure" subtitle="Visual hierarchy and restructuring">
                <div className="flex flex-col items-center justify-center py-20">
                    <AlertCircle className="w-12 h-12 text-red-500 mb-4" />
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-2">
                        Failed to load organization
                    </h3>
                    <p className="text-slate-600 dark:text-slate-400 mb-4">
                        {(error as Error).message || 'An error occurred while loading data'}
                    </p>
                    <Button onClick={handleRefresh} className="flex items-center gap-2">
                        <RefreshCw className="w-4 h-4" />
                        Try Again
                    </Button>
                </div>
            </MainLayout>
        );
    }

    // Map departments to include agents
    const departmentsWithAgents: DepartmentWithAgents[] = departments.map(dept => ({
        ...dept,
        agents: agents.filter(a => a.departmentId === dept.id),
    }));

    return (
        <MainLayout title="Organization Structure" subtitle="Visual hierarchy and restructuring">
            {/* Actions Bar */}
            <div className="flex items-center justify-between mb-6">
                <div className="flex items-center gap-4">
                    <div className="text-sm text-slate-600 dark:text-slate-400">
                        <span className="font-semibold">{totalDepartments}</span> departments ·{' '}
                        <span className="font-semibold">{totalAgents}</span> agents
                    </div>
                    <Button variant="ghost" size="sm" onClick={handleRefresh} className="flex items-center gap-1">
                        <RefreshCw className="w-3 h-3" />
                        Refresh
                    </Button>
                </div>
                <Button onClick={handleAddDepartment} className="flex items-center gap-2">
                    <Plus className="w-4 h-4" />
                    Add Department
                </Button>
            </div>

            {/* Empty State */}
            {departments.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-20 bg-slate-50 dark:bg-slate-800/50 rounded-lg">
                    <Building2 className="w-16 h-16 text-slate-300 dark:text-slate-600 mb-4" />
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-2">
                        No departments yet
                    </h3>
                    <p className="text-slate-600 dark:text-slate-400 mb-4 text-center max-w-md">
                        Start building your organization structure by adding departments and assigning agents.
                    </p>
                    <Button onClick={handleAddDepartment} className="flex items-center gap-2">
                        <Plus className="w-4 h-4" />
                        Create First Department
                    </Button>
                </div>
            ) : (
                <>
                    {/* Org Tree */}
                    <div className="space-y-4">
                        {departmentsWithAgents.map(dept => (
                            <DepartmentCard
                                key={dept.id}
                                dept={dept}
                                agents={agents}
                                onAddAgent={handleAddAgent}
                                onConfigure={handleConfigure}
                            />
                        ))}
                    </div>

                    {/* Help Text */}
                    <div className="mt-8 p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">
                        <div className="text-sm text-blue-700 dark:text-blue-300">
                            <strong>Tip:</strong> Click the settings icon to configure a department.
                            Use the add user icon to assign agents.
                        </div>
                    </div>
                </>
            )}

            {/* Add Department Modal */}
            <AddDepartmentModal
                isOpen={showAddDeptModal}
                onClose={() => setShowAddDeptModal(false)}
                tenantId={tenantId}
                onSuccess={handleRefresh}
            />

            {/* Add Agent to Department Modal */}
            {selectedDeptForAgent && (
                <AddAgentToDeptModal
                    isOpen={showAddAgentModal}
                    onClose={() => {
                        setShowAddAgentModal(false);
                        setSelectedDeptForAgent(null);
                    }}
                    departmentId={selectedDeptForAgent.id}
                    departmentName={selectedDeptForAgent.name}
                    tenantId={tenantId}
                    onSuccess={handleRefresh}
                />
            )}
        </MainLayout>
    );
}
