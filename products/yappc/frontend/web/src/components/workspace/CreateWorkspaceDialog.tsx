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
import { useState, useEffect, useRef, useCallback } from 'react';
import { useAtomValue, useSetAtom } from 'jotai';

import {
    currentWorkspaceIdAtom,
    workspaceAtom,
    type Workspace,
} from '../../state/atoms/workspaceAtom';
import { useCreateWorkspace, useNameSuggestions } from '../../hooks/useWorkspaceData';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { useTranslation } from '@ghatana/i18n';

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
    const { t } = useTranslation('common');
    const state = useAtomValue(workspaceAtom);
    const setCurrentWorkspaceId = useSetAtom(currentWorkspaceIdAtom);
    const createWorkspace = useCreateWorkspace();
    const { suggestWorkspace } = useNameSuggestions();

    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [createDefaultProject, setCreateDefaultProject] = useState(true);
    const [isCreating, setIsCreating] = useState(false);
    const [aiSuggestion, setAiSuggestion] = useState<string | null>(null);
    const [submitError, setSubmitError] = useState<string | null>(null);

    const inputRef = useRef<HTMLInputElement>(null);
    const formId = 'create-workspace-form';

    const generateAiSuggestion = useCallback(async (): Promise<void> => {
        try {
            const suggestion = await suggestWorkspace();
            setAiSuggestion(suggestion);
        } catch (error) {
            console.warn('Failed to generate workspace suggestion:', error);
            setAiSuggestion(null);
        }
    }, [suggestWorkspace]);

    // Focus input on open
    useEffect(() => {
        if (isOpen) {
            setSubmitError(null);
            setTimeout(() => inputRef.current?.focus(), 100);
            void generateAiSuggestion();
        } else {
            setName('');
            setDescription('');
            setCreateDefaultProject(true);
            setAiSuggestion(null);
            setSubmitError(null);
        }
    }, [generateAiSuggestion, isOpen]);

    const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault();

        const trimmedName = name.trim();
        if (!trimmedName) return;

        setSubmitError(null);

        const existingNames = new Set(
            state.availableWorkspaces.map((workspace) => workspace.name.trim().toLowerCase())
        );
        if (existingNames.has(trimmedName.toLowerCase())) {
            setSubmitError(`A workspace named "${trimmedName}" already exists.`);
            return;
        }

        setIsCreating(true);

        try {
            const newWorkspace: Workspace = await createWorkspace.mutateAsync({
                name: trimmedName,
                description: description.trim() || undefined,
                createDefaultProject,
            });

            setCurrentWorkspaceId(newWorkspace.id);
            onCreated?.(newWorkspace);
            onClose();
        } catch (error: unknown) {
            console.error('Failed to create workspace:', error);
            const message = error instanceof Error ? error.message : 'Failed to create workspace.';
            setSubmitError(message);
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
                    <Button
                        type="button"
                        onClick={onClose}
                        variant="ghost"
                        size="small"
                        className="p-1 text-grey-400 hover:text-grey-600 dark:hover:text-grey-300 transition-colors"
                        aria-label={t('workspace.create.close')}
                    >
                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </Button>
                </div>

                {/* Form */}
                <form id={formId} onSubmit={handleSubmit} className="px-6 py-4 space-y-4">
                    {/* AI Suggestion Banner */}
                    {aiSuggestion && !name && (
                        <div className="flex items-center gap-3 px-4 py-3 bg-primary-50 dark:bg-primary-900/20 rounded-lg">
                            <span className="text-lg">✨</span>
                            <div className="flex-1">
                                <p className="text-sm text-grey-700 dark:text-grey-300">
                                    AI Suggestion: <strong>{aiSuggestion}</strong>
                                </p>
                            </div>
                            <Button
                                type="button"
                                onClick={handleUseSuggestion}
                                variant="ghost"
                                size="small"
                                className="px-3 py-1 text-sm font-medium text-primary-600 dark:text-primary-400 
                  hover:bg-primary-100 dark:hover:bg-primary-900/40 rounded-md transition-colors"
                            >
                                Use
                            </Button>
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
                        <Input
                            ref={inputRef}
                            id="workspace-name"
                            type="text"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            placeholder={t('workspace.create.namePlaceholder')}
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
                        <Textarea
                            id="workspace-description"
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            placeholder={t('workspace.create.descriptionPlaceholder')}
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
                        <Input
                            id="create-default-project"
                            type="checkbox"
                            checked={createDefaultProject}
                            onChange={(e) => setCreateDefaultProject(e.target.checked)}
                            className="
                mt-0.5 w-4 h-4
                !w-4 !min-h-0 !px-0 !py-0
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

                    {submitError && (
                        <p
                            className="text-sm text-destructive dark:text-destructive"
                            data-testid="create-workspace-error"
                        >
                            {submitError}
                        </p>
                    )}
                </form>

                {/* Footer */}
                <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-grey-200 dark:border-grey-800">
                    <Button
                        type="button"
                        onClick={onClose}
                        variant="ghost"
                        className="
              px-4 py-2 text-sm font-medium
              text-grey-700 dark:text-grey-300
              hover:bg-grey-100 dark:hover:bg-grey-800
              rounded-lg transition-colors
            "
                    >
                        {t('workspace.create.cancel')}
                    </Button>
                    <Button
                        type="submit"
                        form={formId}
                        disabled={!name.trim() || isCreating}
                        variant="solid"
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
                            <span>Create Workspace</span>
                        )}
                    </Button>
                </div>
            </div>
        </div>
    );
}
