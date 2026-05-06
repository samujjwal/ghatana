/**
 * Phase icon resolver.
 *
 * Converts a typed {@link PhaseIconId} to a lucide-react icon component.
 * All icons rendered via this resolver are accessible (no raw emoji strings).
 *
 * @doc.type utility
 * @doc.purpose Resolves PhaseIconId to a themed lucide-react icon element
 * @doc.layer product
 * @doc.pattern Utility
 */
import React from 'react';
import {
  ArrowUpRight,
  CheckCircle,
  Code2,
  Eye,
  Layers,
  Lightbulb,
  PlayCircle,
  Target,
} from 'lucide-react';

import type { PhaseIconId } from './types';

const ICON_SIZE = 24;
const ICON_STROKE = 1.75;

/**
 * Returns a properly sized lucide-react icon component for the given phase icon ID.
 * The icon inherits `currentColor` for theme compatibility.
 */
export function resolvePhaseIcon(iconId: PhaseIconId): React.ReactElement {
  const props = {
    size: ICON_SIZE,
    strokeWidth: ICON_STROKE,
    'aria-hidden': true as const,
  };

  switch (iconId) {
    case 'target':
      return <Target {...props} />;
    case 'layers':
      return <Layers {...props} />;
    case 'check-circle':
      return <CheckCircle {...props} />;
    case 'code-2':
      return <Code2 {...props} />;
    case 'play-circle':
      return <PlayCircle {...props} />;
    case 'eye':
      return <Eye {...props} />;
    case 'lightbulb':
      return <Lightbulb {...props} />;
    case 'arrow-up-right':
      return <ArrowUpRight {...props} />;
  }
}
