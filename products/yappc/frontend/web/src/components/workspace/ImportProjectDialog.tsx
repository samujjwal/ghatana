/**
 * Import Project Dialog
 * 
 * Dead simple modal for including projects from other workspaces.
 * These projects become read-only in the current workspace.
 * AI shows compatibility and usage suggestions.
 * 
 * @doc.type component
 * @doc.purpose Include external projects (read-only)
 * @doc.layer product
 * @doc.pattern Modal Component
 */
import { useState, useEffect } from 'react';
import { useAtom, useSetAtom } from 'jotai';

import {
    workspaceAtom,
    addIncludedProjectAtom,
    type ProjectWithOwnership
} from '../../state/atoms/workspaceAtom';

interface ImportProjectDialogProps {
    isOpen: boolean;
    onClose: () => void;
    onImported?: (project: ProjectWithOwnership) => void;
}

// Mock available projects for import (would come from API)
interface AvailableProject {
    id: string;
    name: string;
    description?: string;
    type: 'UI' | 'BACKEND' | 'MOBILE' | 'DESKTOP' | 'FULL_STACK';
    ownerWorkspaceName: string;
    aiCompatibilityScore?: number;
    aiUsageHint?: string;
}

export function ImportProjectDialog({
    isOpen,
    onClose,
    onImported
}: ImportProjectDialogProps) {
    const [state] = useAtom(workspaceAtom);
    const addIncludedProject = useSetAtom(addIncludedProjectAtom);

    const [searchQuery, setSearchQuery] = useState('');
    const [availableProjects, setAvailableProjects] = useState<AvailableProject[]>([]);
    const [selectedProject, setSelectedProject] = useState<AvailableProject | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [isImporting, setIsImporting] = useState(false);

    // Load available projects on open
    useEffect(() => {
        if (isOpen) {
            loadAvailableProjects();
        } else {
            setSearchQuery('');
            setSelectedProject(null);
            setAvailableProjects([]);
        }
    }, [isOpen]);

    const loadAvailableProjects = async () => {
        setIsLoading(true);

        // NOTE: Replace with actual API call
        // Simulating available projects from other workspaces
        setTimeout(() => {
            const mockProjects: AvailableProject[] = [
                {
                    id: 'ext_1',
                    name: 'Shared UI Components',
                    description: 'Reusable component library',
                    type: 'UI',
                    ownerWorkspaceName: 'Design Team',
                    aiCompatibilityScore: 95,
                    aiUsageHint: 'Perfect for importing styled components',
                },
                {
                    id: 'ext_2',
                    name: 'Auth Service',
                    description: 'Authentication and authorization microservice',
                    type: 'BACKEND',
                    ownerWorkspaceName: 'Platform Core',
                    aiCompatibilityScore: 88,
                    aiUsageHint: 'Good for adding auth to your app',
                },
                {
                    id: 'ext_3',
                    name: 'Analytics SDK',
                    description: 'Event tracking and analytics',
                    type: 'FULL_STACK',
                    ownerWorkspaceName: 'Data Team',
                    aiCompatibilityScore: 72,
                    aiUsageHint: 'Add analytics to track user behavior',
                },
                {
                    id: 'ext_4',
                    name: 'Mobile UI Kit',
                    description: 'Cross-platform mobile components',
                    type: 'MOBILE',
                    ownerWorkspaceName: 'Mobile Team',
                    aiCompatibilityScore: 65,
                    aiUsageHint: 'Useful if building mobile features',
                },
            ];

            setAvailableProjects(mockProjects);
            setIsLoading(false);
        }, 500);
    };

    const filteredProjects = availableProjects.filter(p =>
        p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        p.ownerWorkspaceName.toLowerCase().includes(searchQuery.toLowerCase())
    );

    const handleImport = async () => {
        if (!selectedProject || !state.currentWorkspace) return;

        setIsImporting(true);

        try {
            // NOTE: Replace with actual API call
            const importedProject: ProjectWithOwnership = {
                id: selectedProject.id,
                name: selectedProject.name,
                description: selectedProject.description,
                type: selectedProject.type,
                status: 'ACTIVE',
                ownerWorkspaceId: 'external-workspace-id', // Different from current
                isDefault: false,
                isOwned: false,
                aiSummary: selectedProject.description,
                aiNextActions: [selectedProject.aiUsageHint || 'Explore project components'],
                aiHealthScore: selectedProject.aiCompatibilityScore,
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            };

            addIncludedProject(importedProject);
            onImported?.(importedProject);
            onClose();
        } catch (error) {
            console.error('Failed to import project:', error);
        } finally {
            setIsImporting(false);
        }
    };

    const getCompatibilityColor = (score?: number) => {
        if (!score) return 'text-grey-400';
        if (score >= 80) return 'text-green-600 dark:text-green-400';
        if (score >= 60) return 'text-yellow-600 dark:text-yellow-400';
        return 'text-orange-600 dark:text-orange-400';
    };

    if (!isOpen) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center"
            role="dialog"
            aria-modal="true"
            aria-labelledby="import-project-title"
        >
            {/* Backdrop */}
            <div
                className="absolute inset-0 bg-black/50 backdrop-blur-sm"
                onClick={onClose}
            />

            {/* Modal */}
            <div className="
        relative w-full max-w-xl mx-4
        bg-white dark:bg-grey-900
        rounded-xl shadow-2xl
        animate-in fade-in zoom-in-95 duration-200
        max-h-[80vh] flex flex-col
      ">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-grey-200 dark:border-grey-800">
                    <div>
                        <h2 id="import-project-title" className="text-lg font-semibold text-grey-900 dark:text-grey-100">
                            Include Project
                        </h2>
                        <p className="text-sm text-grey-500 dark:text-grey-400 mt-0.5">
                            Add external projects as read-only references
                        </p>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        className="p-1 text-grey-400 hover:text-grey-600 dark:hover:text-grey-300 transition-colors"
                        aria-label="Close"
                    >
                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                </div>

                {/* Search */}
                <div className="px-6 py-3 border-b border-grey-100 dark:border-grey-800">
                    <div className="relative">
                        <svg
                            className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-grey-400"
                            fill="none"
                            viewBox="0 0 24 24"
                            stroke="currentColor"
                        >
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                            />
                        </svg>
                        <input
                            type="text"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            placeholder="Search projects or workspaces..."
                            className="
                w-full pl-10 pr-4 py-2
                bg-grey-50 dark:bg-grey-800
                border border-grey-200 dark:border-grey-700
                rounded-lg
                text-sm text-grey-900 dark:text-grey-100
                placeholder:text-grey-400 dark:placeholder:text-grey-500
                focus:outline-none focus:ring-2 focus:ring-primary-500/20 focus:border-primary-500
                transition-colors
              "
                        />
                    </div>
                </div>

                {/* Project List */}
                <div className="flex-1 overflow-y-auto px-6 py-3">
                    {isLoading ? (
                        <div className="flex items-center justify-center py-12">
                            <svg className="animate-spin w-6 h-6 text-primary-500" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                            </svg>
                        </div>
                    ) : filteredProjects.length === 0 ? (
                        <div className="text-center py-12">
                            <p className="text-grey-500 dark:text-grey-400">
                                {searchQuery ? 'No projects match your search' : 'No projects available'}
                            </p>
                        </div>
                    ) : (
                        <ul className="space-y-2">
                            {filteredProjects.map((project) => (
                                <li key={project.id}>
                                    <button
                                        type="button"
                                        onClick={() => setSelectedProject(project)}
                                        className={`
                      w-full flex items-start gap-4 p-4 rounded-lg text-left
                      border transition-all
                      ${selectedProject?.id === project.id
                                                ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                                                : 'border-grey-200 dark:border-grey-700 hover:border-grey-300 dark:hover:border-grey-600'
                                            }
                    `}
                                    >
                                        {/* Compatibility Score */}
                                        <div className="flex-shrink-0 w-12 text-center">
                                            <span className={`text-lg font-bold ${getCompatibilityColor(project.aiCompatibilityScore)}`}>
                                                {project.aiCompatibilityScore || '—'}
                                            </span>
                                            <p className="text-[10px] text-grey-400 uppercase">Match</p>
                                        </div>

                                        {/* Project Info */}
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2">
                                                <span className="font-medium text-grey-900 dark:text-grey-100">
                                                    {project.name}
                                                </span>
                                                <span className="px-1.5 py-0.5 text-[10px] font-medium 
                          bg-grey-100 dark:bg-grey-700 text-grey-600 dark:text-grey-400 rounded">
                                                    {project.type.replace('_', ' ')}
                                                </span>
                                            </div>

                                            <p className="text-sm text-grey-500 dark:text-grey-400 mt-0.5">
                                                from <span className="font-medium">{project.ownerWorkspaceName}</span>
                                            </p>

                                            {project.description && (
                                                <p className="text-xs text-grey-400 dark:text-grey-500 mt-1 line-clamp-1">
                                                    {project.description}
                                                </p>
                                            )}

                                            {/* AI Hint */}
                                            {project.aiUsageHint && (
                                                <p className="text-xs text-primary-600 dark:text-primary-400 mt-2">
                                                    ✨ {project.aiUsageHint}
                                                </p>
                                            )}
                                        </div>

                                        {/* Selected Check */}
                                        {selectedProject?.id === project.id && (
                                            <svg className="w-5 h-5 text-primary-600 dark:text-primary-400 flex-shrink-0"
                                                fill="currentColor" viewBox="0 0 20 20">
                                                <path fillRule="evenodd"
                                                    d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                                                    clipRule="evenodd"
                                                />
                                            </svg>
                                        )}
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>

                {/* Footer */}
                <div className="flex items-center justify-between gap-3 px-6 py-4 border-t border-grey-200 dark:border-grey-800">
                    <p className="text-xs text-grey-500 dark:text-grey-400">
                        Included projects are read-only
                    </p>
                    <div className="flex gap-3">
                        <button
                            type="button"
                            onClick={onClose}
                            className="
                px-4 py-2 text-sm font-medium
                text-grey-700 dark:text-grey-300
                hover:bg-grey-100 dark:hover:bg-grey-800
                rounded-lg transition-colors
              "
                        >
                            Cancel
                        </button>
                        <button
                            type="button"
                            onClick={handleImport}
                            disabled={!selectedProject || isImporting}
                            data-testid="import-project-submit"
                            className="
                px-4 py-2 text-sm font-medium
                bg-primary-600 hover:bg-primary-700
                text-white
                rounded-lg transition-colors
                disabled:opacity-50 disabled:cursor-not-allowed
              "
                        >
                            {isImporting ? 'Including...' : 'Include Project'}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
