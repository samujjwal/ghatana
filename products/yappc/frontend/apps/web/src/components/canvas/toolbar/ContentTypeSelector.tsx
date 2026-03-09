/**
 * Content Type Selector
 * 
 * Dropdown selector for choosing between 28 different canvas content types.
 * Maps Mode + Level combinations to available content types.
 * 
 * @doc.type component
 * @doc.purpose Content type selection for canvas
 * @doc.layer product
 * @doc.pattern Selector Component
 */

import React, { useState, useMemo } from 'react';
import { Box, Button, Menu, MenuItem, ListItemIcon, ListItemText, Typography, Divider, Chip } from '@ghatana/ui';
import { ChevronDown as KeyboardArrowDown, Building2 as Architecture, Code, GitBranch as AccountTree, Network as Schema, HardDrive as Storage, LayoutDashboard as Dashboard, ClipboardList as Assignment, Bug as BugReport, FlaskConical as Science, Activity as Timeline, StickyNote as StickyNote2, LayoutGrid as ViewModule, Palette as ColorLens, Settings, FileText as Description, Shield as Security, Cloud as CloudQueue, Terminal, Eye as Visibility, Gauge as Speed, Compass as Explore } from 'lucide-react';

// ============================================================================
// Types
// ============================================================================

export type ContentType =
    | 'architecture-diagram'
    | 'sequence-diagram'
    | 'class-diagram'
    | 'component-diagram'
    | 'infrastructure-diagram'
    | 'api-topology'
    | 'mind-map'
    | 'sticky-notes'
    | 'code-editor'
    | 'pseudocode'
    | 'test-editor'
    | 'test-file-list'
    | 'e2e-coverage'
    | 'unit-coverage'
    | 'page-layouts'
    | 'design-system'
    | 'style-tokens'
    | 'component-specs'
    | 'component-metrics'
    | 'config-browser'
    | 'config-editor'
    | 'file-explorer'
    | 'log-viewer'
    | 'trace-explorer'
    | 'system-dashboard'
    | 'annotations'
    | 'container-orchestration'
    | 'module-graph';

interface ContentTypeOption {
    id: ContentType;
    label: string;
    description: string;
    icon: React.ReactNode;
    category: 'diagrams' | 'code' | 'design' | 'testing' | 'ops' | 'config' | 'docs';
}

export interface ContentTypeSelectorProps {
    selectedType: ContentType;
    onTypeChange: (type: ContentType) => void;
    disabled?: boolean;
}

// ============================================================================
// Content Type Definitions
// ============================================================================

const CONTENT_TYPES: ContentTypeOption[] = [
    // Diagrams Category
    {
        id: 'architecture-diagram',
        label: 'Architecture Diagram',
        description: 'High-level system architecture',
        icon: <Architecture size={16} />,
        category: 'diagrams',
    },
    {
        id: 'sequence-diagram',
        label: 'Sequence Diagram',
        description: 'UML sequence flows',
        icon: <Timeline size={16} />,
        category: 'diagrams',
    },
    {
        id: 'class-diagram',
        label: 'Class Diagram',
        description: 'UML class relationships',
        icon: <Schema size={16} />,
        category: 'diagrams',
    },
    {
        id: 'component-diagram',
        label: 'Component Diagram',
        description: 'Component hierarchy',
        icon: <AccountTree size={16} />,
        category: 'diagrams',
    },
    {
        id: 'infrastructure-diagram',
        label: 'Infrastructure Diagram',
        description: 'Cloud architecture',
        icon: <CloudQueue size={16} />,
        category: 'diagrams',
    },
    {
        id: 'api-topology',
        label: 'API Topology',
        description: 'API relationships',
        icon: <Storage size={16} />,
        category: 'diagrams',
    },
    {
        id: 'module-graph',
        label: 'Module Graph',
        description: 'Module dependencies',
        icon: <ViewModule size={16} />,
        category: 'diagrams',
    },
    {
        id: 'mind-map',
        label: 'Mind Map',
        description: 'Brainstorming canvas',
        icon: <Explore size={16} />,
        category: 'diagrams',
    },

    // Code Category
    {
        id: 'code-editor',
        label: 'Code Editor',
        description: 'Monaco-based editor',
        icon: <Code size={16} />,
        category: 'code',
    },
    {
        id: 'pseudocode',
        label: 'Pseudocode',
        description: 'Algorithm design',
        icon: <Description size={16} />,
        category: 'code',
    },
    {
        id: 'file-explorer',
        label: 'File Explorer',
        description: 'Project file tree',
        icon: <Terminal size={16} />,
        category: 'code',
    },

    // Testing Category
    {
        id: 'test-editor',
        label: 'Test Editor',
        description: 'Test authoring',
        icon: <BugReport size={16} />,
        category: 'testing',
    },
    {
        id: 'test-file-list',
        label: 'Test File List',
        description: 'Test file browser',
        icon: <Assignment size={16} />,
        category: 'testing',
    },
    {
        id: 'e2e-coverage',
        label: 'E2E Coverage',
        description: 'End-to-end test coverage',
        icon: <Science size={16} />,
        category: 'testing',
    },
    {
        id: 'unit-coverage',
        label: 'Unit Coverage',
        description: 'Unit test coverage',
        icon: <Security size={16} />,
        category: 'testing',
    },

    // Design Category
    {
        id: 'page-layouts',
        label: 'Page Layouts',
        description: 'UI layouts',
        icon: <Dashboard size={16} />,
        category: 'design',
    },
    {
        id: 'design-system',
        label: 'Design System',
        description: 'Design tokens',
        icon: <ColorLens size={16} />,
        category: 'design',
    },
    {
        id: 'style-tokens',
        label: 'Style Tokens',
        description: 'Theme editor',
        icon: <ColorLens size={16} />,
        category: 'design',
    },
    {
        id: 'component-specs',
        label: 'Component Specs',
        description: 'Specifications',
        icon: <Description size={16} />,
        category: 'design',
    },
    {
        id: 'component-metrics',
        label: 'Component Metrics',
        description: 'Performance metrics',
        icon: <Speed size={16} />,
        category: 'design',
    },

    // Ops Category
    {
        id: 'log-viewer',
        label: 'Log Viewer',
        description: 'Log streaming',
        icon: <Terminal size={16} />,
        category: 'ops',
    },
    {
        id: 'trace-explorer',
        label: 'Trace Explorer',
        description: 'Distributed tracing',
        icon: <Visibility size={16} />,
        category: 'ops',
    },
    {
        id: 'system-dashboard',
        label: 'System Dashboard',
        description: 'Monitoring',
        icon: <Dashboard size={16} />,
        category: 'ops',
    },
    {
        id: 'container-orchestration',
        label: 'Container Orchestration',
        description: 'K8s/Docker',
        icon: <CloudQueue size={16} />,
        category: 'ops',
    },

    // Config Category
    {
        id: 'config-browser',
        label: 'Config Browser',
        description: 'Configuration viewer',
        icon: <Settings size={16} />,
        category: 'config',
    },
    {
        id: 'config-editor',
        label: 'Config Editor',
        description: 'Config editing',
        icon: <Settings size={16} />,
        category: 'config',
    },

    // Docs Category
    {
        id: 'sticky-notes',
        label: 'Sticky Notes',
        description: 'Kanban-style notes',
        icon: <StickyNote2 size={16} />,
        category: 'docs',
    },
    {
        id: 'annotations',
        label: 'Annotations',
        description: 'Notes and callouts',
        icon: <Description size={16} />,
        category: 'docs',
    },
];

const CATEGORY_LABELS: Record<string, string> = {
    diagrams: 'Diagrams',
    code: 'Code',
    design: 'Design',
    testing: 'Testing',
    ops: 'Operations',
    config: 'Configuration',
    docs: 'Documentation',
};

// ============================================================================
// Component
// ============================================================================

export const ContentTypeSelector: React.FC<ContentTypeSelectorProps> = ({
    selectedType,
    onTypeChange,
    disabled = false,
}) => {
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
    const open = Boolean(anchorEl);

    const handleClick = (event: React.MouseEvent<HTMLElement>) => {
        setAnchorEl(event.currentTarget);
    };

    const handleClose = () => {
        setAnchorEl(null);
    };

    const handleSelect = (type: ContentType) => {
        onTypeChange(type);
        handleClose();
    };

    const selectedOption = CONTENT_TYPES.find(t => t.id === selectedType);

    // Group content types by category
    const groupedTypes = useMemo(() => {
        const groups: Record<string, ContentTypeOption[]> = {};
        CONTENT_TYPES.forEach(type => {
            if (!groups[type.category]) {
                groups[type.category] = [];
            }
            groups[type.category].push(type);
        });
        return groups;
    }, []);

    return (
        <>
            <Button
                onClick={handleClick}
                disabled={disabled}
                endIcon={<KeyboardArrowDown />}
                size="sm"
                className="normal-case font-medium text-gray-500 dark:text-gray-400 rounded-lg px-3 hover:bg-gray-100"
            >
                <Box className="flex items-center gap-1.5">
                    {selectedOption?.icon}
                    <Typography as="p" className="text-sm" fontWeight={500}>
                        {selectedOption?.label || 'Select Content'}
                    </Typography>
                </Box>
            </Button>

            <Menu
                anchorEl={anchorEl}
                open={open}
                onClose={handleClose}
                PaperProps={{
                    sx: {
                        maxHeight: 400,
                        minWidth: 280,
                        borderRadius: 2,
                        mt: 1,
                    },
                }}
                transformOrigin={{ horizontal: 'left', vertical: 'top' }}
                anchorOrigin={{ horizontal: 'left', vertical: 'bottom' }}
            >
                <Box className="px-4 py-2">
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary" fontWeight={600}>
                        SELECT CONTENT TYPE
                    </Typography>
                </Box>
                <Divider />

                {Object.entries(groupedTypes).map(([category, types], catIndex) => (
                    <React.Fragment key={category}>
                        {catIndex > 0 && <Divider />}
                        <Box className="px-4 py-1.5 bg-gray-100 dark:bg-gray-800">
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" fontWeight={600}>
                                {CATEGORY_LABELS[category]}
                            </Typography>
                        </Box>
                        {types.map(type => (
                            <MenuItem
                                key={type.id}
                                onClick={() => handleSelect(type.id)}
                                selected={type.id === selectedType}
                                className="py-2 bg-blue-100 dark:bg-blue-900/30 bg-blue-100 dark:bg-blue-900/30"
                            >
                                <ListItemIcon className="min-w-[36px]">
                                    {type.icon}
                                </ListItemIcon>
                                <ListItemText
                                    primary={type.label}
                                    secondary={type.description}
                                    primaryTypographyProps={{ fontSize: '0.875rem', fontWeight: 500 }}
                                    secondaryTypographyProps={{ fontSize: '0.75rem' }}
                                />
                                {type.id === selectedType && (
                                    <Chip
                                        label="Active"
                                        size="sm"
                                        tone="primary"
                                        className="ml-2 h-[20px] text-[0.65rem]"
                                    />
                                )}
                            </MenuItem>
                        ))}
                    </React.Fragment>
                ))}
            </Menu>
        </>
    );
};

export default ContentTypeSelector;
