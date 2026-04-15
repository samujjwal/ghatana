import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import { PluginsPage } from '../../pages/PluginsPage';


describe('PluginsPage', () => {
  it('renders the plugin-management shell with stats, tabs, and search controls', () => {
    render(<PluginsPage />, { wrapper: TestWrapper });

    expect(screen.getByRole('heading', { name: 'Plugins' })).toBeInTheDocument();
    expect(screen.getByText(/Monitor the bundled plugins shipped with the current launcher build/i)).toBeInTheDocument();
    expect(screen.getByText('Installed')).toBeInTheDocument();
    expect(screen.getByText('Catalog Boundary')).toBeInTheDocument();
    expect(screen.getByText('Deployment')).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Search plugins/i)).toBeInTheDocument();
  });
});
