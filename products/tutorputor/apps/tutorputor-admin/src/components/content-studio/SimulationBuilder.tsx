/**
 * Simulation Builder Component
 * 
 * Allows content authors to generate and configure simulations
 * for learning experiences with safety constraints and versioning.
 */

import { useState } from 'react';
import { clsx } from 'clsx';
import { RiskBadge, ConfidenceIndicator, type RiskLevel } from './RiskBadge';

interface SimulationManifest {
    id: string;
    version: string;
    title: string;
    description: string;
    domain: string;
    parameters: SimulationParameter[];
    safetyConstraints: SafetyConstraint[];
    status: 'DRAFT' | 'REVIEW' | 'ACTIVE' | 'DEPRECATED';
    riskLevel: RiskLevel;
    confidenceScore: number;
}

interface SimulationParameter {
    name: string;
    type: 'number' | 'string' | 'boolean' | 'range';
    defaultValue: unknown;
    min?: number;
    max?: number;
    description: string;
}

interface SafetyConstraint {
    id: string;
    type: 'value_bound' | 'rate_limit' | 'timeout' | 'content_filter';
    description: string;
    enabled: boolean;
}

interface SimulationBuilderProps {
    experienceId: string;
    experienceTitle: string;
    domain: string;
    currentSimulation?: SimulationManifest;
    onSimulationGenerated: (simulation: SimulationManifest) => void;
    onSimulationLinked: (simulationId: string, version: string) => void;
    className?: string;
}

export function SimulationBuilder({
    experienceTitle,
    domain,
    currentSimulation,
    onSimulationGenerated,
    onSimulationLinked,
    className,
}: SimulationBuilderProps) {
    const [isGenerating, setIsGenerating] = useState(false);
    const [description, setDescription] = useState('');
    const [generatedSimulation, setGeneratedSimulation] = useState<SimulationManifest | null>(
        currentSimulation || null
    );
    const [error, setError] = useState<string | null>(null);

    const handleGenerate = async () => {
        if (!description.trim()) {
            setError('Please provide a description for the simulation');
            return;
        }

        setIsGenerating(true);
        setError(null);

        try {
            const response = await fetch('/api/content-studio/ai/generate-simulation', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'x-tenant-id': 'default',
                },
                body: JSON.stringify({
                    description,
                    conceptName: experienceTitle,
                    domain,
                }),
            });

            if (!response.ok) {
                throw new Error('Failed to generate simulation');
            }

            const simulation = await response.json();

            // Add metadata
            const manifest: SimulationManifest = {
                id: simulation.id || `sim_${Date.now()}`,
                version: '1.0.0',
                title: simulation.title || `${experienceTitle} Simulation`,
                description: simulation.description || description,
                domain,
                parameters: simulation.parameters || [],
                safetyConstraints: [
                    { id: 'sc1', type: 'value_bound', description: 'Parameter values within safe ranges', enabled: true },
                    { id: 'sc2', type: 'timeout', description: 'Maximum execution time: 60 seconds', enabled: true },
                    { id: 'sc3', type: 'rate_limit', description: 'Maximum 100 steps per session', enabled: true },
                ],
                status: 'DRAFT',
                riskLevel: 'LOW',
                confidenceScore: 0.85,
            };

            setGeneratedSimulation(manifest);
            onSimulationGenerated(manifest);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to generate simulation');
        } finally {
            setIsGenerating(false);
        }
    };

    const handleLink = () => {
        if (generatedSimulation) {
            onSimulationLinked(generatedSimulation.id, generatedSimulation.version);
        }
    };

    return (
        <div className={clsx('rounded-lg border border-gray-200 bg-white p-6', className)}>
            <h3 className="mb-4 text-lg font-semibold text-gray-900">Simulation Builder</h3>

            {!generatedSimulation ? (
                <div className="space-y-4">
                    <div>
                        <label className="mb-1 block text-sm font-medium text-gray-700">
                            Describe the simulation you want to create
                        </label>
                        <textarea
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            placeholder="e.g., An interactive physics simulation showing projectile motion with adjustable initial velocity and angle..."
                            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                            rows={4}
                        />
                    </div>

                    {error && (
                        <div className="rounded-md bg-red-50 p-3 text-sm text-red-700">
                            {error}
                        </div>
                    )}

                    <button
                        onClick={handleGenerate}
                        disabled={isGenerating || !description.trim()}
                        className={clsx(
                            'flex w-full items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-medium transition-colors',
                            isGenerating || !description.trim()
                                ? 'cursor-not-allowed bg-gray-100 text-gray-400'
                                : 'bg-blue-600 text-white hover:bg-blue-700'
                        )}
                    >
                        {isGenerating ? (
                            <>
                                <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                                </svg>
                                Generating with AI...
                            </>
                        ) : (
                            <>
                                <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                                </svg>
                                Generate Simulation
                            </>
                        )}
                    </button>
                </div>
            ) : (
                <div className="space-y-4">
                    {/* Simulation Header */}
                    <div className="flex items-start justify-between">
                        <div>
                            <h4 className="font-medium text-gray-900">{generatedSimulation.title}</h4>
                            <p className="text-sm text-gray-500">Version {generatedSimulation.version}</p>
                        </div>
                        <div className="flex items-center gap-2">
                            <RiskBadge riskLevel={generatedSimulation.riskLevel} size="sm" />
                            <span className={clsx(
                                'rounded-full px-2 py-0.5 text-xs font-medium',
                                generatedSimulation.status === 'ACTIVE' && 'bg-green-100 text-green-800',
                                generatedSimulation.status === 'DRAFT' && 'bg-gray-100 text-gray-800',
                                generatedSimulation.status === 'REVIEW' && 'bg-yellow-100 text-yellow-800',
                                generatedSimulation.status === 'DEPRECATED' && 'bg-red-100 text-red-800'
                            )}>
                                {generatedSimulation.status}
                            </span>
                        </div>
                    </div>

                    {/* Confidence Score */}
                    <div>
                        <label className="mb-1 block text-xs font-medium text-gray-500">AI Confidence</label>
                        <ConfidenceIndicator score={generatedSimulation.confidenceScore} size="sm" />
                    </div>

                    {/* Parameters */}
                    {generatedSimulation.parameters.length > 0 && (
                        <div>
                            <label className="mb-2 block text-sm font-medium text-gray-700">Parameters</label>
                            <div className="space-y-2">
                                {generatedSimulation.parameters.map((param, idx) => (
                                    <div key={idx} className="flex items-center justify-between rounded-md bg-gray-50 px-3 py-2 text-sm">
                                        <span className="font-medium text-gray-700">{param.name}</span>
                                        <span className="text-gray-500">{param.type}</span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Safety Constraints */}
                    <div>
                        <label className="mb-2 block text-sm font-medium text-gray-700">Safety Constraints</label>
                        <div className="space-y-2">
                            {generatedSimulation.safetyConstraints.map((constraint) => (
                                <div key={constraint.id} className="flex items-center gap-2 text-sm">
                                    <span className={clsx(
                                        'h-2 w-2 rounded-full',
                                        constraint.enabled ? 'bg-green-500' : 'bg-gray-300'
                                    )} />
                                    <span className="text-gray-600">{constraint.description}</span>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Actions */}
                    <div className="flex gap-2 pt-2">
                        <button
                            onClick={handleLink}
                            className="flex-1 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
                        >
                            Link to Experience
                        </button>
                        <button
                            onClick={() => setGeneratedSimulation(null)}
                            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                        >
                            Regenerate
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
