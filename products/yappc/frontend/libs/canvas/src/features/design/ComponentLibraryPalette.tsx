/**
 * Component Library Palette
 * 
 * Provides a draggable component palette for the design mode editor.
 * Part of Journey 5.1: UX Designer - High-Fidelity Mockups.
 * 
 * @doc.type component
 * @doc.purpose Component library palette for design mode
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState } from 'react';
import { Surface as Paper, Box, Typography, InteractiveList as List, ListItem, ListItemButton, ListItemIcon, ListItemText, Collapse, TextField, InputAdornment, Chip, Tooltip, Divider } from '@ghatana/ui';
import { ChevronDown as ExpandIcon, ChevronUp as CollapseIcon, Search as SearchIcon, UserCircle as AvatarIcon, Type as TextFieldIcon, CheckBox as CheckBoxIcon, CircleDot as RadioIcon, Image as ImageIcon, SmartButton as ButtonIcon, List as ListIcon, Columns as ColumnIcon, Tab as TabsIcon, ArrowDropDown as DropdownIcon, DateRange as DatePickerIcon, ToggleOn as SwitchIcon, LinearScale as SliderIcon, CardGiftcard as CardIcon, Table as TableIcon, Sparkles as IconButtonIcon } from 'lucide-react';

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

/**
 * Component category
 */
export type ComponentCategory =
    | 'inputs'
    | 'display'
    | 'feedback'
    | 'navigation'
    | 'layout'
    | 'data';

/**
 * Component definition
 */
export interface ComponentDefinition {
    id: string;
    type: string;
    label: string;
    icon: React.ReactNode;
    category: ComponentCategory;
    description: string;
    defaultProps?: Record<string, unknown>;
    tags: string[];
}

/**
 * Component props
 */
export interface ComponentLibraryPaletteProps {
    /**
     * Component selection handler
     */
    onSelectComponent: (component: ComponentDefinition) => void;

    /**
     * Whether drag mode is enabled
     */
    dragEnabled?: boolean;

    /**
     * Custom component definitions
     */
    customComponents?: ComponentDefinition[];

    /**
     * Filter by tags
     */
    filterTags?: string[];
}

// ============================================================================
// COMPONENT LIBRARY
// ============================================================================

const BUILT_IN_COMPONENTS: ComponentDefinition[] = [
    // Inputs
    {
        id: 'textfield',
        type: 'textfield',
        label: 'Text Field',
        icon: <TextFieldIcon />,
        category: 'inputs',
        description: 'Single-line text input',
        defaultProps: { placeholder: 'Enter text', variant: 'outlined' },
        tags: ['input', 'form', 'text'],
    },
    {
        id: 'button',
        type: 'button',
        label: 'Button',
        icon: <ButtonIcon />,
        category: 'inputs',
        description: 'Clickable button',
        defaultProps: { label: 'Button', variant: 'contained', color: 'primary' },
        tags: ['input', 'action', 'form'],
    },
    {
        id: 'checkbox',
        type: 'checkbox',
        label: 'Checkbox',
        icon: <CheckBoxIcon />,
        category: 'inputs',
        description: 'Checkbox input',
        defaultProps: { label: 'Checkbox', checked: false },
        tags: ['input', 'form', 'boolean'],
    },
    {
        id: 'radio',
        type: 'radio',
        label: 'Radio Button',
        icon: <RadioIcon />,
        category: 'inputs',
        description: 'Radio button input',
        defaultProps: { label: 'Radio', value: '' },
        tags: ['input', 'form', 'select'],
    },
    {
        id: 'dropdown',
        type: 'dropdown',
        label: 'Dropdown',
        icon: <DropdownIcon />,
        category: 'inputs',
        description: 'Dropdown select',
        defaultProps: { label: 'Select', options: [] },
        tags: ['input', 'form', 'select'],
    },
    {
        id: 'datepicker',
        type: 'datepicker',
        label: 'Date Picker',
        icon: <DatePickerIcon />,
        category: 'inputs',
        description: 'Date picker input',
        defaultProps: { label: 'Date' },
        tags: ['input', 'form', 'date'],
    },
    {
        id: 'switch',
        type: 'switch',
        label: 'Switch',
        icon: <SwitchIcon />,
        category: 'inputs',
        description: 'Toggle switch',
        defaultProps: { label: 'Switch', checked: false },
        tags: ['input', 'toggle', 'boolean'],
    },
    {
        id: 'slider',
        type: 'slider',
        label: 'Slider',
        icon: <SliderIcon />,
        category: 'inputs',
        description: 'Range slider',
        defaultProps: { min: 0, max: 100, value: 50 },
        tags: ['input', 'range', 'number'],
    },

    // Display
    {
        id: 'avatar',
        type: 'avatar',
        label: 'Avatar',
        icon: <AvatarIcon />,
        category: 'display',
        description: 'User avatar',
        defaultProps: { src: '', alt: 'Avatar', size: 'medium' },
        tags: ['display', 'user', 'image'],
    },
    {
        id: 'image',
        type: 'image',
        label: 'Image',
        icon: <ImageIcon />,
        category: 'display',
        description: 'Image component',
        defaultProps: { src: '', alt: 'Image' },
        tags: ['display', 'media'],
    },
    {
        id: 'text',
        type: 'text',
        label: 'Text',
        icon: <TextFieldIcon />,
        category: 'display',
        description: 'Static text',
        defaultProps: { content: 'Text', variant: 'body1' },
        tags: ['display', 'typography'],
    },
    {
        id: 'iconbutton',
        type: 'iconbutton',
        label: 'Icon Button',
        icon: <IconButtonIcon />,
        category: 'display',
        description: 'Icon-only button',
        defaultProps: { icon: 'star', size: 'medium' },
        tags: ['input', 'action', 'icon'],
    },

    // Feedback
    {
        id: 'card',
        type: 'card',
        label: 'Card',
        icon: <CardIcon />,
        category: 'feedback',
        description: 'Content card',
        defaultProps: { title: 'Card Title', content: 'Card content' },
        tags: ['layout', 'container', 'content'],
    },

    // Navigation
    {
        id: 'tabs',
        type: 'tabs',
        label: 'Tabs',
        icon: <TabsIcon />,
        category: 'navigation',
        description: 'Tab navigation',
        defaultProps: { tabs: ['Tab 1', 'Tab 2', 'Tab 3'] },
        tags: ['navigation', 'tabs'],
    },

    // Layout
    {
        id: 'container',
        type: 'container',
        label: 'Container',
        icon: <ColumnIcon />,
        category: 'layout',
        description: 'Layout container',
        defaultProps: { direction: 'column', spacing: 2 },
        tags: ['layout', 'container'],
    },

    // Data
    {
        id: 'list',
        type: 'list',
        label: 'List',
        icon: <ListIcon />,
        category: 'data',
        description: 'List of items',
        defaultProps: { items: [] },
        tags: ['data', 'list', 'display'],
    },
    {
        id: 'table',
        type: 'table',
        label: 'Table',
        icon: <TableIcon />,
        category: 'data',
        description: 'Data table',
        defaultProps: { columns: [], rows: [] },
        tags: ['data', 'table', 'display'],
    },
];

// ============================================================================
// COMPONENT IMPLEMENTATION
// ============================================================================

/**
 * Component Library Palette
 * 
 * Usage:
 * ```tsx
 * <ComponentLibraryPalette
 *   onSelectComponent={(component) => console.log(component)}
 *   dragEnabled={true}
 * />
 * ```
 */
export const ComponentLibraryPalette: React.FC<ComponentLibraryPaletteProps> = ({
    onSelectComponent,
    dragEnabled = true,
    customComponents = [],
    filterTags = [],
}) => {
    const [searchQuery, setSearchQuery] = useState('');
    const [expandedCategories, setExpandedCategories] = useState<Set<ComponentCategory>>(
        new Set(['inputs', 'display'])
    );

    // Merge built-in and custom components
    const allComponents = [...BUILT_IN_COMPONENTS, ...customComponents];

    // Filter components by search query and tags
    const filteredComponents = allComponents.filter(component => {
        const matchesSearch =
            searchQuery === '' ||
            component.label.toLowerCase().includes(searchQuery.toLowerCase()) ||
            component.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
            component.tags.some(tag => tag.toLowerCase().includes(searchQuery.toLowerCase()));

        const matchesTags =
            filterTags.length === 0 ||
            filterTags.some(tag => component.tags.includes(tag));

        return matchesSearch && matchesTags;
    });

    // Group components by category
    const componentsByCategory = filteredComponents.reduce((acc, component) => {
        if (!acc[component.category]) {
            acc[component.category] = [];
        }
        acc[component.category].push(component);
        return acc;
    }, {} as Record<ComponentCategory, ComponentDefinition[]>);

    // Toggle category expansion
    const toggleCategory = (category: ComponentCategory) => {
        setExpandedCategories(prev => {
            const newSet = new Set(prev);
            if (newSet.has(category)) {
                newSet.delete(category);
            } else {
                newSet.add(category);
            }
            return newSet;
        });
    };

    // Handle component drag start
    const handleDragStart = (component: ComponentDefinition) => (e: React.DragEvent) => {
        if (!dragEnabled) return;
        e.dataTransfer.effectAllowed = 'copy';
        e.dataTransfer.setData('application/json', JSON.stringify(component));
    };

    // Category labels
    const categoryLabels: Record<ComponentCategory, string> = {
        inputs: 'Inputs',
        display: 'Display',
        feedback: 'Feedback',
        navigation: 'Navigation',
        layout: 'Layout',
        data: 'Data',
    };

    return (
        <Paper
            elevation={2}
            className="h-full flex flex-col overflow-hidden w-[280px]"
        >
            {/* Header */}
            <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                <Typography as="h6" gutterBottom>
                    Component Library
                </Typography>

                {/* Search */}
                <TextField
                    fullWidth
                    size="sm"
                    placeholder="Search components..."
                    value={searchQuery}
                    onChange={e => setSearchQuery(e.target.value)}
                    InputProps={{
                        startAdornment: (
                            <InputAdornment position="start">
                                <SearchIcon size={16} />
                            </InputAdornment>
                        ),
                    }}
                />

                {/* Tag Filters */}
                {filterTags.length > 0 && (
                    <Box className="flex flex-wrap gap-1 mt-2">
                        {filterTags.map(tag => (
                            <Chip key={tag} label={tag} size="sm" />
                        ))}
                    </Box>
                )}
            </Box>

            {/* Component List */}
            <Box className="flex-1 overflow-auto">
                <List dense>
                    {Object.entries(componentsByCategory).map(([category, components]) => (
                        <React.Fragment key={category}>
                            {/* Category Header */}
                            <ListItemButton onClick={() => toggleCategory(category as ComponentCategory)}>
                                <ListItemText
                                    primary={categoryLabels[category as ComponentCategory]}
                                    primaryTypographyProps={{ variant: 'subtitle2', fontWeight: 'bold' }}
                                />
                                <Chip label={components.length} size="sm" />
                                {expandedCategories.has(category as ComponentCategory) ? (
                                    <CollapseIcon />
                                ) : (
                                    <ExpandIcon />
                                )}
                            </ListItemButton>

                            {/* Category Components */}
                            <Collapse in={expandedCategories.has(category as ComponentCategory)}>
                                {components.map(component => (
                                    <Tooltip
                                        key={component.id}
                                        title={component.description}
                                        placement="right"
                                        arrow
                                    >
                                        <ListItem
                                            disablePadding
                                            className="pl-4"
                                            draggable={dragEnabled}
                                            onDragStart={handleDragStart(component)}
                                        >
                                            <ListItemButton
                                                onClick={() => onSelectComponent(component)}
                                                className="hover:bg-gray-100 hover:dark:bg-gray-800" style={{ cursor: dragEnabled ? 'grab' : 'pointer' }}
                                            >
                                                <ListItemIcon className="min-w-[40px]">
                                                    {component.icon}
                                                </ListItemIcon>
                                                <ListItemText
                                                    primary={component.label}
                                                    primaryTypographyProps={{ variant: 'body2' }}
                                                />
                                            </ListItemButton>
                                        </ListItem>
                                    </Tooltip>
                                ))}
                            </Collapse>

                            <Divider />
                        </React.Fragment>
                    ))}
                </List>
            </Box>

            {/* Footer */}
            <Box className="p-2 border-gray-200 dark:border-gray-700 border-t" >
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary" align="center" display="block">
                    {filteredComponents.length} component{filteredComponents.length !== 1 ? 's' : ''}
                </Typography>
            </Box>
        </Paper>
    );
};
