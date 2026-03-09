import { createLogger } from '../utils/logger.js';
const logger = createLogger('ContentCreationWorkflow');

/**
 * Content Creation Workflow
 * 
 * @doc.type component
 * @doc.purpose Phase 2 of authoring flow - AI generation and manual design
 * @doc.layer product
 * @doc.pattern Workflow
 */

import { useState } from "react";

interface ContentCreationWorkflowProps {
    objectives: any;
    onComplete: (manifest: any) => void;
    onBack: () => void;
}

type CreationMethod = 'ai' | 'manual' | 'template';

export function ContentCreationWorkflow({ objectives, onComplete, onBack }: ContentCreationWorkflowProps) {
    const [method, setMethod] = useState<CreationMethod | null>(null);
    const [aiPrompt, setAiPrompt] = useState("");
    const [isGenerating, setIsGenerating] = useState(false);
    const [generatedManifest, setGeneratedManifest] = useState<any>(null);
    const [provider, setProvider] = useState("openai-primary");

    const providers = [
        { value: "openai-primary", label: "OpenAI GPT-4", cost: "$$", speed: "Fast" },
        { value: "anthropic-backup", label: "Anthropic Claude", cost: "$", speed: "Medium" },
        { value: "ollama-local", label: "Ollama (Local)", cost: "Free", speed: "Slow" }
    ];

    const handleAIGeneration = async () => {
        setIsGenerating(true);
        try {
            const response = await fetch('/api/v1/simulations/generate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    prompt: aiPrompt,
                    domain: objectives.domain,
                    constraints: {
                        gradeLevel: objectives.gradeLevel,
                        duration: objectives.duration,
                        objectives: objectives.objectives
                    },
                    provider
                })
            });

            const result = await response.json();
            setGeneratedManifest(result.manifest);
        } catch (error) {
            logger.error({}, 'Generation failed:', error);
        } finally {
            setIsGenerating(false);
        }
    };

    const handleTemplateSelection = (templateId: string) => {
        // Load template and customize
        logger.info({}, 'Loading template:', templateId);
    };

    if (!method) {
        return (
            <div className="content-creation-workflow max-w-4xl mx-auto p-6">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold mb-2">Choose Creation Method</h1>
                    <p className="text-gray-600">How would you like to create your simulation?</p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    {/* AI Generation */}
                    <button
                        onClick={() => setMethod('ai')}
                        className="p-6 border-2 rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-all text-left"
                    >
                        <div className="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center mb-4">
                            <svg className="w-6 h-6 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                            </svg>
                        </div>
                        <h3 className="font-semibold text-lg mb-2">AI Generation</h3>
                        <p className="text-sm text-gray-600 mb-4">
                            Describe your simulation in natural language and let AI create it for you
                        </p>
                        <div className="flex items-center gap-2 text-xs">
                            <span className="px-2 py-1 bg-green-100 text-green-700 rounded">Fastest</span>
                            <span className="px-2 py-1 bg-blue-100 text-blue-700 rounded">Recommended</span>
                        </div>
                    </button>

                    {/* Template-Based */}
                    <button
                        onClick={() => setMethod('template')}
                        className="p-6 border-2 rounded-lg hover:border-purple-500 hover:bg-purple-50 transition-all text-left"
                    >
                        <div className="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center mb-4">
                            <svg className="w-6 h-6 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H5a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h2a1 1 0 011 1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-6z" />
                            </svg>
                        </div>
                        <h3 className="font-semibold text-lg mb-2">Use Template</h3>
                        <p className="text-sm text-gray-600 mb-4">
                            Start from a pre-built template and customize it to your needs
                        </p>
                        <div className="flex items-center gap-2 text-xs">
                            <span className="px-2 py-1 bg-yellow-100 text-yellow-700 rounded">Quick Start</span>
                        </div>
                    </button>

                    {/* Manual Design */}
                    <button
                        onClick={() => setMethod('manual')}
                        className="p-6 border-2 rounded-lg hover:border-gray-500 hover:bg-gray-50 transition-all text-left"
                    >
                        <div className="w-12 h-12 bg-gray-100 rounded-lg flex items-center justify-center mb-4">
                            <svg className="w-6 h-6 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                            </svg>
                        </div>
                        <h3 className="font-semibold text-lg mb-2">Manual Design</h3>
                        <p className="text-sm text-gray-600 mb-4">
                            Build your simulation from scratch with full control over every detail
                        </p>
                        <div className="flex items-center gap-2 text-xs">
                            <span className="px-2 py-1 bg-gray-200 text-gray-700 rounded">Advanced</span>
                        </div>
                    </button>
                </div>

                <div className="mt-8 flex justify-between">
                    <button
                        onClick={onBack}
                        className="px-6 py-2 border rounded-md hover:bg-gray-50"
                    >
                        Back
                    </button>
                </div>
            </div>
        );
    }

    if (method === 'ai') {
        return (
            <div className="content-creation-workflow max-w-4xl mx-auto p-6">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold mb-2">AI-Powered Generation</h1>
                    <p className="text-gray-600">Describe your simulation and let AI create it</p>
                </div>

                {!generatedManifest ? (
                    <div className="space-y-6">
                        {/* AI Provider Selection */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                AI Provider
                            </label>
                            <div className="grid grid-cols-3 gap-4">
                                {providers.map(p => (
                                    <button
                                        key={p.value}
                                        onClick={() => setProvider(p.value)}
                                        className={`p-4 border-2 rounded-lg text-left transition-all ${provider === p.value
                                                ? 'border-blue-500 bg-blue-50'
                                                : 'border-gray-200 hover:border-gray-300'
                                            }`}
                                    >
                                        <div className="font-medium mb-1">{p.label}</div>
                                        <div className="text-xs text-gray-600 space-x-2">
                                            <span>Cost: {p.cost}</span>
                                            <span>•</span>
                                            <span>{p.speed}</span>
                                        </div>
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Prompt Input */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Describe Your Simulation
                            </label>
                            <textarea
                                className="w-full px-4 py-3 border rounded-md focus:ring-2 focus:ring-blue-500 min-h-[200px]"
                                placeholder={`Example: Create an interactive simulation showing ${objectives.topic}. Include:\n- Interactive controls for key parameters\n- Visual animations showing the process\n- Step-by-step explanations\n- Assessment questions to check understanding`}
                                value={aiPrompt}
                                onChange={(e) => setAiPrompt(e.target.value)}
                            />
                        </div>

                        {/* Context Display */}
                        <div className="bg-gray-50 border rounded-lg p-4">
                            <h4 className="font-medium mb-2">Context from Planning Phase</h4>
                            <dl className="grid grid-cols-2 gap-3 text-sm">
                                <div>
                                    <dt className="text-gray-600">Domain</dt>
                                    <dd className="font-medium">{objectives.domain}</dd>
                                </div>
                                <div>
                                    <dt className="text-gray-600">Topic</dt>
                                    <dd className="font-medium">{objectives.topic}</dd>
                                </div>
                                <div>
                                    <dt className="text-gray-600">Grade Level</dt>
                                    <dd className="font-medium">{objectives.gradeLevel}</dd>
                                </div>
                                <div>
                                    <dt className="text-gray-600">Duration</dt>
                                    <dd className="font-medium">{objectives.duration}</dd>
                                </div>
                            </dl>
                        </div>

                        {/* Example Prompts */}
                        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                            <h4 className="font-medium text-blue-900 mb-2">💡 Example Prompts</h4>
                            <div className="space-y-2 text-sm">
                                <button
                                    onClick={() => setAiPrompt("Create an interactive physics simulation showing projectile motion with adjustable launch angle and velocity. Include trajectory visualization and real-time calculations.")}
                                    className="block w-full text-left p-2 hover:bg-blue-100 rounded"
                                >
                                    "Create an interactive physics simulation showing projectile motion..."
                                </button>
                                <button
                                    onClick={() => setAiPrompt("Build a chemistry simulation demonstrating acid-base reactions with pH indicators, molecular animations, and interactive mixing controls.")}
                                    className="block w-full text-left p-2 hover:bg-blue-100 rounded"
                                >
                                    "Build a chemistry simulation demonstrating acid-base reactions..."
                                </button>
                            </div>
                        </div>

                        {/* Generate Button */}
                        <div className="flex justify-between items-center pt-6 border-t">
                            <button
                                onClick={() => setMethod(null)}
                                className="px-6 py-2 border rounded-md hover:bg-gray-50"
                            >
                                Change Method
                            </button>
                            <button
                                onClick={handleAIGeneration}
                                disabled={!aiPrompt.trim() || isGenerating}
                                className="px-8 py-3 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                            >
                                {isGenerating ? (
                                    <>
                                        <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
                                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                                        </svg>
                                        Generating...
                                    </>
                                ) : (
                                    'Generate Simulation'
                                )}
                            </button>
                        </div>
                    </div>
                ) : (
                    <div className="space-y-6">
                        {/* Generated Preview */}
                        <div className="bg-green-50 border border-green-200 rounded-lg p-4">
                            <div className="flex items-center gap-2 mb-2">
                                <svg className="w-5 h-5 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                                </svg>
                                <span className="font-medium text-green-900">Simulation Generated Successfully!</span>
                            </div>
                            <p className="text-sm text-green-800">
                                Your simulation has been created. Review it below and proceed to the next step.
                            </p>
                        </div>

                        {/* Manifest Preview */}
                        <div className="border rounded-lg p-6">
                            <h3 className="font-semibold text-lg mb-4">Generated Simulation</h3>
                            <dl className="space-y-3">
                                <div>
                                    <dt className="text-sm font-medium text-gray-600">Title</dt>
                                    <dd className="text-gray-900">{generatedManifest?.title}</dd>
                                </div>
                                <div>
                                    <dt className="text-sm font-medium text-gray-600">Description</dt>
                                    <dd className="text-gray-900">{generatedManifest?.description}</dd>
                                </div>
                                <div>
                                    <dt className="text-sm font-medium text-gray-600">Entities</dt>
                                    <dd className="text-gray-900">{generatedManifest?.initialEntities?.length || 0} entities</dd>
                                </div>
                                <div>
                                    <dt className="text-sm font-medium text-gray-600">Steps</dt>
                                    <dd className="text-gray-900">{generatedManifest?.steps?.length || 0} steps</dd>
                                </div>
                            </dl>
                        </div>

                        {/* Actions */}
                        <div className="flex justify-between items-center pt-6 border-t">
                            <button
                                onClick={() => setGeneratedManifest(null)}
                                className="px-6 py-2 border rounded-md hover:bg-gray-50"
                            >
                                Regenerate
                            </button>
                            <button
                                onClick={() => onComplete(generatedManifest)}
                                className="px-8 py-3 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                            >
                                Continue to Design
                            </button>
                        </div>
                    </div>
                )}
            </div>
        );
    }

    // Template and Manual methods would go here
    return (
        <div className="content-creation-workflow max-w-4xl mx-auto p-6">
            <div className="text-center py-12">
                <h2 className="text-2xl font-bold mb-4">Coming Soon</h2>
                <p className="text-gray-600 mb-6">
                    {method === 'template' ? 'Template selection' : 'Manual design'} interface is under development
                </p>
                <button
                    onClick={() => setMethod(null)}
                    className="px-6 py-2 border rounded-md hover:bg-gray-50"
                >
                    Choose Different Method
                </button>
            </div>
        </div>
    );
}
