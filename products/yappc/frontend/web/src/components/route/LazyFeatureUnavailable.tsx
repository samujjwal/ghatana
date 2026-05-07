/**
 * Lazy Feature Unavailable Component
 *
 * Renders an explicit unavailable state for feature-gated lazy surfaces.
 *
 * @doc.type component
 * @doc.purpose Honest fallback UI for incomplete or feature-gated lazy routes
 * @doc.layer product
 * @doc.pattern Component
 */

import { LockKeyhole } from 'lucide-react';
import { Box, Surface, Typography } from '@ghatana/design-system';

export interface LazyFeatureUnavailableProps {
  readonly title: string;
  readonly description: string;
  readonly testId?: string;
}

export function LazyFeatureUnavailable({
  title,
  description,
  testId,
}: LazyFeatureUnavailableProps): React.JSX.Element {
  return (
    <Surface
      elevation={1}
      className="rounded-xl border border-border bg-surface-raised p-4"
      data-testid={testId}
      role="status"
      aria-live="polite"
    >
      <Box className="flex items-start gap-3">
        <Box className="rounded-full bg-warning-bg p-2 text-warning-color">
          <LockKeyhole size={18} aria-hidden="true" />
        </Box>
        <Box>
          <Typography variant="body2" className="font-semibold text-fg">
            {title}
          </Typography>
          <Typography variant="caption" className="mt-1 block text-fg-muted">
            {description}
          </Typography>
        </Box>
      </Box>
    </Surface>
  );
}

export default LazyFeatureUnavailable;
