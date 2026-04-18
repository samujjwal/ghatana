import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestWrapper } from '../test-utils/wrapper';
import {
  PLUGINS_CATALOG_BOUNDARY_DETAIL,
  PLUGINS_INVENTORY_HEADER_DETAIL,
} from '@/lib/runtime-boundaries';
import { PluginsPage } from '../../pages/PluginsPage';


describe('PluginsPage', () => {
  it('renders the plugin-management shell with stats, tabs, and search controls', async () => {
    const user = userEvent.setup();

    render(<PluginsPage />, { wrapper: TestWrapper });

    expect(screen.getByRole('heading', { name: 'Plugins' })).toBeInTheDocument();
  expect(screen.getByText(PLUGINS_INVENTORY_HEADER_DETAIL)).toBeInTheDocument();
    expect(screen.getByText('Installed')).toBeInTheDocument();
    expect(screen.getByText('Catalog Boundary')).toBeInTheDocument();
    expect(screen.getByText('Deployment')).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Search plugins/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Catalog Boundary/i }));

    expect(screen.getByText(PLUGINS_CATALOG_BOUNDARY_DETAIL)).toBeInTheDocument();
  });
});
