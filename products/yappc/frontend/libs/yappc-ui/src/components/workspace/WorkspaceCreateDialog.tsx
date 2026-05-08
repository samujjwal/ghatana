/**
 * WorkspaceCreateDialog
 *
 * Modal dialog for creating a new workspace. Validates the name client-side
 * before calling `onCreate`.
 *
 * @doc.type component
 * @doc.purpose Create-workspace form in a modal dialog
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React, { useState } from 'react';

export interface WorkspaceCreateDialogProps {
  open: boolean;
  onClose: () => void;
  onCreate: (data: { name: string; description?: string }) => Promise<void>;
  isLoading?: boolean;
}

/**
 * Dialog for creating a new workspace.
 */
export const WorkspaceCreateDialog: React.FC<WorkspaceCreateDialogProps> = ({
  open,
  onClose,
  onCreate,
  isLoading = false,
}) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [nameError, setNameError] = useState('');

  const resetForm = (): void => {
    setName('');
    setDescription('');
    setNameError('');
  };

  const handleClose = (): void => {
    resetForm();
    onClose();
  };

  const submitForm = async (): Promise<void> => {
    const trimmedName = name.trim();
    if (!trimmedName) {
      setNameError('Workspace name is required.');
      return;
    }
    if (trimmedName.length < 2) {
      setNameError('Name must be at least 2 characters.');
      return;
    }

    try {
      await onCreate({
        name: trimmedName,
        description: description.trim() || undefined,
      });
      handleClose();
    } catch (error) {
      setNameError(
        error instanceof Error ? error.message : 'Failed to create workspace.'
      );
    }
  };

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    void submitForm();
  };

  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="workspace-create-title"
        className="w-full max-w-lg rounded-2xl bg-white shadow-2xl"
      >
        <form onSubmit={handleSubmit}>
          <div className="border-b border-slate-200 px-6 py-4">
            <h2
              id="workspace-create-title"
              className="text-lg font-semibold text-slate-900"
            >
              Create workspace
            </h2>
          </div>

          <div className="space-y-4 px-6 py-5">
            <p className="text-sm leading-6 text-slate-600">
              A workspace groups projects and allows you to collaborate with
              team members.
            </p>

            <label className="block">
              <span className="text-sm font-medium text-slate-700">
                Workspace name
              </span>
              <input
                value={name}
                onChange={(event) => {
                  setName(event.target.value);
                  if (nameError) {
                    setNameError('');
                  }
                }}
                required
                autoFocus
                maxLength={80}
                aria-invalid={Boolean(nameError)}
                aria-describedby={
                  nameError ? 'workspace-name-error' : undefined
                }
                className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {nameError && (
                <span
                  id="workspace-name-error"
                  role="alert"
                  className="mt-1 block text-xs text-red-600"
                >
                  {nameError}
                </span>
              )}
            </label>

            <label className="block">
              <span className="text-sm font-medium text-slate-700">
                Description (optional)
              </span>
              <textarea
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                rows={3}
                maxLength={500}
                className="mt-1 block w-full resize-none rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </label>
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-200 px-6 py-4">
            <button
              type="button"
              disabled={isLoading}
              className="rounded-lg px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60 focus:outline-none focus:ring-2 focus:ring-blue-500"
              onClick={handleClose}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isLoading || !name.trim()}
              className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {isLoading && (
                <span
                  className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white"
                  aria-hidden="true"
                />
              )}
              Create
            </button>
          </div>
        </form>
      </section>
    </div>
  );
};
