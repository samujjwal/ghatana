/**
 * Palette Panel Component
 * 
 * Element library organized by layer with role-specific templates.
 * Supports quick add functionality, search, and drag-and-drop.
 * 
 * @doc.type component
 * @doc.purpose Element library and templates
 * @doc.layer presentation
 */

import React, { useState, useMemo } from 'react';
import { useAtomValue } from 'jotai';
import {
    chromeSemanticLayerAtom,
    chromeActiveRolesAtom,
    SemanticLayer,
} from '../../chrome';
import { useActionExecutor } from '../../hooks/useAvailableActions';

interface PalettePanelProps {
    onClose: () => void;
}

interface PaletteItem {
    id: string;
    label: string;
    icon: string;
    layer: SemanticLayer | 'universal';
    actionId: string;
    description: string;
}

const PALETTE_ITEMS: PaletteItem[] = [
    // Architecture Layer
    {
        id: 'service',
        label: 'Service',
        icon: '🔷',
        layer: 'architecture',
        actionId: 'arch-add-service',
        description: 'Add a service node',
    },
    {
        id: 'database',
        label: 'Database',
        icon: '🗄️',
        layer: 'architecture',
        actionId: 'arch-add-database',
        description: 'Add a database node',
    },
    {
        id: 'api-contract',
        label: 'API Contract',
        icon: '🔌',
        layer: 'architecture',
        actionId: 'arch-add-api-contract',
        description: 'Add an API contract',
    },
    // Design Layer
    {
        id: 'component',
        label: 'Component',
        icon: '🧩',
        layer: 'design',
        actionId: 'design-add-component',
        description: 'Add a UI component',
    },
    {
        id: 'screen',
        label: 'Screen',
        icon: '📱',
        layer: 'design',
        actionId: 'design-add-screen',
        description: 'Add a screen',
    },
    {
        id: 'wireframe',
        label: 'Wireframe',
        icon: '📐',
        layer: 'design',
        actionId: 'design-add-wireframe',
        description: 'Add a wireframe',
    },
    // Component Layer
    {
        id: 'comp-component',
        label: 'Component Detail',
        icon: '⚙️',
        layer: 'component',
        actionId: 'comp-add-component',
        description: 'Add component specification',
    },
    {
        id: 'state',
        label: 'State',
        icon: '📊',
        layer: 'component',
        actionId: 'comp-add-state',
        description: 'Add component state',
    },
    {
        id: 'event',
        label: 'Event',
        icon: '⚡',
        layer: 'component',
        actionId: 'comp-add-event',
        description: 'Add event handler',
    },
    // Implementation Layer
    {
        id: 'code-block',
        label: 'Code Block',
        icon: '💻',
        layer: 'implementation',
        actionId: 'impl-add-code-block',
        description: 'Add a code block',
    },
    {
        id: 'function',
        label: 'Function',
        icon: 'ƒ',
        layer: 'implementation',
        actionId: 'impl-add-function',
        description: 'Add a function',
    },
    {
        id: 'class',
        label: 'Class',
        icon: '🏛️',
        layer: 'implementation',
        actionId: 'impl-add-class',
        description: 'Add a class',
    },
    // Universal
    {
        id: 'shape',
        label: 'Shape',
        icon: '⬜',
        layer: 'universal',
        actionId: 'universal-add-shape',
        description: 'Add a shape',
    },
    {
        id: 'text',
        label: 'Text',
        icon: '📝',
        layer: 'universal',
        actionId: 'universal-add-text',
        description: 'Add text',
    },
    {
        id: 'frame',
        label: 'Frame',
        icon: '🖼️',
        layer: 'universal',
        actionId: 'universal-add-frame',
        description: 'Add a frame',
    },
];

export const PalettePanel: React.FC<PalettePanelProps> = ({ onClose }) => {
    const currentLayer = useAtomValue(chromeSemanticLayerAtom);
    const activeRoles = useAtomValue(chromeActiveRolesAtom);
    const { executeAction } = useActionExecutor();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedCategory, setSelectedCategory] = useState<SemanticLayer | 'universal' | 'all'>('all');

    // Filter items based on search and category
    const filteredItems = useMemo(() => {
        let items = PALETTE_ITEMS;

        // Filter by category
        if (selectedCategory !== 'all') {
            items = items.filter(item => item.layer === selectedCategory);
        }

        // Filter by search query
        if (searchQuery) {
            const query = searchQuery.toLowerCase();
            items = items.filter(
                item =>
                    item.label.toLowerCase().includes(query) ||
                    item.description.toLowerCase().includes(query)
            );
        }

        return items;
    }, [searchQuery, selectedCategory]);

    // Group items by layer
    const groupedItems = useMemo(() => {
        const groups = new Map<string, PaletteItem[]>();

        filteredItems.forEach(item => {
            const layer = item.layer;
            if (!groups.has(layer)) {
                groups.set(layer, []);
            }
            groups.get(layer)!.push(item);
        });

        return Array.from(groups.entries()).map(([layer, items]) => ({
            layer,
            items,
        }));
    }, [filteredItems]);

    const handleAddItem = async (item: PaletteItem) => {
        try {
            await executeAction(item.actionId);
            console.log(`✅ Added ${item.label} to canvas`);
        } catch (error) {
            console.error(`❌ Failed to add ${item.label}:`, error);
        }
    };

    const categories: Array<{ value: SemanticLayer | 'universal' | 'all'; label: string }> = [
        { value: 'all', label: 'All' },
        { value: 'architecture', label: 'Architecture' },
        { value: 'design', label: 'Design' },
        { value: 'component', label: 'Component' },
        { value: 'implementation', label: 'Implementation' },
        { value: 'universal', label: 'Universal' },
    ];

    return (
        <div
            style={{
                width: '320px',
                height: '100%',
                backgroundColor: '#ffffff',
                borderRight: '1px solid #e0e0e0',
                display: 'flex',
                flexDirection: 'column',
            }}
        >
            {/* Header */}
            <div
                style={{
                    height: '48px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '0 16px',
                    borderBottom: '1px solid #e0e0e0',
                }}
            >
                <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 600 }}>
                    Palette
                </h3>
                <button
                    onClick={onClose}
                    style={{
                        border: 'none',
                        background: 'transparent',
                        cursor: 'pointer',
                        fontSize: '20px',
                        padding: '4px',
                    }}
                    aria-label="Close panel"
                >
                    ×
                </button>
            </div>

            {/* Search */}
            <div style={{ padding: '12px 16px', borderBottom: '1px solid #e0e0e0' }}>
                <input
                    type="text"
                    placeholder="Search elements..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    style={{
                        width: '100%',
                        padding: '8px 12px',
                        border: '1px solid #d1d5db',
                        borderRadius: '6px',
                        fontSize: '14px',
                        outline: 'none',
                    }}
                />
            </div>

            {/* Category Filter */}
            <div
                style={{
                    padding: '12px 16px',
                    borderBottom: '1px solid #e0e0e0',
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: '6px',
                }}
            >
                {categories.map(category => (
                    <button
                        key={category.value}
                        onClick={() => setSelectedCategory(category.value)}
                        style={{
                            padding: '4px 10px',
                            border: '1px solid #d1d5db',
                            borderRadius: '4px',
                            background: selectedCategory === category.value ? '#e3f2fd' : '#ffffff',
                            color: selectedCategory === category.value ? '#1976d2' : '#374151',
                            cursor: 'pointer',
                            fontSize: '11px',
                            fontWeight: selectedCategory === category.value ? 600 : 400,
                        }}
                    >
                        {category.label}
                    </button>
                ))}
            </div>

            {/* Current Layer Indicator */}
            <div
                style={{
                    padding: '8px 16px',
                    backgroundColor: '#f9fafb',
                    borderBottom: '1px solid #e0e0e0',
                    fontSize: '12px',
                    color: '#6b7280',
                }}
            >
                Current layer: <strong>{currentLayer}</strong>
            </div>

            {/* Element Grid */}
            <div style={{ flex: 1, overflow: 'auto', padding: '16px' }}>
                {groupedItems.length === 0 ? (
                    <div
                        style={{
                            padding: '32px 16px',
                            textAlign: 'center',
                            color: '#9ca3af',
                            fontSize: '14px',
                        }}
                    >
                        {searchQuery ? 'No elements found' : 'No elements available'}
                    </div>
                ) : (
                    groupedItems.map((group, groupIndex) => (
                        <div key={groupIndex} style={{ marginBottom: '24px' }}>
                            {/* Group Header */}
                            <div
                                style={{
                                    fontSize: '11px',
                                    fontWeight: 600,
                                    color: '#6b7280',
                                    padding: '4px 0',
                                    marginBottom: '8px',
                                    textTransform: 'uppercase',
                                    letterSpacing: '0.5px',
                                }}
                            >
                                {group.layer} ({group.items.length})
                            </div>

                            {/* Items Grid */}
                            <div
                                style={{
                                    display: 'grid',
                                    gridTemplateColumns: 'repeat(2, 1fr)',
                                    gap: '8px',
                                }}
                            >
                                {group.items.map(item => (
                                    <button
                                        key={item.id}
                                        onClick={() => handleAddItem(item)}
                                        style={{
                                            padding: '16px 12px',
                                            border: '1px solid #e5e7eb',
                                            borderRadius: '8px',
                                            background: '#ffffff',
                                            cursor: 'pointer',
                                            display: 'flex',
                                            flexDirection: 'column',
                                            alignItems: 'center',
                                            gap: '8px',
                                            transition: 'all 0.2s',
                                        }}
                                        onMouseEnter={(e) => {
                                            e.currentTarget.style.borderColor = '#1976d2';
                                            e.currentTarget.style.background = '#f0f9ff';
                                        }}
                                        onMouseLeave={(e) => {
                                            e.currentTarget.style.borderColor = '#e5e7eb';
                                            e.currentTarget.style.background = '#ffffff';
                                        }}
                                        title={item.description}
                                    >
                                        <span style={{ fontSize: '32px' }}>{item.icon}</span>
                                        <span
                                            style={{
                                                fontSize: '12px',
                                                fontWeight: 500,
                                                color: '#374151',
                                                textAlign: 'center',
                                            }}
                                        >
                                            {item.label}
                                        </span>
                                    </button>
                                ))}
                            </div>
                        </div>
                    ))
                )}
            </div>

            {/* Help Text */}
            <div
                style={{
                    padding: '12px 16px',
                    borderTop: '1px solid #e0e0e0',
                    fontSize: '11px',
                    color: '#6b7280',
                    lineHeight: '1.5',
                }}
            >
                💡 Click an element to add it to the canvas. Elements are organized by semantic layer.
            </div>
        </div>
    );
};
