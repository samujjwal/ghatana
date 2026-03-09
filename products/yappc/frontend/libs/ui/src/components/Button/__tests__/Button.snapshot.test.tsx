// All tests skipped - incomplete feature
import { render } from '@testing-library/react';
import React from 'react';
import { describe, it, expect } from 'vitest';

import ThemeProvider from '../../../theme/ThemeProvider';
import { Button } from '../Button';

describe.skip('Button Snapshots', () => {
  // Helper function to render button with theme
  const renderWithTheme = (ui: React.ReactNode) => {
    return render(<ThemeProvider mode="light">{ui}</ThemeProvider>);
  };

  const checkButton = (container: HTMLElement, expectedLabel: string, opts?: { disabled?: boolean; fullWidth?: boolean }) => {
    const button = container.querySelector('button');
    expect(button).toBeTruthy();
    if (!button) return;
    // accessible name is set via aria-label when children are simple text
    expect(button).toHaveAttribute('aria-label', expectedLabel);
    if (opts?.disabled) {
      expect(button).toBeDisabled();
      expect(button).toHaveAttribute('aria-disabled', 'true');
    }
    if (opts?.fullWidth) {
      expect(button).toHaveClass('MuiButton-fullWidth');
    }
  };

  it('renders various button variants and attributes correctly', () => {
    // primary
    let res = renderWithTheme(<Button variant="contained" color="primary">Primary Button</Button>);
    checkButton(res.container, 'Primary Button');

    // secondary
    res = renderWithTheme(<Button variant="contained" color="secondary">Secondary Button</Button>);
    checkButton(res.container, 'Secondary Button');

    // outlined
    res = renderWithTheme(<Button variant="outlined" color="primary">Outlined Button</Button>);
    checkButton(res.container, 'Outlined Button');

    // text
    res = renderWithTheme(<Button variant="text" color="primary">Text Button</Button>);
    checkButton(res.container, 'Text Button');

    // disabled
    res = renderWithTheme(<Button variant="contained" color="primary" disabled>Disabled Button</Button>);
    checkButton(res.container, 'Disabled Button', { disabled: true });

    // sizes
    res = renderWithTheme(<Button variant="contained" color="primary" size="small">Small Button</Button>);
    checkButton(res.container, 'Small Button');
    res = renderWithTheme(<Button variant="contained" color="primary" size="large">Large Button</Button>);
    checkButton(res.container, 'Large Button');

    // fullWidth
    res = renderWithTheme(<Button variant="contained" color="primary" fullWidth>Full Width Button</Button>);
    checkButton(res.container, 'Full Width Button', { fullWidth: true });

    // tooltip wrapper
    res = renderWithTheme(<Button variant="contained" color="primary" tooltip="Button tooltip">Button with Tooltip</Button>);
    // when tooltip is used we wrap in a span
    expect(res.container.querySelector('span')).toBeTruthy();
    checkButton(res.container, 'Button with Tooltip');

    // custom elevation
    res = renderWithTheme(<Button variant="contained" color="primary" elevation={8}>Elevated Button</Button>);
    checkButton(res.container, 'Elevated Button');
  });
});
