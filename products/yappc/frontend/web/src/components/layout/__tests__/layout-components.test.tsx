/**
 * Tests for design-system components: Badge, Icon
 * and layout components: Card, PageHeader
 */
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, it, expect } from 'vitest';
import { Badge } from '../../design-system/Badge';
import { Card } from '../../layout/Card';
import { PageHeader } from '../../layout/PageHeader';

// ─── Badge ────────────────────────────────────────────────────────────────────

describe('Badge', () => {
  it('renders label text', () => {
    render(<Badge label="Active" />);
    expect(screen.getByText('Active')).toBeTruthy();
  });

  it('renders success variant', () => {
    render(<Badge label="OK" variant="success" />);
    expect(screen.getByText('OK')).toBeTruthy();
  });

  it('renders warning variant', () => {
    render(<Badge label="Warn" variant="warning" />);
    expect(screen.getByText('Warn')).toBeTruthy();
  });

  it('renders error variant', () => {
    render(<Badge label="Error" variant="error" />);
    expect(screen.getByText('Error')).toBeTruthy();
  });

  it('renders info variant', () => {
    render(<Badge label="Info" variant="info" />);
    expect(screen.getByText('Info')).toBeTruthy();
  });

  it('renders primary variant', () => {
    render(<Badge label="Primary" variant="primary" />);
    expect(screen.getByText('Primary')).toBeTruthy();
  });

  it('renders sm size', () => {
    render(<Badge label="Small" size="sm" />);
    expect(screen.getByText('Small')).toBeTruthy();
  });

  it('renders icon when provided', () => {
    render(<Badge label="With Icon" icon={<span data-testid="icon">★</span>} />);
    expect(screen.getByTestId('icon')).toBeTruthy();
  });

  it('renders dot variant', () => {
    const { container } = render(<Badge label="Draft" dot />);
    // dot is a small div rendered before the label
    expect(screen.getByText('Draft')).toBeTruthy();
    expect(container.querySelector('span')).toBeTruthy();
  });

  it('applies custom className', () => {
    const { container } = render(
      <Badge label="Custom" className="my-badge" />
    );
    const span = container.querySelector('.my-badge');
    expect(span).toBeTruthy();
  });
});

// ─── layout/Card ─────────────────────────────────────────────────────────────

describe('layout/Card', () => {
  it('renders children', () => {
    render(<Card>Card body</Card>);
    expect(screen.getByText('Card body')).toBeTruthy();
  });

  it('renders with sm padding', () => {
    render(<Card padding="sm">Content</Card>);
    expect(screen.getByText('Content')).toBeTruthy();
  });

  it('renders with none padding', () => {
    render(<Card padding="none">No Pad</Card>);
    expect(screen.getByText('No Pad')).toBeTruthy();
  });

  it('renders with lg padding', () => {
    render(<Card padding="lg">Large</Card>);
    expect(screen.getByText('Large')).toBeTruthy();
  });

  it('applies custom className', () => {
    const { container } = render(
      <Card className="custom-card">Body</Card>
    );
    const div = container.querySelector('.custom-card');
    expect(div).toBeTruthy();
  });

  it('renders hoverable card', () => {
    render(<Card hoverable>Hoverable</Card>);
    expect(screen.getByText('Hoverable')).toBeTruthy();
  });
});

// ─── PageHeader ───────────────────────────────────────────────────────────────

describe('PageHeader', () => {
  it('renders page title', () => {
    render(
      <MemoryRouter>
        <PageHeader title="Dashboard" />
      </MemoryRouter>
    );
    expect(screen.getByText('Dashboard')).toBeTruthy();
  });

  it('renders description when provided', () => {
    render(
      <MemoryRouter>
        <PageHeader title="Settings" description="Manage your settings" />
      </MemoryRouter>
    );
    expect(screen.getByText('Manage your settings')).toBeTruthy();
  });

  it('renders back link when backTo provided', () => {
    render(
      <MemoryRouter>
        <PageHeader
          title="Details"
          backTo={{ href: '/list', label: 'Back to List' }}
        />
      </MemoryRouter>
    );
    expect(screen.getByText('Back to List')).toBeTruthy();
  });

  it('renders actions when provided', () => {
    render(
      <MemoryRouter>
        <PageHeader
          title="Projects"
          actions={<button>New Project</button>}
        />
      </MemoryRouter>
    );
    expect(screen.getByText('New Project')).toBeTruthy();
  });

  it('renders without optional props', () => {
    render(
      <MemoryRouter>
        <PageHeader title="Simple Page" />
      </MemoryRouter>
    );
    expect(screen.getByText('Simple Page')).toBeTruthy();
  });
});
