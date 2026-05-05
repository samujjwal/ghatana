/**
 * Accessibility Checker
 *
 * Automated accessibility checks for PageConfig.
 *
 * @packageDocumentation
 */

import { Accessibility as A11yIcon, AlertTriangle as WarningIcon, Check as CheckIcon } from 'lucide-react';
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
import React, { useMemo } from 'react';

import type { PageConfig } from 'yappc-config-schema';

/**
 * @doc.type component
 * @doc.purpose Automated accessibility checks for PageConfig
 * @doc.layer product
 * @doc.pattern Panel Component
 */
interface AccessibilityCheckerProps {
  config: PageConfig;
}

interface A11yIssue {
  componentId: string;
  severity: 'error' | 'warning' | 'info';
  message: string;
  suggestion?: string;
}

export const AccessibilityChecker: React.FC<AccessibilityCheckerProps> = ({ config }) => {
  const issues = useMemo(() => {
    const results: A11yIssue[] = [];

    (config.components ?? []).forEach((comp) => {
      const componentId = comp.id ?? comp.type;

      // Check for missing labels on input components
      if (comp.type === 'TextField' || comp.type === 'Select') {
        const hasLabel = comp.props?.label !== undefined;
        if (!hasLabel) {
          results.push({
            componentId,
            severity: 'error',
            message: 'Input component missing label',
            suggestion: 'Add a label prop for screen reader accessibility',
          });
        }
      }

      // Check for color contrast (placeholder - would need actual color values)
      if (comp.props?.color) {
        results.push({
          componentId,
          severity: 'warning',
          message: 'Custom color detected - verify contrast ratio',
          suggestion: 'Ensure color contrast meets WCAG 2.1 AA standards (4.5:1 for text)',
        });
      }

      // Check for keyboard navigation
      if (comp.type === 'Button' && !comp.props?.['aria-label'] && !comp.props?.children) {
        results.push({
          componentId,
          severity: 'warning',
          message: 'Button may not have accessible text',
          suggestion: 'Add aria-label or children text for screen readers',
        });
      }

      // Check for alt text on images
      if (comp.type === 'Image' && !comp.props?.alt) {
        results.push({
          componentId,
          severity: 'error',
          message: 'Image missing alt text',
          suggestion: 'Add alt prop for screen reader accessibility',
        });
      }

      // Check for heading hierarchy
      const variant = typeof comp.props?.variant === 'string' ? comp.props.variant : undefined;
      if (comp.type === 'Typography' && variant?.startsWith('h')) {
        results.push({
          componentId,
          severity: 'info',
          message: 'Heading component detected',
          suggestion: 'Ensure heading hierarchy follows semantic order (h1 → h2 → h3)',
        });
      }
    });

    return results;
  }, [config.components]);

  const errorCount = issues.filter((i) => i.severity === 'error').length;
  const warningCount = issues.filter((i) => i.severity === 'warning').length;
  const infoCount = issues.filter((i) => i.severity === 'info').length;

  return (
    <Box data-testid="accessibility-checker" className="p-4">
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 3 }}>
        <A11yIcon size={16} />
        <Typography variant="h6">Accessibility Checker</Typography>
      </Stack>

      <Stack direction="row" spacing={2} sx={{ mb: 3 }}>
        <Chip label={`${errorCount} Errors`} color="error" size="small" />
        <Chip label={`${warningCount} Warnings`} color="warning" size="small" />
        <Chip label={`${infoCount} Info`} color="info" size="small" />
      </Stack>

      {issues.length === 0 ? (
        <Alert severity="success" icon={<CheckIcon />}>
          No accessibility issues detected
        </Alert>
      ) : (
        <List>
          {issues.map((issue, idx) => (
            <ListItem key={idx} className="mb-2">
              <ListItemIcon>
                {issue.severity === 'error' && <WarningIcon size={20} className="text-destructive" />}
                {issue.severity === 'warning' && <WarningIcon size={20} className="text-warning-color" />}
                {issue.severity === 'info' && <CheckIcon size={20} className="text-info-color" />}
              </ListItemIcon>
              <ListItemText
                primary={
                  <Stack spacing={0.5}>
                    <Typography variant="subtitle2" className="font-medium">
                      {issue.componentId}
                    </Typography>
                    <Typography variant="body2">{issue.message}</Typography>
                    {issue.suggestion && (
                      <Typography variant="caption" color="text.secondary">
                        Suggestion: {issue.suggestion}
                      </Typography>
                    )}
                  </Stack>
                }
              />
            </ListItem>
          ))}
        </List>
      )}

      <Alert severity="info" className="mt-4">
        <Typography variant="body2">
          This is a basic accessibility checker. For comprehensive testing, integrate axe-core or
          use browser accessibility tools.
        </Typography>
      </Alert>
    </Box>
  );
};
