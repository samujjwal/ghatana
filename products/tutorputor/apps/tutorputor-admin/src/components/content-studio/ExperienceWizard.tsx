/**
 * Experience Wizard Component
 * 
 * AI-powered wizard for creating new Learning Experiences.
 * Implements the "Describe" step of the 3-step flow.
 * 
 * @doc.type component
 * @doc.purpose AI-first experience creation wizard
 * @doc.layer product
 * @doc.pattern Wizard
 */

import { useState, useCallback } from 'react';
import { Sparkles, BookOpen, Target, GraduationCap, Loader2, Beaker, Clock } from 'lucide-react';
import { Button } from '@ghatana/design-system';
import { Card } from '../ui';
import { SimulationBuilder } from './SimulationBuilder';

// Types
interface GradeAdaptation {
    gradeRange: string;
    mathLevel: string;
    rigorLevel: string;
    scaffoldingLevel: string;
    vocabularyComplexity: number;
    readingLevel: number;
    prerequisiteConcepts: string[];
}

interface LearningClaim {
    id: string;
    text: string;
    bloom: string;
    masteryThreshold: number;
    orderIndex: number;
    evidenceRequirements: any[];
    tasks: any[];
}

interface LearningExperience {
    id: string;
    tenantId: string;
    slug: string;
    title: string;
    description: string;
    status: string;
    version: number;
    gradeAdaptation: GradeAdaptation;
    claims: LearningClaim[];
    estimatedTimeMinutes: number;
    keywords: string[];
    moduleId?: string;
    simulationId?: string;
    authorId?: string;
    createdAt: Date;
    updatedAt: Date;
}

interface ValidationResult {
    status: string;
    canPublish: boolean;
    checks: any[];
    score: number;
    pillarScores: Record<string, number>;
    validatedAt: Date;
}

interface ExperienceWizardProps {
    onExperienceCreated: (experience: LearningExperience, validation?: ValidationResult) => void;
    onCancel: () => void;
}

const GRADE_RANGES = [
    { value: 'k_2', label: 'K-2 (Ages 5-7)', icon: '🎨' },
    { value: 'grade_3_5', label: 'Grades 3-5 (Ages 8-10)', icon: '📚' },
    { value: 'grade_6_8', label: 'Grades 6-8 (Ages 11-13)', icon: '🔬' },
    { value: 'grade_9_12', label: 'Grades 9-12 (Ages 14-18)', icon: '🎓' },
    { value: 'undergraduate', label: 'Undergraduate', icon: '🏛️' },
    { value: 'graduate', label: 'Graduate', icon: '🔬' },
    { value: 'professional', label: 'Professional', icon: '💼' },
];

export function ExperienceWizard({ onExperienceCreated, onCancel }: ExperienceWizardProps) {
    const [query, setQuery] = useState('');
    const [isGenerating, setIsGenerating] = useState(false);
    const [isSearching, setIsSearching] = useState(false);
    const [gradeVariations, setGradeVariations] = useState<any[]>([]);
    const [similarExperiences, setSimilarExperiences] = useState<any[]>([]);
    const [error, setError] = useState<string | null>(null);
    const [linkedSimulation, setLinkedSimulation] = useState<any>(null);
    const [showSimulationBuilder, setShowSimulationBuilder] = useState(false);
    const [selectedVariation, setSelectedVariation] = useState<any>(null);

    // Search for similar experiences as user types (debounced)
    const searchSimilar = useCallback(async (searchQuery: string) => {
        if (!searchQuery.trim() || searchQuery.length < 10) {
            setSimilarExperiences([]);
            return;
        }

        setIsSearching(true);

        try {
            const response = await fetch('/api/content-studio/experiences/search-similar', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'x-tenant-id': 'default' },
                body: JSON.stringify({ query: searchQuery, limit: 5 }),
            });

            if (response.ok) {
                const result = await response.json();
                setSimilarExperiences(result.similar || []);
            }
        } catch (err) {
            console.error('Failed to search similar experiences:', err);
        } finally {
            setIsSearching(false);
        }
    }, []);

    // Handle query input with debouncing
    const handleQueryChange = useCallback((value: string) => {
        setQuery(value);

        // Auto-search after user stops typing
        const timeoutId = setTimeout(() => {
            if (value.length >= 10) {
                searchSimilar(value);
            }
        }, 500);

        return () => clearTimeout(timeoutId);
    }, [searchSimilar]);

    // Generate content for all grade levels automatically
    const handleGenerate = useCallback(async () => {
        if (!query.trim() || query.length < 10) {
            setError('Please provide more detail about what you want to create (at least 10 characters)');
            return;
        }

        setError(null);
        setIsGenerating(true);
        setGradeVariations([]);

        try {
            // Always generate for all grade levels
            const aiResponse = await fetch('/api/content-studio/ai/generate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    prompt: query,
                    generateForAllGrades: true, // Always generate for all grades
                }),
            });

            if (!aiResponse.ok) {
                throw new Error('Failed to generate content');
            }

            const aiResult = await aiResponse.json();
            setGradeVariations(aiResult.variations || []);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'An error occurred');
        } finally {
            setIsGenerating(false);
        }
    }, [query]);

    const handleSimulationGenerated = useCallback((simulation: any) => {
        setLinkedSimulation(simulation);
        setShowSimulationBuilder(false);
    }, []);

    const handleSimulationLinked = useCallback((simulationId: string, version: string) => {
        setLinkedSimulation({ id: simulationId, version });
        setShowSimulationBuilder(false);
    }, []);

    const handleCreateWithSimulation = useCallback((variation: any) => {
        setSelectedVariation(variation);
        setShowSimulationBuilder(true);
    }, []);

    const handleCreateExperience = useCallback(async (variation: any) => {
        if (!variation) return;

        try {
            // Extract title from query (first sentence or up to 50 chars)
            const generatedTitle = query.split('.')[0].slice(0, 50).trim();

            const response = await fetch('/api/content-studio/experiences', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'x-tenant-id': 'default' },
                body: JSON.stringify({
                    title: generatedTitle,
                    description: query,
                    claims: variation.claims,
                    keywords: variation.keywords,
                    estimatedTimeMinutes: variation.estimatedTimeMinutes,
                    gradeAdaptation: variation.gradeAdaptation,
                    // Use simulation from variation if available, otherwise use linked simulation
                    simulationManifestId: variation.simulation?.id || linkedSimulation?.id,
                    simulationVersion: variation.simulation?.version || linkedSimulation?.version,
                }),
            });

            if (!response.ok) {
                throw new Error('Failed to create experience');
            }

            const result = await response.json();
            onExperienceCreated(result);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to create experience');
        }
    }, [query, linkedSimulation, onExperienceCreated]);

    const handleSelectExisting = useCallback((experience: any) => {
        onExperienceCreated(experience);
    }, [onExperienceCreated]);

    // Main render - simplified UI (embedded mode)
    return (
        <div className="w-full">
            {/* Main Input Section */}
            <div className="mb-6">
                <div className="relative">
                    <textarea
                        value={query}
                        onChange={(e) => handleQueryChange(e.target.value)}
                        placeholder="✨ Describe what you want to teach... 
Example: Students will understand how forces affect motion, predict object movement, and explain inertia and acceleration"
                        rows={3}
                        className="w-full px-4 py-3 rounded-lg border-2 border-white/30 bg-white/90 dark:bg-gray-800/90 backdrop-blur text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-white focus:border-white shadow-lg resize-none"
                    />
                    <div className="absolute bottom-3 right-3 flex items-center gap-2">
                        {isSearching && (
                            <span className="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1">
                                <Loader2 className="h-3 w-3 animate-spin" />
                                Searching...
                            </span>
                        )}
                        <Button
                            onClick={handleGenerate}
                            disabled={isGenerating || query.length < 10}
                            size="sm"
                            className="bg-white text-purple-600 hover:bg-white/90 shadow-md"
                        >
                            {isGenerating ? (
                                <>
                                    <Loader2 className="h-4 w-4 mr-1 animate-spin" />
                                    Generating...
                                </>
                            ) : (
                                <>
                                    <Sparkles className="h-4 w-4 mr-1" />
                                    Generate
                                </>
                            )}
                        </Button>
                    </div>
                </div>
                
                {/* Character count and hint */}
                <div className="flex items-center justify-between mt-2 px-1">
                    <span className="text-xs text-white/70">
                        {query.length < 10 ? `${10 - query.length} more characters needed` : '✓ Ready to generate'}
                    </span>
                    <span className="text-xs text-white/70">
                        AI will create content for all grade levels
                    </span>
                </div>
            </div>

            {/* Error Message */}
            {error && (
                <div className="mb-6 p-4 bg-red-500/20 border border-red-300/50 dark:border-red-700/50 rounded-lg backdrop-blur">
                    <p className="text-sm text-white">{error}</p>
                </div>
            )}

            {/* Results Section */}
            {(gradeVariations.length > 0 || similarExperiences.length > 0) && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Generated Variations */}
                    {gradeVariations.length > 0 && (
                        <div className="lg:col-span-2">
                            <div className="bg-white/95 dark:bg-gray-800/95 backdrop-blur rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 p-6">
                                <div className="flex items-center gap-2 mb-4">
                                    <Sparkles className="h-5 w-5 text-purple-500" />
                                    <h3 className="font-semibold text-gray-900 dark:text-white">
                                        AI-Generated Content
                                    </h3>
                                    <span className="text-xs bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300 px-2 py-1 rounded-full">
                                        {gradeVariations.length} variations
                                    </span>
                                </div>
                                <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                                    Select a grade level to create your experience
                                </p>
                                <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                                    {gradeVariations.map((variation, idx) => (
                                        <button
                                            key={variation.gradeRange || variation.gradeLabel || idx}
                                            onClick={() => handleCreateExperience(variation)}
                                            className="group relative text-left p-4 rounded-lg border-2 border-gray-200 dark:border-gray-600 hover:border-purple-500 dark:hover:border-purple-400 bg-white dark:bg-gray-800 hover:shadow-lg transition-all duration-200"
                                        >
                                            <div className="absolute inset-0 bg-gradient-to-br from-purple-500/5 to-blue-500/5 rounded-lg opacity-0 group-hover:opacity-100 transition-opacity" />
                                            <div className="relative">
                                                <div className="flex items-center justify-between mb-2">
                                                    <span className="text-sm font-bold text-purple-600 dark:text-purple-400">
                                                        {variation.gradeLabel}
                                                    </span>
                                                    <Clock className="h-4 w-4 text-gray-400" />
                                                </div>
                                                <div className="space-y-1">
                                                    <div className="flex items-center gap-1 text-xs text-gray-600 dark:text-gray-400">
                                                        <Target className="h-3 w-3" />
                                                        <span>{variation.claims?.length || 0} objectives</span>
                                                    </div>
                                                    <div className="flex items-center gap-1 text-xs text-gray-600 dark:text-gray-400">
                                                        <Clock className="h-3 w-3" />
                                                        <span>{variation.estimatedTimeMinutes} min</span>
                                                    </div>
                                                    <div className="text-xs text-purple-600 dark:text-purple-400 font-medium">
                                                        {variation.gradeAdaptation?.rigorLevel} rigor
                                                    </div>
                                                </div>
                                            </div>
                                        </button>
                                    ))}
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Similar Experiences Sidebar */}
                    {similarExperiences.length > 0 && (
                        <div className="lg:col-span-1">
                            <div className="bg-blue-50/95 dark:bg-blue-900/20 backdrop-blur rounded-xl shadow-lg border border-blue-200 dark:border-blue-800 p-6 sticky top-6">
                                <div className="flex items-center gap-2 mb-4">
                                    <BookOpen className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                                    <h3 className="font-semibold text-gray-900 dark:text-white">
                                        Similar Content
                                    </h3>
                                </div>
                                <p className="text-xs text-gray-600 dark:text-gray-400 mb-3">
                                    Found existing experiences that match your description
                                </p>
                                <div className="space-y-2">
                                    {similarExperiences.map((exp) => (
                                        <button
                                            key={exp.id}
                                            onClick={() => handleSelectExisting(exp)}
                                            className="w-full text-left p-3 rounded-lg bg-white dark:bg-gray-800 border border-blue-200 dark:border-blue-700 hover:border-blue-400 dark:hover:border-blue-500 hover:shadow-md transition-all"
                                        >
                                            <div className="font-medium text-sm text-gray-900 dark:text-white mb-1 line-clamp-1">
                                                {exp.title}
                                            </div>
                                            <div className="text-xs text-gray-600 dark:text-gray-400 line-clamp-2 mb-2">
                                                {exp.description}
                                            </div>
                                            <div className="flex items-center justify-between text-xs">
                                                <span className="bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 px-2 py-0.5 rounded">
                                                    {exp.similarityScore || 'N/A'}% match
                                                </span>
                                                <span className="text-gray-500 dark:text-gray-400">
                                                    {exp.gradeRange}
                                                </span>
                                            </div>
                                        </button>
                                    ))}
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

export default ExperienceWizard;
