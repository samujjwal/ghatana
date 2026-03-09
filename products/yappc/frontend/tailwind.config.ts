import type { Config } from 'tailwindcss';
import { palette, spacing, borderRadius, fontFamilies, fontWeights, fontSizes, lineHeights, lightShadows } from './libs/ui/src/tokens/index';

/**
 * Tailwind CSS configuration for the monorepo
 * 
 * Integrates @ghatana/yappc-ui design tokens with Tailwind CSS utility classes.
 * This configuration is shared across all apps (web, desktop, mobile-cap).
 * 
 * Design token mapping:
 * - Colors: palette.primary → colors.primary[50-900]
 * - Spacing: spacing[4] → spacing.4 (p-4, mt-4, etc.)
 * - Border radius: borderRadius.md → rounded-md
 * - Typography: fontSizes, fontWeights → text-*, font-*
 * - Shadows: lightShadows → shadow-*
 * 
 * @see {@link https://base-ui.com/react/overview/quick-start Base UI Quick Start}
 * @see {@link BASE_UI_TAILWIND_MIGRATION_PLAN.md Migration Plan}
 */
const config: Config = {
  // Scan all source files in apps and libs for Tailwind classes
  content: [
    './apps/web/src/**/*.{ts,tsx}',
    './apps/desktop/src/**/*.{ts,tsx}',
    './apps/mobile-cap/src/**/*.{ts,tsx}',
    './libs/ui/src/**/*.{ts,tsx}',
    './libs/canvas/src/**/*.{ts,tsx}',
    './libs/diagram/src/**/*.{ts,tsx}',
  ],

  // Dark mode via class strategy (add 'dark' class to <html>)
  darkMode: 'class',

  theme: {
    /**
     * MUI-aligned breakpoints for consistent responsive design
     * Aligns with MUI theme breakpoints to prevent layout issues
     * when mixing Tailwind utilities with MUI components.
     *
     * MUI defaults: xs: 0, sm: 600, md: 900, lg: 1200, xl: 1536
     * We align sm/md/lg to match MUI for consistency
     */
    screens: {
      'xs': '0px',
      'sm': '600px',   // MUI sm (was 640px in Tailwind)
      'md': '900px',   // MUI md (was 768px in Tailwind)
      'lg': '1200px',  // MUI lg (was 1024px in Tailwind)
      'xl': '1536px',  // MUI xl (was 1280px in Tailwind)
      '2xl': '1920px', // Extended for large displays
    },
    extend: {
      // Color palette from @ghatana/yappc-shared-ui-core/tokens
      colors: {
        // Primary color scale
        primary: {
          50: palette.primary[50],
          100: palette.primary[100],
          200: palette.primary[200],
          300: palette.primary[300],
          400: palette.primary[400],
          500: palette.primary[500], // Main primary
          600: palette.primary[600],
          700: palette.primary[700],
          800: palette.primary[800],
          900: palette.primary[900],
        },

        // Secondary color scale
        secondary: {
          50: palette.secondary[50],
          100: palette.secondary[100],
          200: palette.secondary[200],
          300: palette.secondary[300],
          400: palette.secondary[400],
          500: palette.secondary[500], // Main secondary
          600: palette.secondary[600],
          700: palette.secondary[700],
          800: palette.secondary[800],
          900: palette.secondary[900],
        },

        // Neutral/grey scale
        grey: {
          50: palette.neutral[50],
          100: palette.neutral[100],
          200: palette.neutral[200],
          300: palette.neutral[300],
          400: palette.neutral[400],
          500: palette.neutral[500],
          600: palette.neutral[600],
          700: palette.neutral[700],
          800: palette.neutral[800],
          900: palette.neutral[900],
        },

        // Semantic colors
        success: {
          50: '#f0fdf4',
          100: '#dcfce7',
          200: '#bbf7d0',
          300: '#86efac',
          400: '#4ade80',
          500: palette.success.main, // Main success
          600: '#16a34a',
          700: '#15803d',
          800: '#166534',
          900: '#14532d',
          light: palette.success.light,
          DEFAULT: palette.success.main,
          dark: palette.success.dark,
        },
        warning: {
          50: '#fffbeb',
          100: '#fef3c7',
          200: '#fde68a',
          300: '#fcd34d',
          400: '#fbbf24',
          500: palette.warning.main, // Main warning
          600: '#d97706',
          700: '#b45309',
          800: '#92400e',
          900: '#78350f',
          light: palette.warning.light,
          DEFAULT: palette.warning.main,
          dark: palette.warning.dark,
        },
        error: {
          50: '#fef2f2',
          100: '#fee2e2',
          200: '#fecaca',
          300: '#fca5a5',
          400: '#f87171',
          500: palette.error.main, // Main error
          600: '#dc2626',
          700: '#b91c1c',
          800: '#991b1b',
          900: '#7f1d1d',
          light: palette.error.light,
          DEFAULT: palette.error.main,
          dark: palette.error.dark,
        },
        info: {
          50: '#eff6ff',
          100: '#dbeafe',
          200: '#bfdbfe',
          300: '#93c5fd',
          400: '#60a5fa',
          500: palette.info.main, // Main info
          600: '#2563eb',
          700: '#1d4ed8',
          800: '#1e40af',
          900: '#1e3a8a',
          light: palette.info.light,
          DEFAULT: palette.info.main,
          dark: palette.info.dark,
        },

        // Background colors (from theme)
        background: {
          paper: '#ffffff',
          default: '#fafafa',
        },

        // Text colors (from theme)
        text: {
          primary: 'rgba(0, 0, 0, 0.87)',
          secondary: 'rgba(0, 0, 0, 0.6)',
          disabled: 'rgba(0, 0, 0, 0.38)',
        },
      },

      // Spacing scale from @ghatana/yappc-shared-ui-core/tokens
      spacing: {
        0: `${spacing[0]}px`,
        0.5: `${spacing[0.5]}px`,
        1: `${spacing[1]}px`,
        1.5: `${spacing[1.5]}px`,
        2: `${spacing[2]}px`,
        2.5: `${spacing[2.5]}px`,
        3: `${spacing[3]}px`,
        3.5: `${spacing[3.5]}px`,
        4: `${spacing[4]}px`,
        5: `${spacing[5]}px`,
        6: `${spacing[6]}px`,
        7: `${spacing[7]}px`,
        8: `${spacing[8]}px`,
        9: `${spacing[9]}px`,
        10: `${spacing[10]}px`,
        12: `${spacing[12]}px`,
        14: `${spacing[14]}px`,
        16: `${spacing[16]}px`,
        20: `${spacing[20]}px`,
        24: `${spacing[24]}px`,
        28: `${spacing[28]}px`,
        32: `${spacing[32]}px`,
        36: `${spacing[36]}px`,
        40: `${spacing[40]}px`,
        44: `${spacing[44]}px`,
        48: `${spacing[48]}px`,
        52: `${spacing[52]}px`,
        56: `${spacing[56]}px`,
        60: `${spacing[60]}px`,
        64: `${spacing[64]}px`,
        72: `${spacing[72]}px`,
        80: `${spacing[80]}px`,
        96: `${spacing[96]}px`,

        // Semantic spacing aliases
        xs: `${spacing[1]}px`,   // 4px
        sm: `${spacing[2]}px`,   // 8px
        md: `${spacing[4]}px`,   // 16px
        lg: `${spacing[6]}px`,   // 24px
        xl: `${spacing[8]}px`,   // 32px
        '2xl': `${spacing[12]}px`, // 48px
        '3xl': `${spacing[16]}px`, // 64px
      },

      // Border radius from @ghatana/yappc-shared-ui-core/tokens
      borderRadius: {
        none: `${borderRadius.none}px`,
        sm: `${borderRadius.sm}px`,
        md: `${borderRadius.md}px`,
        lg: `${borderRadius.lg}px`,
        xl: `${borderRadius.xl}px`,
        '2xl': `${borderRadius['2xl']}px`,
        '3xl': `${borderRadius['3xl']}px`,
        full: '9999px',
      },

      // Box shadows from @ghatana/yappc-shared-ui-core/tokens
      boxShadow: {
        sm: lightShadows[1],
        DEFAULT: lightShadows[2],
        md: lightShadows[4],
        lg: lightShadows[6],
        xl: lightShadows[8],
        '2xl': lightShadows[12],
        none: 'none',
      },

      // Typography from @ghatana/yappc-shared-ui-core/tokens
      fontFamily: {
        sans: fontFamilies.primary,
        mono: fontFamilies.code,
      },

      fontSize: {
        xs: [fontSizes.xs, { lineHeight: lineHeights.tight }],
        sm: [fontSizes.sm, { lineHeight: lineHeights.snug }],
        base: [fontSizes.md, { lineHeight: lineHeights.normal }],
        lg: [fontSizes.lg, { lineHeight: lineHeights.relaxed }],
        xl: [fontSizes.xl, { lineHeight: lineHeights.relaxed }],
        '2xl': [fontSizes['2xl'], { lineHeight: lineHeights.tight }],
        '3xl': [fontSizes['3xl'], { lineHeight: lineHeights.tight }],
        '4xl': [fontSizes['4xl'], { lineHeight: lineHeights.tight }],
        '5xl': [fontSizes['5xl'], { lineHeight: lineHeights.none }],
        '6xl': [fontSizes['6xl'], { lineHeight: lineHeights.none }],
      },

      fontWeight: {
        light: `${fontWeights.light}`,
        normal: `${fontWeights.regular}`,
        medium: `${fontWeights.medium}`,
        semibold: `${fontWeights.semiBold}`,
        bold: `${fontWeights.bold}`,
      },

      // Custom animations
      keyframes: {
        'progress-indeterminate': {
          '0%': { left: '-40%' },
          '100%': { left: '100%' },
        },
        'spin-slow': {
          '0%': { transform: 'rotate(0deg)' },
          '100%': { transform: 'rotate(360deg)' },
        },
      },
      animation: {
        'progress-indeterminate': 'progress-indeterminate 1.5s ease-in-out infinite',
        'spin-slow': 'spin-slow 2s linear infinite',
      },
    },
  },

  plugins: [
    require('@tailwindcss/forms'),
    require('@tailwindcss/typography'),
    require('tailwindcss-animate'),
  ],
};

export default config;
