/**
 * Create Workspace Dialog
 * 
 * Dead simple modal for creating new workspaces.
 * AI auto-suggests names based on context and existing workspaces.
 * 
 * @doc.type component
 * @doc.purpose Workspace creation with AI suggestions
 * @doc.layer product
 * @doc.pattern Modal Component
 */
import { useState, useEffect, useRef } from 'react';
import { useAtom, useSetAtom } from 'jotai';

import { workspaceAtom, addWorkspaceAtom, type Workspace } from '../../state/atoms/workspaceAtom';

interface CreateWorkspaceDialogProps {
    isOpen: boolean;
    onClose: () => void;
    onCreated?: (workspace: Workspace) => void;
}

export function CreateWorkspaceDialog({
    isOpen,
    onClose,
    onCreated
}: CreateWorkspaceDialogProps) {
    const [state] = useAtom(workspaceAtom);
    const addWorkspace = useSetAtom(addWorkspaceAtom);

    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [createDefaultProject, setCreateDefaultProject] = useState(true);
    const [isCreating, setIsCreating] = useState(false);
    const [aiSuggestion, setAiSuggestion] = useState<string | null>(null);

    const inputRef = useRef<HTMLInputElement>(null);

    // Focus input on open
    useEffect(() => {
        if (isOpen) {
            setTimeout(() => inputRef.current?.focus(), 100);
            generateAiSuggestion();
        } else {
            setName('');
            setDescription('');
            setAiSuggestion(null);
        }
    }, [isOpen]);

    // Generate AI suggestion based on existing workspaces
    const generateAiSuggestion = () => {
        const existingNames = state.availableWorkspaces.map(w => w.name.toLowerCase());

        // Simple AI-like suggestion logic
        const suggestions = [
            'Product Development',
            'Client Projects',
            'Personal',
            'Experiments',
            'Team Alpha',
            'Platform Core',
            'Mobile Apps',
            'Backend Services',
        ];

        const available = suggestions.filter(
            s => !existingNames.includes(s.toLowerCase())
        );

        if (available.length > 0) {
            setAiSuggestion(available[0]);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!name.trim()) return;

        setIsCreating(true);

        try {
            // NOTE: Replace with actual API call
            const newWorkspace: Workspace = {
                id: `ws_${Date.now()}`,
                name: name.trim(),
                description: description.trim() || undefined,
                ownerId: 'current-user-id', // NOTE: Get from auth context
                isDefault: false,
                aiSummary: undefined,
                aiTags: [],
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            };

            addWorkspace(newWorkspace);
            onCreated?.(newWorkspace);
            onClose();
        } catch (error) {
            console.error('Failed to create workspace:', error);
        } finally {
            setIsCreating(false);
        }
    };

    const handleUseSuggestion = () => {
        if (aiSuggestion) {
            setName(aiSuggestion);
        }
    };

    if (!isOpen) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center"
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-workspace-title"
        >
            {/* Backdrop */}
            <div
                className="absolute inset-0 bg-black/50 backdrop-blur-sm"
                onClick={onClose}
            />

            {/* Modal */}
            <div className="
        relative w-full max-w-md mx-4
        bg-white dark:bg-grey-900
        rounded-xl shadow-2xl
        animate-in fade-in zoom-in-95 duration-200
      ">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-grey-200 dark:border-grey-800">
                    <h2 id="create-workspace-title" className="text-lg font-semibold text-grey-900 dark:text-grey-100">
                        Create Workspace
                    </h2>
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
                <form onSubmit={handleSubmit} className="px-6 py-4 space-y-4">
                    {/* AI Suggestion Banner */}
                    {aiSuggestion && !name && (
                        <div className="flex items-center gap-3 px-4 py-3 bg-primary-50 dark:bg-primary-900/20 rounded-lg">
                            <span className="text-lg">✨</span>
                            <div className="flex-1">
                                <p className="text-sm text-grey-700 dark:text-grey-300">
                                    AI Suggestion: <strong>{aiSuggestion}</strong>
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={handleUseSuggestion}
                                className="px-3 py-1 text-sm font-medium text-primary-600 dark:text-primary-400 
                  hover:bg-primary-100 dark:hover:bg-primary-900/40 rounded-md transition-colors"
                            >
                                Use
                            </button>
                        </div>
                    )}

                    {/* Name Field */}
                    <div>
                        <label
                            htmlFor="workspace-name"
                            className="block text-sm font-medium text-grey-700 dark:text-grey-300 mb-1.5"
                        >
                            Name
                        </label>
                        <input
                            ref={inputRef}
                            id="workspace-name"
                            type="text"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            placeholder="e.g., Product Development"
                            data-testid="workspace-name-input"
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

                    {/* Description Field */}
                    <div>
                        <label
                            htmlFor="workspace-description"
                            className="block text-sm font-medium text-grey-700 dark:text-grey-300 mb-1.5"
                        >
                            Description <span className="text-grey-400">(optional)</span>
                        </label>
                        <textarea
                            id="workspace-description"
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            placeholder="What's this workspace for?"
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

                    {/* Create Default Project Checkbox */}
                    <div className="flex items-start gap-3">
                        <input
                            id="create-default-project"
                            type="checkbox"
                            checked={createDefaultProject}
                            onChange={(e) => setCreateDefaultProject(e.target.checked)}
                            className="
                mt-0.5 w-4 h-4
                text-primary-600
                border-grey-300 dark:border-grey-600
                rounded
                focus:ring-2 focus:ring-primary-500/20
              "
                        />
                        <label htmlFor="create-default-project" className="text-sm text-grey-600 dark:text-grey-400">
                            Create a default project in this workspace
                        </label>
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
                        data-testid="create-workspace-submit"
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
                            'Create Workspace'
                        )}
                    </button>
                </div>
            </div>
        </div>
    );
}
