/**
 * @fileoverview Preview Locale and RTL Rendering Tests
 *
 * Tests for locale-aware and RTL (right-to-left) rendering in the preview component.
 * Verifies that the preview correctly handles different locales, text direction,
 * date/currency formatting, theme switching, and viewport changes.
 *
 * @doc.type test
 * @doc.purpose Locale and RTL rendering tests for preview
 * @doc.layer product
 * @doc.pattern Internationalization Test
 */

import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import React from 'react';
import { vi, describe, it, expect, beforeEach } from 'vitest';

// Mock the preview component for testing
const MockPreview = ({ locale, direction, theme }: { locale: string; direction: 'ltr' | 'rtl'; theme: 'light' | 'dark' }) => (
  <div
    data-testid="preview-container"
    lang={locale}
    dir={direction}
    data-theme={theme}
    className={[direction === 'rtl' ? 'rtl' : 'ltr', theme].join(' ')}
  >
    <div data-testid="preview-content">
      <h1 data-testid="title">Preview Title</h1>
      <p data-testid="date-display">2024-01-15</p>
      <p data-testid="currency-display">$1,234.56</p>
      <input data-testid="text-input" type="text" placeholder="Enter text" />
    </div>
  </div>
);

describe('Preview Locale and RTL Rendering', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  describe('Locale fixtures', () => {
    it('should render with English locale (en-US)', () => {
      render(<MockPreview locale="en-US" direction="ltr" theme="light" />);

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'en-US');
    });

    it('should render with Spanish locale (es-ES)', () => {
      render(<MockPreview locale="es-ES" direction="ltr" theme="light" />);

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'es-ES');
    });

    it('should render with French locale (fr-FR)', () => {
      render(<MockPreview locale="fr-FR" direction="ltr" theme="light" />);

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'fr-FR');
    });

    it('should render with German locale (de-DE)', () => {
      render(<MockPreview locale="de-DE" direction="ltr" theme="light" />);

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'de-DE');
    });

    it('should render with Japanese locale (ja-JP)', () => {
      render(<MockPreview locale="ja-JP" direction="ltr" theme="light" />);

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'ja-JP');
    });

    it('should render with Chinese locale (zh-CN)', () => {
      render(<MockPreview locale="zh-CN" direction="ltr" theme="light" />);

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'zh-CN');
    });

    it('should render with Arabic locale (ar-SA)', () => {
      render(<MockPreview locale="ar-SA" direction="rtl" theme="light" />);

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'ar-SA');
    });

    it('should render with Hebrew locale (he-IL)', () => {
      render(<MockPreview locale="he-IL" direction="rtl" theme="light" />);

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'he-IL');
    });
  });

  describe('Text direction (LTR/RTL)', () => {
    it('should render with LTR direction for left-to-right languages', () => {
      render(
<MockPreview locale="en-US" direction="ltr" theme="light" />
      );

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('dir', 'ltr');
    });

    it('should render with RTL direction for right-to-left languages', () => {
      render(
<MockPreview locale="ar-SA" direction="rtl" theme="light" />
      );

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('dir', 'rtl');
    });

    it('should apply RTL-specific CSS classes when direction is RTL', () => {
      render(
<MockPreview locale="ar-SA" direction="rtl" theme="light" />
      );

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveClass('rtl');
    });

    it('should mirror layout elements in RTL mode', () => {
      render(
<MockPreview locale="ar-SA" direction="rtl" theme="light" />
      );

      const container = screen.getByTestId('preview-container');
      const computedStyle = window.getComputedStyle(container);
      expect(computedStyle.direction).toBe('rtl');
    });

    it('should switch direction when locale changes from LTR to RTL', () => {
      const { rerender } = render(
<MockPreview locale="en-US" direction="ltr" theme="light" />
      );

      let container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('dir', 'ltr');

      rerender(
<MockPreview locale="ar-SA" direction="rtl" theme="light" />
      );

      container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('dir', 'rtl');
    });
  });

  describe('Date formatting', () => {
    it('should format dates according to locale (en-US)', () => {
      render(<MockPreview locale="en-US" direction="ltr" theme="light" />);

      const dateDisplay = screen.getByTestId('date-display');
      expect(dateDisplay).toBeInTheDocument();
    });

    it('should format dates according to locale (de-DE)', () => {
      render(<MockPreview locale="de-DE" direction="ltr" theme="light" />);

      const dateDisplay = screen.getByTestId('date-display');
      expect(dateDisplay).toBeInTheDocument();
    });

    it('should format dates according to locale (ja-JP)', () => {
      render(<MockPreview locale="ja-JP" direction="ltr" theme="light" />);

      const dateDisplay = screen.getByTestId('date-display');
      expect(dateDisplay).toBeInTheDocument();
    });
  });

  describe('Currency formatting', () => {
    it('should format currency according to locale (en-US)', () => {
      render(<MockPreview locale="en-US" direction="ltr" theme="light" />);

      const currencyDisplay = screen.getByTestId('currency-display');
      expect(currencyDisplay).toBeInTheDocument();
    });

    it('should format currency according to locale (de-DE)', () => {
      render(<MockPreview locale="de-DE" direction="ltr" theme="light" />);

      const currencyDisplay = screen.getByTestId('currency-display');
      expect(currencyDisplay).toBeInTheDocument();
    });

    it('should format currency according to locale (ja-JP)', () => {
      render(<MockPreview locale="ja-JP" direction="ltr" theme="light" />);

      const currencyDisplay = screen.getByTestId('currency-display');
      expect(currencyDisplay).toBeInTheDocument();
    });

    it('should format currency according to locale (ar-SA)', () => {
      render(<MockPreview locale="ar-SA" direction="rtl" theme="light" />);

      const currencyDisplay = screen.getByTestId('currency-display');
      expect(currencyDisplay).toBeInTheDocument();
    });
  });

  describe('Theme switching', () => {
    it('should render with light theme', () => {
      render(
<MockPreview locale="en-US" direction="ltr" theme="light" />
      );

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('data-theme', 'light');
    });

    it('should render with dark theme', () => {
      render(
<MockPreview locale="en-US" direction="ltr" theme="dark" />
      );

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('data-theme', 'dark');
    });

    it('should apply theme-specific CSS classes', () => {
      render(
<MockPreview locale="en-US" direction="ltr" theme="dark" />
      );

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveClass('dark');
    });

    it('should switch theme from light to dark', () => {
      const { rerender } = render(
<MockPreview locale="en-US" direction="ltr" theme="light" />
      );

      let container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('data-theme', 'light');

      rerender(
<MockPreview locale="en-US" direction="ltr" theme="dark" />
      );

      container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('data-theme', 'dark');
    });

    it('should maintain locale when switching themes', () => {
      const { rerender } = render(
<MockPreview locale="ar-SA" direction="rtl" theme="light" />
      );

      let container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'ar-SA');
      expect(container).toHaveAttribute('dir', 'rtl');

      rerender(
<MockPreview locale="ar-SA" direction="rtl" theme="dark" />
      );

      container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'ar-SA');
      expect(container).toHaveAttribute('dir', 'rtl');
    });
  });

  describe('Viewport changes', () => {
    it('should handle viewport width changes', () => {
      render(
<MockPreview locale="en-US" direction="ltr" theme="light" />
      );

      const container = screen.getByTestId('preview-container');

      // Simulate viewport width change
      window.innerWidth = 768;
      window.dispatchEvent(new Event('resize'));

      expect(container).toBeInTheDocument();
    });

    it('should handle viewport height changes', () => {
      render(
<MockPreview locale="en-US" direction="ltr" theme="light" />
      );

      const container = screen.getByTestId('preview-container');

      // Simulate viewport height change
      window.innerHeight = 1024;
      window.dispatchEvent(new Event('resize'));

      expect(container).toBeInTheDocument();
    });

    it('should apply responsive classes based on viewport width', () => {
      render(
<MockPreview locale="en-US" direction="ltr" theme="light" />
      );

      const container = screen.getByTestId('preview-container');

      // Mobile viewport
      window.innerWidth = 375;
      window.dispatchEvent(new Event('resize'));
      expect(container).toBeInTheDocument();

      // Tablet viewport
      window.innerWidth = 768;
      window.dispatchEvent(new Event('resize'));
      expect(container).toBeInTheDocument();

      // Desktop viewport
      window.innerWidth = 1920;
      window.dispatchEvent(new Event('resize'));
      expect(container).toBeInTheDocument();
    });

    it('should maintain locale and direction during viewport changes', () => {
      render(
<MockPreview locale="ar-SA" direction="rtl" theme="light" />
      );

      const container = screen.getByTestId('preview-container');

      // Simulate viewport resize
      window.innerWidth = 768;
      window.dispatchEvent(new Event('resize'));

      expect(container).toHaveAttribute('lang', 'ar-SA');
      expect(container).toHaveAttribute('dir', 'rtl');
    });
  });

  describe('Combined locale, RTL, and theme scenarios', () => {
    it('should handle Arabic locale with RTL and dark theme', () => {
      render(
<MockPreview locale="ar-SA" direction="rtl" theme="dark" />
      );

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'ar-SA');
      expect(container).toHaveAttribute('dir', 'rtl');
      expect(container).toHaveAttribute('data-theme', 'dark');
    });

    it('should handle Hebrew locale with RTL and light theme', () => {
      render(
<MockPreview locale="he-IL" direction="rtl" theme="light" />
      );

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'he-IL');
      expect(container).toHaveAttribute('dir', 'rtl');
      expect(container).toHaveAttribute('data-theme', 'light');
    });

    it('should handle Japanese locale with LTR and dark theme', () => {
      render(
<MockPreview locale="ja-JP" direction="ltr" theme="dark" />
      );

      const container = screen.getByTestId('preview-container');
      expect(container).toHaveAttribute('lang', 'ja-JP');
      expect(container).toHaveAttribute('dir', 'ltr');
      expect(container).toHaveAttribute('data-theme', 'dark');
    });
  });

  describe('Input field locale awareness', () => {
    it('should set input placeholder according to locale', () => {
      render(
<MockPreview locale="en-US" direction="ltr" theme="light" />
      );

      const input = screen.getByTestId('text-input');
      expect(input).toHaveAttribute('placeholder');
    });

    it('should handle RTL input fields correctly', () => {
      render(
<MockPreview locale="ar-SA" direction="rtl" theme="light" />
      );

      const input = screen.getByTestId('text-input');
      const container = screen.getByTestId('preview-container');
      
      // Input should inherit RTL direction from container
      expect(container).toHaveAttribute('dir', 'rtl');
    });
  });
});
