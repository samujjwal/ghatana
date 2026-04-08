/**
 * Accessibility Library Tests
 */

import { describe, it, expect } from 'vitest';
import {
  trapFocus,
  getFocusableElements,
  focusFirstElement,
  focusLastElement,
  restoreFocus,
  getModalAriaProps,
  getLiveRegionProps,
  getProgressProps,
  getTabPanelProps,
  getTabProps,
  getMenuItemProps,
  getToggleButtonProps,
  getExpandableProps,
  announceToScreenReader,
  visuallyHiddenStyles,
  getRelativeLuminance,
  getContrastRatio,
  meetsWCAGAA,
  meetsWCAGAAA,
  prefersReducedMotion,
  getAnimationDuration,
  getHeadingProps,
  getLandmarkProps,
} from '../accessibility';

describe('Accessibility Library', () => {
  describe('Focus Management', () => {
    it('should get focusable elements', () => {
      const container = document.createElement('div');
      container.innerHTML = `
        <button>Button 1</button>
        <a href="#">Link 1</a>
        <input type="text" />
        <div>Not focusable</div>
      `;
      document.body.appendChild(container);

      const focusable = getFocusableElements(container);

      expect(focusable.length).toBe(3);
      document.body.removeChild(container);
    });

    it('should focus first element', () => {
      const container = document.createElement('div');
      const button1 = document.createElement('button');
      const button2 = document.createElement('button');

      container.appendChild(button1);
      container.appendChild(button2);
      document.body.appendChild(container);

      const focused = focusFirstElement(container);

      expect(focused).toBe(true);
      document.body.removeChild(container);
    });

    it('should focus last element', () => {
      const container = document.createElement('div');
      const button1 = document.createElement('button');
      const button2 = document.createElement('button');

      container.appendChild(button1);
      container.appendChild(button2);
      document.body.appendChild(container);

      const focused = focusLastElement(container);

      expect(focused).toBe(true);
      document.body.removeChild(container);
    });
  });

  describe('ARIA Helpers', () => {
    it('should generate modal ARIA props', () => {
      const props = getModalAriaProps('modal-id', 'modal-title', 'modal-desc');

      expect(props.role).toBe('dialog');
      expect(props['aria-modal']).toBe('true');
      expect(props['aria-labelledby']).toBe('modal-title');
      expect(props['aria-describedby']).toBe('modal-desc');
    });

    it('should generate live region props', () => {
      const props = getLiveRegionProps('polite');

      expect(props.role).toBe('status');
      expect(props['aria-live']).toBe('polite');
      expect(props['aria-atomic']).toBe('true');
    });

    it('should generate progress props', () => {
      const props = getProgressProps(50, 100);

      expect(props.role).toBe('progressbar');
      expect(props['aria-valuenow']).toBe(50);
      expect(props['aria-valuemin']).toBe(0);
      expect(props['aria-valuemax']).toBe(100);
    });

    it('should generate tab panel props', () => {
      const props = getTabPanelProps('panel-1', 'tab-1', true);

      expect(props.role).toBe('tabpanel');
      expect(props['aria-labelledby']).toBe('tab-1');
      expect(props['aria-hidden']).toBe(false);
      expect(props.tabIndex).toBe(0);
    });

    it('should generate tab props', () => {
      const props = getTabProps('tab-1', 'panel-1', true);

      expect(props.role).toBe('tab');
      expect(props['aria-selected']).toBe(true);
      expect(props['aria-controls']).toBe('panel-1');
      expect(props.tabIndex).toBe(0);
    });

    it('should generate toggle button props', () => {
      const props = getToggleButtonProps(true, 'Toggle');

      expect(props['aria-pressed']).toBe(true);
      expect(props['aria-label']).toBe('Toggle');
      expect(props.role).toBe('button');
    });

    it('should generate expandable props', () => {
      const props = getExpandableProps(true, 'content-1');

      expect(props['aria-expanded']).toBe(true);
      expect(props['aria-controls']).toBe('content-1');
    });
  });

  describe('Screen Reader', () => {
    it('should announce to screen reader', () => {
      announceToScreenReader('Test message');

      const announcements = document.querySelectorAll('[role="status"]');
      expect(announcements.length).toBeGreaterThan(0);
    });
  });

  describe('Color Contrast', () => {
    it('should calculate relative luminance', () => {
      const luminance = getRelativeLuminance('#ffffff');

      expect(luminance).toBe(1);
    });

    it('should calculate contrast ratio', () => {
      const ratio = getContrastRatio('#ffffff', '#000000');

      expect(ratio).toBe(21);
    });

    it('should check WCAG AA compliance', () => {
      const compliant = meetsWCAGAA('#ffffff', '#000000');

      expect(compliant).toBe(true);
    });

    it('should check WCAG AAA compliance', () => {
      const compliant = meetsWCAGAAA('#ffffff', '#000000');

      expect(compliant).toBe(true);
    });
  });

  describe('Reduced Motion', () => {
    it('should check reduced motion preference', () => {
      const prefersReduced = prefersReducedMotion();

      expect(typeof prefersReduced).toBe('boolean');
    });

    it('should get animation duration respecting reduced motion', () => {
      const duration = getAnimationDuration(300);

      expect(typeof duration).toBe('number');
    });
  });

  describe('Semantic HTML', () => {
    it('should generate heading props', () => {
      const props = getHeadingProps(1);

      expect(props.role).toBe('heading');
      expect(props['aria-level']).toBe(1);
    });

    it('should generate landmark props', () => {
      const props = getLandmarkProps('main');

      expect(props.role).toBe('main');
    });
  });
});
