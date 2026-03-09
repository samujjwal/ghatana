/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    /**
     * MUI-aligned breakpoints for consistent responsive design (Phase 2)
     * Aligned with @ghatana/tokens and MUI theme breakpoints
     * 
     * @ghatana/tokens: xs: 0, sm: 600, md: 960, lg: 1280, xl: 1920
     * Previous: sm: 640, md: 768, lg: 1024, xl: 1280
     */
    screens: {
      'sm': '600px',   // @ghatana/tokens sm (was 640px)
      'md': '960px',   // @ghatana/tokens md (was 768px)
      'lg': '1280px',  // @ghatana/tokens lg (was 1024px)
      'xl': '1920px',  // @ghatana/tokens xl (was 1280px)
    },
    extend: {
      colors: {
        primary: {
          50: '#f0f9ff',
          100: '#e0f2fe',
          200: '#bae6fd',
          300: '#7dd3fc',
          400: '#38bdf8',
          500: '#0ea5e9',
          600: '#0284c7',
          700: '#0369a1',
          800: '#075985',
          900: '#0c4a6e',
        },
      },
    },
  },
  plugins: [],
}


