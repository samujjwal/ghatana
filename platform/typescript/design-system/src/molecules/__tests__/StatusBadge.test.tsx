/**
 * StatusBadge Component Tests
 *
 * @doc.type test
 * @doc.purpose Test StatusBadge component with various status mappings
 * @doc.layer design-system
 */
import React from 'react';
import { render, screen } from '@testing-library/react';
import { StatusBadge } from '../StatusBadge';

describe('StatusBadge', () => {
  describe('with default status mappings', () => {
    it('renders success status correctly', () => {
      render(<StatusBadge status="active" />);
      expect(screen.getByText('Active')).toBeInTheDocument();
    });

    it('renders warning status correctly', () => {
      render(<StatusBadge status="pending" />);
      expect(screen.getByText('Pending')).toBeInTheDocument();
    });

    it('renders error status correctly', () => {
      render(<StatusBadge status="failed" />);
      expect(screen.getByText('Failed')).toBeInTheDocument();
    });

    it('renders neutral status correctly', () => {
      render(<StatusBadge status="draft" />);
      expect(screen.getByText('Draft')).toBeInTheDocument();
    });

    it('handles case-insensitive status matching', () => {
      render(<StatusBadge status="ACTIVE" />);
      expect(screen.getByText('Active')).toBeInTheDocument();
    });

    it('handles status with hyphens', () => {
      render(<StatusBadge status="pending-approval" />);
      expect(screen.getByText('Pending Approval')).toBeInTheDocument();
    });

    it('shows status as fallback when no mapping found', () => {
      render(<StatusBadge status="unknown_status" />);
      expect(screen.getByText('unknown_status')).toBeInTheDocument();
    });
  });

  describe('with custom status mappings', () => {
    it('uses custom variant mapping', () => {
      const customMappings = {
        custom_status: { variant: 'success' as const, tone: 'success' as const },
      };
      render(<StatusBadge status="custom_status" statusMappings={customMappings} />);
      expect(screen.getByText('custom_status')).toBeInTheDocument();
    });

    it('uses custom label mapping', () => {
      const customMappings = {
        custom_status: { variant: 'default' as const, tone: 'neutral' as const, label: 'Custom Label' },
      };
      render(<StatusBadge status="custom_status" statusMappings={customMappings} />);
      expect(screen.getByText('Custom Label')).toBeInTheDocument();
    });

    it('prioritizes custom mappings over defaults', () => {
      const customMappings = {
        active: { variant: 'default' as const, tone: 'neutral' as const, label: 'Not Active' },
      };
      render(<StatusBadge status="active" statusMappings={customMappings} />);
      expect(screen.getByText('Not Active')).toBeInTheDocument();
    });
  });

  describe('with custom label mappings', () => {
    it('uses custom label mapping separately', () => {
      const labelMappings = {
        active: 'Custom Active Label',
      };
      render(<StatusBadge status="active" labelMappings={labelMappings} />);
      expect(screen.getByText('Custom Active Label')).toBeInTheDocument();
    });
  });

  describe('fallback behavior', () => {
    it('uses fallback variant when status not in mappings', () => {
      render(<StatusBadge status="unknown" fallbackVariant="success" />);
      expect(screen.getByText('Unknown')).toBeInTheDocument();
    });

    it('uses fallback tone when status not in mappings', () => {
      render(<StatusBadge status="unknown" fallbackTone="warning" />);
      expect(screen.getByText('Unknown')).toBeInTheDocument();
    });

    it('hides status when showStatusAsFallback is false', () => {
      render(<StatusBadge status="completely_unmapped_status" showStatusAsFallback={false} />);
      expect(screen.queryByText('Completely Unmapped Status')).not.toBeInTheDocument();
    });
  });

  describe('Badge props passthrough', () => {
    it('passes additional props to Badge component', () => {
      render(<StatusBadge status="active" className="custom-class" />);
      const badge = screen.getByText('Active').closest('.gh-badge');
      expect(badge).toHaveClass('custom-class');
    });

    it('passes startIcon prop', () => {
      render(<StatusBadge status="active" startIcon={<span data-testid="icon">★</span>} />);
      expect(screen.getByTestId('icon')).toBeInTheDocument();
    });

    it('passes endIcon prop', () => {
      render(<StatusBadge status="active" endIcon={<span data-testid="icon">★</span>} />);
      expect(screen.getByTestId('icon')).toBeInTheDocument();
    });
  });

  describe('common healthcare status patterns', () => {
    it('renders granted status correctly', () => {
      render(<StatusBadge status="granted" />);
      expect(screen.getByText('Granted')).toBeInTheDocument();
    });

    it('renders revoked status correctly', () => {
      render(<StatusBadge status="revoked" />);
      expect(screen.getByText('Revoked')).toBeInTheDocument();
    });

    it('renders expiring status correctly', () => {
      render(<StatusBadge status="expiring" />);
      expect(screen.getByText('Expiring')).toBeInTheDocument();
    });
  });

  describe('common campaign status patterns', () => {
    it('renders launched status correctly', () => {
      render(<StatusBadge status="launched" />);
      expect(screen.getByText('Launched')).toBeInTheDocument();
    });

    it('render rolled_back status correctly', () => {
      render(<StatusBadge status="rolled_back" />);
      expect(screen.getByText('Rolled Back')).toBeInTheDocument();
    });

    it('renders approved status correctly', () => {
      render(<StatusBadge status="approved" />);
      expect(screen.getByText('Approved')).toBeInTheDocument();
    });
  });
});
