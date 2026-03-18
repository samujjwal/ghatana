/**
 * Semantic Color Tokens
 * 
 * WCAG AAA compliant color system with semantic naming
 * for consistent theming across admin and student apps.
 * 
 * @doc.type tokens
 * @doc.purpose Unified color system
 * @doc.layer core
 */

export const semanticColors = {
  // Brand Colors
  brand: {
    primary: {
      50: '#eff6ff',
      100: '#dbeafe',
      200: '#bfdbfe',
      300: '#93c5fd',
      400: '#60a5fa',
      500: '#3b82f6',
      600: '#2563eb',  // Main primary
      700: '#1d4ed8',
      800: '#1e40af',
      900: '#1e3a8a',
      950: '#172554',
    },
    secondary: {
      50: '#faf5ff',
      100: '#f3e8ff',
      200: '#e9d5ff',
      300: '#d8b4fe',
      400: '#c084fc',
      500: '#a855f7',
      600: '#9333ea',  // Main secondary
      700: '#7e22ce',
      800: '#6b21a8',
      900: '#581c87',
      950: '#3b0764',
    },
    accent: {
      50: '#f0f9ff',
      100: '#e0f2fe',
      200: '#bae6fd',
      300: '#7dd3fc',
      400: '#38bdf8',
      500: '#0ea5e9',
      600: '#0284c7',  // Main accent
      700: '#0369a1',
      800: '#075985',
      900: '#0c4a6e',
      950: '#082f49',
    },
  },
  
  // Semantic States
  semantic: {
    success: {
      main: '#16a34a',       // green-600
      bg: '#f0fdf4',         // green-50
      bgHover: '#dcfce7',    // green-100
      text: '#15803d',       // green-700
      border: '#86efac',     // green-300
    },
    warning: {
      main: '#ca8a04',       // yellow-600
      bg: '#fefce8',         // yellow-50
      bgHover: '#fef9c3',    // yellow-100
      text: '#a16207',       // yellow-700
      border: '#fde047',     // yellow-300
    },
    error: {
      main: '#dc2626',       // red-600
      bg: '#fef2f2',         // red-50
      bgHover: '#fee2e2',    // red-100
      text: '#b91c1c',       // red-700
      border: '#fca5a5',     // red-300
    },
    info: {
      main: '#2563eb',       // blue-600
      bg: '#eff6ff',         // blue-50
      bgHover: '#dbeafe',    // blue-100
      text: '#1d4ed8',       // blue-700
      border: '#93c5fd',     // blue-300
    },
  },
  
  // Neutral Colors (WCAG AAA compliant)
  neutral: {
    text: {
      primary: {
        light: '#111827',    // gray-900
        dark: '#f9fafb',     // gray-50
      },
      secondary: {
        light: '#4b5563',    // gray-600
        dark: '#d1d5db',     // gray-300
      },
      tertiary: {
        light: '#6b7280',    // gray-500
        dark: '#9ca3af',     // gray-400
      },
      disabled: {
        light: '#9ca3af',    // gray-400
        dark: '#6b7280',     // gray-500
      },
    },
    background: {
      primary: {
        light: '#ffffff',
        dark: '#111827',     // gray-900
      },
      secondary: {
        light: '#f9fafb',    // gray-50
        dark: '#1f2937',     // gray-800
      },
      tertiary: {
        light: '#f3f4f6',    // gray-100
        dark: '#374151',     // gray-700
      },
      elevated: {
        light: '#ffffff',
        dark: '#1f2937',     // gray-800 (for cards)
      },
      overlay: {
        light: 'rgba(0, 0, 0, 0.5)',
        dark: 'rgba(0, 0, 0, 0.7)',
      },
    },
    border: {
      default: {
        light: '#e5e7eb',    // gray-200
        dark: '#374151',     // gray-700
      },
      subtle: {
        light: '#f3f4f6',    // gray-100
        dark: '#4b5563',     // gray-600
      },
      strong: {
        light: '#d1d5db',    // gray-300
        dark: '#6b7280',     // gray-500
      },
    },
  },
  
  // Tailwind CSS class utilities
  tw: {
    // Primary
    primary: 'bg-primary-600 hover:bg-primary-700 text-white',
    primaryOutline: 'border-2 border-primary-600 text-primary-600 hover:bg-primary-50',
    primaryGhost: 'text-primary-600 hover:bg-primary-50',
    primarySubtle: 'bg-primary-50 text-primary-700',
    
    // Secondary
    secondary: 'bg-gray-100 hover:bg-gray-200 text-gray-900',
    secondaryOutline: 'border-2 border-gray-300 text-gray-700 hover:bg-gray-50',
    secondaryGhost: 'text-gray-700 hover:bg-gray-100',
    
    // Success
    success: 'bg-green-600 hover:bg-green-700 text-white',
    successOutline: 'border-2 border-green-600 text-green-600 hover:bg-green-50',
    successSubtle: 'bg-green-50 text-green-700',
    
    // Warning
    warning: 'bg-yellow-600 hover:bg-yellow-700 text-white',
    warningOutline: 'border-2 border-yellow-600 text-yellow-600 hover:bg-yellow-50',
    warningSubtle: 'bg-yellow-50 text-yellow-700',
    
    // Error/Danger
    danger: 'bg-red-600 hover:bg-red-700 text-white',
    dangerOutline: 'border-2 border-red-600 text-red-600 hover:bg-red-50',
    dangerSubtle: 'bg-red-50 text-red-700',
    
    // Info
    info: 'bg-blue-600 hover:bg-blue-700 text-white',
    infoOutline: 'border-2 border-blue-600 text-blue-600 hover:bg-blue-50',
    infoSubtle: 'bg-blue-50 text-blue-700',
  },
} as const;

// CSS variable exports for runtime theme switching
export const cssVariables = {
  light: {
    '--color-primary': semanticColors.brand.primary[600],
    '--color-secondary': semanticColors.brand.secondary[600],
    '--color-success': semanticColors.semantic.success.main,
    '--color-warning': semanticColors.semantic.warning.main,
    '--color-error': semanticColors.semantic.error.main,
    '--color-info': semanticColors.semantic.info.main,
    '--color-text-primary': semanticColors.neutral.text.primary.light,
    '--color-text-secondary': semanticColors.neutral.text.secondary.light,
    '--color-bg-primary': semanticColors.neutral.background.primary.light,
    '--color-bg-secondary': semanticColors.neutral.background.secondary.light,
    '--color-border': semanticColors.neutral.border.default.light,
  },
  dark: {
    '--color-primary': semanticColors.brand.primary[600],
    '--color-secondary': semanticColors.brand.secondary[600],
    '--color-success': semanticColors.semantic.success.main,
    '--color-warning': semanticColors.semantic.warning.main,
    '--color-error': semanticColors.semantic.error.main,
    '--color-info': semanticColors.semantic.info.main,
    '--color-text-primary': semanticColors.neutral.text.primary.dark,
    '--color-text-secondary': semanticColors.neutral.text.secondary.dark,
    '--color-bg-primary': semanticColors.neutral.background.primary.dark,
    '--color-bg-secondary': semanticColors.neutral.background.secondary.dark,
    '--color-border': semanticColors.neutral.border.default.dark,
  },
} as const;

export type SemanticColor = keyof typeof semanticColors;
export type SemanticColorVariant = keyof typeof semanticColors.semantic;
