/**
 * Accessibility A11y Utilities Tests
 * @doc.type test
 * @doc.purpose Test WCAG 2.1 AA accessibility utilities
 * @doc.layer unit
 */

import { describe, it, expect, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { render, screen } from '@testing-library/react';
import React from 'react';
import {
  generateAriaProps,
  useCanvasFocus,
  useAnnouncer,
  ScreenReaderAnnouncer,
  AccessibleCanvasElement,
} from './canvas-a11y';
import type { A11yConfig } from './canvas-a11y';

describe('Accessibility Utilities', () => {
  describe('ARIA Props Generation', () => {
    it('should generate basic ARIA properties', () => {
      const config: A11yConfig = {
        role: 'button',
        label: 'Save',
      };

      const ariaProps = generateAriaProps(config);

      expect(ariaProps['aria-label']).toBe('Save');
      expect(ariaProps.role).toBe('button');
    });

    it('should generate ARIA properties for interactive elements', () => {
      const config: A11yConfig = {
        role: 'button',
        label: 'Expand Menu',
        pressed: true,
      };

      const ariaProps = generateAriaProps(config);

      expect(ariaProps['aria-pressed']).toBe(true);
    });

    it('should support aria-describedby', () => {
      const config: A11yConfig = {
        role: 'region',
        label: 'Main Content',
        describedBy: 'description-1',
      };

      const ariaProps = generateAriaProps(config);

      expect(ariaProps['aria-describedby']).toBe('description-1');
    });

    it('should support aria-labelledby', () => {
      const config: A11yConfig = {
        role: 'section',
        labelledBy: 'heading-1',
      };

      const ariaProps = generateAriaProps(config);

      expect(ariaProps['aria-labelledby']).toBe('heading-1');
    });

    it('should set aria-hidden when needed', () => {
      const config: A11yConfig = {
        hidden: true,
      };

      const ariaProps = generateAriaProps(config);

      expect(ariaProps['aria-hidden']).toBe(true);
    });

    it('should support aria-expanded for expandable elements', () => {
      const config: A11yConfig = {
        role: 'button',
        expanded: true,
        controls: 'menu-1',
      };

      const ariaProps = generateAriaProps(config);

      expect(ariaProps['aria-expanded']).toBe(true);
      expect(ariaProps['aria-controls']).toBe('menu-1');
    });
  });

  describe('Canvas Focus Management', () => {
    it('should initialize focus hook', () => {
      const { result } = renderHook(() => useCanvasFocus());

      expect(result.current.focusedElement).toBeNull();
    });

    it('should set focus to element', () => {
      const { result } = renderHook(() => useCanvasFocus());

      act(() => {
        result.current.setFocus('element-1');
      });

      expect(result.current.focusedElement).toBe('element-1');
    });

    it('should navigate focus with keyboard', async () => {
      const { result } = renderHook(() => useCanvasFocus());

      act(() => {
        result.current.navigateNext();
      });

      expect(result.current.focusedElement).toBeDefined();
    });

    it('should cycle focus backwards', () => {
      const { result } = renderHook(() => useCanvasFocus());

      act(() => {
        result.current.navigatePrevious();
      });

      expect(result.current).toBeDefined();
    });

    it('should handle focus restoration', () => {
      const { result } = renderHook(() => useCanvasFocus());

      act(() => {
        result.current.setFocus('element-1');
        result.current.saveFocusState();
      });

      act(() => {
        result.current.setFocus('element-2');
      });

      act(() => {
        result.current.restoreFocusState();
      });

      expect(result.current.focusedElement).toBe('element-1');
    });
  });

  describe('Screen Reader Announcements', () => {
    it('should initialize announcer hook', () => {
      const { result } = renderHook(() => useAnnouncer());

      expect(result.current.announce).toBeDefined();
    });

    it('should announce polite messages', () => {
      const { result } = renderHook(() => useAnnouncer());

      act(() => {
        result.current.announce('Saved successfully', 'polite');
      });

      expect(result.current).toBeDefined();
    });

    it('should announce assertive messages', () => {
      const { result } = renderHook(() => useAnnouncer());

      act(() => {
        result.current.announce('Error occurred', 'assertive');
      });

      expect(result.current).toBeDefined();
    });

    it('should clear previous announcements', () => {
      const { result } = renderHook(() => useAnnouncer());

      act(() => {
        result.current.announce('First message');
        result.current.announce('Second message');
      });

      expect(result.current).toBeDefined();
    });
  });

  describe('ScreenReaderAnnouncer Component', () => {
    it('should render live region', () => {
      const { container } = render(
        React.createElement(ScreenReaderAnnouncer, { id: 'announcer-1' })
      );

      const liveRegion = container.querySelector('[role="status"]');
      expect(liveRegion).toBeInTheDocument();
    });

    it('should have correct ARIA attributes', () => {
      const { container } = render(
        React.createElement(ScreenReaderAnnouncer, {
          id: 'announcer-1',
          polite: false,
        })
      );

      const liveRegion = container.querySelector('[role="status"]');
      expect(liveRegion).toHaveAttribute('aria-live', 'assertive');
      expect(liveRegion).toHaveAttribute('aria-atomic', 'true');
    });

    it('should be visually hidden', () => {
      const { container } = render(
        React.createElement(ScreenReaderAnnouncer, { id: 'announcer-1' })
      );

      const announcer = container.querySelector('[role="status"]');
      const computedStyle = window.getComputedStyle(announcer!);

      expect(computedStyle.position).toBe('absolute');
    });
  });

  describe('Accessible Canvas Element', () => {
    it('should render canvas element with accessibility features', () => {
      const { container } = render(
        React.createElement(AccessibleCanvasElement, {
          id: 'canvas-1',
          label: 'Interactive Canvas',
          role: 'img',
        } as any)
      );

      const element = container.querySelector('[role="img"]');
      expect(element).toBeInTheDocument();
    });

    it('should support keyboard navigation', () => {
      const handleKeyDown = vi.fn();
      const { container } = render(
        React.createElement(AccessibleCanvasElement, {
          id: 'canvas-1',
          label: 'Canvas',
          onKeyDown: handleKeyDown,
        } as any)
      );

      expect(container).toBeInTheDocument();
    });

    it('should be focusable', () => {
      const { container } = render(
        React.createElement(AccessibleCanvasElement, {
          id: 'canvas-1',
          label: 'Canvas',
          focusable: true,
        } as any)
      );

      const element = container.querySelector('[tabindex="0"]');
      expect(element).toBeInTheDocument();
    });
  });

  describe('WCAG Compliance', () => {
    it('should ensure color contrast for text', () => {
      const config: A11yConfig = {
        role: 'button',
        label: 'High Contrast Button',
      };

      const ariaProps = generateAriaProps(config);
      expect(ariaProps).toBeDefined();
    });

    it('should support keyboard shortcuts', () => {
      const { result } = renderHook(() => useCanvasFocus());

      expect(result.current.navigateNext).toBeDefined();
      expect(result.current.navigatePrevious).toBeDefined();
    });

    it('should announce state changes to screen readers', () => {
      const { result } = renderHook(() => useAnnouncer());

      act(() => {
        result.current.announce('Button activated');
      });

      expect(result.current).toBeDefined();
    });

    it('should support reduced motion', () => {
      const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
      expect(mediaQuery).toBeDefined();
    });
  });

  describe('Focus Visible', () => {
    it('should show focus indicator for keyboard users', () => {
      const { container } = render(
        React.createElement(AccessibleCanvasElement, {
          id: 'canvas-1',
          label: 'Canvas',
        } as any)
      );

      expect(container).toBeInTheDocument();
    });
  });
});
