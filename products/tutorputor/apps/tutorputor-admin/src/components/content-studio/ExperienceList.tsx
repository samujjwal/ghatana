/**
 * Experience List Component
 * 
 * Displays a list of Learning Experiences with filtering and search.
 * 
 * @doc.type component
 * @doc.purpose Experience listing and discovery
 * @doc.layer product
 * @doc.pattern List
 */

import { useState, useEffect } from 'react';
import {
    Search,
    Clock,
    Target,
    GraduationCap,
    ChevronRight,
    Sparkles,
    FileText
} from 'lucide-react';

// Types
interface LearningExperience {
    id: string;
    tenantId: string;
    slug: string;
    title: string;
    description: string;
    status: string;
    version: number;
    gradeAdaptation: {
        gradeRange: string;
    };
    claims: any[];
    estimatedTimeMinutes: number;
    keywords: string[];
    authorId?: string;
    createdAt: Date;
    updatedAt: Date;
}

interface ExperienceListProps {
    onSelect: (experience: LearningExperience) => void;
    onCreate: () => void;
}

const STATUS_STYLES: Record<string, { bg: string; text: string }> = {
    draft: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300' },
    validating: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300' },
    review: { bg: 'bg-purple-100 dark:bg-purple-900/30', text: 'text-purple-700 dark:text-purple-300' },
    approved: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
    published: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
    archived: { bg: 'bg-gray-100 dark:bg-gray-700', text: 'text-gray-700 dark:text-gray-300' },
};

const GRADE_LABELS: Record<string, string> = {
    k_2: 'K-2',
    grade_3_5: '3-5',
    grade_6_8: '6-8',
    grade_9_12: '9-12',
    undergraduate: 'Undergrad',
    graduate: 'Graduate',
    professional: 'Professional',
};

export function ExperienceList({ onSelect, onCreate }: ExperienceListProps) {
    const [experiences, setExperiences] = useState<LearningExperience[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [statusFilter, setStatusFilter] = useState<string | null>(null);
    const [gradeFilter, setGradeFilter] = useState<string | null>(null);

    // Fetch experiences
    useEffect(() => {
        async function fetchExperiences() {
            setIsLoading(true);
            try {
                const params = new URLSearchParams({ tenantId: 'default' });
                if (statusFilter) params.set('status', statusFilter);
                if (gradeFilter) params.set('gradeRange', gradeFilter);

                const response = await fetch(`/api/content-studio/experiences?${params}`);
                if (response.ok) {
                    const data = await response.json();
                    setExperiences(data.experiences || []);
                }
            } catch (error) {
                console.error('Failed to fetch experiences:', error);
            } finally {
                setIsLoading(false);
            }
        }

        fetchExperiences();
    }, [statusFilter, gradeFilter]);

    // Filter experiences by search query
    const filteredExperiences = experiences.filter(exp => {
        if (!searchQuery) return true;
        const query = searchQuery.toLowerCase();
        return (
            exp.title.toLowerCase().includes(query) ||
            exp.description.toLowerCase().includes(query) ||
            exp.keywords.some(k => k.toLowerCase().includes(query))
        );
    });

    // Get status style
    const getStatusStyle = (status: string) => {
        return STATUS_STYLES[status] || STATUS_STYLES.draft;
    };

    // Format date
    const formatDate = (date: Date) => {
        return new Date(date).toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
        });
    };

    return (
        <div className="space-y-6">
            {/* Filters Bar */}
            <div className="flex flex-col sm:flex-row gap-4">
                {/* Search */}
                <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400" />
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Search experiences..."
                        className="w-full pl-10 pr-4 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white placeholder-gray-400 focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                    />
                </div>

                {/* Status Filter */}
                <select
                    value={statusFilter || ''}
                    onChange={(e) => setStatusFilter(e.target.value || null)}
                    className="px-4 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                >
                    <option value="">All Statuses</option>
                    <option value="draft">Draft</option>
                    <option value="review">In Review</option>
                    <option value="published">Published</option>
                    <option value="archived">Archived</option>
                </select>

                {/* Grade Filter */}
                <select
                    value={gradeFilter || ''}
                    onChange={(e) => setGradeFilter(e.target.value || null)}
                    className="px-4 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                >
                    <option value="">All Grades</option>
                    <option value="k_2">K-2</option>
                    <option value="grade_3_5">Grades 3-5</option>
                    <option value="grade_6_8">Grades 6-8</option>
                    <option value="grade_9_12">Grades 9-12</option>
                    <option value="undergraduate">Undergraduate</option>
                    <option value="graduate">Graduate</option>
                    <option value="professional">Professional</option>
                </select>
            </div>

            {/* Stats Summary */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
                    <div className="flex items-center gap-2 text-gray-500 dark:text-gray-400 mb-1">
                        <FileText className="h-4 w-4" />
                        <span className="text-sm">Total</span>
                    </div>
                    <p className="text-2xl font-bold text-gray-900 dark:text-white">
                        {experiences.length}
                    </p>
                </div>
                <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
                    <div className="flex items-center gap-2 text-yellow-500 mb-1">
                        <Clock className="h-4 w-4" />
                        <span className="text-sm">Drafts</span>
                    </div>
                    <p className="text-2xl font-bold text-gray-900 dark:text-white">
                        {experiences.filter(e => e.status === 'draft').length}
                    </p>
                </div>
                <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
                    <div className="flex items-center gap-2 text-purple-500 mb-1">
                        <Target className="h-4 w-4" />
                        <span className="text-sm">In Review</span>
                    </div>
                    <p className="text-2xl font-bold text-gray-900 dark:text-white">
                        {experiences.filter(e => e.status === 'review').length}
                    </p>
                </div>
                <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
                    <div className="flex items-center gap-2 text-green-500 mb-1">
                        <Sparkles className="h-4 w-4" />
                        <span className="text-sm">Published</span>
                    </div>
                    <p className="text-2xl font-bold text-gray-900 dark:text-white">
                        {experiences.filter(e => e.status === 'published').length}
                    </p>
                </div>
            </div>

            {/* Experience Cards */}
            {isLoading ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {[1, 2, 3, 4, 5, 6].map((i) => (
                        <div
                            key={i}
                            className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6 animate-pulse"
                        >
                            <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-3/4 mb-3" />
                            <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded w-full mb-2" />
                            <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded w-2/3" />
                        </div>
                    ))}
                </div>
            ) : filteredExperiences.length === 0 ? (
                <div className="text-center py-12">
                    <div className="inline-flex p-4 bg-gray-100 dark:bg-gray-800 rounded-full mb-4">
                        <FileText className="h-8 w-8 text-gray-400" />
                    </div>
                    <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                        No experiences found
                    </h3>
                    <p className="text-gray-500 dark:text-gray-400 mb-6">
                        {searchQuery || statusFilter || gradeFilter
                            ? 'Try adjusting your filters'
                            : 'Create your first learning experience with AI'}
                    </p>
                    <button
                        onClick={onCreate}
                        className="inline-flex items-center gap-2 px-6 py-3 bg-gradient-to-r from-purple-500 to-blue-500 hover:from-purple-600 hover:to-blue-600 text-white rounded-lg font-medium"
                    >
                        <Sparkles className="h-5 w-5" />
                        Create Experience
                    </button>
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {filteredExperiences.map((exp) => {
                        const statusStyle = getStatusStyle(exp.status);
                        return (
                            <button
                                key={exp.id}
                                onClick={() => onSelect(exp)}
                                className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6 text-left hover:border-purple-300 dark:hover:border-purple-600 hover:shadow-lg transition-all group"
                            >
                                <div className="flex items-start justify-between mb-3">
                                    <span className={`px-2 py-1 rounded text-xs font-medium ${statusStyle.bg} ${statusStyle.text}`}>
                                        {exp.status}
                                    </span>
                                    <ChevronRight className="h-5 w-5 text-gray-400 group-hover:text-purple-500 transition-colors" />
                                </div>

                                <h3 className="font-semibold text-gray-900 dark:text-white mb-2 line-clamp-2">
                                    {exp.title}
                                </h3>
                                <p className="text-sm text-gray-500 dark:text-gray-400 mb-4 line-clamp-2">
                                    {exp.description}
                                </p>

                                <div className="flex items-center gap-4 text-xs text-gray-500 dark:text-gray-400">
                                    <div className="flex items-center gap-1">
                                        <Target className="h-3 w-3" />
                                        <span>{(exp.claims || []).length} claims</span>
                                    </div>
                                    <div className="flex items-center gap-1">
                                        <Clock className="h-3 w-3" />
                                        <span>{exp.estimatedTimeMinutes || 30} min</span>
                                    </div>
                                    <div className="flex items-center gap-1">
                                        <GraduationCap className="h-3 w-3" />
                                        <span>{GRADE_LABELS[exp.gradeAdaptation?.gradeRange] || exp.gradeAdaptation?.gradeRange || 'N/A'}</span>
                                    </div>
                                </div>

                                <div className="mt-4 pt-4 border-t dark:border-gray-700 text-xs text-gray-400">
                                    Updated {formatDate(exp.updatedAt || new Date())}
                                </div>
                            </button>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
