/**
 * Quick Create Menu Component
 * 
 * Context menu for rapid artifact creation via double-click.
 * 
 * @doc.type component
 * @doc.purpose Quick artifact creation menu
 * @doc.layer product
 * @doc.pattern ContextMenu
 */

import * as React from 'react';
import { Menu, MenuItem, ListItemIcon, ListItemText, Typography, Divider, Box, TextField, InputAdornment } from '@ghatana/ui';
import { Search as SearchIcon } from 'lucide-react';
import { ARTIFACT_TEMPLATES, type ArtifactTemplate } from './artifact-templates';
import { LifecyclePhase } from '@/types/lifecycle';

export interface QuickCreateMenuProps {
    open: boolean;
    anchorPosition: { x: number; y: number } | null;
    currentPhase: LifecyclePhase;
    onClose: () => void;
    onCreate: (template: ArtifactTemplate, position: { x: number; y: number }) => void;
}

export const QuickCreateMenu: React.FC<QuickCreateMenuProps> = ({
    open,
    anchorPosition,
    currentPhase,
    onClose,
    onCreate,
}) => {
    const [searchQuery, setSearchQuery] = React.useState('');
    const searchInputRef = React.useRef<HTMLInputElement>(null);

    // Focus search input when menu opens
    React.useEffect(() => {
        if (open && searchInputRef.current) {
            setTimeout(() => searchInputRef.current?.focus(), 100);
        }
        if (!open) {
            setSearchQuery('');
        }
    }, [open]);

    const filteredTemplates = React.useMemo(() => {
        const query = searchQuery.toLowerCase().trim();
        if (!query) {
            // Show templates for current phase first, then others
            const currentPhaseTemplates = ARTIFACT_TEMPLATES.filter((t) => t.phase === currentPhase);
            const otherTemplates = ARTIFACT_TEMPLATES.filter((t) => t.phase !== currentPhase);
            return [...currentPhaseTemplates, ...otherTemplates];
        }

        return ARTIFACT_TEMPLATES.filter(
            (t) =>
                t.label.toLowerCase().includes(query) ||
                t.description.toLowerCase().includes(query) ||
                t.type.toLowerCase().includes(query)
        );
    }, [searchQuery, currentPhase]);

    const handleCreate = (template: ArtifactTemplate) => {
        if (anchorPosition) {
            onCreate(template, anchorPosition);
        }
        onClose();
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Escape') {
            onClose();
        }
    };

    return (
        <Menu
            open={open}
            onClose={onClose}
            anchorReference="anchorPosition"
            anchorPosition={anchorPosition ? { top: anchorPosition.y, left: anchorPosition.x } : undefined}
            PaperProps={{
                sx: {
                    minWidth: 320,
                    maxHeight: 500,
                },
            }}
            onKeyDown={handleKeyDown}
        >
            {/* Search Header */}
            <Box className="px-4 py-3 border-gray-200 dark:border-gray-700 border-b" >
                <TextField
                    inputRef={searchInputRef}
                    fullWidth
                    size="sm"
                    placeholder="Search artifacts..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    InputProps={{
                        startAdornment: (
                            <InputAdornment position="start">
                                <SearchIcon size={16} />
                            </InputAdornment>
                        ),
                        className: 'bg-white dark:bg-gray-900',
                    }}
                />
            </Box>

            {/* Context Info */}
            {!searchQuery && (
                <Box className="px-4 py-2 bg-gray-100 dark:bg-gray-800">
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        Creating in <strong>{currentPhase}</strong> phase
                    </Typography>
                </Box>
            )}

            {/* Template List */}
            {filteredTemplates.length > 0 ? (
                filteredTemplates.flatMap((template, index) => {
                    const isCurrentPhase = template.phase === currentPhase;
                    const showDivider =
                        !searchQuery &&
                        index > 0 &&
                        filteredTemplates[index - 1].phase !== template.phase &&
                        isCurrentPhase !== (filteredTemplates[index - 1].phase === currentPhase);

                    const items = [];
                    if (showDivider) {
                        items.push(<Divider key={`divider-${template.type}`} className="my-2" />);
                    }
                    items.push(
                        <MenuItem
                            key={template.type}
                            onClick={() => handleCreate(template)}
                            className={`py-3 ${isCurrentPhase ? 'bg-gray-50' : 'bg-transparent'}`}
                        >
                            <ListItemIcon className="text-2xl">{template.icon}</ListItemIcon>
                            <ListItemText
                                primary={
                                    <Box className="flex items-center gap-2">
                                        <Typography as="p" className="text-sm">{template.label}</Typography>
                                        {isCurrentPhase && (
                                            <Typography
                                                as="span" className="text-xs text-gray-500"
                                                className="px-1 bg-blue-600 text-white rounded-sm text-[0.65rem]"
                                            >
                                                CURRENT
                                            </Typography>
                                        )}
                                    </Box>
                                }
                                secondary={template.description}
                                secondaryTypographyProps={{
                                    variant: 'caption',
                                    color: 'text.secondary',
                                }}
                            />
                        </MenuItem>
                    );
                    return items;
                })
            ) : (
                <Box className="px-4 py-6 text-center">
                    <Typography as="p" className="text-sm" color="text.secondary">
                        No artifacts found
                    </Typography>
                </Box>
            )}
        </Menu>
    );
};
