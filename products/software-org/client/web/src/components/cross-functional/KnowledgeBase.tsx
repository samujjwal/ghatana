/**
 * KnowledgeBase Component
 *
 * Organizational knowledge management system with articles, documentation,
 * FAQs, and search. Tracks knowledge sharing and collaboration across teams.
 *
 * Features:
 * - Knowledge metrics dashboard (Total Articles, Recent Updates, Popular Topics, Contributors)
 * - 4-tab interface: Articles, Categories, Contributors, Activity
 * - Advanced search and filtering
 * - Article management with versioning
 * - Category organization
 * - Contributor tracking
 * - Activity timeline
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Box,
    Button,
    Card,
    Chip,
    Grid,
    KpiCard,
    Stack,
    Tabs,
    TextField,
    Typography,
} from '@ghatana/ui';

// ============================================================================
// TypeScript Interfaces
// ============================================================================

/**
 * Knowledge base metrics overview
 */
export interface KnowledgeMetrics {
    totalArticles: number;
    recentUpdates: number;
    popularTopics: number;
    activeContributors: number;
}

/**
 * Knowledge article with content and metadata
 */
export interface KnowledgeArticle {
    id: string;
    title: string;
    summary: string;
    category: string;
    tags: string[];
    author: string;
    authorRole: string;
    createdDate: string;
    lastUpdated: string;
    views: number;
    likes: number;
    version: number;
    status: 'published' | 'draft' | 'archived';
    readTime: number; // minutes
    difficulty: 'beginner' | 'intermediate' | 'advanced';
}

/**
 * Knowledge category with article count
 */
export interface KnowledgeCategory {
    id: string;
    name: string;
    description: string;
    articleCount: number;
    subcategories: string[];
    icon: string;
    color: string;
}

/**
 * Knowledge contributor with contribution stats
 */
export interface KnowledgeContributor {
    id: string;
    name: string;
    role: string;
    team: string;
    articlesWritten: number;
    articlesUpdated: number;
    totalViews: number;
    totalLikes: number;
    expertise: string[];
    joinedDate: string;
    lastActive: string;
}

/**
 * Knowledge activity entry
 */
export interface KnowledgeActivity {
    id: string;
    type: 'created' | 'updated' | 'commented' | 'liked';
    description: string;
    articleTitle: string;
    actor: string;
    timestamp: string;
}

/**
 * Component props
 */
export interface KnowledgeBaseProps {
    metrics: KnowledgeMetrics;
    articles: KnowledgeArticle[];
    categories: KnowledgeCategory[];
    contributors: KnowledgeContributor[];
    activities: KnowledgeActivity[];
    onArticleClick?: (articleId: string) => void;
    onCategoryClick?: (categoryId: string) => void;
    onContributorClick?: (contributorId: string) => void;
    onActivityClick?: (activityId: string) => void;
    onCreateArticle?: () => void;
    onSearch?: (query: string) => void;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Get color for article status
 */
const getStatusColor = (status: string): 'success' | 'warning' | 'default' | 'error' => {
    switch (status) {
        case 'published':
            return 'success';
        case 'draft':
            return 'warning';
        case 'archived':
            return 'default';
        default:
            return 'default';
    }
};

/**
 * Get color for difficulty level
 */
const getDifficultyColor = (difficulty: string): 'success' | 'warning' | 'error' | 'default' => {
    switch (difficulty) {
        case 'beginner':
            return 'success';
        case 'intermediate':
            return 'warning';
        case 'advanced':
            return 'error';
        default:
            return 'default';
    }
};

/**
 * Get color for activity type
 */
const getActivityColor = (type: string): 'success' | 'primary' | 'warning' | 'error' | 'default' => {
    switch (type) {
        case 'created':
            return 'success';
        case 'updated':
            return 'primary';
        case 'commented':
            return 'warning';
        case 'liked':
            return 'error';
        default:
            return 'default';
    }
};

/**
 * Format date to readable string
 */
const formatDate = (dateString: string): string => {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
};

/**
 * Format number with K/M abbreviations
 */
const formatNumber = (num: number): string => {
    if (num >= 1000000) {
        return `${(num / 1000000).toFixed(1)}M`;
    }
    if (num >= 1000) {
        return `${(num / 1000).toFixed(1)}K`;
    }
    return num.toString();
};

/**
 * Get time ago string
 */
const getTimeAgo = (dateString: string): string => {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 60) {
        return `${diffMins}m ago`;
    }
    if (diffHours < 24) {
        return `${diffHours}h ago`;
    }
    if (diffDays < 30) {
        return `${diffDays}d ago`;
    }
    return formatDate(dateString);
};

// ============================================================================
// Mock Data
// ============================================================================

export const mockKnowledgeBaseData = {
    metrics: {
        totalArticles: 347,
        recentUpdates: 23,
        popularTopics: 12,
        activeContributors: 45,
    } as KnowledgeMetrics,

    articles: [
        {
            id: 'article-1',
            title: 'ActiveJ Event Loop Best Practices',
            summary: 'Comprehensive guide to writing efficient asynchronous code with ActiveJ event loops. Covers common pitfalls and performance optimization techniques.',
            category: 'Engineering',
            tags: ['ActiveJ', 'Performance', 'Best Practices', 'Java'],
            author: 'Sarah Chen',
            authorRole: 'Senior Engineer',
            createdDate: '2024-11-15',
            lastUpdated: '2024-12-10',
            views: 1250,
            likes: 89,
            version: 3,
            status: 'published',
            readTime: 12,
            difficulty: 'advanced',
        },
        {
            id: 'article-2',
            title: 'Onboarding Guide for New Developers',
            summary: 'Step-by-step onboarding guide for new team members. Includes setup instructions, codebase overview, and team practices.',
            category: 'Onboarding',
            tags: ['Onboarding', 'Developer Experience', 'Getting Started'],
            author: 'Michael Rodriguez',
            authorRole: 'Engineering Manager',
            createdDate: '2024-10-20',
            lastUpdated: '2024-12-05',
            views: 2150,
            likes: 156,
            version: 5,
            status: 'published',
            readTime: 25,
            difficulty: 'beginner',
        },
        {
            id: 'article-3',
            title: 'API Design Guidelines',
            summary: 'Standards and best practices for designing RESTful APIs. Covers naming conventions, versioning, error handling, and documentation.',
            category: 'Architecture',
            tags: ['API', 'REST', 'Design', 'Standards'],
            author: 'Emily Watson',
            authorRole: 'Principal Architect',
            createdDate: '2024-09-10',
            lastUpdated: '2024-11-28',
            views: 890,
            likes: 67,
            version: 2,
            status: 'published',
            readTime: 18,
            difficulty: 'intermediate',
        },
        {
            id: 'article-4',
            title: 'Database Migration Strategy',
            summary: 'DRAFT: Comprehensive strategy for database migrations with zero downtime. Includes rollback procedures and testing guidelines.',
            category: 'Engineering',
            tags: ['Database', 'Migration', 'DevOps'],
            author: 'David Kim',
            authorRole: 'Staff Engineer',
            createdDate: '2024-12-08',
            lastUpdated: '2024-12-10',
            views: 45,
            likes: 3,
            version: 1,
            status: 'draft',
            readTime: 20,
            difficulty: 'advanced',
        },
    ] as KnowledgeArticle[],

    categories: [
        {
            id: 'cat-1',
            name: 'Engineering',
            description: 'Technical documentation, coding standards, and best practices',
            articleCount: 87,
            subcategories: ['Backend', 'Frontend', 'Mobile', 'DevOps'],
            icon: '💻',
            color: '#3b82f6',
        },
        {
            id: 'cat-2',
            name: 'Onboarding',
            description: 'Getting started guides and team onboarding materials',
            articleCount: 23,
            subcategories: ['Developer', 'Product', 'Design'],
            icon: '🚀',
            color: '#10b981',
        },
        {
            id: 'cat-3',
            name: 'Architecture',
            description: 'System design, architecture patterns, and technical decisions',
            articleCount: 45,
            subcategories: ['Microservices', 'Data', 'Security'],
            icon: '🏗️',
            color: '#f59e0b',
        },
        {
            id: 'cat-4',
            name: 'Product',
            description: 'Product documentation, feature specs, and roadmap',
            articleCount: 56,
            subcategories: ['Features', 'Roadmap', 'Analytics'],
            icon: '📦',
            color: '#8b5cf6',
        },
        {
            id: 'cat-5',
            name: 'Design',
            description: 'Design system, UI/UX guidelines, and design processes',
            articleCount: 34,
            subcategories: ['UI Components', 'UX Patterns', 'Brand'],
            icon: '🎨',
            color: '#ec4899',
        },
        {
            id: 'cat-6',
            name: 'Processes',
            description: 'Team processes, workflows, and operational procedures',
            articleCount: 41,
            subcategories: ['Development', 'Release', 'Support'],
            icon: '⚙️',
            color: '#6366f1',
        },
    ] as KnowledgeCategory[],

    contributors: [
        {
            id: 'contrib-1',
            name: 'Sarah Chen',
            role: 'Senior Engineer',
            team: 'Platform Engineering',
            articlesWritten: 15,
            articlesUpdated: 28,
            totalViews: 18500,
            totalLikes: 1240,
            expertise: ['ActiveJ', 'Performance', 'Java', 'Backend'],
            joinedDate: '2024-03-15',
            lastActive: '2024-12-10',
        },
        {
            id: 'contrib-2',
            name: 'Michael Rodriguez',
            role: 'Engineering Manager',
            team: 'Platform Engineering',
            articlesWritten: 12,
            articlesUpdated: 35,
            totalViews: 22000,
            totalLikes: 1580,
            expertise: ['Leadership', 'Onboarding', 'Team Building'],
            joinedDate: '2024-01-10',
            lastActive: '2024-12-09',
        },
        {
            id: 'contrib-3',
            name: 'Emily Watson',
            role: 'Principal Architect',
            team: 'Architecture',
            articlesWritten: 23,
            articlesUpdated: 42,
            totalViews: 31000,
            totalLikes: 2100,
            expertise: ['Architecture', 'API Design', 'System Design'],
            joinedDate: '2023-11-05',
            lastActive: '2024-12-08',
        },
        {
            id: 'contrib-4',
            name: 'David Kim',
            role: 'Staff Engineer',
            team: 'Data Engineering',
            articlesWritten: 8,
            articlesUpdated: 15,
            totalViews: 9200,
            totalLikes: 650,
            expertise: ['Database', 'Migration', 'Data Pipeline'],
            joinedDate: '2024-06-20',
            lastActive: '2024-12-10',
        },
    ] as KnowledgeContributor[],

    activities: [
        {
            id: 'activity-1',
            type: 'updated',
            description: 'Updated article with latest ActiveJ 6.0 features',
            articleTitle: 'ActiveJ Event Loop Best Practices',
            actor: 'Sarah Chen',
            timestamp: '2024-12-10T14:30:00Z',
        },
        {
            id: 'activity-2',
            type: 'created',
            description: 'Created new article on database migration strategy',
            articleTitle: 'Database Migration Strategy',
            actor: 'David Kim',
            timestamp: '2024-12-08T09:15:00Z',
        },
        {
            id: 'activity-3',
            type: 'commented',
            description: 'Added comment with additional examples',
            articleTitle: 'API Design Guidelines',
            actor: 'Michael Rodriguez',
            timestamp: '2024-12-07T16:45:00Z',
        },
        {
            id: 'activity-4',
            type: 'liked',
            description: 'Liked article on onboarding',
            articleTitle: 'Onboarding Guide for New Developers',
            actor: 'Emily Watson',
            timestamp: '2024-12-06T11:20:00Z',
        },
    ] as KnowledgeActivity[],
};

// ============================================================================
// Component
// ============================================================================

/**
 * KnowledgeBase Component
 */
export const KnowledgeBase: React.FC<KnowledgeBaseProps> = ({
    metrics,
    articles,
    categories,
    contributors,
    activities,
    onArticleClick,
    onCategoryClick,
    onContributorClick,
    onActivityClick,
    onCreateArticle,
    onSearch,
}) => {
    // State
    type KnowledgeTab = 'articles' | 'categories' | 'contributors' | 'activity';
    const [selectedTab, setSelectedTab] = useState<KnowledgeTab>('articles');
    const [statusFilter, setStatusFilter] = useState<'all' | 'published' | 'draft' | 'archived'>('all');
    const [difficultyFilter, setDifficultyFilter] = useState<'all' | 'beginner' | 'intermediate' | 'advanced'>('all');
    const [searchQuery, setSearchQuery] = useState('');

    // Handlers
    const handleTabChange = (value: KnowledgeTab) => {
        setSelectedTab(value);
    };

    const handleSearch = (event: React.ChangeEvent<HTMLInputElement>) => {
        const query = event.target.value;
        setSearchQuery(query);
        onSearch?.(query);
    };

    // Filtering
    let filteredArticles = articles;
    if (statusFilter !== 'all') {
        filteredArticles = filteredArticles.filter((article) => article.status === statusFilter);
    }
    if (difficultyFilter !== 'all') {
        filteredArticles = filteredArticles.filter((article) => article.difficulty === difficultyFilter);
    }
    if (searchQuery) {
        filteredArticles = filteredArticles.filter(
            (article) =>
                article.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                article.summary.toLowerCase().includes(searchQuery.toLowerCase()) ||
                article.tags.some((tag) => tag.toLowerCase().includes(searchQuery.toLowerCase()))
        );
    }

    // Counts
    const publishedCount = articles.filter((a) => a.status === 'published').length;
    const draftCount = articles.filter((a) => a.status === 'draft').length;
    const archivedCount = articles.filter((a) => a.status === 'archived').length;
    const beginnerCount = articles.filter((a) => a.difficulty === 'beginner').length;
    const intermediateCount = articles.filter((a) => a.difficulty === 'intermediate').length;
    const advancedCount = articles.filter((a) => a.difficulty === 'advanced').length;

    return (
        <Box className="p-6">
            {/* Header */}
            <Box className="mb-6">
                <Box className="flex items-center justify-between mb-2">
                    <Typography variant="h4" className="font-bold text-slate-900 dark:text-neutral-100">
                        Knowledge Base
                    </Typography>
                    <Button variant="contained" color="primary" onClick={onCreateArticle}>
                        Create Article
                    </Button>
                </Box>
                <Typography variant="body1" className="text-slate-600 dark:text-neutral-400">
                    Organizational knowledge hub with documentation, guides, and best practices
                </Typography>
            </Box>

            {/* Metrics */}
            <Grid container spacing={3} className="mb-6">
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Total Articles"
                        value={metrics.totalArticles.toString()}
                        trend="up"
                        trendValue="12%"
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Recent Updates"
                        value={metrics.recentUpdates.toString()}
                        trend="up"
                        trendValue="8%"
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Popular Topics"
                        value={metrics.popularTopics.toString()}
                        trend="neutral"
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Active Contributors"
                        value={metrics.activeContributors.toString()}
                        trend="up"
                        trendValue="5%"
                    />
                </Grid>
            </Grid>

            {/* Search Bar */}
            {selectedTab === 'articles' && (
                <Box className="mb-4">
                    <TextField
                        fullWidth
                        placeholder="Search articles by title, content, or tags..."
                        value={searchQuery}
                        onChange={handleSearch}
                        variant="outlined"
                    />
                </Box>
            )}

            {/* Tabs */}
            <Card>
                <Box className="border-b">
                    <Tabs
                        value={selectedTab}
                        onChange={(value: KnowledgeTab) => handleTabChange(value)}
                        tabs={[
                            { label: 'Articles', value: 'articles' },
                            { label: 'Categories', value: 'categories' },
                            { label: 'Contributors', value: 'contributors' },
                            { label: 'Activity', value: 'activity' },
                        ]}
                    />
                </Box>

                <Box className="p-4">
                    {/* Articles Tab */}
                    {selectedTab === 'articles' && (
                        <Box>
                            {/* Filters */}
                            <Box className="mb-4">
                                <Box className="mb-2">
                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500 mb-1">
                                        Status
                                    </Typography>
                                    <Stack direction="row" spacing={1}>
                                        <Chip
                                            label={`All (${articles.length})`}
                                            color={statusFilter === 'all' ? 'primary' : 'default'}
                                            onClick={() => setStatusFilter('all')}
                                            className="cursor-pointer"
                                        />
                                        <Chip
                                            label={`Published (${publishedCount})`}
                                            color={statusFilter === 'published' ? 'success' : 'default'}
                                            onClick={() => setStatusFilter('published')}
                                            className="cursor-pointer"
                                        />
                                        <Chip
                                            label={`Draft (${draftCount})`}
                                            color={statusFilter === 'draft' ? 'warning' : 'default'}
                                            onClick={() => setStatusFilter('draft')}
                                            className="cursor-pointer"
                                        />
                                        <Chip
                                            label={`Archived (${archivedCount})`}
                                            color={statusFilter === 'archived' ? 'default' : 'default'}
                                            onClick={() => setStatusFilter('archived')}
                                            className="cursor-pointer"
                                        />
                                    </Stack>
                                </Box>
                                <Box>
                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500 mb-1">
                                        Difficulty
                                    </Typography>
                                    <Stack direction="row" spacing={1}>
                                        <Chip
                                            label={`All (${articles.length})`}
                                            color={difficultyFilter === 'all' ? 'primary' : 'default'}
                                            onClick={() => setDifficultyFilter('all')}
                                            className="cursor-pointer"
                                        />
                                        <Chip
                                            label={`Beginner (${beginnerCount})`}
                                            color={difficultyFilter === 'beginner' ? 'success' : 'default'}
                                            onClick={() => setDifficultyFilter('beginner')}
                                            className="cursor-pointer"
                                        />
                                        <Chip
                                            label={`Intermediate (${intermediateCount})`}
                                            color={difficultyFilter === 'intermediate' ? 'warning' : 'default'}
                                            onClick={() => setDifficultyFilter('intermediate')}
                                            className="cursor-pointer"
                                        />
                                        <Chip
                                            label={`Advanced (${advancedCount})`}
                                            color={difficultyFilter === 'advanced' ? 'error' : 'default'}
                                            onClick={() => setDifficultyFilter('advanced')}
                                            className="cursor-pointer"
                                        />
                                    </Stack>
                                </Box>
                            </Box>

                            {/* Articles Grid */}
                            <Grid container spacing={3}>
                                {filteredArticles.map((article) => (
                                    <Grid item xs={12} md={6} key={article.id}>
                                        <Card
                                            className="cursor-pointer hover:shadow-md transition-shadow h-full"
                                            onClick={() => onArticleClick?.(article.id)}
                                        >
                                            <Box className="p-4">
                                                <Box className="flex items-start justify-between mb-2">
                                                    <Typography variant="h6" className="flex-1 text-slate-900 dark:text-neutral-100">
                                                        {article.title}
                                                    </Typography>
                                                    <Box className="flex gap-1 ml-2">
                                                        <Chip label={article.status} color={getStatusColor(article.status)} size="small" />
                                                        <Chip label={article.difficulty} color={getDifficultyColor(article.difficulty)} size="small" />
                                                    </Box>
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-3">
                                                    {article.summary}
                                                </Typography>
                                                <Box className="mb-3">
                                                    <Stack direction="row" spacing={1} className="flex-wrap gap-1">
                                                        {article.tags.slice(0, 4).map((tag) => (
                                                            <Chip key={tag} label={tag} size="small" variant="outlined" />
                                                        ))}
                                                    </Stack>
                                                </Box>
                                                <Grid container spacing={2}>
                                                    <Grid item xs={6}>
                                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                            Author
                                                        </Typography>
                                                        <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                            {article.author}
                                                        </Typography>
                                                    </Grid>
                                                    <Grid item xs={6}>
                                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                            Category
                                                        </Typography>
                                                        <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                            {article.category}
                                                        </Typography>
                                                    </Grid>
                                                    <Grid item xs={4}>
                                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                            Views
                                                        </Typography>
                                                        <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                            {formatNumber(article.views)}
                                                        </Typography>
                                                    </Grid>
                                                    <Grid item xs={4}>
                                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                            Likes
                                                        </Typography>
                                                        <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                            {article.likes}
                                                        </Typography>
                                                    </Grid>
                                                    <Grid item xs={4}>
                                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                            Read Time
                                                        </Typography>
                                                        <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                            {article.readTime}m
                                                        </Typography>
                                                    </Grid>
                                                    <Grid item xs={12}>
                                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                            Last Updated: {formatDate(article.lastUpdated)} • Version {article.version}
                                                        </Typography>
                                                    </Grid>
                                                </Grid>
                                            </Box>
                                        </Card>
                                    </Grid>
                                ))}
                            </Grid>

                            {filteredArticles.length === 0 && (
                                <Box className="text-center py-8">
                                    <Typography variant="body1" className="text-slate-500 dark:text-neutral-500">
                                        No articles found matching your filters
                                    </Typography>
                                </Box>
                            )}
                        </Box>
                    )}

                    {/* Categories Tab */}
                    {selectedTab === 'categories' && (
                        <Grid container spacing={3}>
                            {categories.map((category) => (
                                <Grid item xs={12} sm={6} md={4} key={category.id}>
                                    <Card
                                        className="cursor-pointer hover:shadow-md transition-shadow h-full"
                                        onClick={() => onCategoryClick?.(category.id)}
                                    >
                                        <Box className="p-4">
                                            <Box className="flex items-center gap-3 mb-3">
                                                <Box
                                                    className="text-4xl"
                                                    style={{
                                                        backgroundColor: `${category.color}20`,
                                                        borderRadius: '8px',
                                                        padding: '8px',
                                                    }}
                                                >
                                                    {category.icon}
                                                </Box>
                                                <Box className="flex-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {category.name}
                                                    </Typography>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                        {category.articleCount} articles
                                                    </Typography>
                                                </Box>
                                            </Box>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-3">
                                                {category.description}
                                            </Typography>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-500 mb-1">
                                                    Subcategories
                                                </Typography>
                                                <Stack direction="row" spacing={1} className="flex-wrap gap-1">
                                                    {category.subcategories.map((sub) => (
                                                        <Chip key={sub} label={sub} size="small" variant="outlined" />
                                                    ))}
                                                </Stack>
                                            </Box>
                                        </Box>
                                    </Card>
                                </Grid>
                            ))}
                        </Grid>
                    )}

                    {/* Contributors Tab */}
                    {selectedTab === 'contributors' && (
                        <Grid container spacing={3}>
                            {contributors.map((contributor) => (
                                <Grid item xs={12} sm={6} md={6} key={contributor.id}>
                                    <Card
                                        className="cursor-pointer hover:shadow-md transition-shadow"
                                        onClick={() => onContributorClick?.(contributor.id)}
                                    >
                                        <Box className="p-4">
                                            <Box className="flex items-center justify-between mb-3">
                                                <Box>
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {contributor.name}
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {contributor.role} • {contributor.team}
                                                    </Typography>
                                                </Box>
                                                <Chip
                                                    label={`Last active ${getTimeAgo(contributor.lastActive)}`}
                                                    size="small"
                                                    color="primary"
                                                />
                                            </Box>
                                            <Grid container spacing={2} className="mb-3">
                                                <Grid item xs={3}>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                        Written
                                                    </Typography>
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {contributor.articlesWritten}
                                                    </Typography>
                                                </Grid>
                                                <Grid item xs={3}>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                        Updated
                                                    </Typography>
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {contributor.articlesUpdated}
                                                    </Typography>
                                                </Grid>
                                                <Grid item xs={3}>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                        Views
                                                    </Typography>
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {formatNumber(contributor.totalViews)}
                                                    </Typography>
                                                </Grid>
                                                <Grid item xs={3}>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                        Likes
                                                    </Typography>
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {formatNumber(contributor.totalLikes)}
                                                    </Typography>
                                                </Grid>
                                            </Grid>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-500 mb-1">
                                                    Expertise
                                                </Typography>
                                                <Stack direction="row" spacing={1} className="flex-wrap gap-1">
                                                    {contributor.expertise.map((skill) => (
                                                        <Chip key={skill} label={skill} size="small" color="primary" />
                                                    ))}
                                                </Stack>
                                            </Box>
                                        </Box>
                                    </Card>
                                </Grid>
                            ))}
                        </Grid>
                    )}

                    {/* Activity Tab */}
                    {selectedTab === 'activity' && (
                        <Box>
                            {activities.map((activity) => (
                                <Card
                                    key={activity.id}
                                    className="mb-3 cursor-pointer hover:shadow-md transition-shadow"
                                    onClick={() => onActivityClick?.(activity.id)}
                                >
                                    <Box className="p-4">
                                        <Box className="flex items-start gap-3">
                                            <Chip
                                                label={activity.type}
                                                color={getActivityColor(activity.type)}
                                                size="small"
                                            />
                                            <Box className="flex-1">
                                                <Typography variant="body1" className="text-slate-900 dark:text-neutral-100 mb-1">
                                                    {activity.description}
                                                </Typography>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-2">
                                                    Article: {activity.articleTitle}
                                                </Typography>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                    {activity.actor} • {getTimeAgo(activity.timestamp)}
                                                </Typography>
                                            </Box>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Box>
                    )}
                </Box>
            </Card>
        </Box>
    );
};

export default KnowledgeBase;
