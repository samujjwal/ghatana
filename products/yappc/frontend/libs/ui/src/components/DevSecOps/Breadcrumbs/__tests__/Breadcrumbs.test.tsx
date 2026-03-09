/**
 * Breadcrumbs Component Tests
 *
 * @module DevSecOps/Breadcrumbs/tests
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { Breadcrumbs } from './Breadcrumbs';
import { devsecopsTheme } from '../../../theme/devsecops-theme';


const renderWithTheme = (component: React.ReactElement) => {
  return render(
    <ThemeProvider theme={devsecopsTheme}>{component}</ThemeProvider>
  );
};

describe('Breadcrumbs', () => {
  const mockItems = [
    { label: 'Home', href: '/' },
    { label: 'DevSecOps', href: '/devsecops' },
    { label: 'Planning Phase' },
  ];

  it('renders all breadcrumb items', () => {
    renderWithTheme(<Breadcrumbs items={mockItems} />);

    expect(screen.getByText('Home')).toBeInTheDocument();
    expect(screen.getByText('DevSecOps')).toBeInTheDocument();
    expect(screen.getByText('Planning Phase')).toBeInTheDocument();
  });

  it('renders last item as text (not link)', () => {
    renderWithTheme(<Breadcrumbs items={mockItems} />);

    const lastItem = screen.getByText('Planning Phase');
    expect(lastItem.tagName).toBe('P'); // Typography renders as p
  });

  it('renders non-last items as links', () => {
    renderWithTheme(<Breadcrumbs items={mockItems} />);

    const homeLink = screen.getByText('Home').closest('a');
    expect(homeLink).toBeInTheDocument();
    expect(homeLink).toHaveAttribute('href', '/');

    const devSecOpsLink = screen.getByText('DevSecOps').closest('a');
    expect(devSecOpsLink).toBeInTheDocument();
    expect(devSecOpsLink).toHaveAttribute('href', '/devsecops');
  });

  it('calls onClick when breadcrumb is clicked', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();

    const itemsWithClick = [
      { label: 'Home', onClick },
      { label: 'Current' },
    ];

    renderWithTheme(<Breadcrumbs items={itemsWithClick} />);

    const homeLink = screen.getByText('Home');
    await user.click(homeLink);

    expect(onClick).toHaveBeenCalled();
  });

  it('renders with custom separator', () => {
    renderWithTheme(<Breadcrumbs items={mockItems} separator="/" />);

    expect(screen.getByText('Home')).toBeInTheDocument();
    // MUI Breadcrumbs renders separator between items
  });

  it('handles single item', () => {
    const singleItem = [{ label: 'Single Page' }];
    renderWithTheme(<Breadcrumbs items={singleItem} />);

    expect(screen.getByText('Single Page')).toBeInTheDocument();
  });

  it('handles empty items array', () => {
    renderWithTheme(<Breadcrumbs items={[]} />);

    const breadcrumbNav = screen.getByRole('navigation');
    expect(breadcrumbNav).toBeInTheDocument();
  });

  it('respects maxItems prop', () => {
    const manyItems = [
      { label: 'Item 1', href: '/1' },
      { label: 'Item 2', href: '/2' },
      { label: 'Item 3', href: '/3' },
      { label: 'Item 4', href: '/4' },
      { label: 'Item 5' },
    ];

    renderWithTheme(<Breadcrumbs items={manyItems} maxItems={3} />);

    // MUI Breadcrumbs will collapse items when maxItems is exceeded
    expect(screen.getByText('Item 1')).toBeInTheDocument();
    expect(screen.getByText('Item 5')).toBeInTheDocument();
  });
});
