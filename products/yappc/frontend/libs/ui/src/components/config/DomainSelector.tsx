/**
 * DomainSelector Component
 *
 * Allows users to select a task domain from available options.
 *
 * @module ui/components/config
 * @doc.type component
 * @doc.purpose Domain selection for task filtering
 * @doc.layer ui
 */

import React from 'react';

import { Box, Typography, Button } from '@ghatana/ui';
import { useAtom } from 'jotai';

import { useTaskDomains } from '../../hooks/useConfig';
import { selectedDomainIdAtom } from '../../state/configAtoms';

// ============================================================================
// Types
// ============================================================================

interface DomainSelectorProps {
  /** Additional CSS class name */
  className?: string;
}

// ============================================================================
// Component
// ============================================================================

/**
 * Domain selector component for choosing task domains
 *
 * @example
 * ```tsx
 * <DomainSelector className="my-4" />
 * ```
 */
export const DomainSelector: React.FC<DomainSelectorProps> = ({ className = '' }) => {
  const domains = useTaskDomains();
  const [selectedId, setSelectedId] = useAtom(selectedDomainIdAtom);

  return (
    <Box className={`${className} space-y-4`}>
      <Typography variant="subtitle2" color="text.secondary" gutterBottom>
        Select Domain
      </Typography>
      <Box
        className="grid grid-cols-1 sm:grid-cols-2 gap-2"
      >
        {domains.map((domain) => (
          <Button
            key={domain.id}
            fullWidth
            variant={selectedId === domain.id ? 'contained' : 'outlined'}
            color={selectedId === domain.id ? 'primary' : 'inherit'}
            onClick={() => setSelectedId(domain.id)}
            className={`p-3 justify-start text-left normal-case ${
              selectedId === domain.id
                ? 'border-blue-600 bg-blue-100 dark:bg-blue-900/30'
                : 'border-gray-200 dark:border-gray-700 bg-transparent'
            }`}
          >
            <Box className="flex items-center gap-2">
              <Typography component="span" className="text-2xl">
                {domain.icon}
              </Typography>
              <Box>
                <Typography variant="body2" fontWeight={500}>
                  {domain.name}
                </Typography>
                {domain.description && (
                  <Typography variant="caption" color="text.secondary">
                    {domain.description}
                  </Typography>
                )}
              </Box>
            </Box>
          </Button>
        ))}
      </Box>
    </Box>
  );
};

export default DomainSelector;
