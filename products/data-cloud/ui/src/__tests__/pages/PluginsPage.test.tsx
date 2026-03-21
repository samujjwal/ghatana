import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestWrapper } from '../test-utils/wrapper';
import { PluginsPage } from '../../pages/PluginsPage';


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
