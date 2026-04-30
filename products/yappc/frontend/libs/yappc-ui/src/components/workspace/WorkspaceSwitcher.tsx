/**
 * WorkspaceSwitcher
 *
 * Compact workspace selector rendered as a button that opens a dropdown list
 * of available workspaces. Selecting one calls `onSelect` and closes the menu.
 *
 * @doc.type component
 * @doc.purpose Quick workspace switching via dropdown
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import {
  Box,
  Button,
  Menu,
  MenuItem,
  Avatar,
  Typography,
  Divider,
} from '@mui/material';
import {
  ChevronDown as ChevronDownIcon,
  Plus as PlusIcon,
  Check as CheckIcon,
} from 'lucide-react';
import React, { useState } from 'react';

import type { Workspace } from 'yappc-core/types';

export interface WorkspaceSwitcherProps {
  workspaces: Workspace[];
  currentWorkspace?: Workspace | null;
  onSelect: (workspace: Workspace) => void;
  onCreateNew?: () => void;
}

/**
 * A compact workspace selector button with dropdown.
 */
export const WorkspaceSwitcher: React.FC<WorkspaceSwitcherProps> = ({
  workspaces,
  currentWorkspace,
  onSelect,
  onCreateNew,
}) => {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const open = Boolean(anchorEl);

  const handleOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleSelect = (workspace: Workspace) => {
    onSelect(workspace);
    handleClose();
  };

  const initials = (name: string) =>
    name
      .split(' ')
      .map((w) => w[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();

  return (
    <>
      <Button
        variant="text"
        color="inherit"
        onClick={handleOpen}
        endIcon={<ChevronDownIcon size={14} />}
        sx={{
          textTransform: 'none',
          fontWeight: 600,
          px: 1,
          gap: 0.75,
        }}
      >
        {currentWorkspace ? (
          <>
            <Avatar
              sx={{
                width: 22,
                height: 22,
                fontSize: 10,
                bgcolor: 'primary.main',
              }}
            >
              {initials(currentWorkspace.name)}
            </Avatar>
            <Typography
              variant="body2"
              fontWeight={600}
              noWrap
              sx={{ maxWidth: 160 }}
            >
              {currentWorkspace.name}
            </Typography>
          </>
        ) : (
          <Typography variant="body2" color="text.secondary">
            Select workspace
          </Typography>
        )}
      </Button>

      <Menu
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        PaperProps={{ sx: { minWidth: 220, maxHeight: 360 } }}
      >
        {workspaces.map((ws) => (
          <MenuItem
            key={ws.id}
            selected={ws.id === currentWorkspace?.id}
            onClick={() => handleSelect(ws)}
            sx={{ gap: 1 }}
          >
            <Avatar
              sx={{
                width: 24,
                height: 24,
                fontSize: 11,
                bgcolor: 'secondary.main',
              }}
            >
              {initials(ws.name)}
            </Avatar>
            <Box flexGrow={1} minWidth={0}>
              <Typography variant="body2" noWrap fontWeight={500}>
                {ws.name}
              </Typography>
            </Box>
            {ws.id === currentWorkspace?.id && (
              <CheckIcon size={14} color="inherit" />
            )}
          </MenuItem>
        ))}

        {onCreateNew && (
          <>
            <Divider />
            <MenuItem
              onClick={() => {
                onCreateNew();
                handleClose();
              }}
              sx={{ gap: 1 }}
            >
              <PlusIcon size={16} />
              <Typography variant="body2">New workspace</Typography>
            </MenuItem>
          </>
        )}
      </Menu>
    </>
  );
};
