/**
 * Persona Preferences Page
 *
 * Purpose:
 * Allows users to view and update their persona configuration for the current workspace.
 *
 * Features:
 * - View all available role definitions
 * - Select active roles (1-5 roles, validation applied)
 * - Configure dashboard layout preferences
 * - Enable/disable plugins
 * - Real-time validation with Java domain service
 * - Optimistic UI updates
 *
 * Data Flow:
 * 1. Loader: Prefetch roles + current preferences (SSR-ready)
 * 2. Component: Display role selection + preferences form
 * 3. Mutation: Validate with Java → Save to Node.js → Update cache
 * 4. WebSocket: Real-time sync across tabs (Task 16)
 */

import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router';
import {
    useRoleDefinitions,
    usePersonaPreference,
    useUpdatePersonaPreference,
} from '@/lib/hooks/usePersonaQueries';
import { usePersonaSync } from '@/lib/hooks/usePersonaSync';
import { useToast } from '@/lib/toast';
import type { UpdatePersonaPreferenceInput } from '@/lib/api/persona.types';
import { RoleInheritanceTree } from '@/components/RoleInheritanceTree';
import { AuditTimeline } from '@/components/AuditTimeline';
import { useAuditLog } from '@/hooks/useAudit';
import { AuditAction, AuditResourceType, AuditSeverity } from '@/types/audit';

/**
 * Loading skeleton for role cards
 * Provides better UX than spinner by showing content structure
 */
const RoleCardSkeleton = () => (
    <div className="border border-slate-200 rounded-lg p-4" role="status" aria-label="Loading role card">
        <div className="flex items-start justify-between">
            <div className="flex-1 space-y-3">
                {/* Role title */}
                <div className="h-5 bg-slate-200 rounded w-3/4 animate-pulse" />
                {/* Role description */}
                <div className="h-4 bg-slate-200 rounded w-full animate-pulse" />
                <div className="h-4 bg-slate-200 rounded w-5/6 animate-pulse" />
                {/* Role metadata */}
                <div className="h-3 bg-slate-200 rounded w-1/2 animate-pulse" />
            </div>
            {/* Checkbox skeleton */}
            <div className="h-5 w-5 bg-slate-200 rounded animate-pulse" />
        </div>
    </div>
);

export function PersonasPage() {
    const navigate = useNavigate();
    const { workspaceId = 'default' } = useParams<{ workspaceId: string }>();
    const { data: roles, isLoading: rolesLoading } = useRoleDefinitions();
    const { data: preference, isLoading: preferenceLoading } = usePersonaPreference(workspaceId);
    const updatePreference = useUpdatePersonaPreference(workspaceId);
    const { showWarning, showError, showSuccess } = useToast();
    const { logEvent } = useAuditLog();

    // WebSocket real-time sync (auto-invalidates cache on remote updates)
    const { isConnected, error: syncError } = usePersonaSync(workspaceId);

    const [selectedRoles, setSelectedRoles] = useState<string[]>(
        preference?.activeRoles || []
    );

    // View mode toggle (list or tree or audit)
    const [viewMode, setViewMode] = useState<'list' | 'tree' | 'audit'>('list');

    // Update selected roles when preference loads
    React.useEffect(() => {
        if (preference?.activeRoles) {
            setSelectedRoles(preference.activeRoles);
        }
    }, [preference?.activeRoles]);

    const handleRoleToggle = (roleId: string) => {
        const role = roles?.find((r) => r.roleId === roleId);

        setSelectedRoles((prev) => {
            const isAdding = !prev.includes(roleId);

            if (isAdding) {
                if (prev.length >= 5) {
                    showWarning('Maximum 5 roles allowed');
                    return prev;
                }

                // Log role addition
                logEvent({
                    action: AuditAction.ROLE_CREATED,
                    resourceType: AuditResourceType.ROLE,
                    resourceId: roleId,
                    resourceName: role?.displayName || roleId,
                    metadata: {
                        userId: 'current-user', // Replace with actual user ID
                        userName: 'Current User', // Replace with actual user name
                        reason: 'Added to active roles',
                    },
                });

                return [...prev, roleId];
            } else {
                // Log role removal
                logEvent({
                    action: AuditAction.ROLE_DELETED,
                    resourceType: AuditResourceType.ROLE,
                    resourceId: roleId,
                    resourceName: role?.displayName || roleId,
                    metadata: {
                        userId: 'current-user',
                        userName: 'Current User',
                        reason: 'Removed from active roles',
                    },
                });

                return prev.filter((id) => id !== roleId);
            }
        });
    };

    const handleSave = async () => {
        if (selectedRoles.length === 0) {
            showWarning('At least 1 role is required');
            return;
        }

        const input: UpdatePersonaPreferenceInput = {
            activeRoles: selectedRoles,
            preferences: preference?.preferences || {},
        };

        try {
            await updatePreference.mutateAsync(input);
            showSuccess('Persona preferences saved successfully!');

            // Log successful save
            await logEvent({
                action: AuditAction.PERSONA_UPDATED,
                resourceType: AuditResourceType.PERSONA,
                resourceId: workspaceId,
                resourceName: `Workspace ${workspaceId}`,
                changes: [
                    {
                        field: 'activeRoles',
                        oldValue: preference?.activeRoles || [],
                        newValue: selectedRoles,
                    },
                ],
                metadata: {
                    userId: 'current-user',
                    userName: 'Current User',
                    reason: 'Saved persona preferences',
                },
            });
        } catch (error: any) {
            showError(`Failed to save: ${error.message}`);

            // Log failed save
            await logEvent({
                action: AuditAction.PERSONA_UPDATED,
                resourceType: AuditResourceType.PERSONA,
                resourceId: workspaceId,
                resourceName: `Workspace ${workspaceId}`,
                success: false,
                errorMessage: error.message,
                severity: AuditSeverity.ERROR,
                metadata: {
                    userId: 'current-user',
                    userName: 'Current User',
                    reason: 'Failed to save persona preferences',
                },
            });
        }
    };

    if (rolesLoading || preferenceLoading) {
        return (
            <div className="container mx-auto px-4 py-8">
                <div className="max-w-4xl mx-auto">
                    <div className="mb-8">
                        <div className="h-8 bg-slate-200 rounded w-64 animate-pulse mb-2" />
                        <div className="h-4 bg-slate-200 rounded w-96 animate-pulse" />
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {Array.from({ length: 6 }).map((_, i) => (
                            <RoleCardSkeleton key={i} />
                        ))}
                    </div>
                </div>
            </div>
        );
    }

    const baseRoles = roles?.filter((r) => r.type === 'BASE') || [];
    const specializedRoles = roles?.filter((r) => r.type === 'SPECIALIZED') || [];

    return (
        <div className="container mx-auto px-4 py-8">
            <div className="max-w-4xl mx-auto">
                {/* Sync status banner */}
                {syncError && (
                    <div className="mb-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
                        <p className="text-sm text-yellow-800">
                            ⚠️ Real-time sync unavailable: {syncError.message}
                        </p>
                    </div>
                )}
                {!isConnected && !syncError && (
                    <div className="mb-4 p-3 bg-blue-50 border border-blue-200 rounded-lg">
                        <p className="text-sm text-blue-800">
                            🔄 Connecting to real-time sync...
                        </p>
                    </div>
                )}
                {isConnected && (
                    <div className="mb-4 p-3 bg-green-50 border border-green-200 rounded-lg">
                        <p className="text-sm text-green-800">
                            ✅ Real-time sync active - changes will sync across tabs
                        </p>
                    </div>
                )}
                <div className="flex flex-col md:flex-row md:justify-between md:items-start gap-4 mb-4">
                    <div>
                        <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
                            Persona Preferences
                        </h1>
                        <p className="text-slate-600 dark:text-neutral-400">
                            Select your active roles and configure your persona preferences. You can activate 1-5 roles.
                        </p>
                    </div>
                    <button
                        onClick={() => navigate('/org?type=persona')}
                        className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
                    >
                        <span>🏗️</span>
                        <span>Open in Org Builder</span>
                    </button>
                </div>

                {/* View Mode Toggle */}
                <div className="flex justify-end mb-6">
                    <div className="inline-flex rounded-lg border border-slate-300 dark:border-neutral-600 bg-white dark:bg-neutral-800">
                        <button
                            onClick={() => setViewMode('list')}
                            className={`px-4 py-2 text-sm font-medium rounded-l-lg transition-colors ${viewMode === 'list'
                                ? 'bg-blue-600 text-white'
                                : 'text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-700'
                                }`}
                        >
                            📋 List View
                        </button>
                        <button
                            onClick={() => setViewMode('tree')}
                            className={`px-4 py-2 text-sm font-medium transition-colors ${viewMode === 'tree'
                                ? 'bg-blue-600 text-white'
                                : 'text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-700'
                                }`}
                        >
                            🌳 Tree View
                        </button>
                        <button
                            onClick={() => setViewMode('audit')}
                            className={`px-4 py-2 text-sm font-medium rounded-r-lg transition-colors ${viewMode === 'audit'
                                ? 'bg-blue-600 text-white'
                                : 'text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-700'
                                }`}
                        >
                            📊 Audit Log
                        </button>
                    </div>
                </div>

                {/* Active Roles Summary */}
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-8">
                    <h2 className="text-sm font-semibold text-blue-900 mb-2">
                        Active Roles ({selectedRoles.length}/5)
                    </h2>
                    <div className="flex flex-wrap gap-2">
                        {selectedRoles.length === 0 ? (
                            <span className="text-sm text-blue-600">No roles selected</span>
                        ) : (
                            selectedRoles.map((roleId) => {
                                const role = roles?.find((r) => r.roleId === roleId);
                                return (
                                    <span
                                        key={roleId}
                                        className="px-3 py-1 bg-blue-600 text-white text-sm rounded-full"
                                    >
                                        {role?.displayName || roleId}
                                    </span>
                                );
                            })
                        )}
                    </div>
                </div>

                {/* Conditional Rendering: List View or Tree View or Audit */}
                {viewMode === 'audit' ? (
                    <div className="mb-8">
                        <AuditTimeline
                            initialFilter={{
                                resourceTypes: [AuditResourceType.ROLE, AuditResourceType.PERSONA],
                                limit: 50,
                            }}
                            maxHeight={700}
                            showFilters={true}
                            onEventClick={(event) => {
                                console.log('Event clicked:', event);
                            }}
                        />
                    </div>
                ) : viewMode === 'tree' ? (
                    <div className="mb-8">
                        <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg overflow-hidden" style={{ height: '600px' }}>
                            {selectedRoles.length > 0 ? (
                                <RoleInheritanceTree
                                    personaId={selectedRoles[0]}
                                    interactive={true}
                                    layout="vertical"
                                    onNodeClick={(roleId) => {
                                        console.log('Node clicked:', roleId);
                                        // Could add role to selection or show details
                                    }}
                                />
                            ) : (
                                <div className="flex items-center justify-center h-full text-slate-500 dark:text-neutral-400">
                                    <div className="text-center">
                                        <div className="text-4xl mb-2">🌳</div>
                                        <p className="font-medium">Select a role to view its inheritance tree</p>
                                        <p className="text-sm mt-1">The tree will show parent roles and permissions</p>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                ) : (
                    <>
                        {/* Base Roles */}
                        <section className="mb-8">
                            <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 mb-4">Base Roles</h2>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                {baseRoles.map((role) => {
                                    const isSelected = selectedRoles.includes(role.roleId);
                                    return (
                                        <div
                                            key={role.roleId}
                                            className={`border rounded-lg p-4 cursor-pointer transition-all ${isSelected
                                                ? 'border-blue-600 bg-blue-50 dark:bg-blue-900/30'
                                                : 'border-slate-200 dark:border-neutral-600 hover:border-slate-300 dark:hover:border-slate-600'
                                                }`}
                                            onClick={() => handleRoleToggle(role.roleId)}
                                        >
                                            <div className="flex items-start justify-between">
                                                <div className="flex-1">
                                                    <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                                                        {role.displayName}
                                                    </h3>
                                                    <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                                                        {role.description}
                                                    </p>
                                                    {role.parentRoles && role.parentRoles.length > 0 && (
                                                        <p className="text-xs text-slate-500 dark:text-slate-500 dark:text-neutral-400 mt-2">
                                                            Inherits from: {role.parentRoles.join(', ')}
                                                        </p>
                                                    )}
                                                    <div className="mt-3 text-xs text-slate-500 dark:text-neutral-400">
                                                        {role.permissions.length} permissions • {role.capabilities.length} capabilities
                                                    </div>
                                                </div>
                                                <input
                                                    type="checkbox"
                                                    checked={isSelected}
                                                    onChange={() => handleRoleToggle(role.roleId)}
                                                    className="mt-1 h-6 w-6 min-h-[44px] min-w-[44px] p-2 text-blue-600 rounded cursor-pointer"
                                                    onClick={(e) => e.stopPropagation()}
                                                    aria-label={role.displayName}
                                                />
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </section>

                        {/* Specialized Roles Section */}
                        <section>
                            <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                                Specialized Roles
                            </h2>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                {specializedRoles.map((role) => {
                                    const isSelected = selectedRoles.includes(role.roleId);
                                    return (
                                        <div
                                            key={role.roleId}
                                            className={`border rounded-lg p-4 cursor-pointer transition-all ${isSelected
                                                ? 'border-blue-600 bg-blue-50 dark:bg-blue-900/30'
                                                : 'border-slate-200 dark:border-neutral-600 hover:border-slate-300 dark:hover:border-slate-600'
                                                }`}
                                            onClick={() => handleRoleToggle(role.roleId)}
                                        >
                                            <div className="flex items-start justify-between">
                                                <div className="flex-1">
                                                    <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                                                        {role.displayName}
                                                    </h3>
                                                    <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                                                        {role.description}
                                                    </p>
                                                    {role.parentRoles && role.parentRoles.length > 0 && (
                                                        <p className="text-xs text-slate-500 dark:text-slate-500 dark:text-neutral-400 mt-2">
                                                            Inherits from: {role.parentRoles.join(', ')}
                                                        </p>
                                                    )}
                                                    <div className="mt-3 text-xs text-slate-500 dark:text-neutral-400">
                                                        {role.permissions.length} permissions • {role.capabilities.length} capabilities
                                                    </div>
                                                </div>
                                                <input
                                                    type="checkbox"
                                                    checked={isSelected}
                                                    onChange={() => handleRoleToggle(role.roleId)}
                                                    className="mt-1 h-6 w-6 min-h-[44px] min-w-[44px] p-2 text-blue-600 rounded cursor-pointer"
                                                    onClick={(e) => e.stopPropagation()}
                                                    aria-label={role.displayName}
                                                />
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </section>
                    </>
                )}

                {/* Action Buttons */}
                <div className="flex flex-col sm:flex-row justify-end space-y-3 sm:space-y-0 sm:space-x-4 mt-8">
                    <button
                        onClick={() => setSelectedRoles(preference?.activeRoles || [])}
                        className="px-6 py-3 min-h-[44px] border border-slate-300 text-slate-700 rounded-lg hover:bg-slate-50 transition-colors touch-manipulation"
                    >
                        Reset
                    </button>
                    <button
                        onClick={handleSave}
                        disabled={updatePreference.isPending || selectedRoles.length === 0}
                        className="px-6 py-3 min-h-[44px] bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors touch-manipulation"
                    >
                        {updatePreference.isPending ? 'Saving...' : 'Save Preferences'}
                    </button>
                </div>

                {/* Validation Error */}
                {updatePreference.isError && (
                    <div className="mt-4 p-4 bg-red-50 border border-red-200 rounded-lg">
                        <p className="text-sm text-red-800">
                            {(updatePreference.error as any)?.message || 'Failed to save preferences'}
                        </p>
                    </div>
                )}
            </div>
        </div>
    );
}

export default PersonasPage;
