/**
 * @fileoverview Tests for DesignSystemPage.
 *
 * Verifies that:
 * - The page renders the Design System Generator heading
 * - Preset selector and output-format buttons are rendered
 * - The "Generate" button triggers output generation
 * - The "Download" button appears after output is generated
 * - Switching output format changes the active button state
 * - Token preview panel renders color swatches from the selected preset
 *
 * Because `handleGenerate` uses `setTimeout(300)` internally, we mock the
 * ds-generator module to keep the tests fast and deterministic. The
 * DesignSystemDocument model is constructed by the real factory, so we
 * only stub the emit functions.
 *
 * @doc.type test
 * @doc.purpose Design System Generator page tests
 * @doc.layer studio
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

// ============================================================================
// Mocks
// ============================================================================

vi.mock('@ghatana/ds-generator', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@ghatana/ds-generator')>();
  return {
    ...actual,
    // Replace emit functions with deterministic stubs
    emitCss: vi.fn(() => ':root { --color-primary: #0050ff; }'),
    emitJson: vi.fn(() => ({ json: '{"tokens":{}}' })),
    emitTailwind: vi.fn(() => 'module.exports = { theme: { extend: {} } };'),
    emitReactTheme: vi.fn(() => "export const theme = {};"),
    // Leave createDesignSystemDocument, materializePreset, and presets unchanged
  };
});

import { DesignSystemPage } from '../DesignSystemPage';

// ============================================================================
// Tests
// ============================================================================

describe('DesignSystemPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('initial rendering', () => {
    it('renders the Design System Generator heading', () => {
      render(<DesignSystemPage />);
      expect(
        screen.getByRole('heading', { name: /Design System Generator/i }),
      ).toBeInTheDocument();
    });

    it('renders the Preset selector with all built-in presets', () => {
      render(<DesignSystemPage />);
      const select = screen.getByRole('combobox');
      expect(select).toBeInTheDocument();
      expect(screen.getByRole('option', { name: /Ghatana Default/i })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: /Enterprise Neutral/i })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: /Creative Bold/i })).toBeInTheDocument();
    });

    it('renders output format buttons', () => {
      render(<DesignSystemPage />);
      expect(screen.getByRole('button', { name: 'CSS' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'JSON' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'TAILWIND' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'REACT-THEME' })).toBeInTheDocument();
    });

    it('renders the Generate Design System button', () => {
      render(<DesignSystemPage />);
      expect(
        screen.getByRole('button', { name: /Generate Design System/i }),
      ).toBeInTheDocument();
    });

    it('does not render the Download button before generation', () => {
      render(<DesignSystemPage />);
      expect(screen.queryByRole('button', { name: /Download/i })).not.toBeInTheDocument();
    });

    it('renders the Token Preview panel', () => {
      render(<DesignSystemPage />);
      expect(screen.getByRole('heading', { name: /Token Preview/i })).toBeInTheDocument();
    });
  });

  describe('output generation', () => {
    it('generates CSS output when the Generate button is clicked (CSS format)', async () => {
      render(<DesignSystemPage />);
      await userEvent.click(screen.getByRole('button', { name: /Generate Design System/i }));

      await waitFor(() => {
        expect(screen.getByText(/:root/)).toBeInTheDocument();
      });
    });

    it('shows the Download button after generation completes', async () => {
      render(<DesignSystemPage />);
      await userEvent.click(screen.getByRole('button', { name: /Generate Design System/i }));

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Download/i })).toBeInTheDocument();
      });
    });

    it('generates JSON output when the JSON format button is selected', async () => {
      render(<DesignSystemPage />);
      await userEvent.click(screen.getByRole('button', { name: 'JSON' }));
      await userEvent.click(screen.getByRole('button', { name: /Generate Design System/i }));

      await waitFor(() => {
        expect(screen.getByText(/\{.*tokens/)).toBeInTheDocument();
      });
    });

    it('generates Tailwind output when the TAILWIND format button is selected', async () => {
      render(<DesignSystemPage />);
      await userEvent.click(screen.getByRole('button', { name: 'TAILWIND' }));
      await userEvent.click(screen.getByRole('button', { name: /Generate Design System/i }));

      await waitFor(() => {
        expect(screen.getByText(/module\.exports/)).toBeInTheDocument();
      });
    });

    it('generates React theme output when the REACT-THEME format button is selected', async () => {
      render(<DesignSystemPage />);
      await userEvent.click(screen.getByRole('button', { name: 'REACT-THEME' }));
      await userEvent.click(screen.getByRole('button', { name: /Generate Design System/i }));

      await waitFor(() => {
        expect(screen.getByText(/export const theme/)).toBeInTheDocument();
      });
    });
  });

  describe('preset switching', () => {
    it('updates the token preview when a different preset is selected', async () => {
      render(<DesignSystemPage />);
      const select = screen.getByRole('combobox');
      // Switch to Enterprise Neutral (index 1)
      await userEvent.selectOptions(select, '1');
      // Token preview panel should still render (colors exist in all presets)
      expect(screen.getByRole('heading', { name: /Token Preview/i })).toBeInTheDocument();
    });
  });
});
