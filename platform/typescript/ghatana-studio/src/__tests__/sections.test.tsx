/**
 * @ghatana/ghatana-studio sections test suite
 * Tests for Ghatana Studio section components
 *
 * @test.type integration-browser
 * @test.execution 1-10s
 * @test.infra jsdom
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import BuilderStudio from '../sections/BuilderStudio';
import ThemeStudio from '../sections/ThemeStudio';
import ComponentPlayground from '../sections/ComponentPlayground';
import CanvasDiagnostics from '../sections/CanvasDiagnostics';
import AIReviewConsole from '../sections/AIReviewConsole';
import ImportMigrationLab from '../sections/ImportMigrationLab';
import PreviewLab from '../sections/PreviewLab';

describe('@ghatana/ghatana-studio - Section Components', () => {
  describe('Builder Studio', () => {
    it('should render Builder Studio section with title and button', () => {
      render(<BuilderStudio />);
      expect(screen.getByText('Builder Studio')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /new document/i })).toBeInTheDocument();
      expect(screen.getByText(/create and edit builderdocument/i)).toBeInTheDocument();
      expect(screen.getByText(/no documents yet/i)).toBeInTheDocument();
    });

    it('should have proper heading hierarchy', () => {
      const { container } = render(<BuilderStudio />);
      const heading = container.querySelector('h2');
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe('Builder Studio');
    });

    it('should display empty state message', () => {
      render(<BuilderStudio />);
      expect(screen.getByText(/create your first builderdocument/i)).toBeInTheDocument();
    });
  });

  describe('Theme Studio', () => {
    it('should render Theme Studio section with title and button', () => {
      render(<ThemeStudio />);
      expect(screen.getByText('Theme Studio')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /create theme/i })).toBeInTheDocument();
      expect(screen.getByText(/materialize and customize design system presets/i)).toBeInTheDocument();
      expect(screen.getByText(/no themes yet/i)).toBeInTheDocument();
    });

    it('should have proper heading hierarchy', () => {
      const { container } = render(<ThemeStudio />);
      const heading = container.querySelector('h2');
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe('Theme Studio');
    });

    it('should display empty state message', () => {
      render(<ThemeStudio />);
      expect(screen.getByText(/create your first design system theme/i)).toBeInTheDocument();
    });
  });

  describe('Component Playground', () => {
    it('should render Component Playground section with title and button', () => {
      render(<ComponentPlayground />);
      expect(screen.getByText('Component Playground')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /select component/i })).toBeInTheDocument();
      expect(screen.getByText(/explore and test design system components/i)).toBeInTheDocument();
      expect(screen.getByText(/select a component to explore/i)).toBeInTheDocument();
    });

    it('should have proper heading hierarchy', () => {
      const { container } = render(<ComponentPlayground />);
      const heading = container.querySelector('h2');
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe('Component Playground');
    });

    it('should display empty state message', () => {
      render(<ComponentPlayground />);
      expect(screen.getByText(/select a component to explore its props and variants/i)).toBeInTheDocument();
    });
  });

  describe('Canvas Diagnostics', () => {
    it('should render Canvas Diagnostics section with title and button', () => {
      render(<CanvasDiagnostics />);
      expect(screen.getByText('Canvas Diagnostics')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /refresh diagnostics/i })).toBeInTheDocument();
      expect(screen.getByText(/inspect canvas plugins/i)).toBeInTheDocument();
      expect(screen.getByText(/no canvas diagnostics data available/i)).toBeInTheDocument();
    });

    it('should have proper heading hierarchy', () => {
      const { container } = render(<CanvasDiagnostics />);
      const heading = container.querySelector('h2');
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe('Canvas Diagnostics');
    });

    it('should display empty state message', () => {
      render(<CanvasDiagnostics />);
      expect(screen.getByText(/no canvas diagnostics data available yet/i)).toBeInTheDocument();
    });
  });

  describe('AI Review Console', () => {
    it('should render AI Review Console section with title and button', () => {
      render(<AIReviewConsole />);
      expect(screen.getByText('AI Review Console')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /load events/i })).toBeInTheDocument();
      expect(screen.getByText(/review ai operations/i)).toBeInTheDocument();
      expect(screen.getByText(/no ai events loaded yet/i)).toBeInTheDocument();
    });

    it('should have proper heading hierarchy', () => {
      const { container } = render(<AIReviewConsole />);
      const heading = container.querySelector('h2');
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe('AI Review Console');
    });

    it('should display confidence badge', () => {
      render(<AIReviewConsole />);
      expect(screen.getByText(/sample confidence display/i)).toBeInTheDocument();
      // ConfidenceBadge should render with confidence score
      expect(screen.getByText(/85%/i)).toBeInTheDocument();
    });

    it('should display empty state message', () => {
      render(<AIReviewConsole />);
      expect(screen.getByText(/load telemetry data to begin review/i)).toBeInTheDocument();
    });
  });

  describe('Import/Migration Lab', () => {
    it('should render Import/Migration Lab section with title and button', () => {
      render(<ImportMigrationLab />);
      expect(screen.getByText('Import/Migration Lab')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /import code/i })).toBeInTheDocument();
      expect(screen.getByText(/test code import from json/i)).toBeInTheDocument();
      expect(screen.getByText(/paste or upload code/i)).toBeInTheDocument();
    });

    it('should have proper heading hierarchy', () => {
      const { container } = render(<ImportMigrationLab />);
      const heading = container.querySelector('h2');
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe('Import/Migration Lab');
    });

    it('should display empty state message', () => {
      render(<ImportMigrationLab />);
      expect(screen.getByText(/paste or upload code to test import and reconciliation/i)).toBeInTheDocument();
    });
  });

  describe('Preview Lab', () => {
    it('should render Preview Lab section with title and button', () => {
      render(<PreviewLab />);
      expect(screen.getByText('Preview Lab')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /launch preview/i })).toBeInTheDocument();
      expect(screen.getByText(/test preview sandbox/i)).toBeInTheDocument();
      expect(screen.getByText(/load a builderdocument/i)).toBeInTheDocument();
    });

    it('should have proper heading hierarchy', () => {
      const { container } = render(<PreviewLab />);
      const heading = container.querySelector('h2');
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe('Preview Lab');
    });

    it('should display empty state message', () => {
      render(<PreviewLab />);
      expect(screen.getByText(/load a builderdocument to test preview rendering/i)).toBeInTheDocument();
    });
  });
});
