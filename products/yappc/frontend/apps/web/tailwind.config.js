/**
 * Tailwind CSS configuration for YAPPC Web Application
 *
 * Uses shared preset from workspace root for consistent design system.
 * Follows the same pattern as data-cloud and tutorputor projects.
 *
 * @doc.type config
 * @doc.purpose Tailwind CSS configuration with design tokens
 * @doc.layer frontend
 */

export default {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
    '../../libs/ui/src/**/*.{js,ts,jsx,tsx}',
    '../../libs/canvas/src/**/*.{js,ts,jsx,tsx}',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      // Package-specific theme extensions
      // Note: Common design tokens are in workspace tailwind.config.ts
    },
  },
  plugins: [],
  safelist: [
    // Common UI patterns
    'bg-primary-50',
    'bg-primary-100',
    'bg-primary-500',
    'bg-primary-600',
    'bg-secondary-50',
    'bg-secondary-500',
    'bg-neutral-50',
    'bg-neutral-100',
    'bg-success',
    'bg-warning',
    'bg-error',
    'bg-info',
    'text-primary',
    'text-secondary',
    'text-muted',
    'border-neutral-200',
    'border-neutral-300',
    'hover:bg-primary-700',
    'hover:bg-secondary-600',
    'focus:ring-primary-500',
    'focus:ring-offset-2',
    // Dark mode text colors - explicit safelist
    'dark:text-white',
    'dark:text-gray-50',
    'dark:text-gray-100',
    'dark:text-gray-200',
    'dark:text-gray-300',
    'dark:bg-gray-900',
    'dark:bg-gray-800',
    'dark:border-gray-800',
    'dark:border-gray-700',
    // Dark card backgrounds for better contrast
    'dark:bg-slate-900',
    'dark:border-slate-700',
    'dark:border-slate-800',
    // Card backgrounds
    'dark:bg-indigo-900/40',
    'dark:bg-green-900/40',
    'dark:bg-blue-900/40',
    'dark:bg-purple-900/40',
    'dark:bg-orange-900/40',
    'dark:bg-cyan-900/40',
    // Icon text colors
    'dark:text-indigo-400',
    'dark:text-green-400',
    'dark:text-blue-400',
    'dark:text-purple-400',
    'dark:text-orange-400',
    'dark:text-cyan-400',
  ],
};
