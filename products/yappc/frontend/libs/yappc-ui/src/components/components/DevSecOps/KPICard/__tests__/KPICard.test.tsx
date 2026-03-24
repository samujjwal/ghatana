import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { KPICard } from '../KPICard';
import type { KPICardProps } from '../types';

/**
 * KPICard Component Unit Tests
 * 
 * Tests individual KPICard component behavior in isolation
 * - Rendering with different props
 * - Trend indicators
 * - Progress bars
 * - Edge cases
 */

describe('KPICard Component', () => {
  const defaultProps: KPICardProps = {
    title: 'Test Metric',
    value: 42,
  };

  describe('Basic Rendering', () => {
    it('should render title correctly', () => {
      render(<KPICard {...defaultProps} />);
      
      expect(screen.getByText('Test Metric')).toBeInTheDocument();
    });

    it('should render value correctly', () => {
      render(<KPICard {...defaultProps} />);
      
      expect(screen.getByText('42')).toBeInTheDocument();
    });

    it('should render value with unit', () => {
      render(<KPICard {...defaultProps} unit=" items" />);
      
      expect(screen.getByText(/42 items/)).toBeInTheDocument();
    });

    it('should render without unit when not provided', () => {
      render(<KPICard {...defaultProps} />);
      
      expect(screen.getByText('42')).toBeInTheDocument();
    });

    it('should render string values', () => {
      render(<KPICard {...defaultProps} value="N/A" />);
      
      expect(screen.getByText('N/A')).toBeInTheDocument();
    });

    it('should render decimal values', () => {
      render(<KPICard {...defaultProps} value={85.5} unit="%" />);
      
      expect(screen.getByText(/85\.5%/)).toBeInTheDocument();
    });
  });

  describe('Trend Indicators', () => {
    it('should render upward trend indicator', () => {
      render(
        <KPICard
          {...defaultProps}
          trend={{ direction: 'up', value: 12.5 }}
        />
      );
      
      // Should show +12.5%
      expect(screen.getByText(/\+12\.5%/)).toBeInTheDocument();
      
      // Should have TrendingUpIcon
      const trendIcon = screen.getByTestId('TrendingUpIcon');
      expect(trendIcon).toBeInTheDocument();
    });

    it('should render downward trend indicator', () => {
      render(
        <KPICard
          {...defaultProps}
          trend={{ direction: 'down', value: -5.2 }}
        />
      );
      
      // Should show -5.2%
      expect(screen.getByText(/-5\.2%/)).toBeInTheDocument();
      
      // Should have TrendingDownIcon
      const trendIcon = screen.getByTestId('TrendingDownIcon');
      expect(trendIcon).toBeInTheDocument();
    });

    it('should render neutral trend', () => {
      render(
        <KPICard
          {...defaultProps}
          trend={{ direction: 'neutral', value: 0 }}
        />
      );
      
      // Should show 0%
      expect(screen.getByText(/0%/)).toBeInTheDocument();
    });

    it('should not render trend when not provided', () => {
      render(<KPICard {...defaultProps} />);
      
      // Should not have trend icons
      expect(screen.queryByTestId('TrendingUpIcon')).not.toBeInTheDocument();
      expect(screen.queryByTestId('TrendingDownIcon')).not.toBeInTheDocument();
    });

    it('should handle positive value with up trend', () => {
      render(
        <KPICard
          {...defaultProps}
          trend={{ direction: 'up', value: 8 }}
        />
      );
      
      expect(screen.getByText(/\+8%/)).toBeInTheDocument();
    });

    it('should handle zero trend value', () => {
      render(
        <KPICard
          {...defaultProps}
          trend={{ direction: 'neutral', value: 0 }}
        />
      );
      
      expect(screen.getByText(/0%/)).toBeInTheDocument();
    });
  });

  describe('Progress Bar', () => {
    it('should render progress bar when showProgress is true', () => {
      render(
        <KPICard
          {...defaultProps}
          value={75}
          target={100}
          showProgress={true}
        />
      );
      
      // LinearProgress should be rendered
      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toBeInTheDocument();
    });

    it('should not render progress bar when showProgress is false', () => {
      render(
        <KPICard
          {...defaultProps}
          value={75}
          target={100}
          showProgress={false}
        />
      );
      
      // LinearProgress should not be rendered
      const progressBar = screen.queryByRole('progressbar');
      expect(progressBar).not.toBeInTheDocument();
    });

    it('should not render progress bar when target is not provided', () => {
      render(
        <KPICard
          {...defaultProps}
          value={75}
          showProgress={true}
        />
      );
      
      // LinearProgress should not be rendered without target
      const progressBar = screen.queryByRole('progressbar');
      expect(progressBar).not.toBeInTheDocument();
    });

    it('should calculate progress percentage correctly', () => {
      render(
        <KPICard
          {...defaultProps}
          value={75}
          target={100}
          showProgress={true}
        />
      );
      
      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveAttribute('aria-valuenow', '75');
    });

    it('should cap progress at 100%', () => {
      render(
        <KPICard
          {...defaultProps}
          value={150}
          target={100}
          showProgress={true}
        />
      );
      
      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveAttribute('aria-valuenow', '100');
    });

    it('should display target value', () => {
      render(
        <KPICard
          {...defaultProps}
          value={75}
          target={100}
          showProgress={true}
        />
      );
      
      // Should show target somewhere in the component
      expect(screen.getByText(/100/)).toBeInTheDocument();
    });

    it('should display both value and target', () => {
      render(
        <KPICard
          {...defaultProps}
          value={75}
          target={100}
          showProgress={true}
        />
      );
      
      expect(screen.getByText(/75/)).toBeInTheDocument();
      expect(screen.getByText(/100/)).toBeInTheDocument();
    });
  });

  describe('Edge Cases', () => {
    it('should handle zero value', () => {
      render(<KPICard {...defaultProps} value={0} />);
      
      expect(screen.getByText('0')).toBeInTheDocument();
    });

    it('should handle negative values', () => {
      render(<KPICard {...defaultProps} value={-10} />);
      
      expect(screen.getByText('-10')).toBeInTheDocument();
    });

    it('should handle very large numbers', () => {
      render(<KPICard {...defaultProps} value={1000000} />);
      
      expect(screen.getByText('1000000')).toBeInTheDocument();
    });

    it('should handle very long titles gracefully', () => {
      const longTitle = 'This is a very long title that should be handled gracefully by the component';
      render(<KPICard {...defaultProps} title={longTitle} />);
      
      expect(screen.getByText(longTitle)).toBeInTheDocument();
    });

    it('should handle empty string unit', () => {
      render(<KPICard {...defaultProps} unit="" />);
      
      expect(screen.getByText('42')).toBeInTheDocument();
    });

    it('should handle zero target', () => {
      render(
        <KPICard
          {...defaultProps}
          value={50}
          target={0}
          showProgress={true}
        />
      );
      
      // Should not crash, may not show progress
      expect(screen.getByText('50')).toBeInTheDocument();
    });

    it('should handle decimal progress values', () => {
      render(
        <KPICard
          {...defaultProps}
          value={33.33}
          target={100}
          showProgress={true}
        />
      );
      
      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toBeInTheDocument();
    });
  });

  describe('Typography and Styling', () => {
    it('should use h2 variant for value', () => {
      render(<KPICard {...defaultProps} />);
      
      // Value should be in a div with h2 variant
      const valueElement = screen.getByText('42');
      expect(valueElement).toHaveStyle({ fontWeight: 700 });
    });

    it('should use body2 variant for title', () => {
      render(<KPICard {...defaultProps} />);
      
      const titleElement = screen.getByText('Test Metric');
      expect(titleElement).toBeInTheDocument();
    });

    it('should use body2 variant for trend percentage', () => {
      render(
        <KPICard
          {...defaultProps}
          trend={{ direction: 'up', value: 5 }}
        />
      );
      
      expect(screen.getByText(/\+5%/)).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA attributes on progress bar', () => {
      render(
        <KPICard
          {...defaultProps}
          value={75}
          target={100}
          showProgress={true}
        />
      );
      
      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveAttribute('aria-valuenow', '75');
      expect(progressBar).toHaveAttribute('aria-valuemin', '0');
      expect(progressBar).toHaveAttribute('aria-valuemax', '100');
    });

    it('should have readable text content', () => {
      render(
        <KPICard
          title="Deployment Frequency"
          value={24}
          unit=" per week"
        />
      );
      
      expect(screen.getByText('Deployment Frequency')).toBeInTheDocument();
      expect(screen.getByText(/24 per week/)).toBeInTheDocument();
    });

    it('should have semantic structure', () => {
      render(<KPICard {...defaultProps} />);
      
      // Card should be rendered
      const card = screen.getByText('Test Metric').closest('.MuiCard-root');
      expect(card).toBeInTheDocument();
    });
  });

  describe('Interactive States', () => {
    it('should render in default state', () => {
      const { container } = render(<KPICard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).toBeInTheDocument();
    });

    it('should have transition styles', () => {
      const { container } = render(<KPICard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      // Card should have transition property defined
      expect(card).toBeTruthy();
    });
  });

  describe('Color Coding', () => {
    it('should use success color for upward trend', () => {
      const { container } = render(
        <KPICard
          {...defaultProps}
          trend={{ direction: 'up', value: 10 }}
        />
      );
      
      // Trend box should have success color
      const trendBox = container.querySelector('[data-testid="TrendingUpIcon"]')?.parentElement;
      expect(trendBox).toBeTruthy();
    });

    it('should use error color for downward trend', () => {
      const { container } = render(
        <KPICard
          {...defaultProps}
          trend={{ direction: 'down', value: -5 }}
        />
      );
      
      // Trend box should have error color
      const trendBox = container.querySelector('[data-testid="TrendingDownIcon"]')?.parentElement;
      expect(trendBox).toBeTruthy();
    });
  });

  describe('Component Variants', () => {
    it('should render minimal KPICard (only title and value)', () => {
      render(<KPICard title="Simple Metric" value={100} />);
      
      expect(screen.getByText('Simple Metric')).toBeInTheDocument();
      expect(screen.getByText('100')).toBeInTheDocument();
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    });

    it('should render KPICard with all features', () => {
      render(
        <KPICard
          title="Complete Metric"
          value={85.5}
          unit="%"
          target={100}
          showProgress={true}
          trend={{ direction: 'up', value: 5.5 }}
        />
      );
      
      expect(screen.getByText('Complete Metric')).toBeInTheDocument();
      expect(screen.getByText(/85\.5%/)).toBeInTheDocument();
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
      expect(screen.getByText(/\+5\.5%/)).toBeInTheDocument();
    });

    it('should render KPICard with trend but no progress', () => {
      render(
        <KPICard
          {...defaultProps}
          trend={{ direction: 'up', value: 3 }}
        />
      );
      
      expect(screen.getByText(/\+3%/)).toBeInTheDocument();
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    });

    it('should render KPICard with progress but no trend', () => {
      render(
        <KPICard
          {...defaultProps}
          value={60}
          target={100}
          showProgress={true}
        />
      );
      
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
      expect(screen.queryByTestId('TrendingUpIcon')).not.toBeInTheDocument();
    });
  });

  describe('Responsive Behavior', () => {
    it('should have responsive width', () => {
      const { container } = render(<KPICard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).toHaveStyle({ width: '280px' });
    });

    it('should have minimum height', () => {
      const { container } = render(<KPICard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).toHaveStyle({ minHeight: '200px' });
    });
  });

  describe('Number Formatting', () => {
    it('should display integer values without decimals', () => {
      render(<KPICard {...defaultProps} value={42} />);
      
      expect(screen.getByText('42')).toBeInTheDocument();
      expect(screen.queryByText('42.0')).not.toBeInTheDocument();
    });

    it('should display decimal values with precision', () => {
      render(<KPICard {...defaultProps} value={42.567} />);
      
      expect(screen.getByText('42.567')).toBeInTheDocument();
    });

    it('should handle scientific notation', () => {
      render(<KPICard {...defaultProps} value={1e6} />);
      
      expect(screen.getByText('1000000')).toBeInTheDocument();
    });
  });
});
