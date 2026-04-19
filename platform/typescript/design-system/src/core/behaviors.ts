import type * as React from 'react';

import type { ComponentBehavior } from './composition';

export interface PressableBehaviorOptions {
  disabled?: boolean;
  role?: React.AriaRole;
  tabIndex?: number;
}

export function createPressableBehavior(options: PressableBehaviorOptions = {}): ComponentBehavior {
  const { disabled = false, role = 'button', tabIndex = 0 } = options;

  return {
    name: 'pressable',
    state: {
      disabled,
      interactive: !disabled,
    },
    features: ['pressable', disabled ? undefined : 'keyboard-accessible'],
    rootProps: ({ telemetry }) => ({
      role,
      tabIndex: disabled ? -1 : tabIndex,
      'aria-disabled': disabled || undefined,
      onKeyDown: (event) => {
        const keyboardEvent = event as React.KeyboardEvent<HTMLElement>;
        if (disabled) return;
        if (keyboardEvent.key !== 'Enter' && keyboardEvent.key !== ' ') return;

        keyboardEvent.preventDefault();
        telemetry('press', { source: 'keyboard' });
        keyboardEvent.currentTarget.click();
      },
      onClick: () => {
        if (!disabled) {
          telemetry('press', { source: 'pointer' });
        }
      },
    }),
  };
}
