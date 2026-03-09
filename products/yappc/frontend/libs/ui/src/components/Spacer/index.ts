import * as React from 'react';
import { Spacer as GlobalSpacer } from '@ghatana/yappc-ui';

import type { SpacerProps as GlobalSpacerProps } from '@ghatana/yappc-ui';

export type { GlobalSpacerProps as SpacerProps };

export const Spacer = React.forwardRef<HTMLDivElement, GlobalSpacerProps>((props, ref) => (
  React.createElement(GlobalSpacer, { ref, ...props })
));

Spacer.displayName = 'Spacer';

export { Spacer as SpacerTailwind };
export type { GlobalSpacerProps as SpacerTailwindProps };
