import { createLogger } from '../utils/logger.js';
const logger = createLogger('ReviewPublishWorkflow');

/**
 * Review & Publish Workflow
 * 
 * @doc.type component
 * @doc.purpose Phase 5 of authoring flow - Validation, review, and publication
 * @doc.layer product
 * @doc.pattern Workflow
 */

import { useState, useEffect } from "react";
import { SimulationRefinementPanel } from "../cms/SimulationRefinementPanel";

interface ReviewPublishWorkflowProps {
    manifest: any;
    onPublish: (manifest: any) => void;
    onBack: () => void;
}

export function ReviewPublishWorkflow({ manifest, onPublish, onBack }: ReviewPublishWorkflowProps) {
    const [activeTab, setActiveTab] = useState<'preview' | 'validate' | 'refine' | 'publish'>('preview');
    const [validation, setValidation] = useState<any>(null);
    const [isValidating, setIsValidating] = useState(false);
    const [isPublishing, setIsPublishing] = useState(false);

    useEffect(() => {
        // Auto-validate on mount
        handleValidate();
    }, []);

    const handleValidate = async () => {
        setIsValidating(true);
        try {
            const response = await fetch(`/api/v1/simulations/${manifest.id}/validate`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ manifest })
            });
            const result = await response.json();
            setValidation(result);
        } catch (error) {
            logger.error({}, 'Validation failed:', error);
        } finally {
            setIsValidating(false);
        }
    };

    const handlePublish = async () => {
        setIsPublishing(true);
        try {
            await onPublish(manifest);
        } catch (error) {
            logger.error({}, 'Publication failed:', error);
        } finally {
            setIsPublishing(false);
        }
    };

    const handleRefine = async (refinement: string) => {
        // Call refinement API
        logger.info({}, 'Refining with:', refinement);
    };

    const handleApplySuggestion = async (suggestionId: string) => {
        // Apply auto-fix suggestion
        logger.info({}, 'Applying suggestion:', suggestionId);
    };

    return (
        <div className="review-publish-workflow max-w-6xl mx-auto p-6">
            <div className="mb-8">
                <h1 className="text-3xl font-bold mb-2">Review & Publish</h1>
                <p className="text-gray-600">Validate, refine, and publish your simulation</p>
            </div>

            {/* Tabs */}
            <div className="border-b mb-6">
                <div className="flex gap-4">
                    <button
                        onClick={() => setActiveTab('preview')}
                        className={`px-4 py-2 font-medium border-b-2 transition-colors ${activeTab === 'preview'
                                ? 'border-blue-500 text-blue-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                    >
                        Preview
                    </button>
                    <button
                        onClick={() => setActiveTab('validate')}
                        className={`px-4 py-2 font-medium border-b-2 transition-colors ${activeTab === 'validate'
                                ? 'border-blue-500 text-blue-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                    >
                        Validation
                        {validation && !validation.valid && (
                            <span className="ml-2 px-2 py-0.5 text-xs bg-red-100 text-red-600 rounded-full">
                                {validation.errors?.length || 0}
                            </span>
                        )}
                    </button>
                    <button
                        onClick={() => setActiveTab('refine')}
                        className={`px-4 py-2 font-medium border-b-2 transition-colors ${activeTab === 'refine'
                                ? 'border-blue-500 text-blue-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                    >
                        AI Refinement
                    </button>
                    <button
                        onClick={() => setActiveTab('publish')}
                        className={`px-4 py-2 font-medium border-b-2 transition-colors ${activeTab === 'publish'
                                ? 'border-blue-500 text-blue-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                    >
                        Publish
                    </button>
                </div>
            </div>

            {/* Preview Tab */}
            {activeTab === 'preview' && (
                <div className="space-y-6">
                    <div className="border rounded-lg bg-gray-50 aspect-video flex items-center justify-center">
                        <div className="text-center text-gray-500">
                            <svg className="w-16 h-16 mx-auto mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                            <p className="font-medium">Simulation Preview</p>
                            <p className="text-sm">Interactive preview will appear here</p>
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-6">
                        <div className="border rounded-lg p-6">
                            <h3 className="font-semibold mb-4">Simulation Details</h3>
                            <dl className="space-y-3">
                                <div>
                                    <dt className="text-sm text-gray-600">Title</dt>
                                    <dd className="font-medium">{manifest.title}</dd>
                                </div>
                                <div>
                                    <dt className="text-sm text-gray-600">Domain</dt>
                                    <dd className="font-medium">{manifest.domain}</dd>
                                </div>
                                <div>
                                    <dt className="text-sm text-gray-600">Entities</dt>
                                    <dd className="font-medium">{manifest.initialEntities?.length || 0}</dd>
                                </div>
                                <div>
                                    <dt className="text-sm text-gray-600">Steps</dt>
                                    <dd className="font-medium">{manifest.steps?.length || 0}</dd>
                                </div>
                            </dl>
                        </div>

                        <div className="border rounded-lg p-6">
                            <h3 className="font-semibold mb-4">Features</h3>
                            <div className="space-y-2">
                                <div className="flex items-center gap-2">
                                    <svg className={`w-5 h-5 ${manifest.accessibility?.screenReaderNarration ? 'text-green-600' : 'text-gray-400'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                                    </svg>
                                    <span className="text-sm">Screen Reader Support</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <svg className={`w-5 h-5 ${manifest.accessibility?.reducedMotion ? 'text-green-600' : 'text-gray-400'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                                    </svg>
                                    <span className="text-sm">Reduced Motion</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <svg className={`w-5 h-5 ${manifest.accessibility?.highContrast ? 'text-green-600' : 'text-gray-400'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                                    </svg>
                                    <span className="text-sm">High Contrast Mode</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <svg className={`w-5 h-5 ${manifest.ecd ? 'text-green-600' : 'text-gray-400'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                                    </svg>
                                    <span className="text-sm">ECD Assessment</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Validation Tab */}
            {activeTab === 'validate' && (
                <div className="space-y-6">
                    {isValidating ? (
                        <div className="text-center py-12">
                            <svg className="animate-spin h-12 w-12 mx-auto mb-4 text-blue-600" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                            </svg>
                            <p className="text-gray-600">Validating simulation...</p>
                        </div>
                    ) : validation ? (
                        <>
                            {/* Summary */}
                            <div className={`p-4 rounded-lg ${validation.valid ? 'bg-green-50 border border-green-200' : 'bg-red-50 border border-red-200'
                                }`}>
                                <div className="flex items-center gap-2">
                                    {validation.valid ? (
                                        <svg className="h-5 w-5 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                                        </svg>
                                    ) : (
                                        <svg className="h-5 w-5 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                        </svg>
                                    )}
                                    <span className={`font-medium ${validation.valid ? 'text-green-800' : 'text-red-800'}`}>
                                        {validation.valid ? 'Validation Passed' : 'Validation Failed'}
                                    </span>
                                </div>
                                <p className="text-sm mt-1 text-gray-700">
                                    {validation.errors?.length || 0} errors, {validation.warnings?.length || 0} warnings
                                </p>
                            </div>

                            {/* Errors */}
                            {validation.errors?.length > 0 && (
                                <div>
                                    <h4 className="font-medium text-red-800 mb-2">Errors</h4>
                                    <div className="space-y-2">
                                        {validation.errors.map((error: any, idx: number) => (
                                            <div key={idx} className="p-3 bg-red-50 border border-red-200 rounded-md">
                                                <div className="font-mono text-xs text-red-600">{error.path}</div>
                                                <div className="text-sm text-red-800 mt-1">{error.message}</div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Warnings */}
                            {validation.warnings?.length > 0 && (
                                <div>
                                    <h4 className="font-medium text-yellow-800 mb-2">Warnings</h4>
                                    <div className="space-y-2">
                                        {validation.warnings.map((warning: any, idx: number) => (
                                            <div key={idx} className="p-3 bg-yellow-50 border border-yellow-200 rounded-md">
                                                <div className="font-mono text-xs text-yellow-600">{warning.path}</div>
                                                <div className="text-sm text-yellow-800 mt-1">{warning.message}</div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}

                            <button
                                onClick={handleValidate}
                                className="w-full px-4 py-2 border rounded-md hover:bg-gray-50"
                            >
                                Re-validate
                            </button>
                        </>
                    ) : null}
                </div>
            )}

            {/* AI Refinement Tab */}
            {activeTab === 'refine' && (
                <SimulationRefinementPanel
                    manifest={manifest}
                    onRefine={handleRefine}
                    onApplySuggestion={handleApplySuggestion}
                    onValidate={async () => {
                        await handleValidate();
                        return validation || { valid: false, errors: [], warnings: [] };
                    }}
                />
            )}

            {/* Publish Tab */}
            {activeTab === 'publish' && (
                <div className="space-y-6">
                    <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                        <h4 className="font-medium text-blue-900 mb-2">📢 Ready to Publish?</h4>
                        <p className="text-sm text-blue-800">
                            Your simulation will be submitted for review and published to the marketplace
                        </p>
                    </div>

                    <div className="border rounded-lg p-6">
                        <h3 className="font-semibold mb-4">Publication Checklist</h3>
                        <div className="space-y-3">
                            <label className="flex items-center gap-3">
                                <input type="checkbox" className="w-5 h-5" defaultChecked />
                                <span className="text-sm">Content is accurate and pedagogically sound</span>
                            </label>
                            <label className="flex items-center gap-3">
                                <input type="checkbox" className="w-5 h-5" defaultChecked />
                                <span className="text-sm">Accessibility features are enabled</span>
                            </label>
                            <label className="flex items-center gap-3">
                                <input type="checkbox" className="w-5 h-5" defaultChecked />
                                <span className="text-sm">Assessment is aligned with objectives</span>
                            </label>
                            <label className="flex items-center gap-3">
                                <input type="checkbox" className="w-5 h-5" defaultChecked />
                                <span className="text-sm">Simulation has been tested</span>
                            </label>
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Publication Notes (Optional)
                        </label>
                        <textarea
                            className="w-full px-4 py-3 border rounded-md"
                            rows={4}
                            placeholder="Add any notes for reviewers..."
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Tags
                        </label>
                        <input
                            type="text"
                            className="w-full px-4 py-2 border rounded-md"
                            placeholder="physics, mechanics, newton, interactive"
                        />
                    </div>
                </div>
            )}

            {/* Navigation */}
            <div className="flex justify-between items-center mt-8 pt-6 border-t">
                <button
                    onClick={onBack}
                    className="px-6 py-2 border rounded-md hover:bg-gray-50"
                >
                    Back
                </button>
                <div className="flex gap-3">
                    <button
                        className="px-6 py-2 border rounded-md hover:bg-gray-50"
                    >
                        Save Draft
                    </button>
                    <button
                        onClick={handlePublish}
                        disabled={isPublishing || (validation && !validation.valid)}
                        className="px-8 py-3 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {isPublishing ? 'Publishing...' : 'Publish Simulation'}
                    </button>
                </div>
            </div>
        </div>
    );
}
