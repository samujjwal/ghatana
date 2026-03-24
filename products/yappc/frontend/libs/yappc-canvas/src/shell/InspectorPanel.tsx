/**
 * Inspector Panel
 *
 * Property inspector for selected nodes.
 * Dynamically renders form fields based on artifact contracts.
 *
 * @doc.type component
 * @doc.purpose Property inspector UI
 * @doc.layer ui
 * @doc.pattern Composite Component
 */

import React, {
    useState,
    useMemo,
    useCallback,
    type ReactNode,
} from 'react';
import type {
    UniversalNode,
    ArtifactContract,
    SchemaDefinition,
} from '../model/contracts';
import type { UpdatePropsPayload, UpdateStylePayload } from '../commands/CommandTypes';

// ============================================================================
// Inspector Types
// ============================================================================

/**
 * Inspector tab types
 */
export type InspectorTab = 'properties' | 'style' | 'layout' | 'interactions' | 'data';

/**
 * Inspector panel props
 */
export interface InspectorPanelProps {
    /** Currently selected nodes */
    selectedNodes: UniversalNode[];
    /** Contracts for selected nodes */
    contracts: Map<string, ArtifactContract>;
    /** Active tab */
    activeTab?: InspectorTab;
    /** Panel width */
    width?: number;
    /** Whether panel is collapsed */
    collapsed?: boolean;
    /** Callback when property changes */
    onPropertyChange?: (nodeId: string, payload: UpdatePropsPayload) => void;
    /** Callback when style changes */
    onStyleChange?: (nodeId: string, payload: UpdateStylePayload) => void;
    /** Callback when tab changes */
    onTabChange?: (tab: InspectorTab) => void;
    /** Callback when collapse state changes */
    onCollapsedChange?: (collapsed: boolean) => void;
    /** Custom field renderer */
    renderField?: (
        schema: SchemaDefinition,
        value: unknown,
        onChange: (value: unknown) => void,
        path: string
    ) => ReactNode;
    /** Additional CSS class */
    className?: string;
}

/**
 * Field group definition
 */
interface FieldGroup {
    label: string;
    fields: Array<{
        key: string;
        schema: SchemaDefinition;
        value: unknown;
        mixed?: boolean;
    }>;
}

// ============================================================================
// Inspector Panel Component
// ============================================================================

export const InspectorPanel: React.FC<InspectorPanelProps> = ({
    selectedNodes,
    contracts,
    activeTab = 'properties',
    width = 300,
    collapsed = false,
    onPropertyChange,
    onStyleChange,
    onTabChange,
    onCollapsedChange,
    renderField,
    className,
}) => {
    const [expandedGroups, setExpandedGroups] = useState<Set<string>>(
        new Set(['basic', 'layout', 'appearance'])
    );

    // Get common contract (if all selected nodes are same type)
    const commonContract = useMemo((): ArtifactContract | null => {
        if (selectedNodes.length === 0) return null;

        const firstKind = selectedNodes[0].kind;
        const allSameKind = selectedNodes.every((n) => n.kind === firstKind);

        if (!allSameKind) return null;

        return contracts.get(firstKind) ?? null;
    }, [selectedNodes, contracts]);

    // Merge values from multiple selected nodes
    const mergedProps = useMemo((): Record<string, { value: unknown; mixed: boolean }> => {
        if (selectedNodes.length === 0) return {};

        const result: Record<string, { value: unknown; mixed: boolean }> = {};

        // Get all unique keys
        const allKeys = new Set<string>();
        selectedNodes.forEach((node) => {
            Object.keys(node.props).forEach((key) => allKeys.add(key));
        });

        // Check each key for mixed values
        allKeys.forEach((key) => {
            const values = selectedNodes.map((n) => n.props[key]);
            const firstValue = values[0];
            const isMixed = values.some(
                (v) => JSON.stringify(v) !== JSON.stringify(firstValue)
            );

            result[key] = { value: firstValue, mixed: isMixed };
        });

        return result;
    }, [selectedNodes]);

    // Merge styles from multiple selected nodes
    const mergedStyles = useMemo((): Record<string, { value: unknown; mixed: boolean }> => {
        if (selectedNodes.length === 0) return {};

        const result: Record<string, { value: unknown; mixed: boolean }> = {};

        const allKeys = new Set<string>();
        selectedNodes.forEach((node) => {
            Object.keys(node.style).forEach((key) => allKeys.add(key));
        });

        allKeys.forEach((key) => {
            const values = selectedNodes.map((n) => (n.style as Record<string, unknown>)[key]);
            const firstValue = values[0];
            const isMixed = values.some(
                (v) => JSON.stringify(v) !== JSON.stringify(firstValue)
            );

            result[key] = { value: firstValue, mixed: isMixed };
        });

        return result;
    }, [selectedNodes]);

    // Handle property change
    const handlePropertyChange = useCallback(
        (key: string, value: unknown) => {
            selectedNodes.forEach((node) => {
                onPropertyChange?.(node.id, {
                    nodeId: node.id,
                    changes: { [key]: value },
                });
            });
        },
        [selectedNodes, onPropertyChange]
    );

    // Handle style change
    const handleStyleChange = useCallback(
        (key: string, value: unknown) => {
            selectedNodes.forEach((node) => {
                onStyleChange?.(node.id, {
                    nodeId: node.id,
                    changes: { [key]: value },
                });
            });
        },
        [selectedNodes, onStyleChange]
    );

    // Toggle group expansion
    const toggleGroup = useCallback((groupId: string) => {
        setExpandedGroups((prev) => {
            const next = new Set(prev);
            if (next.has(groupId)) {
                next.delete(groupId);
            } else {
                next.add(groupId);
            }
            return next;
        });
    }, []);

    // Render collapsed state
    if (collapsed) {
        return (
            <div
                className={className}
                style={{
                    width: 48,
                    height: '100%',
                    backgroundColor: '#f8fafc',
                    borderLeft: '1px solid #e2e8f0',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    padding: '8px 0',
                }}
            >
                <button
                    onClick={() => onCollapsedChange?.(false)}
                    style={{
                        width: 32,
                        height: 32,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        backgroundColor: 'transparent',
                        border: 'none',
                        borderRadius: 4,
                        cursor: 'pointer',
                        fontSize: 16,
                    }}
                    title="Expand inspector"
                >
                    ◀
                </button>
            </div>
        );
    }

    return (
        <div
            className={className}
            style={{
                width,
                height: '100%',
                backgroundColor: '#f8fafc',
                borderLeft: '1px solid #e2e8f0',
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden',
            }}
        >
            {/* Header */}
            <div
                style={{
                    padding: '12px 16px',
                    borderBottom: '1px solid #e2e8f0',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                }}
            >
                <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600, color: '#1e293b' }}>
                    {selectedNodes.length === 0
                        ? 'Inspector'
                        : selectedNodes.length === 1
                            ? selectedNodes[0].name
                            : `${selectedNodes.length} items selected`}
                </h3>
                <button
                    onClick={() => onCollapsedChange?.(true)}
                    style={{
                        width: 24,
                        height: 24,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        backgroundColor: 'transparent',
                        border: 'none',
                        borderRadius: 4,
                        cursor: 'pointer',
                        fontSize: 12,
                    }}
                    title="Collapse inspector"
                >
                    ▶
                </button>
            </div>

            {/* Tabs */}
            <div
                style={{
                    display: 'flex',
                    borderBottom: '1px solid #e2e8f0',
                    padding: '0 8px',
                }}
            >
                {(['properties', 'style', 'layout', 'interactions', 'data'] as InspectorTab[]).map(
                    (tab) => (
                        <button
                            key={tab}
                            onClick={() => onTabChange?.(tab)}
                            style={{
                                padding: '8px 12px',
                                backgroundColor: 'transparent',
                                border: 'none',
                                borderBottom: activeTab === tab ? '2px solid #6366f1' : '2px solid transparent',
                                color: activeTab === tab ? '#6366f1' : '#64748b',
                                fontSize: 12,
                                fontWeight: activeTab === tab ? 600 : 400,
                                cursor: 'pointer',
                                textTransform: 'capitalize',
                            }}
                        >
                            {tab}
                        </button>
                    )
                )}
            </div>

            {/* Content */}
            <div style={{ flex: 1, overflow: 'auto', padding: 16 }}>
                {selectedNodes.length === 0 ? (
                    <EmptyState />
                ) : activeTab === 'properties' ? (
                    <PropertiesTab
                        contract={commonContract}
                        mergedProps={mergedProps}
                        expandedGroups={expandedGroups}
                        onToggleGroup={toggleGroup}
                        onPropertyChange={handlePropertyChange}
                        renderField={renderField}
                    />
                ) : activeTab === 'style' ? (
                    <StyleTab
                        contract={commonContract}
                        mergedStyles={mergedStyles}
                        expandedGroups={expandedGroups}
                        onToggleGroup={toggleGroup}
                        onStyleChange={handleStyleChange}
                        renderField={renderField}
                    />
                ) : activeTab === 'layout' ? (
                    <LayoutTab
                        selectedNodes={selectedNodes}
                        onStyleChange={handleStyleChange}
                    />
                ) : activeTab === 'interactions' ? (
                    <InteractionsTab
                        contract={commonContract}
                        mergedProps={mergedProps}
                        onPropertyChange={handlePropertyChange}
                    />
                ) : activeTab === 'data' ? (
                    <DataTab
                        contract={commonContract}
                        mergedProps={mergedProps}
                        onPropertyChange={handlePropertyChange}
                    />
                ) : null}
            </div>
        </div>
    );
};

// ============================================================================
// Tab Components
// ============================================================================

const EmptyState: React.FC = () => (
    <div
        style={{
            padding: 32,
            textAlign: 'center',
            color: '#94a3b8',
        }}
    >
        <div style={{ fontSize: 48, marginBottom: 16 }}>🎯</div>
        <div style={{ fontSize: 14 }}>Select an element to inspect</div>
    </div>
);

interface PropertiesTabProps {
    contract: ArtifactContract | null;
    mergedProps: Record<string, { value: unknown; mixed: boolean }>;
    expandedGroups: Set<string>;
    onToggleGroup: (groupId: string) => void;
    onPropertyChange: (key: string, value: unknown) => void;
    renderField?: (
        schema: SchemaDefinition,
        value: unknown,
        onChange: (value: unknown) => void,
        path: string
    ) => ReactNode;
}

const PropertiesTab: React.FC<PropertiesTabProps> = ({
    contract,
    mergedProps,
    expandedGroups,
    onToggleGroup,
    onPropertyChange,
    renderField,
}) => {
    const groups = useMemo((): FieldGroup[] => {
        if (!contract?.propsSchema) {
            // No schema, show raw props
            return [
                {
                    label: 'Properties',
                    fields: Object.entries(mergedProps).map(([key, { value, mixed }]) => ({
                        key,
                        schema: { type: typeof value },
                        value,
                        mixed,
                    })),
                },
            ];
        }

        // Group fields by category
        const grouped: Record<string, FieldGroup['fields']> = { basic: [] };

        Object.entries(contract.propsSchema.properties ?? {}).forEach(
            ([key, schema]) => {
                const group = (schema as unknown).group || 'basic';
                if (!grouped[group]) {
                    grouped[group] = [];
                }
                grouped[group].push({
                    key,
                    schema: schema as SchemaDefinition,
                    value: mergedProps[key]?.value,
                    mixed: mergedProps[key]?.mixed,
                });
            }
        );

        return Object.entries(grouped).map(([label, fields]) => ({
            label: label.charAt(0).toUpperCase() + label.slice(1),
            fields,
        }));
    }, [contract, mergedProps]);

    return (
        <div>
            {groups.map((group) => (
                <FieldGroupComponent
                    key={group.label}
                    group={group}
                    expanded={expandedGroups.has(group.label.toLowerCase())}
                    onToggle={() => onToggleGroup(group.label.toLowerCase())}
                    onChange={onPropertyChange}
                    renderField={renderField}
                />
            ))}
        </div>
    );
};

interface StyleTabProps {
    contract: ArtifactContract | null;
    mergedStyles: Record<string, { value: unknown; mixed: boolean }>;
    expandedGroups: Set<string>;
    onToggleGroup: (groupId: string) => void;
    onStyleChange: (key: string, value: unknown) => void;
    renderField?: (
        schema: SchemaDefinition,
        value: unknown,
        onChange: (value: unknown) => void,
        path: string
    ) => ReactNode;
}

const StyleTab: React.FC<StyleTabProps> = ({
    contract,
    mergedStyles,
    expandedGroups,
    onToggleGroup,
    onStyleChange,
    renderField,
}) => {
    const groups = useMemo((): FieldGroup[] => {
        const commonStyles = [
            { key: 'backgroundColor', schema: { type: 'color' }, group: 'appearance' },
            { key: 'borderColor', schema: { type: 'color' }, group: 'appearance' },
            { key: 'borderWidth', schema: { type: 'number' }, group: 'appearance' },
            { key: 'borderRadius', schema: { type: 'number' }, group: 'appearance' },
            { key: 'opacity', schema: { type: 'number', min: 0, max: 1 }, group: 'appearance' },
            { key: 'padding', schema: { type: 'spacing' }, group: 'spacing' },
            { key: 'margin', schema: { type: 'spacing' }, group: 'spacing' },
            { key: 'fontSize', schema: { type: 'number' }, group: 'typography' },
            { key: 'fontWeight', schema: { type: 'select', options: ['normal', 'bold', '500', '600'] }, group: 'typography' },
            { key: 'color', schema: { type: 'color' }, group: 'typography' },
            { key: 'textAlign', schema: { type: 'select', options: ['left', 'center', 'right'] }, group: 'typography' },
        ];

        const grouped: Record<string, FieldGroup['fields']> = {};

        commonStyles.forEach(({ key, schema, group }) => {
            if (!grouped[group]) {
                grouped[group] = [];
            }
            grouped[group].push({
                key,
                schema: schema as SchemaDefinition,
                value: mergedStyles[key]?.value,
                mixed: mergedStyles[key]?.mixed,
            });
        });

        return Object.entries(grouped).map(([label, fields]) => ({
            label: label.charAt(0).toUpperCase() + label.slice(1),
            fields,
        }));
    }, [mergedStyles]);

    return (
        <div>
            {groups.map((group) => (
                <FieldGroupComponent
                    key={group.label}
                    group={group}
                    expanded={expandedGroups.has(group.label.toLowerCase())}
                    onToggle={() => onToggleGroup(group.label.toLowerCase())}
                    onChange={onStyleChange}
                    renderField={renderField}
                />
            ))}
        </div>
    );
};

interface LayoutTabProps {
    selectedNodes: UniversalNode[];
    onStyleChange: (key: string, value: unknown) => void;
}

const LayoutTab: React.FC<LayoutTabProps> = ({ selectedNodes, onStyleChange }) => {
    const node = selectedNodes[0];
    if (!node) return null;

    return (
        <div>
            <div style={{ marginBottom: 16 }}>
                <label style={{ fontSize: 12, color: '#64748b', display: 'block', marginBottom: 4 }}>
                    Position
                </label>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                    <div>
                        <label style={{ fontSize: 10, color: '#94a3b8' }}>X</label>
                        <input
                            type="number"
                            value={node.transform.x}
                            onChange={(e) => onStyleChange('transform.x', parseFloat(e.target.value))}
                            style={{ width: '100%', padding: 8, border: '1px solid #e2e8f0', borderRadius: 4 }}
                        />
                    </div>
                    <div>
                        <label style={{ fontSize: 10, color: '#94a3b8' }}>Y</label>
                        <input
                            type="number"
                            value={node.transform.y}
                            onChange={(e) => onStyleChange('transform.y', parseFloat(e.target.value))}
                            style={{ width: '100%', padding: 8, border: '1px solid #e2e8f0', borderRadius: 4 }}
                        />
                    </div>
                </div>
            </div>

            <div style={{ marginBottom: 16 }}>
                <label style={{ fontSize: 12, color: '#64748b', display: 'block', marginBottom: 4 }}>
                    Size
                </label>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                    <div>
                        <label style={{ fontSize: 10, color: '#94a3b8' }}>Width</label>
                        <input
                            type="number"
                            value={node.transform.width}
                            onChange={(e) => onStyleChange('transform.width', parseFloat(e.target.value))}
                            style={{ width: '100%', padding: 8, border: '1px solid #e2e8f0', borderRadius: 4 }}
                        />
                    </div>
                    <div>
                        <label style={{ fontSize: 10, color: '#94a3b8' }}>Height</label>
                        <input
                            type="number"
                            value={node.transform.height}
                            onChange={(e) => onStyleChange('transform.height', parseFloat(e.target.value))}
                            style={{ width: '100%', padding: 8, border: '1px solid #e2e8f0', borderRadius: 4 }}
                        />
                    </div>
                </div>
            </div>

            <div style={{ marginBottom: 16 }}>
                <label style={{ fontSize: 12, color: '#64748b', display: 'block', marginBottom: 4 }}>
                    Rotation
                </label>
                <input
                    type="number"
                    value={node.transform.rotation ?? 0}
                    onChange={(e) => onStyleChange('transform.rotation', parseFloat(e.target.value))}
                    style={{ width: '100%', padding: 8, border: '1px solid #e2e8f0', borderRadius: 4 }}
                />
            </div>

            <div>
                <label style={{ fontSize: 12, color: '#64748b', display: 'block', marginBottom: 4 }}>
                    Z-Index
                </label>
                <input
                    type="number"
                    value={node.transform.zIndex ?? 0}
                    onChange={(e) => onStyleChange('transform.zIndex', parseInt(e.target.value))}
                    style={{ width: '100%', padding: 8, border: '1px solid #e2e8f0', borderRadius: 4 }}
                />
            </div>
        </div>
    );
};

interface InteractionsTabProps {
    contract: ArtifactContract | null;
    mergedProps: Record<string, { value: unknown; mixed: boolean }>;
    onPropertyChange: (key: string, value: unknown) => void;
}

const InteractionsTab: React.FC<InteractionsTabProps> = ({
    contract,
    mergedProps,
    onPropertyChange,
}) => {
    const events = contract?.interactionContract.events ?? [];

    return (
        <div>
            {events.length === 0 ? (
                <div style={{ color: '#94a3b8', fontSize: 13, textAlign: 'center', padding: 16 }}>
                    No interactions available for this component
                </div>
            ) : (
                events.map((event) => (
                    <div key={event} style={{ marginBottom: 12 }}>
                        <label style={{ fontSize: 12, color: '#64748b', display: 'block', marginBottom: 4 }}>
                            on{event.charAt(0).toUpperCase() + event.slice(1)}
                        </label>
                        <select
                            value={(mergedProps[`on${event}`]?.value as string) ?? ''}
                            onChange={(e) => onPropertyChange(`on${event}`, e.target.value)}
                            style={{
                                width: '100%',
                                padding: 8,
                                border: '1px solid #e2e8f0',
                                borderRadius: 4,
                                backgroundColor: '#fff',
                            }}
                        >
                            <option value="">No action</option>
                            <option value="navigate">Navigate</option>
                            <option value="openModal">Open Modal</option>
                            <option value="submitForm">Submit Form</option>
                            <option value="customAction">Custom Action</option>
                        </select>
                    </div>
                ))
            )}
        </div>
    );
};

interface DataTabProps {
    contract: ArtifactContract | null;
    mergedProps: Record<string, { value: unknown; mixed: boolean }>;
    onPropertyChange: (key: string, value: unknown) => void;
}

const DataTab: React.FC<DataTabProps> = ({
    contract,
    mergedProps,
    onPropertyChange,
}) => {
    const dataBindings = contract?.stateContract.dataBindings ?? [];

    return (
        <div>
            {dataBindings.length === 0 ? (
                <div style={{ color: '#94a3b8', fontSize: 13, textAlign: 'center', padding: 16 }}>
                    No data bindings available for this component
                </div>
            ) : (
                dataBindings.map((binding) => (
                    <div key={binding.property} style={{ marginBottom: 12 }}>
                        <label style={{ fontSize: 12, color: '#64748b', display: 'block', marginBottom: 4 }}>
                            {binding.property} binding
                        </label>
                        <input
                            type="text"
                            value={(mergedProps[`_binding_${binding.property}`]?.value as string) ?? ''}
                            onChange={(e) => onPropertyChange(`_binding_${binding.property}`, e.target.value)}
                            placeholder="e.g., $.data.items"
                            style={{
                                width: '100%',
                                padding: 8,
                                border: '1px solid #e2e8f0',
                                borderRadius: 4,
                            }}
                        />
                    </div>
                ))
            )}
        </div>
    );
};

// ============================================================================
// Field Group Component
// ============================================================================

interface FieldGroupComponentProps {
    group: FieldGroup;
    expanded: boolean;
    onToggle: () => void;
    onChange: (key: string, value: unknown) => void;
    renderField?: (
        schema: SchemaDefinition,
        value: unknown,
        onChange: (value: unknown) => void,
        path: string
    ) => ReactNode;
}

const FieldGroupComponent: React.FC<FieldGroupComponentProps> = ({
    group,
    expanded,
    onToggle,
    onChange,
    renderField,
}) => {
    return (
        <div style={{ marginBottom: 8 }}>
            <button
                onClick={onToggle}
                style={{
                    width: '100%',
                    padding: '8px 0',
                    backgroundColor: 'transparent',
                    border: 'none',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    cursor: 'pointer',
                    textAlign: 'left',
                }}
            >
                <span style={{ fontSize: 10, color: '#64748b' }}>{expanded ? '▼' : '▶'}</span>
                <span style={{ fontSize: 12, fontWeight: 500, color: '#334155' }}>{group.label}</span>
            </button>

            {expanded && (
                <div style={{ paddingLeft: 16 }}>
                    {group.fields.map((field) => (
                        <div key={field.key} style={{ marginBottom: 8 }}>
                            <label
                                style={{
                                    fontSize: 11,
                                    color: '#64748b',
                                    display: 'block',
                                    marginBottom: 4,
                                }}
                            >
                                {field.key}
                                {field.mixed && (
                                    <span style={{ marginLeft: 4, color: '#f59e0b', fontSize: 10 }}>
                                        (mixed)
                                    </span>
                                )}
                            </label>
                            {renderField ? (
                                renderField(field.schema, field.value, (v) => onChange(field.key, v), field.key)
                            ) : (
                                <DefaultFieldRenderer
                                    schema={field.schema}
                                    value={field.value}
                                    mixed={field.mixed}
                                    onChange={(v) => onChange(field.key, v)}
                                />
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

// ============================================================================
// Default Field Renderer
// ============================================================================

interface DefaultFieldRendererProps {
    schema: SchemaDefinition;
    value: unknown;
    mixed?: boolean;
    onChange: (value: unknown) => void;
}

const DefaultFieldRenderer: React.FC<DefaultFieldRendererProps> = ({
    schema,
    value,
    mixed,
    onChange,
}) => {
    const type = schema.type as string;

    const inputStyle = {
        width: '100%',
        padding: '6px 8px',
        border: '1px solid #e2e8f0',
        borderRadius: 4,
        fontSize: 12,
        backgroundColor: mixed ? '#fef3c7' : '#fff',
    };

    switch (type) {
        case 'string':
        case 'text':
            return (
                <input
                    type="text"
                    value={(value as string) ?? ''}
                    placeholder={mixed ? 'Multiple values' : undefined}
                    onChange={(e) => onChange(e.target.value)}
                    style={inputStyle}
                />
            );

        case 'number':
        case 'integer':
            return (
                <input
                    type="number"
                    value={(value as number) ?? ''}
                    placeholder={mixed ? 'Multiple values' : undefined}
                    min={(schema as unknown).min}
                    max={(schema as unknown).max}
                    step={(schema as unknown).step ?? 1}
                    onChange={(e) => onChange(parseFloat(e.target.value))}
                    style={inputStyle}
                />
            );

        case 'boolean':
            return (
                <input
                    type="checkbox"
                    checked={(value as boolean) ?? false}
                    onChange={(e) => onChange(e.target.checked)}
                    style={{ marginTop: 4 }}
                />
            );

        case 'color':
            return (
                <div style={{ display: 'flex', gap: 8 }}>
                    <input
                        type="color"
                        value={(value as string) ?? '#000000'}
                        onChange={(e) => onChange(e.target.value)}
                        style={{ width: 32, height: 32, border: 'none', cursor: 'pointer' }}
                    />
                    <input
                        type="text"
                        value={(value as string) ?? ''}
                        onChange={(e) => onChange(e.target.value)}
                        style={{ ...inputStyle, flex: 1 }}
                    />
                </div>
            );

        case 'select':
            return (
                <select
                    value={(value as string) ?? ''}
                    onChange={(e) => onChange(e.target.value)}
                    style={{ ...inputStyle, backgroundColor: mixed ? '#fef3c7' : '#fff' }}
                >
                    <option value="">Select...</option>
                    {((schema as unknown).options ?? []).map((opt: string) => (
                        <option key={opt} value={opt}>
                            {opt}
                        </option>
                    ))}
                </select>
            );

        default:
            // JSON input for complex types
            return (
                <textarea
                    value={typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value ?? '')}
                    onChange={(e) => {
                        try {
                            onChange(JSON.parse(e.target.value));
                        } catch {
                            onChange(e.target.value);
                        }
                    }}
                    style={{ ...inputStyle, minHeight: 60, fontFamily: 'monospace', fontSize: 11 }}
                />
            );
    }
};

export default InspectorPanel;
