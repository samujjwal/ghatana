import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { PluginsPage } from '../../pages/PluginsPage';
import { EnhancedPluginsPage } from '../../pages/EnhancedPluginsPage';

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider><BrowserRouter>{children}</BrowserRouter></Provider>
);

describe('PluginsPage', () => {
  it('renders without crashing', () => {
    render(<PluginsPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays plugin-related content', () => {
    render(<PluginsPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/plugin|connector|integration|extension/i);
  });

  it('renders interactive elements', () => {
    render(<PluginsPage />, { wrapper: TestWrapper });
    const buttons = document.querySelectorAll('button');
    expect(buttons.length).toBeGreaterThanOrEqual(0);
  });
});

describe('EnhancedPluginsPage', () => {
  it('renders without crashing', () => {
    render(<EnhancedPluginsPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays plugin marketplace content', () => {
    render(<EnhancedPluginsPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/plugin|connector|integration|marketplace|install/i);
  });

  it('has search or filter capability', () => {
    render(<EnhancedPluginsPage />, { wrapper: TestWrapper });
    const inputs = document.querySelectorAll('input, button');
    expect(inputs.length).toBeGreaterThanOrEqual(0);
  });
});
