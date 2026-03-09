/**
 * Page Layouts Canvas Content
 * 
 * Page layout templates for Design × Component level.
 * Pre-built layout patterns and responsive templates.
 * 
 * @doc.type component
 * @doc.purpose Page layout template browser
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Button,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

interface PageLayout {
    id: string;
    name: string;
    description: string;
    category: 'marketing' | 'dashboard' | 'e-commerce' | 'blog' | 'auth' | 'admin';
    components: string[];
    responsive: boolean;
    complexity: 'simple' | 'moderate' | 'complex';
    tags?: string[];
}

// Mock page layouts
const MOCK_LAYOUTS: PageLayout[] = [
    {
        id: '1',
        name: 'Hero with Features',
        description: 'Landing page with hero section and feature grid',
        category: 'marketing',
        components: ['Header', 'Hero', 'FeatureGrid', 'CTA', 'Footer'],
        responsive: true,
        complexity: 'simple',
        tags: ['landing', 'saas', 'conversion'],
    },
    {
        id: '2',
        name: 'Analytics Dashboard',
        description: 'Multi-chart dashboard with sidebar navigation',
        category: 'dashboard',
        components: ['Sidebar', 'TopBar', 'ChartGrid', 'MetricsCards', 'DataTable'],
        responsive: true,
        complexity: 'complex',
        tags: ['analytics', 'data-viz', 'responsive'],
    },
    {
        id: '3',
        name: 'Product Catalog',
        description: 'E-commerce product listing with filters',
        category: 'e-commerce',
        components: ['Header', 'FilterSidebar', 'ProductGrid', 'Pagination', 'Footer'],
        responsive: true,
        complexity: 'moderate',
        tags: ['shop', 'catalog', 'filters'],
    },
    {
        id: '4',
        name: 'Blog Post',
        description: 'Article layout with sidebar and related posts',
        category: 'blog',
        components: ['Header', 'ArticleContent', 'Sidebar', 'RelatedPosts', 'Comments', 'Footer'],
        responsive: true,
        complexity: 'simple',
        tags: ['content', 'article', 'reading'],
    },
    {
        id: '5',
        name: 'Sign In / Sign Up',
        description: 'Centered authentication form with social login',
        category: 'auth',
        components: ['Logo', 'AuthForm', 'SocialButtons', 'Footer'],
        responsive: true,
        complexity: 'simple',
        tags: ['login', 'registration', 'oauth'],
    },
    {
        id: '6',
        name: 'Admin Panel',
        description: 'Full-featured admin interface with data management',
        category: 'admin',
        components: ['Sidebar', 'TopBar', 'Breadcrumbs', 'DataTable', 'ActionButtons', 'Modals'],
        responsive: true,
        complexity: 'complex',
        tags: ['crud', 'management', 'enterprise'],
    },
    {
        id: '7',
        name: 'Checkout Flow',
        description: 'Multi-step checkout with cart summary',
        category: 'e-commerce',
        components: ['Header', 'StepIndicator', 'CheckoutForm', 'CartSummary', 'PaymentMethods'],
        responsive: true,
        complexity: 'complex',
        tags: ['payment', 'conversion', 'multi-step'],
    },
    {
        id: '8',
        name: 'Pricing Page',
        description: 'Tiered pricing with feature comparison',
        category: 'marketing',
        components: ['Header', 'PricingTiers', 'ComparisonTable', 'FAQ', 'CTA', 'Footer'],
        responsive: true,
        complexity: 'moderate',
        tags: ['pricing', 'saas', 'conversion'],
    },
    {
        id: '9',
        name: 'User Profile',
        description: 'User settings and profile management',
        category: 'dashboard',
        components: ['Sidebar', 'ProfileHeader', 'TabNavigation', 'SettingsSections', 'SaveButtons'],
        responsive: true,
        complexity: 'moderate',
        tags: ['profile', 'settings', 'account'],
    },
    {
        id: '10',
        name: 'Blog Grid',
        description: 'Article grid with featured posts',
        category: 'blog',
        components: ['Header', 'FeaturedPost', 'ArticleGrid', 'CategoryFilter', 'Pagination', 'Footer'],
        responsive: true,
        complexity: 'simple',
        tags: ['blog', 'content', 'grid'],
    },
];

const getCategoryColor = (category: PageLayout['category']) => {
    switch (category) {
        case 'marketing':
            return '#6366F1';
        case 'dashboard':
            return '#8B5CF6';
        case 'e-commerce':
            return '#10B981';
        case 'blog':
            return '#F59E0B';
        case 'auth':
            return '#EF4444';
        case 'admin':
            return '#06B6D4';
    }
};

const getComplexityColor = (complexity: PageLayout['complexity']) => {
    switch (complexity) {
        case 'simple':
            return '#10B981';
        case 'moderate':
            return '#F59E0B';
        case 'complex':
            return '#EF4444';
    }
};

export const PageLayoutsCanvas = () => {
    const [layouts] = useState<PageLayout[]>(MOCK_LAYOUTS);
    const [selectedLayout, setSelectedLayout] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterCategory, setFilterCategory] = useState<PageLayout['category'] | 'all'>('all');

    const filteredLayouts = useMemo(() => {
        return layouts.filter(layout => {
            const matchesSearch =
                searchQuery === '' ||
                layout.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                layout.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
                layout.tags?.some(tag => tag.toLowerCase().includes(searchQuery.toLowerCase()));

            const matchesCategory = filterCategory === 'all' || layout.category === filterCategory;

            return matchesSearch && matchesCategory;
        });
    }, [layouts, searchQuery, filterCategory]);

    const stats = useMemo(() => {
        return {
            total: layouts.length,
            responsive: layouts.filter(l => l.responsive).length,
            byCategory: {
                marketing: layouts.filter(l => l.category === 'marketing').length,
                dashboard: layouts.filter(l => l.category === 'dashboard').length,
                'e-commerce': layouts.filter(l => l.category === 'e-commerce').length,
                blog: layouts.filter(l => l.category === 'blog').length,
                auth: layouts.filter(l => l.category === 'auth').length,
                admin: layouts.filter(l => l.category === 'admin').length,
            },
        };
    }, [layouts]);

    const hasContent = layouts.length > 0;

    const selectedLayoutData = layouts.find(l => l.id === selectedLayout);

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Create Layout',
                    onClick: () => {
                        console.log('Create Layout');
                    },
                },
                secondaryAction: {
                    label: 'Import Template',
                    onClick: () => {
                        console.log('Import Template');
                    },
                },
            }}
        >
            <Box
                className="relative h-full w-full flex flex-col bg-[#fafafa]"
            >
                {/* Top toolbar */}
                <Box
                    className="z-[10] p-4 bg-white" style={{ borderBottom: '1px solid rgba(0 }} >
                    <Box className="flex gap-4 items-center mb-2">
                        <TextField
                            size="small"
                            placeholder="Search layouts..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="flex-1"
                        />
                        <Button variant="outlined" size="small">
                            New Layout
                        </Button>
                        <Button variant="outlined" size="small">
                            Export
                        </Button>
                    </Box>

                    <Box className="flex gap-2 flex-wrap">
                        <Chip
                            label="All"
                            size="small"
                            onClick={() => setFilterCategory('all')}
                            color={filterCategory === 'all' ? 'primary' : 'default'}
                        />
                        {(['marketing', 'dashboard', 'e-commerce', 'blog', 'auth', 'admin'] as const).map(category => (
                            <Chip
                                key={category}
                                label={category}
                                size="small"
                                onClick={() => setFilterCategory(category)}
                                className="capitalize" style={{ backgroundColor: filterCategory === category ? getCategoryColor(category) : undefined, color: filterCategory === category ? 'white' : undefined, gridTemplateColumns: 'repeat(auto-fill }}
                            />
                        ))}
                    </Box>
                </Box>

                {/* Content area */}
                <Box
                    className="flex-1 overflow-y-auto p-4"
                >
                    {filteredLayouts.length === 0 && (
                        <Box className="flex justify-center items-center h-full">
                            <Typography color="text.secondary">No layouts match your search</Typography>
                        </Box>
                    )}

                    <Box className="grid gap-4" >
                        {filteredLayouts.map(layout => (
                            <Paper
                                key={layout.id}
                                elevation={selectedLayout === layout.id ? 6 : 2}
                                onClick={() => setSelectedLayout(layout.id === selectedLayout ? null : layout.id)}
                                className="p-4 cursor-pointer" style={{ border: selectedLayout === layout.id
                                            ? `3px solid ${getCategoryColor(layout.category), backgroundColor: getCategoryColor(layout.category) }}
                            >
                                {/* Layout preview mockup */}
                                <Box
                                    className="rounded flex flex-col gap-1 h-[160px] bg-[#F3F4F6] mb-3 p-2"
                                >
                                    {layout.components.slice(0, 5).map((_, idx) => (
                                        <Box
                                            key={idx}
                                            className="rounded-sm opacity-[0.3]" style={{ height: idx === 0 ? 40 : 20, backgroundColor: getComplexityColor(layout.complexity) }}
                                        />
                                    ))}
                                </Box>

                                <Typography variant="subtitle2" className="font-semibold mb-1">
                                    {layout.name}
                                </Typography>

                                <Typography variant="body2" color="text.secondary" className="text-[0.85rem] mb-2">
                                    {layout.description}
                                </Typography>

                                <Box className="flex gap-1 flex-wrap mb-2">
                                    <Chip
                                        label={layout.category}
                                        size="small"
                                        clx: backgroundColor: getCategoryColor(layout.category) */
                                    />
                                    <Chip
                                        label={layout.complexity}
                                        size="small"
                                        className="capitalize text-white h-[18px] text-[0.65rem]" />
                                    {layout.responsive && (
                                        <Chip label="📱 Responsive" size="small" variant="outlined" className="h-[18px] text-[0.65rem]" />
                                    )}
                                </Box>

                                <Typography variant="caption" color="text.secondary" className="text-[0.7rem]">
                                    {layout.components.length} components
                                </Typography>
                            </Paper>
                        ))}
                    </Box>
                </Box>

                {/* Stats panel */}
                <Box
                    className="absolute rounded top-[80px] right-[16px] bg-white p-4 shadow min-w-[180px]"
                >
                    <Typography variant="subtitle2" gutterBottom className="font-semibold">
                        Layout Library
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Total: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Responsive: {stats.responsive}
                    </Typography>
                    <Box className="mt-2">
                        {(Object.entries(stats.byCategory) as [PageLayout['category'], number][]).map(([category, count]) => (
                            <Typography
                                key={category}
                                variant="caption"
                                display="block"
                                className="capitalize" style={{ color: getCategoryColor(category) }} >
                                {category}: {count}
                            </Typography>
                        ))}
                    </Box>
                </Box>

                {/* Layout details */}
                {selectedLayoutData && (
                    <Box
                        className="absolute bottom-[16px] left-[16px] bg-white p-4 rounded shadow-lg min-w-[320px] max-w-[400px]" style={{ border: `3px solid ${getCategoryColor(selectedLayoutData.category), backgroundColor: getComplexityColor(selectedLayoutData.complexity), backgroundColor: getCategoryColor(selectedLayoutData.category) }}
                    >
                        <Typography variant="subtitle2" className="font-semibold mb-2">
                            {selectedLayoutData.name}
                        </Typography>

                        <Typography variant="body2" className="text-[0.85rem] mb-2">
                            {selectedLayoutData.description}
                        </Typography>

                        <Box className="flex gap-1 flex-wrap mb-2">
                            <Chip
                                label={selectedLayoutData.category}
                                size="smallx: backgroundColor: getCategoryColor(selectedLayoutData.category) */
                            />
                            <Chip
                                label={selectedLayoutData.complexity}
                                size="small"
                                className="capitalize text-white" />
                        </Box>

                        <Typography variant="caption" className="font-semibold block mb-1">
                            Components ({selectedLayoutData.components.length}):
                        </Typography>
                        <Box className="flex flex-wrap gap-1 mb-2">
                            {selectedLayoutData.components.map(component => (
                                <Chip key={component} label={component} size="small" variant="outlined" className="h-[18px] text-[0.65rem]" />
                            ))}
                        </Box>

                        {selectedLayoutData.tags && selectedLayoutData.tags.length > 0 && (
                            <>
                                <Typography variant="caption" className="font-semibold block mb-1">
                                    Tags:
                                </Typography>
                                <Box className="flex flex-wrap gap-1 mb-2">
                                    {selectedLayoutData.tags.map(tag => (
                                        <Chip key={tag} label={tag} size="small" variant="outlined" className="h-[18px] text-[0.65rem]" />
                                    ))}
                                </Box>
                            </>
                        )}

                        <Box className="flex gap-2 mt-2">
                            <Button variant="outlined" size="small" className="flex-1">
                                Use Template
                            </Button>
                            <Button variant="outlined" size="small" className="flex-1">
                                Preview
                            </Button>
                        </Box>
                    </Box>
                )}
            </Box>
        </BaseCanvasContent>
    );
};

export default PageLayoutsCanvas;
