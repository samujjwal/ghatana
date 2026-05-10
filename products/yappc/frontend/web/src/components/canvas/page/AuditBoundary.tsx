/**
 * @doc.type component
 * @doc.purpose Boundary component for displaying audit warnings and telemetry status
 * @doc.layer product
 * @doc.pattern Widget
 */

import { AlertTriangle, X } from 'lucide-react';
import { Box, Stack, Typography, IconButton, Alert } from '@ghatana/design-system';
import React, { useCallback } from 'react';

interface AuditBoundaryProps {
  readonly auditWarning: string | null;
  readonly onDismiss?: () => void;
}

export const AuditBoundary: React.FC<AuditBoundaryProps> = ({ auditWarning, onDismiss }) => {
  const handleDismiss = useCallback(() => {
    onDismiss?.();
  }, [onDismiss]);

  if (!auditWarning) {
    return null;
  }

  return (
    <Alert severity="warning" sx={{ mb: 2 }}>
      <Stack direction="row" alignItems="flex-start" spacing={2}>
        <AlertTriangle size={20} />
        <Box sx={{ flex: 1 }}>
          <Typography variant="body2">Audit Warning</Typography>
          <Typography variant="body2" color="text.secondary">
            {auditWarning}
          </Typography>
        </Box>
        {onDismiss && (
          <IconButton size="small" onClick={handleDismiss} aria-label="Dismiss audit warning">
            <X size={16} />
          </IconButton>
        )}
      </Stack>
    </Alert>
  );
};
