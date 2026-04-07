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

export interface AccessibleCanvasElementProps {
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
  const toolbarId = useId();
  const [activeIndex, setActiveIndex] = React.useState(0);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    const isHorizontal = orientation === 'horizontal';
    let newIndex = activeIndex;

    switch (e.key) {
      case isHorizontal ? 'ArrowRight' : 'ArrowDown':
        e.preventDefault();
        newIndex = (activeIndex + 1) % buttons.length;
        setActiveIndex(newIndex);
        break;
      case isHorizontal ? 'ArrowLeft' : 'ArrowUp':
        e.preventDefault();
        newIndex = activeIndex === 0 ? buttons.length - 1 : activeIndex - 1;
        setActiveIndex(newIndex);
        break;
      case 'Home':
        e.preventDefault();
        setActiveIndex(0);
        break;
      case 'End':
        e.preventDefault();
        setActiveIndex(buttons.length - 1);
        break;
    }
  }, [activeIndex, buttons.length, orientation]);

  useEffect(() => {
    const button = document.getElementById(`${toolbarId}-button-${activeIndex}`);
    button?.focus();
  }, [activeIndex, toolbarId]);

  return (
    <div
      role="toolbar"
      aria-orientation={orientation}
      className={className}
      onKeyDown={handleKeyDown}
    >
      {buttons.map((button, index) => (
        <button
          key={button.id}
          id={`${toolbarId}-button-${index}`}
          type="button"
          tabIndex={index === activeIndex ? 0 : -1}
          aria-label={button.shortcut ? `${button.label} (${button.shortcut})` : button.label}
          aria-pressed={button.active}
          disabled={button.disabled}
          onClick={button.onClick}
        >
          {button.icon}
          <span className="sr-only">{button.label}</span>
        </button>
      ))}
    </div>
  );
};