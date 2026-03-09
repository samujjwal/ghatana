import { Accessibility as AccessibilityIcon, AlertTriangle as WarningIcon, AlertCircle as ErrorIcon, CheckCircle as CheckIcon } from 'lucide-react';
import {
  Box,
  Typography,
  ListItem,
  ListItemText,
  Chip,
  Button,
  Alert,
  Surface as Paper,
  InteractiveList as List,
} from '@ghatana/ui';
import React, { useState } from 'react';

import type { CanvasTool, CanvasContext } from '../../../state/tools/ToolAPI';

/**
 *
 */
interface AccessibilityIssue {
  id: string;
  severity: 'error' | 'warning' | 'info';
  message: string;
  elementId?: string;
  suggestion?: string;
}

/**
 *
 */
export class AccessibilityTool implements CanvasTool {
  id = 'accessibility';
  name = 'Accessibility Checker';
  description = 'Check canvas for accessibility issues';
  icon = '♿';
  category = 'analysis' as const;

  private issues: AccessibilityIssue[] = [];
  private context: CanvasContext | null = null;

  /**
   *
   */
  initialize(context: CanvasContext): void {
    this.context = context;
  }

  /**
   *
   */
  onActivate(context: CanvasContext): void {
    this.runAccessibilityCheck(context);
  }

  /**
   *
   */
  private runAccessibilityCheck(context: CanvasContext): void {
    const state = context.getCanvasState();
    this.issues = [];

    // Check for missing labels
    state.elements?.forEach((element: unknown) => {
      if (!element.data?.label && element.kind !== 'shape') {
        this.issues.push({
          id: `missing-label-${element.id}`,
          severity: 'warning',
          message: `Element "${element.id}" is missing a label`,
          elementId: element.id,
          suggestion: 'Add a descriptive label for screen readers',
        });
      }

      // Check for low contrast (simplified)
      if (element.style?.color && element.style?.backgroundColor) {
        // Simplified contrast check
        this.issues.push({
          id: `contrast-${element.id}`,
          severity: 'info',
          message: `Check color contrast for element "${element.id}"`,
          elementId: element.id,
          suggestion: 'Ensure text has sufficient contrast ratio (4.5:1 for normal text)',
        });
      }
    });

    // Check for keyboard navigation
    const hasKeyboardShortcuts = state.metadata?.keyboardShortcuts;
    if (!hasKeyboardShortcuts) {
      this.issues.push({
        id: 'keyboard-nav',
        severity: 'warning',
        message: 'No keyboard shortcuts defined',
        suggestion: 'Add keyboard shortcuts for common actions',
      });
    }

    // Check for alt text on images/icons
    state.elements?.forEach((element: unknown) => {
      if (element.data?.icon && !element.data?.iconAlt) {
        this.issues.push({
          id: `missing-alt-${element.id}`,
          severity: 'error',
          message: `Icon in "${element.id}" is missing alt text`,
          elementId: element.id,
          suggestion: 'Add descriptive alt text for the icon',
        });
      }
    });
  }

  /**
   *
   */
  renderPanel(context: CanvasContext): React.ReactNode {
    return <AccessibilityPanel issues={this.issues} context={context} />;
  }
}

/**
 *
 */
interface AccessibilityPanelProps {
  issues: AccessibilityIssue[];
  context: CanvasContext;
}

const AccessibilityPanel: React.FC<AccessibilityPanelProps> = ({ issues, context }) => {
  const [selectedIssue, setSelectedIssue] = useState<string | null>(null);

  const errorCount = issues.filter((i) => i.severity === 'error').length;
  const warningCount = issues.filter((i) => i.severity === 'warning').length;
  const infoCount = issues.filter((i) => i.severity === 'info').length;

  const handleFixIssue = (issue: AccessibilityIssue) => {
    if (issue.elementId) {
      // Select the element
      context.setSelection([issue.elementId]);
      
      // Fit view to element
      context.fitView([issue.elementId]);
    }
  };

  const getSeverityIcon = (severity: string) => {
    switch (severity) {
      case 'error':
        return <ErrorIcon color="error" size={16} />;
      case 'warning':
        return <WarningIcon color="warning" size={16} />;
      default:
        return <CheckIcon color="info" size={16} />;
    }
  };

  return (
    <Paper className="p-4 h-full overflow-auto">
      <Box className="flex items-center mb-4">
        <AccessibilityIcon className="mr-2" />
        <Typography variant="h6">Accessibility Report</Typography>
      </Box>

      <Box className="flex gap-2 mb-4">
        <Chip
          label={`${errorCount} Errors`}
          color="error"
          size="small"
          variant={errorCount > 0 ? 'filled' : 'outlined'}
        />
        <Chip
          label={`${warningCount} Warnings`}
          color="warning"
          size="small"
          variant={warningCount > 0 ? 'filled' : 'outlined'}
        />
        <Chip
          label={`${infoCount} Info`}
          color="info"
          size="small"
          variant={infoCount > 0 ? 'filled' : 'outlined'}
        />
      </Box>

      {issues.length === 0 ? (
        <Alert severity="success" className="mt-4">
          No accessibility issues found! Great job! 🎉
        </Alert>
      ) : (
        <List className="mt-4">
          {issues.map((issue) => (
            <ListItem
              key={issue.id}
              className={`mb-2 rounded border border-gray-200 dark:border-gray-700 flex-col items-start ${selectedIssue === issue.id ? 'bg-gray-100' : 'bg-white dark:bg-gray-900'}`}
              onClick={() => setSelectedIssue(issue.id)}
            >
              <Box className="flex items-start w-full">
                <Box className="mr-2 mt-1">{getSeverityIcon(issue.severity)}</Box>
                <ListItemText
                  primary={issue.message}
                  secondary={issue.suggestion}
                  primaryTypographyProps={{ fontSize: '0.9rem', fontWeight: 500 }}
                  secondaryTypographyProps={{ fontSize: '0.8rem' }}
                />
              </Box>
              {issue.elementId && (
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => handleFixIssue(issue)}
                  className="mt-2"
                >
                  Go to Element
                </Button>
              )}
            </ListItem>
          ))}
        </List>
      )}
    </Paper>
  );
};
