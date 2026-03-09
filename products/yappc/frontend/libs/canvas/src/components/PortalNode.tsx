import { ExternalLink as Launch, SubdirectoryArrowRight, SubdirectoryArrowLeft, Eye as Visibility, Settings, Image as ImageIcon } from 'lucide-react';
import { Handle, Position } from '@xyflow/react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  IconButton,
  Chip,
  Tooltip,
  Avatar,
  Stack,
} from '@ghatana/ui';
import { alpha, useTheme, resolveMuiColor } from '@ghatana/yappc-ui';
import React, { memo, useState } from 'react';

import type { NodeProps } from '@xyflow/react';

/**
 *
 */
export interface PortalNodeData {
  label: string;
  description?: string;
  thumbnail?: string;
  targetCanvasId: string;
  portalType: 'entry' | 'exit';
  onDrillDown: () => void;
  nodeCount?: number;
  lastModified?: number;
}

/**
 * Portal Node component for canvas drill-down navigation
 * Implements Phase 5 portal element requirements
 */
export function PortalNode({ id, data, selected }: NodeProps<PortalNodeData>) {
  const theme = useTheme();
  const [isHovered, setIsHovered] = useState(false);
  const [showPreview, setShowPreview] = useState(false);

  const isEntry = data.portalType === 'entry';
  const portalIcon = isEntry ? SubdirectoryArrowRight : SubdirectoryArrowLeft;
  const portalColor = isEntry ? 'primary' : 'secondary';

  const handleDrillDown = (e: React.MouseEvent) => {
    e.stopPropagation();
    data.onDrillDown();
  };

  const handlePreviewToggle = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowPreview(!showPreview);
  };

  const formatLastModified = (timestamp?: number) => {
    if (!timestamp) return 'Never';
    const now = Date.now();
    const diff = now - timestamp;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days}d ago`;
    if (hours > 0) return `${hours}h ago`;
    if (minutes > 0) return `${minutes}m ago`;
    return 'Just now';
  };

  return (
    <Box
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      className="relative transition-all duration-300" style={{ transform: isHovered ? 'scale(1.02)' : 'scale(1)' }}
    >
      {/* Connection handles */}
      <Handle
        type="target"
        position={Position.Left}
        style={{
          background: '#555',
          width: 8,
          height: 8,
        }}
      />
      <Handle
        type="source"
        position={Position.Right}
        style={{
          background: '#555',
          width: 8,
          height: 8,
        }}
      />

      {/* Main portal card */}
      <Card
        className="min-w-[200px] max-w-[280px]" style={{ borderWidth: selected ? 2 : 1, borderStyle: 'solid', borderColor: portalColor }}
      >
        <CardContent className="p-4 last:pb-4">
          {/* Header with icon and type */}
          <Stack direction="row" alignItems="center" spacing={1} mb={1}>
            <Avatar
              className="w-[32px] h-[32px]" style={{ backgroundColor: portalColor }}
            >
              {React.createElement(portalIcon, { fontSize: 'small' })}
            </Avatar>
            <Box flexGrow={1}>
              <Typography variant="subtitle2" fontWeight="bold">
                {data.label}
              </Typography>
              <Chip
                label={isEntry ? 'Entry Portal' : 'Exit Portal'}
                size="small"
                color={resolveMuiColor(theme, portalColor, 'default')}
                variant="outlined"
                className="text-[0.7rem] h-[16px]"
              />
            </Box>
          </Stack>

          {/* Description */}
          {data.description && (
            <Typography
              variant="body2"
              color="text.secondary"
              className="mb-2 text-[0.8rem]"
            >
              {data.description}
            </Typography>
          )}

          {/* Metadata */}
          <Stack direction="row" spacing={1} mb={1.5} flexWrap="wrap">
            {data.nodeCount !== undefined && (
              <Chip
                label={`${data.nodeCount} nodes`}
                size="small"
                variant="outlined"
                className="text-[0.7rem] h-[18px]"
              />
            )}
            {data.lastModified && (
              <Chip
                label={formatLastModified(data.lastModified)}
                size="small"
                variant="outlined"
                className="text-[0.7rem] h-[18px]"
              />
            )}
          </Stack>

          {/* Thumbnail preview */}
          {data.thumbnail && (
            <Box
              className="w-full h-[80px] bg-gray-100 dark:bg-gray-800 rounded mb-2" style={{ backgroundImage: `url(${data.thumbnail }}
            >
              {!data.thumbnail && <ImageIcon color="disabled" />}
            </Box>
          )}

          {/* Action buttons */}
          <Stack direction="row" spacing={1} justifyContent="space-between">
            <Tooltip title={isEntry ? 'Drill down into canvas' : 'Return to parent canvas'}>
              <IconButton
                size="small"
                color={resolveMuiColor(theme, portalColor, 'default')}
                onClick={handleDrillDown}
                style={{
                  backgroundColor: alpha(portalColor === 'primary' ? '#2196f3' : '#9c27b0', 0.1),
                }}
              >
                <Launch size={16} />
              </IconButton>
            </Tooltip>

            <Stack direction="row" spacing={0.5}>
              <Tooltip title="Preview canvas">
                <IconButton
                  size="small"
                  onClick={handlePreviewToggle}
                  color={resolveMuiColor(theme, showPreview ? 'primary' : 'default', 'default')}
                >
                  <Visibility size={16} />
                </IconButton>
              </Tooltip>

              <Tooltip title="Portal settings">
                <IconButton size="small" color="default">
                  <Settings size={16} />
                </IconButton>
              </Tooltip>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {/* Hover overlay with quick actions */}
      {isHovered && (
        <Box
          className="absolute top-[-8px] right-[-8px] z-[1000]"
        >
          <Tooltip title={`Target: ${data.targetCanvasId}`}>
            <Chip
              label={data.targetCanvasId}
              size="small"
              color="info"
              className="text-[0.7rem] h-[20px] bg-white dark:bg-gray-900 shadow"
            />
          </Tooltip>
        </Box>
      )}
    </Box>
  );
}

export default memo(PortalNode);