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
import { useAtom } from 'jotai';

import {
    workspaceAtom,
    type ProjectWithOwnership
} from '../../state/atoms/workspaceAtom';
import { useCreateProject, useNameSuggestions } from '../../hooks/useWorkspaceData';
import { useProgressiveDisclosure } from '../../hooks/useProgressiveDisclosure';

interface CreateProjectDialogProps {
    isOpen: boolean;
    onClose: () => void;
    onCreated?: (project: ProjectWithOwnership) => void;
}

type ProjectType = 'UI' | 'BACKEND' | 'MOBILE' | 'DESKTOP' | 'FULL_STACK';

interface AiProjectSuggestion {
    name: string;
    type: ProjectType;
    rationale: string;
    summary: string;
    recommendations: string[];
    relatedProjects: Array<{
        id: string;
        name: string;
        type: string;
        ownerWorkspaceName: string;
    }>;
}

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
    const createProject = useCreateProject();
    const { suggestProject, suggestProjectSetup } = useNameSuggestions();

    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [type, setType] = useState<ProjectType>('FULL_STACK');
    const [aiSuggestion, setAiSuggestion] = useState<AiProjectSuggestion | null>(null);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [isSuggestionLoading, setIsSuggestionLoading] = useState(false);
    const {
        isFeatureVisible,
        showFeature,
        dismissFeature,
    } = useProgressiveDisclosure({
        experienceLevel: 'intermediate',
        storageKey: 'create-project-dialog-disclosure',
        features: [
            {
                featureId: 'advanced-ai-context',
                minExperienceLevel: 'advanced',
                showHint: true,
            },
        ],
    });

    const inputRef = useRef<HTMLInputElement>(null);
    const isAdvancedAiContextVisible = isFeatureVisible('advanced-ai-context');

    // Focus input and generate suggestion on open
    useEffect(() => {
        if (isOpen) {
            setTimeout(() => inputRef.current?.focus(), 100);
            void generateAiSuggestion();
        } else {
            setName('');
            setDescription('');
            setType('FULL_STACK');
            setAiSuggestion(null);
            setSubmitError(null);
            dismissFeature('advanced-ai-context');
        }
    }, [dismissFeature, isOpen]);

    useEffect(() => {
        if (!isOpen || name.trim()) {
            return;
        }

        void generateAiSuggestion();
    }, [isOpen, type]);

    const normalizedName = name.trim().toLowerCase();
    const duplicateProject = state.ownedProjects.find(
        (project) => project.name.trim().toLowerCase() === normalizedName
    );

    // AI-like suggestion based on existing projects
    const generateAiSuggestion = async () => {
        if (!state.currentWorkspace) {
            return;
        }

        setIsSuggestionLoading(true);

        try {
            const existingNames = state.ownedProjects.map((project) => project.name.toLowerCase());
            const setupSuggestion = await suggestProjectSetup(
                state.currentWorkspace.id,
                description.trim() || undefined,
                type
            );
            const fallbackName = await suggestProject(state.currentWorkspace.id, type);
            const suggestedName = existingNames.includes(setupSuggestion.suggestion.trim().toLowerCase())
                ? fallbackName
                : setupSuggestion.suggestion;

            setAiSuggestion({
                name: suggestedName,
                type: setupSuggestion.inferredType as ProjectType,
                rationale: setupSuggestion.rationale,
                summary: setupSuggestion.summary,
                recommendations: setupSuggestion.recommendations,
                relatedProjects: setupSuggestion.relatedProjects,
            });
        } finally {
            setIsSuggestionLoading(false);
        }
    };

    const handleUseSuggestion = () => {
        if (aiSuggestion) {
            setName(aiSuggestion.name);
            setType(aiSuggestion.type);
        }
    };

    const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        if (!name.trim() || !state.currentWorkspace) return;

        setSubmitError(null);

        try {
            const newProject = await createProject.mutateAsync({
                name: name.trim(),
                description: description.trim() || undefined,
                type,
                ownerWorkspaceId: state.currentWorkspace.id,
            });

            onCreated?.({ ...newProject, isOwned: true } as ProjectWithOwnership);
            onClose();
        } catch (error: unknown) {
            setSubmitError(
                error instanceof Error ? error.message : 'Failed to create project'
            );
        }
    };

    const isCreating = createProject.isPending;

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
                <form id="create-project-form" onSubmit={handleSubmit} className="px-6 py-4 space-y-5">
                    {submitError && (
                        <div
                            role="alert"
                            className="rounded-lg border border-destructive-border bg-destructive-bg px-4 py-3 text-sm text-destructive dark:border-destructive-border/60 dark:bg-destructive-bg/40 dark:text-destructive"
                        >
                            {submitError}
                        </div>
                    )}

                    {/* AI Suggestion Banner */}
                    {aiSuggestion && !name && (
                        <div className="flex items-center gap-3 px-4 py-3 bg-gradient-to-r from-primary-50 to-violet-50 dark:from-primary-900/20 dark:to-violet-900/20 rounded-lg">
                            <span className="text-xl">✨</span>
                            <div className="flex-1">
                                <p className="text-sm font-medium text-grey-800 dark:text-grey-200">
                                    {isSuggestionLoading ? 'Finding a strong project name...' : aiSuggestion.name}
                                </p>
                                <p className="text-xs text-grey-500 dark:text-grey-400">
                                    Suggested as {PROJECT_TYPES.find(t => t.value === aiSuggestion.type)?.label}
                                </p>
                                <p className="mt-1 text-xs text-grey-600 dark:text-grey-300">
                                    {aiSuggestion.summary}
                                </p>
                                {!isAdvancedAiContextVisible && (
                                    <button
                                        type="button"
                                        onClick={() => showFeature('advanced-ai-context')}
                                        className="mt-2 text-xs font-medium text-primary-600 hover:text-primary-700 dark:text-primary-300 dark:hover:text-primary-200"
                                        data-testid="show-advanced-ai-context"
                                    >
                                        Show AI context
                                    </button>
                                )}
                                {isAdvancedAiContextVisible && (
                                    <div className="mt-2" data-testid="advanced-ai-context-panel">
                                        <p className="text-xs text-grey-500 dark:text-grey-400">
                                            {aiSuggestion.rationale}
                                        </p>
                                        {aiSuggestion.recommendations.length > 0 && (
                                            <div className="mt-2 flex flex-wrap gap-2">
                                                {aiSuggestion.recommendations.map((recommendation) => (
                                                    <span
                                                        key={recommendation}
                                                        className="rounded-full bg-white/80 px-2.5 py-1 text-[11px] text-grey-600 shadow-sm dark:bg-grey-800/80 dark:text-grey-300"
                                                    >
                                                        {recommendation}
                                                    </span>
                                                ))}
                                            </div>
                                        )}
                                        {aiSuggestion.relatedProjects.length > 0 && (
                                            <div className="mt-3 rounded-md bg-white/70 px-3 py-2 text-xs text-grey-600 shadow-sm dark:bg-grey-900/40 dark:text-grey-300">
                                                <p className="font-medium text-grey-700 dark:text-grey-200">Related projects in other workspaces</p>
                                                <ul className="mt-1 space-y-1">
                                                    {aiSuggestion.relatedProjects.map((project) => (
                                                        <li key={project.id}>
                                                            {project.name} in {project.ownerWorkspaceName} ({project.type})
                                                        </li>
                                                    ))}
                                                </ul>
                                            </div>
                                        )}
                                    </div>
                                )}
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
                        {duplicateProject && (
                            <p className="mt-1.5 text-xs text-warning-color dark:text-warning-color">
                                A project named {duplicateProject.name} already exists in this workspace.
                            </p>
                        )}
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
                            data-testid="project-description-input"
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
                        form="create-project-form"
                        disabled={!name.trim() || isCreating || Boolean(duplicateProject)}
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
