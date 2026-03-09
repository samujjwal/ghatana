import * as React from 'react';
import { Checkbox as GlobalCheckbox } from '@ghatana/ui';

import type { CheckboxProps as GlobalCheckboxProps } from '@ghatana/yappc-ui';

export type { GlobalCheckboxProps as CheckboxProps };

type LegacyColor = 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'grey';

type LegacySize = NonNullable<GlobalCheckboxProps['size']>;

const toneMap: Record<LegacyColor, GlobalCheckboxProps['tone']> = {
  primary: 'primary',
  secondary: 'secondary',
  success: 'success',
  error: 'danger',
  warning: 'warning',
  grey: 'neutral',
};

interface LegacyProps extends Omit<GlobalCheckboxProps, 'tone'> {
  colorScheme?: LegacyColor;
}

export const Checkbox = React.forwardRef<HTMLInputElement, LegacyProps>((props, ref) => {
  const { colorScheme = 'primary', ...rest } = props;

  return React.createElement(GlobalCheckbox, {
    ref,
    tone: toneMap[colorScheme] ?? 'primary',
    ...rest,
  });
});

Checkbox.displayName = 'Checkbox';

export { Checkbox as CheckboxTailwind };
export type { LegacyProps as CheckboxTailwindProps };
