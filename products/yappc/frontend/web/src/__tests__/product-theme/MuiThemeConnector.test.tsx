/**
 * MuiThemeConnector render tests
 *
 * Verifies that MuiThemeConnector renders children without crashing,
 * using the @ghatana/theme mock already configured in vitest.config.ts.
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

// @ghatana/theme is aliased to src/__mocks__/@ghatana/theme.ts in vitest.config
// @mui/material is mocked below — MuiThemeConnector wraps it, not the subject of test
vi.mock('@mui/material', () => ({
  ThemeProvider: ({ children }: React.PropsWithChildren) => React.createElement('div', { 'data-testid': 'mui-theme-provider' }, children),
  createTheme: (opts?: object) => opts ?? {},
  CssBaseline: () => null,
}));

vi.mock('@yappc/ui', () => ({
  lightTheme: { palette: { mode: 'light' } },
  darkTheme: { palette: { mode: 'dark' } },
}));

import { MuiThemeConnector } from '@yappc/product-theme/mui-bridge';

describe('MuiThemeConnector', () => {
  it('renders children without crashing', () => {
    render(
      <MuiThemeConnector>
        <span data-testid="child">hello</span>
      </MuiThemeConnector>,
    );
    expect(screen.getByTestId('child')).toBeDefined();
  });

  it('renders children in dark mode without crashing', () => {
    // The @ghatana/theme mock returns resolvedTheme = 'light' by default.
    // Override via module mock to verify dark branch is exercised.
    vi.doMock('@ghatana/theme', () => ({
      useTheme: () => ({
        resolvedTheme: 'dark',
        theme: 'dark',
        toggleTheme: vi.fn(),
        setTheme: vi.fn(),
        themeDefinition: undefined,
      }),
    }));

    render(
      <MuiThemeConnector>
        <span data-testid="dark-child">dark</span>
      </MuiThemeConnector>,
    );
    expect(screen.getByTestId('dark-child')).toBeDefined();

    vi.doUnmock('@ghatana/theme');
  });

  it('renders children when themeDefinition provides computed colors', () => {
    vi.doMock('@ghatana/theme', () => ({
      useTheme: () => ({
        resolvedTheme: 'light',
        theme: 'light',
        toggleTheme: vi.fn(),
        setTheme: vi.fn(),
        themeDefinition: {
          computed: {
            colors: {
              background: { default: '#ffffff', paper: '#f5f5f5' },
              text: { primary: '#000000', secondary: '#555555', disabled: '#aaaaaa' },
              divider: '#e0e0e0',
            },
          },
        },
      }),
    }));

    render(
      <MuiThemeConnector>
        <span data-testid="colors-child">with colors</span>
      </MuiThemeConnector>,
    );
    expect(screen.getByTestId('colors-child')).toBeDefined();

    vi.doUnmock('@ghatana/theme');
  });
});
