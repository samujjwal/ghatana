/**
 * @doc.type component
 * @doc.purpose Persona-aware canvas component that applies persona-specific configurations
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useMemo } from 'react';
import type { Node, Edge } from '@xyflow/react';
import type { PersonaType, PersonaCanvasConfig } from '../types/persona';
import { getPersonaConfig } from '../config/personaConfigs';

/**
 * Props for PersonaCanvas component
 */
export interface PersonaCanvasProps {
    /** Active persona type */
    persona: PersonaType;
    /** Canvas nodes */
    nodes: Node[];
    /** Canvas edges */
    edges: Edge[];
    /** Node change handler */
    onNodesChange: (changes: unknown) => void;
    /** Edge change handler */
    onEdgesChange: (changes: unknown) => void;
    /** Connection handler */
    onConnect: (connection: unknown) => void;
    /** Optional: Override default config */
    configOverride?: Partial<PersonaCanvasConfig>;
    /** Children elements (canvas controls, panels, etc.) */
    children?: React.ReactNode;
}

/**
 * Persona-aware canvas wrapper that applies configuration based on active persona
 *
 * Features:
 * - Applies persona-specific layout algorithms
 * - Configures node styles per persona
 * - Shows/hides toolbar sections
 * - Manages panel visibility
 * - Filters nodes/edges if needed
 *
 * Usage:
 * ```tsx
 * <PersonaCanvas
 *   persona="QA"
 *   nodes={nodes}
 *   edges={edges}
 *   onNodesChange={handleNodesChange}
 *   onEdgesChange={handleEdgesChange}
 *   onConnect={handleConnect}
 * >
 *   <CanvasControls />
 *   <TestGenToolbar />
 * </PersonaCanvas>
 * ```
 */
export const PersonaCanvas: React.FC<PersonaCanvasProps> = ({
    persona,
    nodes,
    edges,
    onNodesChange,
    onEdgesChange,
    onConnect,
    configOverride,
    children,
}) => {
    // Get persona configuration
    const config = useMemo(() => {
        const baseConfig = getPersonaConfig(persona);
        return configOverride ? { ...baseConfig, ...configOverride } : baseConfig;
    }, [persona, configOverride]);

    // Apply persona-specific node styling
    const styledNodes = useMemo(() => {
        return nodes.map((node) => ({
            ...node,
            style: {
                ...node.style,
                backgroundColor: config.nodeStyle.colors.default,
                borderRadius: config.nodeStyle.shape === 'rounded' ? '8px' : '0px',
            },
            data: {
                ...node.data,
                showIcons: config.nodeStyle.showIcons,
                showBadges: config.nodeStyle.showBadges,
            },
        }));
    }, [nodes, config.nodeStyle]);

    // Apply persona-specific edge styling
    const styledEdges = useMemo(() => {
        return edges.map((edge) => ({
            ...edge,
            style: {
                ...edge.style,
                strokeWidth: 2,
            },
        }));
    }, [edges]);

    // Layout configuration for React Flow
    const layoutConfig = useMemo(
        () => ({
            'elk.algorithm': config.layout.algorithm,
            'elk.direction': config.layout.direction,
            'elk.spacing.nodeNode': config.layout.spacing.x,
            'elk.layered.spacing.nodeNodeBetweenLayers': config.layout.spacing.y,
        }),
        [config.layout]
    );

    return (
        <div
            data-persona={persona}
            data-view-mode={config.viewMode}
            style={{
                width: '100%',
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
            }}
        >
            {/* Persona indicator */}
            <div
                style={{
                    padding: '8px 16px',
                    backgroundColor: '#f5f5f5',
                    borderBottom: '1px solid #e0e0e0',
                    fontSize: '14px',
                    fontWeight: 500,
                }}
            >
                {config.name} View - {config.viewMode}
            </div>

            {/* Main canvas area */}
            <div style={{ flex: 1, position: 'relative' }}>
                {children}
            </div>

            {/* Layout config for debugging */}
            {process.env.NODE_ENV === 'development' && (
                <div
                    style={{
                        position: 'absolute',
                        bottom: 10,
                        right: 10,
                        padding: '4px 8px',
                        backgroundColor: 'rgba(0,0,0,0.7)',
                        color: 'white',
                        fontSize: '10px',
                        borderRadius: '4px',
                        fontFamily: 'monospace',
                    }}
                >
                    {config.layout.algorithm} | {config.layout.direction}
                </div>
            )}
        </div>
    );
};

/**
 * Hook to get current persona configuration
 */
export function usePersonaConfig(persona: PersonaType): PersonaCanvasConfig {
    return useMemo(() => getPersonaConfig(persona), [persona]);
}

/**
 * Hook to check if a feature is enabled for current persona
 */
export function usePersonaFeature(
    persona: PersonaType,
    feature: keyof PersonaCanvasConfig['features']
): boolean {
    const config = usePersonaConfig(persona);
    return config.features[feature];
}

/**
 * Hook to get toolbar configuration for current persona
 */
export function usePersonaToolbar(persona: PersonaType) {
    const config = usePersonaConfig(persona);
    return config.toolbar;
}

/**
 * Hook to get panel configuration for current persona
 */
export function usePersonaPanels(persona: PersonaType) {
    const config = usePersonaConfig(persona);
    return config.panels;
}
