/**
 * Create Project Dialog
 * 
 * Dead simple modal for creating new projects.
 * AI auto-generates project name and suggests type based on workspace context.
 * 
 * @doc.type component
 * @doc.purpose Project creation with AI auto-naming
 * @doc.layer product
 * @doc.pattern Modal Component
 */
import { useState, useEffect, useRef } from 'react';
import { useAtom, useSetAtom } from 'jotai';

import {
    workspaceAtom,
    addOwnedProjectAtom,
    type ProjectWithOwnership
} from '../../state/atoms/workspaceAtom';

interface CreateProjectDialogProps {
    isOpen: boolean;
    onClose: () => void;
    onCreated?: (project: ProjectWithOwnership) => void;
}

type ProjectType = 'UI' | 'BACKEND' | 'MOBILE' | 'DESKTOP' | 'FULL_STACK';

const PROJECT_TYPES: { value: ProjectType; label: string; icon: string; description: string }[] = [
    { value: 'FULL_STACK', label: 'Full Stack', icon: 'FS', description: 'Complete web application' },
    { value: 'UI', label: 'UI Only', icon: 'UI', description: 'Frontend / Design System' },
    { value: 'BACKEND', label: 'Backend', icon: 'BE', description: 'API / Server' },
    { value: 'MOBILE', label: 'Mobile', icon: 'MB', description: 'iOS / Android app' },
    { value: 'DESKTOP', label: 'Desktop', icon: 'DS', description: 'Desktop application' },
];

export function CreateProjectDialog({
    isOpen,
    onClose,
    onCreated
}: CreateProjectDialogProps) {
    const [state] = useAtom(workspaceAtom);
    const addProject = useSetAtom(addOwnedProjectAtom);

    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [type, setType] = useState<ProjectType>('FULL_STACK');
    const [isCreating, setIsCreating] = useState(false);
    const [aiSuggestion, setAiSuggestion] = useState<{ name: string; type: ProjectType } | null>(null);

    const inputRef = useRef<HTMLInputElement>(null);

    // Focus input and generate suggestion on open
    useEffect(() => {
        if (isOpen) {
            setTimeout(() => inputRef.current?.focus(), 100);
            generateAiSuggestion();
        } else {
            setName('');
            setDescription('');
            setType('FULL_STACK');
            setAiSuggestion(null);
        }
    }, [isOpen]);

    // AI-like suggestion based on existing projects
    const generateAiSuggestion = () => {
        const existingNames = state.ownedProjects.map(p => p.name.toLowerCase());
        const workspaceName = state.currentWorkspace?.name || '';

        // Smart naming based on workspace context
        const prefixes = [
            workspaceName.split(' ')[0],
            'App',
            'Service',
            'Platform',
            'Module',
        ];

        const suffixes = [
            'v2',
            'Core',
            'Main',
            'Pro',
            '',
        ];

        // Find a unique name
        for (const prefix of prefixes) {
            for (const suffix of suffixes) {
                const candidate = `${prefix} ${suffix}`.trim();
                if (!existingNames.includes(candidate.toLowerCase()) && candidate.length > 2) {
                    // Suggest type based on existing projects in workspace
                    const typeCount = state.ownedProjects.reduce((acc, p) => {
                        acc[p.type] = (acc[p.type] || 0) + 1;
                        return acc;
                    }, {} as Record<string, number>);

                    // Suggest a type that's underrepresented
                    let suggestedType: ProjectType = 'FULL_STACK';
                    if (!typeCount['BACKEND']) suggestedType = 'BACKEND';
                    else if (!typeCount['MOBILE']) suggestedType = 'MOBILE';
                    else if (!typeCount['UI']) suggestedType = 'UI';

                    setAiSuggestion({ name: candidate, type: suggestedType });
                    return;
                }
            }
        }

        // Fallback
        setAiSuggestion({ name: `Project ${state.ownedProjects.length + 1}`, type: 'FULL_STACK' });
    };

    const handleUseSuggestion = () => {
        if (aiSuggestion) {
            setName(aiSuggestion.name);
            setType(aiSuggestion.type);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!name.trim() || !state.currentWorkspace) return;

        setIsCreating(true);

        try {
            // NOTE: Replace with actual API call
            const newProject: ProjectWithOwnership = {
                id: `prj_${Date.now()}`,
                name: name.trim(),
                description: description.trim() || undefined,
                type,
                status: 'DRAFT',
                ownerWorkspaceId: state.currentWorkspace.id,
                isDefault: false,
                isOwned: true,
                aiSummary: undefined,
                aiNextActions: ['Set up project structure', 'Define initial requirements'],
                aiHealthScore: undefined,
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            };

            addProject(newProject);
            onCreated?.(newProject);
            onClose();
        } catch (error) {
            console.error('Failed to create project:', error);
        } finally {
            setIsCreating(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center"
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-project-title"
        >
            {/* Backdrop */}
            <div
                className="absolute inset-0 bg-black/50 backdrop-blur-sm"
                onClick={onClose}
            />

            {/* Modal */}
            <div className="
        relative w-full max-w-lg mx-4
        bg-white dark:bg-grey-900
        rounded-xl shadow-2xl
        animate-in fade-in zoom-in-95 duration-200
      ">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-grey-200 dark:border-grey-800">
                    <div>
                        <h2 id="create-project-title" className="text-lg font-semibold text-grey-900 dark:text-grey-100">
                            New Project
                        </h2>
                        <p className="text-sm text-grey-500 dark:text-grey-400 mt-0.5">
                            in {state.currentWorkspace?.name}
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

                {/* Form */}
                <form onSubmit={handleSubmit} className="px-6 py-4 space-y-5">
                    {/* AI Suggestion Banner */}
                    {aiSuggestion && !name && (
                        <div className="flex items-center gap-3 px-4 py-3 bg-gradient-to-r from-primary-50 to-violet-50 dark:from-primary-900/20 dark:to-violet-900/20 rounded-lg">
                            <span className="text-xl">✨</span>
                            <div className="flex-1">
                                <p className="text-sm font-medium text-grey-800 dark:text-grey-200">
                                    {aiSuggestion.name}
                                </p>
                                <p className="text-xs text-grey-500 dark:text-grey-400">
                                    Suggested as {PROJECT_TYPES.find(t => t.value === aiSuggestion.type)?.label}
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={handleUseSuggestion}
                                className="px-3 py-1.5 text-sm font-medium text-primary-600 dark:text-primary-400 
                  bg-white dark:bg-grey-800 rounded-md shadow-sm
                  hover:bg-grey-50 dark:hover:bg-grey-750 transition-colors"
                            >
                                Use This
                            </button>
                        </div>
                    )}

                    {/* Name Field */}
                    <div>
                        <label
                            htmlFor="project-name"
                            className="block text-sm font-medium text-grey-700 dark:text-grey-300 mb-1.5"
                        >
                            Project Name
                        </label>
                        <input
                            ref={inputRef}
                            id="project-name"
                            type="text"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            placeholder="e.g., E-commerce Platform"
                            data-testid="project-name-input"
                            className="
                w-full px-4 py-2.5
                bg-white dark:bg-grey-800
                border border-grey-300 dark:border-grey-700
                rounded-lg
                text-grey-900 dark:text-grey-100
                placeholder:text-grey-400 dark:placeholder:text-grey-500
                focus:outline-none focus:ring-2 focus:ring-primary-500/20 focus:border-primary-500
                transition-colors
              "
                            required
                        />
                    </div>

                    {/* Project Type */}
                    <div>
                        <label className="block text-sm font-medium text-grey-700 dark:text-grey-300 mb-2">
                            Project Type
                        </label>
                        <div className="grid grid-cols-5 gap-2">
                            {PROJECT_TYPES.map((pt) => (
                                <button
                                    key={pt.value}
                                    type="button"
                                    onClick={() => setType(pt.value)}
                                    className={`
                    flex flex-col items-center gap-1 p-3 rounded-lg
                    border transition-all
                    ${type === pt.value
                                            ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                                            : 'border-grey-200 dark:border-grey-700 hover:border-grey-300 dark:hover:border-grey-600'
                                        }
                  `}
                                >
                                    <span className="text-xl">{pt.icon}</span>
                                    <span className={`text-xs font-medium ${type === pt.value
                                            ? 'text-primary-700 dark:text-primary-300'
                                            : 'text-grey-600 dark:text-grey-400'
                                        }`}>
                                        {pt.label}
                                    </span>
                                </button>
                            ))}
                        </div>
                        <p className="mt-2 text-xs text-grey-500 dark:text-grey-400">
                            {PROJECT_TYPES.find(t => t.value === type)?.description}
                        </p>
                    </div>

                    {/* Description Field */}
                    <div>
                        <label
                            htmlFor="project-description"
                            className="block text-sm font-medium text-grey-700 dark:text-grey-300 mb-1.5"
                        >
                            Description <span className="text-grey-400">(optional)</span>
                        </label>
                        <textarea
                            id="project-description"
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            placeholder="Brief description of this project..."
                            rows={2}
                            className="
                w-full px-4 py-2.5
                bg-white dark:bg-grey-800
                border border-grey-300 dark:border-grey-700
                rounded-lg
                text-grey-900 dark:text-grey-100
                placeholder:text-grey-400 dark:placeholder:text-grey-500
                focus:outline-none focus:ring-2 focus:ring-primary-500/20 focus:border-primary-500
                transition-colors resize-none
              "
                        />
                    </div>
                </form>

                {/* Footer */}
                <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-grey-200 dark:border-grey-800">
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
                        type="submit"
                        onClick={handleSubmit}
                        disabled={!name.trim() || isCreating}
                        data-testid="create-project-submit"
                        className="
              px-4 py-2 text-sm font-medium
              bg-primary-600 hover:bg-primary-700
              text-white
              rounded-lg transition-colors
              disabled:opacity-50 disabled:cursor-not-allowed
            "
                    >
                        {isCreating ? (
                            <span className="flex items-center gap-2">
                                <svg className="animate-spin w-4 h-4" viewBox="0 0 24 24">
                                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                                </svg>
                                Creating...
                            </span>
                        ) : (
                            'Create Project'
                        )}
                    </button>
                </div>
            </div>
        </div>
    );
}
