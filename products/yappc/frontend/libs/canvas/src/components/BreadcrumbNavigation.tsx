import { Home, ChevronRight, Layers, ExternalLink as Launch, History, AlertTriangle as Warning } from 'lucide-react';
import {
  Typography,
  Box,
  Chip,
  IconButton,
  Stack,
  Tooltip,
} from '@ghatana/ui';
import { Breadcrumbs, Link } from '@ghatana/ui';
import React from 'react';

import { useCanvasPortal } from '../hooks/useCanvasPortal';

import type { CSSProperties } from 'react';

export interface BreadcrumbNavigationProps {
  /** Show home icon for root canvas */
  showHomeIcon?: boolean;
  /** Maximum number of breadcrumb items before collapsing */
  maxItems?: number;
  /** Show validation warnings */
  showValidation?: boolean;
  /** Custom styling */
  sx?: CSSProperties & Record<string, unknown>;
}

/**
 * Breadcrumb navigation component for hierarchical canvas drill-down
 * Implements Phase 5 breadcrumb navigation requirements
 */
export function BreadcrumbNavigation({
  showHomeIcon = true,
  maxItems = 5,
  showValidation = true,
  sx = {}
}: BreadcrumbNavigationProps) {
  const {
    drillDownContext,
    navigateToCanvas,
    validateCanvasReferences,
    drillUp
  } = useCanvasPortal();

  const validation = showValidation ? validateCanvasReferences() : null;

  const handleBreadcrumbClick = (canvasId: string) => {
    navigateToCanvas(canvasId);
  };

  const renderBreadcrumbItem = (item: { id: string; label: string }, index: number, isLast: boolean) => {
    const isRoot = item.id === 'root';

    if (isLast) {
      return (
        <Box key={item.id} display="flex" alignItems="center" gap={1}>
          {isRoot && showHomeIcon && <Home size={16} />}
          <Typography
            color="text.primary"
            fontWeight="medium"
            variant="body2"
          >
            {item.label}
          </Typography>
          {drillDownContext.canvasStack.length > 0 && (
            <Chip
              label={`Level ${drillDownContext.canvasStack.length + 1}`}
              size="small"
              variant="outlined"
              color="primary"
            />
          )}
        </Box>
      );
    }

    return (
      <Link
        key={item.id}
        underline="hover"
        color="inherit"
        onClick={() => handleBreadcrumbClick(item.id)}
        className="cursor-pointer flex items-center gap-1 hover:text-blue-600"
      >
        {isRoot && showHomeIcon && <Home size={16} />}
        {item.label}
      </Link>
    );
  };

  return (
    <Box
      style={{ display: 'flex', alignItems: 'center', gap: 16, ...sx }}
    >
      {/* Main breadcrumb navigation */}
      <Breadcrumbs
        aria-label="canvas navigation"
        separator={<ChevronRight size={16} />}
        maxItems={maxItems}
        className="grow"
      >
        {drillDownContext.breadcrumbPath.map((item, index) => {
          const isLast = index === drillDownContext.breadcrumbPath.length - 1;
          return renderBreadcrumbItem(item, index, isLast);
        })}
      </Breadcrumbs>

      {/* Action buttons */}
      <Stack direction="row" spacing={1}>
        {/* Back button */}
        {drillDownContext.canvasStack.length > 0 && (
          <Tooltip title="Back to parent canvas">
            <IconButton
              size="small"
              onClick={drillUp}
              color="primary"
            >
              <History />
            </IconButton>
          </Tooltip>
        )}

        {/* Canvas layers indicator */}
        <Tooltip title={`Canvas depth: ${drillDownContext.canvasStack.length + 1}`}>
          <IconButton size="small" disabled>
            <Layers />
            <Typography variant="caption" className="ml-1">
              {drillDownContext.canvasStack.length + 1}
            </Typography>
          </IconButton>
        </Tooltip>

        {/* Validation warning */}
        {validation && !validation.isValid && (
          <Tooltip title={`Validation issues: ${validation.errors.length} errors, ${validation.warnings.length} warnings`}>
            <IconButton size="small" color="warning">
              <Warning />
            </IconButton>
          </Tooltip>
        )}

        {/* Deep link button */}
        <Tooltip title="Copy deep link to current canvas">
          <IconButton
            size="small"
            onClick={() => {
              const url = window.location.href;
              navigator.clipboard.writeText(url);
            }}
          >
            <Launch />
          </IconButton>
        </Tooltip>
      </Stack>
    </Box>
  );
}

export default BreadcrumbNavigation;