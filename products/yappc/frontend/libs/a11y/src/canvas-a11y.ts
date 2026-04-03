/**
 * @fileoverview Accessibility Utilities for YAPPC Canvas
 * WCAG 2.1 AA compliance helpers for canvas components
 * 
 * @doc.type utility
 * @doc.purpose Ensure canvas accessibility for screen readers and keyboard users
 * @doc.layer presentation
 * @doc.pattern Accessibility
 */

import React, { useId, useCallback, useEffect, useRef } from 'react';

// ============================================================================
// Types
// ============================================================================

export interface A11yConfig {
  role?: string;
  label?: string;
  description?: string;
  labelledBy?: string;
  describedBy?: string;
  liveRegion?: 'polite' | 'assertive' | 'off';
  hidden?: boolean;
  expanded?: boolean;
  selected?: boolean;
  pressed?: boolean;
  current?: 'page' | 'step' | 'location' | 'date' | 'time' | boolean;
  hasPopup?: boolean | 'menu' | 'listbox' | 'tree' | 'grid' | 'dialog';
  level?: number;
  posInSet?: number;
  setSize?: number;
}

// ============================================================================
// ARIA Helpers
// ============================================================================

/**
 * Generate ARIA attributes for canvas elements
 * @doc.purpose Create accessible canvas element attributes
 */
export function generateAriaProps(config: A11yConfig): Record<string, string | boolean | number | undefined> {
  const props: Record<string, string | boolean | number | undefined> = {};
  
  if (config.role) props.role = config.role;
  if (config.label) props['aria-label'] = config.label;
  if (config.description) props['aria-description'] = config.description;
  if (config.labelledBy) props['aria-labelledby'] = config.labelledBy;
  if (config.describedBy) props['aria-describedby'] = config.describedBy;
  if (config.liveRegion) props['aria-live'] = config.liveRegion;
  if (config.hidden !== undefined) props['aria-hidden'] = config.hidden;
  if (config.expanded !== undefined) props['aria-expanded'] = config.expanded;
  if (config.selected !== undefined) props['aria-selected'] = config.selected;
  if (config.pressed !== undefined) props['aria-pressed'] = config.pressed;
  if (config.current !== undefined) props['aria-current'] = config.current;
  if (config.hasPopup !== undefined) props['aria-haspopup'] = config.hasPopup;
  if (config.level !== undefined) props['aria-level'] = config.level;
  if (config.posInSet !== undefined) props['aria-posinset'] = config.posInSet;
  if (config.setSize !== undefined) props['aria-setsize'] = config.setSize;
  
  return props;
}

// ============================================================================
// Focus Management
// ============================================================================

/**
 * Hook for managing focus within canvas
 * @doc.purpose Handle keyboard navigation and focus trapping
 */
export function useCanvasFocus(containerRef: React.RefObject<HTMLElement>) {
  const [focusedElement, setFocusedElement] = React.useState<string | null>(null);
  const focusableElementsRef = useRef<string[]>([]);

  const registerFocusable = useCallback((id: string) => {
    if (!focusableElementsRef.current.includes(id)) {
      focusableElementsRef.current.push(id);
    }
  }, []);

  const unregisterFocusable = useCallback((id: string) => {
    focusableElementsRef.current = focusableElementsRef.current.filter(el => el !== id);
  }, []);

  const focusNext = useCallback(() => {
    const elements = focusableElementsRef.current;
    const currentIndex = focusedElement ? elements.indexOf(focusedElement) : -1;
    const nextIndex = (currentIndex + 1) % elements.length;
    const nextId = elements[nextIndex];
    
    setFocusedElement(nextId);
    document.getElementById(nextId)?.focus();
  }, [focusedElement]);

  const focusPrevious = useCallback(() => {
    const elements = focusableElementsRef.current;
    const currentIndex = focusedElement ? elements.indexOf(focusedElement) : -1;
    const prevIndex = currentIndex <= 0 ? elements.length - 1 : currentIndex - 1;
    const prevId = elements[prevIndex];
    
    setFocusedElement(prevId);
    document.getElementById(prevId)?.focus();
  }, [focusedElement]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Tab') {
        e.preventDefault();
        if (e.shiftKey) {
          focusPrevious();
        } else {
          focusNext();
        }
      }
    };

    const container = containerRef.current;
    if (container) {
      container.addEventListener('keydown', handleKeyDown);
      return () => container.removeEventListener('keydown', handleKeyDown);
    }
  }, [containerRef, focusNext, focusPrevious]);

  return {
    focusedElement,
    setFocusedElement,
    registerFocusable,
    unregisterFocusable,
    focusNext,
    focusPrevious,
  };
}

// ============================================================================
// Screen Reader Announcer
// ============================================================================

/**
 * Hook for announcing changes to screen readers
 * @doc.purpose Provide feedback for canvas operations
 */
export function useAnnouncer() {
  const announce = useCallback((message: string, priority: 'polite' | 'assertive' = 'polite') => {
    const announcer = document.getElementById('yappc-announcer');
    if (announcer) {
      announcer.setAttribute('aria-live', priority);
      announcer.textContent = message;
      
      // Clear after announcement
      setTimeout(() => {
        announcer.textContent = '';
      }, 1000);
    }
  }, []);

  return { announce };
}

/**
 * Screen Reader Announcer Component
 */
export const ScreenReaderAnnouncer: React.FC = () => {
  return (
    <div
      id="yappc-announcer"
      role="status"
      aria-live="polite"
      aria-atomic="true"
      style={{
        position: 'absolute',
        left: '-10000px',
        width: '1px',
        height: '1px',
        overflow: 'hidden',
      }}
    />
  );
};

// ============================================================================
// Canvas Element Accessibility
// ============================================================================

interface AccessibleCanvasElementProps {
  id: string;
  type: 'shape' | 'text' | 'image' | 'sketch' | 'connector';
  x: number;
  y: number;
  width: number;
  height: number;
  label?: string;
  description?: string;
  selected?: boolean;
  onSelect?: () => void;
  onDelete?: () => void;
  onMove?: (x: number, y: number) => void;
  children: React.ReactNode;
}

/**
 * Accessible Canvas Element Wrapper
 * @doc.purpose Make canvas elements keyboard accessible
 */
export const AccessibleCanvasElement: React.FC<AccessibleCanvasElementProps> = ({
  id,
  type,
  x,
  y,
  width,
  height,
  label,
  description,
  selected,
  onSelect,
  onDelete,
  onMove,
  children,
}) => {
  const elementRef = useRef<HTMLDivElement>(null);
  const { announce } = useAnnouncer();

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    const moveStep = 10; // pixels
    
    switch (e.key) {
      case 'Enter':
      case ' ':
        e.preventDefault();
        onSelect?.();
        announce(`${type} selected`);
        break;
      case 'Delete':
      case 'Backspace':
        e.preventDefault();
        onDelete?.();
        announce(`${type} deleted`);
        break;
      case 'ArrowUp':
        e.preventDefault();
        onMove?.(x, y - moveStep);
        announce(`${type} moved up`);
        break;
      case 'ArrowDown':
        e.preventDefault();
        onMove?.(x, y + moveStep);
        announce(`${type} moved down`);
        break;
      case 'ArrowLeft':
        e.preventDefault();
        onMove?.(x - moveStep, y);
        announce(`${type} moved left`);
        break;
      case 'ArrowRight':
        e.preventDefault();
        onMove?.(x + moveStep, y);
        announce(`${type} moved right`);
        break;
    }
  }, [type, x, y, onSelect, onDelete, onMove, announce]);

  const typeLabel = {
    shape: 'Shape',
    text: 'Text element',
    image: 'Image',
    sketch: 'Sketch',
    connector: 'Connector',
  }[type];

  return (
    <div
      ref={elementRef}
      id={id}
      role="button"
      tabIndex={0}
      aria-label={label || `${typeLabel} at position ${Math.round(x)}, ${Math.round(y)}`}
      aria-description={description}
      aria-selected={selected}
      onKeyDown={handleKeyDown}
      onClick={onSelect}
      style={{
        position: 'absolute',
        left: x,
        top: y,
        width,
        height,
        outline: selected ? '2px solid #0066cc' : 'none',
        outlineOffset: '2px',
      }}
    >
      {children}
    </div>
  );
};

// ============================================================================
// Accessible Toolbar
// ============================================================================

interface ToolbarButton {
  id: string;
  icon: React.ReactNode;
  label: string;
  shortcut?: string;
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
}

interface AccessibleToolbarProps {
  buttons: ToolbarButton[];
  orientation?: 'horizontal' | 'vertical';
  className?: string;
}

/**
 * Accessible Toolbar Component
 * @doc.purpose Keyboard-navigable toolbar with screen reader support
 */
export const AccessibleToolbar: React.FC<AccessibleToolbarProps> = ({
  buttons,
  orientation = 'horizontal',
  className,
}) => {
  const toolbarRef = useRef<HTMLDivElement>(null);
  const [focusedIndex, setFocusedIndex] = React.useState(-1);

  const handleKeyDown = useCallback((e: React.KeyboardEvent, index: number) => {
    const maxIndex = buttons.length - 1;
    
    switch (e.key) {
      case 'ArrowRight':
        if (orientation === 'horizontal') {
          e.preventDefault();
          const nextIndex = index >= maxIndex ? 0 : index + 1;
          setFocusedIndex(nextIndex);
          document.getElementById(`toolbar-btn-${buttons[nextIndex].id}`)?.focus();
        }
        break;
      case 'ArrowLeft':
        if (orientation === 'horizontal') {
          e.preventDefault();
          const prevIndex = index <= 0 ? maxIndex : index - 1;
          setFocusedIndex(prevIndex);
          document.getElementById(`toolbar-btn-${buttons[prevIndex].id}`)?.focus();
        }
        break;
      case 'ArrowDown':
        if (orientation === 'vertical') {
          e.preventDefault();
          const nextIndex = index >= maxIndex ? 0 : index + 1;
          setFocusedIndex(nextIndex);
          document.getElementById(`toolbar-btn-${buttons[nextIndex].id}`)?.focus();
        }
        break;
      case 'ArrowUp':
        if (orientation === 'vertical') {
          e.preventDefault();
          const prevIndex = index <= 0 ? maxIndex : index - 1;
          setFocusedIndex(prevIndex);
          document.getElementById(`toolbar-btn-${buttons[prevIndex].id}`)?.focus();
        }
        break;
      case 'Home':
        e.preventDefault();
        setFocusedIndex(0);
        document.getElementById(`toolbar-btn-${buttons[0].id}`)?.focus();
        break;
      case 'End':
        e.preventDefault();
        setFocusedIndex(maxIndex);
        document.getElementById(`toolbar-btn-${buttons[maxIndex].id}`)?.focus();
        break;
    }
  }, [buttons, orientation]);

  return (
    <div
      ref={toolbarRef}
      role="toolbar"
      aria-orientation={orientation}
      aria-label="Canvas toolbar"
      className={className}
    >
      {buttons.map((button, index) => (
        <button
          key={button.id}
          id={`toolbar-btn-${button.id}`}
          role="button"
          tabIndex={focusedIndex === index ? 0 : -1}
          aria-label={`${button.label}${button.shortcut ? ` (${button.shortcut})` : ''}`}
          aria-pressed={button.active}
          disabled={button.disabled}
          onClick={button.onClick}
          onKeyDown={(e) => handleKeyDown(e, index)}
          onFocus={() => setFocusedIndex(index)}
          style={{
            background: button.active ? '#0066cc' : 'transparent',
            color: button.active ? 'white' : 'inherit',
            opacity: button.disabled ? 0.5 : 1,
          }}
        >
          {button.icon}
          <span className="visually-hidden">{button.label}</span>
        </button>
      ))}
    </div>
  );
};

// ============================================================================
// Accessibility Styles
// ============================================================================

/**
 * CSS for accessibility features
 * Include this in your global styles
 */
export const accessibilityStyles = `
  /* Focus indicators */
  .yappc-focusable:focus {
    outline: 2px solid #0066cc;
    outline-offset: 2px;
  }
  
  .yappc-focusable:focus-visible {
    outline: 2px solid #0066cc;
    outline-offset: 2px;
  }
  
  /* Visually hidden but accessible to screen readers */
  .visually-hidden {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }
  
  /* Skip link for keyboard navigation */
  .skip-link {
    position: absolute;
    top: -40px;
    left: 0;
    background: #000;
    color: #fff;
    padding: 8px;
    z-index: 10000;
    transition: top 0.3s;
  }
  
  .skip-link:focus {
    top: 0;
  }
  
  /* High contrast mode support */
  @media (prefers-contrast: high) {
    .yappc-canvas {
      border: 2px solid currentColor;
    }
    
    .yappc-element {
      border: 1px solid currentColor;
    }
  }
  
  /* Reduced motion support */
  @media (prefers-reduced-motion: reduce) {
    .yappc-animated {
      animation: none !important;
      transition: none !important;
    }
  }
`;

// ============================================================================
// Color Contrast Utilities
// ============================================================================

/**
 * Calculate contrast ratio between two colors
 * @doc.purpose Ensure WCAG color contrast compliance
 */
export function getContrastRatio(color1: string, color2: string): number {
  const lum1 = getLuminance(color1);
  const lum2 = getLuminance(color2);
  const brightest = Math.max(lum1, lum2);
  const darkest = Math.min(lum1, lum2);
  return (brightest + 0.05) / (darkest + 0.05);
}

function getLuminance(color: string): number {
  const rgb = hexToRgb(color) || { r: 0, g: 0, b: 0 };
  const [r, g, b] = [rgb.r, rgb.g, rgb.b].map(c => {
    c = c / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16),
  } : null;
}

/**
 * Check if color combination meets WCAG AA standards
 */
export function meetsWCAGAA(textColor: string, bgColor: string, largeText = false): boolean {
  const ratio = getContrastRatio(textColor, bgColor);
  return largeText ? ratio >= 3 : ratio >= 4.5;
}

// ============================================================================
// Export
// ============================================================================

export default {
  generateAriaProps,
  useCanvasFocus,
  useAnnouncer,
  AccessibleCanvasElement,
  AccessibleToolbar,
  ScreenReaderAnnouncer,
  getContrastRatio,
  meetsWCAGAA,
  accessibilityStyles,
};
