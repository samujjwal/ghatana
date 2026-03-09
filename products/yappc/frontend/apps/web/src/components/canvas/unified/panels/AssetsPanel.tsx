/**
 * Enhanced Assets Panel
 *
 * Context-aware shape/icon library with intelligent filtering.
 * Supports search, favorites, and AI suggestions.
 *
 * @doc.type component
 * @doc.purpose Asset library with context filtering
 * @doc.layer components
 */

import { useState, useMemo } from 'react';
import {
  Box,
  Typography,
  InputAdornment,
  Chip,
  IconButton,
  Tooltip,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { Accordion, AccordionSummary, AccordionDetails } from '@ghatana/yappc-ui';
import { Search, List as ViewList, LayoutGrid as ViewModule } from 'lucide-react';
import type {
  RailPanelProps,
  AssetTemplate,
  AssetCategory,
} from '../UnifiedLeftRail.types';
import {
  ASSET_CATEGORY_META,
  getVisibleAssetCategories,
  getPrioritizedCategories,
} from '../rail-config';
import { SHAPE_TEMPLATES } from '../ShapeLibrary';

/**
 * Enhanced Assets Panel Component
 */
export function AssetsPanel({ context, onInsertNode }: RailPanelProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedCategories, setExpandedCategories] = useState<
    Set<AssetCategory>
  >(
    new Set(['basic']) // Default expanded
  );
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

  // Filter assets based on context
  const visibleCategories = useMemo(
    () => getVisibleAssetCategories(context),
    [context.mode, context.role, context.phase]
  );

  const prioritizedCategories = useMemo(
    () => getPrioritizedCategories(context.mode, context.role),
    [context.mode, context.role]
  );

  // Convert legacy SHAPE_TEMPLATES to new format
  const allAssets = useMemo<AssetTemplate[]>(() => {
    return SHAPE_TEMPLATES.map((template) => ({
      ...template,
      tags: [template.type, template.category, template.name.toLowerCase()],
      visibility: undefined, // Use category visibility
    }));
  }, []);

  // Group assets by category
  const assetsByCategory = useMemo(() => {
    const filtered = searchQuery
      ? allAssets.filter(
          (asset) =>
            asset.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
            asset.tags?.some((tag) => tag.includes(searchQuery.toLowerCase()))
        )
      : allAssets;

    const grouped = new Map<AssetCategory, AssetTemplate[]>();
    filtered.forEach((asset) => {
      if (visibleCategories.includes(asset.category)) {
        if (!grouped.has(asset.category)) {
          grouped.set(asset.category, []);
        }
        grouped.get(asset.category)!.push(asset);
      }
    });

    return grouped;
  }, [allAssets, visibleCategories, searchQuery]);

  // Sorted categories (prioritized first)
  const sortedCategories = useMemo(() => {
    const categories = Array.from(assetsByCategory.keys());
    return categories.sort((a, b) => {
      const aPriority = prioritizedCategories.indexOf(a);
      const bPriority = prioritizedCategories.indexOf(b);

      if (aPriority !== -1 && bPriority !== -1) return aPriority - bPriority;
      if (aPriority !== -1) return -1;
      if (bPriority !== -1) return 1;

      return a.localeCompare(b);
    });
  }, [assetsByCategory, prioritizedCategories]);

  const handleAccordionChange = (category: AssetCategory) => {
    setExpandedCategories((prev) => {
      const next = new Set(prev);
      if (next.has(category)) {
        next.delete(category);
      } else {
        next.add(category);
      }
      return next;
    });
  };

  const handleAssetClick = (asset: AssetTemplate) => {
    if (onInsertNode) {
      onInsertNode({ type: asset.type, ...asset.defaultData });
    }
  };

  return (
    <Box className="flex flex-col h-full">
      {/* Search Bar */}
      <Box className="p-4 pb-2">
        <TextField
          fullWidth
          size="small"
          placeholder="Search assets..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <Search size={16} />
              </InputAdornment>
            ),
          }}
        />

        {/* Context Indicator */}
        <Box className="flex gap-1 mt-2 flex-wrap">
          {context.role && (
            <Chip label={context.role} size="small" variant="outlined" />
          )}
          {context.phase && (
            <Chip label={context.phase} size="small" variant="outlined" />
          )}
        </Box>
      </Box>

      {/* View Toggle */}
      <Box
        className="px-4 pb-2 flex justify-between items-center"
      >
        <Typography variant="caption" color="text.secondary">
          {assetsByCategory.size} categories, {allAssets.length} assets
        </Typography>
        <Box>
          <IconButton
            size="small"
            onClick={() => setViewMode('grid')}
            style={{ opacity: viewMode === 'grid' ? 1 : 0.5 }}
          >
            <ViewModule size={16} />
          </IconButton>
          <IconButton
            size="small"
            onClick={() => setViewMode('list')}
            style={{ opacity: viewMode === 'list' ? 1 : 0.5 }}
          >
            <ViewList size={16} />
          </IconButton>
        </Box>
      </Box>

      {/* Asset Categories */}
      <Box className="flex-1 overflow-auto px-2">
        {sortedCategories.map((categoryId) => {
          const categoryMeta = ASSET_CATEGORY_META[categoryId];
          const assets = assetsByCategory.get(categoryId) || [];
          const isPriority = prioritizedCategories.includes(categoryId);

          return (
            <Accordion
              key={categoryId}
              expanded={expandedCategories.has(categoryId)}
              onChange={() => handleAccordionChange(categoryId)}
              disableGutters
              className={`shadow-none ${isPriority ? 'bg-gray-100 dark:bg-gray-800' : 'bg-transparent'}`}
            >
              <AccordionSummary
                className="min-h-[40px] [&_.MuiAccordionSummary-content]:my-2"
              >
                <Box
                  className="flex items-center gap-2 w-full"
                >
                  <Box className="text-[1.2rem]">{categoryMeta.icon}</Box>
                  <Typography variant="body2" fontWeight={600}>
                    {categoryMeta.label}
                  </Typography>
                  <Chip
                    label={assets.length}
                    size="small"
                    className="h-[20px] ml-auto"
                  />
                  {isPriority && (
                    <Tooltip title="Recommended for your role">
                      <span style={{ fontSize: '0.9rem' }}>⭐</span>
                    </Tooltip>
                  )}
                </Box>
              </AccordionSummary>
              <AccordionDetails className="pt-0">
                {/* Grid View */}
                {viewMode === 'grid' && (
                  <Box
                    className="grid gap-2 [grid-template-columns:repeat(auto-fill,minmax(72px,1fr))]"
                  >
                    {assets.map((asset) => (
                      <Tooltip
                        key={asset.id}
                        title={asset.name}
                        placement="top"
                      >
                        <Box
                          onClick={() => handleAssetClick(asset)}
                          className="flex flex-col items-center justify-center cursor-pointer rounded border border-solid border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 transition-all duration-200 hover:bg-gray-100 hover:dark:bg-gray-800 hover:border-blue-600" >
                          <Box className="text-2xl mb-1">
                            {asset.icon}
                          </Box>
                          <Typography
                            variant="caption"
                            className="text-center px-1 text-[0.65rem]"
                          >
                            {asset.name.split(' ')[0]}
                          </Typography>
                        </Box>
                      </Tooltip>
                    ))}
                  </Box>
                )}

                {/* List View */}
                {viewMode === 'list' && (
                  <Box
                    className="flex flex-col gap-1"
                  >
                    {assets.map((asset) => (
                      <Box
                        key={asset.id}
                        onClick={() => handleAssetClick(asset)}
                        className="flex items-center gap-2 p-2 cursor-pointer rounded hover:bg-gray-100 hover:dark:bg-gray-800"
                      >
                        <Box className="text-[1.2rem]">{asset.icon}</Box>
                        <Typography variant="body2">{asset.name}</Typography>
                      </Box>
                    ))}
                  </Box>
                )}
              </AccordionDetails>
            </Accordion>
          );
        })}
      </Box>

      {/* Empty State */}
      {sortedCategories.length === 0 && (
        <Box className="p-6 text-center">
          <Typography variant="body2" color="text.secondary">
            {searchQuery
              ? 'No assets match your search'
              : 'No assets yet for this context'}
          </Typography>
        </Box>
      )}
    </Box>
  );
}
