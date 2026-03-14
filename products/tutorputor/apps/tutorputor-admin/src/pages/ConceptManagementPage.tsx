/**
 * Concept Management Page
 * 
 * Comprehensive page for managing concepts and their associated
 * simulations and visualizations within a domain.
 * 
 * @doc.type component
 * @doc.purpose Page for managing concepts with simulations/visualizations
 * @doc.layer product
 * @doc.pattern Page
 */

import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams, useNavigate } from 'react-router-dom';
import { Card } from '../components/ui';
import { Input, Button, Spinner, Select, TextArea } from '@ghatana/design-system';
import { SimulationManager } from '../components/SimulationManager';

interface DomainDetail {
    id: string;
    domain: string;
    title: string;
    description: string;
    author: string;
    status: 'PUBLISHED' | 'DRAFT';
    concepts: Concept[];
}

interface Concept {
    id: string;
    domainId: string;
    name: string;
    description: string;
    level: string;
    learningObjectives: string[];
    prerequisites: string[];
    competencies: string[];
    keywords: string[];
    status: string;
    version: number;
    createdAt: string;
    updatedAt: string;
    simulation?: any;
    visualization?: any;
}

export function ConceptManagementPage() {
    const { domainId } = useParams<{ domainId: string }>();
    const navigate = useNavigate();
    const queryClient = useQueryClient();

    const [selectedConceptId, setSelectedConceptId] = useState<string | null>(null);
    const [showNewConceptForm, setShowNewConceptForm] = useState(false);
    const [isGeneratingAI, setIsGeneratingAI] = useState(false);
    const [newConceptData, setNewConceptData] = useState({
        name: '',
        description: '',
        level: 'INTERMEDIATE',
    });

    // Fetch domain with concepts
    const { data: domain, isLoading: domainLoading } = useQuery({
        queryKey: ['domain', domainId],
        queryFn: async () => {
            const res = await fetch(
                `/admin/api/v1/content/db/domains/${domainId}`
            );
            if (!res.ok) throw new Error('Failed to fetch domain');
            return res.json() as Promise<DomainDetail>;
        },
    });

    const selectedConcept = domain?.concepts.find((c) => c.id === selectedConceptId);

    const handleGenerateWithAI = async () => {
        if (!newConceptData.name.trim()) {
            alert('Please enter a concept name first');
            return;
        }

        setIsGeneratingAI(true);
        try {
            const res = await fetch('/api/v1/ai/generate-concept', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    conceptName: newConceptData.name,
                    domain: domain?.domain,
                }),
            });

            if (!res.ok) throw new Error('Failed to generate concept with AI');

            const data = await res.json();
            const generated = data.concept;

            // Update form with AI-generated content
            setNewConceptData({
                name: generated.name,
                description: generated.description,
                level: generated.level,
            });

            alert('AI generated content successfully! Review and edit as needed.');
        } catch (error) {
            alert('Error generating with AI: ' + (error instanceof Error ? error.message : String(error)));
        } finally {
            setIsGeneratingAI(false);
        }
    };

    const handleCreateConcept = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const res = await fetch(
                `/admin/api/v1/content/db/domains/${domainId}/concepts`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        name: newConceptData.name,
                        description: newConceptData.description,
                        level: newConceptData.level,
                    }),
                }
            );

            if (!res.ok) throw new Error('Failed to create concept');

            // Refresh domain data
            queryClient.invalidateQueries({ queryKey: ['domain', domainId] });

            // Reset form
            setNewConceptData({
                name: '',
                description: '',
                level: 'INTERMEDIATE',
            });
            setShowNewConceptForm(false);
        } catch (error) {
            alert('Error creating concept: ' + (error instanceof Error ? error.message : String(error)));
        }
    };

    if (domainLoading) {
        return <Spinner />;
    }

    if (!domain) {
        return (
            <div className="text-center py-8">
                <p className="text-gray-600">Domain not found</p>
                <Button onClick={() => navigate('/content?tab=domains')} className="mt-4">
                    Back to Domains
                </Button>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex justify-between items-start">
                <div>
                    <div className="flex items-center gap-2">
                        <Button
                            variant="ghost"
                            onClick={() => navigate('/content?tab=domains')}
                            className="!p-0"
                        >
                            ← Back
                        </Button>
                        <h1 className="text-3xl font-bold">{domain.title}</h1>
                    </div>
                    <p className="text-gray-600 mt-2">{domain.description}</p>
                    <div className="text-sm text-gray-500 mt-2 space-x-4">
                        <span>Domain: <code>{domain.domain}</code></span>
                        <span>Author: {domain.author}</span>
                        <span>Status: <code className={`px-2 py-1 rounded ${domain.status === 'PUBLISHED' ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'
                            }`}>{domain.status}</code></span>
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-3 gap-6">
                {/* Concepts List */}
                <div className="col-span-1">
                    <Card className="p-4">
                        <div className="flex justify-between items-center mb-4">
                            <h2 className="text-lg font-semibold">Concepts ({domain.concepts.length})</h2>
                            <Button
                                size="sm"
                                onClick={() => setShowNewConceptForm(!showNewConceptForm)}
                            >
                                + New
                            </Button>
                        </div>

                        {showNewConceptForm && (
                            <form onSubmit={handleCreateConcept} className="mb-4 space-y-3 p-3 bg-blue-50 dark:bg-blue-900/20 rounded border border-blue-200 dark:border-blue-800">
                                <div className="flex gap-2">
                                    <Input
                                        placeholder="Concept name"
                                        value={newConceptData.name}
                                        onChange={(e) =>
                                            setNewConceptData({ ...newConceptData, name: e.target.value })
                                        }
                                        required
                                        className="flex-1"
                                    />
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={handleGenerateWithAI}
                                        disabled={isGeneratingAI || !newConceptData.name.trim()}
                                        className="whitespace-nowrap bg-gradient-to-r from-purple-500 to-pink-500 text-white hover:from-purple-600 hover:to-pink-600"
                                    >
                                        {isGeneratingAI ? '🤖 Generating...' : '✨ Generate with AI'}
                                    </Button>
                                </div>
                                <TextArea
                                    placeholder="Description (AI can auto-generate)"
                                    value={newConceptData.description}
                                    onChange={(e) =>
                                        setNewConceptData({ ...newConceptData, description: e.target.value })
                                    }
                                    rows={3}
                                />
                                <Select
                                    value={newConceptData.level}
                                    onChange={(e) =>
                                        setNewConceptData({ ...newConceptData, level: e.target.value })
                                    }
                                >
                                    <option value="FOUNDATIONAL">Foundational</option>
                                    <option value="INTERMEDIATE">Intermediate</option>
                                    <option value="ADVANCED">Advanced</option>
                                    <option value="RESEARCH">Research</option>
                                </Select>
                                <div className="flex gap-2">
                                    <Button type="submit" size="sm">Create</Button>
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={() => setShowNewConceptForm(false)}
                                    >
                                        Cancel
                                    </Button>
                                </div>
                            </form>
                        )}

                        <div className="space-y-2 max-h-96 overflow-y-auto">
                            {domain.concepts.length === 0 ? (
                                <p className="text-gray-500 text-sm">No concepts yet</p>
                            ) : (
                                domain.concepts.map((concept) => (
                                    <button
                                        key={concept.id}
                                        onClick={() => setSelectedConceptId(concept.id)}
                                        className={`w-full text-left p-3 rounded border-l-4 transition ${selectedConceptId === concept.id
                                            ? 'bg-blue-50 border-blue-500'
                                            : 'bg-white border-gray-300 hover:bg-gray-50'
                                            }`}
                                    >
                                        <p className="font-medium text-sm">{concept.name}</p>
                                        <p className="text-xs text-gray-600 mt-1 line-clamp-2">
                                            {concept.description}
                                        </p>
                                        <div className="flex gap-2 mt-2">
                                            {concept.simulation && (
                                                <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-purple-100 text-purple-800">
                                                    Sim
                                                </span>
                                            )}
                                            {concept.visualization && (
                                                <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-cyan-100 text-cyan-800">
                                                    Viz
                                                </span>
                                            )}
                                            <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${concept.level === 'FOUNDATIONAL' ? 'bg-green-100 text-green-800' :
                                                concept.level === 'INTERMEDIATE' ? 'bg-blue-100 text-blue-800' :
                                                    concept.level === 'ADVANCED' ? 'bg-orange-100 text-orange-800' :
                                                        'bg-red-100 text-red-800'
                                                }`}>
                                                {concept.level}
                                            </span>
                                        </div>
                                    </button>
                                ))
                            )}
                        </div>
                    </Card>
                </div>

                {/* Detail View */}
                <div className="col-span-2">
                    {!selectedConcept ? (
                        <Card className="p-8 text-center bg-gray-50">
                            <p className="text-gray-600">Select a concept to manage its simulations and visualizations</p>
                        </Card>
                    ) : (
                        <div className="space-y-4">
                            {/* Concept Details */}
                            <Card className="p-4">
                                <h2 className="text-2xl font-bold mb-2">{selectedConcept.name}</h2>
                                <p className="text-gray-600 mb-4">{selectedConcept.description}</p>

                                <div className="grid grid-cols-2 gap-4 text-sm">
                                    <div>
                                        <p className="font-medium text-gray-700">Level</p>
                                        <p className="text-gray-600">{selectedConcept.level}</p>
                                    </div>
                                    <div>
                                        <p className="font-medium text-gray-700">Status</p>
                                        <p className="text-gray-600">{selectedConcept.status}</p>
                                    </div>
                                </div>

                                {Array.isArray(selectedConcept.learningObjectives) && selectedConcept.learningObjectives.length > 0 && (
                                    <div className="mt-4">
                                        <p className="font-medium text-gray-700 mb-2">Learning Objectives</p>
                                        <ul className="list-disc list-inside text-sm text-gray-600 space-y-1">
                                            {selectedConcept.learningObjectives.map((obj, i) => (
                                                <li key={i}>{obj}</li>
                                            ))}
                                        </ul>
                                    </div>
                                )}

                                {Array.isArray(selectedConcept.keywords) && selectedConcept.keywords.length > 0 && (
                                    <div className="mt-4">
                                        <p className="font-medium text-gray-700 mb-2">Keywords</p>
                                        <div className="flex flex-wrap gap-2">
                                            {selectedConcept.keywords.map((keyword, i) => (
                                                <span
                                                    key={i}
                                                    className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800"
                                                >
                                                    {keyword}
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </Card>

                            {/* Simulation Manager */}
                            <SimulationManager
                                domainId={domainId!}
                                conceptId={selectedConcept.id}
                                conceptName={selectedConcept.name}
                            />
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export default ConceptManagementPage;
