/**
 * AIResponseCard Component
 *
 * Displays AI response with confirm/customize/reject actions.
 * Shows project details, confidence score, and suggested tech stack.
 *
 * @doc.type component
 * @doc.purpose Display AI response for confirmation
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { Sparkles as AutoAwesome, Check, Pencil as Edit, X as Close, Code, HardDrive as Storage, Timer, TrendingUp, CheckCircle } from 'lucide-react';
import { useState } from 'react';
import type { AIResponse } from '../../hooks/useAICommand';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { useTranslation } from '@ghatana/i18n';

// ============================================================================
// Types
// ============================================================================

export interface AIResponseCardProps {
    response: AIResponse;
    onConfirm: () => void;
    onCustomize?: () => void;
    onReject: () => void;
    onUpdate?: (updates: Partial<AIResponse['details']>) => void;
    isConfirming?: boolean;
    className?: string;
}

// ============================================================================
// Component
// ============================================================================

export function AIResponseCard({
    response,
    onConfirm,
    onCustomize,
    onReject,
    onUpdate,
    isConfirming = false,
    className = '',
}: AIResponseCardProps) {
    const { t } = useTranslation('common');
    const { type, summary, details, confidence } = response;
    const [isEditing, setIsEditing] = useState(false);
    const [editedName, setEditedName] = useState(details.name || '');
    const [editedFeatures, setEditedFeatures] = useState<string[]>(details.features || []);
    const [newFeature, setNewFeature] = useState('');

    // Helper to get confidence color
    const getConfidenceColor = () => {
        if (confidence >= 0.8) return 'text-success-color dark:text-success-color';
        if (confidence >= 0.6) return 'text-warning-color dark:text-warning-color';
        return 'text-warning-color dark:text-warning-color';
    };

    // Handle saving edits
    const handleSaveEdits = () => {
        if (onUpdate) {
            onUpdate({
                name: editedName,
                features: editedFeatures,
            });
        }
        setIsEditing(false);
    };

    // Handle adding a new feature
    const handleAddFeature = () => {
        if (newFeature.trim()) {
            setEditedFeatures([...editedFeatures, newFeature.trim()]);
            setNewFeature('');
        }
    };

    // Handle removing a feature
    const handleRemoveFeature = (index: number) => {
        setEditedFeatures(editedFeatures.filter((_, i) => i !== index));
    };

    const cardClassName = `bg-bg-paper border border-primary-200 dark:border-primary-800 rounded-xl shadow-lg overflow-hidden animate-in fade-in slide-in-from-bottom-4 duration-300 ${className}`;

    return (
        <div className={cardClassName}>
            {/* Header */}
            <div className="bg-primary-50 dark:bg-primary-900/30 px-5 py-3 flex items-center justify-between">
                <div className="flex items-center gap-2 flex-1">
                    <AutoAwesome className="w-5 h-5 text-primary-600 dark:text-primary-400" />
                    {isEditing ? (
                        <Input
                            type="text"
                            value={editedName}
                            onChange={(e) => setEditedName(e.target.value)}
                            className="flex-1 px-3 py-1 text-sm font-semibold bg-white dark:bg-grey-800 border border-divider rounded focus:outline-none focus:ring-2 focus:ring-primary-500"
                            placeholder="Project name"
                            fullWidth
                        />
                    ) : (
                        <span className="font-semibold text-text-primary">{summary}</span>
                    )}
                </div>
                <div className="flex items-center gap-2">
                    <span className={`text-sm font-medium ${getConfidenceColor()}`}>
                        {Math.round(confidence * 100)}% confident
                    </span>
                    {!isEditing && type === 'create' && onUpdate && (
                        <Button
                            type="button"
                            onClick={() => setIsEditing(true)}
                            className="p-1 min-h-0 hover:bg-primary-100 dark:hover:bg-primary-800 rounded transition-colors"
                            variant="ghost"
                            size="sm"
                            title="Edit details"
                        >
                            <Edit className="w-4 h-4 text-primary-600 dark:text-primary-400" />
                        </Button>
                    )}
                </div>
            </div>

            {/* Content */}
            <div className="p-5 space-y-4">
                {/* Project Details */}
                {type === 'create' && (
                    <>
                        {/* Project Type */}
                        {details.projectType && (
                            <div className="flex items-center gap-2 text-text-secondary">
                                <Code className="w-4 h-4" />
                                <span className="font-medium text-text-primary">Type:</span>
                                <span className="capitalize">{details.projectType.replace('_', ' ').toLowerCase()}</span>
                            </div>
                        )}

                        {/* Features */}
                        {((details.features && details.features.length > 0) || isEditing) && (
                            <div className="space-y-2">
                                <div className="flex items-center justify-between">
                                    <div className="flex items-center gap-2 text-text-secondary text-sm font-medium">
                                        <AutoAwesome className="w-4 h-4" />
                                        <span>Features ({isEditing ? editedFeatures.length : details.features?.length || 0})</span>
                                    </div>
                                </div>
                                <div className="flex flex-wrap gap-2 pl-6">
                                    {(isEditing ? editedFeatures : details.features || []).map((feature, index) => (
                                        <span
                                            key={index}
                                            className="px-2 py-1 text-xs font-medium rounded-full bg-primary-50 dark:bg-primary-900/30 text-primary-700 dark:text-primary-300 border border-primary-100 dark:border-primary-800 flex items-center gap-1"
                                        >
                                            {feature}
                                            {isEditing && (
                                                <Button
                                                    type="button"
                                                    onClick={() => handleRemoveFeature(index)}
                                                    className="p-0 min-h-0 hover:text-error-color"
                                                    variant="ghost"
                                                    size="sm"
                                                    aria-label={`Remove ${feature}`}
                                                >
                                                    <Close className="w-3 h-3" />
                                                </Button>
                                            )}
                                        </span>
                                    ))}
                                </div>
                                {isEditing && (
                                    <div className="pl-6 flex gap-2">
                                        <Input
                                            type="text"
                                            value={newFeature}
                                            onChange={(e) => setNewFeature(e.target.value)}
                                            className="flex-1 px-3 py-1 text-sm bg-white dark:bg-grey-800 border border-divider rounded focus:outline-none focus:ring-2 focus:ring-primary-500"
                                            placeholder="Add a feature"
                                            fullWidth
                                        />
                                        <Button
                                            type="button"
                                            onClick={handleAddFeature}
                                            className="px-3 py-1 text-sm font-medium bg-primary-600 text-white rounded hover:bg-primary-700"
                                            size="sm"
                                        >
                                            Add
                                        </Button>
                                    </div>
                                )}
                            </div>
                        )}

                        {/* Estimated Time */}
                        {details.estimatedTime && (
                            <div className="flex items-center gap-2 text-text-secondary">
                                <Timer className="w-4 h-4" />
                                <span>{details.estimatedTime}</span>
                            </div>
                        )}

                        {/* Tech Stack */}
                        {details.techStack && details.techStack.length > 0 && (
                            <div className="space-y-2">
                                <div className="flex items-center gap-2 text-text-secondary text-sm font-medium">
                                    <Storage className="w-4 h-4" />
                                    <span>Tech Stack</span>
                                </div>
                                <div className="flex flex-wrap gap-2 pl-6">
                                    {details.techStack.map((tech) => (
                                        <span
                                            key={tech}
                                            className="px-2 py-1 text-xs font-medium rounded bg-grey-100 dark:bg-grey-800 text-text-secondary"
                                        >
                                            {tech}
                                        </span>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Next Actions */}
                        {details.nextActions && details.nextActions.length > 0 && (
                            <div className="space-y-2">
                                <div className="flex items-center gap-2 text-text-secondary text-sm font-medium">
                                    <TrendingUp className="w-4 h-4" />
                                    <span>Suggested Next Steps</span>
                                </div>
                                <ul className="space-y-2 pl-6 text-text-secondary text-sm">
                                    {details.nextActions.map((action, i) => (
                                        <li key={i} className="flex items-start gap-2">
                                            <CheckCircle className="w-4 h-4 text-primary-500 flex-shrink-0 mt-0.5" />
                                            <span>{action}</span>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}
                    </>
                )}
            </div>

            {/* Actions */}
            <div className="px-5 py-4 bg-grey-50 dark:bg-grey-900/50 flex items-center justify-end gap-3">
                {isEditing ? (
                    <>
                        <Button
                            type="button"
                            onClick={() => {
                                setIsEditing(false);
                                setEditedName(details.name || '');
                                setEditedFeatures(details.features || []);
                                setNewFeature('');
                            }}
                            variant="ghost"
                        >
                            Cancel
                        </Button>
                        <Button
                            type="button"
                            onClick={handleSaveEdits}
                            className="px-5 py-2 text-sm font-medium bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors flex items-center gap-2"
                        >
                            <Check className="w-4 h-4" />
                            Save Changes
                        </Button>
                    </>
                ) : (
                    <>
                        <Button
                            type="button"
                            onClick={onReject}
                            disabled={isConfirming}
                            className="px-4 py-2 text-sm font-medium text-text-secondary hover:text-text-primary transition-colors disabled:opacity-50"
                            variant="ghost"
                        >
                            <Close className="w-4 h-4 mr-1 inline" />
                            Cancel
                        </Button>

                        {onCustomize && type === 'create' && (
                            <Button
                                type="button"
                                onClick={onCustomize}
                                disabled={isConfirming}
                                className="px-4 py-2 text-sm font-medium text-text-primary border border-divider rounded-lg hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors disabled:opacity-50"
                                variant="outline"
                            >
                                <Edit className="w-4 h-4 mr-1 inline" />
                                Customize
                            </Button>
                        )}

                        <Button
                            type="button"
                            onClick={onConfirm}
                            disabled={isConfirming}
                            className="px-5 py-2 text-sm font-medium bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50 flex items-center gap-2"
                        >
                            {isConfirming ? (
                                <>
                                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                                    Creating...
                                </>
                            ) : (
                                <>
                                    <Check className="w-4 h-4" />
                                    {type === 'create' ? 'Create Project' : 'Confirm'}
                                </>
                            )}
                        </Button>
                    </>
                )}
            </div>
        </div>
    );
}

export default AIResponseCard;
