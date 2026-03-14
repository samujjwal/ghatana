/**
 * Simulation Template Browser Component
 * 
 * Browse, search, and clone pre-built simulation templates.
 * Provides marketplace-like experience for discovering simulations.
 * 
 * @doc.type component
 * @doc.purpose Template library browser with search and filtering
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card } from './ui';
import { Input, Button, Select, Spinner } from '@ghatana/design-system';

interface SimulationTemplate {
    id: string;
    slug: string;
    title: string;
    description: string;
    domain: string;
    difficulty: string;
    tags: string[];
    thumbnailUrl: string | null;
    license: string;
    isPremium: boolean;
    isVerified: boolean;
    version: string;
    authorName: string | null;
    statsViews: number;
    statsUses: number;
    statsFavorites: number;
    statsRating: number;
    statsRatingCount: number;
    status: string;
}

interface TemplateLibraryProps {
    conceptId?: string;
    onCloneSuccess?: (simulationId: string) => void;
    mode?: 'browse' | 'select'; // browse = just view, select = clone to concept
}

const DOMAINS = ['CS_DISCRETE', 'PHYSICS', 'CHEMISTRY', 'BIOLOGY', 'MEDICINE', 'ECONOMICS', 'ENGINEERING', 'MATHEMATICS'];
const DIFFICULTIES = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'];

export function TemplateLibrary({ conceptId, onCloneSuccess, mode = 'browse' }: TemplateLibraryProps) {
    const [search, setSearch] = useState('');
    const [domainFilter, setDomainFilter] = useState('');
    const [difficultyFilter, setDifficultyFilter] = useState('');
    const [selectedTemplate, setSelectedTemplate] = useState<SimulationTemplate | null>(null);
    const queryClient = useQueryClient();

    // Fetch templates
    const { data, isLoading } = useQuery({
        queryKey: ['simulation-templates', domainFilter, difficultyFilter, search],
        queryFn: async () => {
            const params = new URLSearchParams();
            if (domainFilter) params.set('domain', domainFilter);
            if (difficultyFilter) params.set('difficulty', difficultyFilter);
            if (search) params.set('search', search);

            const res = await fetch(`/admin/api/v1/content/templates?${params.toString()}`);
            if (!res.ok) throw new Error('Failed to fetch templates');
            return res.json() as Promise<{ templates: SimulationTemplate[] }>;
        },
    });

    // Clone template mutation
    const cloneMutation = useMutation({
        mutationFn: async (templateId: string) => {
            const res = await fetch(`/admin/api/v1/content/templates/${templateId}/clone`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ conceptId }),
            });
            if (!res.ok) throw new Error('Failed to clone template');
            return res.json();
        },
        onSuccess: (data) => {
            queryClient.invalidateQueries({ queryKey: ['simulation'] });
            setSelectedTemplate(null);
            alert(`Template cloned successfully! Simulation ID: ${data.simulationId}`);
            onCloneSuccess?.(data.simulationId);
        },
        onError: (error) => {
            alert(`Error cloning template: ${error instanceof Error ? error.message : String(error)}`);
        },
    });

    // Seed templates mutation
    const seedMutation = useMutation({
        mutationFn: async () => {
            const res = await fetch('/admin/api/v1/content/templates/seed', {
                method: 'POST',
            });
            if (!res.ok) throw new Error('Failed to seed templates');
            return res.json();
        },
        onSuccess: (data) => {
            queryClient.invalidateQueries({ queryKey: ['simulation-templates'] });
            alert(`Successfully seeded ${data.count} templates!`);
        },
        onError: (error) => {
            alert(`Error seeding templates: ${error instanceof Error ? error.message : String(error)}`);
        },
    });

    const filteredTemplates = data?.templates ?? [];

    const getDifficultyColor = (difficulty: string) => {
        const colors: Record<string, string> = {
            BEGINNER: 'bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-300',
            INTERMEDIATE: 'bg-blue-100 text-blue-800 dark:bg-blue-900/20 dark:text-blue-300',
            ADVANCED: 'bg-orange-100 text-orange-800 dark:bg-orange-900/20 dark:text-orange-300',
            EXPERT: 'bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-300',
        };
        return colors[difficulty] || 'bg-gray-100 text-gray-800';
    };

    if (isLoading) {
        return <Spinner />;
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex justify-between items-center">
                <div>
                    <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Simulation Template Library</h2>
                    <p className="text-gray-600 dark:text-gray-400">
                        Browse and clone pre-built simulation templates
                    </p>
                </div>
                {filteredTemplates.length === 0 && (
                    <Button onClick={() => seedMutation.mutate()} disabled={seedMutation.isPending}>
                        {seedMutation.isPending ? 'Seeding...' : '🌱 Seed Default Templates'}
                    </Button>
                )}
            </div>

            {/* Filters */}
            <Card className="p-4">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <Input
                        placeholder="Search templates..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                    />
                    <Select value={domainFilter} onChange={(e) => setDomainFilter(e.target.value)}>
                        <option value="">All Domains</option>
                        {DOMAINS.map((d) => (
                            <option key={d} value={d}>
                                {d}
                            </option>
                        ))}
                    </Select>
                    <Select value={difficultyFilter} onChange={(e) => setDifficultyFilter(e.target.value)}>
                        <option value="">All Difficulties</option>
                        {DIFFICULTIES.map((d) => (
                            <option key={d} value={d}>
                                {d}
                            </option>
                        ))}
                    </Select>
                </div>
            </Card>

            {/* Templates Grid */}
            {filteredTemplates.length === 0 ? (
                <Card className="p-8 text-center">
                    <p className="text-gray-600 dark:text-gray-400 mb-4">
                        No templates found. Click "Seed Default Templates" to add pre-built templates.
                    </p>
                </Card>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {filteredTemplates.map((template) => (
                        <Card
                            key={template.id}
                            className="p-4 hover:shadow-lg transition-shadow cursor-pointer"
                            onClick={() => setSelectedTemplate(template)}
                        >
                            {template.thumbnailUrl && (
                                <img
                                    src={template.thumbnailUrl}
                                    alt={template.title}
                                    className="w-full h-32 object-cover rounded mb-3"
                                />
                            )}

                            <div className="flex items-start justify-between mb-2">
                                <h3 className="font-bold text-gray-900 dark:text-white">{template.title}</h3>
                                {template.isVerified && (
                                    <span className="text-blue-500" title="Verified">
                                        ✓
                                    </span>
                                )}
                            </div>

                            <p className="text-sm text-gray-600 dark:text-gray-400 mb-3 line-clamp-2">
                                {template.description}
                            </p>

                            <div className="flex flex-wrap gap-2 mb-3">
                                <span className={`px-2 py-1 rounded text-xs font-medium ${getDifficultyColor(template.difficulty)}`}>
                                    {template.difficulty}
                                </span>
                                <span className="px-2 py-1 rounded text-xs font-medium bg-purple-100 text-purple-800 dark:bg-purple-900/20 dark:text-purple-300">
                                    {template.domain}
                                </span>
                            </div>

                            <div className="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
                                <div className="flex gap-3">
                                    <span title="Views">👁️ {template.statsViews}</span>
                                    <span title="Uses">🔄 {template.statsUses}</span>
                                    <span title="Rating">⭐ {template.statsRating.toFixed(1)}</span>
                                </div>
                                <span className="text-gray-400">v{template.version}</span>
                            </div>

                            {template.tags.length > 0 && (
                                <div className="mt-2 flex flex-wrap gap-1">
                                    {template.tags.slice(0, 3).map((tag) => (
                                        <span key={tag} className="px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 text-xs rounded">
                                            {tag}
                                        </span>
                                    ))}
                                </div>
                            )}
                        </Card>
                    ))}
                </div>
            )}

            {/* Template Detail Modal */}
            {selectedTemplate && (
                <div className="fixed inset-0 bg-black/50 dark:bg-black/70 flex items-center justify-center z-50 p-4">
                    <Card className="w-full max-w-2xl max-h-[90vh] overflow-y-auto p-6">
                        <div className="flex justify-between items-start mb-4">
                            <div>
                                <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-1">
                                    {selectedTemplate.title}
                                    {selectedTemplate.isVerified && <span className="text-blue-500 ml-2">✓</span>}
                                </h2>
                                <p className="text-sm text-gray-500">
                                    {selectedTemplate.authorName || 'TutorPutor'} • v{selectedTemplate.version} • {selectedTemplate.license}
                                </p>
                            </div>
                            <button
                                onClick={() => setSelectedTemplate(null)}
                                className="text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
                            >
                                ✕
                            </button>
                        </div>

                        {selectedTemplate.thumbnailUrl && (
                            <img
                                src={selectedTemplate.thumbnailUrl}
                                alt={selectedTemplate.title}
                                className="w-full h-48 object-cover rounded mb-4"
                            />
                        )}

                        <div className="space-y-4">
                            <div>
                                <h3 className="font-semibold text-gray-900 dark:text-white mb-2">Description</h3>
                                <p className="text-gray-600 dark:text-gray-400">{selectedTemplate.description}</p>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <h4 className="font-semibold text-sm text-gray-700 dark:text-gray-300">Domain</h4>
                                    <p className="text-gray-600 dark:text-gray-400">{selectedTemplate.domain}</p>
                                </div>
                                <div>
                                    <h4 className="font-semibold text-sm text-gray-700 dark:text-gray-300">Difficulty</h4>
                                    <p className="text-gray-600 dark:text-gray-400">{selectedTemplate.difficulty}</p>
                                </div>
                            </div>

                            {selectedTemplate.tags.length > 0 && (
                                <div>
                                    <h4 className="font-semibold text-sm text-gray-700 dark:text-gray-300 mb-2">Tags</h4>
                                    <div className="flex flex-wrap gap-2">
                                        {selectedTemplate.tags.map((tag) => (
                                            <span
                                                key={tag}
                                                className="px-2 py-1 bg-gray-100 dark:bg-gray-700 text-sm rounded"
                                            >
                                                {tag}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            )}

                            <div className="flex items-center gap-6 text-sm text-gray-600 dark:text-gray-400">
                                <span>👁️ {selectedTemplate.statsViews} views</span>
                                <span>🔄 {selectedTemplate.statsUses} uses</span>
                                <span>⭐ {selectedTemplate.statsRating.toFixed(1)} ({selectedTemplate.statsRatingCount} ratings)</span>
                            </div>
                        </div>

                        {mode === 'select' && conceptId && (
                            <div className="mt-6 flex gap-2">
                                <Button
                                    onClick={() => cloneMutation.mutate(selectedTemplate.id)}
                                    disabled={cloneMutation.isPending}
                                    className="flex-1"
                                >
                                    {cloneMutation.isPending ? 'Cloning...' : '📋 Clone to Concept'}
                                </Button>
                                <Button variant="outline" onClick={() => setSelectedTemplate(null)}>
                                    Cancel
                                </Button>
                            </div>
                        )}
                    </Card>
                </div>
            )}
        </div>
    );
}
