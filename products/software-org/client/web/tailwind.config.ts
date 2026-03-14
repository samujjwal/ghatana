import tailwindPlugin from "tailwindcss/plugin";
import { join } from "path";
import { fileURLToPath } from "url";

const __dirname = fileURLToPath(new URL(".", import.meta.url));

/**
 * Tailwind CSS Configuration
 * 
 * Design System:
 * - Uses slate color palette for neutrals (consistent with tokens.css)
 * - All colors are WCAG AA compliant
 * - Dark mode enabled via 'class' strategy
 */
export default {
    content: [
        join(__dirname, "index.html"),
        join(__dirname, "src/**/*.{js,ts,jsx,tsx}"),
    ],
    darkMode: "class",
    theme: {
        /**
         * MUI-aligned breakpoints for consistent responsive design
         * Aligns with MUI theme breakpoints to prevent layout issues
         * when mixing Tailwind utilities with MUI components.
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
            colors: {
                // Brand colors
                brand: {
                    50: "#eff6ff",
                    100: "#dbeafe",
                    200: "#bfdbfe",
                    300: "#93c5fd",
                    400: "#60a5fa",
                    500: "#3b82f6",
                    600: "#2563eb",
                    700: "#1d4ed8",
                    800: "#1e40af",
                    900: "#1e3a8a",
                },
                // Semantic colors for status indicators
                semantic: {
                    success: {
                        light: "#d1fae5",
                        DEFAULT: "#059669",
                        dark: "#064e3b",
                    },
                    warning: {
                        light: "#fef3c7",
                        DEFAULT: "#d97706",
                        dark: "#78350f",
                    },
                    danger: {
                        light: "#fee2e2",
                        DEFAULT: "#dc2626",
                        dark: "#7f1d1d",
                    },
                    info: {
                        light: "#e0f2fe",
                        DEFAULT: "#0284c7",
                        dark: "#0c4a6e",
                    },
                },
                // Surface colors for cards/panels
                surface: {
                    DEFAULT: "var(--color-surface)",
                    hover: "var(--color-surface-hover)",
                    active: "var(--color-surface-active)",
                },
            },
            fontFamily: {
                sans: [
                    "-apple-system",
                    "BlinkMacSystemFont",
                    "Segoe UI",
                    "Roboto",
                    "Helvetica Neue",
                    "Arial",
                    "sans-serif",
                ],
                mono: [
                    "SFMono-Regular",
                    "Consolas",
                    "Liberation Mono",
                    "Menlo",
                    "monospace",
                ],
            },
            fontSize: {
                xs: ["0.75rem", { lineHeight: "1rem" }],
                sm: ["0.875rem", { lineHeight: "1.25rem" }],
                base: ["1rem", { lineHeight: "1.5rem" }],
                lg: ["1.125rem", { lineHeight: "1.75rem" }],
                xl: ["1.25rem", { lineHeight: "1.75rem" }],
                "2xl": ["1.5rem", { lineHeight: "2rem" }],
                "3xl": ["1.875rem", { lineHeight: "2.25rem" }],
                "4xl": ["2.25rem", { lineHeight: "2.5rem" }],
            },
            borderRadius: {
                sm: "0.375rem",
                DEFAULT: "0.5rem",
                md: "0.5rem",
                lg: "0.75rem",
                xl: "1rem",
            },
            boxShadow: {
                sm: "0 1px 2px 0 rgba(0, 0, 0, 0.05)",
                DEFAULT: "0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1)",
                md: "0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1)",
                lg: "0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1)",
                xl: "0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)",
            },
            animation: {
                pulse: "pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite",
                shimmer: "shimmer 2s linear infinite",
                "fade-in": "fadeIn 0.3s ease-in-out",
                "slide-up": "slideUp 0.3s ease-out",
                "spin-slow": "spin 3s linear infinite",
            },
            keyframes: {
                shimmer: {
                    "0%": { backgroundPosition: "-200% 0" },
                    "100%": { backgroundPosition: "200% 0" },
                },
                fadeIn: {
                    "0%": { opacity: "0" },
                    "100%": { opacity: "1" },
                },
                slideUp: {
                    "0%": { transform: "translateY(10px)", opacity: "0" },
                    "100%": { transform: "translateY(0)", opacity: "1" },
                },
            },
        },
    },
    plugins: [
        tailwindPlugin(function ({ addUtilities }) {
            addUtilities({
                ".scrollbar-thin": {
                    scrollbarWidth: "thin",
                    scrollbarColor: "rgb(148, 163, 184) rgb(241, 245, 249)",
                },
                ".scrollbar-thin-dark": {
                    scrollbarWidth: "thin",
                    scrollbarColor: "rgb(71, 85, 105) rgb(30, 41, 59)",
                },
                ".scrollbar-hide": {
                    scrollbarWidth: "none",
                    msOverflowStyle: "none",
                    "&::-webkit-scrollbar": {
                        display: "none",
                    },
                },
                // Text balance for better readability
                ".text-balance": {
                    textWrap: "balance",
                },
                // Focus ring utility
                ".focus-ring": {
                    "&:focus-visible": {
                        outline: "none",
                        boxShadow: "0 0 0 2px var(--color-bg-primary), 0 0 0 4px var(--color-border-focus)",
                    },
                },
            });
        }),
    ],
};
