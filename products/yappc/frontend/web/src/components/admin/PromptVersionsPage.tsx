/**
 * Prompt Versions Page (Admin-Only)
 *
 * Admin interface for managing prompt versions with rollback and weight rebalancing.
 * This provides operators with visibility and control over AI prompt configurations.
 *
 * @doc.type component
 * @doc.purpose Admin-only prompt version management UI
 * @doc.layer product
 * @doc.pattern Admin Component
 */

import React, { useState } from 'react';
import {
  History as HistoryIcon,
  RotateCcw as RollbackIcon,
  Eye as ViewIcon,
  Scale as WeightIcon,
  CheckCircle as ActiveIcon,
  Clock as InactiveIcon,
  X as CloseIcon,
} from 'lucide-react';

interface PromptVersion {
  id: string;
  promptName: string;
  content: string;
  contentHash: string;
  description: string;
  author: string;
  active: boolean;
  createdAt: string;
}

interface PromptVersionsPageProps {
  className?: string;
}

export function PromptVersionsPage({ className }: PromptVersionsPageProps) {
  const [selectedPrompt, setSelectedPrompt] = useState<PromptVersion | null>(null);
  const [viewDialogOpen, setViewDialogOpen] = useState(false);
  const [rollbackDialogOpen, setRollbackDialogOpen] = useState(false);
  const [weightDialogOpen, setWeightDialogOpen] = useState(false);

  // Mock data - in production, this would come from the PromptVersioningService API
  const mockPromptVersions: PromptVersion[] = [
    {
      id: '1',
      promptName: 'requirement-generation',
      content: 'Generate requirements from user intent...',
      contentHash: 'abc123',
      description: 'Improved clarity in requirement extraction',
      author: 'admin',
      active: true,
      createdAt: '2026-04-27T10:00:00Z',
    },
    {
      id: '2',
      promptName: 'requirement-generation',
      content: 'Generate requirements from user intent with context...',
      contentHash: 'def456',
      description: 'Added context awareness',
      author: 'admin',
      active: false,
      createdAt: '2026-04-26T10:00:00Z',
    },
    {
      id: '3',
      promptName: 'code-generation',
      content: 'Generate code from requirements...',
      contentHash: 'ghi789',
      description: 'Initial version',
      author: 'system',
      active: true,
      createdAt: '2026-04-25T10:00:00Z',
    },
  ];

  const handleView = (prompt: PromptVersion) => {
    setSelectedPrompt(prompt);
    setViewDialogOpen(true);
  };

  const handleRollback = (prompt: PromptVersion) => {
    setSelectedPrompt(prompt);
    setRollbackDialogOpen(true);
  };

  const handleConfirmRollback = async () => {
    if (!selectedPrompt) return;
    // In production, this would call the PromptVersioningService.activate() API
    console.log('Rolling back to prompt version:', selectedPrompt.id);
    setRollbackDialogOpen(false);
    setSelectedPrompt(null);
  };

  const handleWeightRebalance = (prompt: PromptVersion) => {
    setSelectedPrompt(prompt);
    setWeightDialogOpen(true);
  };

  const groupedPrompts = mockPromptVersions.reduce((acc, prompt) => {
    if (!acc[prompt.promptName]) {
      acc[prompt.promptName] = [];
    }
    acc[prompt.promptName].push(prompt);
    return acc;
  }, {} as Record<string, PromptVersion[]>);

  return (
    <div className={className}>
      <div className="p-6 space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-2xl font-bold text-zinc-100 mb-2">
            Prompt Version Management
          </h1>
          <p className="text-sm text-zinc-400">
            Manage AI prompt versions, roll back to previous versions, and configure weight rebalancing for A/B testing.
          </p>
        </div>

        {/* Alert */}
        <div className="bg-blue-900/20 border border-blue-800 rounded-lg p-4 text-blue-400">
          This page is for administrators only. Changes here affect AI behavior across all tenants.
        </div>

        {/* Prompt Groups */}
        {Object.entries(groupedPrompts).map(([promptName, versions]) => (
          <div key={promptName} className="bg-zinc-900 border border-zinc-800 rounded-xl p-5">
            <h2 className="text-lg font-semibold text-zinc-100 mb-4">{promptName}</h2>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-zinc-800">
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                      Status
                    </th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                      Version ID
                    </th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                      Description
                    </th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                      Author
                    </th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                      Created
                    </th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                      Hash
                    </th>
                    <th className="text-right py-3 px-4 text-xs font-medium text-zinc-400 uppercase tracking-wider">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {versions.map((version) => (
                    <tr key={version.id} className="border-b border-zinc-800 hover:bg-zinc-800/50">
                      <td className="py-3 px-4">
                        {version.active ? (
                          <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-emerald-900/30 text-emerald-400 text-xs font-medium">
                            <ActiveIcon size={12} />
                            Active
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-zinc-800 text-zinc-400 text-xs font-medium">
                            <InactiveIcon size={12} />
                            Inactive
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-4">
                        <code className="text-xs text-zinc-400">{version.id.slice(0, 8)}</code>
                      </td>
                      <td className="py-3 px-4 text-sm text-zinc-300">{version.description}</td>
                      <td className="py-3 px-4 text-sm text-zinc-400">{version.author}</td>
                      <td className="py-3 px-4 text-sm text-zinc-400">
                        {new Date(version.createdAt).toLocaleString()}
                      </td>
                      <td className="py-3 px-4">
                        <code className="text-xs text-zinc-400">{version.contentHash.slice(0, 8)}</code>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            type="button"
                            onClick={() => handleView(version)}
                            className="p-1.5 rounded hover:bg-zinc-700 text-zinc-400 hover:text-zinc-200 transition-colors"
                            aria-label="View prompt version"
                            title="View content"
                          >
                            <ViewIcon size={14} />
                          </button>
                          {!version.active && (
                            <button
                              type="button"
                              onClick={() => handleRollback(version)}
                              className="p-1.5 rounded hover:bg-zinc-700 text-zinc-400 hover:text-zinc-200 transition-colors"
                              aria-label="Rollback to this version"
                              title="Rollback to this version"
                            >
                              <RollbackIcon size={14} />
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => handleWeightRebalance(version)}
                            className="p-1.5 rounded hover:bg-zinc-700 text-zinc-400 hover:text-zinc-200 transition-colors"
                            aria-label="Configure weight"
                            title="Configure weight"
                          >
                            <WeightIcon size={14} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ))}
      </div>

      {/* View Dialog */}
      {viewDialogOpen && selectedPrompt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl max-w-2xl w-full max-h-[80vh] overflow-auto">
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h2 className="text-lg font-semibold text-zinc-100">Prompt Version Content</h2>
              <button
                type="button"
                onClick={() => setViewDialogOpen(false)}
                className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
              >
                <CloseIcon size={20} />
              </button>
            </div>
            <div className="p-4 space-y-3">
              <div className="text-sm text-zinc-400">
                <span className="font-medium text-zinc-300">Prompt Name:</span> {selectedPrompt.promptName}
              </div>
              <div className="text-sm text-zinc-400">
                <span className="font-medium text-zinc-300">Description:</span> {selectedPrompt.description}
              </div>
              <div className="text-sm text-zinc-400">
                <span className="font-medium text-zinc-300">Author:</span> {selectedPrompt.author}
              </div>
              <div className="text-sm text-zinc-400">
                <span className="font-medium text-zinc-300">Content Hash:</span> {selectedPrompt.contentHash}
              </div>
              <div className="bg-zinc-800 rounded-lg p-4">
                <pre className="whitespace-pre-wrap text-sm text-zinc-300">
                  {selectedPrompt.content}
                </pre>
              </div>
            </div>
            <div className="flex justify-end p-4 border-t border-zinc-800">
              <button
                type="button"
                onClick={() => setViewDialogOpen(false)}
                className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-lg text-sm font-medium transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Rollback Confirmation Dialog */}
      {rollbackDialogOpen && selectedPrompt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl max-w-md w-full">
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h2 className="text-lg font-semibold text-zinc-100">Confirm Rollback</h2>
              <button
                type="button"
                onClick={() => setRollbackDialogOpen(false)}
                className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
              >
                <CloseIcon size={20} />
              </button>
            </div>
            <div className="p-4">
              <p className="text-sm text-zinc-300">
                Are you sure you want to rollback to version{' '}
                <code className="text-zinc-400">{selectedPrompt.id.slice(0, 8)}</code>? This will make this
                version the active prompt and deactivate the current active version.
              </p>
            </div>
            <div className="flex justify-end gap-2 p-4 border-t border-zinc-800">
              <button
                type="button"
                onClick={() => setRollbackDialogOpen(false)}
                className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-lg text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmRollback}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-sm font-medium transition-colors"
              >
                Confirm Rollback
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Weight Configuration Dialog */}
      {weightDialogOpen && selectedPrompt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl max-w-md w-full">
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h2 className="text-lg font-semibold text-zinc-100">Configure Weight</h2>
              <button
                type="button"
                onClick={() => setWeightDialogOpen(false)}
                className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
              >
                <CloseIcon size={20} />
              </button>
            </div>
            <div className="p-4 space-y-4">
              <p className="text-sm text-zinc-300">
                Configure the weight for this prompt version in A/B testing.
                Higher weights increase the likelihood of this version being
                selected.
              </p>
              <div className="text-sm text-zinc-400">
                <span className="font-medium text-zinc-300">Current Version:</span>{' '}
                <code>{selectedPrompt.id.slice(0, 8)}</code>
              </div>
              <div className="bg-blue-900/20 border border-blue-800 rounded-lg p-4 text-blue-400 text-sm">
                Weight configuration will be implemented in a future update.
              </div>
            </div>
            <div className="flex justify-end p-4 border-t border-zinc-800">
              <button
                type="button"
                onClick={() => setWeightDialogOpen(false)}
                className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-lg text-sm font-medium transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default PromptVersionsPage;
