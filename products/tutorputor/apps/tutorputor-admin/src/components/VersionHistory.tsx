/**
 * Version History Panel Component
 * 
 * Displays version history for content with rollback capability.
 * Shows timeline of changes with diff view and restore functionality.
 * 
 * @doc.type component
 * @doc.purpose Content version tracking and rollback
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card } from './ui';
import { Button, Spinner } from '@ghatana/ui';

interface VersionHistoryItem {
    id: string;
    version: number;
    snapshot: Record<string, unknown>;
    createdBy: string;
    createdAt: string;
}

interface VersionHistoryProps {
    entityType: 'domain' | 'concept' | 'simulation';
    entityId: string;
    currentVersion: number;
    onRestore?: (version: number) => void;
}

export function VersionHistory({ entityType, entityId, currentVersion, onRestore }: VersionHistoryProps) {
    const [selectedVersion, setSelectedVersion] = useState<VersionHistoryItem | null>(null);
    const [showDiff, setShowDiff] = useState(false);
    const queryClient = useQueryClient();

    // Fetch version history
    const { data, isLoading } = useQuery({
        queryKey: ['version-history', entityType, entityId],
        queryFn: async () => {
            // Note: This endpoint needs to be implemented in the backend
            const res = await fetch(`/admin/api/v1/content/versions/${entityType}/${entityId}`);
            if (!res.ok) throw new Error('Failed to fetch version history');
            return res.json() as Promise<{ versions: VersionHistoryItem[] }>;
        },
    });

    // Restore version mutation
    const restoreMutation = useMutation({
        mutationFn: async (versionId: string) => {
            const res = await fetch(`/admin/api/v1/content/versions/${entityType}/${entityId}/restore`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ versionId }),
            });
            if (!res.ok) throw new Error('Failed to restore version');
            return res.json();
        },
        onSuccess: (data) => {
            queryClient.invalidateQueries({ queryKey: ['version-history', entityType, entityId] });
            queryClient.invalidateQueries({ queryKey: [entityType, entityId] });
            setSelectedVersion(null);
            setShowDiff(false);
            onRestore?.(data.version);
            alert(`Successfully restored to version ${data.version}`);
        },
        onError: (error) => {
            alert(`Failed to restore version: ${error instanceof Error ? error.message : String(error)}`);
        },
    });

    const handleRestore = (version: VersionHistoryItem) => {
        if (window.confirm(`Restore to version ${version.version}? This will create a new version with the old content.`)) {
            restoreMutation.mutate(version.id);
        }
    };

    const renderDiff = (oldSnapshot: Record<string, unknown>, newSnapshot: Record<string, unknown>) => {
        const oldKeys = new Set(Object.keys(oldSnapshot));
        const newKeys = new Set(Object.keys(newSnapshot));
        const allKeys = new Set([...oldKeys, ...newKeys]);

        return Array.from(allKeys).map((key) => {
            const oldValue = oldSnapshot[key];
            const newValue = newSnapshot[key];
            const hasChanged = JSON.stringify(oldValue) !== JSON.stringify(newValue);

            if (!hasChanged) return null;

            return (
                <div key={key} className="mb-3 pb-3 border-b border-gray-200 dark:border-gray-700">
                    <div className="font-semibold text-sm text-gray-700 dark:text-gray-300 mb-1">{key}</div>
                    <div className="grid grid-cols-2 gap-2 text-sm">
                        <div className="bg-red-50 dark:bg-red-900/20 p-2 rounded">
                            <div className="text-red-700 dark:text-red-400 font-semibold mb-1">Old</div>
                            <pre className="text-xs whitespace-pre-wrap break-words">
                                {JSON.stringify(oldValue, null, 2)}
                            </pre>
                        </div>
                        <div className="bg-green-50 dark:bg-green-900/20 p-2 rounded">
                            <div className="text-green-700 dark:text-green-400 font-semibold mb-1">New</div>
                            <pre className="text-xs whitespace-pre-wrap break-words">
                                {JSON.stringify(newValue, null, 2)}
                            </pre>
                        </div>
                    </div>
                </div>
            );
        }).filter(Boolean);
    };

    if (isLoading) {
        return (
            <Card className="p-4">
                <div className="flex items-center gap-2">
                    <Spinner />
                    <span className="text-gray-600 dark:text-gray-400">Loading version history...</span>
                </div>
            </Card>
        );
    }

    const versions = data?.versions ?? [];

    if (versions.length === 0) {
        return (
            <Card className="p-4 text-center">
                <p className="text-gray-600 dark:text-gray-400">No version history available</p>
                <p className="text-sm text-gray-500 dark:text-gray-500 mt-1">
                    Versions will be saved as you make changes
                </p>
            </Card>
        );
    }

    return (
        <div className="space-y-4">
            <Card className="p-4">
                <h3 className="font-semibold text-gray-900 dark:text-white mb-4">
                    Version History ({versions.length} versions)
                </h3>

                <div className="space-y-3">
                    {versions.map((version, idx) => {
                        const isCurrent = version.version === currentVersion;
                        const nextVersion = versions[idx - 1];

                        return (
                            <div
                                key={version.id}
                                className={`p-3 rounded-lg border ${isCurrent
                                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                        : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
                                    }`}
                            >
                                <div className="flex items-center justify-between">
                                    <div className="flex-1">
                                        <div className="flex items-center gap-2">
                                            <span className="font-semibold text-gray-900 dark:text-white">
                                                Version {version.version}
                                            </span>
                                            {isCurrent && (
                                                <span className="px-2 py-0.5 bg-blue-500 text-white text-xs rounded">
                                                    Current
                                                </span>
                                            )}
                                        </div>
                                        <div className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                                            {new Date(version.createdAt).toLocaleString()} • {version.createdBy}
                                        </div>
                                    </div>

                                    <div className="flex gap-2">
                                        {nextVersion && (
                                            <Button
                                                size="sm"
                                                variant="outline"
                                                onClick={() => {
                                                    setSelectedVersion(version);
                                                    setShowDiff(true);
                                                }}
                                            >
                                                View Changes
                                            </Button>
                                        )}
                                        {!isCurrent && (
                                            <Button
                                                size="sm"
                                                variant="outline"
                                                onClick={() => handleRestore(version)}
                                                disabled={restoreMutation.isPending}
                                            >
                                                {restoreMutation.isPending ? 'Restoring...' : 'Restore'}
                                            </Button>
                                        )}
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </Card>

            {/* Diff Modal */}
            {showDiff && selectedVersion && (
                <div className="fixed inset-0 bg-black/50 dark:bg-black/70 flex items-center justify-center z-50 p-4">
                    <Card className="w-full max-w-4xl max-h-[90vh] overflow-y-auto p-6">
                        <div className="flex justify-between items-start mb-4">
                            <div>
                                <h2 className="text-2xl font-bold text-gray-900 dark:text-white">
                                    Changes in Version {selectedVersion.version}
                                </h2>
                                <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                                    {new Date(selectedVersion.createdAt).toLocaleString()} • {selectedVersion.createdBy}
                                </p>
                            </div>
                            <button
                                onClick={() => {
                                    setShowDiff(false);
                                    setSelectedVersion(null);
                                }}
                                className="text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
                            >
                                ✕
                            </button>
                        </div>

                        <div className="space-y-4">
                            {(() => {
                                const currentIdx = versions.findIndex((v) => v.id === selectedVersion.id);
                                const previousVersion = versions[currentIdx + 1];

                                if (!previousVersion) {
                                    return (
                                        <p className="text-gray-600 dark:text-gray-400">
                                            No previous version to compare
                                        </p>
                                    );
                                }

                                const diff = renderDiff(previousVersion.snapshot, selectedVersion.snapshot);

                                if (diff.length === 0) {
                                    return (
                                        <p className="text-gray-600 dark:text-gray-400">No changes detected</p>
                                    );
                                }

                                return diff;
                            })()}
                        </div>

                        <div className="mt-6 flex gap-2 justify-end">
                            <Button
                                variant="outline"
                                onClick={() => {
                                    setShowDiff(false);
                                    setSelectedVersion(null);
                                }}
                            >
                                Close
                            </Button>
                            <Button
                                onClick={() => {
                                    setShowDiff(false);
                                    handleRestore(selectedVersion);
                                }}
                                disabled={restoreMutation.isPending}
                            >
                                {restoreMutation.isPending ? 'Restoring...' : 'Restore This Version'}
                            </Button>
                        </div>
                    </Card>
                </div>
            )}
        </div>
    );
}
