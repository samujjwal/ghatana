/**
 * Tailwind CSS configuration for CES Workflow Platform UI.
 * Uses shared preset from workspace root for consistent design system.
 *
 * @doc.type config
 * @doc.purpose Tailwind CSS configuration with design tokens
 * @doc.layer frontend
 */
export default {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
    '../../../libs/typescript/ui/src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      // Package-specific theme extensions
      // Note: Common design tokens are in workspace tailwind.preset.cjs
    },
  },
  plugins: [],
  safelist: [
    // Workflow node colors
    'bg-blue-50',
    'bg-green-50',
    'bg-yellow-50',
    'bg-red-50',
    'border-blue-200',
    'border-green-200',
    'border-yellow-200',
    'border-red-200',
  ],
};
