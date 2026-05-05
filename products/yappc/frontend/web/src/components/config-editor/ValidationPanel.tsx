/**
 * Validation Panel
 *
 * Display validation results for PageConfig.
 *
 * @packageDocumentation
 */

import { CheckCircle as CheckIcon, AlertCircle as ErrorIcon, Info as InfoIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  Alert,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Chip,
} from '@ghatana/design-system';
import React from 'react';

import type { PageConfig } from 'yappc-config-schema';

/**
 * @doc.type component
 * @doc.purpose Display validation results for PageConfig
 * @doc.layer product
 * @doc.pattern Panel Component
 */
interface ValidationPanelProps {
  config: PageConfig;
}

interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
  info: string[];
}

export const ValidationPanel: React.FC<ValidationPanelProps> = ({ config }) => {
  const validation = validatePageConfig(config);

  if (validation.valid && validation.warnings.length === 0 && validation.info.length === 0) {
    return (
      <Alert severity="success" icon={<CheckIcon />}>
        Configuration is valid
      </Alert>
    );
  }

  return (
    <Box data-testid="validation-panel">
      <Typography variant="h6" gutterBottom>
        Validation Results
      </Typography>

      {validation.errors.length > 0 && (
        <Alert severity="error" sx={{ mb: 2 }}>
          <Typography variant="subtitle2" gutterBottom>
            Errors ({validation.errors.length})
          </Typography>
          <List dense>
            {validation.errors.map((error, idx) => (
              <ListItem key={idx}>
                <ListItemIcon>
                  <ErrorIcon size={16} className="text-destructive" />
                </ListItemIcon>
                <ListItemText primary={error} />
              </ListItem>
            ))}
          </List>
        </Alert>
      )}

      {validation.warnings.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          <Typography variant="subtitle2" gutterBottom>
            Warnings ({validation.warnings.length})
          </Typography>
          <List dense>
            {validation.warnings.map((warning, idx) => (
              <ListItem key={idx}>
                <ListItemIcon>
                  <InfoIcon size={16} className="text-warning-color" />
                </ListItemIcon>
                <ListItemText primary={warning} />
              </ListItem>
            ))}
          </List>
        </Alert>
      )}

      {validation.info.length > 0 && (
        <Alert severity="info">
          <Typography variant="subtitle2" gutterBottom>
            Information ({validation.info.length})
          </Typography>
          <List dense>
            {validation.info.map((info, idx) => (
              <ListItem key={idx}>
                <ListItemIcon>
                  <InfoIcon size={16} className="text-info-color" />
                </ListItemIcon>
                <ListItemText primary={info} />
              </ListItem>
            ))}
          </List>
        </Alert>
      )}

      <Stack direction="row" spacing={2} sx={{ mt: 2 }}>
        <Chip
          label={`Components: ${config.components?.length || 0}`}
          variant="outlined"
          size="small"
        />
        <Chip
          label={`Connections: ${config.connections ? (config.connections.events?.length || 0) + (config.connections.data?.length || 0) + (config.connections.navigation?.length || 0) : 0}`}
          variant="outlined"
          size="small"
        />
        <Chip
          label={`Layout: ${config.layout}`}
          variant="outlined"
          size="small"
        />
      </Stack>
    </Box>
  );
};

function validatePageConfig(config: PageConfig): ValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];
  const info: string[] = [];

  // Required fields
  if (!config.id) {
    errors.push('Missing required field: id');
  }
  if (!config.title) {
    errors.push('Missing required field: title');
  }
  if (!config.route) {
    errors.push('Missing required field: route');
  }

  // Components validation
  if (!config.components || config.components.length === 0) {
    warnings.push('No components defined');
  } else {
    config.components.forEach((comp, idx) => {
      if (!comp.id) {
        errors.push(`Component ${idx}: Missing id`);
      }
      if (!comp.type) {
        errors.push(`Component ${idx}: Missing type`);
      }
    });
  }

  // Connections validation
  if (config.connections) {
    if (!config.connections.events && !config.connections.data && !config.connections.navigation) {
      info.push('Connections defined but empty');
    }
  }

  // Layout validation
  const validLayouts = ['canvas', 'grid', 'flex', 'sidebar'];
  if (!validLayouts.includes(config.layout)) {
    errors.push(`Invalid layout: ${config.layout}. Must be one of: ${validLayouts.join(', ')}`);
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
    info,
  };
}
