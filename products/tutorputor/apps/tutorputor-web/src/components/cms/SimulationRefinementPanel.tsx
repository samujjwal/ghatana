/**
 * AI-Powered Simulation Refinement Panel
 * 
 * @doc.type component
 * @doc.purpose AI-assisted manifest refinement with suggestions and validation
 * @doc.layer product
 * @doc.pattern Editor
 */

import { useState, useCallback } from "react";
import type { SimulationManifest } from "@ghatana/tutorputor-contracts/v1/simulation/types";

interface RefinementSuggestion {
    id: string;
    type: 'improvement' | 'warning' | 'error';
    category: 'structure' | 'pedagogy' | 'accessibility' | 'performance';
    title: string;
    description: string;
    autoFixable: boolean;
    priority: 'high' | 'medium' | 'low';
}

interface ValidationResult {
    valid: boolean;
    errors: Array<{ path: string; message: string }>;
    warnings: Array<{ path: string; message: string }>;
}

interface SimulationRefinementPanelProps {
    manifest: SimulationManifest;
    onRefine: (refinement: string) => Promise<void>;
    onApplySuggestion: (suggestionId: string) => Promise<void>;
    onValidate: () => Promise<ValidationResult>;
}

export function SimulationRefinementPanel({
    manifest,
    onRefine,
    onApplySuggestion,
    onValidate
}: SimulationRefinementPanelProps) {
    const [refinementText, setRefinementText] = useState("");
    const [isRefining, setIsRefining] = useState(false);
    const [suggestions, setSuggestions] = useState<RefinementSuggestion[]>([]);
    const [validation, setValidation] = useState<ValidationResult | null>(null);
    const [activeTab, setActiveTab] = useState<'refine' | 'suggestions' | 'validation'>('refine');

    const handleRefine = useCallback(async () => {
        if (!refinementText.trim()) return;

        setIsRefining(true);
        try {
            await onRefine(refinementText);
            setRefinementText("");

            // Generate new suggestions after refinement
            const newSuggestions = await generateSuggestions(manifest);
            setSuggestions(newSuggestions);
        } catch (error) {
            console.error("Refinement failed:", error);
        } finally {
            setIsRefining(false);
        }
    }, [refinementText, manifest, onRefine]);

    const handleValidate = useCallback(async () => {
        const result = await onValidate();
        setValidation(result);
        setActiveTab('validation');
    }, [onValidate]);

    const handleApplySuggestion = useCallback(async (suggestionId: string) => {
        try {
            await onApplySuggestion(suggestionId);
            setSuggestions(prev => prev.filter(s => s.id !== suggestionId));
        } catch (error) {
            console.error("Failed to apply suggestion:", error);
        }
    }, [onApplySuggestion]);

    return (
        <div className="simulation-refinement-panel border rounded-lg bg-white shadow-sm">
            {/* Header */}
            <div className="border-b p-4">
                <h3 className="text-lg font-semibold">AI-Powered Refinement</h3>
                <p className="text-sm text-gray-600 mt-1">
                    Improve your simulation with AI suggestions and validation
                </p>
            </div>

            {/* Tabs */}
            <div className="border-b">
                <div className="flex">
                    <button
                        className={`px-4 py-2 font-medium ${activeTab === 'refine'
                                ? 'border-b-2 border-blue-500 text-blue-600'
                                : 'text-gray-600 hover:text-gray-900'
                            }`}
                        onClick={() => setActiveTab('refine')}
                    >
                        Refine
                    </button>
                    <button
                        className={`px-4 py-2 font-medium ${activeTab === 'suggestions'
                                ? 'border-b-2 border-blue-500 text-blue-600'
                                : 'text-gray-600 hover:text-gray-900'
                            }`}
                        onClick={() => setActiveTab('suggestions')}
                    >
                        Suggestions
                        {suggestions.length > 0 && (
                            <span className="ml-2 px-2 py-0.5 text-xs bg-blue-100 text-blue-600 rounded-full">
                                {suggestions.length}
                            </span>
                        )}
                    </button>
                    <button
                        className={`px-4 py-2 font-medium ${activeTab === 'validation'
                                ? 'border-b-2 border-blue-500 text-blue-600'
                                : 'text-gray-600 hover:text-gray-900'
                            }`}
                        onClick={() => setActiveTab('validation')}
                    >
                        Validation
                        {validation && !validation.valid && (
                            <span className="ml-2 px-2 py-0.5 text-xs bg-red-100 text-red-600 rounded-full">
                                {validation.errors.length}
                            </span>
                        )}
                    </button>
                </div>
            </div>

            {/* Content */}
            <div className="p-4">
                {activeTab === 'refine' && (
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Describe your refinement
                            </label>
                            <textarea
                                className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                rows={4}
                                placeholder="E.g., 'Add more intermediate steps', 'Slow down the animation', 'Add accessibility features'"
                                value={refinementText}
                                onChange={(e) => setRefinementText(e.target.value)}
                                disabled={isRefining}
                            />
                        </div>

                        <div className="flex items-center justify-between">
                            <div className="text-sm text-gray-600">
                                <span className="font-medium">Tip:</span> Be specific about what you want to improve
                            </div>
                            <button
                                className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                                onClick={handleRefine}
                                disabled={!refinementText.trim() || isRefining}
                            >
                                {isRefining ? 'Refining...' : 'Apply Refinement'}
                            </button>
                        </div>

                        {/* Quick Actions */}
                        <div className="border-t pt-4">
                            <h4 className="text-sm font-medium text-gray-700 mb-2">Quick Actions</h4>
                            <div className="grid grid-cols-2 gap-2">
                                <button
                                    className="px-3 py-2 text-sm border rounded-md hover:bg-gray-50"
                                    onClick={() => setRefinementText("Add more intermediate steps for better understanding")}
                                >
                                    Add Steps
                                </button>
                                <button
                                    className="px-3 py-2 text-sm border rounded-md hover:bg-gray-50"
                                    onClick={() => setRefinementText("Improve accessibility with alt text and narration")}
                                >
                                    Accessibility
                                </button>
                                <button
                                    className="px-3 py-2 text-sm border rounded-md hover:bg-gray-50"
                                    onClick={() => setRefinementText("Add ECD metadata for assessment integration")}
                                >
                                    Add ECD
                                </button>
                                <button
                                    className="px-3 py-2 text-sm border rounded-md hover:bg-gray-50"
                                    onClick={() => setRefinementText("Optimize performance by reducing entity count")}
                                >
                                    Optimize
                                </button>
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === 'suggestions' && (
                    <div className="space-y-3">
                        {suggestions.length === 0 ? (
                            <div className="text-center py-8 text-gray-500">
                                <svg className="mx-auto h-12 w-12 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                                </svg>
                                <p className="mt-2">No suggestions available</p>
                                <p className="text-sm">Your simulation looks good!</p>
                            </div>
                        ) : (
                            suggestions.map((suggestion) => (
                                <div
                                    key={suggestion.id}
                                    className={`border rounded-lg p-4 ${suggestion.type === 'error'
                                            ? 'border-red-200 bg-red-50'
                                            : suggestion.type === 'warning'
                                                ? 'border-yellow-200 bg-yellow-50'
                                                : 'border-blue-200 bg-blue-50'
                                        }`}
                                >
                                    <div className="flex items-start justify-between">
                                        <div className="flex-1">
                                            <div className="flex items-center gap-2">
                                                <span
                                                    className={`px-2 py-0.5 text-xs font-medium rounded ${suggestion.type === 'error'
                                                            ? 'bg-red-100 text-red-700'
                                                            : suggestion.type === 'warning'
                                                                ? 'bg-yellow-100 text-yellow-700'
                                                                : 'bg-blue-100 text-blue-700'
                                                        }`}
                                                >
                                                    {suggestion.type}
                                                </span>
                                                <span className="text-xs text-gray-600">{suggestion.category}</span>
                                                <span
                                                    className={`text-xs ${suggestion.priority === 'high'
                                                            ? 'text-red-600'
                                                            : suggestion.priority === 'medium'
                                                                ? 'text-yellow-600'
                                                                : 'text-gray-600'
                                                        }`}
                                                >
                                                    {suggestion.priority} priority
                                                </span>
                                            </div>
                                            <h4 className="font-medium mt-2">{suggestion.title}</h4>
                                            <p className="text-sm text-gray-700 mt-1">{suggestion.description}</p>
                                        </div>
                                        {suggestion.autoFixable && (
                                            <button
                                                className="ml-4 px-3 py-1 text-sm bg-white border rounded-md hover:bg-gray-50"
                                                onClick={() => handleApplySuggestion(suggestion.id)}
                                            >
                                                Auto-fix
                                            </button>
                                        )}
                                    </div>
                                </div>
                            ))
                        )}

                        <button
                            className="w-full px-4 py-2 text-sm border-2 border-dashed rounded-md hover:bg-gray-50"
                            onClick={handleValidate}
                        >
                            Run Validation to Generate Suggestions
                        </button>
                    </div>
                )}

                {activeTab === 'validation' && (
                    <div className="space-y-4">
                        {!validation ? (
                            <div className="text-center py-8">
                                <button
                                    className="px-6 py-3 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                                    onClick={handleValidate}
                                >
                                    Validate Simulation
                                </button>
                            </div>
                        ) : (
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
                                        {validation.errors.length} errors, {validation.warnings.length} warnings
                                    </p>
                                </div>

                                {/* Errors */}
                                {validation.errors.length > 0 && (
                                    <div>
                                        <h4 className="font-medium text-red-800 mb-2">Errors</h4>
                                        <div className="space-y-2">
                                            {validation.errors.map((error, idx) => (
                                                <div key={idx} className="p-3 bg-red-50 border border-red-200 rounded-md">
                                                    <div className="font-mono text-xs text-red-600">{error.path}</div>
                                                    <div className="text-sm text-red-800 mt-1">{error.message}</div>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                {/* Warnings */}
                                {validation.warnings.length > 0 && (
                                    <div>
                                        <h4 className="font-medium text-yellow-800 mb-2">Warnings</h4>
                                        <div className="space-y-2">
                                            {validation.warnings.map((warning, idx) => (
                                                <div key={idx} className="p-3 bg-yellow-50 border border-yellow-200 rounded-md">
                                                    <div className="font-mono text-xs text-yellow-600">{warning.path}</div>
                                                    <div className="text-sm text-yellow-800 mt-1">{warning.message}</div>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                <button
                                    className="w-full px-4 py-2 text-sm border rounded-md hover:bg-gray-50"
                                    onClick={handleValidate}
                                >
                                    Re-validate
                                </button>
                            </>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}

// Helper function to generate AI suggestions
async function generateSuggestions(manifest: SimulationManifest): Promise<RefinementSuggestion[]> {
    const suggestions: RefinementSuggestion[] = [];

    // Check for missing accessibility
    if (!manifest.accessibility) {
        suggestions.push({
            id: 'acc-1',
            type: 'improvement',
            category: 'accessibility',
            title: 'Add Accessibility Features',
            description: 'Include alt text, screen reader narration, and reduced motion options',
            autoFixable: true,
            priority: 'high'
        });
    }

    // Check for missing ECD metadata
    if (!manifest.ecd) {
        suggestions.push({
            id: 'ecd-1',
            type: 'improvement',
            category: 'pedagogy',
            title: 'Add ECD Metadata',
            description: 'Define learning claims, evidence sources, and tasks for assessment integration',
            autoFixable: true,
            priority: 'medium'
        });
    }

    // Check for performance issues
    if ((manifest.initialEntities?.length || 0) > 50) {
        suggestions.push({
            id: 'perf-1',
            type: 'warning',
            category: 'performance',
            title: 'High Entity Count',
            description: `${manifest.initialEntities?.length} entities may cause performance issues. Consider reducing to under 50.`,
            autoFixable: false,
            priority: 'medium'
        });
    }

    // Check for missing safety constraints
    if (!manifest.safety) {
        suggestions.push({
            id: 'safety-1',
            type: 'warning',
            category: 'structure',
            title: 'Add Safety Constraints',
            description: 'Define execution limits and parameter bounds for safe runtime execution',
            autoFixable: true,
            priority: 'high'
        });
    }

    return suggestions;
}
