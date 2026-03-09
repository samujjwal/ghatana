import * as React from 'react';
import { Switch as GlobalSwitch } from '@ghatana/ui';

import type { SwitchProps as GlobalSwitchProps } from '@ghatana/yappc-ui';

export type { GlobalSwitchProps as SwitchProps };

type LegacyColor = 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'grey';

type LegacyProps = Omit<GlobalSwitchProps, 'tone'> & { colorScheme?: LegacyColor };

const toneMap: Record<LegacyColor, GlobalSwitchProps['tone']> = {
  primary: 'primary',
  secondary: 'secondary',
  success: 'success',
  error: 'danger',
  warning: 'warning',
  grey: 'neutral',
};

export const Switch = React.forwardRef<HTMLButtonElement, LegacyProps>((props, ref) => {
  const { colorScheme = 'primary', ...rest } = props;

  return React.createElement(GlobalSwitch, {
    ref,
    tone: toneMap[colorScheme] ?? 'primary',
    ...rest,
  });
});

Switch.displayName = 'Switch';

export { Switch as SwitchTailwind };
export type { LegacyProps as SwitchTailwindProps };
