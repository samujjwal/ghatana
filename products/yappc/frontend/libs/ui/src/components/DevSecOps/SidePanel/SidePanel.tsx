/**
 * SidePanel Component
 *
 * A slide-out panel for detailed views and drill-down information.
 *
 * @module DevSecOps/SidePanel
 */

import { X as CloseIcon } from 'lucide-react';
import { Box, Divider, Drawer, IconButton, Typography } from '@ghatana/ui';

import type { SidePanelProps } from './types';
import type React from 'react';

/**
 * SidePanel - Slide-out panel component
 *
 * A drawer component that slides in from the right for detailed content views.
 *
 * @param props - SidePanel component props
 * @returns Rendered SidePanel component
 *
 * @example
 * ```tsx
 * <SidePanel
 *   open={isOpen}
 *   onClose={handleClose}
 *   title="Feature Details"
 *   width={600}
 * >
 *   <DetailedContent />
 * </SidePanel>
 * ```
 */
export const SidePanel: React.FC<SidePanelProps> = ({
  open,
  onClose,
  title,
  children,
  width = 480,
}) => {
  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      className="shadow-xl"
      style={{ width }}
    >
      <Box className="p-6">
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography as="h5" fontWeight={600}>
            {title}
          </Typography>
          <IconButton
            onClick={onClose}
            size="sm"
            aria-label="Close panel"
            className="hover:bg-gray-100 dark:hover:bg-gray-800"
          >
            <CloseIcon />
          </IconButton>
        </Box>
      </Box>

      <Divider />

      <Box
        className="p-6 overflow-y-auto flex-grow [&::-webkit-scrollbar]:w-2 [&::-webkit-scrollbar-thumb]:bg-neutral-300 [&::-webkit-scrollbar-thumb]:rounded"
      >
        {children}
      </Box>
    </Drawer>
  );
};
