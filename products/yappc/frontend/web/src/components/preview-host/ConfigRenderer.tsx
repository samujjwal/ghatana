/**
 * Config Renderer
 *
 * Renders PageConfig to live UI using ComponentRenderer.
 *
 * @packageDocumentation
 */

import { Box, Stack, Typography, Alert } from '@ghatana/design-system';
import React, { useMemo } from 'react';

import type { PageConfig } from 'yappc-config-schema';

// Placeholder for ComponentRenderer - in production this would import from yappc-ui
// For now, we'll create a simple renderer that displays the component structure

/**
 * @doc.type component
 * @doc.purpose Render PageConfig to live UI
 * @doc.layer product
 * @doc.pattern Renderer
 */
interface ConfigRendererProps {
  config: PageConfig;
  mockData?: Record<string, unknown>;
}

export const ConfigRenderer: React.FC<ConfigRendererProps> = ({ config, mockData = {} }) => {
  const renderedComponents = useMemo(() => {
    return (config.components ?? []).map((comp) => renderComponent(comp, mockData));
  }, [config.components, mockData]);

  return (
    <Box data-testid="config-renderer" className="p-4">
      <Alert severity="info" className="mb-4">
        <Typography variant="body2">
          This is a placeholder renderer. In production, this would use ComponentRenderer from
          @ghatana/yappc-ui to render the actual components.
        </Typography>
      </Alert>

      <Stack spacing={2}>
        {renderedComponents}
      </Stack>
    </Box>
  );
};

function renderComponent(component: PageConfig['components'][number], mockData: Record<string, unknown>): React.ReactNode {
  const { type, props, children } = component;

  const style = {
    border: '1px solid #e0e0e0',
    backgroundColor: '#f5f5f5',
    padding: '8px',
    borderRadius: '4px',
  };

  const childElements = children
    ? (Array.isArray(children) ? children : [children]).map((child, index) => {
        if (typeof child === 'string') {
          return (
            <Typography key={`${component.id}-text-${index}`} variant="body2">
              {child}
            </Typography>
          );
        }
        return renderComponent(child as unknown as PageConfig['components'][number], mockData);
      })
    : null;

  return (
    <Box key={component.id} style={style} data-testid={`component-${component.id}`}>
      <Typography variant="caption" fontWeight="bold" gutterBottom>
        {type}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        ID: {component.id}
      </Typography>
      {props && (
        <Typography variant="caption" color="text.secondary">
          Props: {JSON.stringify(props, null, 2)}
        </Typography>
      )}
      {childElements}
    </Box>
  );
}
