/**
 * Workspaces Route
 * 
 * Display all workspaces for the current user with options to create, edit, and switch workspaces.
 * 
 * @doc.type route
 * @doc.purpose Workspace management and selection
 * @doc.layer product
 * @doc.pattern Route Module
 */

import { useNavigate } from "react-router";
import { Plus as Add, Settings, Users as Group, FolderOpen } from 'lucide-react';
import { useWorkspaceContext } from "../../hooks/useWorkspaceData";
import { useSetAtom } from "jotai";
import { setWorkspaceBreadcrumbAtom } from "../../state/atoms/breadcrumbAtom";
import { ApiUnavailableFallback } from "../../components/route/ApiUnavailableFallback";
import { useCallback } from "react";

/**
 * Workspaces Page Component
 */
export default function Component() {
    const navigate = useNavigate();
    const { workspaces, isLoading, error } = useWorkspaceContext();
    const setWorkspaceBreadcrumb = useSetAtom(setWorkspaceBreadcrumbAtom);

    const handleSelectWorkspace = useCallback((workspaceId: string) => {
        setWorkspaceBreadcrumb({ id: workspaceId, name: workspaces.find(w => w.id === workspaceId)?.name || 'Workspace' });
        navigate(`/app/projects`);
    }, [workspaces, setWorkspaceBreadcrumb, navigate]);

    const handleCreateWorkspace = useCallback(() => {
        navigate("/app/workspaces/new");
    }, [navigate]);

    const handleRetry = useCallback(() => {
        window.location.reload();
    }, []);

    if (isLoading) {
        return (
            <div className="flex items-center justify-center min-h-[80vh]">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-primary-500 mx-auto mb-4"></div>
                    <p className="text-text-secondary">Loading workspaces...</p>
                </div>
            </div>
        );
    }

    // Check if error is a service unavailability error
    if (error) {
        const errorMessage = typeof error === 'string' ? error : error.message || String(error);
        if (errorMessage.includes('unavailable') || errorMessage.includes('connection') || errorMessage.includes('database')) {
            return (
                <ApiUnavailableFallback
                    error={errorMessage}
                    onRetry={handleRetry}
                    isRetrying={false}
                />
            );
        }

        return (
            <div className="flex items-center justify-center min-h-[80vh]">
                <div className="bg-error-50 border border-error-200 rounded-lg p-6 max-w-md">
                    <h2 className="text-lg font-semibold text-error-900 mb-2">Error Loading Workspaces</h2>
                    <p className="text-error-700 mb-4">{errorMessage}</p>
                    <button
                        onClick={handleRetry}
                        className="px-4 py-2 bg-error-600 text-white rounded-lg hover:bg-error-700 transition-colors"
                    >
                        Retry
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-background-primary p-8">
            <div className="max-w-6xl mx-auto">
                {/* Header */}
                <div className="mb-8">
                    <div className="flex items-center justify-between mb-2">
                        <h1 className="text-3xl font-bold text-text-primary">Workspaces</h1>
                        <button
                            onClick={handleCreateWorkspace}
                            className="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors"
                        >
                            <Add className="w-5 h-5" />
                            New Workspace
                        </button>
                    </div>
                    <p className="text-text-secondary">Manage and switch between your workspaces</p>
                </div>

                {/* Workspaces Grid */}
                {workspaces && workspaces.length > 0 ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        {workspaces.map((workspace) => (
                            <div
                                key={workspace.id}
                                className="bg-surface-primary border border-surface-secondary rounded-lg hover:border-primary-400 transition-colors cursor-pointer group"
                                onClick={() => handleSelectWorkspace(workspace.id)}
                            >
                                <div className="p-6">
                                    {/* Workspace Icon */}
                                    <div className="mb-4 inline-flex items-center justify-center w-12 h-12 rounded-lg bg-primary-50 group-hover:bg-primary-100 transition-colors">
                                        <FolderOpen className="w-6 h-6 text-primary-500" />
                                    </div>

                                    {/* Workspace Info */}
                                    <h3 className="text-lg font-semibold text-text-primary mb-1">
                                        {workspace.name}
                                    </h3>
                                    <p className="text-sm text-text-secondary mb-4">
                                        {workspace.description || "No description"}
                                    </p>

                                    {/* Stats */}
                                    <div className="flex items-center gap-4 text-sm text-text-secondary mb-4">
                                        <div className="flex items-center gap-1">
                                            <FolderOpen className="w-4 h-4" />
                                            <span>{workspace.projectCount || 0} projects</span>
                                        </div>
                                        <div className="flex items-center gap-1">
                                            <Group className="w-4 h-4" />
                                            <span>{workspace.memberCount || 1} member</span>
                                        </div>
                                    </div>

                                    {/* Actions */}
                                    <div className="flex gap-2 pt-4 border-t border-surface-secondary">
                                        <button
                                            className="flex-1 px-3 py-2 text-sm font-medium text-primary-500 hover:bg-primary-50 rounded transition-colors"
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                navigate(`/app/workspaces/${workspace.id}/settings`);
                                            }}
                                        >
                                            <Settings className="w-4 h-4 inline mr-2" />
                                            Settings
                                        </button>
                                        <button
                                            className="flex-1 px-3 py-2 text-sm font-medium text-text-primary hover:bg-surface-secondary rounded transition-colors"
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                handleSelectWorkspace(workspace.id);
                                            }}
                                        >
                                            Open
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                ) : (
                    <div className="text-center py-12">
                        <div className="mb-4">
                            <FolderOpen className="w-16 h-16 text-text-secondary mx-auto opacity-50" />
                        </div>
                        <h2 className="text-xl font-semibold text-text-primary mb-2">No workspaces yet</h2>
                        <p className="text-text-secondary mb-6">Create your first workspace to get started</p>
                        <button
                            onClick={handleCreateWorkspace}
                            className="inline-flex items-center gap-2 px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors"
                        >
                            <Add className="w-5 h-5" />
                            Create Workspace
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}
