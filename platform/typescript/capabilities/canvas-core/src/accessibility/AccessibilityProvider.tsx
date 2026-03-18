/**
 * Canvas Accessibility Provider
 *
 * React component that provides comprehensive accessibility features
 * including focus management, screen reader support, keyboard navigation,
 * and high contrast mode detection.
 *
 * @doc.type component
 * @doc.purpose Accessibility provider and management
 * @doc.layer presentation
 */

import React, { useEffect, useRef, useCallback } from "react";
import { useAtom, useAtomValue } from "jotai";
import {
  accessibilitySettingsAtom,
  navigationModeAtom,
  focusManager,
  screenReaderManager,
  keyboardNavigationManager,
  contrastModeManager,
  trapFocus,
  generateAriaId,
  applyReducedMotion,
} from "./accessibility";
import type { AccessibilitySettings, FocusableElement } from "./accessibility";

interface AccessibilityProviderProps {
  children: React.ReactNode;
  enableKeyboardNavigation?: boolean;
  enableScreenReader?: boolean;
  enableHighContrast?: boolean;
}

/**
 * Accessibility Provider Component
 *
 * Wraps the canvas application and provides comprehensive accessibility features.
 * Automatically detects user preferences and applies appropriate accommodations.
 *
 * @example
 * ```tsx
 * import { AccessibilityProvider } from '@ghatana/canvas/accessibility';
 *
 * function App() {
 *   return (
 *     <AccessibilityProvider>
 *       <ThemeProvider>
 *         <CanvasChromeLayout>
 *           {/* Your canvas content *\/}
 *         </CanvasChromeLayout>
 *       </ThemeProvider>
 *     </AccessibilityProvider>
 *   );
 * }
 * ```
 */
export const AccessibilityProvider: React.FC<AccessibilityProviderProps> = ({
  children,
  enableKeyboardNavigation = true,
  enableScreenReader = true,
  enableHighContrast = true,
}) => {
  const [settings, setSettings] = useAtom(accessibilitySettingsAtom);
  const [navigationMode, setNavigationMode] = useAtom(navigationModeAtom);
  const containerRef = useRef<HTMLDivElement>(null);
  const focusCleanupRef = useRef<(() => void) | null>(null);

  // Cycle through major sections (defined before useEffect that references it)
  const cycleThroughSections = useCallback(() => {
    const sections = [
      "top-bar",
      "left-rail",
      "canvas-area",
      "right-panel",
      "bottom-bar",
    ]
      .map((id) => document.getElementById(id))
      .filter((element): element is HTMLElement => element !== null);

    const currentIndex = sections.findIndex((section) =>
      section.contains(document.activeElement),
    );

    const nextIndex = (currentIndex + 1) % sections.length;
    const nextSection = sections[nextIndex];

    if (nextSection) {
      const focusableElements = nextSection.querySelectorAll(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      );

      if (focusableElements.length > 0) {
        (focusableElements[0] as HTMLElement).focus();
      }
    }
  }, []);

  // Initialize keyboard navigation
  useEffect(() => {
    if (!enableKeyboardNavigation) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      // Let keyboard navigation manager handle it first
      if (keyboardNavigationManager.handle(event)) {
        return;
      }

      // Handle global keyboard shortcuts
      switch (event.key) {
        case "Tab":
          // Tab navigation is handled by browser, but we can enhance it
          break;
        case "Escape":
          // Escape to go back to previous focus
          focusManager.goBack();
          break;
        case "F6":
          // F6 to cycle through major sections
          event.preventDefault();
          cycleThroughSections();
          break;
        case "/":
          // Slash to open command palette (if not in input)
          if (event.target instanceof HTMLInputElement) return;
          event.preventDefault();
          // Trigger command palette
          break;
      }
    };

    // Detect keyboard-only navigation
    const handleMouseDown = () => {
      if (settings.keyboardOnly) {
        setSettings((prev) => ({ ...prev, keyboardOnly: false }));
      }
    };

    const handleKeyDownOnly = () => {
      if (!settings.keyboardOnly) {
        setSettings((prev) => ({ ...prev, keyboardOnly: true }));
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    document.addEventListener("mousedown", handleMouseDown);
    document.addEventListener("keydown", handleKeyDownOnly, { once: true });

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.removeEventListener("mousedown", handleMouseDown);
      document.removeEventListener("keydown", handleKeyDownOnly);
    };
  }, [
    enableKeyboardNavigation,
    settings.keyboardOnly,
    setSettings,
    cycleThroughSections,
  ]);

  // Initialize screen reader support
  useEffect(() => {
    if (!enableScreenReader) return;

    // Detect screen reader usage
    const handleFocus = (event: FocusEvent) => {
      const target = event.target as HTMLElement;
      const label =
        target.getAttribute("aria-label") || target.textContent || "";

      if (label && settings.screenReader) {
        screenReaderManager.announce(`Focused on ${label}`);
      }
    };

    document.addEventListener("focus", handleFocus, true);

    return () => {
      document.removeEventListener("focus", handleFocus, true);
    };
  }, [enableScreenReader, settings.screenReader]);

  // Initialize high contrast mode
  useEffect(() => {
    if (!enableHighContrast) return;

    const unsubscribe = contrastModeManager.subscribe((mode) => {
      const isHighContrast = mode === "high-contrast";
      const isReducedMotion = mode === "reduced-motion";

      setSettings((prev) => ({
        ...prev,
        highContrast: isHighContrast,
        reducedMotion: isReducedMotion || prev.reducedMotion,
      }));

      // Apply high contrast CSS variables
      if (isHighContrast) {
        contrastModeManager.applyHighContrastVariables();
      }
    });

    return unsubscribe;
  }, [enableHighContrast, setSettings]);

  return (
    <div
      ref={containerRef}
      data-navigation-mode={navigationMode}
      data-accessibility-enabled={
        enableKeyboardNavigation || enableScreenReader || enableHighContrast
      }
      data-keyboard-only={settings.keyboardOnly}
      data-screen-reader={settings.screenReader}
      data-high-contrast={settings.highContrast}
      data-reduced-motion={settings.reducedMotion}
      style={{
        // Apply accessibility styles
        ...(settings.reducedMotion ? {
          "--transition-duration": "0.01ms",
          "--animation-duration": "0.01ms",
        } as React.CSSProperties : {}),
        ...(settings.fontSize !== "medium" ? {
          "--font-size-multiplier": getFontSizeMultiplier(settings.fontSize),
        } as React.CSSProperties : {}),
      }}
    >
      {children}
    </div>
  );
};

/**
 * Hook for accessibility utilities
 */
export const useAccessibility = () => {
  const [settings] = useAtom(accessibilitySettingsAtom);
  const navigationMode = useAtomValue(navigationModeAtom);

  const announce = useCallback(
    (message: string, priority: "polite" | "assertive" = "polite") => {
      screenReaderManager.announce(message, priority);
    },
    [],
  );

  const focusElement = useCallback((id: string) => {
    return focusManager.focus(id);
  }, []);

  const registerFocusable = useCallback(
    (element: HTMLElement, options: Partial<FocusableElement>) => {
      return focusManager.register(element, options);
    },
    [],
  );

  const trapFocusInContainer = useCallback((container: HTMLElement) => {
    if (focusCleanupRef.current) {
      focusCleanupRef.current();
    }
    focusCleanupRef.current = trapFocus(container);
  }, []);

  const generateId = useCallback((prefix: string = "canvas") => {
    return generateAriaId(prefix);
  }, []);

  return {
    settings,
    navigationMode,
    announce,
    focusElement,
    registerFocusable,
    trapFocusInContainer,
    generateId,
    isKeyboardOnly: settings.keyboardOnly,
    isScreenReader: settings.screenReader,
    isHighContrast: settings.highContrast,
    prefersReducedMotion: settings.reducedMotion,
  };
};

/**
 * Focus Trap Hook
 */
export const useFocusTrap = (
  isActive: boolean,
  containerRef: React.RefObject<HTMLElement>,
) => {
  useEffect(() => {
    if (!isActive || !containerRef.current) return;

    const cleanup = trapFocus(containerRef.current);
    return cleanup;
  }, [isActive, containerRef]);
};

/**
 * Focus Management Hook
 */
export const useFocusManagement = (
  containerRef: React.RefObject<HTMLElement>,
) => {
  const focusableElementsRef = useRef<string[]>([]);

  const registerFocusable = useCallback((element: HTMLElement, id: string) => {
    const focusId = focusManager.register(element, { id });
    focusableElementsRef.current.push(focusId);
    return focusId;
  }, []);

  const unregisterFocusable = useCallback((id: string) => {
    focusManager.unregister(id);
    focusableElementsRef.current = focusableElementsRef.current.filter(
      (fId) => fId !== id,
    );
  }, []);

  const focusFirst = useCallback(() => {
    if (focusableElementsRef.current.length > 0) {
      focusManager.focus(focusableElementsRef.current[0]);
    }
  }, []);

  const focusLast = useCallback(() => {
    if (focusableElementsRef.current.length > 0) {
      focusManager.focus(
        focusableElementsRef.current[focusableElementsRef.current.length - 1],
      );
    }
  }, []);

  useEffect(() => {
    return () => {
      // Cleanup on unmount
      focusableElementsRef.current.forEach((id) => {
        focusManager.unregister(id);
      });
    };
  }, []);

  return {
    registerFocusable,
    unregisterFocusable,
    focusFirst,
    focusLast,
  };
};

/**
 * Keyboard Navigation Hook
 */
export const useKeyboardNavigation = () => {
  const registerHandler = useCallback(
    (key: string, handler: (event: KeyboardEvent) => boolean) => {
      keyboardNavigationManager.register(key, handler);

      return () => {
        keyboardNavigationManager.unregister(key);
      };
    },
    [],
  );

  return {
    registerHandler,
  };
};

/**
 * Font size multiplier helper
 */
function getFontSizeMultiplier(
  fontSize: AccessibilitySettings["fontSize"],
): number {
  switch (fontSize) {
    case "small":
      return 0.875;
    case "medium":
      return 1;
    case "large":
      return 1.125;
    case "extra-large":
      return 1.25;
    default:
      return 1;
  }
}

// Export focus cleanup ref for use in components
export const focusCleanupRef = { current: null as (() => void) | null };
