import { render, cleanup } from '@testing-library/react';
import React from 'react';

import { lightTheme, darkTheme } from './theme';
import { ThemeProvider } from './ThemeContext';
import MuiThemeProvider from './ThemeProvider';

import type { ThemeMode } from './ThemeContext';
import type { RenderOptions} from '@testing-library/react';
import type { ReactElement } from 'react';

/**
 * Custom render function that wraps the component with ThemeProvider
 * @param ui - Component to render
 * @param options - Render options
 * @param themeMode - Theme mode to use (defaults to 'light')
 * @returns Rendered component with testing utilities
 */
export function renderWithTheme(
  ui: ReactElement,
  options?: Omit<RenderOptions, 'wrapper'>,
  themeMode: ThemeMode = 'light'
): ReturnType<typeof render> {
  const Wrapper = ({ children }: { children: React.ReactNode }) => (
    <ThemeProvider defaultMode={themeMode}>
      <MuiThemeProvider mode={themeMode}>
        {children}
      </MuiThemeProvider>
    </ThemeProvider>
  );

  // Ensure previous renders are cleaned up so multiple calls to renderWithTheme
  // won't leave mounted ThemeProviders or duplicate ThemeToggle instances.
  cleanup();
  return render(ui, { wrapper: Wrapper, ...options });
}

/**
 * Get the theme object for testing
 * @param mode - Theme mode to get
 * @returns Theme object
 */
export function getTheme(mode: ThemeMode = 'light') {
  return mode === 'light' ? lightTheme : darkTheme;
}

/**
 * Check if a color passes WCAG contrast ratio against another color
 * @param foreground - Foreground color in hex format (e.g., '#ffffff')
 * @param background - Background color in hex format (e.g., '#000000')
 * @param level - WCAG level to check against ('AA' or 'AAA')
 * @param largeText - Whether the text is considered large (>=18pt or 14pt bold)
 * @returns Whether the contrast ratio passes the specified level
 */
export function checkContrastRatio(
  foreground: string,
  background: string,
  level: 'AA' | 'AAA' = 'AA',
  largeText: boolean = false
): boolean {
  // Convert hex to RGB
  const hexToRgb = (hex: string): number[] => {
    const shorthandRegex = /^#?([a-f\d])([a-f\d])([a-f\d])$/i;
    const formattedHex = hex.replace(shorthandRegex, (_, r, g, b) => r + r + g + g + b + b);
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(formattedHex);
    return result 
      ? [
          parseInt(result[1], 16),
          parseInt(result[2], 16),
          parseInt(result[3], 16)
        ]
      : [0, 0, 0];
  };

  // Calculate relative luminance
  const calculateLuminance = (rgb: number[]): number => {
    const [r, g, b] = rgb.map(c => {
      const sRGB = c / 255;
      return sRGB <= 0.03928
        ? sRGB / 12.92
        : Math.pow((sRGB + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };

  const foregroundRgb = hexToRgb(foreground);
  const backgroundRgb = hexToRgb(background);
  
  const foregroundLuminance = calculateLuminance(foregroundRgb);
  const backgroundLuminance = calculateLuminance(backgroundRgb);
  
  // Calculate contrast ratio
  const contrastRatio = 
    (Math.max(foregroundLuminance, backgroundLuminance) + 0.05) /
    (Math.min(foregroundLuminance, backgroundLuminance) + 0.05);
  
  // WCAG 2.1 contrast requirements
  const minimumRatio = level === 'AA'
    ? (largeText ? 3 : 4.5)
    : (largeText ? 4.5 : 7);
  
  return contrastRatio >= minimumRatio;
}

/**
 * Test all theme color combinations for accessibility
 * @param mode - Theme mode to test
 * @param level - WCAG level to check against ('AA' or 'AAA')
 * @returns Object with test results for each color combination
 */
export function testThemeAccessibility(
  mode: ThemeMode = 'light',
  level: 'AA' | 'AAA' = 'AA'
) {
  const theme = getTheme(mode);
  const results: Record<string, { pass: boolean; ratio: number }> = {};
  
  // Test text colors against background colors
  const textColors = {
    primary: theme.palette.text.primary,
    secondary: theme.palette.text.secondary,
    disabled: theme.palette.text.disabled,
  };
  
  const backgroundColors = {
    default: theme.palette.background.default,
    paper: theme.palette.background.paper,
  };
  
  // Test primary button colors
  const buttonColors = {
    primary: theme.palette.primary.main,
    secondary: theme.palette.secondary.main,
    error: theme.palette.error.main,
    warning: theme.palette.warning.main,
    info: theme.palette.info.main,
    success: theme.palette.success.main,
  };
  
  // Test text against backgrounds
  Object.entries(textColors).forEach(([textKey, textColor]) => {
    Object.entries(backgroundColors).forEach(([bgKey, bgColor]) => {
      const key = `${textKey}_on_${bgKey}`;
      const ratio = calculateContrastRatio(textColor, bgColor);
      results[key] = {
        pass: checkContrastRatio(textColor, bgColor, level),
        ratio,
      };
    });
  });
  
  // Test button text against button backgrounds
  Object.entries(buttonColors).forEach(([buttonKey, buttonColor]) => {
    const key = `text_on_${buttonKey}_button`;
    const ratio = calculateContrastRatio('#ffffff', buttonColor);
    results[key] = {
      pass: checkContrastRatio('#ffffff', buttonColor, level),
      ratio,
    };
  });
  
  return results;
}

/**
 * Calculate contrast ratio between two colors
 * @param color1 - First color in hex format
 * @param color2 - Second color in hex format
 * @returns Contrast ratio as a number
 */
export function calculateContrastRatio(color1: string, color2: string): number {
  // Convert hex to RGB
  const hexToRgb = (hex: string): number[] => {
    const shorthandRegex = /^#?([a-f\d])([a-f\d])([a-f\d])$/i;
    const formattedHex = hex.replace(shorthandRegex, (_, r, g, b) => r + r + g + g + b + b);
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(formattedHex);
    return result 
      ? [
          parseInt(result[1], 16),
          parseInt(result[2], 16),
          parseInt(result[3], 16)
        ]
      : [0, 0, 0];
  };

  // Calculate relative luminance
  const calculateLuminance = (rgb: number[]): number => {
    const [r, g, b] = rgb.map(c => {
      const sRGB = c / 255;
      return sRGB <= 0.03928
        ? sRGB / 12.92
        : Math.pow((sRGB + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };

  const color1Rgb = hexToRgb(color1);
  const color2Rgb = hexToRgb(color2);
  
  const color1Luminance = calculateLuminance(color1Rgb);
  const color2Luminance = calculateLuminance(color2Rgb);
  
  // Calculate contrast ratio
  return (Math.max(color1Luminance, color2Luminance) + 0.05) /
         (Math.min(color1Luminance, color2Luminance) + 0.05);
}
