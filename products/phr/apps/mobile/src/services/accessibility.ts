/**
 * Accessibility utilities for PHR mobile app.
 *
 * Provides hooks and utilities for:
 * - Dynamic font scaling (large text support)
 * - High contrast mode detection
 * - Screen reader status
 */

import { AccessibilityInfo, Dimensions, Platform } from 'react-native';

/**
 * Returns true if the device is in high contrast mode.
 */
export async function isHighContrastEnabled(): Promise<boolean> {
  if (Platform.OS === 'ios') {
    try {
      const isHighContrast = await AccessibilityInfo.isHighTextContrastEnabled();
      return isHighContrast;
    } catch {
      return false;
    }
  }
  return false;
}

/**
 * Returns true if a screen reader is active.
 */
export async function isScreenReaderEnabled(): Promise<boolean> {
  try {
    const isScreenReaderEnabled = await AccessibilityInfo.isScreenReaderEnabled();
    return isScreenReaderEnabled;
  } catch {
    return false;
  }
}

/**
 * Returns the current font scale factor from the device's accessibility settings.
 * Default is 1.0. Values > 1.0 indicate larger text is preferred.
 */
export async function getFontScale(): Promise<number> {
  try {
    const { fontScale } = Dimensions.get('window');
    return fontScale || 1.0;
  } catch {
    return 1.0;
  }
}

/**
 * Returns true if the user prefers large text (font scale > 1.2).
 */
export async function prefersLargeText(): Promise<boolean> {
  const scale = await getFontScale();
  return scale > 1.2;
}

/**
 * Generates accessible styles that adapt to high contrast mode.
 */
export interface AccessibleColors {
  background: string;
  text: string;
  textSecondary: string;
  border: string;
  error: string;
  success: string;
  warning: string;
}

const DEFAULT_COLORS: AccessibleColors = {
  background: '#f3f8ff',
  text: '#0b1b35',
  textSecondary: '#4b5c77',
  border: '#d5dded',
  error: '#dc2626',
  success: '#059669',
  warning: '#f59e0b',
};

const HIGH_CONTRAST_COLORS: AccessibleColors = {
  background: '#000000',
  text: '#ffffff',
  textSecondary: '#ffff00',
  border: '#ffffff',
  error: '#ff0000',
  success: '#00ff00',
  warning: '#ffff00',
};

export function getAccessibleColors(highContrast: boolean): AccessibleColors {
  return highContrast ? HIGH_CONTRAST_COLORS : DEFAULT_COLORS;
}

/**
 * Generates font size that scales with user preferences.
 */
export function getScaledFontSize(baseSize: number, fontScale: number): number {
  const scaled = baseSize * fontScale;
  // Clamp to reasonable bounds
  return Math.max(baseSize, Math.min(scaled, baseSize * 2));
}

/**
 * Generates accessible style variants for text components.
 */
export interface AccessibleTextStyle {
  fontSize: number;
  fontWeight: 'normal' | 'bold' | '600' | '700';
  color: string;
  lineHeight?: number;
}

export function createAccessibleTextStyle(
  baseSize: number,
  fontScale: number,
  highContrast: boolean,
  weight: 'normal' | 'bold' | '600' | '700' = 'normal',
): AccessibleTextStyle {
  const colors = getAccessibleColors(highContrast);
  return {
    fontSize: getScaledFontSize(baseSize, fontScale),
    fontWeight: weight,
    color: colors.text,
    lineHeight: getScaledFontSize(baseSize, fontScale) * 1.4,
  };
}
