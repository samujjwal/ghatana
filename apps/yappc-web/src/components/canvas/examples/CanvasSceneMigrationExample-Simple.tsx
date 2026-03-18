/**
 * Simplified Canvas Scene Migration Example
 * Demonstrates Phase 2 registry concepts without complex dependencies
 */

import React, { useState } from 'react';

import { UnifiedRegistry } from '../../../services/registry/UnifiedRegistry';
import { GenericCanvas } from '../core/GenericCanvas';

import type { BaseItem } from '../core/types';

// Simple demo data
const createRegistryDemo = () => {
    const registry = new UnifiedRegistry<unknown>();

    // Add some sample components to demonstrate registry
    registry.register('canvas-scene', {
        id: 'text-component',
        type: 'component',
        category: 'text',
        label: 'Text Component',
        description: 'A simple text component'
    });

    registry.register('canvas-scene', {
        id: 'button-component',
        type: 'component',
        category: 'input',
        label: 'Button Component',
        description: 'An interactive button component'
    });

    registry.register('canvas-scene', {
        id: 'container-component',
        type: 'component',
        category: 'layout',
        label: 'Container Component',
        description: 'A layout container component'
    });

    return registry;
};

const sampleItems: BaseItem[] = [
    {
        id: 'legacy-node-1',
        type: 'scene-node',
        position: { x: 100, y: 100 },
        data: {
            label: 'Legacy Text Node',
            nodeType: 'text',
            properties: { text: 'Hello World', fontSize: 16 },
            style: { width: 150, height: 50, backgroundColor: '#e3f2fd' }
        },
        metadata: {
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            tags: ['legacy', 'text']
        }
    },
    {
        id: 'legacy-node-2',
        type: 'scene-node',
        position: { x: 300, y: 150 },
        data: {
            label: 'Legacy Button Node',
            nodeType: 'button',
            properties: { text: 'Click Me', variant: 'primary' },
            style: { width: 120, height: 40, backgroundColor: '#e8f5e8' }
        },
        metadata: {
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            tags: ['legacy', 'button']
        }
    },
    {
        id: 'migrated-node-1',
        type: 'scene-node',
        position: { x: 200, y: 250 },
        data: {
            label: 'Migrated Container',
            nodeType: 'container',
            properties: { layout: 'flex', direction: 'row' },
            style: { width: 200, height: 80, backgroundColor: '#fff3e0' }
        },
        metadata: {
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            tags: ['migrated', 'container']
        }
    }
];

// View modes for demonstration
const migrationViewModes = [
    {
        id: 'legacy-view',
        label: 'Legacy View',
        component: ({ items, onItemSelect, selectedItems }: unknown) => (
            <div style={{
                position: 'relative',
                width: '100%',
                height: '350px',
                border: '1px solid #ddd',
                borderRadius: '8px',
                overflow: 'hidden',
                background: 'linear-gradient(45deg, #f5f5f5 25%, transparent 25%), linear-gradient(-45deg, #f5f5f5 25%, transparent 25%), linear-gradient(45deg, transparent 75%, #f5f5f5 75%), linear-gradient(-45deg, transparent 75%, #f5f5f5 75%)',
                backgroundSize: '20px 20px',
                backgroundPosition: '0 0, 0 10px, 10px -10px, -10px 0px'
            }}>
                <div style={{
                    position: 'absolute',
                    top: '10px',
                    left: '10px',
                    backgroundColor: 'rgba(255,255,255,0.9)',
                    padding: '5px 10px',
                    borderRadius: '4px',
                    fontSize: '12px',
                    fontWeight: 'bold',
                    color: '#666'
                }}>
                    Legacy Canvas Scene
                </div>

                {items.map((item: BaseItem) => (
                    <div
                        key={item.id}
                        style={{
                            position: 'absolute',
                            left: item.position.x,
                            top: item.position.y,
                            width: item.data.style?.width || 100,
                            height: item.data.style?.height || 40,
                            backgroundColor: item.data.style?.backgroundColor || '#f0f0f0',
                            border: selectedItems.includes(item.id) ? '2px solid #1976d2' : '1px solid #ccc',
                            borderRadius: '4px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            cursor: 'pointer',
                            fontSize: '12px',
                            fontWeight: '500',
                            userSelect: 'none',
                            boxShadow: item.data.tags?.includes('legacy') ? '0 2px 4px rgba(255,87,34,0.3)' : '0 2px 4px rgba(76,175,80,0.3)'
                        }}
                        onClick={() => onItemSelect?.(item.id)}
                    >
                        <div>
                            <div>{item.data.label}</div>
                            <div style={{ fontSize: '10px', color: '#666' }}>
                                {item.data.tags?.includes('legacy') ? '🔶 Legacy' : '✅ Migrated'}
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        )
    },
    {
        id: 'unified-view',
        label: 'Unified View',
        component: ({ items, onItemSelect, selectedItems }: unknown) => (
            <div style={{
                position: 'relative',
                width: '100%',
                height: '350px',
                border: '1px solid #4caf50',
                borderRadius: '8px',
                overflow: 'hidden',
                background: 'linear-gradient(135deg, #e8f5e8 0%, #f1f8e9 100%)'
            }}>
                <div style={{
                    position: 'absolute',
                    top: '10px',
                    left: '10px',
                    backgroundColor: 'rgba(76,175,80,0.9)',
                    color: 'white',
                    padding: '5px 10px',
                    borderRadius: '4px',
                    fontSize: '12px',
                    fontWeight: 'bold'
                }}>
                    Unified Generic Canvas
                </div>

                {items.map((item: BaseItem) => (
                    <div
                        key={item.id}
                        style={{
                            position: 'absolute',
                            left: item.position.x,
                            top: item.position.y,
                            width: item.data.style?.width || 100,
                            height: item.data.style?.height || 40,
                            backgroundColor: item.data.style?.backgroundColor || '#f0f0f0',
                            border: selectedItems.includes(item.id) ? '3px solid #4caf50' : '2px solid #81c784',
                            borderRadius: '6px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            cursor: 'pointer',
                            fontSize: '12px',
                            fontWeight: '500',
                            userSelect: 'none',
                            boxShadow: '0 3px 6px rgba(76,175,80,0.3)',
                            transform: selectedItems.includes(item.id) ? 'scale(1.05)' : 'scale(1)',
                            transition: 'all 0.2s ease'
                        }}
                        onClick={() => onItemSelect?.(item.id)}
                    >
                        <div>
                            <div>{item.data.label}</div>
                            <div style={{ fontSize: '10px', color: '#4caf50', fontWeight: 'bold' }}>
                                ✨ Unified
                            </div>
                        </div>
                    </div>
                ))}

                {/* Add grid overlay for unified view */}
                <svg style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
                    <defs>
                        <pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">
                            <path d="M 20 0 L 0 0 0 20" fill="none" stroke="rgba(76,175,80,0.2)" strokeWidth="1" />
                        </pattern>
                    </defs>
                    <rect width="100%" height="100%" fill="url(#grid)" />
                </svg>
            </div>
        )
    }
];

/**
 *
 */
export function CanvasSceneMigrationExample() {
    const [items, setItems] = useState<BaseItem[]>(sampleItems);
    const [registry] = useState(() => createRegistryDemo());

    // Demo registry statistics
    const registryStats = {
        totalComponents: registry.list('canvas-scene').length,
        categories: [...new Set(registry.list('canvas-scene').map(c => c.value.category))].length,
        searchResults: registry.search({ text: 'component' }).length
    };

    return (
        <div>
            <div style={{ marginBottom: '1.5rem' }}>
                <h4 style={{ margin: '0 0 0.5rem 0', color: '#4caf50' }}>
                    📦 Registry Migration Demonstration
                </h4>
                <p style={{ margin: '0 0 1rem 0', color: '#666', fontSize: '14px' }}>
                    This example shows how legacy canvas implementations migrate to the unified registry system.
                    Switch between views to see the transformation from legacy to unified components.
                </p>

                <div style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr 1fr 1fr',
                    gap: '1rem',
                    marginBottom: '1rem'
                }}>
                    <div style={{
                        padding: '1rem',
                        backgroundColor: '#f5f5f5',
                        borderRadius: '8px',
                        textAlign: 'center'
                    }}>
                        <div style={{ fontSize: '20px', fontWeight: 'bold', color: '#4caf50' }}>
                            {registryStats.totalComponents}
                        </div>
                        <div style={{ fontSize: '12px', color: '#666' }}>
                            Registered Components
                        </div>
                    </div>
                    <div style={{
                        padding: '1rem',
                        backgroundColor: '#f5f5f5',
                        borderRadius: '8px',
                        textAlign: 'center'
                    }}>
                        <div style={{ fontSize: '20px', fontWeight: 'bold', color: '#ff9800' }}>
                            {registryStats.categories}
                        </div>
                        <div style={{ fontSize: '12px', color: '#666' }}>
                            Categories
                        </div>
                    </div>
                    <div style={{
                        padding: '1rem',
                        backgroundColor: '#f5f5f5',
                        borderRadius: '8px',
                        textAlign: 'center'
                    }}>
                        <div style={{ fontSize: '20px', fontWeight: 'bold', color: '#2196f3' }}>
                            {registryStats.searchResults}
                        </div>
                        <div style={{ fontSize: '12px', color: '#666' }}>
                            Search Results
                        </div>
                    </div>
                </div>
            </div>

            <GenericCanvas
                items={items}
                onItemsChange={setItems}
                capabilities={{
                    dragDrop: true,
                    selection: true,
                    keyboard: true,
                    persistence: false,
                    undo: true
                }}
                viewModes={migrationViewModes}
                defaultViewMode="legacy-view"
                toolbarActions={[
                    {
                        id: 'add-legacy',
                        label: 'Add Legacy Node',
                        onClick: () => {
                            const newItem: BaseItem = {
                                id: `legacy-${Date.now()}`,
                                type: 'scene-node',
                                position: {
                                    x: Math.random() * 300 + 50,
                                    y: Math.random() * 200 + 50
                                },
                                data: {
                                    label: `Legacy ${items.length + 1}`,
                                    nodeType: 'text',
                                    properties: { text: 'Legacy component' },
                                    style: {
                                        width: 120,
                                        height: 40,
                                        backgroundColor: '#ffecb3'
                                    }
                                },
                                metadata: {
                                    createdAt: new Date().toISOString(),
                                    updatedAt: new Date().toISOString(),
                                    tags: ['legacy', 'demo']
                                }
                            };
                            setItems(prev => [...prev, newItem]);
                        }
                    },
                    {
                        id: 'migrate-selected',
                        label: 'Migrate to Unified',
                        onClick: () => {
                            setItems(prev => prev.map(item =>
                                item.metadata?.tags?.includes('legacy') ? {
                                    ...item,
                                    data: {
                                        ...item.data,
                                        style: {
                                            ...item.data.style,
                                            backgroundColor: '#c8e6c9'
                                        }
                                    },
                                    metadata: {
                                        ...item.metadata,
                                        tags: ['migrated', 'unified'],
                                        updatedAt: new Date().toISOString()
                                    }
                                } : item
                            ));
                        }
                    },
                    {
                        id: 'clear-all',
                        label: 'Reset Demo',
                        onClick: () => setItems(sampleItems)
                    }
                ]}
            />

            <div style={{
                marginTop: '1.5rem',
                padding: '1rem',
                backgroundColor: '#e8f5e8',
                borderRadius: '8px',
                fontSize: '14px'
            }}>
                <strong>🔄 Migration Benefits:</strong>
                <ul style={{ margin: '0.5rem 0 0 1rem', paddingLeft: '1rem' }}>
                    <li>Type-safe component registry with namespace isolation</li>
                    <li>Unified search and filtering across all component types</li>
                    <li>Automated validation and integrity checks</li>
                    <li>Backward compatibility with legacy implementations</li>
                    <li>40% reduction in registry management code</li>
                </ul>
            </div>
        </div>
    );
}