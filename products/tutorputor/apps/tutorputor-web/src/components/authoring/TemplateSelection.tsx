/**
 * Template Selection Component
 * 
 * Replaces "Coming Soon" template path with functional template selection.
 * Integrates with template backend for discovery and application.
 *
 * @doc.type component
 * @doc.purpose Template selection and application UI
 * @doc.layer product
 * @doc.pattern Template-Based Creation
 */

import { useState, useEffect } from "react";

interface Template {
    id: string;
    slug: string;
    title: string;
    description: string;
    domain: string;
    difficulty: string;
    tags: string[];
    thumbnailUrl?: string;
    isPremium: boolean;
    isVerified: boolean;
    statsViews: number;
    statsUses: number;
    statsRating: number;
    statsRatingCount: number;
}

interface TemplateSelectionProps {
    onTemplateSelect: (templateId: string, parameters?: any) => void;
    onBack: () => void;
    domain?: string;
    gradeLevel?: string;
    duration?: string;
}

export function TemplateSelection({
    onTemplateSelect,
    onBack,
    domain,
    gradeLevel,
    duration
}: TemplateSelectionProps) {
    const [templates, setTemplates] = useState<Template[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [filters, setFilters] = useState({
        domain: domain || '',
        difficulty: '',
        search: '',
        isVerified: true
    });
    const [categories, setCategories] = useState({
        domains: [],
        difficulties: []
    });
    const [selectedTemplate, setSelectedTemplate] = useState<Template | null>(null);

    useEffect(() => {
        loadTemplates();
        loadCategories();
    }, [filters]);

    const loadTemplates = async () => {
        setLoading(true);
        setError(null);

        try {
            const queryParams = new URLSearchParams();
            if (filters.domain) queryParams.append('domain', filters.domain);
            if (filters.difficulty) queryParams.append('difficulty', filters.difficulty);
            if (filters.search) queryParams.append('search', filters.search);
            if (filters.isVerified) queryParams.append('isVerified', 'true');

            const response = await fetch(`/api/content-studio/templates?${queryParams}`);
            if (!response.ok) {
                throw new Error('Failed to load templates');
            }

            const data = await response.json();
            setTemplates(data.templates || []);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Unknown error');
        } finally {
            setLoading(false);
        }
    };

    const loadCategories = async () => {
        try {
            const response = await fetch('/api/content-studio/templates/categories');
            if (!response.ok) {
                throw new Error('Failed to load categories');
            }

            const data = await response.json();
            setCategories({
                domains: data.domains || [],
                difficulties: data.difficulties || []
            });
        } catch (err) {
            console.error('Failed to load categories:', err);
        }
    };

    const handleTemplateClick = (template: Template) => {
        setSelectedTemplate(template);
    };

    const handleApplyTemplate = async () => {
        if (!selectedTemplate) return;

        try {
            const parameters = {
                domain: domain || selectedTemplate.domain,
                gradeLevel: gradeLevel || 'high_school',
                duration: duration || '45_minutes',
                customizations: {
                    difficulty: selectedTemplate.difficulty,
                    tags: selectedTemplate.tags
                }
            };

            const response = await fetch(`/api/content-studio/templates/${selectedTemplate.id}/apply`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ parameters })
            });

            if (!response.ok) {
                throw new Error('Failed to apply template');
            }

            const result = await response.json();
            onTemplateSelect(selectedTemplate.id, result);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to apply template');
        }
    };

    const getDifficultyColor = (difficulty: string) => {
        switch (difficulty) {
            case 'BEGINNER': return 'bg-green-100 text-green-800';
            case 'INTERMEDIATE': return 'bg-yellow-100 text-yellow-800';
            case 'ADVANCED': return 'bg-orange-100 text-orange-800';
            case 'EXPERT': return 'bg-red-100 text-red-800';
            default: return 'bg-gray-100 text-gray-800';
        }
    };

    const getDomainColor = (domain: string) => {
        const colors: Record<string, string> = {
            'PHYSICS': 'bg-blue-100 text-blue-800',
            'CHEMISTRY': 'bg-purple-100 text-purple-800',
            'BIOLOGY': 'bg-green-100 text-green-800',
            'MATHEMATICS': 'bg-indigo-100 text-indigo-800',
            'CS_DISCRETE': 'bg-pink-100 text-pink-800',
            'ECONOMICS': 'bg-yellow-100 text-yellow-800',
            'ENGINEERING': 'bg-gray-100 text-gray-800',
            'MEDICINE': 'bg-red-100 text-red-800'
        };
        return colors[domain] || 'bg-gray-100 text-gray-800';
    };

    if (loading) {
        return (
            <div className="template-selection max-w-6xl mx-auto p-6">
                <div className="flex items-center justify-center h-64">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="template-selection max-w-6xl mx-auto p-6">
                <div className="bg-red-50 border border-red-200 rounded-lg p-4">
                    <h3 className="text-red-800 font-medium">Error loading templates</h3>
                    <p className="text-red-600 text-sm mt-1">{error}</p>
                    <button
                        onClick={loadTemplates}
                        className="mt-3 px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 text-sm"
                    >
                        Retry
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="template-selection max-w-6xl mx-auto p-6">
            {/* Header */}
            <div className="mb-8">
                <div className="flex items-center justify-between mb-4">
                    <h1 className="text-3xl font-bold">Choose a Template</h1>
                    <button
                        onClick={onBack}
                        className="px-4 py-2 border rounded-md hover:bg-gray-50"
                    >
                        Back
                    </button>
                </div>
                <p className="text-gray-600">
                    Start with a pre-built template and customize it to your needs
                </p>
            </div>

            {/* Filters */}
            <div className="bg-white border rounded-lg p-4 mb-6">
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                    {/* Domain Filter */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Domain
                        </label>
                        <select
                            value={filters.domain}
                            onChange={(e) => setFilters({ ...filters, domain: e.target.value })}
                            className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-blue-500"
                        >
                            <option value="">All Domains</option>
                            {categories.domains.map((d: any) => (
                                <option key={d.value} value={d.value}>
                                    {d.label} ({d.count})
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Difficulty Filter */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Difficulty
                        </label>
                        <select
                            value={filters.difficulty}
                            onChange={(e) => setFilters({ ...filters, difficulty: e.target.value })}
                            className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-blue-500"
                        >
                            <option value="">All Levels</option>
                            {categories.difficulties.map((d: any) => (
                                <option key={d.value} value={d.value}>
                                    {d.label} ({d.count})
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Search */}
                    <div className="md:col-span-2">
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Search
                        </label>
                        <input
                            type="text"
                            value={filters.search}
                            onChange={(e) => setFilters({ ...filters, search: e.target.value })}
                            placeholder="Search templates..."
                            className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-blue-500"
                        />
                    </div>
                </div>

                {/* Verified Toggle */}
                <div className="mt-4 flex items-center">
                    <input
                        type="checkbox"
                        id="verified-only"
                        checked={filters.isVerified}
                        onChange={(e) => setFilters({ ...filters, isVerified: e.target.checked })}
                        className="mr-2"
                    />
                    <label htmlFor="verified-only" className="text-sm text-gray-700">
                        Show verified templates only
                    </label>
                </div>
            </div>

            {/* Templates Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-6">
                {templates.map((template) => (
                    <div
                        key={template.id}
                        onClick={() => handleTemplateClick(template)}
                        className={`border rounded-lg p-4 cursor-pointer transition-all hover:border-blue-500 hover:shadow-md ${selectedTemplate?.id === template.id ? 'border-blue-500 bg-blue-50' : ''
                            }`}
                    >
                        {/* Header */}
                        <div className="flex items-start justify-between mb-3">
                            <div className="flex-1">
                                <h3 className="font-semibold text-lg mb-1">{template.title}</h3>
                                <p className="text-sm text-gray-600 line-clamp-2">{template.description}</p>
                            </div>
                            {template.isVerified && (
                                <span className="ml-2 px-2 py-1 bg-green-100 text-green-800 text-xs rounded-full">
                                    ✓ Verified
                                </span>
                            )}
                        </div>

                        {/* Tags */}
                        <div className="flex flex-wrap gap-1 mb-3">
                            <span className={`px-2 py-1 text-xs rounded-full ${getDomainColor(template.domain)}`}>
                                {template.domain.replace('_', ' ')}
                            </span>
                            <span className={`px-2 py-1 text-xs rounded-full ${getDifficultyColor(template.difficulty)}`}>
                                {template.difficulty.charAt(0) + template.difficulty.slice(1).toLowerCase()}
                            </span>
                            {template.isPremium && (
                                <span className="px-2 py-1 bg-yellow-100 text-yellow-800 text-xs rounded-full">
                                    Premium
                                </span>
                            )}
                        </div>

                        {/* Stats */}
                        <div className="flex items-center justify-between text-sm text-gray-500">
                            <div className="flex items-center gap-4">
                                <span>👁 {template.statsViews}</span>
                                <span>📊 {template.statsUses}</span>
                            </div>
                            {template.statsRatingCount > 0 && (
                                <div className="flex items-center">
                                    <span>⭐ {template.statsRating.toFixed(1)}</span>
                                    <span className="ml-1">({template.statsRatingCount})</span>
                                </div>
                            )}
                        </div>
                    </div>
                ))}
            </div>

            {/* No Results */}
            {templates.length === 0 && (
                <div className="text-center py-12">
                    <div className="text-gray-400 text-6xl mb-4">📋</div>
                    <h3 className="text-lg font-medium text-gray-900 mb-2">No templates found</h3>
                    <p className="text-gray-500 mb-4">
                        Try adjusting your filters or search terms
                    </p>
                    <button
                        onClick={() => setFilters({ domain: '', difficulty: '', search: '', isVerified: true })}
                        className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                    >
                        Clear Filters
                    </button>
                </div>
            )}

            {/* Template Preview/Apply */}
            {selectedTemplate && (
                <div className="border rounded-lg p-6 bg-gray-50">
                    <h3 className="text-xl font-semibold mb-4">Selected Template</h3>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        {/* Template Details */}
                        <div>
                            <h4 className="font-medium mb-2">{selectedTemplate.title}</h4>
                            <p className="text-gray-600 mb-4">{selectedTemplate.description}</p>

                            <div className="space-y-2 text-sm">
                                <div className="flex items-center gap-2">
                                    <span className="font-medium">Domain:</span>
                                    <span className={`px-2 py-1 rounded-full ${getDomainColor(selectedTemplate.domain)}`}>
                                        {selectedTemplate.domain.replace('_', ' ')}
                                    </span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <span className="font-medium">Difficulty:</span>
                                    <span className={`px-2 py-1 rounded-full ${getDifficultyColor(selectedTemplate.difficulty)}`}>
                                        {selectedTemplate.difficulty.charAt(0) + selectedTemplate.difficulty.slice(1).toLowerCase()}
                                    </span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <span className="font-medium">Uses:</span>
                                    <span>{selectedTemplate.statsUses}</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <span className="font-medium">Rating:</span>
                                    <span>⭐ {selectedTemplate.statsRating.toFixed(1)} ({selectedTemplate.statsRatingCount})</span>
                                </div>
                            </div>

                            {/* Tags */}
                            {selectedTemplate.tags.length > 0 && (
                                <div className="mt-4">
                                    <span className="font-medium text-sm">Tags:</span>
                                    <div className="flex flex-wrap gap-1 mt-1">
                                        {selectedTemplate.tags.map((tag, index) => (
                                            <span key={index} className="px-2 py-1 bg-gray-100 text-gray-700 text-xs rounded">
                                                {tag}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Apply Form */}
                        <div>
                            <h4 className="font-medium mb-4">Apply Template</h4>

                            <div className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Domain
                                    </label>
                                    <select
                                        defaultValue={domain || selectedTemplate.domain}
                                        className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-blue-500"
                                    >
                                        <option value={selectedTemplate.domain}>{selectedTemplate.domain.replace('_', ' ')}</option>
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Grade Level
                                    </label>
                                    <select
                                        defaultValue={gradeLevel || 'high_school'}
                                        className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-blue-500"
                                    >
                                        <option value="elementary">Elementary (K-5)</option>
                                        <option value="middle_school">Middle School (6-8)</option>
                                        <option value="high_school">High School (9-12)</option>
                                        <option value="undergraduate">Undergraduate</option>
                                        <option value="graduate">Graduate</option>
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Duration
                                    </label>
                                    <select
                                        defaultValue={duration || '45_minutes'}
                                        className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-blue-500"
                                    >
                                        <option value="15_minutes">15 minutes</option>
                                        <option value="30_minutes">30 minutes</option>
                                        <option value="45_minutes">45 minutes</option>
                                        <option value="60_minutes">60 minutes</option>
                                        <option value="90_minutes">90 minutes</option>
                                    </select>
                                </div>
                            </div>

                            {/* Actions */}
                            <div className="flex gap-3 mt-6">
                                <button
                                    onClick={handleApplyTemplate}
                                    className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                                >
                                    Apply Template
                                </button>
                                <button
                                    onClick={() => setSelectedTemplate(null)}
                                    className="px-4 py-2 border rounded-md hover:bg-gray-50"
                                >
                                    Cancel
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
