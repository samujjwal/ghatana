/**
 * Version History
 *
 * Version history viewer with rollback and compare functionality.
 *
 * @packageDocumentation
 */

import { History as HistoryIcon, Undo as RollbackIcon, GitCompare as CompareIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  Button,
  Paper,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Chip,
  Divider,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@ghatana/design-system';
import React, { useState, useCallback, useMemo } from 'react';

import type { PageConfig } from 'yappc-config-schema';

import { ConfigDiff } from './ConfigDiff';

/**
 * @doc.type component
 * @doc.purpose Version history viewer with rollback and compare functionality
 * @doc.layer product
 * @doc.pattern Panel Component
 */
interface ConfigVersion {
  id: string;
  timestamp: Date;
  config: PageConfig;
  author: string;
  message: string;
}

interface VersionHistoryProps {
  versions: ConfigVersion[];
  currentVersionId: string;
  onRollback?: (versionId: string) => void;
  onCompare?: (baseId: string, targetId: string) => void;
}

export const VersionHistory: React.FC<VersionHistoryProps> = ({
  versions,
  currentVersionId,
  onRollback,
  onCompare,
}) => {
  const [compareDialogOpen, setCompareDialogOpen] = useState(false);
  const [selectedVersionId, setSelectedVersionId] = useState<string | null>(null);

  const sortedVersions = useMemo(() => {
    return [...versions].sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime());
  }, [versions]);

  const handleRollback = useCallback(
    (versionId: string) => {
      if (versionId === currentVersionId) return;
      onRollback?.(versionId);
    },
    [currentVersionId, onRollback]
  );

  const handleCompare = useCallback(
    (versionId: string) => {
      setSelectedVersionId(versionId);
      setCompareDialogOpen(true);
    },
    []
  );

  const handleCompareConfirm = useCallback(() => {
    if (selectedVersionId) {
      onCompare?.(selectedVersionId, currentVersionId);
      setCompareDialogOpen(false);
      setSelectedVersionId(null);
    }
  }, [selectedVersionId, currentVersionId, onCompare]);

  const selectedVersion = selectedVersionId
    ? versions.find((v) => v.id === selectedVersionId)
    : null;

  const currentVersion = versions.find((v) => v.id === currentVersionId);

  return (
    <Box data-testid="version-history">
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ mb: 2 }}
      >
        <Stack direction="row" alignItems="center" spacing={1}>
          <HistoryIcon size={16} />
          <Typography variant="h6">Version History</Typography>
        </Stack>
        <Chip label={`${versions.length} versions`} size="small" variant="outlined" />
      </Stack>

      <List>
        {sortedVersions.map((version: ConfigVersion, index: number) => {
          const isCurrent = version.id === currentVersionId;
          return (
            <React.Fragment key={version.id}>
              <ListItem
                secondaryAction={
                  !isCurrent && (
                    <Stack direction="row" spacing={1}>
                      <Button
                        size="small"
                        variant="outlined"
                        startIcon={<CompareIcon size={14} />}
                        onClick={() => handleCompare(version.id)}
                      >
                        Compare
                      </Button>
                      <Button
                        size="small"
                        variant="outlined"
                        color="warning"
                        startIcon={<RollbackIcon size={14} />}
                        onClick={() => handleRollback(version.id)}
                      >
                        Rollback
                      </Button>
                    </Stack>
                  )
                }
              >
                <ListItemIcon>
                  {isCurrent ? (
                    <div className="w-2 h-2 rounded-full bg-green-500" />
                  ) : (
                    <div className="w-2 h-2 rounded-full bg-gray-300" />
                  )}
                </ListItemIcon>
                <ListItemText
                  primary={
                    <Stack direction="row" alignItems="center" spacing={1}>
                      <Typography variant="subtitle2" className="font-medium">
                        {version.message}
                      </Typography>
                      {isCurrent && (
                        <Chip label="Current" size="small" color="success" />
                      )}
                    </Stack>
                  }
                  secondary={
                    <Stack spacing={0.5}>
                      <Typography variant="caption" color="text.secondary">
                        {version.timestamp.toLocaleString()}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        by {version.author}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        ID: {version.id}
                      </Typography>
                    </Stack>
                  }
                />
              </ListItem>
              {index < sortedVersions.length - 1 && <Divider />}
            </React.Fragment>
          );
        })}
      </List>

      {/* Compare Dialog */}
      <Dialog
        open={compareDialogOpen}
        onClose={() => setCompareDialogOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Compare Versions</DialogTitle>
        <DialogContent>
          {selectedVersion && currentVersion && (
            <ConfigDiff
              baseConfig={selectedVersion.config}
              targetConfig={currentVersion.config}
              onApplyChange={() => { }}
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCompareDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleCompareConfirm} variant="contained">
            Apply Changes
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export type { ConfigVersion };
