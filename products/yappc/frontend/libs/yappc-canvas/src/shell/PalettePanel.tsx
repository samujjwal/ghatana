/**
 * Palette Panel
 *
 * Displays available artifacts organized by category.
 * Supports drag-and-drop to canvas, search, and filtering.
 *
 * @doc.type component
 * @doc.purpose Component palette UI
 * @doc.layer ui
 * @doc.pattern Composite Component
 */

import React, {
    useState,
    useMemo,
    useCallback,
    type ReactNode,
} from 'react';
import type { ArtifactContract, ArtifactCategory, ContentModality } from '../model/contracts';
import { getArtifactRegistry } from '../registry/ArtifactRegistry';
import { DragDropManager } from '../interaction/DragDropManager';
import type { DragSourceType } from '../interaction/DragDropManager';

// ============================================================================
// Palette Types
// ============================================================================

/**
 * Palette view mode
 */
export type PaletteViewMode = 'grid' | 'list' | 'compact';

/**
 * Palette panel props
 */
export interface PalettePanelProps {
    /** Currently active category filter */
    activeCategory?: ArtifactCategory;
    /** Currently active modality filter */
    activeModality?: ContentModality;
    /** Search query */
    searchQuery?: string;
    /** View mode */
    viewMode?: PaletteViewMode;
    /** Panel width */
    width?: number;
    /** Whether panel is collapsed */
    collapsed?: boolean;
    /** Callback when item is selected */
    onItemSelect?: (contract: ArtifactContract) => void;
    /** Callback when item drag starts */
    onDragStart?: (contract: ArtifactContract) => void;
    /** Callback when category changes */
    onCategoryChange?: (category: ArtifactCategory | undefined) => void;
    /** Callback when collapse state changes */
    onCollapsedChange?: (collapsed: boolean) => void;
    /** Custom item renderer */
    renderItem?: (contract: ArtifactContract, mode: PaletteViewMode) => ReactNode;
    /** Custom category header renderer */
    renderCategoryHeader?: (
        category: ArtifactCategory,
        contracts: ArtifactContract[]
    ) => ReactNode;
    /** Additional CSS class */
    className?: string;
}

/**
 * Palette item data
 */
interface PaletteItem {
    contract: ArtifactContract;
    category: ArtifactCategory;
}

// ============================================================================
// Category Definitions
// ============================================================================

const CATEGORY_INFO: Record<
    ArtifactCategory,
    { label: string; icon: string; order: number }
> = {
    primitive: { label: 'Primitives', icon: '⬜', order: 0 },
    container: { label: 'Containers', icon: '📦', order: 1 },
    input: { label: 'Inputs', icon: '✏️', order: 2 },
    display: { label: 'Display', icon: '📺', order: 3 },
    navigation: { label: 'Navigation', icon: '🧭', order: 4 },
    data: { label: 'Data', icon: '📊', order: 5 },
    media: { label: 'Media', icon: '🖼️', order: 6 },
    diagram: { label: 'Diagrams', icon: '📐', order: 7 },
    connector: { label: 'Connectors', icon: '🔗', order: 8 },
    annotation: { label: 'Annotations', icon: '💬', order: 9 },
    portal: { label: 'Portals', icon: '🚪', order: 10 },
    custom: { label: 'Custom', icon: '🔧', order: 11 },
};

const MODALITY_INFO: Record<ContentModality, { label: string; icon: string }> = {
    visual: { label: 'Visual', icon: '👁️' },
    diagram: { label: 'Diagram', icon: '📐' },
    code: { label: 'Code', icon: '💻' },
    document: { label: 'Document', icon: '📄' },
    form: { label: 'Form', icon: '📝' },
    drawing: { label: 'Drawing', icon: '🎨' },
};

// ============================================================================
// Palette Panel Component
// ============================================================================

export const PalettePanel: React.FC<PalettePanelProps> = ({
    activeCategory,
    activeModality,
    searchQuery = '',
    viewMode = 'grid',
    width = 280,
    collapsed = false,
    onItemSelect,
    onDragStart,
    onCategoryChange,
    onCollapsedChange,
    renderItem,
    renderCategoryHeader,
    className,
}) => {
    const [expandedCategories, setExpandedCategories] = useState<Set<ArtifactCategory>>(
        new Set(['primitive', 'container', 'input'])
    );
    const [localSearchQuery, setLocalSearchQuery] = useState(searchQuery);

    const registry = getArtifactRegistry();

    // Get all registered artifacts
    const allArtifacts = useMemo((): PaletteItem[] => {
        const contracts = registry.getAll();
        return contracts.map((contract) => ({
            contract,
            category: contract.identity.category,
        }));
    }, [registry]);

    // Filter artifacts by search, category, and modality
    const filteredArtifacts = useMemo((): PaletteItem[] => {
        let items = allArtifacts;

        // Filter by category
        if (activeCategory) {
            items = items.filter((item) => item.category === activeCategory);
        }

        // Filter by modality
        if (activeModality) {
            items = items.filter((item) =>
                item.contract.identity.modalities.includes(activeModality)
            );
        }

        // Filter by search query
        const query = localSearchQuery.toLowerCase().trim();
        if (query) {
            items = items.filter(
                (item) =>
                    item.contract.identity.name.toLowerCase().includes(query) ||
                    item.contract.identity.description?.toLowerCase().includes(query) ||
                    item.contract.identity.tags?.some((tag) =>
                        tag.toLowerCase().includes(query)
                    )
            );
        }

        return items;
    }, [allArtifacts, activeCategory, activeModality, localSearchQuery]);

    // Group artifacts by category
    const groupedArtifacts = useMemo((): Map<ArtifactCategory, PaletteItem[]> => {
        const grouped = new Map<ArtifactCategory, PaletteItem[]>();

        filteredArtifacts.forEach((item) => {
            const existing = grouped.get(item.category) ?? [];
            existing.push(item);
            grouped.set(item.category, existing);
        });

        // Sort categories by order
        const sorted = new Map(
            [...grouped.entries()].sort(
                ([a], [b]) =>
                    (CATEGORY_INFO[a]?.order ?? 99) - (CATEGORY_INFO[b]?.order ?? 99)
            )
        );

        return sorted;
    }, [filteredArtifacts]);

    // Toggle category expansion
    const toggleCategory = useCallback((category: ArtifactCategory) => {
        setExpandedCategories((prev) => {
            const next = new Set(prev);
            if (next.has(category)) {
                next.delete(category);
            } else {
                next.add(category);
            }
            return next;
        });
    }, []);

    // Handle item drag start
    const handleDragStart = useCallback(
        (contract: ArtifactContract, e: React.DragEvent | React.MouseEvent) => {
            const dragManager = DragDropManager.getInstance();

            dragManager.startDrag({
                type: 'palette' as DragSourceType,
                artifactKind: contract.identity.kind,
                data: {
                    contract,
                    defaultProps: contract.propsSchema?.default ?? {},
                    defaultStyle: contract.styleSchema?.default ?? {},
                },
            }, e.nativeEvent);

            onDragStart?.(contract);
        },
        [onDragStart]
    );

    // Handle item click
    const handleItemClick = useCallback(
        (contract: ArtifactContract) => {
            onItemSelect?.(contract);
        },
        [onItemSelect]
    );

    // Render collapsed state
    if (collapsed) {
        return (
            <div
                className={className}
                style={{
                    width: 48,
                    height: '100%',
                    backgroundColor: '#f8fafc',
                    borderRight: '1px solid #e2e8f0',
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
                    title="Expand palette"
                >
                    ▶
                </button>

                <div style={{ marginTop: 8, borderTop: '1px solid #e2e8f0', paddingTop: 8 }}>
                    {Array.from(groupedArtifacts.keys()).map((category) => (
                        <button
                            key={category}
                            onClick={() => {
                                onCollapsedChange?.(false);
                                onCategoryChange?.(category);
                            }}
                            style={{
                                width: 32,
                                height: 32,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                backgroundColor: activeCategory === category ? '#e0e7ff' : 'transparent',
                                border: 'none',
                                borderRadius: 4,
                                cursor: 'pointer',
                                fontSize: 16,
                                marginBottom: 4,
                            }}
                            title={CATEGORY_INFO[category]?.label}
                        >
                            {CATEGORY_INFO[category]?.icon}
                        </button>
                    ))}
                </div>
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
                borderRight: '1px solid #e2e8f0',
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
                    Components
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
                    title="Collapse palette"
                >
                    ◀
                </button>
            </div>

            {/* Search */}
            <div style={{ padding: '8px 16px', borderBottom: '1px solid #e2e8f0' }}>
                <input
                    type="text"
                    value={localSearchQuery}
                    onChange={(e) => setLocalSearchQuery(e.target.value)}
                    placeholder="Search components..."
                    style={{
                        width: '100%',
                        padding: '8px 12px',
                        border: '1px solid #e2e8f0',
                        borderRadius: 6,
                        fontSize: 13,
                        outline: 'none',
                    }}
                />
            </div>

            {/* Modality filter */}
            <div
                style={{
                    padding: '8px 16px',
                    borderBottom: '1px solid #e2e8f0',
                    display: 'flex',
                    gap: 4,
                    flexWrap: 'wrap',
                }}
            >
                {Object.entries(MODALITY_INFO).map(([modality, info]) => (
                    <button
                        key={modality}
                        onClick={() =>
                            onCategoryChange?.(
                                activeModality === modality ? undefined : (modality as ContentModality as unknown)
                            )
                        }
                        style={{
                            padding: '4px 8px',
                            backgroundColor: activeModality === modality ? '#e0e7ff' : '#ffffff',
                            border: `1px solid ${activeModality === modality ? '#6366f1' : '#e2e8f0'}`,
                            borderRadius: 4,
                            fontSize: 11,
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 4,
                        }}
                        title={info.label}
                    >
                        <span>{info.icon}</span>
                        <span>{info.label}</span>
                    </button>
                ))}
            </div>

            {/* Content */}
            <div style={{ flex: 1, overflow: 'auto', padding: '8px 0' }}>
                {Array.from(groupedArtifacts.entries()).map(([category, items]) => {
                    const categoryInfo = CATEGORY_INFO[category];
                    const isExpanded = expandedCategories.has(category);

                    return (
                        <div key={category} style={{ marginBottom: 4 }}>
                            {/* Category Header */}
                            {renderCategoryHeader ? (
                                renderCategoryHeader(category, items.map((i) => i.contract))
                            ) : (
                                <button
                                    onClick={() => toggleCategory(category)}
                                    style={{
                                        width: '100%',
                                        padding: '8px 16px',
                                        backgroundColor: 'transparent',
                                        border: 'none',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: 8,
                                        cursor: 'pointer',
                                        textAlign: 'left',
                                    }}
                                >
                                    <span style={{ fontSize: 10, color: '#64748b' }}>
                                        {isExpanded ? '▼' : '▶'}
                                    </span>
                                    <span style={{ fontSize: 16 }}>{categoryInfo?.icon}</span>
                                    <span
                                        style={{ flex: 1, fontSize: 12, fontWeight: 500, color: '#334155' }}
                                    >
                                        {categoryInfo?.label}
                                    </span>
                                    <span
                                        style={{
                                            fontSize: 11,
                                            color: '#94a3b8',
                                            backgroundColor: '#f1f5f9',
                                            padding: '2px 6px',
                                            borderRadius: 10,
                                        }}
                                    >
                                        {items.length}
                                    </span>
                                </button>
                            )}

                            {/* Category Items */}
                            {isExpanded && (
                                <div
                                    style={{
                                        padding: viewMode === 'grid' ? '8px 16px' : '0 16px',
                                        display: viewMode === 'grid' ? 'grid' : 'flex',
                                        gridTemplateColumns:
                                            viewMode === 'grid' ? 'repeat(2, 1fr)' : undefined,
                                        flexDirection: viewMode === 'list' ? 'column' : undefined,
                                        gap: viewMode === 'compact' ? 4 : 8,
                                    }}
                                >
                                    {items.map(({ contract }) =>
                                        renderItem ? (
                                            renderItem(contract, viewMode)
                                        ) : (
                                            <PaletteItem
                                                key={contract.identity.kind}
                                                contract={contract}
                                                viewMode={viewMode}
                                                onDragStart={handleDragStart}
                                                onClick={handleItemClick}
                                            />
                                        )
                                    )}
                                </div>
                            )}
                        </div>
                    );
                })}

                {filteredArtifacts.length === 0 && (
                    <div
                        style={{
                            padding: 32,
                            textAlign: 'center',
                            color: '#94a3b8',
                            fontSize: 13,
                        }}
                    >
                        <div style={{ fontSize: 32, marginBottom: 8 }}>🔍</div>
                        <div>No components found</div>
                        {localSearchQuery && (
                            <button
                                onClick={() => setLocalSearchQuery('')}
                                style={{
                                    marginTop: 8,
                                    padding: '4px 12px',
                                    backgroundColor: '#f1f5f9',
                                    border: 'none',
                                    borderRadius: 4,
                                    fontSize: 12,
                                    cursor: 'pointer',
                                }}
                            >
                                Clear search
                            </button>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
};

// ============================================================================
// Palette Item Component
// ============================================================================

interface PaletteItemProps {
    contract: ArtifactContract;
    viewMode: PaletteViewMode;
    onDragStart: (contract: ArtifactContract, e: React.MouseEvent) => void;
    onClick: (contract: ArtifactContract) => void;
}

const PaletteItem: React.FC<PaletteItemProps> = ({
    contract,
    viewMode,
    onDragStart,
    onClick,
}) => {
    const [isHovered, setIsHovered] = useState(false);

    const handleMouseDown = useCallback(
        (e: React.MouseEvent) => {
            // Only start drag on left click
            if (e.button === 0) {
                onDragStart(contract, e);
            }
        },
        [contract, onDragStart]
    );

    const handleClick = useCallback(() => {
        onClick(contract);
    }, [contract, onClick]);

    if (viewMode === 'compact') {
        return (
            <div
                onMouseDown={handleMouseDown}
                onClick={handleClick}
                onMouseEnter={() => setIsHovered(true)}
                onMouseLeave={() => setIsHovered(false)}
                style={{
                    padding: '4px 8px',
                    backgroundColor: isHovered ? '#f1f5f9' : 'transparent',
                    borderRadius: 4,
                    cursor: 'grab',
                    fontSize: 12,
                    color: '#334155',
                }}
                title={contract.identity.description}
                draggable
            >
                {contract.identity.icon && <span style={{ marginRight: 4 }}>{contract.identity.icon}</span>}
                {contract.identity.name}
            </div>
        );
    }

    if (viewMode === 'list') {
        return (
            <div
                onMouseDown={handleMouseDown}
                onClick={handleClick}
                onMouseEnter={() => setIsHovered(true)}
                onMouseLeave={() => setIsHovered(false)}
                style={{
                    padding: '8px 12px',
                    backgroundColor: isHovered ? '#f1f5f9' : '#ffffff',
                    border: '1px solid #e2e8f0',
                    borderRadius: 6,
                    cursor: 'grab',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    marginBottom: 4,
                }}
                title={contract.identity.description}
                draggable
            >
                <div
                    style={{
                        width: 36,
                        height: 36,
                        backgroundColor: '#f1f5f9',
                        borderRadius: 6,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 18,
                    }}
                >
                    {contract.identity.icon || '📦'}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 500, color: '#334155' }}>
                        {contract.identity.name}
                    </div>
                    {contract.identity.description && (
                        <div
                            style={{
                                fontSize: 11,
                                color: '#94a3b8',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace: 'nowrap',
                            }}
                        >
                            {contract.identity.description}
                        </div>
                    )}
                </div>
            </div>
        );
    }

    // Grid mode (default)
    return (
        <div
            onMouseDown={handleMouseDown}
            onClick={handleClick}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
            style={{
                padding: 12,
                backgroundColor: isHovered ? '#f1f5f9' : '#ffffff',
                border: '1px solid #e2e8f0',
                borderRadius: 8,
                cursor: 'grab',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                textAlign: 'center',
                transition: 'background-color 0.15s, box-shadow 0.15s',
                boxShadow: isHovered ? '0 2px 8px rgba(0,0,0,0.08)' : 'none',
            }}
            title={contract.identity.description}
            draggable
        >
            <div
                style={{
                    width: 48,
                    height: 48,
                    backgroundColor: '#f1f5f9',
                    borderRadius: 8,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 24,
                    marginBottom: 8,
                }}
            >
                {contract.identity.icon || '📦'}
            </div>
            <div
                style={{
                    fontSize: 12,
                    fontWeight: 500,
                    color: '#334155',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    width: '100%',
                }}
            >
                {contract.identity.name}
            </div>
        </div>
    );
};

export default PalettePanel;
