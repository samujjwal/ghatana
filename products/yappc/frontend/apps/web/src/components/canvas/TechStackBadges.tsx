/**
 * @doc.type component
 * @doc.purpose Display tech stack badges with categories
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { Box, Chip, Tooltip, Typography, Menu, MenuItem, ListItemIcon, ListItemText, Divider } from '@ghatana/ui';
import { Plus as Add, ChevronUp as ExpandLess, X as Close, Sparkles as AutoAwesome } from 'lucide-react';
import { TIMING } from '@/styles/design-tokens';
import {
    Technology,
    TechCategory,
    TECH_CATEGORIES,
    getCategoryInfo,
    groupByCategory,
} from '../../types/techStack';

interface TechStackBadgesProps {
    technologies: Technology[];
    maxVisible?: number;
    showCategories?: boolean;
    variant?: 'full' | 'compact' | 'inline';
    editable?: boolean;
    availableTechnologies?: Array<Omit<Technology, 'confidence' | 'source'>>;
    onAdd?: (tech: Omit<Technology, 'confidence' | 'source'>) => void;
    onRemove?: (techId: string) => void;
}

export function TechStackBadges({
    technologies,
    maxVisible = 6,
    showCategories = false,
    variant = 'compact',
    editable = false,
    availableTechnologies = [],
    onAdd,
    onRemove,
}: TechStackBadgesProps) {
    const [expanded, setExpanded] = useState(false);
    const [addMenuAnchor, setAddMenuAnchor] = useState<null | HTMLElement>(null);
    const [categoryFilter, setCategoryFilter] = useState<TechCategory | null>(null);

    const grouped = useMemo(() => groupByCategory(technologies), [technologies]);

    const visibleTech = useMemo(() => {
        if (expanded || variant === 'full') return technologies;
        return technologies.slice(0, maxVisible);
    }, [technologies, expanded, maxVisible, variant]);

    const hasMore = technologies.length > maxVisible && !expanded;

    // Available techs for add menu, optionally filtered by category
    const filteredAvailable = useMemo(() => {
        if (!categoryFilter) return availableTechnologies;
        return availableTechnologies.filter(t => t.category === categoryFilter);
    }, [availableTechnologies, categoryFilter]);

    if (variant === 'inline') {
        return (
            <InlineBadges
                technologies={visibleTech}
                onExpand={() => setExpanded(true)}
            />
        );
    }

    if (showCategories) {
        return (
            <CategorizedBadges
                grouped={grouped}
                editable={editable}
                onRemove={onRemove}
            />
        );
    }

    return (
        <Box>
            {/* Tech badges */}
            <Box className="flex flex-wrap gap-1.5">
                {visibleTech.map(tech => (
                    <TechBadge
                        key={tech.id}
                        tech={tech}
                        editable={editable}
                        onRemove={onRemove}
                        size={variant === 'compact' ? 'small' : 'medium'}
                    />
                ))}

                {/* Show more indicator */}
                {hasMore && (
                    <Chip
                        label={`+${technologies.length - maxVisible}`}
                        size="sm"
                        variant="outlined"
                        onClick={() => setExpanded(true)}
                        className="cursor-pointer"
                    />
                )}

                {/* Add button */}
                {editable && onAdd && availableTechnologies.length > 0 && (
                    <>
                        <Chip
                            icon={<Add className="text-base" />}
                            label="Add"
                            size="sm"
                            variant="outlined"
                            onClick={(e) => setAddMenuAnchor(e.currentTarget)}
                            className="cursor-pointer border-dashed" />

                        <AddTechMenu
                            anchorEl={addMenuAnchor}
                            onClose={() => {
                                setAddMenuAnchor(null);
                                setCategoryFilter(null);
                            }}
                            technologies={filteredAvailable}
                            categories={TECH_CATEGORIES}
                            categoryFilter={categoryFilter}
                            onCategoryFilter={setCategoryFilter}
                            onAdd={(tech) => {
                                onAdd(tech);
                                setAddMenuAnchor(null);
                            }}
                        />
                    </>
                )}
            </Box>

            {/* Collapse button when expanded */}
            {expanded && technologies.length > maxVisible && (
                <Box className="flex justify-center mt-2">
                    <Chip
                        icon={<ExpandLess className="text-base" />}
                        label="Show less"
                        size="sm"
                        variant="outlined"
                        onClick={() => setExpanded(false)}
                        className="cursor-pointer"
                    />
                </Box>
            )}
        </Box>
    );
}

// Individual tech badge
function TechBadge({
    tech,
    editable,
    onRemove,
    size = 'small',
}: {
    tech: Technology;
    editable?: boolean;
    onRemove?: (id: string) => void;
    size?: 'small' | 'medium';
}) {
    const categoryInfo = getCategoryInfo(tech.category);

    const chipContent = (
        <Box className="flex items-center gap-1">
            <span>{tech.icon || categoryInfo.icon}</span>
            <span>{tech.name}</span>
            {tech.version && (
                <Typography
                    component="span"
                    as="span" className="text-xs text-gray-500"
                    className="ml-1 opacity-[0.7]"
                >
                    v{tech.version}
                </Typography>
            )}
        </Box>
    );

    return (
        <Tooltip
            title={
                <Box>
                    <Typography as="span" className="text-xs text-gray-500" display="block">
                        {categoryInfo.label} • {Math.round(tech.confidence * 100)}% confidence
                    </Typography>
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        Source: {tech.source}
                    </Typography>
                </Box>
            }
            arrow
        >
            <Chip
                label={chipContent}
                size={size}
                style={{ backgroundColor: tech.color ? `${tech.color }}
                variant="outlined"
                onDelete={editable && onRemove ? () => onRemove(tech.id) : undefined}
                deleteIcon={<Close className="text-sm" />}
            />
        </Tooltip>
    );
}

// Inline badges (minimal, single line)
function InlineBadges({
    technologies,
    onExpand,
}: {
    technologies: Technology[];
    onExpand: () => void;
}) {
    return (
        <Box className="flex items-center gap-1">
            {technologies.slice(0, 4).map(tech => (
                <Tooltip key={tech.id} title={tech.name}>
                    <Box
                        className="cursor-default text-base leading-none"
                    >
                        {tech.icon || getCategoryInfo(tech.category).icon}
                    </Box>
                </Tooltip>
            ))}
            {technologies.length > 4 && (
                <Tooltip title={`${technologies.length - 4} more technologies`} enterDelay={TIMING.tooltipDelay}>
                    <Typography
                        as="span" className="text-xs text-gray-500 cursor-pointer text-gray-500 dark:text-gray-400 hover:text-blue-600"
                        onClick={onExpand}
                    >
                        +{technologies.length - 4}
                    </Typography>
                </Tooltip>
            )}
        </Box>
    );
}

// Categorized badges view
function CategorizedBadges({
    grouped,
    editable,
    onRemove,
}: {
    grouped: Record<TechCategory, Technology[]>;
    editable?: boolean;
    onRemove?: (id: string) => void;
}) {
    const nonEmptyCategories = TECH_CATEGORIES.filter(
        cat => grouped[cat.category].length > 0
    );

    return (
        <Box className="flex flex-col gap-3">
            {nonEmptyCategories.map(catInfo => (
                <Box key={catInfo.category}>
                    <Typography
                        as="span" className="text-xs text-gray-500"
                        className="flex items-center gap-1 mb-1 font-semibold text-gray-500 dark:text-gray-400"
                    >
                        <span>{catInfo.icon}</span>
                        {catInfo.label}
                    </Typography>
                    <Box className="flex flex-wrap gap-1">
                        {grouped[catInfo.category].map(tech => (
                            <TechBadge
                                key={tech.id}
                                tech={tech}
                                editable={editable}
                                onRemove={onRemove}
                            />
                        ))}
                    </Box>
                </Box>
            ))}
        </Box>
    );
}

// Add technology menu
function AddTechMenu({
    anchorEl,
    onClose,
    technologies,
    categories,
    categoryFilter,
    onCategoryFilter,
    onAdd,
}: {
    anchorEl: HTMLElement | null;
    onClose: () => void;
    technologies: Array<Omit<Technology, 'confidence' | 'source'>>;
    categories: typeof TECH_CATEGORIES;
    categoryFilter: TechCategory | null;
    onCategoryFilter: (cat: TechCategory | null) => void;
    onAdd: (tech: Omit<Technology, 'confidence' | 'source'>) => void;
}) {
    return (
        <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={onClose}
            PaperProps={{
                sx: { maxHeight: 400, width: 280 },
            }}
        >
            {/* Category filters */}
            <Box className="px-2 pb-2 flex flex-wrap gap-1">
                <Chip
                    label="All"
                    size="sm"
                    variant={!categoryFilter ? 'filled' : 'outlined'}
                    onClick={() => onCategoryFilter(null)}
                    className="cursor-pointer"
                />
                {categories.slice(0, 5).map(cat => (
                    <Chip
                        key={cat.category}
                        label={cat.icon}
                        size="sm"
                        variant={categoryFilter === cat.category ? 'filled' : 'outlined'}
                        onClick={() => onCategoryFilter(cat.category)}
                        className="cursor-pointer min-w-[32px]"
                    />
                ))}
            </Box>

            <Divider />

            {technologies.length === 0 ? (
                <MenuItem disabled>
                    <Typography as="p" className="text-sm" color="text.secondary">
                        No more technologies available
                    </Typography>
                </MenuItem>
            ) : (
                technologies.slice(0, 15).map(tech => (
                    <MenuItem key={tech.id} onClick={() => onAdd(tech)}>
                        <ListItemIcon className="min-w-[32px]">
                            {tech.icon || getCategoryInfo(tech.category).icon}
                        </ListItemIcon>
                        <ListItemText
                            primary={tech.name}
                            secondary={getCategoryInfo(tech.category).label}
                            secondaryTypographyProps={{ variant: 'caption' }}
                        />
                    </MenuItem>
                ))
            )}

            {technologies.length > 15 && (
                <MenuItem disabled>
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        +{technologies.length - 15} more...
                    </Typography>
                </MenuItem>
            )}
        </Menu>
    );
}

// Compact pill showing primary stack
export function TechStackPill({
    technologies,
    onClick,
}: {
    technologies: Technology[];
    onClick?: () => void;
}) {
    const primary = technologies
        .filter(t => ['language', 'framework', 'database'].includes(t.category))
        .slice(0, 3);

    if (primary.length === 0) {
        return (
            <Chip
                icon={<AutoAwesome className="text-base" />}
                label="Set tech stack"
                size="sm"
                variant="outlined"
                onClick={onClick}
                className="cursor-pointer border-dashed" />
        );
    }

    return (
        <Tooltip title="Tech Stack" enterDelay={TIMING.tooltipDelay}>
            <Chip
                label={
                    <Box className="flex items-center gap-1">
                        {primary.map(t => (
                            <span key={t.id}>{t.icon}</span>
                        ))}
                        {technologies.length > 3 && (
                            <Typography as="span" className="text-xs text-gray-500" className="ml-1">
                                +{technologies.length - 3}
                            </Typography>
                        )}
                    </Box>
                }
                size="sm"
                onClick={onClick}
                style={{ cursor: onClick ? 'pointer' : 'default' }}
            />
        </Tooltip>
    );
}

export default TechStackBadges;
