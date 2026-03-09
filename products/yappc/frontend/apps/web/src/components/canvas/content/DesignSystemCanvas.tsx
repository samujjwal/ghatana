/**
 * Design System Canvas Content
 * 
 * Design system browser for Design × System level.
 * Centralized design tokens, components, and patterns.
 * 
 * @doc.type component
 * @doc.purpose Design system browser and documentation
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

interface DesignToken {
    id: string;
    name: string;
    value: string;
    category: 'color' | 'typography' | 'spacing' | 'shadow' | 'radius' | 'breakpoint';
    usage: string;
    preview?: string;
}

// Mock design tokens
const MOCK_DESIGN_TOKENS: DesignToken[] = [
    {
        id: '1',
        name: 'primary-600',
        value: '#6366F1',
        category: 'color',
        usage: 'Primary brand color for buttons, links',
        preview: '#6366F1',
    },
    {
        id: '2',
        name: 'primary-500',
        value: '#818CF8',
        category: 'color',
        usage: 'Hover state for primary actions',
        preview: '#818CF8',
    },
    {
        id: '3',
        name: 'success-600',
        value: '#10B981',
        category: 'color',
        usage: 'Success messages, positive states',
        preview: '#10B981',
    },
    {
        id: '4',
        name: 'error-600',
        value: '#EF4444',
        category: 'color',
        usage: 'Error messages, destructive actions',
        preview: '#EF4444',
    },
    {
        id: '5',
        name: 'warning-600',
        value: '#F59E0B',
        category: 'color',
        usage: 'Warning messages, caution states',
        preview: '#F59E0B',
    },
    {
        id: '6',
        name: 'neutral-900',
        value: '#111827',
        category: 'color',
        usage: 'Primary text color',
        preview: '#111827',
    },
    {
        id: '7',
        name: 'neutral-500',
        value: '#6B7280',
        category: 'color',
        usage: 'Secondary text, muted content',
        preview: '#6B7280',
    },
    {
        id: '8',
        name: 'heading-xl',
        value: '2.25rem / 2.5rem',
        category: 'typography',
        usage: 'Page titles, hero headings',
    },
    {
        id: '9',
        name: 'heading-lg',
        value: '1.875rem / 2.25rem',
        category: 'typography',
        usage: 'Section headings',
    },
    {
        id: '10',
        name: 'body-base',
        value: '1rem / 1.5rem',
        category: 'typography',
        usage: 'Body text, paragraphs',
    },
    {
        id: '11',
        name: 'body-sm',
        value: '0.875rem / 1.25rem',
        category: 'typography',
        usage: 'Small text, captions',
    },
    {
        id: '12',
        name: 'spacing-xs',
        value: '0.25rem (4px)',
        category: 'spacing',
        usage: 'Tight spacing between elements',
    },
    {
        id: '13',
        name: 'spacing-sm',
        value: '0.5rem (8px)',
        category: 'spacing',
        usage: 'Small gaps, padding',
    },
    {
        id: '14',
        name: 'spacing-md',
        value: '1rem (16px)',
        category: 'spacing',
        usage: 'Standard spacing unit',
    },
    {
        id: '15',
        name: 'spacing-lg',
        value: '1.5rem (24px)',
        category: 'spacing',
        usage: 'Section padding, large gaps',
    },
    {
        id: '16',
        name: 'spacing-xl',
        value: '2rem (32px)',
        category: 'spacing',
        usage: 'Page margins, major sections',
    },
    {
        id: '17',
        name: 'shadow-sm',
        value: '0 1px 2px rgba(0,0,0,0.05)',
        category: 'shadow',
        usage: 'Subtle elevation for cards',
    },
    {
        id: '18',
        name: 'shadow-md',
        value: '0 4px 6px rgba(0,0,0,0.1)',
        category: 'shadow',
        usage: 'Medium elevation for dropdowns',
    },
    {
        id: '19',
        name: 'shadow-lg',
        value: '0 10px 15px rgba(0,0,0,0.1)',
        category: 'shadow',
        usage: 'High elevation for modals',
    },
    {
        id: '20',
        name: 'radius-sm',
        value: '0.25rem (4px)',
        category: 'radius',
        usage: 'Small rounded corners',
    },
    {
        id: '21',
        name: 'radius-md',
        value: '0.5rem (8px)',
        category: 'radius',
        usage: 'Standard border radius',
    },
    {
        id: '22',
        name: 'radius-lg',
        value: '1rem (16px)',
        category: 'radius',
        usage: 'Large rounded corners',
    },
    {
        id: '23',
        name: 'breakpoint-sm',
        value: '640px',
        category: 'breakpoint',
        usage: 'Mobile devices',
    },
    {
        id: '24',
        name: 'breakpoint-md',
        value: '768px',
        category: 'breakpoint',
        usage: 'Tablets',
    },
    {
        id: '25',
        name: 'breakpoint-lg',
        value: '1024px',
        category: 'breakpoint',
        usage: 'Laptops',
    },
    {
        id: '26',
        name: 'breakpoint-xl',
        value: '1280px',
        category: 'breakpoint',
        usage: 'Desktops',
    },
];

const getCategoryColor = (category: DesignToken['category']) => {
    switch (category) {
        case 'color':
            return '#6366F1';
        case 'typography':
            return '#8B5CF6';
        case 'spacing':
            return '#10B981';
        case 'shadow':
            return '#F59E0B';
        case 'radius':
            return '#EC4899';
        case 'breakpoint':
            return '#06B6D4';
    }
};

const getCategoryIcon = (category: DesignToken['category']) => {
    switch (category) {
        case 'color':
            return '🎨';
        case 'typography':
            return '📝';
        case 'spacing':
            return '📏';
        case 'shadow':
            return '☁️';
        case 'radius':
            return '🔘';
        case 'breakpoint':
            return '📱';
    }
};

export const DesignSystemCanvas = () => {
    const [tokens] = useState<DesignToken[]>(MOCK_DESIGN_TOKENS);
    const [selectedToken, setSelectedToken] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterCategory, setFilterCategory] = useState<DesignToken['category'] | 'all'>('all');

    const filteredTokens = useMemo(() => {
        return tokens.filter(token => {
            const matchesSearch =
                searchQuery === '' ||
                token.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                token.value.toLowerCase().includes(searchQuery.toLowerCase()) ||
                token.usage.toLowerCase().includes(searchQuery.toLowerCase());

            const matchesCategory = filterCategory === 'all' || token.category === filterCategory;

            return matchesSearch && matchesCategory;
        });
    }, [tokens, searchQuery, filterCategory]);

    const groupedTokens = useMemo(() => {
        const groups: Record<DesignToken['category'], DesignToken[]> = {
            color: [],
            typography: [],
            spacing: [],
            shadow: [],
            radius: [],
            breakpoint: [],
        };
        filteredTokens.forEach(token => {
            groups[token.category].push(token);
        });
        return groups;
    }, [filteredTokens]);

    const stats = useMemo(() => {
        return {
            total: tokens.length,
            byCategory: {
                color: tokens.filter(t => t.category === 'color').length,
                typography: tokens.filter(t => t.category === 'typography').length,
                spacing: tokens.filter(t => t.category === 'spacing').length,
                shadow: tokens.filter(t => t.category === 'shadow').length,
                radius: tokens.filter(t => t.category === 'radius').length,
                breakpoint: tokens.filter(t => t.category === 'breakpoint').length,
            },
        };
    }, [tokens]);

    const hasContent = tokens.length > 0;

    const selectedTokenData = tokens.find(t => t.id === selectedToken);

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Create Design System',
                    onClick: () => {
                        console.log('Create Design System');
                    },
                },
                secondaryAction: {
                    label: 'Import Tokens',
                    onClick: () => {
                        console.log('Import Tokens');
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
                            placeholder="Search tokens..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="flex-1"
                        />
                        <Button variant="outlined" size="small">
                            Add Token
                        </Button>
                        <Button variant="outlined" size="small">
                            Export CSS
                        </Button>
                    </Box>

                    <Box className="flex gap-2 flex-wrap">
                        <Chip
                            label="All"
                            size="small"
                            onClick={() => setFilterCategory('all')}
                            color={filterCategory === 'all' ? 'primary' : 'default'}
                        />
                        {(['color', 'typography', 'spacing', 'shadow', 'radius', 'breakpoint'] as const).map(category => (
                            <Chip
                                key={category}
                                label={`${getCategoryIcon(category)} ${category}`}
                                size="small"
                                onClick={() => setFilterCategory(category)}
                                style={{ backgroundColor: filterCategory === category ? getCategoryColor(category) : undefined, color: filterCategory === category ? 'white' : undefined, backgroundColor: getCategoryColor(category) }}
                            />
                        ))}
                    </Box>
                </Box>

                {/* Content area */}
                <Box
                    className="flex-1 overflow-y-auto p-4"
                >
                    {filteredTokens.length === 0 && (
                        <Box className="flex justify-center items-center h-full">
                            <Typography color="text.secondary">No tokens match your search</Typography>
                        </Box>
                    )}

                    {(Object.entries(groupedTokens) as [DesignToken['category'], DesignToken[]][]).map(
                        ([category, categoryTokens]) =>
                            categoryTokens.length > 0 && (
                                <Box key={category} className="mb-6">
                                    <Box
                                        className="flex items-center gap-2 mb-3 pb-1" style={{ borderBottom: `2px solid ${getCategoryColor(category), alignItems: 'start', gridTemplateColumns: 'repeat(auto-fill }}
                                    >
                                        <Typography className="text-2xl">{getCategoryIcon(category)}</Typography>
                                        <Typography variant="subtitle2" className="font-semibold capitalize">
                                            {category}
                                        </Typography>
                                        <Chip
                                            label={categoryTokens.length}
                                            size="small" remaining sx: backgroundColor: getCategoryColor(category) */
                                        />
                                    </Box>

                                    <Box className="grid gap-2" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))' }}>
                                        {categoryTokens.map(token => (
                                            <Paper
                                                key={token.id}
                                                elevation={selectedToken === token.id ? 4 : 2}
                                                onClick={() => setSelectedToken(token.id === selectedToken ? null : token.id)}
                                                className="p-3 cursor-pointer" style={{ border: selectedToken === token.id
                                                            ? `3px solid ${getCategoryColor(category)}` : '2px solid transparent' }}
                                            >
                                                <Box className="flex gap-3">
                                                    {token.preview && (
                                                        <Box
                                                            className="rounded shrink-0 w-[40px] h-[40px]" style={{ backgroundColor: token.preview }}
                                                        />
                                                    )}
                                                    <Box className="flex-1 min-w-0">
                                                        <Typography
                                                            variant="subtitle2"
                                                            className="font-semibold text-[0.85rem] break-words font-mono mb-1"
                                                        >
                                                            {token.name}
                                                        </Typography>
                                                        <Typography
                                                            variant="body2"
                                                            className="text-xs break-words font-mono mb-1" >
                                                            {token.value}
                                                        </Typography>
                                                        <Typography variant="caption" color="text.secondary" className="text-[0.7rem]">
                                                            {token.usage}
                                                        </Typography>
                                                    </Box>
                                                </Box>
                                            </Paper>
                                        ))}
                                    </Box>
                                </Box>
                            )
                    )}
                </Box>

                {/* Stats panel */}
                <Box
                    className="absolute rounded top-[80px] right-[16px] bg-white p-4 shadow min-w-[200px]"
                >
                    <Typography variant="subtitle2" gutterBottom className="font-semibold">
                        Design Tokens
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Total: {stats.total}
                    </Typography>
                    <Box className="mt-2">
                        {(Object.entries(stats.byCategory) as [DesignToken['category'], number][]).map(([category, count]) => (
                            <Typography
                                key={category}
                                variant="caption"
                                display="block"
                                className="capitalize" style={{ color: getCategoryColor(category) }} >
                                {getCategoryIcon(category)} {category}: {count}
                            </Typography>
                        ))}
                    </Box>
                </Box>

                {/* Token details modal */}
                {selectedTokenData && (
                    <Box
                        className="absolute bottom-[16px] left-[16px] bg-white p-4 rounded shadow-lg min-w-[320px] max-w-[400px]" style={{ border: `3px solid ${getCategoryColor(selectedTokenData.category), backgroundColor: 'selectedTokenData.preview' }}
                    >
                        <Box className="flex items-center gap-2 mb-2">
                            <Typography className="text-2xl">{getCategoryIcon(selectedTokenData.category)}</Typography>
                            <Typography variant="subtitle2" className="font-semibold">
                                Selected Token
                            </Typography>
                        </Box>

                        <Typography
                            variant="body2"
                            className="font-semibold font-mono text-[0.9rem] mb-2"
                        >
                            {selectedTokenData.name}
                        </Typography>

                        {selectedTokenData.preview && (
                            <Box
                                className="w-full rounded h-[60px] border-['2px_solid_rgba(0] mb-2" />
                        )}

                        <Paper
                            className="p-2 bg-[#F3F4F6] mb-2"
                        >
                            <Typography
                                variant="caption"
                                className="text-xs block font-mono"
                            >
                                Value: {selectedTokenData.value}
                            </Typography>
                        </Paper>

                        <Typography variant="caption" color="text.secondary" className="text-xs">
                            Usage: {selectedTokenData.usage}
                        </Typography>

                        <Box className="flex gap-2 mt-2">
                            <Button variant="outlined" size="small" className="flex-1">
                                Copy
                            </Button>
                            <Button variant="outlined" size="small" className="flex-1">
                                Edit
                            </Button>
                        </Box>
                    </Box>
                )}
            </Box>
        </BaseCanvasContent>
    );
};

export default DesignSystemCanvas;
