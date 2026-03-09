/**
 * Component Specs Canvas Content
 * 
 * Component specifications for Design × File level.
 * Detailed component documentation and API reference.
 * 
 * @doc.type component
 * @doc.purpose Component specification documentation
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

interface ComponentSpec {
    id: string;
    name: string;
    description: string;
    category: 'form' | 'layout' | 'navigation' | 'feedback' | 'data-display' | 'utility';
    props: PropSpec[];
    variants: string[];
    accessibility: {
        ariaLabels: boolean;
        keyboardNav: boolean;
        screenReader: boolean;
    };
    dependencies: string[];
    examples: number;
    status: 'stable' | 'beta' | 'deprecated';
}

interface PropSpec {
    name: string;
    type: string;
    required: boolean;
    default?: string;
    description: string;
}

// Mock component specs
const MOCK_SPECS: ComponentSpec[] = [
    {
        id: '1',
        name: 'Button',
        description: 'Primary interactive element for user actions',
        category: 'form',
        props: [
            { name: 'variant', type: 'primary | secondary | ghost', required: false, default: 'primary', description: 'Visual style variant' },
            { name: 'size', type: 'sm | md | lg', required: false, default: 'md', description: 'Button size' },
            { name: 'disabled', type: 'boolean', required: false, default: 'false', description: 'Disabled state' },
            { name: 'onClick', type: '() => void', required: true, description: 'Click handler' },
            { name: 'loading', type: 'boolean', required: false, default: 'false', description: 'Loading state with spinner' },
        ],
        variants: ['primary', 'secondary', 'ghost', 'danger'],
        accessibility: { ariaLabels: true, keyboardNav: true, screenReader: true },
        dependencies: ['Spinner', 'Icon'],
        examples: 8,
        status: 'stable',
    },
    {
        id: '2',
        name: 'Modal',
        description: 'Overlay dialog for focused user interactions',
        category: 'feedback',
        props: [
            { name: 'open', type: 'boolean', required: true, description: 'Visibility state' },
            { name: 'onClose', type: '() => void', required: true, description: 'Close handler' },
            { name: 'title', type: 'string', required: false, description: 'Modal title' },
            { name: 'size', type: 'sm | md | lg | full', required: false, default: 'md', description: 'Modal size' },
            { name: 'closeOnOverlay', type: 'boolean', required: false, default: 'true', description: 'Close on backdrop click' },
        ],
        variants: ['default', 'fullscreen', 'drawer'],
        accessibility: { ariaLabels: true, keyboardNav: true, screenReader: true },
        dependencies: ['Portal', 'FocusTrap', 'Overlay'],
        examples: 6,
        status: 'stable',
    },
    {
        id: '3',
        name: 'DataGrid',
        description: 'Advanced table with sorting, filtering, and pagination',
        category: 'data-display',
        props: [
            { name: 'data', type: 'T[]', required: true, description: 'Array of data items' },
            { name: 'columns', type: 'Column<T>[]', required: true, description: 'Column definitions' },
            { name: 'onSort', type: '(field: string) => void', required: false, description: 'Sort handler' },
            { name: 'onFilter', type: '(filters: Filter[]) => void', required: false, description: 'Filter handler' },
            { name: 'pagination', type: 'PaginationConfig', required: false, description: 'Pagination settings' },
        ],
        variants: ['default', 'compact', 'striped'],
        accessibility: { ariaLabels: true, keyboardNav: true, screenReader: true },
        dependencies: ['Table', 'Pagination', 'FilterPanel', 'SortIcon'],
        examples: 12,
        status: 'stable',
    },
    {
        id: '4',
        name: 'Sidebar',
        description: 'Side navigation panel with collapsible sections',
        category: 'navigation',
        props: [
            { name: 'items', type: 'NavItem[]', required: true, description: 'Navigation items' },
            { name: 'collapsed', type: 'boolean', required: false, default: 'false', description: 'Collapsed state' },
            { name: 'onNavigate', type: '(path: string) => void', required: true, description: 'Navigation handler' },
            { name: 'activeItem', type: 'string', required: false, description: 'Active item ID' },
        ],
        variants: ['default', 'mini', 'floating'],
        accessibility: { ariaLabels: true, keyboardNav: true, screenReader: true },
        dependencies: ['NavLink', 'Icon', 'Tooltip'],
        examples: 5,
        status: 'stable',
    },
    {
        id: '5',
        name: 'Toast',
        description: 'Temporary notification message',
        category: 'feedback',
        props: [
            { name: 'message', type: 'string', required: true, description: 'Notification message' },
            { name: 'type', type: 'success | error | warning | info', required: false, default: 'info', description: 'Toast type' },
            { name: 'duration', type: 'number', required: false, default: '3000', description: 'Display duration (ms)' },
            { name: 'position', type: 'Position', required: false, default: 'top-right', description: 'Screen position' },
            { name: 'onClose', type: '() => void', required: false, description: 'Close callback' },
        ],
        variants: ['default', 'action', 'persistent'],
        accessibility: { ariaLabels: true, keyboardNav: false, screenReader: true },
        dependencies: ['Icon', 'Portal'],
        examples: 4,
        status: 'stable',
    },
    {
        id: '6',
        name: 'Tabs',
        description: 'Tabbed interface for content organization',
        category: 'navigation',
        props: [
            { name: 'tabs', type: 'TabConfig[]', required: true, description: 'Tab definitions' },
            { name: 'activeTab', type: 'string', required: true, description: 'Active tab ID' },
            { name: 'onTabChange', type: '(id: string) => void', required: true, description: 'Tab change handler' },
            { name: 'variant', type: 'default | pills | underline', required: false, default: 'default', description: 'Tab style' },
        ],
        variants: ['default', 'pills', 'underline', 'vertical'],
        accessibility: { ariaLabels: true, keyboardNav: true, screenReader: true },
        dependencies: ['TabPanel'],
        examples: 7,
        status: 'stable',
    },
    {
        id: '7',
        name: 'Dropdown',
        description: 'Contextual menu with actions',
        category: 'utility',
        props: [
            { name: 'trigger', type: 'ReactNode', required: true, description: 'Trigger element' },
            { name: 'items', type: 'MenuItem[]', required: true, description: 'Menu items' },
            { name: 'position', type: 'Position', required: false, default: 'bottom-left', description: 'Dropdown position' },
            { name: 'onSelect', type: '(item: MenuItem) => void', required: true, description: 'Selection handler' },
        ],
        variants: ['default', 'context-menu'],
        accessibility: { ariaLabels: true, keyboardNav: true, screenReader: true },
        dependencies: ['Portal', 'Menu'],
        examples: 5,
        status: 'beta',
    },
    {
        id: '8',
        name: 'Card',
        description: 'Container for grouping related content',
        category: 'layout',
        props: [
            { name: 'children', type: 'ReactNode', required: true, description: 'Card content' },
            { name: 'title', type: 'string', required: false, description: 'Card title' },
            { name: 'footer', type: 'ReactNode', required: false, description: 'Card footer' },
            { name: 'elevation', type: 'number', required: false, default: '1', description: 'Shadow elevation (0-4)' },
            { name: 'onClick', type: '() => void', required: false, description: 'Click handler (makes card interactive)' },
        ],
        variants: ['default', 'outlined', 'elevated'],
        accessibility: { ariaLabels: false, keyboardNav: false, screenReader: true },
        dependencies: [],
        examples: 6,
        status: 'stable',
    },
];

const getCategoryColor = (category: ComponentSpec['category']) => {
    switch (category) {
        case 'form':
            return '#6366F1';
        case 'layout':
            return '#8B5CF6';
        case 'navigation':
            return '#10B981';
        case 'feedback':
            return '#F59E0B';
        case 'data-display':
            return '#06B6D4';
        case 'utility':
            return '#EC4899';
    }
};

const getStatusColor = (status: ComponentSpec['status']) => {
    switch (status) {
        case 'stable':
            return '#10B981';
        case 'beta':
            return '#F59E0B';
        case 'deprecated':
            return '#EF4444';
    }
};

export const ComponentSpecsCanvas = () => {
    const [specs] = useState<ComponentSpec[]>(MOCK_SPECS);
    const [selectedSpec, setSelectedSpec] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterCategory, setFilterCategory] = useState<ComponentSpec['category'] | 'all'>('all');

    const filteredSpecs = useMemo(() => {
        return specs.filter(spec => {
            const matchesSearch =
                searchQuery === '' ||
                spec.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                spec.description.toLowerCase().includes(searchQuery.toLowerCase());

            const matchesCategory = filterCategory === 'all' || spec.category === filterCategory;

            return matchesSearch && matchesCategory;
        });
    }, [specs, searchQuery, filterCategory]);

    const stats = useMemo(() => {
        return {
            total: specs.length,
            stable: specs.filter(s => s.status === 'stable').length,
            beta: specs.filter(s => s.status === 'beta').length,
            byCategory: {
                form: specs.filter(s => s.category === 'form').length,
                layout: specs.filter(s => s.category === 'layout').length,
                navigation: specs.filter(s => s.category === 'navigation').length,
                feedback: specs.filter(s => s.category === 'feedback').length,
                'data-display': specs.filter(s => s.category === 'data-display').length,
                utility: specs.filter(s => s.category === 'utility').length,
            },
        };
    }, [specs]);

    const hasContent = specs.length > 0;

    const selectedSpecData = specs.find(s => s.id === selectedSpec);

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Document Component',
                    onClick: () => {
                        console.log('Document Component');
                    },
                },
                secondaryAction: {
                    label: 'Import Docs',
                    onClick: () => {
                        console.log('Import Docs');
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
                            placeholder="Search components..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="flex-1"
                        />
                        <Button variant="outlined" size="small">
                            Add Spec
                        </Button>
                        <Button variant="outlined" size="small">
                            Generate Docs
                        </Button>
                    </Box>

                    <Box className="flex gap-2 flex-wrap">
                        <Chip
                            label="All"
                            size="small"
                            onClick={() => setFilterCategory('all')}
                            color={filterCategory === 'all' ? 'primary' : 'default'}
                        />
                        {(['form', 'layout', 'navigation', 'feedback', 'data-display', 'utility'] as const).map(category => (
                            <Chip
                                key={category}
                                label={category}
                                size="small"
                                onClick={() => setFilterCategory(category)}
                                style={{ backgroundColor: filterCategory === category ? getCategoryColor(category) : undefined, color: filterCategory === category ? 'white' : undefined, alignItems: 'start' }}
                            />
                        ))}
                    </Box>
                </Box>

                {/* Content area */}
                <Box
                    className="flex-1 flex gap-4 overflow-hidden p-4"
                >
                    {/* Component list */}
                    <Box
                        className="overflow-y-auto transition-all duration-300" style={{ width: selectedSpecData ? '35%' : '100%', backgroundColor: getStatusColor(spec.status) }}
                    >
                        {filteredSpecs.length === 0 && (
                            <Box className="flex justify-center items-center h-full">
                                <Typography color="text.secondary">No components match your search</Typography>
                            </Box>
                        )}

                        {filteredSpecs.map(spec => (
                            <Paper
                                key={spec.id}
                                elevation={selectedSpec === spec.id ? 4 : 2}
                                onClick={() => setSelectedSpec(spec.id === selectedSpec ? null : spec.id)}
                                className="p-4 mb-3 cursor-pointer" style={{ border: selectedSpec === spec.id ? `3px solid ${getCategoryColor(spec.category)}` : '2px solid transparent' }}
                            >
                                <Box className="flex justify-between mb-1">
                                    <Typography variant="subtitle2" className="font-semibold font-mono">
                                        {spec.name}
                                    </Typography>
                                    <Chip
                                        label={spec.status}
                                        size="small"
                                    />
                                </Box>

                                <Typography variant="body2" color="text.secondary" className="text-[0.85rem] mb-2">
                                    {spec.description}
                                </Typography>

                                <Box className="flex gap-1 flex-wrap mb-1">
                                    <Chip
                                        label={spec.category}
                                        size="small"
                                        className="h-[18px] text-white text-[0.65rem]" />
                                    <Chip label={`${spec.props.length} props`} size="small" variant="outlined" className="h-[18px] text-[0.65rem]" />
                                    <Chip label={`${spec.examples} examples`} size="small" variant="outlined" className="h-[18px] text-[0.65rem]" />
                                </Box>

                                {(spec.accessibility.ariaLabels || spec.accessibility.keyboardNav || spec.accessibility.screenReader) && (
                                    <Typography variant="caption" color="success.main" className="text-[0.7rem]">
                                        ✓ Accessible
                                    </Typography>
                                )}
                            </Paper>
                        ))}
                    </Box>

                    {/* Detailed spec view */}
                    {selectedSpecData && (
                        <Box
                            className="overflow-y-auto w-[65%]"
                        >
                            <Paper elevation={3} className="p-6">
                                <Box className="flex justify-between mb-4" style={{ alignItems: 'start' }} >
                                    <Box>
                                        <Typography variant="h6" className="font-semibold font-mono mb-1">
                                            {selectedSpecData.name}
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            {selectedSpecData.description}
                                        </Typography>
                                    </Box>
                                    <Chip
                                        label={selectedSpecData.status}
                                        className="text-white" style={{ backgroundColor: getStatusColor(selectedSpecData.status) }}
                                    />
                                </Box>

                                <Box className="flex gap-2 mb-4">
                                    <Chip
                                        label={selectedSpecData.category}
                                        size="small"
                                        className="text-white" style={{ backgroundColor: getCategoryColor(selectedSpecData.category) }}
                                    />
                                    {selectedSpecData.variants.map(variant => (
                                        <Chip key={variant} label={variant} size="small" variant="outlined" />
                                    ))}
                                </Box>

                                <Typography variant="subtitle2" className="font-semibold mb-2">
                                    Props ({selectedSpecData.props.length})
                                </Typography>
                                <Paper className="p-4 bg-[#F9FAFB] mb-4">
                                    {selectedSpecData.props.map((prop, idx) => (
                                        <Box key={prop.name} style={{ marginBottom: idx < selectedSpecData.props.length - 1 ? 2 : 0 }}>
                                            <Box className="flex gap-2 items-center mb-1">
                                                <Typography
                                                    variant="body2"
                                                    className="font-semibold text-[0.85rem] font-mono"
                                                >
                                                    {prop.name}
                                                </Typography>
                                                {prop.required && (
                                                    <Chip label="required" size="small" color="error" className="h-[16px] text-[0.6rem]" />
                                                )}
                                                <Typography
                                                    variant="caption"
                                                    className="text-xs font-mono text-gray-500 dark:text-gray-400"
                                                >
                                                    {prop.type}
                                                </Typography>
                                            </Box>
                                            <Typography variant="caption" color="text.secondary" className="text-xs">
                                                {prop.description}
                                            </Typography>
                                            {prop.default && (
                                                <Typography
                                                    variant="caption"
                                                    className="block font-mono text-blue-600 text-[0.7rem]"
                                                >
                                                    Default: {prop.default}
                                                </Typography>
                                            )}
                                        </Box>
                                    ))}
                                </Paper>

                                <Typography variant="subtitle2" className="font-semibold mb-2">
                                    Accessibility
                                </Typography>
                                <Box className="flex gap-2 mb-4">
                                    {selectedSpecData.accessibility.ariaLabels && (
                                        <Chip label="ARIA Labels" size="small" color="success" />
                                    )}
                                    {selectedSpecData.accessibility.keyboardNav && (
                                        <Chip label="Keyboard Nav" size="small" color="success" />
                                    )}
                                    {selectedSpecData.accessibility.screenReader && (
                                        <Chip label="Screen Reader" size="small" color="success" />
                                    )}
                                </Box>

                                {selectedSpecData.dependencies.length > 0 && (
                                    <>
                                        <Typography variant="subtitle2" className="font-semibold mb-2">
                                            Dependencies ({selectedSpecData.dependencies.length})
                                        </Typography>
                                        <Box className="flex flex-wrap gap-1 mb-4">
                                            {selectedSpecData.dependencies.map(dep => (
                                                <Chip key={dep} label={dep} size="small" variant="outlined" />
                                            ))}
                                        </Box>
                                    </>
                                )}

                                <Box className="flex gap-2">
                                    <Button variant="outlined" size="small">
                                        View Examples ({selectedSpecData.examples})
                                    </Button>
                                    <Button variant="outlined" size="small">
                                        Edit Spec
                                    </Button>
                                </Box>
                            </Paper>
                        </Box>
                    )}
                </Box>

                {/* Stats panel */}
                <Box
                    className="absolute rounded top-[80px] right-[16px] bg-white p-4 shadow min-w-[180px]"
                >
                    <Typography variant="subtitle2" gutterBottom className="font-semibold">
                        Component Library
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Total: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('stable') }}>
                        Stable: {stats.stable}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('beta') }}>
                        Beta: {stats.beta}
                    </Typography>
                    <Box className="mt-2">
                        {(Object.entries(stats.byCategory) as [ComponentSpec['category'], number][]).map(([category, count]) => (
                            <Typography
                                key={category}
                                variant="caption"
                                display="block"
                                style={{ color: getCategoryColor(category) }}
                            >
                                {category}: {count}
                            </Typography>
                        ))}
                    </Box>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default ComponentSpecsCanvas;
