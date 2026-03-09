/**
 * Style Tokens Canvas Content
 * 
 * CSS/design token editor for Design × Code level.
 * Raw token values with code generation.
 * 
 * @doc.type component
 * @doc.purpose Style token editor with code export
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

interface StyleToken {
    id: string;
    name: string;
    value: string;
    cssVar: string;
    sassVar: string;
    jsConst: string;
    category: 'color' | 'typography' | 'spacing' | 'shadow' | 'animation' | 'breakpoint';
    platform: 'web' | 'mobile' | 'all';
}

// Mock style tokens
const MOCK_TOKENS: StyleToken[] = [
    {
        id: '1',
        name: 'Primary Color',
        value: '#6366F1',
        cssVar: '--color-primary',
        sassVar: '$color-primary',
        jsConst: 'COLOR_PRIMARY',
        category: 'color',
        platform: 'all',
    },
    {
        id: '2',
        name: 'Secondary Color',
        value: '#8B5CF6',
        cssVar: '--color-secondary',
        sassVar: '$color-secondary',
        jsConst: 'COLOR_SECONDARY',
        category: 'color',
        platform: 'all',
    },
    {
        id: '3',
        name: 'Success Color',
        value: '#10B981',
        cssVar: '--color-success',
        sassVar: '$color-success',
        jsConst: 'COLOR_SUCCESS',
        category: 'color',
        platform: 'all',
    },
    {
        id: '4',
        name: 'Error Color',
        value: '#EF4444',
        cssVar: '--color-error',
        sassVar: '$color-error',
        jsConst: 'COLOR_ERROR',
        category: 'color',
        platform: 'all',
    },
    {
        id: '5',
        name: 'Font Size Base',
        value: '16px',
        cssVar: '--font-size-base',
        sassVar: '$font-size-base',
        jsConst: 'FONT_SIZE_BASE',
        category: 'typography',
        platform: 'web',
    },
    {
        id: '6',
        name: 'Font Size Large',
        value: '20px',
        cssVar: '--font-size-lg',
        sassVar: '$font-size-lg',
        jsConst: 'FONT_SIZE_LG',
        category: 'typography',
        platform: 'web',
    },
    {
        id: '7',
        name: 'Line Height Normal',
        value: '1.5',
        cssVar: '--line-height-normal',
        sassVar: '$line-height-normal',
        jsConst: 'LINE_HEIGHT_NORMAL',
        category: 'typography',
        platform: 'web',
    },
    {
        id: '8',
        name: 'Spacing Small',
        value: '8px',
        cssVar: '--spacing-sm',
        sassVar: '$spacing-sm',
        jsConst: 'SPACING_SM',
        category: 'spacing',
        platform: 'all',
    },
    {
        id: '9',
        name: 'Spacing Medium',
        value: '16px',
        cssVar: '--spacing-md',
        sassVar: '$spacing-md',
        jsConst: 'SPACING_MD',
        category: 'spacing',
        platform: 'all',
    },
    {
        id: '10',
        name: 'Spacing Large',
        value: '24px',
        cssVar: '--spacing-lg',
        sassVar: '$spacing-lg',
        jsConst: 'SPACING_LG',
        category: 'spacing',
        platform: 'all',
    },
    {
        id: '11',
        name: 'Shadow Small',
        value: '0 1px 3px rgba(0,0,0,0.12)',
        cssVar: '--shadow-sm',
        sassVar: '$shadow-sm',
        jsConst: 'SHADOW_SM',
        category: 'shadow',
        platform: 'web',
    },
    {
        id: '12',
        name: 'Shadow Medium',
        value: '0 4px 6px rgba(0,0,0,0.1)',
        cssVar: '--shadow-md',
        sassVar: '$shadow-md',
        jsConst: 'SHADOW_MD',
        category: 'shadow',
        platform: 'web',
    },
    {
        id: '13',
        name: 'Transition Fast',
        value: '150ms ease-in-out',
        cssVar: '--transition-fast',
        sassVar: '$transition-fast',
        jsConst: 'TRANSITION_FAST',
        category: 'animation',
        platform: 'web',
    },
    {
        id: '14',
        name: 'Transition Normal',
        value: '300ms ease-in-out',
        cssVar: '--transition-normal',
        sassVar: '$transition-normal',
        jsConst: 'TRANSITION_NORMAL',
        category: 'animation',
        platform: 'web',
    },
    {
        id: '15',
        name: 'Breakpoint Mobile',
        value: '640px',
        cssVar: '--breakpoint-mobile',
        sassVar: '$breakpoint-mobile',
        jsConst: 'BREAKPOINT_MOBILE',
        category: 'breakpoint',
        platform: 'web',
    },
    {
        id: '16',
        name: 'Breakpoint Tablet',
        value: '768px',
        cssVar: '--breakpoint-tablet',
        sassVar: '$breakpoint-tablet',
        jsConst: 'BREAKPOINT_TABLET',
        category: 'breakpoint',
        platform: 'web',
    },
];

const getCategoryColor = (category: StyleToken['category']) => {
    switch (category) {
        case 'color':
            return '#6366F1';
        case 'typography':
            return '#8B5CF6';
        case 'spacing':
            return '#10B981';
        case 'shadow':
            return '#F59E0B';
        case 'animation':
            return '#EC4899';
        case 'breakpoint':
            return '#06B6D4';
    }
};

export const StyleTokensCanvas = () => {
    const [tokens] = useState<StyleToken[]>(MOCK_TOKENS);
    const [selectedToken, setSelectedToken] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterCategory, setFilterCategory] = useState<StyleToken['category'] | 'all'>('all');
    const [exportFormat, setExportFormat] = useState<'css' | 'sass' | 'js'>('css');

    const filteredTokens = useMemo(() => {
        return tokens.filter(token => {
            const matchesSearch =
                searchQuery === '' ||
                token.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                token.cssVar.toLowerCase().includes(searchQuery.toLowerCase());

            const matchesCategory = filterCategory === 'all' || token.category === filterCategory;

            return matchesSearch && matchesCategory;
        });
    }, [tokens, searchQuery, filterCategory]);

    const exportCode = useMemo(() => {
        const filtered = filteredTokens;
        if (exportFormat === 'css') {
            return `:root {\n${filtered.map(t => `  ${t.cssVar}: ${t.value};`).join('\n')}\n}`;
        } else if (exportFormat === 'sass') {
            return filtered.map(t => `${t.sassVar}: ${t.value};`).join('\n');
        } else {
            return filtered.map(t => `export const ${t.jsConst} = '${t.value}';`).join('\n');
        }
    }, [filteredTokens, exportFormat]);

    const stats = useMemo(() => {
        return {
            total: tokens.length,
            byCategory: {
                color: tokens.filter(t => t.category === 'color').length,
                typography: tokens.filter(t => t.category === 'typography').length,
                spacing: tokens.filter(t => t.category === 'spacing').length,
                shadow: tokens.filter(t => t.category === 'shadow').length,
                animation: tokens.filter(t => t.category === 'animation').length,
                breakpoint: tokens.filter(t => t.category === 'breakpoint').length,
            },
            byPlatform: {
                web: tokens.filter(t => t.platform === 'web' || t.platform === 'all').length,
                mobile: tokens.filter(t => t.platform === 'mobile' || t.platform === 'all').length,
            },
        };
    }, [tokens]);

    const hasContent = tokens.length > 0;

    const selectedTokenData = tokens.find(t => t.id === selectedToken);

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Create Token',
                    onClick: () => {
                        console.log('Create Token');
                    },
                },
                secondaryAction: {
                    label: 'Import Tokens',
                    onClick: () => {
                        console.log('Import Tokens');
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
                            placeholder="Search tokens..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="flex-1"
                        />
                        <Button variant="outlined" size="small">
                            Add Token
                        </Button>
                    </Box>

                    <Box className="flex gap-2 flex-wrap">
                        <Chip
                            label="All"
                            size="small"
                            onClick={() => setFilterCategory('all')}
                            color={filterCategory === 'all' ? 'primary' : 'default'}
                        />
                        {(['color', 'typography', 'spacing', 'shadow', 'animation', 'breakpoint'] as const).map(category => (
                            <Chip
                                key={category}
                                label={category}
                                size="small"
                                onClick={() => setFilterCategory(category)}
                                style={{ backgroundColor: filterCategory === category ? getCategoryColor(category) : undefined, color: filterCategory === category ? 'white' : undefined, backgroundColor: 'token.value' }}
                            />
                        ))}
                    </Box>
                </Box>

                {/* Content area */}
                <Box
                    className="flex-1 flex gap-4 overflow-hidden p-4"
                >
                    {/* Token list */}
                    <Box
                        className="overflow-y-auto w-[50%]"
                    >
                        {filteredTokens.length === 0 && (
                            <Box className="flex justify-center items-center h-full">
                                <Typography color="text.secondary">No tokens match your search</Typography>
                            </Box>
                        )}

                        {filteredTokens.map(token => (
                            <Paper
                                key={token.id}
                                elevation={selectedToken === token.id ? 4 : 2}
                                onClick={() => setSelectedToken(token.id === selectedToken ? null : token.id)}
                                className="p-3 mb-2 cursor-pointer" style={{ border: selectedToken === token.id
                                            ? `3px solid ${getCategoryColor(token.category)}` : '2px solid transparent' }}
                            >
                                <Box className="flex items-center gap-3 mb-1">
                                    {token.category === 'color' && (
                                        <Box className="w-[24px] h-[24px] rounded border border-solid" style={{ backgroundColor: token.value }}
                                        />
                                    )}
                                    <Box className="flex-1">
                                        <Typography variant="subtitle2" className="font-semibold text-[0.85rem] mb-[2.4px]">
                                            {token.name}
                                        </Typography>
                                        <Typography
                                            variant="caption"
                                        >
                                            {token.value}
                                        </Typography>
                                    </Box>
                                    <Chip
                                        label={token.category}
                                        size="small"
                                        className="h-[18px] text-white text-[0.65rem]" />
                                </Box>
                            </Paper>
                        ))}
                    </Box>

                    {/* Code export */}
                    <Box
                        className="flex flex-col w-[50%]"
                    >
                        <Paper elevation={3} className="p-4 mb-4">
                            <Box className="flex gap-2 mb-2">
                                <Chip
                                    label="CSS"
                                    size="small"
                                    onClick={() => setExportFormat('css')}
                                    color={exportFormat === 'css' ? 'primary' : 'default'}
                                />
                                <Chip
                                    label="SASS"
                                    size="small"
                                    onClick={() => setExportFormat('sass')}
                                    color={exportFormat === 'sass' ? 'primary' : 'default'}
                                />
                                <Chip
                                    label="JavaScript"
                                    size="small"
                                    onClick={() => setExportFormat('js')}
                                    color={exportFormat === 'js' ? 'primary' : 'default'}
                                />
                                <Button variant="outlined" size="small" className="ml-auto">
                                    Copy Code
                                </Button>
                            </Box>

                            <Paper
                                className="text-[0.8rem] overflow-y-auto p-4 bg-[#1E1E1E] text-[#D4D4D4] font-mono max-h-[400px] whitespace-pre"
                            >
                                {exportCode}
                            </Paper>
                        </Paper>

                        {selectedTokenData && (
                            <Paper elevation={3} className="flex-1 p-4">
                                <Typography variant="subtitle2" className="font-semibold mb-2">
                                    {selectedTokenData.name}
                                </Typography>

                                {selectedTokenData.category === 'color' && (
                                    <Box
                                        className="w-full rounded h-[80px] border-['2px_solid_rgba(0] mb-4" style={{ backgroundColor: 'selectedTokenData.value' }} />
                                )}

                                <Box className="mb-4">
                                    <Typography variant="caption" className="font-semibold block mb-1">
                                        Value:
                                    </Typography>
                                    <Paper className="p-2 bg-[#F3F4F6]">
                                        <Typography variant="body2" className="text-[0.85rem] font-mono">
                                            {selectedTokenData.value}
                                        </Typography>
                                    </Paper>
                                </Box>

                                <Box className="mb-2">
                                    <Typography variant="caption" className="font-semibold block mb-1">
                                        CSS Variable:
                                    </Typography>
                                    <Typography variant="body2" className="text-[0.8rem] font-mono">
                                        {selectedTokenData.cssVar}
                                    </Typography>
                                </Box>

                                <Box className="mb-2">
                                    <Typography variant="caption" className="font-semibold block mb-1">
                                        SASS Variable:
                                    </Typography>
                                    <Typography variant="body2" className="text-[0.8rem] font-mono">
                                        {selectedTokenData.sassVar}
                                    </Typography>
                                </Box>

                                <Box className="mb-2">
                                    <Typography variant="caption" className="font-semibold block mb-1">
                                        JS Constant:
                                    </Typography>
                                    <Typography variant="body2" className="text-[0.8rem] font-mono">
                                        {selectedTokenData.jsConst}
                                    </Typography>
                                </Box>

                                <Box className="flex gap-1 mt-4">
                                    <Chip
                                        label={selectedTokenData.category}
                                        size="small"
                                        className="text-white" style={{ backgroundColor: getCategoryColor(selectedTokenData.category) }}
                                    />
                                    <Chip label={selectedTokenData.platform} size="small" variant="outlined" />
                                </Box>
                            </Paper>
                        )}
                    </Box>
                </Box>

                {/* Stats panel */}
                <Box
                    className="absolute rounded bottom-[16px] right-[16px] bg-white p-4 shadow min-w-[180px]"
                >
                    <Typography variant="subtitle2" gutterBottom className="font-semibold">
                        Token Stats
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Total: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Web: {stats.byPlatform.web}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Mobile: {stats.byPlatform.mobile}
                    </Typography>
                    <Box className="mt-2">
                        {(Object.entries(stats.byCategory) as [StyleToken['category'], number][]).map(([category, count]) => (
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

export default StyleTokensCanvas;
