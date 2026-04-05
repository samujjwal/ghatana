/**
 * Generic Artifact Detail Panel
 * 
 * Reusable panel for viewing and editing any lifecycle artifact.
 * Adapts to artifact kind with appropriate form fields.
 * 
 * @doc.type component
 * @doc.purpose Generic artifact viewer/editor
 * @doc.layer product
 * @doc.pattern Generic Component
 */

import React, { useState } from 'react';
import { X as Close, Pencil as Edit, Save, Trash2 as Delete, History } from 'lucide-react';
import type { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';
import { LIFECYCLE_ARTIFACT_CATALOG } from '@/shared/types/lifecycle-artifacts';

export interface ArtifactDetailPanelProps {
    artifactId: string;
    kind: LifecycleArtifactKind;
    title: string;
    status: 'draft' | 'complete' | 'validated' | 'archived';
    payload: Record<string, unknown>;
    createdAt: string;
    updatedAt: string;
    onClose: () => void;
    onSave?: (payload: Record<string, unknown>) => void;
    onDelete?: () => void;
}

export const ArtifactDetailPanel: React.FC<ArtifactDetailPanelProps> = ({
    artifactId,
    kind,
    title,
    status,
    payload,
    createdAt,
    updatedAt,
    onClose,
    onSave,
    onDelete,
}) => {
    const [isEditing, setIsEditing] = useState(false);
    const [editedPayload, setEditedPayload] = useState(payload);

    const metadata = LIFECYCLE_ARTIFACT_CATALOG[kind];

    const handleSave = () => {
        onSave?.(editedPayload);
        setIsEditing(false);
    };

    const formatDate = (dateStr: string) => {
        return new Date(dateStr).toLocaleString();
    };

    const renderField = (_key: string, value: unknown): React.ReactNode => {
        if (Array.isArray(value)) {
            return (
                <ul className="list-disc list-inside space-y-1">
                    {value.map((item, idx) => (
                        <li key={idx} className="text-sm text-text-primary">
                            {typeof item === 'object' ? JSON.stringify(item) : String(item)}
                        </li>
                    ))}
                </ul>
            );
        }

        if (typeof value === 'object' && value !== null) {
            return (
                <div className="pl-4 space-y-2 border-l-2 border-grey-200">
                    {Object.entries(value).map(([k, v]) => (
                        <div key={k}>
                            <div className="text-xs font-medium text-text-secondary capitalize">
                                {k.replace(/_/g, ' ')}
                            </div>
                            {renderField(k, v)}
                        </div>
                    ))}
                </div>
            );
        }

        if (typeof value === 'boolean') {
            return <span className="text-sm">{value ? '✓ Yes' : '✗ No'}</span>;
        }

        return <p className="text-sm text-text-primary whitespace-pre-wrap">{String(value)}</p>;
    };

    const renderEditableField = (key: string, value: unknown): React.ReactNode => {
        if (Array.isArray(value)) {
            return (
                <textarea
                    value={JSON.stringify(value, null, 2)}
                    onChange={(e) => {
                        try {
                            const parsed = JSON.parse(e.target.value);
                            setEditedPayload({ ...editedPayload, [key]: parsed });
                        } catch {
                            // Invalid JSON, ignore
                        }
                    }}
                    className="w-full px-3 py-2 text-sm border border-divider rounded-md focus:outline-none focus:ring-2 focus:ring-primary-600 font-mono"
                    rows={5}
                />
            );
        }

        if (typeof value === 'string' && value.length > 100) {
            return (
                <textarea
                    value={value}
                    onChange={(e) => setEditedPayload({ ...editedPayload, [key]: e.target.value })}
                    className="w-full px-3 py-2 text-sm border border-divider rounded-md focus:outline-none focus:ring-2 focus:ring-primary-600"
                    rows={4}
                />
            );
        }

        if (typeof value === 'boolean') {
            return (
                <label className="flex items-center gap-2 cursor-pointer">
                    <input
                        type="checkbox"
                        checked={value}
                        onChange={(e) => setEditedPayload({ ...editedPayload, [key]: e.target.checked })}
                        className="w-4 h-4 text-primary-600 rounded"
                    />
                    <span className="text-sm text-text-primary">Enabled</span>
                </label>
            );
        }

        return (
            <input
                type="text"
                value={String(value)}
                onChange={(e) => setEditedPayload({ ...editedPayload, [key]: e.target.value })}
                className="w-full px-3 py-2 text-sm border border-divider rounded-md focus:outline-none focus:ring-2 focus:ring-primary-600"
            />
        );
    };

    const getStatusColor = () => {
        switch (status) {
            case 'complete': return 'bg-success-color text-white';
            case 'validated': return 'bg-blue-600 text-white';
            case 'archived': return 'bg-grey-400 text-white';
            default: return 'bg-warning-color text-white';
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
            <div className="w-full max-w-3xl max-h-[90vh] flex flex-col bg-bg-paper rounded-lg shadow-xl">
                {/* Header */}
                <div className="flex items-start justify-between p-4 border-b border-divider">
                    <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-2">
                            <h2 className="text-lg font-semibold text-text-primary truncate">{title}</h2>
                            <span className={`text-xs px-2 py-1 rounded-full font-medium ${getStatusColor()}`}>
                                {status}
                            </span>
                        </div>
                        <div className="flex items-center gap-4 text-xs text-text-secondary">
                            <span>{metadata.label}</span>
                            <span>•</span>
                            <span className="capitalize">{metadata.phase.toLowerCase()} phase</span>
                            <span>•</span>
                            <span>ID: {artifactId.slice(0, 8)}</span>
                        </div>
                    </div>
                    <button
                        onClick={onClose}
                        className="p-2 hover:bg-grey-100 rounded-md transition-colors"
                    >
                        <Close className="w-5 h-5 text-text-secondary" />
                    </button>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-2 px-4 py-2 border-b border-divider bg-bg-default">
                    {isEditing ? (
                        <>
                            <button
                                onClick={handleSave}
                                className="flex items-center gap-1 px-3 py-1.5 text-sm bg-primary-600 text-white rounded-md hover:bg-primary-700 transition-colors"
                            >
                                <Save className="w-4 h-4" />
                                Save
                            </button>
                            <button
                                onClick={() => {
                                    setEditedPayload(payload);
                                    setIsEditing(false);
                                }}
                                className="px-3 py-1.5 text-sm text-text-secondary hover:bg-grey-100 rounded-md transition-colors"
                            >
                                Cancel
                            </button>
                        </>
                    ) : (
                        <>
                            <button
                                onClick={() => setIsEditing(true)}
                                className="flex items-center gap-1 px-3 py-1.5 text-sm text-primary-600 hover:bg-primary-50 rounded-md transition-colors"
                            >
                                <Edit className="w-4 h-4" />
                                Edit
                            </button>
                            <button className="flex items-center gap-1 px-3 py-1.5 text-sm text-text-secondary hover:bg-grey-100 rounded-md transition-colors">
                                <History className="w-4 h-4" />
                                History
                            </button>
                            {onDelete && (
                                <button
                                    onClick={onDelete}
                                    className="flex items-center gap-1 px-3 py-1.5 text-sm text-error-color hover:bg-red-50 rounded-md transition-colors ml-auto"
                                >
                                    <Delete className="w-4 h-4" />
                                    Delete
                                </button>
                            )}
                        </>
                    )}
                </div>

                {/* Content */}
                <div className="flex-1 overflow-auto p-6 space-y-6">
                    {/* Metadata */}
                    <div className="grid grid-cols-2 gap-4 pb-6 border-b border-divider">
                        <div>
                            <div className="text-xs font-medium text-text-secondary mb-1">Created</div>
                            <div className="text-sm text-text-primary">{formatDate(createdAt)}</div>
                        </div>
                        <div>
                            <div className="text-xs font-medium text-text-secondary mb-1">Last Updated</div>
                            <div className="text-sm text-text-primary">{formatDate(updatedAt)}</div>
                        </div>
                    </div>

                    {/* Payload Fields */}
                    <div className="space-y-4">
                        {Object.entries(isEditing ? editedPayload : payload).map(([key, value]) => (
                            <div key={key}>
                                <label className="block text-sm font-medium text-text-secondary mb-2 capitalize">
                                    {key.replace(/_/g, ' ')}
                                </label>
                                {isEditing ? renderEditableField(key, value) : renderField(key, value)}
                            </div>
                        ))}

                        {Object.keys(payload).length === 0 && (
                            <div className="text-center py-8 text-sm text-text-secondary">
                                No data available
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};
