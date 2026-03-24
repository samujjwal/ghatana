/* eslint-disable import/order */
import React from 'react';
import { render as rtlRender, type RenderResult } from '@testing-library/react';

/**
 * Render helper that wraps components in MUI ThemeProvider for tests.
 */
export function renderWithTheme(ui: React.ReactElement): RenderResult {
    const theme = createTheme();
    return rtlRender(<ThemeProvider theme={theme}>{ui}</ThemeProvider>);
}

// Backwards-compatible named export expected by tests
export const render = renderWithTheme;

export default renderWithTheme;
